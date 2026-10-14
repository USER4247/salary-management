import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * If an API call fails with 401 ("not signed in"), the access token has probably just expired.
 * Get a new one with the refresh cookie and repeat the call once; the user never notices.
 * If refreshing fails too, the session is really over, so sign the user out.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);

  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      // Login, refresh, logout and /me handle their own 401s; retrying them would loop.
      const isAuthCall = request.url.startsWith('/api/auth/');
      if (error.status !== 401 || isAuthCall) {
        return throwError(() => error);
      }
      return auth.refresh().pipe(
        switchMap(() => next(request)),   // try the original call again, now with the new cookie
        catchError((refreshError) => {
          auth.expire();
          return throwError(() => refreshError);
        }),
      );
    }),
  );
};
