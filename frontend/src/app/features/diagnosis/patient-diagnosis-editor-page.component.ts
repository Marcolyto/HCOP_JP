import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { concatMap, finalize, map } from 'rxjs';
import { ApiClientService } from '../../core/api/api-client.service';
import { ApiError } from '../../core/api/api-error';
import { ClinicalDiagnosis, ClinicalDocument, PatientWorkspaceResponse } from '../../core/patients/patient.models';

interface CatalogEntry { code: string; display: string; group?: string; version?: string; source?: string; system?: string; }
interface CatalogSearchResponse { ok: boolean; items: CatalogEntry[]; }
interface AjccSite { id: string; name: string; group: string; }
interface AjccListResponse { ok: boolean; sites: AjccSite[]; }
interface AjccAxis { label?: string; categories?: Array<{ code: string; description?: string }>; }
interface AjccDetailResponse { ok: boolean; id: string; name: string; axes: Record<string, AjccAxis>; }
interface StageResponse { ok: boolean; stage?: string; sourceRow?: number; missing?: string[]; }

@Component({
  selector: 'app-patient-diagnosis-editor-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './patient-diagnosis-editor-page.component.html',
  styleUrl: './patient-diagnosis-editor-page.component.scss'
})
export class PatientDiagnosisEditorPageComponent {
  private readonly api = inject(ApiClientService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly patientId = signal<string | null>(null);
  readonly patientName = signal('');
  readonly document = signal<ClinicalDocument | null>(null);
  readonly sites = signal<AjccSite[]>([]);
  readonly detail = signal<AjccDetailResponse | null>(null);
  readonly axisValues = signal<Record<string, string>>({});
  readonly snomedResults = signal<CatalogEntry[]>([]);
  readonly cieResults = signal<CatalogEntry[]>([]);
  readonly selectedSnomed = signal<CatalogEntry | null>(null);
  readonly selectedCie = signal<CatalogEntry | null>(null);
  readonly loading = signal(true);
  readonly catalogLoading = signal(false);
  readonly staging = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly conflict = signal(false);
  readonly stageHelp = signal('Complete TNM para calcular el estadio o indíquelo manualmente.');
  readonly form = new FormGroup({
    siteId: new FormControl('', { nonNullable: true }),
    diagnosisDate: new FormControl(new Date().toISOString().slice(0, 10), { nonNullable: true }),
    stage: new FormControl('', { nonNullable: true }),
    snomedQuery: new FormControl('', { nonNullable: true }),
    cieQuery: new FormControl('', { nonNullable: true })
  });
  private readonly formValue = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });
  readonly axes = computed(() => Object.entries(this.detail()?.axes ?? {})
    .filter(([key]) => !['Classification', 'DescY', 'DescR', 'DescM'].includes(key)));
  readonly complete = computed(() => {
    const value = this.formValue();
    return Boolean(value.siteId && this.selectedSnomed() && this.selectedCie() && value.stage?.trim());
  });

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const patientId = params.get('patientId');
      if (patientId) this.load(patientId);
    });
  }

  siteChanged(): void {
    const siteId = this.form.controls.siteId.value;
    this.detail.set(null);
    this.axisValues.set({});
    this.form.controls.stage.setValue('');
    this.stageHelp.set('Cargando criterios TNM del sitio seleccionado…');
    if (!siteId) return;
    this.api.get<AjccDetailResponse>('/api/ajcc8/detail', { id: siteId }).subscribe({
      next: (detail) => {
        this.detail.set(detail);
        this.axisValues.set({});
        this.stageHelp.set('Seleccione T, N y M. El estadio se calculará automáticamente cuando exista una combinación contemplada.');
      },
      error: (error: unknown) => this.error.set(ApiError.from(error).message)
    });
  }

  axisChanged(axis: string, value: string): void {
    this.axisValues.update((current) => ({ ...current, [axis]: value }));
    this.calculateStage();
  }

  search(system: 'snomed' | 'cie10'): void {
    const query = system === 'snomed' ? this.form.controls.snomedQuery.value : this.form.controls.cieQuery.value;
    if (query.trim().length < 2) {
      this.error.set('Escriba al menos dos caracteres para buscar en el catálogo.');
      return;
    }
    this.catalogLoading.set(true);
    this.error.set(null);
    this.api.get<CatalogSearchResponse>('/api/diagnosis-catalogs/search', { system, q: query, limit: 40 }).pipe(
      finalize(() => this.catalogLoading.set(false))
    ).subscribe({
      next: (response) => system === 'snomed' ? this.snomedResults.set(response.items) : this.cieResults.set(response.items),
      error: (error: unknown) => this.error.set(ApiError.from(error).message)
    });
  }

  choose(system: 'snomed' | 'cie10', code: string): void {
    const item = (system === 'snomed' ? this.snomedResults() : this.cieResults()).find((candidate) => candidate.code === code) ?? null;
    if (system === 'snomed') this.selectedSnomed.set(item); else this.selectedCie.set(item);
  }

  save(): void {
    const document = this.document();
    const patientId = this.patientId();
    const site = this.sites().find((candidate) => candidate.id === this.form.controls.siteId.value);
    const snomed = this.selectedSnomed();
    const cie = this.selectedCie();
    if (!document || !patientId || !site || !snomed || !cie || !this.complete() || this.saving()) {
      this.error.set('AJCC, SNOMED CT, CIE-10 y estadio son obligatorios para guardar el diagnóstico.');
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.conflict.set(false);
    const id = `diagnosis-${crypto.randomUUID()}`;
    const record = this.buildRecord(id, site, snomed, cie);
    const payload = this.withDiagnosis(document, record);
    this.api.put('/api/hc', payload).pipe(
      concatMap(() => this.api.get<ClinicalDocument>('/api/hc')),
      concatMap((saved) => this.api.put('/api/clinical/patients/' + encodeURIComponent(patientId) + '/diagnosis', {
        expectedRevision: saved.meta?.persistenceRevision ?? 0, diagnosisEntryId: id
      }).pipe(map(() => saved))),
      finalize(() => this.saving.set(false))
    ).subscribe({
      next: () => this.router.navigate(['/patients', patientId, 'history']),
      error: (error: unknown) => {
        const apiError = ApiError.from(error);
        this.conflict.set(apiError.status === 409 || apiError.code === 'VERSION_CONFLICT');
        this.error.set(apiError.message);
      }
    });
  }

  reload(): void { const id = this.patientId(); if (id) this.load(id); }

  private load(patientId: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.conflict.set(false);
    this.api.post<PatientWorkspaceResponse>(`/api/clinical/patients/${patientId}/activate`).pipe(
      concatMap((workspace) => this.api.get<AjccListResponse>('/api/ajcc8').pipe(map((catalog) => ({ workspace, catalog })))),
      finalize(() => this.loading.set(false))
    ).subscribe({
      next: ({ workspace, catalog }) => {
        this.patientId.set(patientId); this.patientName.set(workspace.patient.fullName); this.document.set(workspace.state); this.sites.set(catalog.sites);
      }, error: (error: unknown) => this.error.set(ApiError.from(error).message)
    });
  }

  private calculateStage(): void {
    const detail = this.detail();
    const values = this.axisValues();
    if (!detail || !values['T'] || !values['N'] || !values['M']) { this.stageHelp.set('Seleccione T, N y M para calcular automáticamente el estadio.'); return; }
    this.staging.set(true);
    const stageValues = { ...values, Classification: 'c', DescY: 'No', DescR: 'No', DescM: 'No' };
    this.api.post<StageResponse>('/api/ajcc8/stage', { id: detail.id, values: stageValues }).pipe(finalize(() => this.staging.set(false))).subscribe({
      next: (response) => {
        if (response.stage) { this.form.controls.stage.setValue(response.stage); this.stageHelp.set(`Estadio calculado con AJCC 8${response.sourceRow ? ` · fila ${response.sourceRow}` : ''}. Puede corregirlo si corresponde.`); }
        else this.stageHelp.set(`No hay estadio automático: ${(response.missing ?? []).join(', ') || 'combinación no contemplada'}. Ingréselo manualmente si corresponde.`);
      }, error: (error: unknown) => { this.stageHelp.set('No se pudo calcular automáticamente. Puede consignar el estadio manualmente.'); this.error.set(ApiError.from(error).message); }
    });
  }

  private buildRecord(id: string, site: AjccSite, snomed: CatalogEntry, cie: CatalogEntry): ClinicalDiagnosis {
    const date = this.form.controls.diagnosisDate.value;
    const stage = this.form.controls.stage.value.trim();
    const classifications = {
      ajcc: { system: 'AJCC', code: site.id, display: site.name, group: site.group, version: 'AJCC 8', source: 'Catálogo AJCC 8 local', freeText: site.name },
      snomed: { ...snomed, freeText: snomed.display }, cie10: { ...cie, freeText: cie.display }
    };
    const values = this.axisValues();
    const tnm = { siteId: site.id, siteDisplay: site.name, prefix: 'c', date, T: values['T'] ?? '', N: values['N'] ?? '', M: values['M'] ?? '', stage };
    return { id, date, diagnosis: snomed.display, topography: site.name, stage, diagnosticClassifications: classifications, tnm, createdAt: new Date().toISOString() } as ClinicalDiagnosis;
  }

  private withDiagnosis(document: ClinicalDocument, record: ClinicalDiagnosis): ClinicalDocument {
    const copy = JSON.parse(JSON.stringify(document)) as ClinicalDocument;
    const oncology = { ...(copy.oncology ?? {}) };
    const existing = Array.isArray(oncology.diagnosisRecords) ? oncology.diagnosisRecords : [];
    oncology.diagnosisRecords = [...existing, record];
    oncology.diagnosticClassifications = record.diagnosticClassifications;
    oncology.tnm = record.tnm;
    oncology.diagnosis = record.diagnosis; oncology.diagnosisDate = record.date; oncology['diagnosisDatePrecision'] = 'day'; oncology.topography = record.topography; oncology.stage = record.stage;
    copy.oncology = oncology;
    return copy;
  }
}
