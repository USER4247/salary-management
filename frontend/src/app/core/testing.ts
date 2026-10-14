import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { EnvironmentProviders, Provider } from '@angular/core';
import { provideRouter } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { SessionResponse } from './auth.service';

/** Shared helpers for the auth tests. */

export const READ_EMPLOYEES = { action: 'read', subject: 'Employee' } as const;

/** A server response for a signed-in user whose access token ends in `minutes` minutes. */
export function sessionFor(
  rules: SessionResponse['rules'] = [READ_EMPLOYEES],
  minutes = 15,
  user: Partial<SessionResponse['user']> = {},
): SessionResponse {
  return {
    user: { id: 1, email: 'maya@acme.test', fullName: 'Maya Manager', role: 'HR_MANAGER', countryScope: null, ...user },
    accessExpiresAt: new Date(Date.now() + minutes * 60_000).toISOString(),
    rules,
  };
}

/** The same HTTP setup as the real app (including the interceptor), but with requests we can answer by hand. */
export function authTestProviders(): (Provider | EnvironmentProviders)[] {
  return [
    provideHttpClient(withInterceptors([authInterceptor])),
    provideHttpClientTesting(),
    provideRouter([]),
  ];
}
