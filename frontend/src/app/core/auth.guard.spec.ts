import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { authGuard, guestGuard } from './auth.guard';
import { AuthService } from './auth.service';
import { authTestProviders } from './testing';

describe('route guards', () => {
  let auth: AuthService;
  let router: Router;

  const run = (guard: typeof authGuard, url = '/employees') =>
    TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    );

  beforeEach(() => {
    vi.stubGlobal('BroadcastChannel', undefined);
    TestBed.configureTestingModule({ providers: authTestProviders() });
    auth = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
  });

  afterEach(() => vi.unstubAllGlobals());

  const signedIn = () =>
    auth.user.set({ id: 1, email: 'a@b.c', fullName: 'A', role: 'HR_VIEWER', countryScope: null });

  it('lets a signed-in user through', () => {
    signedIn();

    expect(run(authGuard)).toBe(true);
  });

  it('sends an anonymous user to sign-in and remembers where they were going', () => {
    const result = run(authGuard, '/employees') as UrlTree;

    expect(router.serializeUrl(result)).toBe('/sign-in?returnUrl=%2Femployees');
  });

  it('keeps signed-in users away from the sign-in page', () => {
    signedIn();

    expect(router.serializeUrl(run(guestGuard) as UrlTree)).toBe('/');
  });

  it('shows the sign-in page to anonymous users', () => {
    expect(run(guestGuard)).toBe(true);
  });
});
