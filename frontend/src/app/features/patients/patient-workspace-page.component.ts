import { CommonModule } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { ApiClientService } from '../../core/api/api-client.service';
import { ApiError } from '../../core/api/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { PatientSearchResponse, PatientSummary, PatientWorkspaceResponse } from '../../core/patients/patient.models';

@Component({
  selector: 'app-patient-workspace-page',
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './patient-workspace-page.component.html',
  styleUrl: './patient-workspace-page.component.scss'
})
export class PatientWorkspacePageComponent {
  private readonly api = inject(ApiClientService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  readonly query = new FormControl('', { nonNullable: true });
  readonly results = signal<PatientSummary[]>([]);
  readonly workspace = signal<PatientWorkspaceResponse | null>(null);
  readonly loading = signal(false);
  readonly searching = signal(false);
  readonly error = signal<string | null>(null);

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const patientId = params.get('patientId') ?? this.auth.session()?.activePatientId;
      if (patientId) this.openPatient(patientId, false);
      else this.workspace.set(null);
    });
  }

  search(): void {
    this.searching.set(true);
    this.error.set(null);
    this.api.get<PatientSearchResponse>('/api/clinical/patients', { q: this.query.value }).pipe(
      finalize(() => this.searching.set(false))
    ).subscribe({
      next: (response) => this.results.set(response.patients),
      error: (error: unknown) => this.error.set(ApiError.from(error).message)
    });
  }

  openFromList(patient: PatientSummary): void {
    this.openPatient(patient.id, true);
  }

  closePatient(): void {
    this.auth.setActivePatient(null).subscribe({
      next: () => {
        this.workspace.set(null);
        this.router.navigateByUrl('/patients');
      },
      error: (error: unknown) => this.error.set(ApiError.from(error).message)
    });
  }

  patientLabel(patient: PatientSummary): string {
    return [patient.fullName, patient.dni ? `DNI ${patient.dni}` : null].filter(Boolean).join(' · ');
  }

  private openPatient(patientId: string, navigate: boolean): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.post<PatientWorkspaceResponse>(`/api/clinical/patients/${patientId}/activate`).pipe(
      finalize(() => this.loading.set(false))
    ).subscribe({
      next: (workspace) => {
        this.workspace.set(workspace);
        this.auth.loadSession(true).subscribe();
        if (navigate) this.router.navigate(['/patients', patientId]);
      },
      error: (error: unknown) => this.error.set(ApiError.from(error).message)
    });
  }
}
