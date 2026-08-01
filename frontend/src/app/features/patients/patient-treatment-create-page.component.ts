import { CommonModule } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { concatMap, finalize, tap } from 'rxjs';
import { ApiClientService } from '../../core/api/api-client.service';
import { ApiError } from '../../core/api/api-error';
import { PatientWorkspaceResponse } from '../../core/patients/patient.models';

interface DiagnosisOption {
  id: string;
  nombre: string;
  protocolGroup?: string;
  protocolGroupLabel?: string;
}

interface SchemeOption {
  id: string;
  nombre?: string;
  name?: string;
  cycleDays?: number | null;
  duracionCiclo?: string | number | null;
  durationMinutes?: number | null;
  estimatedDurationMinutes?: number | null;
  estimatedDurationText?: string;
  protocolGroup?: string;
  protocolGroupLabel?: string;
}

interface SimpleOption {
  id: string;
  nombre: string;
  activo?: string;
}

interface TreatmentOptions {
  diagnoses?: DiagnosisOption[];
  diagnosticos?: DiagnosisOption[];
  schemes?: SchemeOption[];
  esquemas?: SchemeOption[];
  characters?: Array<SimpleOption | string>;
  caracteres?: Array<SimpleOption | string>;
  treatmentTypes?: Array<SimpleOption | string>;
  tipos?: Array<SimpleOption | string>;
  consentStates?: Array<SimpleOption | string>;
  consentimientos?: Array<SimpleOption | string>;
}

interface TreatmentOptionsResponse {
  ok: boolean;
  patientId: string;
  options: TreatmentOptions;
}

interface TreatmentRequirements {
  hayPeso?: boolean;
  hayTalla?: boolean;
  hayCalvert?: boolean;
  hayCalcioAlbumina?: boolean;
  edad?: number | null;
  idSexo?: string | null;
}

interface TreatmentRequirementsResponse {
  ok: boolean;
  patientId: string;
  schemeId: string;
  requirements: TreatmentRequirements;
}

interface TreatmentCreateResponse {
  ok: boolean;
  treatment?: { id?: string };
  evolutionCreated?: boolean;
}

