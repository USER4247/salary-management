import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { catchError, debounceTime, distinctUntilChanged, of, Subject, switchMap, tap } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { COUNTRIES, DEPARTMENTS, STATUSES } from '../core/reference-data';
import { ConfirmDialog } from '../shared/confirm-dialog';
import { EmployeeForm } from './employee-form';
import { Employee, EmployeePage, EmployeeQuery, EmployeeService } from './employee.service';

/**
 * The employee list: search, filters, sorting and paging. The server does all of that work, so the browser only
 * ever holds one page (never all 10,000 people). What you can see is limited by the server according to your role.
 */
@Component({
  selector: 'app-employee-list',
  imports: [
    ReactiveFormsModule, MatCardModule, MatTableModule, MatSortModule, MatPaginatorModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatButtonModule, MatProgressBarModule,
  ],
  template: `
    <mat-card>
      <mat-card-header>
        <mat-card-title>Employees</mat-card-title>
        <mat-card-subtitle>
          @if (limitedTo(); as country) {
            Showing only employees in {{ country }}, the region you are responsible for
          } @else {
            {{ page()?.totalElements ?? 0 }} employees
          }
        </mat-card-subtitle>
        @if (auth.can('create', 'Employee')) {
          <button mat-flat-button type="button" class="add" (click)="add()">Add employee</button>
        }
      </mat-card-header>

      <mat-card-content>
        <div class="filters">
          <mat-form-field appearance="outline" class="search">
            <mat-label>Search name, email or job title</mat-label>
            <input matInput [formControl]="searchControl" />
          </mat-form-field>

          @if (!limitedTo()) {
            <mat-form-field appearance="outline">
              <mat-label>Country</mat-label>
              <mat-select [value]="query().country" (selectionChange)="changeFilter('country', $event.value)">
                <mat-option value="">All countries</mat-option>
                @for (country of countries; track country.code) {
                  <mat-option [value]="country.code">{{ country.name }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
          }

          <mat-form-field appearance="outline">
            <mat-label>Department</mat-label>
            <mat-select [value]="query().department" (selectionChange)="changeFilter('department', $event.value)">
              <mat-option value="">All departments</mat-option>
              @for (department of departments; track department) {
                <mat-option [value]="department">{{ department }}</mat-option>
              }
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Status</mat-label>
            <mat-select [value]="query().status" (selectionChange)="changeFilter('status', $event.value)">
              <mat-option value="">Any status</mat-option>
              @for (status of statuses; track status.value) {
                <mat-option [value]="status.value">{{ status.label }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        </div>

        @if (loading()) {
          <mat-progress-bar mode="indeterminate" />
        }

        @if (failed()) {
          <p class="error" role="alert">
            Could not load employees.
            <button mat-button type="button" (click)="reload()">Try again</button>
          </p>
        }

        <div class="table-wrap">
          <table mat-table [dataSource]="rows()" matSort [matSortActive]="query().sort" [matSortDirection]="query().direction"
                 (matSortChange)="changeSort($event)">
            <ng-container matColumnDef="fullName">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>Name</th>
              <td mat-cell *matCellDef="let e">{{ e.fullName }}<div class="sub">{{ e.email }}</div></td>
            </ng-container>
            <ng-container matColumnDef="country">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>Country</th>
              <td mat-cell *matCellDef="let e">{{ e.country }}</td>
            </ng-container>
            <ng-container matColumnDef="department">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>Department</th>
              <td mat-cell *matCellDef="let e">{{ e.department }}</td>
            </ng-container>
            <ng-container matColumnDef="jobTitle">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>Job title</th>
              <td mat-cell *matCellDef="let e">{{ e.jobTitle }}</td>
            </ng-container>
            <ng-container matColumnDef="annualSalary">
              <th mat-header-cell *matHeaderCellDef mat-sort-header class="number">Annual salary</th>
              <td mat-cell *matCellDef="let e" class="number">{{ money(e) }}</td>
            </ng-container>
            <ng-container matColumnDef="hireDate">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>Hired</th>
              <td mat-cell *matCellDef="let e">{{ e.hireDate }}</td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>Status</th>
              <td mat-cell *matCellDef="let e"><span class="status {{ e.status }}">{{ statusLabel(e.status) }}</span></td>
            </ng-container>

            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let e" class="number">
                @if (auth.can('update', 'Employee')) {
                  <button mat-button type="button" (click)="edit(e)" [attr.aria-label]="'Edit ' + e.fullName">Edit</button>
                }
                @if (auth.can('delete', 'Employee')) {
                  <button mat-button type="button" (click)="remove(e)" [attr.aria-label]="'Delete ' + e.fullName">Delete</button>
                }
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns"></tr>
          </table>

          @if (!loading() && !failed() && rows().length === 0) {
            <p class="empty">No employees match your search.</p>
          }
        </div>

        <mat-paginator [length]="page()?.totalElements ?? 0" [pageSize]="query().size" [pageIndex]="query().page"
                       [pageSizeOptions]="[10, 25, 50, 100]" (page)="changePage($event)" showFirstLastButtons />
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    mat-card-header { align-items: center; }
    .add { margin-left: auto; }
    .filters { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 16px; }
    .search { flex: 1 1 280px; }
    .table-wrap { overflow-x: auto; }
    table { width: 100%; }
    .number { text-align: right; }
    .sub { font: var(--mat-sys-body-small); color: var(--mat-sys-on-surface-variant); }
    .error { color: var(--mat-sys-error); }
    .empty { padding: 24px; text-align: center; color: var(--mat-sys-on-surface-variant); }
    .status.ACTIVE { color: var(--mat-sys-primary); }
    .status.ON_LEAVE { color: var(--mat-sys-tertiary); }
    .status.TERMINATED { color: var(--mat-sys-outline); }
  `,
})
export class EmployeeList {
  private readonly employeeService = inject(EmployeeService);
  protected readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly countries = COUNTRIES;
  protected readonly departments = DEPARTMENTS;
  protected readonly statuses = STATUSES;
  protected readonly columns = ['fullName', 'country', 'department', 'jobTitle', 'annualSalary', 'hireDate', 'status', 'actions'];

