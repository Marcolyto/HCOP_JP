import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { ClinicalShellComponent } from './layout/clinical-shell.component';
import { LoginPageComponent } from './features/auth/login-page.component';
import { PatientHistoryPageComponent } from './features/clinical-history/patient-history-page.component';
import { PatientHistoryEditorPageComponent } from './features/clinical-history-editor/patient-history-editor-page.component';
import { PatientDiagnosisEditorPageComponent } from './features/diagnosis/patient-diagnosis-editor-page.component';
import { PatientStudiesPageComponent } from './features/studies/patient-studies-page.component';
import { PatientWorkspacePageComponent } from './features/patients/patient-workspace-page.component';
import { PatientCreatePageComponent } from './features/patients/patient-create-page.component';
import { PatientTreatmentCreatePageComponent } from './features/patients/patient-treatment-create-page.component';
import { PatientTreatmentsPageComponent } from './features/patients/patient-treatments-page.component';

export const routes: Routes = [
  { path: 'login', component: LoginPageComponent, title: 'Ingresar · HCOP AJP' },
  {
    path: '',
    component: ClinicalShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'patients' },
      { path: 'patients/new', component: PatientCreatePageComponent, title: 'Nuevo paciente - HCOP AJP' },
      { path: 'hospital-day/pharmacy', loadComponent: () => import('./features/day-hospital/pharmacy-queue-page.component').then((m) => m.PharmacyQueuePageComponent), title: 'Farmacia - HCOP AJP' },
      { path: 'hospital-day/triage', loadComponent: () => import('./features/day-hospital/triage-queue-page.component').then((m) => m.TriageQueuePageComponent), title: 'Triaje - HCOP AJP' },
      { path: 'hospital-day/preparation', loadComponent: () => import('./features/day-hospital/preparation-queue-page.component').then((m) => m.PreparationQueuePageComponent), title: 'Preparacion - HCOP AJP' },
      { path: 'hospital-day/administration', loadComponent: () => import('./features/day-hospital/administration-queue-page.component').then((m) => m.AdministrationQueuePageComponent), title: 'Sala - HCOP AJP' },
      { path: 'hospital-day/schedule', loadComponent: () => import('./features/day-hospital/schedule-page.component').then((m) => m.SchedulePageComponent), title: 'Turnos - HCOP AJP' },
      { path: 'protocols/new', loadComponent: () => import('./features/protocols/protocol-editor-page.component').then((m) => m.ProtocolEditorPageComponent), title: 'Nuevo protocolo - HCOP AJP' },
      { path: 'protocols/:id/edit', loadComponent: () => import('./features/protocols/protocol-editor-page.component').then((m) => m.ProtocolEditorPageComponent), title: 'Editar protocolo - HCOP AJP' },
      { path: 'protocols', loadComponent: () => import('./features/protocols/protocols-page.component').then((m) => m.ProtocolsPageComponent), title: 'Protocolos - HCOP AJP' },
      { path: 'configuration', loadComponent: () => import('./features/configuration/configuration-home-page.component').then((m) => m.ConfigurationHomePageComponent), title: 'Configuracion - HCOP AJP' },
      { path: 'configuration/guides', loadComponent: () => import('./features/configuration/guides-page.component').then((m) => m.GuidesPageComponent), title: 'Guias - HCOP AJP' },
      { path: 'configuration/diagnosis-equivalences', loadComponent: () => import('./features/configuration/diagnosis-equivalences-page.component').then((m) => m.DiagnosisEquivalencesPageComponent), title: 'Equivalencias diagnosticas - HCOP AJP' },
      { path: 'configuration/access-control', loadComponent: () => import('./features/configuration/access-control-page.component').then((m) => m.AccessControlPageComponent), title: 'Usuarios y permisos - HCOP AJP' },
      { path: 'configuration/day-hospital', loadComponent: () => import('./features/configuration/day-hospital-settings-page.component').then((m) => m.DayHospitalSettingsPageComponent), title: 'Configuracion - HCOP AJP' },
      { path: 'configuration/llm', loadComponent: () => import('./features/configuration/llm-settings-page.component').then((m) => m.LlmSettingsPageComponent), title: 'Inteligencia artificial - HCOP AJP' },
      { path: 'patients', component: PatientWorkspacePageComponent, title: 'Pacientes · HCOP AJP' },
      { path: 'patients/:patientId/diagnosis/new', component: PatientDiagnosisEditorPageComponent, title: 'Agregar diagnóstico · HCOP AJP' },
      { path: 'patients/:patientId/treatments/new', component: PatientTreatmentCreatePageComponent, title: 'Nuevo tratamiento · HCOP AJP' },
      { path: 'patients/:patientId/treatments', component: PatientTreatmentsPageComponent, title: 'Tratamientos · HCOP AJP' },
      { path: 'patients/:patientId/studies', component: PatientStudiesPageComponent, title: 'Estudios · HCOP AJP' },
      { path: 'patients/:patientId/history/edit', component: PatientHistoryEditorPageComponent, title: 'Editar historia clínica · HCOP AJP' },
      { path: 'patients/:patientId/history', component: PatientHistoryPageComponent, title: 'Historia clínica · HCOP AJP' },
      { path: 'patients/:patientId', component: PatientWorkspacePageComponent, title: 'Paciente · HCOP AJP' }
    ]
  },
  { path: '**', redirectTo: 'patients' }
];
