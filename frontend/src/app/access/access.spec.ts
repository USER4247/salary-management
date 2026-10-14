import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../core/auth.service';
import { authTestProviders, sessionFor } from '../core/testing';
import { Access } from './access';

describe('Access', () => {
  let auth: AuthService;
  let backend: HttpTestingController;

  beforeEach(() => {
    vi.stubGlobal('BroadcastChannel', undefined);
    TestBed.configureTestingModule({ imports: [Access], providers: authTestProviders() });
    auth = TestBed.inject(AuthService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => vi.unstubAllGlobals());

  async function renderFor(rules: Parameters<typeof sessionFor>[0]): Promise<HTMLElement> {
    auth.signIn('x', 'y').subscribe();
    backend.expectOne('/api/auth/login').flush(sessionFor(rules));
    const fixture = TestBed.createComponent(Access);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows Yes, No and "Own country only" from the rules', async () => {
    const page = await renderFor([
      { action: 'read', subject: 'Employee', conditions: { country: 'IN' } },
      { action: 'read', subject: 'Insights' },
    ]);

    const text = page.textContent ?? '';
    expect(text).toContain('Own country only');
    expect(text).toContain('Yes');
    expect(text).toContain('No');
  });

  it('shows everything as allowed for a superadmin', async () => {
    const page = await renderFor([{ action: 'manage', subject: 'all' }]);

    const cells = Array.from(page.querySelectorAll('td')).map((cell) => cell.textContent?.trim());
    expect(cells).toHaveLength(8);
    expect(cells.every((cell) => cell === 'Yes')).toBe(true);
  });
});
