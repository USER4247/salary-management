import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: 'sign-in', canActivate: [guestGuard], loadComponent: () => import('./sign-in/sign-in').then((m) => m.SignIn) },
  { path: '', canActivate: [authGuard], loadComponent: () => import('./home/home').then((m) => m.Home) },
  { path: '**', redirectTo: '' },
];
