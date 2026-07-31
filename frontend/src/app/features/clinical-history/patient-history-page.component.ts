import { CommonModule } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { ApiClientService } from '../../core/api/api-client.service';
import { ApiError } from '../../core/api/api-error';
import { ClinicalDiagnosis, ClinicalDocument, ClinicalEvolution, PatientWorkspaceResponse } from '../../core/patients/patient.models';

interface NarrativeSection { label: string; text: string; }

@Component({
  selector: 'app-patient-history-page',
  imports: [CommonModule, RouterLink],
  templateUrl: './patient-history-page.component.html',
  styleUrl: './patient-history-page.component.scss'
})
export class PatientHistoryPageComponent {
  private readonly api = inject(ApiClientService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  readonly workspace = signal<PatientWorkspaceResponse | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const patientId = params.get('patientId');
      if (patientId) this.open(patientId);
    });
  }

  diagnoses(document: ClinicalDocument): ClinicalDiagnosis[] {
    const oncology = document.oncology;
    const records = oncology?.diagnosisRecords ?? oncology?.diagnoses ?? [];
    if (records.length) return records;
    return oncology?.diagnosis ? [{ diagnosis: oncology.diagnosis, date: oncology.diagnosisDate, stage: oncology.stage }] : [];
  }

  diagnosisLabel(record: ClinicalDiagnosis): string {
    return record.diagnosis ?? record.diagnostico ?? record.snomed ?? record.cie10 ?? record.topography ?? 'Diagnóstico sin descripción';
  }

  diagnosisStage(record: ClinicalDiagnosis): string | null { return record.stage ?? record.estadio ?? null; }

  narrative(document: ClinicalDocument): NarrativeSection[] {
    const narrative = document.narrative ?? {};
    return [
      ['Motivo de consulta', narrative.chiefComplaint], ['Enfermedad actual', narrative.currentIllness],
      ['Antecedentes personales', narrative.backgroundClinical], ['Medicación actual', narrative.currentMedication],
      ['Antecedentes oncológicos familiares', narrative.familyOncology], ['Antecedentes ginecológicos', narrative.gynecology],
      ['Examen físico', narrative.physicalExam], ['Conclusión / resumen', narrative.summary], ['Plan', narrative.plan]
    ].filter((item): item is [string, string] => Boolean(item[1]?.trim())).map(([label, text]) => ({ label, text }));
  }

  evolutions(document: ClinicalDocument): ClinicalEvolution[] { return document.evolutions ?? []; }

  private open(patientId: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.post<PatientWorkspaceResponse>(`/api/clinical/patients/${patientId}/activate`).pipe(
      finalize(() => this.loading.set(false))
    ).subscribe({ next: (workspace) => this.workspace.set(workspace), error: (error: unknown) => this.error.set(ApiError.from(error).message) });
  }
}
