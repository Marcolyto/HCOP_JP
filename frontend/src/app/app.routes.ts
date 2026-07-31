import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { ClinicalShellComponent } from './layout/clinical-shell.component';
import { LoginPageComponent } from './features/auth/login-page.component';
import { PatientHistoryPageComponent } from './features/clinical-history/patient-history-page.component';
import { PatientHistoryEditorPageComponent } from './features/clinical-history-editor/patient-history-editor-page.component';
import { PatientDiagnosisEditorPageComponent } from './features/diagnosis/patient-diagnosis-editor-page.component';
import { PatientStudiesPageComponent } from './features/studies/patient-studies-page.component';
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
      { path: 'patients/:patientId/diagnosis/new', component: PatientDiagnosisEditorPageComponent, title: 'Agregar diagnóstico · HCOP AJP' },
      { path: 'patients/:patientId/studies', component: PatientStudiesPageComponent, title: 'Estudios · HCOP AJP' },
      { path: 'patients/:patientId/history/edit', component: PatientHistoryEditorPageComponent, title: 'Editar historia clínica · HCOP AJP' },
      { path: 'patients/:patientId/history', component: PatientHistoryPageComponent, title: 'Historia clínica · HCOP AJP' },
      { path: 'patients/:patientId', component: PatientWorkspacePageComponent, title: 'Paciente · HCOP AJP' }
    ]
  },
  { path: '**', redirectTo: 'patients' }
];
