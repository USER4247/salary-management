import { HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MatDialog } from '@angular/material/dialog';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { of } from 'rxjs';
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

    it('asks for the newest employees first, and shows their IDs', async () => {
      const first = lastRequest();
      expect(first.request.params.get('sort')).toBe('id,desc');
      expect(first.request.params.get('page')).toBe('0');
      first.flush(pageOf([{ ...john, id: 10_001 }, asha]));
      await fixture.whenStable();

      const ids = Array.from(page().querySelectorAll('td.id')).map((cell) => cell.textContent?.trim());
      expect(ids).toEqual(['10001', '1']);
    });

    it('goes back to newest first when a column sort is cleared', async () => {
      lastRequest().flush(pageOf([asha]));
      await fixture.whenStable();

      fixture.debugElement.query(By.directive(MatSort)).injector.get(MatSort)
        .sortChange.emit({ active: 'annualSalary', direction: '' });

      expect(lastRequest().request.params.get('sort')).toBe('id,desc');
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

  describe('changing employees', () => {
    /** Stands in for the dialog box: pretend the user closed it with this result. */
    const dialogClosesWith = (result: unknown) =>
      vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({ afterClosed: () => of(result) } as never);
    const button = (label: string) =>
      Array.from(page().querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent?.includes(label));

    beforeEach(async () => open([{ action: 'manage', subject: 'all' }]));

    it('opens the add form and refreshes the list after saving', async () => {
      lastRequest().flush(pageOf([asha]));
      await fixture.whenStable();
      const open = dialogClosesWith({ ...asha, id: 9 });

      button('Add employee')!.click();

      expect(open).toHaveBeenCalledWith(expect.anything(), expect.objectContaining({ data: { limitedToCountry: null } }));
      expect(lastRequest()).toBeDefined();   // the list was asked for again
    });

    it('shows the new employee at the top: back to page one, newest first', async () => {
      lastRequest().flush(pageOf([asha], 100));
      await fixture.whenStable();
      // the user had moved to page 3 and sorted by name
      fixture.debugElement.query(By.directive(MatPaginator)).injector.get(MatPaginator)
        .page.emit({ pageIndex: 2, pageSize: 25, length: 100, previousPageIndex: 0 });
      lastRequest().flush(pageOf([asha], 100));
      fixture.debugElement.query(By.directive(MatSort)).injector.get(MatSort)
        .sortChange.emit({ active: 'fullName', direction: 'asc' });
      lastRequest().flush(pageOf([asha], 100));
      dialogClosesWith({ ...asha, id: 10_001 });

      button('Add employee')!.click();

      const request = lastRequest().request;
      expect(request.params.get('page')).toBe('0');
      expect(request.params.get('sort')).toBe('id,desc');
    });

    it('does not refresh when the form is cancelled', async () => {
      lastRequest().flush(pageOf([asha]));
      await fixture.whenStable();
      dialogClosesWith(undefined);

      button('Add employee')!.click();

      expect(backend.match((r) => r.url === '/api/employees')).toHaveLength(0);
    });

    it('opens the edit form with the chosen employee', async () => {
      lastRequest().flush(pageOf([asha]));
      await fixture.whenStable();
      const open = dialogClosesWith(undefined);

      button('Edit')!.click();

      expect(open).toHaveBeenCalledWith(expect.anything(), expect.objectContaining({ data: { employee: asha, limitedToCountry: null } }));
    });

    it('deletes only after the user confirms, then refreshes', async () => {
      lastRequest().flush(pageOf([asha]));
      await fixture.whenStable();
      dialogClosesWith(true);

      button('Delete')!.click();
      backend.expectOne('/api/employees/1').flush(null, { status: 204, statusText: 'No Content' });

      expect(lastRequest()).toBeDefined();
    });

    it('does not delete when the user cancels', async () => {
      lastRequest().flush(pageOf([asha]));
      await fixture.whenStable();
      dialogClosesWith(false);

      button('Delete')!.click();

      backend.expectNone('/api/employees/1');
    });
  });

  describe('for someone who may only read', () => {
    beforeEach(async () => open([{ action: 'read', subject: 'Employee' }]));

    it('shows no add, edit or delete buttons', async () => {
      lastRequest().flush(pageOf([asha]));
      await fixture.whenStable();

      const labels = Array.from(page().querySelectorAll('button')).map((b) => b.textContent?.trim());
      expect(labels).not.toContain('Add employee');
      expect(labels).not.toContain('Edit');
      expect(labels).not.toContain('Delete');
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
