import { Component, ElementRef, OnDestroy, OnInit, computed, effect, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription, firstValueFrom } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { ClinicalFocusService } from '../../core/clinical/clinical-focus.service';
import { ClinicalDraftHandle, ClinicalDraftRegistryService } from '../../core/patients/clinical-draft-registry.service';
import { PatientWorkspaceService } from '../../core/patients/patient-workspace.service';
import {
  ResearchAuditStamp,
  ResearchGeneralForm,
  ResearchRecord,
  ResearchRecordLine,
  ResearchTemplate,
  ResearchTemplateField,
  blankResearchForm,
  buildCustomResearchRecord,
  buildGeneralResearchRecord,
  dateLabel,
  initialCustomValues,
  researchRecordLines,
  researchRecords,
  validateResearchRecord
} from './research.models';
import { ResearchApiFailure, ResearchService, normalizeResearchFailure } from './research.service';

@Component({
  selector: 'app-research',
  imports: [FormsModule],
  host: {
    class: 'right-tab-panel active angular-research-panel',
    'data-right-panel': 'research',
    role: 'tabpanel'
  },
  templateUrl: './research.component.html',
  styleUrl: './research.component.scss'
})
export class ResearchComponent implements OnInit, OnDestroy {
  readonly focusRequested = output<{ id: string; date: string; text: string }>();
  readonly workspace = inject(PatientWorkspaceService);
  readonly auth = inject(AuthService);
  private readonly research = inject(ResearchService);
  private readonly clinicalFocus = inject(ClinicalFocusService);
  private readonly clinicalDrafts = inject(ClinicalDraftRegistryService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly subscriptions = new Subscription();
  private draft: ClinicalDraftHandle | null = null;
  private lastPatientId: string | null = null;

  readonly templates = signal<readonly ResearchTemplate[]>([]);
  readonly templateId = signal('');
  readonly customValues = signal<Record<string, string | boolean>>({});
  readonly templateLoading = signal(false);
  readonly templateMessage = signal('');
  readonly busy = signal(false);
  readonly message = signal('');
  readonly messageKind = signal<'ready' | 'error' | 'info'>('info');
  readonly standaloneModel = { standalone: true } as const;
  form: ResearchGeneralForm = blankResearchForm();

  readonly selectedTemplate = computed(() => this.templates().find((item) => item.id === this.templateId()) || null);
  readonly records = computed(() => researchRecords(this.workspace.workingWorkspace()?.state.researchRecords));
  readonly canEdit = computed(() => Boolean(
    this.workspace.workspace()
    && this.auth.hasPermission('section.research.edit')
    && this.auth.hasPermission('section.history.edit')
  ));

  constructor() {
    effect(() => {
      const current = this.workspace.workspace();
      const patientId = current?.patientId || null;
      if (patientId === this.lastPatientId) return;
      this.releaseDraft();
      this.lastPatientId = patientId;
      this.form = blankResearchForm(current?.state || {}, current?.patient);
      const template = this.selectedTemplate();
      this.customValues.set(template ? initialCustomValues(template) : {});
      this.message.set('');
    });
  }

  ngOnInit(): void {
    this.subscriptions.add(this.research.invalidated$.subscribe(() => this.loadTemplates(true)));
    this.loadTemplates(false);
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    this.releaseDraft();
  }

  loadTemplates(force = false): void {
    this.templateLoading.set(true);
    this.templateMessage.set('');
    this.subscriptions.add(this.research.templates(force).subscribe({
      next: (catalog) => {
        this.templates.set(catalog.items);
        if (this.templateId() && !catalog.items.some((item) => item.id === this.templateId())) {
          this.templateId.set('');
          this.customValues.set({});
        }
        this.templateLoading.set(false);
      },
      error: (failure: ResearchApiFailure) => {
        this.templates.set([]);
        this.templateId.set('');
        this.customValues.set({});
        this.templateMessage.set(failure.status === 403
          ? 'El registro general está disponible. Su rol no permite consultar formularios configurables.'
          : `${failure.message} Puede continuar con el registro general.`);
        this.templateLoading.set(false);
      }
    }));
  }

  selectTemplate(value: string): void {
    const nextId = String(value || '');
    if (nextId === this.templateId()) return;
    if (this.draft && this.clinicalDrafts.isDirty(this.draft)
        && !window.confirm('¿Descartar los datos no guardados y cambiar de formulario?')) return;
    this.releaseDraft();
    this.templateId.set(nextId);
    const template = this.templates().find((item) => item.id === nextId);
    this.customValues.set(template ? initialCustomValues(template) : {});
    this.message.set('');
  }

  markDirty(): void {
    this.message.set('');
    const patientId = this.workspace.workspace()?.patientId;
    if (!patientId || !this.canEdit()) return;
    this.draft ||= this.clinicalDrafts.acquire({ patientId, label: 'Registro de investigación' });
    this.clinicalDrafts.setDirty(this.draft, true);
  }

  updateCustomValue(field: ResearchTemplateField, value: unknown): void {
    const normalized = field.type === 'checkbox' ? value === true : String(value ?? '');
    this.customValues.update((current) => ({ ...current, [field.key]: normalized }));
    this.markDirty();
  }

  customValue(field: ResearchTemplateField): string | boolean {
    const value = this.customValues()[field.key];
    return field.type === 'checkbox' ? value === true : String(value ?? '');
  }

  reset(): void {
    this.releaseDraft();
    const current = this.workspace.workspace();
    this.form = blankResearchForm(current?.state || {}, current?.patient);
    const template = this.selectedTemplate();
    this.customValues.set(template ? initialCustomValues(template) : {});
    this.message.set('');
  }

  async save(): Promise<void> {
    const current = this.workspace.workspace();
    if (!current) { this.fail('Abra un paciente antes de registrar investigación.'); return; }
    if (!this.canEdit()) { this.fail('Su usuario no tiene permiso para incorporar registros de investigación.'); return; }
    if (this.busy()) return;

    const template = this.selectedTemplate();
    const audit = this.auditStamp();
    const record = template
      ? buildCustomResearchRecord(template, this.customValues(), current.patient, audit)
      : buildGeneralResearchRecord(this.form, audit);
    const validation = validateResearchRecord(record);
    if (validation) {
      this.fail(validation.message);
      this.focusValidation(validation.target);
      return;
    }

    const patientId = current.patientId;
    const preserved = template ? undefined : {
      protocolName: this.form.protocolName,
      protocolCode: this.form.protocolCode,
      phase: this.form.phase,
      sponsor: this.form.sponsor,
      center: this.form.center,
      participantCode: this.form.participantCode
    };
    this.busy.set(true);
    this.message.set('Guardando el registro en la historia clínica…');
    this.messageKind.set('info');
    try {
      await firstValueFrom(this.research.saveRecord(record));
      if (this.workspace.workspace()?.patientId !== patientId) {
        this.fail('El paciente activo cambió durante el guardado. El registro no se aplicó a la ficha abierta.');
        return;
      }
      this.releaseDraft();
      const saved = this.workspace.workspace();
      this.form = blankResearchForm(saved?.state || {}, saved?.patient, preserved);
      this.customValues.set(template ? initialCustomValues(template) : {});
      this.message.set('Registro de investigación incorporado a la historia clínica.');
      this.messageKind.set('ready');
    } catch (error: unknown) {
      if (this.workspace.activeSaveConflict()) this.releaseDraft();
      const failure = normalizeResearchFailure(error, 'No se pudo guardar el registro de investigación.');
      this.fail(failure.message);
    } finally {
      this.busy.set(false);
    }
  }

  focusRecord(record: ResearchRecord): void {
    const text = [record.protocol?.code, record.protocol?.name, record.type].filter(Boolean).join(' ');
    this.focusRequested.emit({ id: record.id, date: record.date, text });
    this.clinicalFocus.focus({ date: record.date, text, highlights: [{ terms: [record.protocol?.name || '', record.protocol?.code || ''].filter(Boolean), color: 'evolution' }] });
  }

  recordLines(record: ResearchRecord): readonly ResearchRecordLine[] { return researchRecordLines(record); }
  date(value: string): string { return dateLabel(value); }
  recordCode(record: ResearchRecord): string { return record.protocol?.code || 'Protocolo'; }
  recordTitle(record: ResearchRecord): string { return record.protocol?.name || record.title || 'Investigación oncológica'; }
  recordType(record: ResearchRecord): string { return record.type || 'Registro'; }

  private auditStamp(): ResearchAuditStamp {
    const user = this.auth.session()?.user;
    const displayName = String(user?.displayName || user?.username || 'Profesional').trim();
    const lastName = displayName.includes(',')
      ? displayName.split(',')[0].trim()
      : displayName.split(/\s+/).filter(Boolean).at(-1) || 'Profesional';
    return { action: 'cargado', lastName, license: user?.licenseNumber || 's/d', at: new Date().toISOString() };
  }

  private focusValidation(target: string): void {
    queueMicrotask(() => {
      const control = [...this.host.nativeElement.querySelectorAll<HTMLElement>('[data-research-target]')]
        .find((element) => element.dataset['researchTarget'] === target);
      control?.focus();
    });
  }

  private fail(message: string): void {
    this.message.set(message);
    this.messageKind.set('error');
  }

  private releaseDraft(): void {
    if (!this.draft) return;
    this.clinicalDrafts.release(this.draft);
    this.draft = null;
  }
}
