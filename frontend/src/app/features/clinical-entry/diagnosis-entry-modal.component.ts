import { Component, ElementRef, OnDestroy, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ClinicalDraftHandle, ClinicalDraftRegistryService } from '../../core/patients/clinical-draft-registry.service';
import { PatientWorkspaceService } from '../../core/patients/patient-workspace.service';
import {
  AjccCategory, AjccSiteDetail, AjccSiteSummary, ClinicalEntrySaveResult, DiagnosisCatalogItem,
  DiagnosisClassification, DiagnosisEditorCatalog, DiagnosisEntryDraft, DiagnosisRecord,
  DiagnosisSystem, DiagnosisValidation, DIAGNOSIS_SYSTEM_LABELS, TnmPrefix
} from './clinical-entry.models';
import {
  ajccAxisCategories, ajccExtraAxisKeys, blankClassification, diagnosisMatchesQuery,
  localIsoDate, mappedCatalogItems, newClinicalEntryId, normalizeClassification,
  validateDiagnosisDraft
} from './clinical-entry.normalizers';
import { ClinicalEntryService, normalizeEntryFailure } from './clinical-entry.service';

const EMPTY_CATALOG: DiagnosisEditorCatalog = { sites: [], groups: [], equivalences: [], requiredSystems: ['ajcc', 'snomed', 'cie10'] };
const PREFIXES: readonly TnmPrefix[] = Object.freeze(['c', 'p', 'yc', 'yp', 'r']);

@Component({
  selector: 'app-diagnosis-entry-modal',
  imports: [FormsModule],
  templateUrl: './diagnosis-entry-modal.component.html',
  styleUrl: './diagnosis-entry-modal.component.scss'
})
export class DiagnosisEntryModalComponent implements OnDestroy {
  readonly open = input(false);
  readonly closed = output<void>();
  readonly saved = output<ClinicalEntrySaveResult<DiagnosisRecord>>();
  readonly evolutionRequested = output<DiagnosisRecord>();