  protected readonly searchControl = new FormControl('', { nonNullable: true });
  protected readonly query = signal<EmployeeQuery>({
    page: 0, size: 25, sort: 'fullName', direction: 'asc', search: '', country: '', department: '', status: '',
  });
  protected readonly page = signal<EmployeePage | null>(null);
  protected readonly rows = signal<Employee[]>([]);
  protected readonly loading = signal(false);
  protected readonly failed = signal(false);

  /** Every change of the query is pushed here. switchMap drops the answer to an older, now-outdated request. */
  private readonly requests = new Subject<EmployeeQuery>();

  constructor() {
    this.requests
      .pipe(
        tap(() => { this.loading.set(true); this.failed.set(false); }),
        switchMap((query) => this.employeeService.list(query).pipe(
          catchError(() => { this.failed.set(true); return of(null); }),
        )),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((page) => {
        this.loading.set(false);
        if (page) {
          this.page.set(page);
          this.rows.set(page.content);
        }
      });

    // Wait until the user stops typing for 300 ms before asking the server.
    this.searchControl.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(inject(DestroyRef)))
      .subscribe((text) => this.update({ search: text, page: 0 }));

    this.requests.next(this.query());
  }

  /** The country a regional officer is limited to, or null for people who may see every country. */
  protected limitedTo(): string | null {
    return this.auth.access('read', 'Employee') === 'limited' ? this.auth.user()?.countryScope ?? null : null;
  }

  protected changeFilter(name: 'country' | 'department' | 'status', value: string): void {
    this.update({ [name]: value, page: 0 });
  }

  protected changeSort(sort: Sort): void {
    // A third click on a column clears the sort (direction ''); fall back to the default order.
    this.update(sort.direction
      ? { sort: sort.active, direction: sort.direction, page: 0 }
      : { sort: 'fullName', direction: 'asc', page: 0 });
  }

  protected changePage(event: PageEvent): void {
    this.update({ page: event.pageIndex, size: event.pageSize });
  }

  protected reload(): void {
    this.requests.next(this.query());
  }

  protected add(): void {
    this.openForm({ limitedToCountry: this.limitedTo() }, 'Employee added');
  }

  protected edit(employee: Employee): void {
    this.openForm({ employee, limitedToCountry: this.limitedTo() }, 'Employee updated');
  }

  /** Asks first, then deletes on the server and refreshes the list. */
  protected remove(employee: Employee): void {
    this.dialog.open(ConfirmDialog, {
      data: { title: 'Delete employee?', message: `${employee.fullName} will be removed permanently.`, confirmLabel: 'Delete' },
    }).afterClosed().subscribe((confirmed) => {
      if (!confirmed) return;
      this.employeeService.remove(employee.id).subscribe({
        next: () => { this.snackBar.open('Employee deleted', 'OK', { duration: 3000 }); this.reload(); },
        error: () => this.snackBar.open('Could not delete the employee', 'OK', { duration: 5000 }),
      });
    });
  }

  private openForm(data: { employee?: Employee; limitedToCountry: string | null }, successMessage: string): void {
    this.dialog.open(EmployeeForm, { data, autoFocus: 'first-tabbable' }).afterClosed().subscribe((saved) => {
      if (saved) {   // closed with a saved employee (not cancelled)
        this.snackBar.open(successMessage, 'OK', { duration: 3000 });
        this.reload();
      }
    });
  }

  protected money(employee: Employee): string {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency: employee.currency, maximumFractionDigits: 0 })
      .format(employee.annualSalary);
  }

  protected statusLabel(value: string): string {
    return STATUSES.find((s) => s.value === value)?.label ?? value;
  }

  private update(change: Partial<EmployeeQuery>): void {
    this.query.update((current) => ({ ...current, ...change }));
    this.requests.next(this.query());
  }
}
