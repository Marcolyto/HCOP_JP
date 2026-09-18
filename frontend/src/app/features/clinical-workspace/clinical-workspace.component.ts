import { Component, ElementRef, OnDestroy, OnInit, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { firstValueFrom, Subscription } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import {
  clearClinicalFocusHighlights,
  findClinicalFocusTarget,
  renderClinicalFocusHighlights
} from '../../core/clinical/clinical-focus.dom';
import { ClinicalFocusRequest, ClinicalFocusService } from '../../core/clinical/clinical-focus.service';
import {
  CLINICAL_CHIEF_COMPLAINT_TEXT_LIMIT,
  applyStructuredChiefComplaintEdit,
  chiefComplaintBaseline,
  supportsStructuredChiefComplaint
} from '../../core/clinical/clinical-chief-complaint-edit';
import {
  CLINICAL_CURRENT_ILLNESS_TEXT_LIMIT,
  applyStructuredCurrentIllnessEdit,
  currentIllnessBaseline,
  supportsStructuredCurrentIllness
} from '../../core/clinical/clinical-current-illness-edit';
import {
  CLINICAL_PERSONAL_HISTORY_TEXT_LIMIT,
  applyStructuredPersonalHistoryEdit,
  personalHistoryBaseline,
  personalHistoryLegacySnapshot,
  supportsStructuredPersonalHistory
} from '../../core/clinical/clinical-personal-history-edit';
import {
  CLINICAL_PHYSICAL_EXAM_HEIGHT_MAX_CM,
  CLINICAL_PHYSICAL_EXAM_HEIGHT_MIN_CM,
  CLINICAL_PHYSICAL_EXAM_TEXT_LIMIT,
  CLINICAL_PHYSICAL_EXAM_WEIGHT_MAX_KG,
  CLINICAL_PHYSICAL_EXAM_WEIGHT_MIN_KG,
  DEFAULT_PHYSICAL_EXAM_TEXT,
  ClinicalPhysicalExamRow,
  applyStructuredPhysicalExamEdit,
  calculatePhysicalExamMetrics,
  physicalExamBaseline,
  physicalExamLegacySnapshot,
  physicalExamRows as projectPhysicalExamRows,
  supportsStructuredPhysicalExam
} from '../../core/clinical/clinical-physical-exam-edit';
import {
  CLINICAL_SUMMARY_PLAN_TEXT_LIMIT,
  applyStructuredSummaryPlanEdit,
  summaryPlanBaseline,
  supportsStructuredSummaryPlan
} from '../../core/clinical/clinical-summary-plan-edit';
import {
  ClinicalPrintFact,
  ClinicalPrintSection,
  clinicalPrintPatientFacts,
  clinicalPrintSectionHasContent
} from '../../core/clinical/clinical-print-projection';
import { ClinicalStudyEntry, clinicalStudyEntries } from '../../core/clinical/clinical-study-projection';
import { clinicalDiagnosisEntries, clinicalDiagnosisEntry } from '../../core/clinical/clinical-diagnosis-projection';
import { ClinicalTreatmentKind, clinicalSectionTreatments, clinicalTreatmentBody } from '../../core/clinical/clinical-treatment-projection';
import { ClinicalDraftHandle, ClinicalDraftRegistryService } from '../../core/patients/clinical-draft-registry.service';
import { PatientWorkspaceService } from '../../core/patients/patient-workspace.service';
import { ClinicalPatient, ClinicalRecord, ClinicalState } from '../../core/patients/patient-workspace.models';
import { ClinicalHighlightHostDirective } from '../../core/highlighting/clinical-highlight-host.directive';
import { ClinicalHighlightCoordinatorService } from '../../core/highlighting/clinical-highlight-coordinator.service';
import { ClinicalHighlightMutation } from '../../core/highlighting/clinical-highlight.models';
import { OncologyHistoryEntrySectionComponent } from '../oncology-history-entry/public-api';
import { DiagnosisEntryModalComponent, EvolutionEntryDraft, EvolutionEntryModalComponent, EvolutionImageAttachment, localIsoDate, newClinicalEntryId, normalizeEvolutionAttachments } from '../clinical-entry';

type SingleNarrativeSectionKey = 'chiefComplaint' | 'currentIllness';
type PersonalHistoryErrorTarget = 'backgroundClinical' | 'currentMedication' | 'familyOncology' | 'gynecology' | 'reason' | '';
type PhysicalExamErrorTarget = 'weightKg' | 'heightCm' | 'physicalExam' | 'reason' | '';

@Component({ selector: 'app-clinical-workspace', imports: [ReactiveFormsModule, ClinicalHighlightHostDirective, OncologyHistoryEntrySectionComponent, EvolutionEntryModalComponent, DiagnosisEntryModalComponent], templateUrl: './clinical-workspace.component.html', styleUrl: './clinical-workspace.component.scss' })
export class ClinicalWorkspaceComponent implements OnInit, OnDestroy {
  readonly printTimestamp = input('');
  readonly studiesRequested = output<{ mode: 'browse' | 'upload'; studyKey?: string }>();
  readonly workspaceService = inject(PatientWorkspaceService);
  readonly auth = inject(AuthService);
  readonly evolutionEntryOpen = signal(false);
  readonly evolutionEntryInitial = signal<EvolutionEntryDraft | null>(null);
  readonly diagnosisEntryOpen = signal(false);
  readonly diagnosisEntries = computed(() => clinicalDiagnosisEntries(this.state()).sort((left, right) =>
    (right.date || String(right.record.createdAt || '')).localeCompare(left.date || String(left.record.createdAt || ''))));
  readonly clinicalEntryMessage = signal('');
  private readonly clinicalFocus = inject(ClinicalFocusService);
  private readonly clinicalHighlightFeedback = inject(ClinicalHighlightCoordinatorService);
  private readonly clinicalDrafts = inject(ClinicalDraftRegistryService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  readonly query = new FormControl('', { nonNullable: true });
  readonly results = signal<ClinicalPatient[]>([]);
  readonly searching = signal(false);
  readonly searchError = signal('');
  readonly narrativeEditorOpen = signal(false);
  readonly narrativeEditorKey = signal<SingleNarrativeSectionKey>('chiefComplaint');
  readonly narrativeEditorInitial = signal(true);
  readonly narrativeEditorBusy = signal(false);
  readonly narrativeEditorError = signal('');
  readonly narrativeEditorErrorTarget = signal<'content' | 'reason' | ''>('');
  readonly narrativeEditorControl = new FormControl('', { nonNullable: true });
  readonly narrativeEditorReasonControl = new FormControl('', { nonNullable: true });
  private narrativeEditorDraft: ClinicalDraftHandle | null = null;
  private narrativeEditorBaseline = { value: '', initial: true };
  private narrativeEditorReturnFocus: HTMLElement | null = null;
  readonly personalHistoryOpen = signal(false);
  readonly personalHistoryInitial = signal(true);
  readonly personalHistoryBusy = signal(false);
  readonly personalHistoryError = signal('');
  readonly personalHistoryErrorTarget = signal<PersonalHistoryErrorTarget>('');
  readonly maxPersonalHistoryChars = CLINICAL_PERSONAL_HISTORY_TEXT_LIMIT;
  readonly backgroundClinicalControl = new FormControl('', { nonNullable: true });
  readonly currentMedicationControl = new FormControl('', { nonNullable: true });
  readonly familyOncologyControl = new FormControl('', { nonNullable: true });
  readonly gynecologyControl = new FormControl('', { nonNullable: true });
  readonly personalHistoryReasonControl = new FormControl('', { nonNullable: true });
  private personalHistoryDraft: ClinicalDraftHandle | null = null;
  private personalHistoryEditorBaseline = {
    backgroundClinical: '',
    currentMedication: '',
    familyOncology: '',
    gynecology: '',
    initial: true
  };
  private personalHistoryReturnFocus: HTMLElement | null = null;
  readonly physicalExamOpen = signal(false);
  readonly physicalExamInitial = signal(true);
  readonly physicalExamBusy = signal(false);
  readonly physicalExamError = signal('');
  readonly physicalExamErrorTarget = signal<PhysicalExamErrorTarget>('');
  readonly maxPhysicalExamChars = CLINICAL_PHYSICAL_EXAM_TEXT_LIMIT;
  readonly physicalExamWeightMin = CLINICAL_PHYSICAL_EXAM_WEIGHT_MIN_KG;
  readonly physicalExamWeightMax = CLINICAL_PHYSICAL_EXAM_WEIGHT_MAX_KG;
  readonly physicalExamHeightMin = CLINICAL_PHYSICAL_EXAM_HEIGHT_MIN_CM;
  readonly physicalExamHeightMax = CLINICAL_PHYSICAL_EXAM_HEIGHT_MAX_CM;
  readonly physicalExamWeightControl = new FormControl('', { nonNullable: true });
  readonly physicalExamHeightControl = new FormControl('', { nonNullable: true });
  readonly physicalExamTextControl = new FormControl('', { nonNullable: true });
  readonly physicalExamReasonControl = new FormControl('', { nonNullable: true });
  private physicalExamDraft: ClinicalDraftHandle | null = null;
  private physicalExamEditorBaseline = { weightKg: '', heightCm: '', physicalExam: '', initial: true };
  private physicalExamReturnFocus: HTMLElement | null = null;
  readonly summaryPlanOpen = signal(false);
  readonly summaryPlanInitial = signal(true);
  readonly summaryPlanBusy = signal(false);
  readonly summaryPlanError = signal('');
  readonly maxClinicalNarrativeChars = CLINICAL_SUMMARY_PLAN_TEXT_LIMIT;
  readonly summaryControl = new FormControl('', { nonNullable: true });
  readonly planControl = new FormControl('', { nonNullable: true });
  readonly summaryPlanReasonControl = new FormControl('', { nonNullable: true });
  private summaryPlanDraft: ClinicalDraftHandle | null = null;
  private summaryPlanBaseline = { summary: '', plan: '', initial: true };
  private summaryPlanReturnFocus: HTMLElement | null = null;
  private patientSearchRequest = 0;
  private patientSearchSubscription: Subscription | null = null;
  private clinicalFocusTimer: ReturnType<typeof setTimeout> | null = null;
  private missingClinicalFocusRequest = 0;

  constructor() {
    effect(() => {
      if (this.workspaceService.pickerOpen() && this.workspaceService.pickerRequest() > 0) this.search();
    });
    effect(() => {
      const request = this.clinicalFocus.request();
      this.workspaceService.workingWorkspace();
      if (!request.id) return;
      queueMicrotask(() => this.applyClinicalFocus(request));
    });
    this.summaryControl.valueChanges.subscribe(() => this.refreshSummaryPlanDirtyState());
    this.planControl.valueChanges.subscribe(() => this.refreshSummaryPlanDirtyState());
    this.summaryPlanReasonControl.valueChanges.subscribe(() => this.refreshSummaryPlanDirtyState());
    this.narrativeEditorControl.valueChanges.subscribe(() => this.refreshNarrativeEditorDirtyState());
    this.narrativeEditorReasonControl.valueChanges.subscribe(() => this.refreshNarrativeEditorDirtyState());
    this.backgroundClinicalControl.valueChanges.subscribe(() => this.refreshPersonalHistoryDirtyState());
    this.currentMedicationControl.valueChanges.subscribe(() => this.refreshPersonalHistoryDirtyState());
    this.familyOncologyControl.valueChanges.subscribe(() => this.refreshPersonalHistoryDirtyState());
    this.gynecologyControl.valueChanges.subscribe(() => this.refreshPersonalHistoryDirtyState());
    this.personalHistoryReasonControl.valueChanges.subscribe(() => this.refreshPersonalHistoryDirtyState());
    this.physicalExamWeightControl.valueChanges.subscribe(() => this.refreshPhysicalExamDirtyState());
    this.physicalExamHeightControl.valueChanges.subscribe(() => this.refreshPhysicalExamDirtyState());
    this.physicalExamTextControl.valueChanges.subscribe(() => this.refreshPhysicalExamDirtyState());
    this.physicalExamReasonControl.valueChanges.subscribe(() => this.refreshPhysicalExamDirtyState());
  }

  ngOnInit(): void {
    this.query.valueChanges.subscribe(() => this.search());
  }

  ngOnDestroy(): void {
    this.patientSearchRequest += 1;
    this.patientSearchSubscription?.unsubscribe();
    if (this.narrativeEditorDraft) this.clinicalDrafts.release(this.narrativeEditorDraft);
    if (this.personalHistoryDraft) this.clinicalDrafts.release(this.personalHistoryDraft);
    if (this.physicalExamDraft) this.clinicalDrafts.release(this.physicalExamDraft);
    if (this.summaryPlanDraft) this.clinicalDrafts.release(this.summaryPlanDraft);
    this.clearClinicalNavigationFocus();
    const root = this.host.nativeElement.querySelector<HTMLElement>('#clinicalDocument');
    if (root) clearClinicalFocusHighlights(root);
  }

  openPicker(): void { this.workspaceService.openPicker(); }
  closePicker(): void { this.workspaceService.pickerOpen.set(false); this.searchError.set(''); }
  canAddClinicalEntry(): boolean {
    return Boolean(
      this.workspaceService.workingWorkspace()
      && !this.workspaceService.loading()
      && this.auth.hasPermission('section.history.edit')
    );
  }
  openEvolutionEntry(): void {
    if (!this.canAddClinicalEntry() || this.workspaceService.hasPendingClinicalWork()) return;
    this.clinicalEntryMessage.set('');
    this.evolutionEntryInitial.set(null);
    this.evolutionEntryOpen.set(true);
  }
  addDiagnosisToEvolution(record: ClinicalRecord): void {
    if (!this.canAddClinicalEntry() || this.workspaceService.hasPendingClinicalWork()) return;
    const selected = clinicalDiagnosisEntry(record);
    const current = this.diagnosisEntries().find(entry => entry.key === selected.key && entry.evolutionText === selected.evolutionText);
    if (!current?.evolutionText) return;
    this.evolutionEntryInitial.set({
      id: newClinicalEntryId('evolution'), date: localIsoDate(), author: '', specialty: 'Oncología',
      text: current.evolutionText
    });
    this.evolutionEntryOpen.set(true);
  }
  closeEvolutionEntry(): void {
    this.evolutionEntryOpen.set(false);
    this.evolutionEntryInitial.set(null);
  }
  openDiagnosisEntry(): void {
    if (!this.canAddClinicalEntry() || this.workspaceService.hasPendingClinicalWork()) return;
    this.clinicalEntryMessage.set('');
    this.diagnosisEntryOpen.set(true);
  }
  clinicalEntrySaved(result: { warning: string }): void {
    this.clinicalEntryMessage.set(result.warning || 'El registro quedó guardado en la historia clínica.');
  }
  search(): void {
    const request = ++this.patientSearchRequest;
    this.patientSearchSubscription?.unsubscribe();
    this.searching.set(true); this.searchError.set('');
    this.patientSearchSubscription = this.workspaceService.search(this.query.value.trim()).subscribe({
      next: (response) => {
        if (request !== this.patientSearchRequest) return;
        this.results.set(response.patients || []); this.searching.set(false);
      },
      error: (response: { error?: { error?: string } }) => {
        if (request !== this.patientSearchRequest) return;
        this.searchError.set(response?.error?.error || 'No se pudo buscar pacientes.'); this.searching.set(false);
      }
    });
  }
  open(patient: ClinicalPatient): void { this.workspaceService.activate(patient); }
  closePatient(): void { this.workspaceService.close(); }

  canEditNarrativeSection(key: SingleNarrativeSectionKey): boolean {
    return Boolean(
      this.workspaceService.workspace()
      && !this.workspaceService.loading()
      && this.auth.hasPermission('section.history.edit')
      && this.supportsNarrativeSection(key, this.state())
    );
  }

  openNarrativeSectionEditor(key: SingleNarrativeSectionKey, event?: Event): void {
    const workspace = this.workspaceService.workspace();
    if (!workspace || this.workspaceService.loading() || !this.canEditNarrativeSection(key) || this.workspaceService.hasPendingClinicalWork()) return;
    this.narrativeEditorReturnFocus = event?.currentTarget instanceof HTMLElement
      ? event.currentTarget
      : document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const baseline = this.singleNarrativeBaseline(key, workspace.state);
    this.narrativeEditorKey.set(key);
    this.narrativeEditorBaseline = baseline;
    this.narrativeEditorInitial.set(baseline.initial);
    this.narrativeEditorControl.setValue(baseline.value, { emitEvent: false });
    this.narrativeEditorReasonControl.setValue('', { emitEvent: false });
    this.narrativeEditorError.set('');
    this.narrativeEditorErrorTarget.set('');
    this.narrativeEditorDraft = this.clinicalDrafts.acquire({
      patientId: workspace.patientId,
      label: this.narrativeEditorTitle()
    });
    this.narrativeEditorOpen.set(true);
    this.refreshNarrativeEditorDirtyState();
    this.focusSummaryPlanAfterRender('#singleNarrativeEditorContent');
  }

  closeNarrativeSectionEditor(): void {
    if (!this.narrativeEditorOpen() || this.narrativeEditorBusy()) return;
    if (this.narrativeEditorDraft && this.clinicalDrafts.isDirty(this.narrativeEditorDraft)
        && !window.confirm(`¿Descartar los cambios no guardados de ${this.narrativeEditorTitle()}?`)) return;
    this.finishNarrativeEditor(true);
  }

  async saveNarrativeSection(): Promise<void> {
    const workspace = this.workspaceService.workspace();
    if (!workspace || !this.narrativeEditorDraft || this.narrativeEditorBusy()) return;
    this.narrativeEditorError.set('');
    this.narrativeEditorErrorTarget.set('');
    if (workspace.patientId !== this.narrativeEditorDraft.patientId) {
      this.narrativeEditorError.set('El paciente activo cambió. Cierre este editor y vuelva a abrir la sección antes de guardar.');
      return;
    }
    let nextState: ClinicalState;
    try {
      nextState = this.applySingleNarrativeEdit(
        this.narrativeEditorKey(),
        workspace.state,
        this.narrativeEditorControl.value,
        this.narrativeEditorReasonControl.value
      );
    } catch (error) {
      this.narrativeEditorError.set(this.errorMessage(error, 'Revise los campos antes de guardar.'));
      const code = this.typedClinicalEditCode(error);
      const target = code === 'REASON_REQUIRED' || code === 'REASON_TOO_LONG'
        ? 'reason'
        : code.startsWith('EMPTY_') || code.endsWith('_TOO_LONG') ? 'content' : '';
      this.narrativeEditorErrorTarget.set(target);
      if (target) this.focusSummaryPlanAfterRender(target === 'reason' ? '#singleNarrativeEditorReason' : '#singleNarrativeEditorContent');
      return;
    }

    this.narrativeEditorBusy.set(true);
    try {
      await firstValueFrom(this.workspaceService.saveState(nextState));
      this.clinicalDrafts.markClean(this.narrativeEditorDraft);
      this.finishNarrativeEditor(true);
    } catch (error) {
      if (this.workspaceService.activeSaveConflict()) {
        this.clinicalDrafts.markClean(this.narrativeEditorDraft);
        this.finishNarrativeEditor(false);
      } else {
        this.narrativeEditorError.set(this.errorMessage(error, `No se pudo guardar ${this.narrativeEditorTitle().toLocaleLowerCase('es-AR')}.`));
      }
    } finally {
      this.narrativeEditorBusy.set(false);
    }
  }

  narrativeEditorCount(): number { return this.narrativeEditorControl.value.length; }
  narrativeSectionIsInitial(key: SingleNarrativeSectionKey): boolean {
    return this.singleNarrativeBaseline(key, this.state()).initial;
  }
  narrativeEditorTitle(): string {
    return this.narrativeEditorKey() === 'chiefComplaint'
      ? 'Motivo de consulta'
      : 'Antecedentes de enfermedad actual';
  }
  narrativeEditorIntro(): string {
    return this.narrativeEditorKey() === 'chiefComplaint'
      ? 'Registre el motivo que origina la consulta actual.'
      : 'Describa el inicio, la evolución y el estado actual de la enfermedad.';
  }
  narrativeEditorRows(): number { return this.narrativeEditorKey() === 'chiefComplaint' ? 5 : 8; }
  narrativeEditorMaxChars(): number {
    return this.narrativeEditorKey() === 'chiefComplaint'
      ? CLINICAL_CHIEF_COMPLAINT_TEXT_LIMIT
      : CLINICAL_CURRENT_ILLNESS_TEXT_LIMIT;
  }

  canEditPersonalHistory(): boolean {
    return Boolean(
      this.workspaceService.workspace()
      && !this.workspaceService.loading()
      && this.auth.hasPermission('section.history.edit')
      && supportsStructuredPersonalHistory(this.state())
    );
  }

  personalHistoryHasContent(): boolean {
    return Boolean(
      this.narrative('backgroundClinical')
      || this.narrative('currentMedication')
      || this.narrative('familyOncology')
      || this.narrative('gynecology')
    );
  }

  personalHistoryLegacyText(): string { return personalHistoryLegacySnapshot(this.state()); }

  personalHistoryIsInitial(): boolean { return personalHistoryBaseline(this.state()).initial; }

  openPersonalHistoryEditor(event?: Event): void {
    const workspace = this.workspaceService.workspace();
    if (!workspace || this.workspaceService.loading() || !this.canEditPersonalHistory() || this.workspaceService.hasPendingClinicalWork()) return;
    this.personalHistoryReturnFocus = event?.currentTarget instanceof HTMLElement
      ? event.currentTarget
      : document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const baseline = personalHistoryBaseline(workspace.state);
    this.personalHistoryEditorBaseline = baseline;
    this.personalHistoryInitial.set(baseline.initial);
    this.backgroundClinicalControl.setValue(baseline.backgroundClinical, { emitEvent: false });
    this.currentMedicationControl.setValue(baseline.currentMedication, { emitEvent: false });
    this.familyOncologyControl.setValue(baseline.familyOncology, { emitEvent: false });
    this.gynecologyControl.setValue(baseline.gynecology, { emitEvent: false });
    this.personalHistoryReasonControl.setValue('', { emitEvent: false });
    this.personalHistoryError.set('');
    this.personalHistoryErrorTarget.set('');
    this.personalHistoryDraft = this.clinicalDrafts.acquire({
      patientId: workspace.patientId,
      label: 'Antecedentes personales'
    });
    this.personalHistoryOpen.set(true);
    this.refreshPersonalHistoryDirtyState();
    this.focusSummaryPlanAfterRender('#personalHistoryBackgroundClinical');
  }

  closePersonalHistoryEditor(): void {
    if (!this.personalHistoryOpen() || this.personalHistoryBusy()) return;
    if (this.personalHistoryDraft && this.clinicalDrafts.isDirty(this.personalHistoryDraft)
        && !window.confirm('¿Descartar los cambios no guardados de Antecedentes personales?')) return;
    this.finishPersonalHistoryEditor(true);
  }

  async savePersonalHistory(): Promise<void> {
    const workspace = this.workspaceService.workspace();
    if (!workspace || !this.personalHistoryDraft || this.personalHistoryBusy()) return;
    this.personalHistoryError.set('');
    this.personalHistoryErrorTarget.set('');
    if (workspace.patientId !== this.personalHistoryDraft.patientId) {
      this.personalHistoryError.set('El paciente activo cambió. Cierre este editor y vuelva a abrir la sección antes de guardar.');
      return;
    }

    let nextState: ClinicalState;
    try {
      const user = this.auth.session()?.user;
      nextState = applyStructuredPersonalHistoryEdit(workspace.state, {
        backgroundClinical: this.backgroundClinicalControl.value,
        currentMedication: this.currentMedicationControl.value,
        familyOncology: this.familyOncologyControl.value,
        gynecology: this.gynecologyControl.value,
        reason: this.personalHistoryReasonControl.value,
        actor: {
          userId: user?.id || '',
          username: user?.username || '',
          displayName: user?.displayName || user?.username || 'Profesional',
          licenseNumber: user?.licenseNumber || ''
        }
      });
    } catch (error) {
      this.personalHistoryError.set(this.errorMessage(error, 'Revise los campos antes de guardar.'));
      const targets: Record<string, PersonalHistoryErrorTarget> = {
        EMPTY_PERSONAL_HISTORY: 'backgroundClinical',
        BACKGROUND_CLINICAL_TOO_LONG: 'backgroundClinical',
        CURRENT_MEDICATION_TOO_LONG: 'currentMedication',
        FAMILY_ONCOLOGY_TOO_LONG: 'familyOncology',
        GYNECOLOGY_TOO_LONG: 'gynecology',
        REASON_REQUIRED: 'reason',
        REASON_TOO_LONG: 'reason'
      };
      const target = targets[this.typedClinicalEditCode(error)] || '';
      this.personalHistoryErrorTarget.set(target);
      if (target) {
        const suffixes: Record<Exclude<PersonalHistoryErrorTarget, ''>, string> = {
          backgroundClinical: 'BackgroundClinical',
          currentMedication: 'CurrentMedication',
          familyOncology: 'FamilyOncology',
          gynecology: 'Gynecology',
          reason: 'Reason'
        };
        this.focusSummaryPlanAfterRender(`#personalHistory${suffixes[target]}`);
      }
      return;
    }

    this.personalHistoryBusy.set(true);
    try {
      await firstValueFrom(this.workspaceService.saveState(nextState));
      this.clinicalDrafts.markClean(this.personalHistoryDraft);
      this.finishPersonalHistoryEditor(true);
    } catch (error) {
      if (this.workspaceService.activeSaveConflict()) {
        this.clinicalDrafts.markClean(this.personalHistoryDraft);
        this.finishPersonalHistoryEditor(false);
      } else {
        this.personalHistoryError.set(this.errorMessage(error, 'No se pudieron guardar los antecedentes personales.'));
      }
    } finally {
      this.personalHistoryBusy.set(false);
    }
  }

  personalHistoryCount(control: FormControl<string>): number { return control.value.length; }

  canEditPhysicalExam(): boolean {
    return Boolean(
      this.workspaceService.workspace()
      && !this.workspaceService.loading()
      && this.auth.hasPermission('section.history.edit')
      && supportsStructuredPhysicalExam(this.state())
    );
  }

  physicalExamHasContent(): boolean {
    return Boolean(this.exam('weightKg') || this.exam('heightM') || this.narrative('physicalExam'));
  }

  physicalExamLegacyText(): string { return physicalExamLegacySnapshot(this.state()); }
  physicalExamHeightCm(): string { return physicalExamBaseline(this.state()).heightCm; }
  physicalExamIsInitial(): boolean { return physicalExamBaseline(this.state()).initial; }
  physicalExamPresentationRows(): ClinicalPhysicalExamRow[] {
    return projectPhysicalExamRows(this.narrative('physicalExam'));
  }
  physicalExamStoredBmi(): string {
    const baseline = physicalExamBaseline(this.state());
    const metrics = calculatePhysicalExamMetrics(baseline.weightKg, baseline.heightCm);
    return metrics.bmi === null ? '' : metrics.bmi.toFixed(2);
  }
  physicalExamStoredBodySurface(): string {
    const baseline = physicalExamBaseline(this.state());
    const metrics = calculatePhysicalExamMetrics(baseline.weightKg, baseline.heightCm);
    return metrics.bodySurfaceM2 === null ? '' : `${metrics.bodySurfaceM2.toFixed(3)} m²`;
  }
  physicalExamBmi(): string {
    const metrics = calculatePhysicalExamMetrics(this.physicalExamWeightControl.value, this.physicalExamHeightControl.value);
    return metrics.bmi === null ? '—' : metrics.bmi.toFixed(2);
  }
  physicalExamBodySurface(): string {
    const metrics = calculatePhysicalExamMetrics(this.physicalExamWeightControl.value, this.physicalExamHeightControl.value);
    return metrics.bodySurfaceM2 === null ? '—' : `${metrics.bodySurfaceM2.toFixed(3)} m²`;
  }

  openPhysicalExamEditor(event?: Event): void {
    const workspace = this.workspaceService.workspace();
    if (!workspace || this.workspaceService.loading() || !this.canEditPhysicalExam() || this.workspaceService.hasPendingClinicalWork()) return;
    this.physicalExamReturnFocus = event?.currentTarget instanceof HTMLElement
      ? event.currentTarget
      : document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const baseline = physicalExamBaseline(workspace.state);
    this.physicalExamEditorBaseline = baseline;
    this.physicalExamInitial.set(baseline.initial);
    this.physicalExamWeightControl.setValue(baseline.weightKg, { emitEvent: false });
    this.physicalExamHeightControl.setValue(baseline.heightCm, { emitEvent: false });
    this.physicalExamTextControl.setValue(baseline.physicalExam, { emitEvent: false });
    this.physicalExamReasonControl.setValue('', { emitEvent: false });
    this.physicalExamError.set('');
    this.physicalExamErrorTarget.set('');
    this.physicalExamDraft = this.clinicalDrafts.acquire({
      patientId: workspace.patientId,
      label: 'Examen físico'
    });
    this.physicalExamOpen.set(true);
    this.refreshPhysicalExamDirtyState();
    this.focusSummaryPlanAfterRender('#physicalExamWeight');
  }

  prefillPhysicalExam(): void {
    if (this.physicalExamBusy()) return;
    if (this.physicalExamTextControl.value.trim()) {
      this.physicalExamError.set('El examen físico ya contiene texto. La plantilla no lo sobrescribió.');
      this.physicalExamErrorTarget.set('physicalExam');
      this.focusSummaryPlanAfterRender('#physicalExamText');
      return;
    }
    this.physicalExamError.set('');
    this.physicalExamErrorTarget.set('');
    this.physicalExamTextControl.setValue(DEFAULT_PHYSICAL_EXAM_TEXT);
    this.focusSummaryPlanAfterRender('#physicalExamText');
  }

  closePhysicalExamEditor(): void {
    if (!this.physicalExamOpen() || this.physicalExamBusy()) return;
    // Recalcular contra la línea de base al cerrar evita depender del orden de
    // emisión de valueChanges para una confirmación que protege datos clínicos.
    if (this.physicalExamDraft && this.physicalExamEditorIsDirty()
        && !window.confirm('¿Descartar los cambios no guardados de Examen físico?')) return;
    this.finishPhysicalExamEditor(true);
  }

  async savePhysicalExam(): Promise<void> {
    const workspace = this.workspaceService.workspace();
    if (!workspace || !this.physicalExamDraft || this.physicalExamBusy()) return;
    this.physicalExamError.set('');
    this.physicalExamErrorTarget.set('');
    if (workspace.patientId !== this.physicalExamDraft.patientId) {
      this.physicalExamError.set('El paciente activo cambió. Cierre este editor y vuelva a abrir la sección antes de guardar.');
      return;
    }
    let nextState: ClinicalState;
    try {
      const user = this.auth.session()?.user;
      nextState = applyStructuredPhysicalExamEdit(workspace.state, {
        weightKg: this.physicalExamWeightControl.value,
        heightCm: this.physicalExamHeightControl.value,
        physicalExam: this.physicalExamTextControl.value,
        reason: this.physicalExamReasonControl.value,
        actor: {
          userId: user?.id || '',
          username: user?.username || '',
          displayName: user?.displayName || user?.username || 'Profesional',
          licenseNumber: user?.licenseNumber || ''
        }
      });
    } catch (error) {
      this.physicalExamError.set(this.errorMessage(error, 'Revise los campos antes de guardar.'));
      const targets: Record<string, PhysicalExamErrorTarget> = {
        EMPTY_PHYSICAL_EXAM: 'weightKg',
        WEIGHT_INVALID: 'weightKg', WEIGHT_OUT_OF_RANGE: 'weightKg',
        HEIGHT_INVALID: 'heightCm', HEIGHT_OUT_OF_RANGE: 'heightCm',
        TEXT_TOO_LONG: 'physicalExam', REASON_REQUIRED: 'reason', REASON_TOO_LONG: 'reason'
      };
      const target = targets[this.typedClinicalEditCode(error)] || '';
      this.physicalExamErrorTarget.set(target);
      const selectors: Record<Exclude<PhysicalExamErrorTarget, ''>, string> = {
        weightKg: '#physicalExamWeight', heightCm: '#physicalExamHeight',
        physicalExam: '#physicalExamText', reason: '#physicalExamReason'
      };
      if (target) this.focusSummaryPlanAfterRender(selectors[target]);
      return;
    }

    this.physicalExamBusy.set(true);
    try {
      await firstValueFrom(this.workspaceService.saveState(nextState));
      this.clinicalDrafts.markClean(this.physicalExamDraft);
      this.finishPhysicalExamEditor(true);
    } catch (error) {
      if (this.workspaceService.activeSaveConflict()) {
        this.clinicalDrafts.markClean(this.physicalExamDraft);
        this.finishPhysicalExamEditor(false);
      } else {
        this.physicalExamError.set(this.errorMessage(error, 'No se pudo guardar el examen físico.'));
      }
    } finally {
      this.physicalExamBusy.set(false);
    }
  }

  physicalExamCount(): number { return this.physicalExamTextControl.value.length; }

  canEditSummaryPlan(): boolean {
    return Boolean(
      this.workspaceService.workspace()
      && !this.workspaceService.loading()
      && this.auth.hasPermission('section.history.edit')
      && supportsStructuredSummaryPlan(this.state())
    );
  }

  openSummaryPlanEditor(event?: Event): void {
    const workspace = this.workspaceService.workspace();
    if (!workspace || !this.canEditSummaryPlan() || this.workspaceService.hasPendingClinicalWork()) return;
    this.summaryPlanReturnFocus = event?.currentTarget instanceof HTMLElement
      ? event.currentTarget
      : document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const baseline = summaryPlanBaseline(workspace.state);
    this.summaryPlanBaseline = baseline;
    this.summaryPlanInitial.set(baseline.initial);
    this.summaryControl.setValue(baseline.summary, { emitEvent: false });
    this.planControl.setValue(baseline.plan, { emitEvent: false });
    this.summaryPlanReasonControl.setValue('', { emitEvent: false });
    this.summaryPlanError.set('');
    this.summaryPlanDraft = this.clinicalDrafts.acquire({
      patientId: workspace.patientId,
      label: 'Conclusión / resumen'
    });
    this.summaryPlanOpen.set(true);
    this.refreshSummaryPlanDirtyState();
    this.focusSummaryPlanAfterRender('#summaryPlanSummary');
  }

  closeSummaryPlanEditor(): void {
    if (!this.summaryPlanOpen() || this.summaryPlanBusy()) return;
    if (this.summaryPlanDraft && this.clinicalDrafts.isDirty(this.summaryPlanDraft)
        && !window.confirm('¿Descartar los cambios no guardados de Conclusión / resumen?')) return;
    this.finishSummaryPlanEditor(true);
  }

  trapSummaryPlanFocus(event: KeyboardEvent): void {
    if (event.key !== 'Tab') return;
    const dialog = event.currentTarget instanceof HTMLElement ? event.currentTarget : null;
    if (!dialog) return;
    const focusable = [...dialog.querySelectorAll<HTMLElement>(
      'button:not([disabled]), input:not([disabled]), textarea:not([disabled]), select:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])'
    )].filter((element) => element.getAttribute('aria-hidden') !== 'true');
    if (!focusable.length) {
      event.preventDefault();
      dialog.focus();
      return;
    }
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    const active = document.activeElement;
    if (event.shiftKey && (active === first || !dialog.contains(active))) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && (active === last || !dialog.contains(active))) {
      event.preventDefault();
      first.focus();
    }
  }

  async saveSummaryPlan(): Promise<void> {
    const workspace = this.workspaceService.workspace();
    if (!workspace || !this.summaryPlanDraft || this.summaryPlanBusy()) return;
    this.summaryPlanError.set('');
    if (workspace.patientId !== this.summaryPlanDraft.patientId) {
      this.summaryPlanError.set('El paciente activo cambió. Cierre este editor y vuelva a abrir la sección antes de guardar.');
      return;
    }
    let nextState: ClinicalState;
    try {
      const user = this.auth.session()?.user;
      nextState = applyStructuredSummaryPlanEdit(workspace.state, {
        summary: this.summaryControl.value,
        plan: this.planControl.value,
        reason: this.summaryPlanReasonControl.value,
        actor: {
          userId: user?.id || '',
          username: user?.username || '',
          displayName: user?.displayName || user?.username || 'Profesional',
          licenseNumber: user?.licenseNumber || ''
        }
      });
    } catch (error) {
      this.summaryPlanError.set(this.errorMessage(error, 'Revise los campos antes de guardar.'));
      return;
    }

    this.summaryPlanBusy.set(true);
    try {
      await firstValueFrom(this.workspaceService.saveState(nextState));
      this.clinicalDrafts.markClean(this.summaryPlanDraft);
      this.finishSummaryPlanEditor(true);
    } catch (error) {
      if (this.workspaceService.activeSaveConflict()) {
        // El servicio conserva una copia profunda del intento; el banner pasa a
        // ser el propietario visible del borrador y evita una doble protección.
        this.clinicalDrafts.markClean(this.summaryPlanDraft);
        this.finishSummaryPlanEditor(false);
      } else {
        this.summaryPlanError.set(this.errorMessage(error, 'No se pudo guardar la conclusión / resumen.'));
      }
    } finally {
      this.summaryPlanBusy.set(false);
    }
  }

  summaryPlanCount(control: FormControl<string>): number { return control.value.length; }

  state(): ClinicalState { return this.workspaceService.workingWorkspace()?.state || {}; }
  records(key: 'diagnoses' | 'studies' | 'treatments' | 'evolutions' | 'prescriptions' | 'researchRecords'): ClinicalRecord[] { return this.state()[key] || []; }
  studyEntries(): ClinicalStudyEntry[] { return clinicalStudyEntries(this.state(), 'asc'); }
  canViewStudies(): boolean { return this.auth.hasPermission('section.studies.view'); }
  canEditStudies(): boolean { return this.auth.hasPermission('section.studies.edit'); }
  openStudies(entry?: ClinicalStudyEntry, upload = false): void {
    if (!this.workspaceService.workspace() || this.workspaceService.loading()
        || !this.canViewStudies() || this.workspaceService.hasPendingClinicalWork()) return;
    this.studiesRequested.emit({
      mode: upload && this.canEditStudies() ? 'upload' : 'browse',
      studyKey: entry?.key
    });
  }
  narrative(key: string): string { return this.text(this.state().narrative?.[key]); }
  oncology(key: string): string { return this.text(this.state().oncology?.[key]); }
  exam(key: string): string { return this.text(this.state().exam?.[key]); }
  text(value: unknown): string { return typeof value === 'string' || typeof value === 'number' ? String(value) : ''; }
  date(value?: string): string { if (!value) return ''; const parsed = new Date(`${value.length === 10 ? `${value}T12:00:00` : value}`); return Number.isNaN(parsed.getTime()) ? value : new Intl.DateTimeFormat('es-AR').format(parsed); }
  diagnosisDate(value: string): string { return /^\d{4}(?:-\d{2})?$/.test(value) ? value : this.date(value); }
  patientLine(patient: ClinicalPatient): string { return [`HC ${patient.medicalRecord || '—'}`, `DNI ${patient.dni || '—'}`, patient.insurance ? `Obra social ${patient.insurance}` : '', patient.affiliateNumber ? `Afiliado ${patient.affiliateNumber}` : ''].filter(Boolean).join(' · '); }
  recordTitle(record: ClinicalRecord): string { return this.text(record.diagnosis) || this.text(record.title) || this.text(record.scheme) || this.text(record.reason) || 'Registro clínico'; }
  recordBody(record: ClinicalRecord): string { return this.text(record.text) || this.text(record.summary) || this.text(record.status); }
  treatmentBody(record: ClinicalRecord): string { return clinicalTreatmentBody(record); }
  studyHeading(record: ClinicalRecord): string { return this.join(this.date(record.date), this.text(record.type), this.text(record.title)); }
  treatmentHeading(record: ClinicalRecord): string { return this.join(this.date(record.date), this.text(record.scheme) || this.text(record.title) || this.text(record.reason)); }
  treatmentRecords(kind: ClinicalTreatmentKind): ClinicalRecord[] {
    return clinicalSectionTreatments(
      this.state(),
      kind,
      this.workspaceService.workingWorkspace()?.treatments?.oncology || []
    );
  }
  printHas(section: ClinicalPrintSection): boolean {
    return clinicalPrintSectionHasContent(
      this.state(),
      section,
      this.workspaceService.workingWorkspace()?.treatments?.oncology || []
    );
  }
  printFacts(patient: ClinicalPatient): ClinicalPrintFact[] { return clinicalPrintPatientFacts(patient); }
  printFactValue(fact: ClinicalPrintFact): string {
    return fact.label === 'Fecha de nacimiento' ? this.date(fact.value) : fact.value;
  }
  printDateTime(): string {
    const value = this.printTimestamp();
    if (!value) return '';
    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? value : new Intl.DateTimeFormat('es-AR', {
      dateStyle: 'short', timeStyle: 'short'
    }).format(parsed);
  }
  evolutionHeading(record: ClinicalRecord): string { return this.join(this.date(record.date), this.text(record.author) || this.text(record.reason)); }
  evolutionAttachments(record: ClinicalRecord): EvolutionImageAttachment[] {
    return this.activityHighlightRecordType(record) === 'evolution' ? normalizeEvolutionAttachments(record.attachments) : [];
  }
  activityRecords(): ClinicalRecord[] {
    return [...this.records('evolutions'), ...this.records('prescriptions'), ...this.records('researchRecords')]
      .sort((left, right) => {
        const leftKey = [left.date || String(left.createdAt || '').slice(0, 10), left.createdAt || left.updatedAt || ''].join('|');
        const rightKey = [right.date || String(right.createdAt || '').slice(0, 10), right.createdAt || right.updatedAt || ''].join('|');
        return leftKey.localeCompare(rightKey);
      });
  }
  activityHighlightRecordType(record: ClinicalRecord): 'evolution' | 'prescription' | 'research' {
    const id = String(record.id || '');
    const research = this.records('researchRecords');
    const prescriptions = this.records('prescriptions');
    if (id) {
      if (research.some((candidate) => String(candidate.id || '') === id)) return 'research';
      if (prescriptions.some((candidate) => String(candidate.id || '') === id)) return 'prescription';
    } else {
      if (research.includes(record)) return 'research';
      if (prescriptions.includes(record)) return 'prescription';
    }
    return 'evolution';
  }
  saveClinicalHighlight(event: ClinicalHighlightMutation): void {
    this.workspaceService.saveState(event.state).subscribe({
      next: () => event.commit(),
      error: () => event.rollback()
    });
  }
  activityHeading(record: ClinicalRecord): string {
    const type = this.text(record.type);
    const label = ({ medication: 'Receta médica', certificate: 'Certificado médico', study: 'Solicitud de estudio', free: 'Indicación médica', systemic: 'Formulario sistémico' } as Record<string, string>)[type];
    return this.join(this.date(record.date), label || this.text(record.author) || this.text(record.reason), this.text(record.title));
  }
  prescriptionDetails(record: ClinicalRecord): string[] {
    const data = record['data'] && typeof record['data'] === 'object' ? record['data'] as Record<string, unknown> : {};
    const value = (key: string): string => this.text(data[key]);
    if (record.type === 'medication') return [
      this.join(value('generic'), value('brand')),
      [value('presentation'), value('form'), value('quantity')].filter(Boolean).join(' - '),
      [value('dose'), value('route'), value('frequency'), value('duration')].filter(Boolean).join(' - '),
      value('indication') ? `Indicación: ${value('indication')}` : '',
      value('instructions') ? `Instrucciones: ${value('instructions')}` : ''
    ].filter(Boolean);
    if (record.type === 'certificate') return [
      [value('from') ? `Desde ${this.date(value('from'))}` : '', value('to') ? `hasta ${this.date(value('to'))}` : ''].filter(Boolean).join(' '),
      value('text'), data['includeDiagnosis'] === true ? 'Incluye diagnóstico' : ''
    ].filter(Boolean);
    if (record.type === 'study') return [
      [value('category'), value('priority')].filter(Boolean).join(' - '), value('name'),
      value('indication') ? `Indicación clínica: ${value('indication')}` : '',
      value('notes') ? `Preparación / observaciones: ${value('notes')}` : ''
    ].filter(Boolean);
    if (record.type === 'systemic') {
      const fields = Array.isArray(data['fields']) ? data['fields'] as Array<Record<string, unknown>> : [];
      const seen = new Set<string>();
      const clinicalLines = fields.map((field) => {
        if (field['kind'] === 'checkbox') return field['value'] === true ? this.text(field['label']) : '';
        const fieldValue = this.text(field['value']);
        const localKey = this.text(field['localKey']);
        if (!fieldValue || /^(patient|professional|exam)\./.test(localKey)
            || ['today', 'todayWithCity', 'mendozaToday', 'alwaysTrue', 'currentYear', 'blank'].includes(localKey)) return '';
        return `${this.text(field['label'])}: ${fieldValue}`;
      }).filter((line) => {
        const key = line.toLocaleLowerCase('es-AR').replace(/\s+/g, ' ').trim();
        if (!key || seen.has(key)) return false;
        seen.add(key); return true;
      });
      const pages = Array.isArray(data['pages']) ? data['pages'].length : 0;
      return [value('formTitle') || this.text(record.title), ...clinicalLines, `${pages} ${pages === 1 ? 'página' : 'páginas'}`].filter(Boolean);
    }
    return [value('title'), value('text'), this.recordBody(record)].filter(Boolean);
  }
  activityAudit(record: ClinicalRecord): string {
    const audit = record['audit'] && typeof record['audit'] === 'object' ? record['audit'] as Record<string, unknown> : {};
    const author = [this.text(audit['lastName']) || this.text(record.author), this.text(audit['license']) ? `Mat. ${this.text(audit['license'])}` : ''].filter(Boolean).join(' · ');
    return author ? `${this.text(audit['action']) || 'cargado'} por ${author}` : '';
  }
  private join(...values: string[]): string { return values.filter((value) => Boolean(value)).join(' · '); }

  private applyClinicalFocus(request: ClinicalFocusRequest): void {
    if (this.clinicalFocus.request().id !== request.id) return;
    const root = this.host.nativeElement.querySelector<HTMLElement>('#clinicalDocument');
    if (!root) return;
    this.clearClinicalNavigationFocus();
    const rendered = renderClinicalFocusHighlights(root, request.highlights || []);
    const target = findClinicalFocusTarget(root, request);
    if (!target) {
      if (request.date || request.text) this.announceMissingClinicalFocus(request.id);
      else rendered.firstMark?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      return;
    }
    this.missingClinicalFocusRequest = 0;
    target.classList.add('agent-navigation-focus');
    target.scrollIntoView({ behavior: 'smooth', block: 'center' });
    this.clinicalFocusTimer = setTimeout(() => {
      target.classList.remove('agent-navigation-focus');
      this.clinicalFocusTimer = null;
    }, 5_000);
  }

  private clearClinicalNavigationFocus(): void {
    if (this.clinicalFocusTimer) clearTimeout(this.clinicalFocusTimer);
    this.clinicalFocusTimer = null;
    this.host.nativeElement.querySelectorAll<HTMLElement>('.agent-navigation-focus')
      .forEach((element) => element.classList.remove('agent-navigation-focus'));
  }

  private announceMissingClinicalFocus(requestId: number): void {
    if (this.missingClinicalFocusRequest === requestId) return;
    this.missingClinicalFocusRequest = requestId;
    this.clinicalHighlightFeedback.announce('NOT_FOUND', 'No se encontró el registro en la historia clínica');
  }

  private normalizeSearch(value: string): string {
    return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('es-AR').replace(/\s+/g, ' ').trim();
  }

  private refreshSummaryPlanDirtyState(): void {
    if (!this.summaryPlanDraft) return;
    // La detección usa la misma normalización que el guardado: espacios solos
    // no deben dejar un borrador fantasma ni pedir confirmación al cerrar.
    const dirty = this.summaryControl.value.trim() !== this.summaryPlanBaseline.summary
      || this.planControl.value.trim() !== this.summaryPlanBaseline.plan
      || this.summaryPlanReasonControl.value.trim().length > 0;
    this.clinicalDrafts.setDirty(this.summaryPlanDraft, dirty);
  }

  private refreshNarrativeEditorDirtyState(): void {
    if (!this.narrativeEditorDraft) return;
    const dirty = this.narrativeEditorControl.value.trim() !== this.narrativeEditorBaseline.value
      || this.narrativeEditorReasonControl.value.trim().length > 0;
    this.clinicalDrafts.setDirty(this.narrativeEditorDraft, dirty);
  }

  private refreshPersonalHistoryDirtyState(): void {
    if (!this.personalHistoryDraft) return;
    const baseline = this.personalHistoryEditorBaseline;
    const dirty = this.backgroundClinicalControl.value.trim() !== baseline.backgroundClinical
      || this.currentMedicationControl.value.trim() !== baseline.currentMedication
      || this.familyOncologyControl.value.trim() !== baseline.familyOncology
      || this.gynecologyControl.value.trim() !== baseline.gynecology
      || this.personalHistoryReasonControl.value.trim().length > 0;
    this.clinicalDrafts.setDirty(this.personalHistoryDraft, dirty);
  }

  private refreshPhysicalExamDirtyState(): void {
    if (!this.physicalExamDraft) return;
    this.clinicalDrafts.setDirty(this.physicalExamDraft, this.physicalExamEditorIsDirty());
  }

  private physicalExamEditorIsDirty(): boolean {
    const baseline = this.physicalExamEditorBaseline;
    return this.normalizedPhysicalNumber(this.physicalExamWeightControl.value) !== baseline.weightKg
      || this.normalizedPhysicalNumber(this.physicalExamHeightControl.value, true) !== baseline.heightCm
      || this.physicalExamTextControl.value.trim() !== baseline.physicalExam
      || this.physicalExamReasonControl.value.trim().length > 0;
  }

  private normalizedPhysicalNumber(value: string | number | null | undefined, height = false): string {
    // Los input[type=number] usan NumberValueAccessor y pueden entregar un
    // número en ejecución aunque el control conserve su contrato textual.
    const raw = String(value ?? '').trim().replace(',', '.');
    if (!raw) return '';
    const numeric = Number(raw);
    if (!Number.isFinite(numeric)) return raw;
    return String(height ? Math.round(numeric * 10) / 10 : numeric);
  }

  private releasePhysicalExamDraft(): void {
    if (!this.physicalExamDraft) return;
    this.clinicalDrafts.release(this.physicalExamDraft);
    this.physicalExamDraft = null;
  }

  private finishPhysicalExamEditor(returnFocus: boolean): void {
    this.releasePhysicalExamDraft();
    this.physicalExamOpen.set(false);
    this.physicalExamError.set('');
    this.physicalExamErrorTarget.set('');
    const trigger = this.physicalExamReturnFocus;
    this.physicalExamReturnFocus = null;
    if (returnFocus && trigger) window.setTimeout(() => {
      if (trigger.isConnected && !trigger.hasAttribute('disabled')) trigger.focus({ preventScroll: true });
    }, 0);
  }

  private releasePersonalHistoryDraft(): void {
    if (!this.personalHistoryDraft) return;
    this.clinicalDrafts.release(this.personalHistoryDraft);
    this.personalHistoryDraft = null;
  }

  private finishPersonalHistoryEditor(returnFocus: boolean): void {
    this.releasePersonalHistoryDraft();
    this.personalHistoryOpen.set(false);
    this.personalHistoryError.set('');
    this.personalHistoryErrorTarget.set('');
    const trigger = this.personalHistoryReturnFocus;
    this.personalHistoryReturnFocus = null;
    if (returnFocus && trigger) window.setTimeout(() => {
      if (trigger.isConnected && !trigger.hasAttribute('disabled')) trigger.focus({ preventScroll: true });
    }, 0);
  }

  private releaseNarrativeEditorDraft(): void {
    if (!this.narrativeEditorDraft) return;
    this.clinicalDrafts.release(this.narrativeEditorDraft);
    this.narrativeEditorDraft = null;
  }

  private finishNarrativeEditor(returnFocus: boolean): void {
    this.releaseNarrativeEditorDraft();
    this.narrativeEditorOpen.set(false);
    this.narrativeEditorError.set('');
    this.narrativeEditorErrorTarget.set('');
    const trigger = this.narrativeEditorReturnFocus;
    this.narrativeEditorReturnFocus = null;
    if (returnFocus && trigger) window.setTimeout(() => {
      if (trigger.isConnected && !trigger.hasAttribute('disabled')) trigger.focus({ preventScroll: true });
    }, 0);
  }

  private supportsNarrativeSection(key: SingleNarrativeSectionKey, state: ClinicalState): boolean {
    return key === 'chiefComplaint'
      ? supportsStructuredChiefComplaint(state)
      : supportsStructuredCurrentIllness(state);
  }

  private singleNarrativeBaseline(
    key: SingleNarrativeSectionKey,
    state: ClinicalState
  ): { value: string; initial: boolean } {
    if (key === 'chiefComplaint') {
      const baseline = chiefComplaintBaseline(state);
      return { value: baseline.chiefComplaint, initial: baseline.initial };
    }
    const baseline = currentIllnessBaseline(state);
    return { value: baseline.currentIllness, initial: baseline.initial };
  }

  private applySingleNarrativeEdit(
    key: SingleNarrativeSectionKey,
    state: ClinicalState,
    value: string,
    reason: string
  ): ClinicalState {
    const user = this.auth.session()?.user;
    const actor = {
      userId: user?.id || '',
      username: user?.username || '',
      displayName: user?.displayName || user?.username || 'Profesional',
      licenseNumber: user?.licenseNumber || ''
    };
    return key === 'chiefComplaint'
      ? applyStructuredChiefComplaintEdit(state, { chiefComplaint: value, reason, actor })
      : applyStructuredCurrentIllnessEdit(state, { currentIllness: value, reason, actor });
  }

  private typedClinicalEditCode(error: unknown): string {
    if (!error || typeof error !== 'object' || !('code' in error)) return '';
    return typeof error.code === 'string' ? error.code : '';
  }

  private releaseSummaryPlanDraft(): void {
    if (!this.summaryPlanDraft) return;
    this.clinicalDrafts.release(this.summaryPlanDraft);
    this.summaryPlanDraft = null;
  }

  private finishSummaryPlanEditor(returnFocus: boolean): void {
    this.releaseSummaryPlanDraft();
    this.summaryPlanOpen.set(false);
    this.summaryPlanError.set('');
    const trigger = this.summaryPlanReturnFocus;
    this.summaryPlanReturnFocus = null;
    if (returnFocus && trigger) window.setTimeout(() => {
      if (trigger.isConnected && !trigger.hasAttribute('disabled')) trigger.focus({ preventScroll: true });
    }, 0);
  }

  private focusSummaryPlanAfterRender(selector: string): void {
    window.setTimeout(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus({ preventScroll: true }), 0);
  }

  private errorMessage(error: unknown, fallback: string): string {
    return error instanceof Error && error.message ? error.message : fallback;
  }
}
