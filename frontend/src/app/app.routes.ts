import { CanDeactivateFn, Routes } from '@angular/router';
import { pendingClinicalDraftGuard } from './core/patients/pending-clinical-draft.guard';
import { authenticatedGuard } from './core/auth/authenticated.guard';

const pendingConfigurationChangesGuard: CanDeactivateFn<{ canDeactivate: () => boolean }> = (component) =>
  component.canDeactivate();

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login-page.component').then((module) => module.LoginPageComponent),
    title: 'Ingresar · HCOP Centro Oncologico'
  },
  {
    path: 'configuration',
    canActivate: [authenticatedGuard],
    canDeactivate: [pendingConfigurationChangesGuard],
    loadComponent: () => import('./features/configuration/configuration-hub').then((module) => module.ConfigurationHubComponent),
    title: 'Configuración · HCOP Centro Oncologico'
  },
  {
    path: 'help',
    canActivate: [authenticatedGuard],
    loadComponent: () => import('./features/help').then((module) => module.HelpCenterComponent),
    title: 'Ayuda · HCOP Centro Oncologico'
  },
  {
    path: 'herramientas',
    loadComponent: () => import('./layout/clinical-shell.component').then((module) => module.ClinicalShellComponent),
    canActivate: [authenticatedGuard],
    canDeactivate: [pendingClinicalDraftGuard],
    data: { initialPane: 'tools' },
    title: 'Herramientas · HCOP Centro Oncologico'
  },
  {
    path: '',
    loadComponent: () => import('./layout/clinical-shell.component').then((module) => module.ClinicalShellComponent),
    canActivate: [authenticatedGuard],
    canDeactivate: [pendingClinicalDraftGuard],
    title: 'HCOP Centro Oncologico'
  },
  { path: '**', redirectTo: '' }
];
