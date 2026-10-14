import { HttpClient } from '@angular/common/http';
import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from './auth.service';
import { authTestProviders, sessionFor } from './testing';

const UNAUTHORIZED = { status: 401, statusText: 'Unauthorized' };

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    vi.stubGlobal('BroadcastChannel', undefined);   // no cross-tab messages between tests
    TestBed.configureTestingModule({ providers: authTestProviders() });
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => {
    backend.verify();
    vi.unstubAllGlobals();
  });

  it('passes successful responses straight through', () => {
    let result: unknown;
    http.get('/api/employees').subscribe((value) => (result = value));

    backend.expectOne('/api/employees').flush(['ok']);

    expect(result).toEqual(['ok']);
  });

  it('refreshes after a 401 and repeats the original request', () => {
    let result: unknown;
    http.get('/api/employees').subscribe((value) => (result = value));

    backend.expectOne('/api/employees').flush(null, UNAUTHORIZED);        // access token had expired
    backend.expectOne('/api/auth/refresh').flush(sessionFor());           // quietly get a new one
    backend.expectOne('/api/employees').flush(['ok']);                    // the retry succeeds

    expect(result).toEqual(['ok']);
  });

  it('signs the user out when the refresh also fails', () => {
    const expire = vi.spyOn(auth, 'expire');
    let failed = false;
    http.get('/api/employees').subscribe({ error: () => (failed = true) });

    backend.expectOne('/api/employees').flush(null, UNAUTHORIZED);
    backend.expectOne('/api/auth/refresh').flush(null, UNAUTHORIZED);

    expect(failed).toBe(true);
    expect(expire).toHaveBeenCalled();
  });

  it('uses a single refresh when several requests fail together', () => {
    http.get('/api/employees').subscribe();
    http.get('/api/insights').subscribe();

    backend.expectOne('/api/employees').flush(null, UNAUTHORIZED);
    backend.expectOne('/api/insights').flush(null, UNAUTHORIZED);
    backend.expectOne('/api/auth/refresh').flush(sessionFor());           // expectOne fails if there were two
    backend.expectOne('/api/employees').flush([]);
    backend.expectOne('/api/insights').flush([]);
  });

  it('does not retry the sign-in calls themselves', () => {
    let status = 0;
    http.post('/api/auth/login', {}).subscribe({ error: (e) => (status = e.status) });

    backend.expectOne('/api/auth/login').flush(null, UNAUTHORIZED);

    backend.expectNone('/api/auth/refresh');
    expect(status).toBe(401);
  });

  it('leaves other errors alone (no refresh for a 500 or 403)', () => {
    let status = 0;
    http.get('/api/employees').subscribe({ error: (e) => (status = e.status) });

    backend.expectOne('/api/employees').flush(null, { status: 403, statusText: 'Forbidden' });

    backend.expectNone('/api/auth/refresh');
    expect(status).toBe(403);
  });
});
