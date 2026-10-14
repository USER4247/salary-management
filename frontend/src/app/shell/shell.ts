import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth.service';

/** The frame around every signed-in page: toolbar with navigation, the user and Sign out. */
@Component({
  selector: 'app-shell',
  imports: [MatToolbarModule, MatButtonModule, RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <mat-toolbar>
      <span class="title">Salary Management</span>
      <nav>
        <a mat-button routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">Employees</a>
        @if (auth.can('read', 'Insights')) {
          <a mat-button routerLink="/insights" routerLinkActive="active">Insights</a>
        }
        <a mat-button routerLink="/access" routerLinkActive="active">My access</a>
      </nav>
      <span class="spacer"></span>
      @if (auth.user(); as user) {
        <span class="who">{{ user.fullName }} <small>({{ user.role }})</small></span>
      }
      <button mat-button type="button" (click)="auth.signOut()">Sign out</button>
    </mat-toolbar>

    <main class="content"><router-outlet /></main>
  `,
  styles: `
    .title { margin-right: 16px; }
    .spacer { flex: 1; }
    .who { margin-right: 8px; }
    .active { background: var(--mat-sys-secondary-container); }
    .content { max-width: 1200px; margin: 24px auto; padding: 0 16px; }
  `,
})
export class Shell {
  protected readonly auth = inject(AuthService);
}
