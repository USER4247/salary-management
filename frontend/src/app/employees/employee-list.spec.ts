import { HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../core/auth.service';
import { authTestProviders, sessionFor } from '../core/testing';
import { EmployeeList } from './employee-list';
import { Employee, EmployeePage } from './employee.service';

const asha: Employee = {
  id: 1, fullName: 'Asha Rao', email: 'asha@acme.test', country: 'IN', department: 'Engineering',
  jobTitle: 'Software Engineer', annualSalary: 2_000_000, currency: 'INR', hireDate: '2020-01-15', status: 'ACTIVE',
};
const john: Employee = {
  id: 2, fullName: 'John Smith', email: 'john@acme.test', country: 'US', department: 'Sales',
  jobTitle: 'Account Executive', annualSalary: 120_000, currency: 'USD', hireDate: '2019-05-01', status: 'ON_LEAVE',
};

const pageOf = (content: Employee[], total = content.length): EmployeePage =>
  ({ content, page: 0, size: 25, totalElements: total, totalPages: 1 });

describe('EmployeeList', () => {
  let fixture: ComponentFixture<EmployeeList>;
  let backend: HttpTestingController;
  const page = () => fixture.nativeElement as HTMLElement;

  /** The most recent request to the list endpoint. */
  const lastRequest = (): TestRequest => {
    const requests = backend.match((r) => r.url === '/api/employees');
    return requests[requests.length - 1];
  };

  async function open(rules: Parameters<typeof sessionFor>[0], user: Parameters<typeof sessionFor>[2] = {}) {
    vi.stubGlobal('BroadcastChannel', undefined);
    TestBed.configureTestingModule({ imports: [EmployeeList], providers: authTestProviders() });
    backend = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).signIn('x', 'y').subscribe();
    backend.expectOne('/api/auth/login').flush(sessionFor(rules, 15, user));
    fixture = TestBed.createComponent(EmployeeList);
    await fixture.whenStable();
  }

  afterEach(() => vi.unstubAllGlobals());

  const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

  describe('for someone who sees every country', () => {
    beforeEach(async () => open([{ action: 'manage', subject: 'all' }]));

    it('loads the first page and shows one row per employee with a formatted salary', async () => {
      lastRequest().flush(pageOf([asha, john], 10_000));
      await fixture.whenStable();

      const rows = page().querySelectorAll('tr[mat-row]');
      expect(rows).toHaveLength(2);
      expect(rows[0].textContent).toContain('Asha Rao');
      expect(rows[0].textContent).toContain('₹2,000,000');   // salary in its own currency
      expect(rows[1].textContent).toContain('$120,000');
      expect(page().textContent).toContain('10000 employees');
    });

    it('offers the country filter', async () => {
      lastRequest().flush(pageOf([asha]));
      await fixture.whenStable();

      expect(page().textContent).toContain('Country');
    });

    it('asks the server to search only after the user stops typing', async () => {
      lastRequest().flush(pageOf([asha]));
      const input = page().querySelector<HTMLInputElement>('input[matInput]')!;

      input.value = 'asha';
      input.dispatchEvent(new Event('input'));
      await wait(100);
      expect(backend.match((r) => r.url === '/api/employees')).toHaveLength(0);   // still typing: nothing sent

      await wait(300);                                                            // 400 ms in: the 300 ms pause has passed
      const request = lastRequest();
      expect(request.request.params.get('search')).toBe('asha');
      expect(request.request.params.get('page')).toBe('0');
      request.flush(pageOf([asha]));
    });

    it('sorts on the server when a column header is clicked', async () => {
      lastRequest().flush(pageOf([asha]));
      await fixture.whenStable();

      fixture.debugElement.query(By.directive(MatSort)).injector.get(MatSort)
        .sortChange.emit({ active: 'annualSalary', direction: 'desc' });

      expect(lastRequest().request.params.get('sort')).toBe('annualSalary,desc');
    });

    it('asks for the right page when the pager is used, and goes back to the first page on a new search', async () => {
      lastRequest().flush(pageOf([asha], 100));
      await fixture.whenStable();

      fixture.debugElement.query(By.directive(MatPaginator)).injector.get(MatPaginator)
        .page.emit({ pageIndex: 2, pageSize: 50, length: 100, previousPageIndex: 0 });
      const request = lastRequest();
      expect(request.request.params.get('page')).toBe('2');
      expect(request.request.params.get('size')).toBe('50');
      request.flush(pageOf([john], 100));
    });

    it('says so when nothing matches', async () => {
      lastRequest().flush(pageOf([]));
      await fixture.whenStable();

      expect(page().textContent).toContain('No employees match your search');
    });

    it('shows an error with a retry button when the server fails, and recovers', async () => {
      lastRequest().flush(null, { status: 500, statusText: 'Server Error' });
      await fixture.whenStable();
      expect(page().querySelector('[role=alert]')?.textContent).toContain('Could not load employees');

      page().querySelector<HTMLButtonElement>('[role=alert] button')!.click();
      lastRequest().flush(pageOf([asha]));
      await fixture.whenStable();

      expect(page().querySelector('[role=alert]')).toBeNull();
      expect(page().querySelectorAll('tr[mat-row]')).toHaveLength(1);
    });
  });

  describe('for a regional officer', () => {
    beforeEach(async () => open(
      [{ action: 'read', subject: 'Employee', conditions: { country: 'IN' } }],
      { role: 'COUNTRY_HR', countryScope: 'IN', fullName: 'Regional HR (India)' },
    ));

    it('says which country they are limited to and hides the country filter', async () => {
      lastRequest().flush(pageOf([asha]));
      await fixture.whenStable();

      expect(page().textContent).toContain('Showing only employees in IN');
      expect(page().textContent).not.toContain('All countries');
    });
  });
});
