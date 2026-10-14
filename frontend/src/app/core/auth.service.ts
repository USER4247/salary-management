import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { createMongoAbility, RawRuleOf, MongoAbility, subject } from '@casl/ability';
import { catchError, finalize, firstValueFrom, Observable, of, shareReplay, tap } from 'rxjs';

export type Role = 'SUPERADMIN' | 'HR_MANAGER' | 'HR_VIEWER' | 'COUNTRY_HR';

export interface User {
  id: number;
  email: string;
  fullName: string;
  role: Role;
  countryScope: string | null;
}

/** What the server returns from login, refresh and /me. The tokens themselves stay in HttpOnly cookies. */
export interface SessionResponse {
  user: User;
  accessExpiresAt: string;
  rules: RawRuleOf<MongoAbility>[];
}

/** How much of an action the user may do: all of it, only within their country, or none. */
export type Access = 'yes' | 'limited' | 'no';

/** Refresh this long before the access token ends, so users never hit an expired token. */
const REFRESH_EARLY_MS = 60_000;

/**
 * Everything about "who is signed in": sign in, sign out, keeping the session alive, and what the user may do.
 *
 * The browser holds the tokens (in cookies that scripts cannot read). This service only knows the user,
 * when the access token ends, and the permission rules sent by the server.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  /** The signed-in user, or null. Components read this signal. */
  readonly user = signal<User | null>(null);
  readonly isSignedIn = computed(() => this.user() !== null);

  // CASL decides "can the user do X?". It is filled with the rules the server sends.
  // This is for showing or hiding things only; the server enforces the real permissions.
  private readonly ability = createMongoAbility();
  private readonly rulesVersion = signal(0);   // bumps when rules change so templates re-evaluate can()

  private refreshInFlight?: Observable<SessionResponse>;
  private refreshTimer?: ReturnType<typeof setTimeout>;

  /** Lets several open tabs sign in and out together. */
  private readonly channel = typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel('salary-auth');

  constructor() {
    this.channel?.addEventListener('message', (event) => {
      if (event.data === 'signed-out') {
        this.clearLocalSession();
        void this.router.navigate(['/sign-in']);
      } else if (event.data === 'signed-in') {
        void this.restore();
      }
    });
  }

  /** Runs once when the app starts: are we still signed in from before? */
  async restore(): Promise<void> {
    await firstValueFrom(
      this.http.get<SessionResponse>('/api/auth/me').pipe(
        tap((session) => this.applySession(session)),
        // The access token may just have expired: try to get a new one with the refresh cookie.
        catchError(() => this.refresh()),
        catchError(() => of(null)),   // not signed in; that is fine
      ),
    );
  }

  signIn(email: string, password: string): Observable<SessionResponse> {
    return this.http.post<SessionResponse>('/api/auth/login', { email, password }).pipe(
      tap((session) => {
        this.applySession(session);
        this.channel?.postMessage('signed-in');
      }),
    );
  }

  /**
   * Gets a new access token using the refresh cookie. If several requests fail at the same moment, they all
   * share one refresh call: the refresh token works only once, so a second call would fail.
   */
  refresh(): Observable<SessionResponse> {
    this.refreshInFlight ??= this.http.post<SessionResponse>('/api/auth/refresh', {}).pipe(
      tap((session) => this.applySession(session)),
      finalize(() => (this.refreshInFlight = undefined)),
      shareReplay(1),
    );
    return this.refreshInFlight;
  }

  async signOut(): Promise<void> {
    // Tell the server to forget the refresh token; even if that fails we still sign out here.
    await firstValueFrom(this.http.post('/api/auth/logout', {}).pipe(catchError(() => of(null))));
    this.channel?.postMessage('signed-out');
    this.expire();
  }

  /** The session is over (signed out, or refreshing failed): forget everything and go to the sign-in page. */
  expire(): void {
    this.clearLocalSession();
    void this.router.navigate(['/sign-in']);
  }

  /** Can the user do this at all? e.g. can('read', 'Employee'). */
  can(action: string, subjectName: string): boolean {
    this.rulesVersion();   // reading the signal makes templates update when rules change
    return this.ability.can(action, subjectName);
  }

  /** Same as can(), but tells "yes" apart from "only in my country". */
  access(action: string, subjectName: string): Access {
    if (!this.can(action, subjectName)) {
      return 'no';
    }
    // If a country the user does not belong to is refused, the rule must be conditional.
    const anywhere = this.ability.can(action, subject(subjectName, { country: '__another_country__' }));
    return anywhere ? 'yes' : 'limited';
  }

  private applySession(session: SessionResponse): void {
    this.user.set(session.user);
    this.ability.update(session.rules);
    this.rulesVersion.update((v) => v + 1);
    this.scheduleRefresh(session.accessExpiresAt);
  }

  /** Refreshes quietly in the background shortly before the access token ends. */
  private scheduleRefresh(accessExpiresAt: string): void {
    clearTimeout(this.refreshTimer);
    const waitMs = Math.max(new Date(accessExpiresAt).getTime() - Date.now() - REFRESH_EARLY_MS, 1_000);
    this.refreshTimer = setTimeout(() => {
      this.refresh().subscribe({ error: () => this.expire() });
    }, waitMs);
  }

  private clearLocalSession(): void {
    clearTimeout(this.refreshTimer);
    this.user.set(null);
    this.ability.update([]);
    this.rulesVersion.update((v) => v + 1);
  }
}
