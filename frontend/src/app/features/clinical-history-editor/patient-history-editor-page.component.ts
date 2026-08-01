import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { concatMap, finalize } from 'rxjs';
import { ApiClientService } from '../../core/api/api-client.service';
import { ApiError } from '../../core/api/api-error';
import { ClinicalDocument, PatientWorkspaceResponse } from '../../core/patients/patient.models';

type HistoryFormValue = {
  chiefComplaint: string;
  currentIllness: string;
  backgroundClinical: string;
  currentMedication: string;
  familyOncology: string;
  gynecology: string;
  physicalExam: string;
  summary: string;
  plan: string;
  weightKg: string;
  heightCm: string;
};

@Component({
  selector: 'app-patient-history-editor-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './patient-history-editor-page.component.html',
  styleUrl: './patient-history-editor-page.component.scss'
})
export class PatientHistoryEditorPageComponent {
  private readonly api = inject(ApiClientService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly patientId = signal<string | null>(null);
  readonly patientName = signal('');
  readonly document = signal<ClinicalDocument | null>(null);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly conflict = signal(false);
  readonly form = new FormGroup({
    chiefComplaint: new FormControl('', { nonNullable: true }),
    currentIllness: new FormControl('', { nonNullable: true }),
    backgroundClinical: new FormControl('', { nonNullable: true }),
    currentMedication: new FormControl('', { nonNullable: true }),
    familyOncology: new FormControl('', { nonNullable: true }),
    gynecology: new FormControl('', { nonNullable: true }),
    physicalExam: new FormControl('', { nonNullable: true }),
    summary: new FormControl('', { nonNullable: true }),
    plan: new FormControl('', { nonNullable: true }),
    weightKg: new FormControl('', { nonNullable: true }),
    heightCm: new FormControl('', { nonNullable: true })
  });
  readonly currentValues = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });
  readonly bmi = computed(() => {
    const value = this.currentValues();
    const weight = Number(value.weightKg);
    const height = Number(value.heightCm) / 100;
    return weight > 0 && height > 0 ? (weight / (height * height)).toFixed(2) : '—';
  });
  readonly bodySurface = computed(() => {
    const value = this.currentValues();
    const weight = Number(value.weightKg);
    const height = Number(value.heightCm);
    return weight > 0 && height > 0 ? Math.sqrt((height * weight) / 3600).toFixed(3) : '—';
  });

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const patientId = params.get('patientId');
      if (patientId) this.load(patientId);
    });
  }

  save(): void {
    const current = this.document();
    const patientId = this.patientId();
    if (!current || !patientId || this.saving()) return;
    this.saving.set(true);
    this.error.set(null);
    this.conflict.set(false);
    const payload = this.withFormValues(current, this.form.getRawValue());
    this.api.put('/api/hc', payload).pipe(
      concatMap(() => this.api.get<ClinicalDocument>('/api/hc')),
      finalize(() => this.saving.set(false))
    ).subscribe({
      next: (document) => {
        this.document.set(document);
        this.router.navigate(['/patients', patientId, 'history']);
      },
      error: (error: unknown) => {
        const apiError = ApiError.from(error);
        this.conflict.set(apiError.status === 409 || apiError.code === 'VERSION_CONFLICT');
        this.error.set(apiError.message);
      }
    });
  }

  reload(): void {
    const patientId = this.patientId();
    if (patientId) this.load(patientId);
  }

  private load(patientId: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.conflict.set(false);
    this.api.post<PatientWorkspaceResponse>(`/api/clinical/patients/${patientId}/activate`).pipe(
      finalize(() => this.loading.set(false))
    ).subscribe({
      next: (workspace) => {
        this.patientId.set(patientId);
        this.patientName.set(workspace.patient.fullName);
        this.document.set(workspace.state);
        this.form.reset(this.formValues(workspace.state));
      },
      error: (error: unknown) => this.error.set(ApiError.from(error).message)
    });
  }

  private formValues(document: ClinicalDocument): HistoryFormValue {
    const narrative = document.narrative ?? {};
    const rawHeight = Number(document.exam?.heightM ?? 0);
    return {
      chiefComplaint: narrative.chiefComplaint ?? '', currentIllness: narrative.currentIllness ?? '',
      backgroundClinical: narrative.backgroundClinical ?? '', currentMedication: narrative.currentMedication ?? '',
      familyOncology: narrative.familyOncology ?? '', gynecology: narrative.gynecology ?? '',
      physicalExam: narrative.physicalExam ?? '', summary: narrative.summary ?? '', plan: narrative.plan ?? '',
      weightKg: String(document.exam?.weightKg ?? ''),
      heightCm: rawHeight > 0 && rawHeight < 3 ? String(Math.round(rawHeight * 100)) : String(document.exam?.heightM ?? '')
    };
  }

  private withFormValues(document: ClinicalDocument, form: HistoryFormValue): ClinicalDocument {
    const copy = JSON.parse(JSON.stringify(document)) as ClinicalDocument;
    copy.narrative = { ...(copy.narrative ?? {}),
      chiefComplaint: form.chiefComplaint.trim(), currentIllness: form.currentIllness.trim(),
      backgroundClinical: form.backgroundClinical.trim(), currentMedication: form.currentMedication.trim(),
      familyOncology: form.familyOncology.trim(), gynecology: form.gynecology.trim(),
      physicalExam: form.physicalExam.trim(), summary: form.summary.trim(), plan: form.plan.trim() };
    copy.exam = {
      ...(copy.exam ?? {}),
      weightKg: String(form.weightKg ?? '').trim(),
      heightM: String(form.heightCm ?? '').trim()
    };
    return copy;
  }
}
