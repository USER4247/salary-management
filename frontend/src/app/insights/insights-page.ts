import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { catchError, of, Subject, switchMap, tap } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { GroupBy, InsightsService, PayInsights, PayStats } from './insights.service';

/**
 * "How do we pay people?": totals and typical pay, overall and per country, department or job title.
 * The server does the calculations in US dollars; this page only displays them.
 */
@Component({
  selector: 'app-insights-page',
  imports: [MatCardModule, MatButtonToggleModule, MatFormFieldModule, MatSelectModule, MatTableModule, MatButtonModule, MatProgressBarModule],
  template: `
    <header class="head">
      <div>
        <h1>Pay insights</h1>
        <p class="note">
          @if (limitedTo(); as country) { Showing only {{ country }}, the region you are responsible for. }
          {{ data()?.assumption }}
        </p>
      </div>

      <div class="controls">
        <mat-button-toggle-group [value]="groupBy()" (change)="changeGroupBy($event.value)" aria-label="Group by">
          <mat-button-toggle value="country">Country</mat-button-toggle>
          <mat-button-toggle value="department">Department</mat-button-toggle>
          <mat-button-toggle value="job-title">Job title</mat-button-toggle>
        </mat-button-toggle-group>

        <mat-form-field appearance="outline" class="status">
          <mat-label>Employees</mat-label>
          <mat-select [value]="status()" (selectionChange)="changeStatus($event.value)">
            <mat-option value="ACTIVE">Active only</mat-option>
            <mat-option value="ON_LEAVE">On leave</mat-option>
            <mat-option value="TERMINATED">Terminated</mat-option>
            <mat-option value="ALL">Everyone</mat-option>
          </mat-select>
        </mat-form-field>
      </div>
    </header>

    @if (loading()) {
      <mat-progress-bar mode="indeterminate" />
    }
    @if (failed()) {
      <p class="error" role="alert">
        Could not load the insights.
        <button mat-button type="button" (click)="reload()">Try again</button>
      </p>
    }

    @if (data(); as insights) {
      <section class="cards">
        <mat-card><mat-card-content><div class="label">Employees</div><div class="value">{{ insights.overall.headcount.toLocaleString('en-US') }}</div></mat-card-content></mat-card>
        <mat-card><mat-card-content><div class="label">Total yearly payroll</div><div class="value">{{ usd(insights.overall.total, true) }}</div></mat-card-content></mat-card>
        <mat-card><mat-card-content><div class="label">Average salary</div><div class="value">{{ usd(insights.overall.average) }}</div></mat-card-content></mat-card>
        <mat-card><mat-card-content><div class="label">Median salary</div><div class="value">{{ usd(insights.overall.median) }}</div></mat-card-content></mat-card>
      </section>

      @if (rows().length === 0) {
        <p class="empty">No employees match.</p>
      } @else {
        <mat-card>
          <div class="table-wrap">
            <table mat-table [dataSource]="rows()">
              <ng-container matColumnDef="key">
                <th mat-header-cell *matHeaderCellDef>{{ groupLabel() }}</th>
                <td mat-cell *matCellDef="let row">{{ row.key }}</td>
              </ng-container>
              <ng-container matColumnDef="headcount">
                <th mat-header-cell *matHeaderCellDef class="number">Employees</th>
                <td mat-cell *matCellDef="let row" class="number">{{ row.headcount.toLocaleString('en-US') }}</td>
              </ng-container>
              <ng-container matColumnDef="average">
                <th mat-header-cell *matHeaderCellDef class="number">Average</th>
                <td mat-cell *matCellDef="let row" class="number">{{ usd(row.average) }}</td>
              </ng-container>
              <ng-container matColumnDef="bar">
                <th mat-header-cell *matHeaderCellDef>Average vs the highest group</th>
                <td mat-cell *matCellDef="let row"><div class="bar" [style.width.%]="barWidth(row)"></div></td>
              </ng-container>
              <ng-container matColumnDef="median">
                <th mat-header-cell *matHeaderCellDef class="number">Median</th>
                <td mat-cell *matCellDef="let row" class="number">{{ usd(row.median) }}</td>
              </ng-container>
              <ng-container matColumnDef="range">
                <th mat-header-cell *matHeaderCellDef class="number">Lowest to highest</th>
                <td mat-cell *matCellDef="let row" class="number">{{ usd(row.min) }} to {{ usd(row.max) }}</td>
              </ng-container>
              <ng-container matColumnDef="total">
                <th mat-header-cell *matHeaderCellDef class="number">Total payroll</th>
                <td mat-cell *matCellDef="let row" class="number">{{ usd(row.total, true) }}</td>
              </ng-container>

              <tr mat-header-row *matHeaderRowDef="columns"></tr>
              <tr mat-row *matRowDef="let row; columns: columns"></tr>
            </table>
          </div>
        </mat-card>
      }
    }
  `,
  styles: `
    .head { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 16px; align-items: flex-start; }
    h1 { margin: 0 0 4px; font: var(--mat-sys-headline-small); }
    .note { margin: 0; max-width: 640px; color: var(--mat-sys-on-surface-variant); font: var(--mat-sys-body-small); }
    .controls { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; }
    .status { width: 170px; margin-top: 18px; }
    .cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 16px; margin: 16px 0; }
    .label { color: var(--mat-sys-on-surface-variant); font: var(--mat-sys-label-large); }
    .value { font: var(--mat-sys-headline-medium); margin-top: 4px; }
    .table-wrap { overflow-x: auto; }
    table { width: 100%; }
    .number { text-align: right; }
    .bar { height: 10px; border-radius: 5px; background: var(--mat-sys-primary); min-width: 2px; }
    .error { color: var(--mat-sys-error); }
    .empty { padding: 24px; text-align: center; color: var(--mat-sys-on-surface-variant); }
  `,
})
export class InsightsPage {
  private readonly service = inject(InsightsService);
  private readonly auth = inject(AuthService);

