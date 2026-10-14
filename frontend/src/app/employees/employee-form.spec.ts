import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { authTestProviders } from '../core/testing';
import { EmployeeForm, EmployeeFormData } from './employee-form';
import { Employee } from './employee.service';

const asha: Employee = {
  id: 5, fullName: 'Asha Rao', email: 'asha@acme.test', country: 'IN', department: 'Engineering',
  jobTitle: 'Software Engineer', annualSalary: 2_000_000, currency: 'INR', hireDate: '2020-01-15', status: 'ACTIVE',
};

/** The form's fields, reached directly: simpler than typing into every input. */
interface Fields { form: { setValue(v: unknown): void; patchValue(v: unknown): void; value: Record<string, unknown>; controls: Record<string, { value: unknown }> }; }

describe('EmployeeForm', () => {
  let fixture: ComponentFixture<EmployeeForm>;
  let backend: HttpTestingController;
  const close = vi.fn();
  const fields = () => fixture.componentInstance as unknown as Fields;
  const page = () => fixture.nativeElement as HTMLElement;

  async function open(data: EmployeeFormData) {
    vi.stubGlobal('BroadcastChannel', undefined);
    TestBed.configureTestingModule({
      imports: [EmployeeForm],
      providers: [
        ...authTestProviders(),
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: { close } },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EmployeeForm);
    await fixture.whenStable();
  }

  const fillAll = () => fields().form.patchValue({
    fullName: 'New Person', email: 'new@acme.test', country: 'US', department: 'Finance', jobTitle: 'Accountant',
    annualSalary: 90_000, currency: 'USD', hireDate: '2024-03-01',
  });

  const save = async () => {
    page().querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();
  };

  afterEach(() => {
    close.mockClear();
    vi.unstubAllGlobals();
  });

  describe('adding', () => {
    it('does not send anything while required fields are empty', async () => {
      await open({ limitedToCountry: null });

      await save();

      backend.expectNone('/api/employees');
      expect(page().textContent).toContain('This is required');
    });

    it('sends the new employee to the server and closes with the saved one', async () => {
      await open({ limitedToCountry: null });
      fillAll();

      await save();
      const request = backend.expectOne('/api/employees');
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toMatchObject({ fullName: 'New Person', email: 'new@acme.test', annualSalary: 90_000, hireDate: '2024-03-01' });
      request.flush({ ...asha, id: 9 });

      expect(close).toHaveBeenCalledWith(expect.objectContaining({ id: 9 }));
    });

    it('rejects a salary of zero in the browser', async () => {
      await open({ limitedToCountry: null });
      fillAll();
      fields().form.patchValue({ annualSalary: 0 });

      await save();

      backend.expectNone('/api/employees');
      expect(page().textContent).toContain('Must be more than 0');
    });

    it('pre-fills the usual currency when a country is chosen', async () => {
      await open({ limitedToCountry: null });

      (fixture.componentInstance as unknown as { useCurrencyOf(c: string): void }).useCurrencyOf('JP');

      expect(fields().form.controls['currency'].value).toBe('JPY');
    });
  });

  describe('editing', () => {
    it('starts with the employee’s current values and updates the same employee', async () => {
      await open({ employee: asha, limitedToCountry: null });
      expect(fields().form.value['fullName']).toBe('Asha Rao');
      fields().form.patchValue({ annualSalary: 2_200_000 });

      await save();
      const request = backend.expectOne('/api/employees/5');
      expect(request.request.method).toBe('PUT');
      expect(request.request.body.annualSalary).toBe(2_200_000);
      request.flush({ ...asha, annualSalary: 2_200_000 });

      expect(close).toHaveBeenCalled();
    });
  });

  describe('when the server says no', () => {
    it('shows a duplicate email right under the email field and stays open', async () => {
      await open({ limitedToCountry: null });
      fillAll();

      await save();
      backend.expectOne('/api/employees').flush(
        { errors: { email: 'Another employee already uses this email' } }, { status: 409, statusText: 'Conflict' });
      await fixture.whenStable();

      expect(page().textContent).toContain('Another employee already uses this email');
      expect(close).not.toHaveBeenCalled();
      expect(page().querySelector<HTMLButtonElement>('button[type=submit]')!.disabled).toBe(false);   // can fix and retry
    });

    it('shows several field messages at once', async () => {
      await open({ limitedToCountry: null });
      fillAll();

      await save();
      backend.expectOne('/api/employees').flush(
        { errors: { hireDate: 'cannot be in the future', currency: 'Unsupported currency XYZ' } },
        { status: 400, statusText: 'Bad Request' });
      await fixture.whenStable();

      const text = page().textContent ?? '';
      expect(text).toContain('cannot be in the future');
      expect(text).toContain('Unsupported currency XYZ');
    });

    it('explains a refusal that has no field', async () => {
      await open({ limitedToCountry: null });
      fillAll();

      await save();
      backend.expectOne('/api/employees').flush(null, { status: 403, statusText: 'Forbidden' });
      await fixture.whenStable();

      expect(page().querySelector('[role=alert]')?.textContent).toContain('not allowed');
    });
  });

  it('limits a regional officer to their own country', async () => {
    await open({ limitedToCountry: 'IN' });

    expect(fields().form.value['country']).toBe('IN');
    expect(fields().form.value['currency']).toBe('INR');
    expect((fixture.componentInstance as unknown as { countries: { code: string }[] }).countries.map((c) => c.code)).toEqual(['IN']);
  });
});
