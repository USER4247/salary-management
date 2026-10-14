import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { COUNTRIES } from '../core/reference-data';

/** Demo accounts for the assessment build. The password is public on purpose so reviewers can sign in. */
const DEMO_PASSWORD = 'Demo@12345';
const DEMO_ACCOUNTS = [
  { label: 'Superadmin', email: 'superadmin@acme.test' },
  { label: 'HR Manager', email: 'manager@acme.test' },
  { label: 'HR Viewer', email: 'viewer@acme.test' },
];

@Component({
  selector: 'app-sign-in',
  imports: [ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatChipsModule],
  template: `
    <main class="page">
      <mat-card class="card">
        <mat-card-header>
          <mat-card-title>Salary Management</mat-card-title>
          <mat-card-subtitle>Sign in to continue</mat-card-subtitle>
        </mat-card-header>

        <mat-card-content>
          <form [formGroup]="form" (ngSubmit)="submit()" class="form">
            <mat-form-field appearance="outline">
              <mat-label>Email</mat-label>
              <input matInput type="email" formControlName="email" autocomplete="username" />
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Password</mat-label>
              <input matInput type="password" formControlName="password" autocomplete="current-password" />
            </mat-form-field>

            @if (error()) {
              <p class="error" role="alert">{{ error() }}</p>
            }

            <button mat-flat-button type="submit" [disabled]="form.invalid || loading()">
              {{ loading() ? 'Signing in...' : 'Sign in' }}
            </button>
          </form>

          <section class="demo">
            <p>Demo accounts (click to fill, password <code>{{ demoPassword }}</code>):</p>
            <mat-chip-set>
              @for (account of demoAccounts; track account.email) {
                <mat-chip (click)="fillDemo(account.email)">{{ account.label }}</mat-chip>
              }
            </mat-chip-set>

            <mat-form-field appearance="outline" class="regional">
              <mat-label>Regional officer (sees one country only)</mat-label>
              <select matNativeControl (change)="fillRegional($any($event.target).value)">
                <option value="">Choose a country...</option>
                @for (country of countries; track country.code) {
                  <option [value]="country.code">{{ country.name }}</option>
                }
              </select>
            </mat-form-field>
          </section>
        </mat-card-content>
      </mat-card>
    </main>
  `,
  styles: `
    .page { min-height: 100vh; display: grid; place-items: center; padding: 16px; box-sizing: border-box; }
    .card { width: 100%; max-width: 420px; padding: 8px; }
    .form { display: flex; flex-direction: column; gap: 4px; margin-top: 16px; }
    .error { color: var(--mat-sys-error); margin: 0 0 8px; }
    .demo { margin-top: 24px; font: var(--mat-sys-body-medium); }
    mat-chip { cursor: pointer; }
    .regional { display: block; margin-top: 16px; }
  `,
})
export class SignIn {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly demoAccounts = DEMO_ACCOUNTS;
  protected readonly countries = COUNTRIES;
  protected readonly demoPassword = DEMO_PASSWORD;
  protected readonly loading = signal(false);
  protected readonly error = signal('');

  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  protected fillDemo(email: string): void {
    this.form.setValue({ email, password: DEMO_PASSWORD });
  }

  /** The backend creates one regional officer per country: hr.<country code>@acme.test. */
  protected fillRegional(countryCode: string): void {
    if (countryCode) {
      this.fillDemo(`hr.${countryCode.toLowerCase()}@acme.test`);
    }
  }

  protected submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.loading.set(true);
    this.error.set('');

    const { email, password } = this.form.getRawValue();
    this.auth.signIn(email, password).subscribe({
      next: () => {
        // Go back to the page the user wanted before being asked to sign in (or home).
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/';
        void this.router.navigateByUrl(returnUrl.startsWith('/') ? returnUrl : '/');
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(this.messageFor(err));
      },
    });
  }

  private messageFor(err: HttpErrorResponse): string {
    if (err.status === 401) return 'Wrong email or password.';
    if (err.status === 429) return 'Too many attempts. Please wait a minute and try again.';
    return 'Something went wrong. Please try again.';
  }
}
