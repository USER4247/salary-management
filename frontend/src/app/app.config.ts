import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth.interceptor';
import { AuthService } from './core/auth.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    // HttpClient also copies the XSRF-TOKEN cookie into the X-XSRF-TOKEN header on POST/PUT/DELETE by itself.
    provideHttpClient(withInterceptors([authInterceptor])),
    // Before the first page shows, find out whether the user is still signed in.
    provideAppInitializer(() => inject(AuthService).restore()),
  ],
};
