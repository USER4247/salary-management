import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../core/auth.service';
import { authTestProviders, sessionFor } from '../core/testing';
import { Shell } from './shell';

describe('Shell', () => {
  let auth: AuthService;

  beforeEach(() => {
    vi.stubGlobal('BroadcastChannel', undefined);
    TestBed.configureTestingModule({ imports: [Shell], providers: authTestProviders() });
    auth = TestBed.inject(AuthService);
    auth.signIn('x', 'y').subscribe();
    TestBed.inject(HttpTestingController).expectOne('/api/auth/login').flush(sessionFor());
  });

  afterEach(() => vi.unstubAllGlobals());

  it('shows the signed-in user, their role and the navigation', async () => {
    const fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    expect(page.textContent).toContain('Maya Manager');
    expect(page.textContent).toContain('HR_MANAGER');
    expect(page.textContent).toContain('Employees');
    expect(page.textContent).toContain('My access');
  });

  it('signs out when the button is pressed', async () => {
    const signOut = vi.spyOn(auth, 'signOut').mockResolvedValue();
    const fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();

    const buttons = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'));
    buttons.find((b) => b.textContent?.includes('Sign out'))!.click();

    expect(signOut).toHaveBeenCalled();
  });
});
