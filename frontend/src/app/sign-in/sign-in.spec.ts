import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { authTestProviders, sessionFor } from '../core/testing';
import { SignIn } from './sign-in';

describe('SignIn', () => {
  let fixture: ComponentFixture<SignIn>;
  let backend: HttpTestingController;
  let navigateByUrl: ReturnType<typeof vi.spyOn>;
  const page = () => fixture.nativeElement as HTMLElement;

  beforeEach(async () => {
    vi.stubGlobal('BroadcastChannel', undefined);
    TestBed.configureTestingModule({ imports: [SignIn], providers: authTestProviders() });
    navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SignIn);
    await fixture.whenStable();
  });

  afterEach(() => {
    backend.verify();
    vi.unstubAllGlobals();
  });

  function type(selector: string, value: string): void {
    const input = page().querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  const submitButton = () => page().querySelector<HTMLButtonElement>('button[type=submit]')!;

  async function submitWith(email: string, password: string): Promise<void> {
    type('input[type=email]', email);
    type('input[type=password]', password);
    await fixture.whenStable();
    submitButton().click();
  }

  it('cannot be submitted until both fields are filled', async () => {
    expect(submitButton().disabled).toBe(true);

    type('input[type=email]', 'maya@acme.test');
    type('input[type=password]', 'secret');
    await fixture.whenStable();

    expect(submitButton().disabled).toBe(false);
  });

  it('fills in a demo account when its chip is clicked', async () => {
    const chips = Array.from(page().querySelectorAll<HTMLElement>('mat-chip'));
    chips.find((chip) => chip.textContent?.includes('HR Viewer'))!.click();
    await fixture.whenStable();

    expect(page().querySelector<HTMLInputElement>('input[type=email]')!.value).toBe('viewer@acme.test');
    expect(page().querySelector<HTMLInputElement>('input[type=password]')!.value).toBe('Demo@12345');
  });

  it('fills in the regional officer account for the chosen country', async () => {
    const choose = page().querySelector<HTMLSelectElement>('select')!;
    choose.value = 'IN';
    choose.dispatchEvent(new Event('change'));
    await fixture.whenStable();

    expect(page().querySelector<HTMLInputElement>('input[type=email]')!.value).toBe('hr.in@acme.test');
    expect(page().querySelector<HTMLInputElement>('input[type=password]')!.value).toBe('Demo@12345');
  });

  it('signs in and goes home on success', async () => {
    await submitWith('maya@acme.test', 'secret');

    backend.expectOne('/api/auth/login').flush(sessionFor());

    expect(navigateByUrl).toHaveBeenCalledWith('/');
  });

  it('shows a clear message for a wrong password', async () => {
    await submitWith('maya@acme.test', 'wrong');

    backend.expectOne('/api/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();

    expect(page().querySelector('[role=alert]')?.textContent).toContain('Wrong email or password');
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it('shows a rate-limit message when the server says 429', async () => {
    await submitWith('maya@acme.test', 'wrong');

    backend.expectOne('/api/auth/login').flush(null, { status: 429, statusText: 'Too Many Requests' });
    await fixture.whenStable();

    expect(page().querySelector('[role=alert]')?.textContent).toContain('Too many attempts');
  });
});
