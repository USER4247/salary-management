import { HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatButtonToggleGroup } from '@angular/material/button-toggle';
import { By } from '@angular/platform-browser';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../core/auth.service';
import { authTestProviders, sessionFor } from '../core/testing';
import { InsightsPage } from './insights-page';
import { PayInsights, PayStats } from './insights.service';

const stats = (key: string, headcount: number, average: number, median: number): PayStats =>
  ({ key, headcount, total: headcount * average, average, median, min: average - 10_000, max: average + 10_000 });

const response = (groups: PayStats[]): PayInsights => ({
  currency: 'USD',
  assumption: 'Converted to US dollars at fixed rates: 1 USD = 83.0 INR.',
  overall: { key: 'All', headcount: 300, total: 30_000_000, average: 100_000, median: 95_000, min: 20_000, max: 400_000 },
  groups,
});

describe('InsightsPage', () => {
  let fixture: ComponentFixture<InsightsPage>;
  let backend: HttpTestingController;
  const page = () => fixture.nativeElement as HTMLElement;
  const lastRequest = (): TestRequest => {
    const requests = backend.match((r) => r.url === '/api/insights/pay');
    return requests[requests.length - 1];
  };

  async function open(rules: Parameters<typeof sessionFor>[0], user: Parameters<typeof sessionFor>[2] = {}) {
    vi.stubGlobal('BroadcastChannel', undefined);
    TestBed.configureTestingModule({ imports: [InsightsPage], providers: authTestProviders() });
    backend = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).signIn('x', 'y').subscribe();
    backend.expectOne('/api/auth/login').flush(sessionFor(rules, 15, user));
    fixture = TestBed.createComponent(InsightsPage);
    await fixture.whenStable();
  }

  afterEach(() => vi.unstubAllGlobals());

  describe('for someone who sees every country', () => {
    const everything = [{ action: 'read', subject: 'Insights' }] as const;

    it('shows the overall figures and the exchange-rate note', async () => {
      await open([...everything]);
      lastRequest().flush(response([stats('US', 200, 120_000, 115_000), stats('IN', 100, 60_000, 55_000)]));
      await fixture.whenStable();

      const text = page().textContent ?? '';
      expect(text).toContain('300');           // employees
      expect(text).toContain('$30M');          // total payroll, compact
      expect(text).toContain('$100,000');      // average
      expect(text).toContain('$95,000');       // median
      expect(text).toContain('fixed rates');
    });

    it('lists the groups with the highest average first, each with a bar', async () => {
      await open([...everything]);
      lastRequest().flush(response([stats('IN', 100, 60_000, 55_000), stats('US', 200, 120_000, 115_000)]));
      await fixture.whenStable();

      const rows = Array.from(page().querySelectorAll('tr[mat-row]'));
      expect(rows[0].textContent).toContain('US');
      expect(rows[1].textContent).toContain('IN');
      const bars = Array.from(page().querySelectorAll<HTMLElement>('.bar')).map((bar) => bar.style.width);
      expect(bars).toEqual(['100%', '50%']);   // IN's average is half of US's
    });

    it('asks for another grouping when a different button is chosen', async () => {
      await open([...everything]);
      lastRequest().flush(response([]));
      await fixture.whenStable();

      fixture.debugElement.query(By.directive(MatButtonToggleGroup)).injector.get(MatButtonToggleGroup)
        .change.emit({ value: 'department' } as never);

      expect(lastRequest().request.params.get('groupBy')).toBe('department');
    });

    it('says so when nothing matches', async () => {
      await open([...everything]);
      lastRequest().flush(response([]));
      await fixture.whenStable();

      expect(page().textContent).toContain('No employees match');
    });

    it('shows an error with a retry button, and recovers', async () => {
      await open([...everything]);
      lastRequest().flush(null, { status: 500, statusText: 'Server Error' });
      await fixture.whenStable();
      expect(page().querySelector('[role=alert]')?.textContent).toContain('Could not load the insights');

      page().querySelector<HTMLButtonElement>('[role=alert] button')!.click();
      lastRequest().flush(response([stats('US', 200, 120_000, 115_000)]));
      await fixture.whenStable();

      expect(page().querySelector('[role=alert]')).toBeNull();
      expect(page().querySelectorAll('tr[mat-row]')).toHaveLength(1);
    });
  });

  it('tells a regional officer which country they are looking at', async () => {
    await open(
      [{ action: 'read', subject: 'Insights', conditions: { country: 'IN' } }],
      { role: 'COUNTRY_HR', countryScope: 'IN' },
    );
    lastRequest().flush(response([stats('IN', 100, 60_000, 55_000)]));
    await fixture.whenStable();

    expect(page().textContent).toContain('Showing only IN');
  });
});
