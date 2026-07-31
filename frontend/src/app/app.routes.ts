import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { ClinicalShellComponent } from './layout/clinical-shell.component';
import { LoginPageComponent } from './features/auth/login-page.component';
import { PatientWorkspacePageComponent } from './features/patients/patient-workspace-page.component';

export const routes: Routes = [
  {
    path: 'login',
    component: LoginPageComponent,
    title: 'Ingresar · HCOP AJP'
  },
  {
    path: '',
    component: ClinicalShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'patients' },
      { path: 'patients', component: PatientWorkspacePageComponent, title: 'Pacientes · HCOP AJP' },
      { path: 'patients/:patientId', component: PatientWorkspacePageComponent, title: 'Paciente · HCOP AJP' }
    ]
  },
  { path: '**', redirectTo: 'patients' }
];