@Component({
  selector: 'app-patient-treatment-create-page',
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './patient-treatment-create-page.component.html',
  styleUrl: './patient-treatment-create-page.component.scss'
})
export class PatientTreatmentCreatePageComponent {
  private readonly api = inject(ApiClientService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly patientId = signal<string | null>(null);
  readonly patientName = signal('');
  readonly diagnoses = signal<DiagnosisOption[]>([]);
  readonly schemes = signal<SchemeOption[]>([]);
  readonly treatmentTypes = signal<SimpleOption[]>([]);
  readonly characters = signal<SimpleOption[]>([]);
  readonly consentStates = signal<SimpleOption[]>([]);
  readonly requirements = signal<TreatmentRequirements | null>(null);
  readonly loading = signal(true);
  readonly loadingRequirements = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly success = signal<string | null>(null);
  readonly selectedDiagnosisId = signal('');
  readonly selectedSchemeId = signal('');
  readonly formVersion = signal(0);

  readonly form = new FormGroup({
    diagnostico: new FormControl('', { nonNullable: true }),
    esquema: new FormControl('', { nonNullable: true }),
    cantidadCiclos: new FormControl('1', { nonNullable: true }),
    cicloInicial: new FormControl('1', { nonNullable: true }),
    duracionCiclo: new FormControl('', { nonNullable: true }),
    fechaCreacion: new FormControl(this.today(), { nonNullable: true }),
    fechaPrimerCiclo: new FormControl(this.today(), { nonNullable: true }),
    tipoOncologico: new FormControl('', { nonNullable: true }),
    caracter: new FormControl('', { nonNullable: true }),
    estadoConsentimiento: new FormControl('Pendiente', { nonNullable: true }),
    peso: new FormControl('', { nonNullable: true }),
    talla: new FormControl('', { nonNullable: true }),
    creatinina: new FormControl('', { nonNullable: true }),
    tfg: new FormControl('', { nonNullable: true }),
    targetAUC: new FormControl('', { nonNullable: true }),
    calcio: new FormControl('', { nonNullable: true }),
    albumina: new FormControl('', { nonNullable: true }),
    requirementsConfirmed: new FormControl(false, { nonNullable: true }),
    protocolMismatchConfirmed: new FormControl(false, { nonNullable: true }),
    protocolMismatchReason: new FormControl('', { nonNullable: true })
  });

  readonly selectedDiagnosis = computed(() => this.diagnoses().find(
    (item) => item.id === this.selectedDiagnosisId()
  ) ?? null);
  readonly selectedScheme = computed(() => this.schemes().find(
    (item) => item.id === this.selectedSchemeId()
  ) ?? null);
  readonly protocolMismatch = computed(() => {
    const diagnosis = this.selectedDiagnosis();
    const scheme = this.selectedScheme();
    return Boolean(diagnosis?.protocolGroup && scheme?.protocolGroup
      && diagnosis.protocolGroup !== scheme.protocolGroup);
  });
  readonly protocolMismatchLabel = computed(() => {
    const diagnosis = this.selectedDiagnosis();
    const scheme = this.selectedScheme();
    const diagnosisGroup = diagnosis?.protocolGroupLabel || diagnosis?.protocolGroup || 'diagnóstico';
    const schemeGroup = scheme?.protocolGroupLabel || scheme?.protocolGroup || 'protocolo';
    return `El diagnóstico pertenece a «${diagnosisGroup}» y el protocolo a «${schemeGroup}».`;
  });
  readonly canSave = computed(() => {
    this.formVersion();
    const value = this.form.getRawValue();
    const cycles = this.number(value.cantidadCiclos);
    const initial = this.number(value.cicloInicial);
    const interval = this.number(value.duracionCiclo);
    const requirements = this.requirements();
    const mismatchOk = !this.protocolMismatch()
      || (value.protocolMismatchConfirmed && value.protocolMismatchReason.trim().length >= 10);
    return Boolean(
      value.diagnostico && value.esquema && value.tipoOncologico && value.caracter
      && value.estadoConsentimiento && cycles >= 1 && initial >= 1
      && (cycles === 1 || interval >= 1)
      && value.requirementsConfirmed
      && mismatchOk
      && (!requirements?.hayPeso || this.positive(value.peso))
      && (!requirements?.hayTalla || this.positive(value.talla))
      && (!requirements?.hayCalvert
        || (this.positive(value.creatinina) && this.positive(value.tfg) && this.positive(value.targetAUC)))
      && (!requirements?.hayCalcioAlbumina
        || (this.positive(value.calcio) && this.positive(value.albumina)))
    );
  });

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.formVersion.update((value) => value + 1);
    });
    this.form.controls.diagnostico.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((value) => {
      this.selectedDiagnosisId.set(value);
    });
    this.form.controls.esquema.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((value) => {
      this.selectedSchemeId.set(value);
    });
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const id = params.get('patientId');
      if (id) {
        this.patientId.set(id);
        this.loadOptions(id);
      }
    });
  }

  loadOptions(patientId = this.patientId()): void {
    if (!patientId) return;
    this.loading.set(true);
    this.error.set(null);
    this.api.post<PatientWorkspaceResponse>(
      `/api/clinical/patients/${encodeURIComponent(patientId)}/activate`
    ).pipe(
      tap((workspace) => this.patientName.set(workspace.patient.fullName)),
      concatMap(() => this.api.get<TreatmentOptionsResponse>(
        `/api/clinical/patients/${encodeURIComponent(patientId)}/treatment-options`
      )),
      finalize(() => this.loading.set(false))
    ).subscribe({
      next: (response) => {
        const options = response.options ?? {};
        this.diagnoses.set(options.diagnoses ?? options.diagnosticos ?? []);
        this.schemes.set(options.schemes ?? options.esquemas ?? []);
        this.treatmentTypes.set(this.simpleOptions(options.treatmentTypes ?? options.tipos));
        this.characters.set(this.simpleOptions(options.characters ?? options.caracteres));
        this.consentStates.set(this.simpleOptions(options.consentStates ?? options.consentimientos));
      },
      error: (error: unknown) => this.error.set(ApiError.from(error).message)
    });
  }

  schemeChanged(): void {
    const scheme = this.selectedScheme();
    const defaultDays = this.number(String(scheme?.cycleDays ?? scheme?.duracionCiclo ?? ''));
    this.form.controls.duracionCiclo.setValue(defaultDays > 0 ? String(defaultDays) : '');
    this.form.controls.requirementsConfirmed.setValue(false);
    this.requirements.set(null);
    const patientId = this.patientId();
    if (!patientId || !scheme?.id) return;
    this.loadingRequirements.set(true);
    this.error.set(null);
    this.api.get<TreatmentRequirementsResponse>(
      `/api/clinical/patients/${encodeURIComponent(patientId)}/treatment-requirements/${encodeURIComponent(scheme.id)}`
    ).pipe(finalize(() => this.loadingRequirements.set(false))).subscribe({
      next: (response) => {
        this.requirements.set(response.requirements ?? {});
        this.form.controls.requirementsConfirmed.setValue(false);
      },
      error: (error: unknown) => this.error.set(ApiError.from(error).message)
    });
  }

  save(): void {
    const patientId = this.patientId();
    const value = this.form.getRawValue();
    if (!patientId || this.saving()) return;
    if (!this.canSave()) {
      this.error.set('Complete los datos obligatorios y confirme los requisitos antes de guardar.');
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.success.set(null);
    const payload = {
      diagnostico: value.diagnostico,
      esquema: value.esquema,
      cantidadCiclos: this.number(value.cantidadCiclos),
      cicloInicial: this.number(value.cicloInicial),
      duracionCiclo: this.number(value.duracionCiclo),
      fechaCreacion: value.fechaCreacion,
      fechaPrimerCiclo: value.fechaPrimerCiclo,
      tipoOncologico: value.tipoOncologico,
      caracter: value.caracter,
      estadoConsentimiento: value.estadoConsentimiento,
      peso: value.peso,
      talla: value.talla,
      creatinina: value.creatinina,
      tfg: value.tfg,
      targetAUC: value.targetAUC,
      calcio: value.calcio,
      albumina: value.albumina,
      requirementsConfirmed: true,
      protocolMismatchConfirmed: this.protocolMismatch() && value.protocolMismatchConfirmed,
      protocolMismatchReason: this.protocolMismatch() ? value.protocolMismatchReason.trim() : ''
    };
    this.api.post<TreatmentCreateResponse>(
      `/api/clinical/patients/${encodeURIComponent(patientId)}/treatments`, payload
    ).pipe(finalize(() => this.saving.set(false))).subscribe({
      next: () => {
        this.success.set('Tratamiento creado y agregado a la historia clínica.');
        this.router.navigate(['/patients', patientId, 'treatments']);
      },
      error: (error: unknown) => this.error.set(ApiError.from(error).message)
    });
  }

  schemeName(scheme: SchemeOption): string {
    return scheme.nombre || scheme.name || 'Esquema sin nombre';
  }

  durationLabel(scheme: SchemeOption): string {
    if (scheme.estimatedDurationText) return scheme.estimatedDurationText;
    const minutes = scheme.estimatedDurationMinutes ?? scheme.durationMinutes;
    if (!minutes || minutes < 1) return 'Duración no informada';
    const hours = Math.floor(minutes / 60);
    const rest = minutes % 60;
    return hours ? `${hours} h${rest ? ` ${rest} min` : ''}` : `${minutes} min`;
  }

  private simpleOptions(values: Array<SimpleOption | string> | undefined): SimpleOption[] {
    return (values ?? []).map((value) => typeof value === 'string'
      ? { id: value, nombre: value, activo: '1' }
      : value);
  }

  private positive(value: string): boolean {
    return Number.isFinite(Number(value)) && Number(value) > 0;
  }

  private number(value: string): number {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }

  private today(): string {
    return new Date().toISOString().slice(0, 10);
  }
}
