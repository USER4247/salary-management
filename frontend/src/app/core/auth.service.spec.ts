import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from './auth.service';
import { authTestProviders, READ_EMPLOYEES, sessionFor } from './testing';

/** Stands in for the browser's BroadcastChannel so tests can play "another tab". */
class FakeChannel {
  static instances: FakeChannel[] = [];
  private listeners: ((event: { data: unknown }) => void)[] = [];
  posted: unknown[] = [];
  constructor() {
    FakeChannel.instances.push(this);
  }
  addEventListener(_type: string, listener: (event: { data: unknown }) => void) {
    this.listeners.push(listener);
  }
  postMessage(message: unknown) {
    this.posted.push(message);
  }
  receive(data: unknown) {
    this.listeners.forEach((listener) => listener({ data }));
  }
}

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    FakeChannel.instances = [];
    vi.stubGlobal('BroadcastChannel', FakeChannel);
    vi.useFakeTimers();
    TestBed.configureTestingModule({ providers: authTestProviders() });
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();   // fails the test if any request was left unanswered or unexpected
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  describe('signing in', () => {
    it('stores the user and loads the rules from the server', () => {
      auth.signIn('maya@acme.test', 'pw').subscribe();
      http.expectOne('/api/auth/login').flush(sessionFor());

      expect(auth.isSignedIn()).toBe(true);
      expect(auth.user()?.fullName).toBe('Maya Manager');
      expect(auth.can('read', 'Employee')).toBe(true);
      expect(auth.can('delete', 'Employee')).toBe(false);
    });

    it('sends the email and password to the server', () => {
      auth.signIn('maya@acme.test', 'pw').subscribe();

      const request = http.expectOne('/api/auth/login');
      expect(request.request.body).toEqual({ email: 'maya@acme.test', password: 'pw' });
      request.flush(sessionFor());
    });

    it('lets a superadmin do anything (manage all)', () => {
      auth.signIn('s@acme.test', 'pw').subscribe();
      http.expectOne('/api/auth/login').flush(sessionFor([{ action: 'manage', subject: 'all' }]));

      expect(auth.can('delete', 'Employee')).toBe(true);
      expect(auth.can('read', 'Insights')).toBe(true);
    });
  });

  describe('access()', () => {
    it('tells yes, own-country-only and no apart', () => {
      auth.signIn('x', 'y').subscribe();
      http.expectOne('/api/auth/login').flush(sessionFor([
        { action: 'read', subject: 'Employee', conditions: { country: 'IN' } },
        { action: 'read', subject: 'Insights' },
      ]));

      expect(auth.access('read', 'Employee')).toBe('limited');
      expect(auth.access('read', 'Insights')).toBe('yes');
      expect(auth.access('delete', 'Employee')).toBe('no');
    });
  });

  describe('restoring a session when the app starts', () => {
    it('signs in again from /me when the cookie is still valid', async () => {
      const done = auth.restore();
      http.expectOne('/api/auth/me').flush(sessionFor());
      await done;

      expect(auth.isSignedIn()).toBe(true);
    });

    it('falls back to the refresh cookie when the access token has expired', async () => {
      const done = auth.restore();
      http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
      http.expectOne('/api/auth/refresh').flush(sessionFor());
      await done;

      expect(auth.isSignedIn()).toBe(true);
    });

    it('stays signed out, without an error, when nothing works', async () => {
      const done = auth.restore();
      http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
      http.expectOne('/api/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });
      await done;

      expect(auth.isSignedIn()).toBe(false);
    });
  });

  describe('refreshing', () => {
    it('shares one request between callers that ask at the same time', () => {
      const results: unknown[] = [];
      auth.refresh().subscribe((s) => results.push(s));
      auth.refresh().subscribe((s) => results.push(s));

      http.expectOne('/api/auth/refresh').flush(sessionFor());   // expectOne fails if there were two

      expect(results).toHaveLength(2);
    });

    it('refreshes by itself shortly before the access token ends', () => {
      auth.signIn('x', 'y').subscribe();
      http.expectOne('/api/auth/login').flush(sessionFor([READ_EMPLOYEES], 15));

      vi.advanceTimersByTime(13 * 60_000);
      http.expectNone('/api/auth/refresh');                      // too early

      vi.advanceTimersByTime(60_000 + 1_000);                    // now inside the last minute
      http.expectOne('/api/auth/refresh').flush(sessionFor());
    });

    it('signs the user out when the automatic refresh fails', () => {
      auth.signIn('x', 'y').subscribe();
      http.expectOne('/api/auth/login').flush(sessionFor());

      vi.advanceTimersByTime(15 * 60_000);
      http.expectOne('/api/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });

      expect(auth.isSignedIn()).toBe(false);
      expect(navigate).toHaveBeenCalledWith(['/sign-in']);
    });
  });

  describe('signing out', () => {
    it('tells the server, clears the session and goes to the sign-in page', async () => {
      auth.signIn('x', 'y').subscribe();
      http.expectOne('/api/auth/login').flush(sessionFor());

      const done = auth.signOut();
      http.expectOne('/api/auth/logout').flush(null, { status: 204, statusText: 'No Content' });
      await done;

      expect(auth.isSignedIn()).toBe(false);
      expect(auth.can('read', 'Employee')).toBe(false);
      expect(navigate).toHaveBeenCalledWith(['/sign-in']);
    });

    it('still signs out here when the server cannot be reached', async () => {
      auth.signIn('x', 'y').subscribe();
      http.expectOne('/api/auth/login').flush(sessionFor());

      const done = auth.signOut();
      http.expectOne('/api/auth/logout').error(new ProgressEvent('error'));
      await done;

      expect(auth.isSignedIn()).toBe(false);
    });

    it('stops the background refresh timer', async () => {
      auth.signIn('x', 'y').subscribe();
      http.expectOne('/api/auth/login').flush(sessionFor());
      const done = auth.signOut();
      http.expectOne('/api/auth/logout').flush(null, { status: 204, statusText: 'No Content' });
      await done;

      vi.advanceTimersByTime(60 * 60_000);

      http.expectNone('/api/auth/refresh');
    });
  });

  describe('other browser tabs', () => {
    it('signs out here when another tab signs out', () => {
      auth.signIn('x', 'y').subscribe();
      http.expectOne('/api/auth/login').flush(sessionFor());

      FakeChannel.instances[0].receive('signed-out');

      expect(auth.isSignedIn()).toBe(false);
      expect(navigate).toHaveBeenCalledWith(['/sign-in']);
    });

    it('tells other tabs when this one signs in', () => {
      auth.signIn('x', 'y').subscribe();
      http.expectOne('/api/auth/login').flush(sessionFor());

      expect(FakeChannel.instances[0].posted).toContain('signed-in');
    });
  });
});