  protected readonly columns = ['key', 'headcount', 'average', 'bar', 'median', 'range', 'total'];
  protected readonly groupBy = signal<GroupBy>('country');
  protected readonly status = signal('ACTIVE');
  protected readonly data = signal<PayInsights | null>(null);
  protected readonly loading = signal(false);
  protected readonly failed = signal(false);

  /** The groups, highest average pay first, so the biggest differences are at the top. */
  protected readonly rows = computed(() => [...(this.data()?.groups ?? [])].sort((a, b) => b.average - a.average));
  private readonly highestAverage = computed(() => Math.max(1, ...this.rows().map((row) => row.average)));

  /** Each change is pushed here; switchMap drops the answer to an older, now-outdated request. */
  private readonly requests = new Subject<void>();

  constructor() {
    this.requests
      .pipe(
        tap(() => { this.loading.set(true); this.failed.set(false); }),
        switchMap(() => this.service.pay(this.groupBy(), this.status()).pipe(
          catchError(() => { this.failed.set(true); return of(null); }),
        )),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((insights) => {
        this.loading.set(false);
        if (insights) {
          this.data.set(insights);
        }
      });
    this.requests.next();
  }

  protected limitedTo(): string | null {
    return this.auth.access('read', 'Insights') === 'limited' ? this.auth.user()?.countryScope ?? null : null;
  }

  protected groupLabel(): string {
    return { country: 'Country', department: 'Department', 'job-title': 'Job title' }[this.groupBy()];
  }

  protected changeGroupBy(value: GroupBy): void {
    this.groupBy.set(value);
    this.requests.next();
  }

  protected changeStatus(value: string): void {
    this.status.set(value);
    this.requests.next();
  }

  protected reload(): void {
    this.requests.next();
  }

  protected barWidth(row: PayStats): number {
    return (row.average / this.highestAverage()) * 100;
  }

  /** $85,261, or compact ($785M) for very large totals. */
  protected usd(amount: number, compact = false): string {
    const text = new Intl.NumberFormat('en-US', {
      style: 'currency', currency: 'USD', maximumFractionDigits: compact ? 1 : 0, notation: compact ? 'compact' : 'standard',
    }).format(amount);
    return text.replace(/\.0(?=[KMBT])/, '');   // "$30.0M" reads better as "$30M"
  }
}
