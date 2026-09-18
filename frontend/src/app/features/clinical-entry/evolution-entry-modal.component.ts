import { Component, OnDestroy, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ClinicalDraftHandle, ClinicalDraftRegistryService } from '../../core/patients/clinical-draft-registry.service';
import { PatientWorkspaceService } from '../../core/patients/patient-workspace.service';
import { ClinicalEntrySaveResult, EvolutionEntryDraft, EvolutionImageAttachment } from './clinical-entry.models';
import { localIsoDate, newClinicalEntryId, normalizeClinicalText, normalizeEvolutionAttachments } from './clinical-entry.normalizers';
import { ClinicalEntryService, normalizeEntryFailure } from './clinical-entry.service';

@Component({
  selector: 'app-evolution-entry-modal',
  imports: [FormsModule],
  templateUrl: './evolution-entry-modal.component.html',
  styleUrl: './evolution-entry-modal.component.scss'
})
export class EvolutionEntryModalComponent implements OnDestroy {
  readonly open = input(false);
  readonly initial = input<EvolutionEntryDraft | null>(null);
  readonly closed = output<void>();
  readonly saved = output<ClinicalEntrySaveResult>();

  readonly workspace = inject(PatientWorkspaceService);
  readonly entries = inject(ClinicalEntryService);
  private readonly drafts = inject(ClinicalDraftRegistryService);
  private draftHandle: ClinicalDraftHandle | null = null;
  private openContext = '';

  readonly id = signal('');
  readonly date = signal(localIsoDate());
  readonly author = signal('');
  readonly specialty = signal('Oncología');
  readonly text = signal('');
  readonly attachments = signal<readonly EvolutionImageAttachment[]>([]);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly discardPrompt = signal(false);
  readonly canSave = computed(() => this.entries.canEdit() && Boolean(normalizeClinicalText(this.text())) && !this.busy());
  readonly standalone = { standalone: true } as const;

  constructor() {
    effect(() => {
      const context = this.open() ? `${this.workspace.workspace()?.patientId || ''}|${this.initial()?.id || 'new'}` : '';
      if (context === this.openContext) return;
      this.releaseDraft();
      this.openContext = context;
      if (context) this.resetFromInput();
    });
  }

  ngOnDestroy(): void { this.releaseDraft(); }

  update(field: 'date' | 'author' | 'specialty' | 'text', value: unknown): void {
    const normalized = String(value ?? '');
    if (field === 'date') this.date.set(normalized);
    else if (field === 'author') this.author.set(normalized);
    else if (field === 'specialty') this.specialty.set(normalized);
    else this.text.set(normalized);
    this.error.set('');
    this.discardPrompt.set(false);
    this.markDirty();
  }

  clear(): void {
    if (this.busy()) return;
    this.text.set('');
    this.error.set('');
    this.markDirty();
  }

  removeAttachment(index: number): void {
    if (this.busy() || !this.entries.canEdit()) return;
    this.attachments.update((items) => items.filter((_, itemIndex) => itemIndex !== index));
    this.error.set('');
    this.discardPrompt.set(false);
    this.markDirty();
  }

  requestClose(): void {
    if (this.busy()) return;
    if (this.draftHandle && this.drafts.isDirty(this.draftHandle)) {
      this.discardPrompt.set(true);
      return;
    }
    this.finishClose();
  }

  continueEditing(): void { this.discardPrompt.set(false); }
  discardAndClose(): void { if (!this.busy()) this.finishClose(); }

  async save(): Promise<void> {
    if (this.busy()) return;
    if (!this.entries.canEdit()) { this.error.set('Su usuario no tiene permiso para editar la historia clínica.'); return; }
    if (!/^\d{4}-\d{2}-\d{2}$/.test(this.date())) { this.error.set('Complete una fecha válida.'); return; }
    if (!normalizeClinicalText(this.text())) { this.error.set('Escriba la evolución clínica antes de guardar.'); return; }
    const draft: EvolutionEntryDraft = {
      id: this.id(), date: this.date(), author: this.author(), specialty: this.specialty(), text: this.text(),
      attachments: this.attachments()
    };
    this.busy.set(true); this.error.set(''); this.discardPrompt.set(false);
    try {
      const result = await firstValueFrom(this.entries.saveEvolution(draft));
      this.releaseDraft();
      this.saved.emit(result);
      this.closed.emit();
    } catch (failure: unknown) {
      this.error.set(normalizeEntryFailure(failure, 'No se pudo guardar la evolución.').message);
    } finally { this.busy.set(false); }
  }

  private resetFromInput(): void {
    const initial = this.initial();
    this.id.set(initial?.id || newClinicalEntryId('evolution'));
    this.date.set(initial?.date || localIsoDate());
    this.author.set(initial?.author || this.entries.professionalName());
    this.specialty.set(initial?.specialty || 'Oncología');
    this.text.set(initial?.text || '');
    this.attachments.set(normalizeEvolutionAttachments(initial?.attachments));
    this.error.set(''); this.discardPrompt.set(false); this.busy.set(false);
    if (this.attachments().length || normalizeClinicalText(this.text())) this.markDirty();
  }

  private markDirty(): void {
    const patientId = this.workspace.workspace()?.patientId;
    if (!patientId || !this.open() || !this.entries.canEdit()) return;
    this.draftHandle ||= this.drafts.acquire({ patientId, label: 'Nueva evolución' });
    this.drafts.setDirty(this.draftHandle, true);
  }

  private finishClose(): void {
    this.releaseDraft(); this.error.set(''); this.discardPrompt.set(false); this.closed.emit();
  }

  private releaseDraft(): void {
    if (!this.draftHandle) return;
    this.drafts.release(this.draftHandle); this.draftHandle = null;
  }
}
