import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { COUNTRIES, COUNTRY_CURRENCY, CURRENCIES, DEPARTMENTS, STATUSES } from '../core/reference-data';
import { Employee, EmployeeInput, EmployeeService } from './employee.service';

/** What the list gives the form: the employee to edit (none = add a new one) and the country a regional officer is limited to. */
export interface EmployeeFormData {
  employee?: Employee;
  limitedToCountry: string | null;
}

/**
 * The add / edit form, shown in a dialog. Checks the basics in the browser for quick feedback; the server checks
 * everything again, and any message it sends back is shown under the matching field.
 */
@Component({
  selector: 'app-employee-form',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ data.employee ? 'Edit employee' : 'Add employee' }}</h2>

    <form [formGroup]="form" (ngSubmit)="save()">
      <mat-dialog-content class="grid">
        <mat-form-field appearance="outline" class="wide">
          <mat-label>Full name</mat-label>
          <input matInput formControlName="fullName" />
          <mat-error>{{ message('fullName') }}</mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline" class="wide">
          <mat-label>Email</mat-label>
          <input matInput type="email" formControlName="email" />
          <mat-error>{{ message('email') }}</mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Country</mat-label>
          <mat-select formControlName="country" (selectionChange)="useCurrencyOf($event.value)">
            @for (country of countries; track country.code) {
              <mat-option [value]="country.code">{{ country.name }}</mat-option>
            }
          </mat-select>
          <mat-error>{{ message('country') }}</mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Department</mat-label>
          <mat-select formControlName="department">
            @for (department of departments; track department) {
              <mat-option [value]="department">{{ department }}</mat-option>
            }
          </mat-select>
          <mat-error>{{ message('department') }}</mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline" class="wide">
          <mat-label>Job title</mat-label>
          <input matInput formControlName="jobTitle" />
          <mat-error>{{ message('jobTitle') }}</mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Annual salary</mat-label>
          <input matInput type="number" formControlName="annualSalary" />
          <mat-error>{{ message('annualSalary') }}</mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Currency</mat-label>
          <mat-select formControlName="currency">
            @for (currency of currencies; track currency) {
              <mat-option [value]="currency">{{ currency }}</mat-option>
            }
          </mat-select>
          <mat-error>{{ message('currency') }}</mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Hire date</mat-label>
          <input matInput type="date" formControlName="hireDate" />
          <mat-error>{{ message('hireDate') }}</mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Status</mat-label>
          <mat-select formControlName="status">
            @for (status of statuses; track status.value) {
              <mat-option [value]="status.value">{{ status.label }}</mat-option>
            }
          </mat-select>
          <mat-error>{{ message('status') }}</mat-error>
        </mat-form-field>

        @if (generalError()) {
          <p class="error wide" role="alert">{{ generalError() }}</p>
        }
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancel</button>
        <button mat-flat-button type="submit" [disabled]="saving()">{{ saving() ? 'Saving...' : 'Save' }}</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px; min-width: min(560px, 80vw); }
    .wide { grid-column: 1 / -1; }
    .error { color: var(--mat-sys-error); margin: 0; }
  `,
})
export class EmployeeForm {
  protected readonly data = inject<EmployeeFormData>(MAT_DIALOG_DATA);
  private readonly dialog = inject<MatDialogRef<EmployeeForm, Employee>>(MatDialogRef);
  private readonly employeeService = inject(EmployeeService);

  protected readonly departments = DEPARTMENTS;
  protected readonly currencies = CURRENCIES;
  protected readonly statuses = STATUSES;
  /** A regional officer can only pick their own country. */
  protected readonly countries = this.data.limitedToCountry
    ? COUNTRIES.filter((country) => country.code === this.data.limitedToCountry)
    : COUNTRIES;

  protected readonly saving = signal(false);
  protected readonly generalError = signal('');

  protected readonly form = inject(FormBuilder).nonNullable.group({
    fullName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    country: [this.data.limitedToCountry ?? '', Validators.required],
    department: ['', Validators.required],
    jobTitle: ['', Validators.required],
    annualSalary: [0, [Validators.required, Validators.min(1)]],
    currency: [COUNTRY_CURRENCY[this.data.limitedToCountry ?? ''] ?? 'USD', Validators.required],
    hireDate: ['', Validators.required],
    status: ['ACTIVE', Validators.required],
  });

  constructor() {
    if (this.data.employee) {
      const { id: _id, ...fields } = this.data.employee;
      this.form.setValue(fields);
    }
  }

  /** Picking a country pre-fills the usual currency for it (it can still be changed). */
  protected useCurrencyOf(country: string): void {
    this.form.controls.currency.setValue(COUNTRY_CURRENCY[country] ?? this.form.controls.currency.value);
  }

  protected save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }
    this.saving.set(true);
    this.generalError.set('');

    const input: EmployeeInput = this.form.getRawValue();
    const request = this.data.employee
      ? this.employeeService.update(this.data.employee.id, input)
      : this.employeeService.create(input);

    request.subscribe({
      next: (saved) => this.dialog.close(saved),
      error: (error: HttpErrorResponse) => {
        this.saving.set(false);
        this.showServerErrors(error);
      },
    });
  }

  /** Puts each message from the server under its own field; anything else becomes a general message. */
  private showServerErrors(error: HttpErrorResponse): void {
    const fieldErrors: Record<string, string> = error.error?.errors ?? {};
    let shown = false;
    for (const [field, message] of Object.entries(fieldErrors)) {
      const control = this.form.get(field);
      if (control) {
        control.setErrors({ server: message });
        control.markAsTouched();
        shown = true;
      }
    }
    if (!shown) {
      this.generalError.set(error.status === 403
        ? 'You are not allowed to make this change.'
        : 'Could not save. Please try again.');
    }
  }

  protected message(field: keyof typeof this.form.controls): string {
    const errors = this.form.controls[field].errors;
    if (!errors) return '';
    if (errors['server']) return errors['server'];
    if (errors['email']) return 'Enter a valid email address';
    if (errors['min']) return 'Must be more than 0';
    return 'This is required';
  }
}
