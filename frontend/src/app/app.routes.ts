import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: 'sign-in', canActivate: [guestGuard], loadComponent: () => import('./sign-in/sign-in').then((m) => m.SignIn) },
  {
    // Everything after sign-in lives inside the shell (toolbar + page).
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./shell/shell').then((m) => m.Shell),
    children: [
      { path: '', loadComponent: () => import('./employees/employee-list').then((m) => m.EmployeeList) },
      { path: 'access', loadComponent: () => import('./access/access').then((m) => m.Access) },
    ],
  },
  { path: '**', redirectTo: '' },
];
