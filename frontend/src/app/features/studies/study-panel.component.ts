import { Component, ElementRef, HostListener, OnDestroy, ViewChild, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { ClinicalStudyEntry, clinicalStudyEntries } from '../../core/clinical/clinical-study-projection';
import { appendStudyImageVersion, studyImages, StudyImagePreview } from '../../core/clinical/study-image-presentation';
import { ClinicalDraftHandle, ClinicalDraftRegistryService } from '../../core/patients/clinical-draft-registry.service';
import { ClinicalRecord, ClinicalState, StudyUploadDescriptor } from '../../core/patients/patient-workspace.models';
import { PatientWorkspaceService } from '../../core/patients/patient-workspace.service';
import { ExternalStudySearchService } from '../../core/studies/external-study-search.service';
import { normalizedStudyDni, safeExternalStudyUrl } from '../../core/studies/external-study-search.models';
import { StudyTemplateEditorComponent } from '../study-template-editor/study-template-editor.component';
import { StudyTemplateEditorSource } from '../study-template-editor/study-template-editor.models';
import { EvolutionEntryModalComponent } from '../clinical-entry/evolution-entry-modal.component';
import { ClinicalEntryService } from '../clinical-entry/clinical-entry.service';
import { EvolutionEntryDraft } from '../clinical-entry/clinical-entry.models';
import { localIsoDate, newClinicalEntryId } from '../clinical-entry/clinical-entry.normalizers';
import { mergeStudyUploads, projectStudyPanel } from './study-panel.models';
import { StudyPdfViewerComponent } from './study-pdf-viewer.component';
import { safeStudyPdfUrl } from './study-pdf-viewer.models';
import { appendStudyPdfPageVersion, studyPdfPages, StudyPdfPageAction, StudyPdfPageVersion } from './study-pdf-page.models';

type UploadStatus = 'ready' | 'uploading' | 'uploaded' | 'error';

interface PdfPageEditing {
  studyKey: string;
  pageNumber: number;
  documentUrl: string;
  kind: 'annotation' | 'snapshot';
}

interface UploadItem {
  id: string;
  file: File;
  extension: string;
  status: UploadStatus;
  error?: string;
  previewUrl?: string;
  editing?: { studyKey: string; image: StudyImagePreview };
  pdfEditing?: PdfPageEditing;
  record?: ClinicalRecord;
  savedStudyKey?: string;
  imageVersion?: { url: string; id: string; audit: Record<string, unknown> };
}

interface DeleteAuthorization {
  storageName: string;
  token: string;
  expiresAt: string;
}

export interface StudyPanelRequest {
  readonly id: number;
  readonly mode: 'browse' | 'upload';
  readonly studyKey?: string;
}

const ACCEPTED_EXTENSIONS = new Set([
  'png', 'jpg', 'jpeg', 'gif', 'webp', 'avif', 'bmp', 'ico', 'tif', 'tiff', 'heic', 'heif', 'svg', 'dcm',
  'pdf', 'doc', 'docx', 'rtf', 'odt', 'ppt', 'pps', 'pptx', 'ppsx', 'odp',
  'mp4', 'm4v', 'mov', '3gp', 'webm', 'mkv', 'avi', 'mpeg', 'mpg', 'ogv', 'wmv', 'flv'
]);
const MAX_FILE_SIZE = 250 * 1024 * 1024;
const MAX_BATCH_SIZE = 500 * 1024 * 1024;
const MAX_FILE_COUNT = 30;

@Component({
  selector: 'app-study-panel',
  providers: [ExternalStudySearchService],
  imports: [ReactiveFormsModule, StudyTemplateEditorComponent, EvolutionEntryModalComponent, StudyPdfViewerComponent],
  templateUrl: './study-panel.component.html',
  styleUrl: './study-panel.component.scss'
})
export class StudyPanelComponent implements OnDestroy {
  readonly workspace = inject(PatientWorkspaceService);
  readonly auth = inject(AuthService);
  readonly entries = inject(ClinicalEntryService);
  readonly externalSearch = inject(ExternalStudySearchService);
  private readonly drafts = inject(ClinicalDraftRegistryService);
  private draftHandle: ClinicalDraftHandle | null = null;
  private editorPatientId = '';
  private editingImage: { studyKey: string; image: StudyImagePreview } | null = null;
  private editingPdfPage: PdfPageEditing | null = null;
  private evolutionUploadId = '';
  readonly editorSource = signal<StudyTemplateEditorSource | null>(null);
  readonly evolutionAfterUpload = signal(false);
  readonly evolutionDraft = signal<EvolutionEntryDraft | null>(null);
  readonly evolutionOpen = signal(false);
  readonly versionSelections = signal<Record<string, string>>({});
  readonly request = input<StudyPanelRequest | null>(null);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  readonly term = new FormControl('', { nonNullable: true });
  readonly searchTerm = signal('');
  readonly uploads = signal<UploadItem[]>([]);
  readonly uploadOpen = signal(false);
  readonly busy = signal(false);
  readonly message = signal('');
  readonly dragActive = signal(false);
  readonly templateEditorOpen = signal(false);
  readonly selectedKey = signal('');
  private readonly deleteAuthorizations = new Map<string, DeleteAuthorization>();
  private pointerInside = false;
  private handledRequestId = 0;
  private uploadReturnFocus: HTMLElement | null = null;
  private searchContext = '';
  @ViewChild('studyUploadClose') private studyUploadClose?: ElementRef<HTMLButtonElement>;

  readonly searchDni = computed(() => {
    const current = this.workspace.workspace();
    return normalizedStudyDni(current?.patient?.dni || current?.state.patient?.dni);
  });
  readonly canSearchExternal = computed(() => Boolean(this.workspace.workspace()?.patientId && this.searchDni()
    && !this.workspace.loading() && this.auth.hasPermission('section.studies.view')));
  readonly externalResult = computed(() => {
    const result = this.externalSearch.result();
    return result?.patientId === this.workspace.workspace()?.patientId ? result : null;
  });
  readonly studyPresentation = computed(() => projectStudyPanel(this.workspace.workingWorkspace()?.state,
    this.externalResult()?.studies || [], this.searchTerm()));
  readonly listEntries = computed(() => this.studyPresentation().listEntries);
  readonly uploadedEntries = computed(() => this.studyPresentation().uploadedEntries);
  readonly studyEntries = computed(() => [...this.listEntries(), ...this.uploadedEntries().map(item => item.entry)]);
  readonly studies = computed(() => this.studyEntries().map((entry) => entry.record));
  readonly selectedEntry = computed(() => this.studyEntries().find(entry => entry.key === this.selectedKey()) || null);
  readonly selectedImages = computed(() => this.selectedEntry() ? studyImages(this.selectedEntry()!.record) : []);

  constructor() {
    this.term.valueChanges.subscribe((value) => this.searchTerm.set(value));
    effect(() => {
      const patientId = this.canSearchExternal() ? this.workspace.workspace()!.patientId : '';
      const dni = this.canSearchExternal() ? this.searchDni() : '';
      const context = `${this.workspace.workspace()?.patientId || ''}|${dni}`;
      if (context !== this.searchContext) {
        this.searchContext = context;
        this.selectedKey.set('');
        this.versionSelections.set({});
        this.term.setValue('', { emitEvent: false });
        this.searchTerm.set('');
      }
      untracked(() => this.externalSearch.setContext(patientId, dni));
    });
    effect(() => {
      const request = this.request();
      if (!request || request.id === this.handledRequestId) return;
      this.handledRequestId = request.id;
      this.term.setValue('', { emitEvent: false });
      this.searchTerm.set('');
      if (request.studyKey) this.selectedKey.set(request.studyKey);
      if (request.mode === 'upload') {
        queueMicrotask(() => this.openUpload());
      } else {
        queueMicrotask(() => this.focusStudy(request.studyKey));
      }
    });
  }

  ngOnDestroy(): void { this.clearUploadUrls(); this.releaseDraft(); }

  refreshExternalStudies(): void { if (this.canSearchExternal()) this.externalSearch.refresh(); }

  openUpload(files?: FileList | File[]): void {
    if (this.busy()) return;
    if (!this.canUpload()) {
      this.message.set('No tiene disponible la carga de estudios en este momento.');
      return;
    }
    if (!this.uploadOpen()) {
      this.uploadReturnFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    }
    this.acquireDraft();
    this.message.set('');
    this.uploadOpen.set(true);
    if (files) this.addFiles(Array.from(files));
    queueMicrotask(() => this.studyUploadClose?.nativeElement.focus());
  }

  openTemplateEditor(): void {
    if (!this.canUpload() || this.busy()) {
      this.message.set('Abra un paciente y finalice cualquier edición pendiente antes de cargar una plantilla.');
      return;
    }
    this.message.set('');
    this.acquireDraft();
    this.editingImage = null;
    this.editingPdfPage = null;
    this.editorSource.set(null);
    this.templateEditorOpen.set(true);
  }

  closeTemplateEditor(): void {
    if (this.busy()) return;
    this.templateEditorOpen.set(false);
    this.editingImage = null;
    this.editingPdfPage = null;
    if (!this.uploadOpen()) this.releaseDraft();
  }

  acceptTemplateImage(file: File): UploadItem | undefined {
    if (this.workspace.workspace()?.patientId !== this.editorPatientId) { this.message.set('El paciente cambió. Vuelva a abrir la imagen.'); return; }
    if (!this.canUpload() || this.busy()) return;
    this.templateEditorOpen.set(false);
    const previousId = this.uploads().at(-1)?.id;
    this.openUpload([file]);
    const last = this.uploads().at(-1);
    if (!last || last.id === previousId) return;
    this.markUpload(last.id, { editing: this.editingImage || undefined, pdfEditing: this.editingPdfPage || undefined });
    this.editingImage = null;
    this.editingPdfPage = null;
    return last;
  }

  acceptTemplateEvolution(file: File): void {
    if (!this.canAddToEvolution()) return;
    const item = this.acceptTemplateImage(file);
    if (item) { this.evolutionUploadId = item.id; this.evolutionAfterUpload.set(true); }
  }

  editImage(entry: ClinicalStudyEntry, image: StudyImagePreview): void {
    if (!this.canUpload() || this.busy() || !entry.record.id) return;
    this.acquireDraft();
    this.editingPdfPage = null;
    this.editingImage = { studyKey: entry.key, image: this.displayedImage(image, entry.key) };
    this.editorSource.set({ url: this.displayedImage(image, entry.key).url, name: String(entry.record.fileName || entry.record.title || 'imagen-clinica'), title: image.title });
    this.templateEditorOpen.set(true);
  }

  pdfPages(record: ClinicalRecord) { return studyPdfPages(record); }

  editPdfPage(entry: ClinicalStudyEntry, action: StudyPdfPageAction): void {
    if (!this.canUpload() || this.busy() || this.uploadOpen() || this.templateEditorOpen() || this.evolutionOpen()) return;
    const page = this.currentPdfPageAction(entry, action);
    if (!page) return;
    this.acquireDraft();
    this.editingImage = null;
    this.editingPdfPage = { studyKey: page.entry.key, pageNumber: action.pageNumber, documentUrl: page.documentUrl, kind: 'annotation' };
    this.editorSource.set({ ...(page.version ? { url: page.version.url } : { file: action.file }),
      name: `${this.title(page.entry.record)}-pagina-${action.pageNumber}.png`,
      title: `${this.title(page.entry.record)} · Página ${action.pageNumber}` });
    this.message.set('');
    this.templateEditorOpen.set(true);
  }

  addPdfPageToEvolution(entry: ClinicalStudyEntry, action: StudyPdfPageAction): void {
    if (!this.canAddToEvolution() || this.uploadOpen() || this.templateEditorOpen() || this.evolutionOpen()) return;
    const page = this.currentPdfPageAction(entry, action);
    if (!page) return;
    if (page.version) {
      this.openPdfPageEvolution(page.entry, action.pageNumber, page.version.id, action.patientId);
      return;
    }
    const previousId = this.uploads().at(-1)?.id;
    this.openUpload([action.file!]);
    const item = this.uploads().at(-1);
    if (!item || item.id === previousId) return;
    this.markUpload(item.id, { pdfEditing: { studyKey: page.entry.key, pageNumber: action.pageNumber, documentUrl: page.documentUrl, kind: 'snapshot' } });
    this.evolutionUploadId = item.id;
    this.evolutionAfterUpload.set(true);
  }

  private currentPdfPageAction(entry: ClinicalStudyEntry, action: StudyPdfPageAction): { entry: ClinicalStudyEntry; documentUrl: string; version?: StudyPdfPageVersion } | null {
    const current = this.workspace.workingWorkspace();
    const liveEntry = current && clinicalStudyEntries({ studies: current.state.studies }).find(item => item.key === entry.key);
    const documentUrl = liveEntry ? safeStudyPdfUrl(this.fileUrl(liveEntry.record), window.location.origin) : '';
    if (!current || current.patientId !== action.patientId || this.workspace.workspace()?.patientId !== action.patientId
      || !liveEntry?.record.id || !this.isPdfUpload(liveEntry.record) || !documentUrl
      || documentUrl !== safeStudyPdfUrl(action.documentUrl, window.location.origin)
      || !Number.isSafeInteger(action.pageNumber) || action.pageNumber < 1) {
      this.message.set('La página ya no corresponde al documento y paciente abiertos. Vuelva a abrir el PDF.');
      return null;
    }
    if (action.versionId === 'original') {
      if (!action.file || action.file.type !== 'image/png' || !action.file.size || action.file.size > MAX_FILE_SIZE) {
        this.message.set('No se pudo preparar la imagen de esta página. Vuelva a intentarlo.');
        return null;
      }
      return { entry: liveEntry, documentUrl };
    }
    const version = studyPdfPages(liveEntry.record).find(page => page.pageNumber === action.pageNumber)?.versions.find(item => item.id === action.versionId);
    if (!version || version.url !== action.url) {
      this.message.set('La versión seleccionada ya no está disponible. Vuelva a abrir la página.');
      return null;
    }
    return { entry: liveEntry, documentUrl, version };
  }

  private openPdfPageEvolution(entry: ClinicalStudyEntry, pageNumber: number, versionId: string, patientId: string): void {
    if (!this.canAddToEvolution() || this.workspace.workspace()?.patientId !== patientId) return;
    const current = this.workspace.workingWorkspace();
    if (current?.patientId !== patientId) return;
    const liveEntry = clinicalStudyEntries({ studies: current.state.studies }).find(item => item.key === entry.key);
    const version = liveEntry && studyPdfPages(liveEntry.record).find(page => page.pageNumber === pageNumber)?.versions.find(item => item.id === versionId);
    if (!liveEntry || !version) { this.message.set('La copia de esta página todavía no está disponible. Vuelva a abrir el PDF.'); return; }
    this.evolutionDraft.set({
      id: newClinicalEntryId('evolution'), date: localIsoDate(), author: this.entries.professionalName(), specialty: 'Oncología', text: '',
      attachments: [{ id: `evo-image-${this.id()}`, studyId: String(liveEntry.record.id), imageId: `pdf-page-${pageNumber}`,
        versionId: version.id, annotated: version.kind === 'annotation', url: version.url, thumbnailUrl: version.url, title: `${this.title(liveEntry.record)} · Página ${pageNumber}`,
        studyDate: String(liveEntry.record.date || ''), studyType: this.fileKind(liveEntry.record), caption: '', createdAt: new Date().toISOString() }]
    });
    this.evolutionOpen.set(true);
  }

  displayedImage(image: StudyImagePreview, studyKey = ''): StudyImagePreview {
    const version = image.versions.find(v => v.id === this.versionSelections()[`${studyKey}|${image.id}`]);
    return version ? { ...image, url: version.url, versionId: version.id, annotated: version.id !== 'original' } : image;
  }

  selectVersion(image: StudyImagePreview, id: string, studyKey = ''): void {
    this.versionSelections.update(value => ({ ...value, [`${studyKey}|${image.id}`]: id }));
  }

  canAddToEvolution(): boolean { return this.entries.canEdit() && this.canUpload() && !this.busy(); }

  addImageToEvolution(entry: ClinicalStudyEntry, image: StudyImagePreview): void {
    if (!this.canAddToEvolution()) return;
    const selected = this.displayedImage(image, entry.key);
    this.evolutionDraft.set({
      id: newClinicalEntryId('evolution'), date: localIsoDate(), author: this.entries.professionalName(), specialty: 'Oncología', text: '',
      attachments: [{ id: `evo-image-${this.id()}`, studyId: String(entry.record.id || ''), imageId: selected.id,
        versionId: selected.versionId, url: selected.url, thumbnailUrl: selected.url, title: String(entry.record.title || image.title),
        studyDate: String(entry.record.date || ''), studyType: this.fileKind(entry.record), caption: '', createdAt: new Date().toISOString() }]
    });
    this.evolutionOpen.set(true);
  }

  closeUpload(): void {
    if (this.busy()) return;
    if (this.uploads().some(item => item.status !== 'uploaded' || item.record) && !window.confirm('¿Descartar los archivos preparados que todavía no se guardaron?')) return;
    this.finishCloseUpload();
  }

  addFiles(files: File[]): void {
    const current = this.uploads();
    let size = current.filter((item) => item.status !== 'error').reduce((total, item) => total + item.file.size, 0);
    const additions = files.map((file, index) => {
      const extension = this.extension(file.name);
      let error = '';
      if (current.length + index >= MAX_FILE_COUNT) error = `Solo se pueden preparar ${MAX_FILE_COUNT} archivos por lote.`;
      else if (!ACCEPTED_EXTENSIONS.has(extension)) error = 'Formato no admitido.';
      else if (!file.size) error = 'El archivo está vacío.';
      else if (file.size > MAX_FILE_SIZE) error = 'Supera el límite de 250 MB.';
      else if (size + file.size > MAX_BATCH_SIZE) error = 'El lote supera el límite total de 500 MB.';
      if (!error) size += file.size;
      return { id: this.id(), file, extension, status: error ? 'error' : 'ready', error,
        previewUrl: !error && /^image\/(png|jpeg|gif|webp|avif|bmp)$/.test(file.type) ? URL.createObjectURL(file) : '' } as UploadItem;
    });
    this.uploads.set([...current, ...additions]);
    if (this.draftHandle && additions.length) this.drafts.setDirty(this.draftHandle, true);
  }

  removeUpload(id: string): void { if (!this.busy()) { const item = this.uploads().find(item => item.id === id); if (item?.previewUrl) URL.revokeObjectURL(item.previewUrl); this.uploads.update((items) => items.filter((item) => item.id !== id)); } }
  retryUpload(id: string): void { if (!this.busy()) this.uploads.update((items) => items.map((item) => item.id === id ? { ...item, status: 'ready', error: '' } : item)); }
  onFileInput(event: Event): void { const input = event.target as HTMLInputElement; if (input.files) this.addFiles(Array.from(input.files)); input.value = ''; }
  onDragOver(event: DragEvent): void { event.preventDefault(); this.dragActive.set(true); }
  onDragLeave(event: DragEvent): void { event.preventDefault(); this.dragActive.set(false); }
  onDrop(event: DragEvent): void { event.preventDefault(); this.dragActive.set(false); if (event.dataTransfer?.files?.length) this.addFiles(Array.from(event.dataTransfer.files)); }

  @HostListener('document:paste', ['$event'])
  onPaste(event: ClipboardEvent): void {
    if ((!this.uploadOpen() && !this.pointerInside) || this.busy() || !this.canUpload()) return;
    const files = Array.from(event.clipboardData?.files || []).filter((file) => file.type.startsWith('image/'));
    if (!files.length) return;
    event.preventDefault();
    this.openUpload(files);
  }

  @HostListener('mouseenter')
  onPointerEnter(): void { this.pointerInside = true; }

  @HostListener('mouseleave')
  onPointerLeave(): void { this.pointerInside = false; }

  async upload(): Promise<void> {
    if (this.workspace.activeSaveConflict()) {
      this.message.set('Resuelva el borrador pendiente antes de cargar nuevos archivos.');
      return;
    }
    const current = this.workspace.workspace();
    const patientId = current?.patientId || current?.patient?.id;
    const ready = this.uploads().filter((item) => item.status === 'ready' || (item.status === 'uploaded' && item.record));
    if (!patientId) { this.message.set('Abra o cree un paciente antes de subir estudios.'); return; }
    if (String(patientId) !== this.editorPatientId) { this.message.set('El paciente cambió. Cierre la carga y vuelva a intentarlo.'); return; }
    if (!ready.length || this.busy()) return;

    const expectedPatientId = String(patientId);
    let releaseOperation: (() => void) | null = null;
    try {
      releaseOperation = this.workspace.beginPatientScopedOperation(expectedPatientId);
      // The scoped operation now owns the patient lock; media uploads reject open editor drafts.
      this.releaseDraft();
    } catch (error) {
      this.message.set(this.error(error, 'El paciente activo cambió antes de iniciar la carga.'));
      return;
    }
    this.busy.set(true);
    this.message.set('');
    const records: ClinicalRecord[] = [];
    let evolutionTarget: ClinicalStudyEntry | null = null;
    let evolutionImageId = '';
    let evolutionPdfPage: { pageNumber: number; versionId: string } | null = null;
    try {
      for (const item of ready) {
        this.requireActivePatient(expectedPatientId);
        if (item.record) {
          const current = records.find(record => record.id === item.record!.id)
            || clinicalStudyEntries(this.requireActivePatient(expectedPatientId).state).find(entry => entry.record.id === item.record!.id)?.record;
          let record = current || item.record;
          if (item.pdfEditing && item.imageVersion) {
            if (!current) throw new Error('El PDF original ya no está disponible.');
            this.requirePdfDocument(current, item.pdfEditing);
            record = appendStudyPdfPageVersion(current, item.pdfEditing.pageNumber, item.imageVersion.url, item.imageVersion.id, item.imageVersion.audit, item.pdfEditing.kind);
          } else if (item.editing && item.imageVersion) {
            if (!current) throw new Error('El estudio original ya no está disponible.');
            const alreadySaved = studyImages(current).some(image => image.versions.some(version => version.id === item.imageVersion!.id));
            if (!alreadySaved) record = appendStudyImageVersion(current, item.editing.image, item.imageVersion.url, item.imageVersion.id, item.imageVersion.audit);
          }
          const previousIndex = records.findIndex(previous => previous.id === record.id);
          if (previousIndex >= 0) records[previousIndex] = record; else records.push(record);
          continue;
        }
        this.markUpload(item.id, { status: 'uploading', error: '' });
        try {
          const editingKey = item.pdfEditing?.studyKey || item.editing?.studyKey;
          const existing = editingKey ? records.find(record => `id:${record.id}` === editingKey)
            || clinicalStudyEntries(this.requireActivePatient(expectedPatientId).state).find(entry => entry.key === editingKey)?.record : null;
          if (editingKey && !existing?.id) throw new Error('El estudio original ya no está disponible.');
          if (existing && item.pdfEditing) this.requirePdfDocument(existing, item.pdfEditing);
          const studyId = existing?.id || `est-${this.id()}`;
          const descriptor = await firstValueFrom(this.workspace.uploadStudy(expectedPatientId, studyId, item.file));
          this.requireActivePatient(expectedPatientId);
          if (!descriptor.url) throw new Error('El servidor no confirmó el archivo cargado.');
          const imageVersion = existing && editingKey ? { url: descriptor.url, id: `${item.pdfEditing ? 'pdf-page' : 'image'}-version-${this.id()}`, audit: { ...this.entries.auditStamp() } } : undefined;
          const record = existing && item.pdfEditing && imageVersion
            ? appendStudyPdfPageVersion(existing, item.pdfEditing.pageNumber, imageVersion.url, imageVersion.id, imageVersion.audit, item.pdfEditing.kind)
            : existing && item.editing && imageVersion
              ? appendStudyImageVersion(existing, item.editing.image, imageVersion.url, imageVersion.id, imageVersion.audit)
            : this.studyFromUpload(studyId, item.file, descriptor);
          const previousRecordIndex = records.findIndex(previous => previous.id === record.id);
          if (previousRecordIndex >= 0) records[previousRecordIndex] = record; else records.push(record);
          if (descriptor.deleteToken && !editingKey) {
            this.deleteAuthorizations.set(studyId, {
              storageName: descriptor.url.split('/').pop() || '',
              token: descriptor.deleteToken,
              expiresAt: descriptor.deleteExpiresAt || ''
            });
          }
          if (editingKey) this.deleteAuthorizations.delete(studyId);
          this.markUpload(item.id, { status: 'uploaded', record, imageVersion, savedStudyKey: `id:${record.id}` });
        } catch (error) {
          this.markUpload(item.id, { status: 'error', error: this.error(error, 'No se pudo cargar el archivo.') });
        }
      }
      if (records.length) {
        const next = this.nextState(expectedPatientId, (state) => ({
          ...state,
          studies: mergeStudyUploads(state.studies || [], records),
          meta: { ...(state.meta || {}), updatedAt: new Date().toISOString() }
        }));
        await firstValueFrom(this.workspace.saveState(next));
        this.requireActivePatient(expectedPatientId);
        this.selectedKey.set(`id:${String(records[0].id)}`);
        this.versionSelections.set({});
        if (this.evolutionAfterUpload()) {
          const item = this.uploads().find(item => item.id === this.evolutionUploadId && item.status === 'uploaded');
          const key = item?.pdfEditing?.studyKey || item?.editing?.studyKey || item?.savedStudyKey || (item?.record?.id ? `id:${item.record.id}` : '');
          evolutionTarget = clinicalStudyEntries(this.requireActivePatient(expectedPatientId).state).find(entry => entry.key === key) || null;
          evolutionImageId = item?.editing?.image.id || '';
          if (item?.pdfEditing && item.imageVersion) evolutionPdfPage = { pageNumber: item.pdfEditing.pageNumber, versionId: item.imageVersion.id };
        }
        for (const item of ready) if (this.uploads().find(current => current.id === item.id)?.status === 'uploaded') this.markUpload(item.id, { record: undefined });
      }
      if (!this.uploads().some((item) => item.status === 'error')) this.finishCloseUpload();
      else this.message.set(`${records.length} archivo(s) cargado(s). Revise los pendientes.`);
    } catch (error) {
      this.message.set(this.error(error, 'Los archivos se cargaron, pero la historia no pudo guardarse.'));
    } finally {
      this.busy.set(false);
      if (this.uploadOpen() && !this.workspace.activeSaveConflict()) {
        this.acquireDraft();
        if (this.draftHandle) this.drafts.setDirty(this.draftHandle, true);
      }
      releaseOperation?.();
    }
    if (evolutionTarget && !this.uploadOpen() && this.workspace.workspace()?.patientId === expectedPatientId) {
      if (evolutionPdfPage) this.openPdfPageEvolution(evolutionTarget, evolutionPdfPage.pageNumber, evolutionPdfPage.versionId, expectedPatientId);
      else {
        const images = studyImages(evolutionTarget.record);
        const image = images.find(image => image.id === evolutionImageId) || images[0];
        if (image) this.addImageToEvolution(evolutionTarget, image);
      }
    }
  }

  async removeStudy(record: ClinicalRecord): Promise<void> {
    if (!this.canDelete(record)) return;
    if (this.workspace.activeSaveConflict()) {
      this.message.set('Resuelva el borrador pendiente antes de eliminar archivos.');
      return;
    }
    const studyId = String(record.id || '');
    const authorization = this.deleteAuthorizations.get(studyId);
    if (!authorization || !authorization.storageName || this.busy()) return;
    if (!window.confirm('¿Eliminar este archivo cargado durante la sesión actual? Esta acción no se puede deshacer.')) return;
    const patientId = String(this.workspace.workspace()?.patientId || '');
    let releaseOperation: (() => void) | null = null;
    try {
      releaseOperation = this.workspace.beginPatientScopedOperation(patientId);
    } catch (error) {
      this.message.set(this.error(error, 'El paciente activo cambió antes de iniciar la eliminación.'));
      return;
    }
    this.busy.set(true);
    this.message.set('');
    try {
      const next = this.nextState(patientId, (state) => ({
        ...state,
        studies: (state.studies || []).filter((item) => String(item.id) !== studyId),
        meta: { ...(state.meta || {}), updatedAt: new Date().toISOString() }
      }));
      await firstValueFrom(this.workspace.saveState(next));
      try { await firstValueFrom(this.workspace.deleteUploadedStudy(authorization.storageName, authorization.token)); }
      catch { this.message.set('La imagen se eliminó de la ficha, pero no se pudo limpiar el archivo local.'); }
      this.deleteAuthorizations.delete(studyId);
      if (this.selectedKey() === `id:${studyId}`) this.selectedKey.set('');
    } catch (error) {
      this.message.set(this.error(error, 'No se pudo confirmar la eliminación de la imagen.'));
    } finally {
      this.busy.set(false);
      releaseOperation?.();
    }
  }

  select(entry: ClinicalStudyEntry): void { this.selectedKey.set(entry.key); }
  hasImagePreview(record: ClinicalRecord): boolean { return studyImages(record).length > 0; }
  showImage(entry: ClinicalStudyEntry): void {
    this.select(entry);
    requestAnimationFrame(() => this.focusStudy(entry.key));
  }
  canDelete(record: ClinicalRecord): boolean {
    const imageUrls = new Set([...studyImages(record).flatMap(image => image.versions.map(version => version.url)),
      ...studyPdfPages(record).flatMap(page => page.versions.map(version => version.url))]);
    if ((this.workspace.workingWorkspace()?.state.evolutions || []).some(evolution =>
      (Array.isArray(evolution['linkedStudyIds']) && evolution['linkedStudyIds'].includes(record.id))
      || evolution.attachments?.some(attachment => attachment['studyId'] === record.id || imageUrls.has(String(attachment.url || ''))))) return false;
    const authorization = this.deleteAuthorizations.get(String(record.id || ''));
    return Boolean(authorization && (!authorization.expiresAt || Date.parse(authorization.expiresAt) > Date.now()));
  }
  canEdit(): boolean { return this.auth.hasPermission('section.studies.edit'); }
  canUpload(): boolean {
    return Boolean(this.workspace.workspace()
      && !this.workspace.loading()
      && !this.workspace.activeSaveConflict() && !this.workspace.saving()
      && (!this.workspace.hasPendingClinicalWork() || Boolean(this.draftHandle))
      && this.canEdit());
  }
  trapUploadFocus(event: KeyboardEvent): void {
    if (event.key !== 'Tab') return;
    const dialog = event.currentTarget instanceof HTMLElement ? event.currentTarget : null;
    if (!dialog) return;
    const focusable = [...dialog.querySelectorAll<HTMLElement>(
      'button:not([disabled]), input:not([disabled]), textarea:not([disabled]), select:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])'
    )].filter((element) => element.getAttribute('aria-hidden') !== 'true');
    if (!focusable.length) { event.preventDefault(); dialog.focus(); return; }
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    const active = document.activeElement;
    if (event.shiftKey && (active === first || !dialog.contains(active))) {
      event.preventDefault(); last.focus();
    } else if (!event.shiftKey && (active === last || !dialog.contains(active))) {
      event.preventDefault(); first.focus();
    }
  }
  fileIcon(record: ClinicalRecord): string { return this.category(record) === 'image' ? '▧' : this.category(record) === 'pdf' ? '▤' : this.category(record) === 'video' ? '▶' : '▱'; }
  fileKind(record: ClinicalRecord): string { return String(record.type || this.category(record) || 'Archivo'); }
  title(record: ClinicalRecord): string { return String(record.title || record.fileName || 'Estudio sin título'); }
  source(record: ClinicalRecord): string { return String(record.source || 'Repositorio local'); }
  fileUrl(record: ClinicalRecord): string {
    const attachments = Array.isArray(record.attachments) ? record.attachments : [];
    return [record.fileUrl, record.reportUrl, record.studyUrl, ...attachments.map(attachment => attachment?.url)]
      .map(safeExternalStudyUrl).find(Boolean) || '';
  }
  isPdfUpload(record: ClinicalRecord): boolean {
    return this.category(record).toLowerCase() === 'pdf'
      || String(record['fileType'] || '').toLowerCase() === 'application/pdf'
      || /\.pdf(?:$|[?#])/i.test(this.fileUrl(record));
  }
  reportUrl(record: ClinicalRecord): string { return safeExternalStudyUrl(record.reportUrl); }
  studyUrl(record: ClinicalRecord): string { return safeExternalStudyUrl(record.studyUrl); }
  date(value: unknown): string { const text = String(value || ''); if (!text) return 'Sin fecha'; const parsed = new Date(`${text.length === 10 ? `${text}T12:00:00` : text}`); return Number.isNaN(parsed.getTime()) ? text : new Intl.DateTimeFormat('es-AR').format(parsed); }
  size(record: ClinicalRecord): string { const value = Number(record.fileSize || record.size || 0); return value ? this.formatSize(value) : ''; }
  queueKind(item: UploadItem): string { return this.kindFromExtension(item.extension); }
  queueLabel(item: UploadItem): string { return item.status === 'ready' ? 'Listo para subir' : item.status === 'uploading' ? 'Subiendo…' : item.status === 'uploaded' ? 'Cargado' : item.error || 'No se pudo cargar'; }
  hasPendingUploads(): boolean { return this.uploads().some(item => item.status === 'ready' || (item.status === 'uploaded' && Boolean(item.record))); }

  private nextState(patientId: string, mutator: (state: ClinicalState) => ClinicalState): ClinicalState {
    const workspace = this.requireActivePatient(patientId);
    return mutator(structuredClone(workspace.state));
  }
  private requireActivePatient(patientId: string) {
    const current = this.workspace.workingWorkspace();
    if (!current || current.patientId !== patientId) {
      throw new Error('El paciente activo cambió durante la operación. No se modificó otra historia clínica.');
    }
    return current;
  }
  private requirePdfDocument(record: ClinicalRecord, editing: PdfPageEditing): void {
    if (!this.isPdfUpload(record) || safeStudyPdfUrl(this.fileUrl(record), window.location.origin) !== editing.documentUrl) {
      throw new Error('El PDF original cambió. Vuelva a abrir la página antes de guardar su copia.');
    }
  }
  private studyFromUpload(id: string, file: File, descriptor: StudyUploadDescriptor): ClinicalRecord {
    const category = descriptor.category || this.categoryFromExtension(this.extension(file.name));
    const attachment = {
      id: descriptor.id || id, fileName: descriptor.fileName || file.name, contentType: descriptor.contentType || file.type,
      size: Number(descriptor.size || file.size), sha256: descriptor.sha256 || '', category, previewable: Boolean(descriptor.previewable),
      url: descriptor.url, uploadedAt: descriptor.uploadedAt || new Date().toISOString()
    };
    const record: ClinicalRecord = {
      id, date: new Date().toISOString().slice(0, 10), datePrecision: 'day', type: this.typeForCategory(category, this.extension(file.name)),
      title: file.name.replace(/\.[^.]+$/, '') || file.name, source: 'Repositorio local', summary: '', tags: [], attachments: [attachment],
      fileName: attachment.fileName, fileType: attachment.contentType, fileSize: attachment.size, fileCategory: category,
      fileSha256: attachment.sha256, fileUrl: attachment.url, createdAt: attachment.uploadedAt, updatedAt: attachment.uploadedAt
    };
    if (category === 'image' && attachment.previewable) Object.assign(record, { presentationKind: 'loose-image', previewImageUrl: attachment.url, displayImageUrls: [attachment.url], imageUrls: [attachment.url], imageCount: 1 });
    else if (category === 'pdf') record.reportUrl = attachment.url;
    else record.studyUrl = attachment.url;
    return record;
  }
  private markUpload(id: string, patch: Partial<UploadItem>): void { this.uploads.update((items) => items.map((item) => item.id === id ? { ...item, ...patch } : item)); }
  private focusStudy(studyKey?: string): void {
    const candidates = [...this.host.nativeElement.querySelectorAll<HTMLElement>('[data-study-key]')];
    const target = studyKey
      ? candidates.find((element) => element.dataset['studyKey'] === studyKey)
      : this.host.nativeElement.querySelector<HTMLElement>('.angular-study-table-scroll');
    target?.scrollIntoView({ block: 'nearest' });
    target?.focus({ preventScroll: true });
  }
  private finishCloseUpload(): void {
    this.uploadOpen.set(false);
    this.clearUploadUrls();
    this.uploads.set([]);
    this.evolutionAfterUpload.set(false);
    this.evolutionUploadId = '';
    this.editingImage = null;
    this.editingPdfPage = null;
    this.releaseDraft();
    this.dragActive.set(false);
    const returnFocus = this.uploadReturnFocus;
    this.uploadReturnFocus = null;
    queueMicrotask(() => {
      if (returnFocus?.isConnected && !returnFocus.hasAttribute('disabled')) returnFocus.focus({ preventScroll: true });
    });
  }
  private acquireDraft(): void {
    const patientId = this.workspace.workspace()?.patientId;
    if (!patientId) return;
    this.editorPatientId = patientId;
    this.draftHandle ||= this.drafts.acquire({ patientId, label: 'Imagen de estudio' });
  }
  private releaseDraft(): void { if (this.draftHandle) this.drafts.release(this.draftHandle); this.draftHandle = null; }
  private clearUploadUrls(): void { for (const item of this.uploads()) if (item.previewUrl) URL.revokeObjectURL(item.previewUrl); }
  private searchText(record: ClinicalRecord): string { return [record.title, record.fileName, record.summary, record.source, record.type, record.modality].map((item) => String(item || '')).join(' ').toLocaleLowerCase('es-AR'); }
  private extension(name: string): string { return name.toLocaleLowerCase().match(/\.([a-z0-9]+)$/)?.[1] || ''; }
  private category(record: ClinicalRecord): string { return String(record.fileCategory || (record.attachments as Array<{ category?: string }> | undefined)?.[0]?.category || this.categoryFromExtension(this.extension(String(record.fileName || '')))); }
  private categoryFromExtension(extension: string): string {
    if (['png', 'jpg', 'jpeg', 'gif', 'webp', 'avif', 'bmp', 'ico', 'tif', 'tiff', 'heic', 'heif', 'svg', 'dcm'].includes(extension)) return 'image';
    if (extension === 'pdf') return 'pdf';
    if (['doc', 'docx', 'rtf', 'odt'].includes(extension)) return 'word';
    if (['ppt', 'pps', 'pptx', 'ppsx', 'odp'].includes(extension)) return 'presentation';
    if (['mp4', 'm4v', 'mov', '3gp', 'webm', 'mkv', 'avi', 'mpeg', 'mpg', 'ogv', 'wmv', 'flv'].includes(extension)) return 'video';
    return 'file';
  }
  private typeForCategory(category: string, extension: string): string { if (category === 'image') return extension === 'dcm' ? 'Imagen DICOM' : 'Imagen'; return ({ pdf: 'Documento PDF', word: 'Documento Word', presentation: 'Presentación', video: 'Video' } as Record<string, string>)[category] || 'Otro'; }
  private kindFromExtension(extension: string): string { return this.typeForCategory(this.categoryFromExtension(extension), extension); }
  formatSize(size: number): string { if (size < 1024) return `${size} B`; if (size < 1024 ** 2) return `${Math.round(size / 1024)} KB`; return `${(size / 1024 ** 2).toFixed(1)} MB`; }
  private id(): string { return globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`; }
  private error(error: unknown, fallback: string): string { return error instanceof Error && error.message ? error.message : fallback; }
}
