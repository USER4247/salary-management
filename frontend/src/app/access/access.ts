import { Component, inject } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { Access as AccessLevel, AuthService } from '../core/auth.service';

/** "My access": what the signed-in role may do, read live from the permission rules sent by the server. */
@Component({
  selector: 'app-access',
  imports: [MatCardModule],
  template: `
    <mat-card>
      <mat-card-header>
        <mat-card-title>What you can do</mat-card-title>
        <mat-card-subtitle>
          {{ scope() ? 'Limited to country ' + scope() : 'Based on your role' }}
        </mat-card-subtitle>
      </mat-card-header>
      <mat-card-content>
        <table>
          <thead>
            <tr><th></th>@for (subject of subjects; track subject) { <th>{{ subject }}</th> }</tr>
          </thead>
          <tbody>
            @for (action of actions; track action) {
              <tr>
                <th scope="row">{{ action }}</th>
                @for (subject of subjects; track subject) {
                  <td [class]="auth.access(action, subject)">{{ label(auth.access(action, subject)) }}</td>
                }
              </tr>
            }
          </tbody>
        </table>
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    table { border-collapse: collapse; margin: 16px 0; }
    th, td { padding: 8px 16px; text-align: left; }
    td.yes { color: var(--mat-sys-primary); font-weight: 500; }
    td.limited { color: var(--mat-sys-tertiary); font-weight: 500; }
    td.no { color: var(--mat-sys-outline); }
  `,
})
export class Access {
  protected readonly auth = inject(AuthService);
  protected readonly actions = ['read', 'create', 'update', 'delete'];
  protected readonly subjects = ['Employee', 'Insights'];

  protected scope(): string | null {
    return this.auth.user()?.countryScope ?? null;
  }

  protected label(access: AccessLevel): string {
    return { yes: 'Yes', limited: 'Own country only', no: 'No' }[access];
  }
}