  readonly workspace = inject(PatientWorkspaceService);
  readonly entries = inject(ClinicalEntryService);
  private readonly drafts = inject(ClinicalDraftRegistryService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private draftHandle: ClinicalDraftHandle | null = null;
  private openContext = '';
  private catalogRequest = 0;
  private detailRequest = 0;
  private stageRequest = 0;
  private searchRequest: Record<'snomed' | 'cie10', number> = { snomed: 0, cie10: 0 };
  private searchTimers: Record<'snomed' | 'cie10', ReturnType<typeof setTimeout> | null> = { snomed: null, cie10: null };

  readonly catalog = signal<DiagnosisEditorCatalog>(EMPTY_CATALOG);
  readonly entryId = signal('');
  readonly selectedSiteId = signal('');
  readonly detail = signal<AjccSiteDetail | null>(null);
  readonly prefix = signal<TnmPrefix>('c');
  readonly date = signal(localIsoDate());
  readonly values = signal<Record<string, string>>({ T: '', N: '', M: '' });
  readonly stage = signal('');
  readonly stageEdited = signal(false);
  readonly sourceRow = signal<number | null>(null);
  readonly classifications = signal<Record<DiagnosisSystem, DiagnosisClassification>>({
    ajcc: blankClassification('ajcc'), snomed: blankClassification('snomed'), cie10: blankClassification('cie10')
  });
  readonly searchTerms = signal<Record<'snomed' | 'cie10', string>>({ snomed: '', cie10: '' });
  readonly searchResults = signal<Record<'snomed' | 'cie10', readonly DiagnosisCatalogItem[]>>({ snomed: [], cie10: [] });
  readonly selectedCodes = signal<Record<'snomed' | 'cie10', string>>({ snomed: '', cie10: '' });
  readonly loadingCatalog = signal(false);
  readonly loadingDetail = signal(false);
  readonly calculating = signal(false);
  readonly searching = signal<Record<'snomed' | 'cie10', boolean>>({ snomed: false, cie10: false });
  readonly stageMessage = signal('Seleccione T, N y M. El estadio se calculará automáticamente y podrá editarlo.');
  readonly error = signal('');
  readonly busy = signal(false);
  readonly discardPrompt = signal(false);
  readonly standalone = { standalone: true } as const;
  readonly systemLabels = DIAGNOSIS_SYSTEM_LABELS;
  readonly coreAxes = Object.freeze(['T', 'N', 'M'] as const);
  readonly terminologySystems = Object.freeze(['snomed', 'cie10'] as const);

  readonly selectedSite = computed(() => this.catalog().sites.find((site) => site.id === this.selectedSiteId()) || null);
  readonly extraAxes = computed(() => ajccExtraAxisKeys(this.detail()));
  readonly currentDraft = computed<DiagnosisEntryDraft | null>(() => {
    const site = this.selectedSite(); const detail = this.detail();
    if (!site || !detail || detail.id !== site.id) return null;
    return { id: this.entryId(), date: this.date(), prefix: this.prefix(), site, detail,
      values: this.values(), stage: this.stage(), stageEdited: this.stageEdited(), sourceRow: this.sourceRow(),
      classifications: this.classifications() };
  });
  readonly validation = computed<DiagnosisValidation>(() =>
    validateDiagnosisDraft(this.currentDraft(), this.catalog().requiredSystems, this.catalog().equivalences));
  readonly canSave = computed(() => this.entries.canEdit() && this.entries.canStage() && this.validation().valid && !this.busy());
  readonly tnmSummary = computed(() => {
    const t = this.values()['T']; const n = this.values()['N']; const m = this.values()['M'];
    return [t, n, m].filter(Boolean).join(' ') || 'TNM incompleto';
  });

  constructor() {
    effect(() => {
      const context = this.open() ? this.workspace.workspace()?.patientId || '' : '';
      if (context === this.openContext) return;
      this.releaseDraft(); this.openContext = context;
      if (context) this.beginOpen(); else this.invalidateRequests();
    });
  }

  ngOnDestroy(): void { this.invalidateRequests(); this.releaseDraft(); }

  axisCategories(key: string): readonly AjccCategory[] { return ajccAxisCategories(this.detail(), key, this.prefix()); }
  axisLabel(key: string): string { return this.detail()?.axes[key]?.label || key; }
  axisValue(key: string): string { return this.values()[key] || ''; }
  axisDefinition(key: string): string {
    const value = this.axisValue(key);
    const item = this.axisCategories(key).find((category) => category.code === value);
    return item ? `${item.code} = ${item.description || 'Sin descripción adicional'}` : 'Seleccione una categoría';
  }
  isRequired(system: DiagnosisSystem): boolean { return this.catalog().requiredSystems.includes(system); }
  selectedClassification(system: DiagnosisSystem): DiagnosisClassification { return this.classifications()[system]; }

  terminologyOptions(system: 'snomed' | 'cie10'): readonly DiagnosisCatalogItem[] {
    const mapped = mappedCatalogItems(this.catalog().equivalences, this.selectedSiteId(), system);
    const searched = this.searchResults()[system].filter((item) => diagnosisMatchesQuery(item, this.searchTerms()[system]));
    return [...new Map([...mapped, ...searched].map((item) => [item.code.toLocaleLowerCase('en-US'), item])).values()];
  }

  async selectSite(siteIdValue: unknown): Promise<void> {
    this.stageRequest += 1; this.calculating.set(false);
    const siteId = String(siteIdValue || '');
    this.selectedSiteId.set(siteId); this.detail.set(null); this.values.set({ T: '', N: '', M: '' });
    this.stage.set(''); this.stageEdited.set(false); this.sourceRow.set(null); this.stageMessage.set('Cargando criterios AJCC 8…');
    this.searchTerms.set({ snomed: '', cie10: '' }); this.searchResults.set({ snomed: [], cie10: [] }); this.selectedCodes.set({ snomed: '', cie10: '' });
    const site = this.catalog().sites.find((item) => item.id === siteId);
    this.classifications.set({
      ajcc: site ? normalizeClassification({ code: site.id, display: site.display, freeText: site.display,
        version: site.edition, source: site.source }, 'ajcc') : blankClassification('ajcc'),
      snomed: blankClassification('snomed'), cie10: blankClassification('cie10')
    });
    this.error.set(''); this.discardPrompt.set(false); this.markDirty();
    if (!site) { this.stageMessage.set('Seleccione el sitio AJCC.'); return; }
    this.applySingleMapping(site.id);
    const request = ++this.detailRequest; this.loadingDetail.set(true);
    try {
      const detail = await firstValueFrom(this.entries.ajccDetail(site.id));
      if (request !== this.detailRequest || this.selectedSiteId() !== site.id) return;
      this.detail.set(detail); this.stageMessage.set('Seleccione T, N y M. El estadio se calculará automáticamente y podrá editarlo.');
    } catch (failure: unknown) {
      if (request === this.detailRequest) this.error.set(normalizeEntryFailure(failure, 'No se pudo cargar AJCC 8.').message);
    } finally { if (request === this.detailRequest) this.loadingDetail.set(false); }
  }

  changePrefix(value: unknown): void {
    const candidate = String(value || '') as TnmPrefix;
    this.prefix.set(PREFIXES.includes(candidate) ? candidate : 'c');
    this.values.update((current) => ({ ...current, T: '' })); this.afterStagingChange();
  }
  changeDate(value: unknown): void { this.date.set(String(value || '')); this.error.set(''); this.markDirty(); }
  changeAxis(key: string, value: unknown): void {
    this.values.update((current) => ({ ...current, [key]: String(value || '') })); this.afterStagingChange();
  }
  changeStage(value: unknown): void {
    this.stageRequest += 1; this.calculating.set(false);
    this.stage.set(String(value || '').trimStart().slice(0, 120)); this.stageEdited.set(true); this.sourceRow.set(null);
    this.stageMessage.set(this.stage() ? 'Estadio informado manualmente. Puede corregirlo antes de guardar.' : 'Ingrese el estadio para completar el diagnóstico.');
    this.error.set(''); this.markDirty();
  }

  search(system: 'snomed' | 'cie10', value: unknown): void {
    const term = String(value || '');
    this.searchTerms.update((current) => ({ ...current, [system]: term })); this.error.set(''); this.markDirty();
    const timer = this.searchTimers[system]; if (timer) clearTimeout(timer);
    if (term.trim().length < 2) { this.searchResults.update((current) => ({ ...current, [system]: [] })); return; }
    this.searchTimers[system] = setTimeout(() => void this.performSearch(system, term.trim()), 250);
  }

  chooseTerminology(system: 'snomed' | 'cie10', codeValue: unknown): void {
    const code = String(codeValue || '');
    const item = this.terminologyOptions(system).find((candidate) => candidate.code === code);
    this.selectedCodes.update((current) => ({ ...current, [system]: code }));
    this.classifications.update((current) => ({ ...current, [system]: item
      ? normalizeClassification({ ...item, freeText: this.searchTerms()[system] || item.display }, system)
      : blankClassification(system) }));
    this.error.set(''); this.markDirty();
  }

  requestClose(): void {
    if (this.busy()) return;
    if (this.draftHandle && this.drafts.isDirty(this.draftHandle)) { this.discardPrompt.set(true); return; }
    this.finishClose();
  }
  continueEditing(): void { this.discardPrompt.set(false); }
  discardAndClose(): void { if (!this.busy()) this.finishClose(); }

  async save(addToEvolution = false): Promise<void> {
    if (this.busy()) return;
    if (!this.entries.canEdit()) { this.error.set('Su usuario no tiene permiso para editar la historia clínica.'); return; }
    if (!this.entries.canStage()) { this.error.set('Su rol no permite consultar y calcular AJCC.'); return; }
    const draft = this.currentDraft(); const validation = this.validation();
    if (!draft || !validation.valid) { this.error.set(validation.message); this.focusIssue(validation.issues[0]?.field || 'ajcc'); return; }
    this.busy.set(true); this.error.set(''); this.discardPrompt.set(false);
    try {
      const result = await firstValueFrom(this.entries.saveDiagnosis(draft));
      this.releaseDraft(); this.saved.emit(result); this.closed.emit();
      if (addToEvolution) this.evolutionRequested.emit(result.record);
    } catch (failure: unknown) {
      this.error.set(normalizeEntryFailure(failure, 'No se pudo guardar el diagnóstico.').message);
    } finally { this.busy.set(false); }
  }

  private beginOpen(): void {
    this.invalidateRequests(); this.resetEditor(); this.loadingCatalog.set(true);
    const request = ++this.catalogRequest;
    this.entries.loadDiagnosisEditor().subscribe({
      next: (catalog) => { if (request === this.catalogRequest) { this.catalog.set(catalog); this.loadingCatalog.set(false); } },
      error: (failure: unknown) => { if (request === this.catalogRequest) {
        this.error.set(normalizeEntryFailure(failure, 'No se pudo cargar el editor de diagnóstico.').message); this.loadingCatalog.set(false);
      } }
    });
  }

  private resetEditor(): void {
    this.catalog.set(EMPTY_CATALOG); this.entryId.set(newClinicalEntryId('diagnosis')); this.selectedSiteId.set('');
    this.detail.set(null); this.prefix.set('c'); this.date.set(localIsoDate()); this.values.set({ T: '', N: '', M: '' });
    this.stage.set(''); this.stageEdited.set(false); this.sourceRow.set(null);
    this.classifications.set({ ajcc: blankClassification('ajcc'), snomed: blankClassification('snomed'), cie10: blankClassification('cie10') });
    this.searchTerms.set({ snomed: '', cie10: '' }); this.searchResults.set({ snomed: [], cie10: [] }); this.selectedCodes.set({ snomed: '', cie10: '' });
    this.loadingDetail.set(false); this.calculating.set(false); this.searching.set({ snomed: false, cie10: false });
    this.stageMessage.set('Seleccione primero el sitio AJCC.'); this.error.set(''); this.busy.set(false); this.discardPrompt.set(false);
  }

  private afterStagingChange(): void {
    this.stageRequest += 1; this.calculating.set(false);
    this.stage.set(''); this.stageEdited.set(false); this.sourceRow.set(null); this.error.set(''); this.markDirty();
    const detail = this.detail();
    if (!detail || !['T', 'N', 'M'].every((key) => this.values()[key])) {
      this.stageMessage.set('Complete T, N y M. Si la combinación no está tabulada, podrá ingresar el estadio manualmente.'); return;
    }
    void this.calculateStage();
  }

  private async calculateStage(): Promise<void> {
    const detail = this.detail(); if (!detail) return;
    const request = ++this.stageRequest; this.calculating.set(true); this.stageMessage.set('Calculando agrupación…');
    try {
      const result = await firstValueFrom(this.entries.calculateStage(detail.id, this.prefix(), this.values()));
      if (request !== this.stageRequest || this.detail()?.id !== detail.id) return;
      this.stage.set(result.stage); this.stageEdited.set(false); this.sourceRow.set(result.sourceRow);
      this.stageMessage.set(result.stage
        ? 'Estadio calculado con la matriz local. Puede corregirlo manualmente antes de guardar.'
        : `Combinación no contemplada${result.missing.length ? `: ${result.missing.join(' · ')}` : ''}. Ingrese el estadio manualmente.`);
    } catch (failure: unknown) {
      if (request === this.stageRequest) this.stageMessage.set(`${normalizeEntryFailure(failure, 'No se pudo calcular el estadio.').message} Ingréselo manualmente.`);
    } finally { if (request === this.stageRequest) this.calculating.set(false); }
  }

  private applySingleMapping(siteId: string): void {
    const mappings = this.catalog().equivalences.filter((item) => item.active && item.ajcc.code.toLocaleLowerCase('en-US') === siteId.toLocaleLowerCase('en-US'));
    const uniqueSnomed = [...new Map(mappings.map((item) => [item.snomed.code, item.snomed])).values()].filter((item) => item.code);
    const uniqueCie10 = [...new Map(mappings.map((item) => [item.cie10.code, item.cie10])).values()].filter((item) => item.code);
    if (uniqueSnomed.length === 1) {
      this.classifications.update((current) => ({ ...current, snomed: normalizeClassification(uniqueSnomed[0], 'snomed') }));
      this.selectedCodes.update((current) => ({ ...current, snomed: uniqueSnomed[0]!.code }));
    }
    if (uniqueCie10.length === 1) {
      this.classifications.update((current) => ({ ...current, cie10: normalizeClassification(uniqueCie10[0], 'cie10') }));
      this.selectedCodes.update((current) => ({ ...current, cie10: uniqueCie10[0]!.code }));
    }
  }

  private async performSearch(system: 'snomed' | 'cie10', term: string): Promise<void> {
    const request = ++this.searchRequest[system]; this.searching.update((current) => ({ ...current, [system]: true }));
    try {
      const results = await firstValueFrom(this.entries.searchDiagnosis(system, term));
      if (request === this.searchRequest[system] && this.searchTerms()[system].trim() === term) {
        this.searchResults.update((current) => ({ ...current, [system]: results.filter((item) => diagnosisMatchesQuery(item, term)) }));
      }
    } catch (failure: unknown) {
      if (request === this.searchRequest[system]) this.error.set(normalizeEntryFailure(failure, 'No se pudo buscar el diagnóstico.').message);
    } finally { if (request === this.searchRequest[system]) this.searching.update((current) => ({ ...current, [system]: false })); }
  }

  private markDirty(): void {
    const patientId = this.workspace.workspace()?.patientId;
    if (!patientId || !this.open() || !this.entries.canEdit()) return;
    this.draftHandle ||= this.drafts.acquire({ patientId, label: 'Nuevo diagnóstico' });
    this.drafts.setDirty(this.draftHandle, true);
  }
  private focusIssue(field: string): void { queueMicrotask(() => this.host.nativeElement.querySelector<HTMLElement>(`[data-entry-field="${CSS.escape(field)}"]`)?.focus()); }
  private finishClose(): void { this.releaseDraft(); this.error.set(''); this.discardPrompt.set(false); this.closed.emit(); }
  private releaseDraft(): void { if (this.draftHandle) { this.drafts.release(this.draftHandle); this.draftHandle = null; } }
  private invalidateRequests(): void {
    this.catalogRequest += 1; this.detailRequest += 1; this.stageRequest += 1; this.searchRequest.snomed += 1; this.searchRequest.cie10 += 1;
    for (const system of ['snomed', 'cie10'] as const) { const timer = this.searchTimers[system]; if (timer) clearTimeout(timer); this.searchTimers[system] = null; }
  }
}
