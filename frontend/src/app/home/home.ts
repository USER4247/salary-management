import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Access, AuthService } from '../core/auth.service';

/**
 * The page you land on after signing in. For now it shows who you are and what your role allows,
 * which doubles as a live demo of the permission rules sent by the server.
 */
@Component({
  selector: 'app-home',
  imports: [MatToolbarModule, MatButtonModule, MatCardModule],
  template: `
    <mat-toolbar>
      <span>Salary Management</span>
      <span class="spacer"></span>
      @if (auth.user(); as user) {
        <span class="who">{{ user.fullName }} <small>({{ user.role }})</small></span>
      }
      <button mat-button type="button" (click)="auth.signOut()">Sign out</button>
    </mat-toolbar>

    <main class="content">
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
          <p class="note">Employee lists and pay insights are coming next.</p>
        </mat-card-content>
      </mat-card>
    </main>
  `,
  styles: `
    .spacer { flex: 1; }
    .who { margin-right: 8px; }
    .content { max-width: 720px; margin: 24px auto; padding: 0 16px; }
    table { border-collapse: collapse; margin: 16px 0; }
    th, td { padding: 8px 16px; text-align: left; }
    td.yes { color: var(--mat-sys-primary); font-weight: 500; }
    td.limited { color: var(--mat-sys-tertiary); font-weight: 500; }
    td.no { color: var(--mat-sys-outline); }
    .note { color: var(--mat-sys-on-surface-variant); }
  `,
})
export class Home {
  protected readonly auth = inject(AuthService);
  protected readonly actions = ['read', 'create', 'update', 'delete'];
  protected readonly subjects = ['Employee', 'Insights'];

  protected scope(): string | null {
    return this.auth.user()?.countryScope ?? null;
  }

  protected label(access: Access): string {
    return { yes: 'Yes', limited: 'Own country only', no: 'No' }[access];
  }
}
