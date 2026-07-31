import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { catchError, concatMap, finalize, from, map, of, tap, toArray } from 'rxjs';
import { ApiClientService } from '../../core/api/api-client.service';
import { ApiError } from '../../core/api/api-error';
import { ClinicalDocument, ClinicalStudy, ClinicalStudyAttachment, PatientWorkspaceResponse } from '../../core/patients/patient.models';

interface StudyUploadResponse extends ClinicalStudyAttachment {
  ok?: boolean;
  storedName?: string;
  deleteToken?: string;
  deleteExpiresAt?: string;
}

interface UploadedStudy {
  file: File;
  studyId: string;
  response: StudyUploadResponse;
}

@Component({
  selector: 'app-patient-studies-page',
  imports: [ReactiveFormsModule],
  templateUrl: './patient-studies-page.component.html',
  styleUrl: './patient-studies-page.component.scss'
})
export class PatientStudiesPageComponent {
  private readonly api = inject(ApiClientService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  readonly patientId = signal<string | null>(null);
  readonly patientName = signal('');
  readonly document = signal<ClinicalDocument | null>(null);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly deletingId = signal<string | null>(null);
  readonly error = signal<string | null>(null);
  readonly uploadErrors = signal<string[]>([]);
  readonly queuedFiles = signal<File[]>([]);
  readonly selectedStudyId = signal<string | null>(null);
  readonly query = new FormControl('', { nonNullable: true });
  private readonly queryValue = toSignal(this.query.valueChanges, { initialValue: '' });
  private readonly sessionDeletes = new Map<string, { storedName: string; deleteToken: string }>();

  readonly studies = computed(() => this.document()?.studies ?? []);
  readonly filteredStudies = computed(() => {
    const term = this.queryValue().trim().toLocaleLowerCase();
    return [...this.studies()]
      .filter((study) => !term || [study.title, study.fileName, study.type, study.source, study.summary]
        .filter(Boolean).join(' ').toLocaleLowerCase().includes(term))
      .sort((left, right) => String(right.date ?? '').localeCompare(String(left.date ?? '')));
  });
  readonly selectedStudy = computed(() => this.filteredStudies().find((study) => study.id === this.selectedStudyId())
    ?? this.filteredStudies()[0] ?? null);

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const patientId = params.get('patientId');
      if (patientId) this.load(patientId);
    });
  }

  reload(): void {
    const id = this.patientId();
    if (id) this.load(id);
  }

  selectStudy(study: ClinicalStudy): void {
    this.selectedStudyId.set(study.id ?? null);
  }

  addFiles(files: FileList | File[]): void {
    const incoming = Array.from(files).filter((file) => file.size > 0);
    if (!incoming.length) return;
    this.queuedFiles.update((current) => [...current, ...incoming]);
  }

  onFileInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files) this.addFiles(input.files);
    input.value = '';
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    if (event.dataTransfer?.files) this.addFiles(event.dataTransfer.files);
  }

  removeQueued(index: number): void {
    this.queuedFiles.update((files) => files.filter((_file, position) => position !== index));
  }

  uploadAll(): void {
    const patientId = this.patientId();
    const document = this.document();
    const files = this.queuedFiles();
    if (!patientId || !document || !files.length || this.busy()) return;
    this.busy.set(true);
    this.error.set(null);
    this.uploadErrors.set([]);
    from(files).pipe(
      concatMap((file) => {
        const studyId = `study-${crypto.randomUUID()}`;
        const query = new URLSearchParams({ patientId, studyId, name: file.name });
        return this.api.upload<StudyUploadResponse>(`/api/media/studies?${query.toString()}`, file).pipe(
          map((response) => ({ file, studyId, response } as UploadedStudy)),
          catchError((error: unknown) => {
            this.uploadErrors.update((messages) => [...messages, `${file.name}: ${ApiError.from(error).message}`]);
            return of(null);
          })
        );
      }),
      toArray(),
      concatMap((results) => {
        const uploaded = results.filter((result): result is UploadedStudy => result !== null);
        if (!uploaded.length) return of<ClinicalDocument | null>(null);
        for (const item of uploaded) {
          if (item.response.storedName && item.response.deleteToken) {
            this.sessionDeletes.set(item.studyId, {
              storedName: item.response.storedName,
              deleteToken: item.response.deleteToken
            });
          }
        }
        const next = this.withStudies(document, uploaded.map((item) => this.studyRecord(item)));
        return this.persist(next);
      }),
      finalize(() => this.busy.set(false))
    ).subscribe({
      next: (saved) => {
        if (!saved) return;
        const failedNames = new Set(this.uploadErrors().map((message) => message.split(':', 1)[0]));
        this.queuedFiles.update((current) => current.filter((file) => failedNames.has(file.name)));
      },
      error: (error: unknown) => this.error.set(ApiError.from(error).message)
    });
  }

  deleteStudy(study: ClinicalStudy): void {
    const patientId = this.patientId();
    const document = this.document();
    const id = String(study.id ?? '');
    const authorization = this.sessionDeletes.get(id);
    if (!patientId || !document || !authorization || this.busy() || !id) return;
    this.busy.set(true);
    this.deletingId.set(id);
    this.error.set(null);
    this.api.delete<{ ok: boolean }>(`/api/media/studies/${encodeURIComponent(authorization.storedName)}`, {
      'X-Study-Delete-Token': authorization.deleteToken
    }).pipe(
      concatMap(() => this.persist(this.withStudies(document, (document.studies ?? []).filter((item) => item.id !== id)))),
      finalize(() => { this.busy.set(false); this.deletingId.set(null); })
    ).subscribe({
      next: () => this.sessionDeletes.delete(id),
      error: (error: unknown) => this.error.set(ApiError.from(error).message)
    });
  }

  attachment(study: ClinicalStudy): ClinicalStudyAttachment | null {
    return study.attachments?.[0] ?? (study.fileUrl ? {
      url: study.fileUrl,
      fileName: study.fileName,
      contentType: study.fileType,
      size: study.fileSize,
      category: study.fileCategory
    } : null);
  }

  isImage(study: ClinicalStudy): boolean {
    return String(this.attachment(study)?.contentType ?? '').startsWith('image/');
  }

  isPdf(study: ClinicalStudy): boolean {
    return this.attachment(study)?.contentType === 'application/pdf';
  }

  isVideo(study: ClinicalStudy): boolean {
    return String(this.attachment(study)?.contentType ?? '').startsWith('video/');
  }

  canDelete(study: ClinicalStudy): boolean {
    return this.sessionDeletes.has(String(study.id ?? ''));
  }

  formatSize(value: number | undefined): string {
    if (!value) return '';
    if (value < 1024) return `${value} B`;
    if (value < 1024 * 1024) return `${Math.round(value / 1024)} KB`;
    return `${(value / (1024 * 1024)).toFixed(1)} MB`;
  }

  private load(patientId: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.post<PatientWorkspaceResponse>(`/api/clinical/patients/${patientId}/activate`).pipe(
      concatMap(() => this.api.get<PatientWorkspaceResponse>(`/api/clinical/patients/${patientId}/workspace`)),
      finalize(() => this.loading.set(false))
    ).subscribe({
      next: (workspace) => {
        this.patientId.set(patientId);
        this.patientName.set(workspace.patient.fullName);
        this.document.set(workspace.state);
        this.selectedStudyId.set(workspace.state.studies?.[0]?.id ?? null);
      },
      error: (error: unknown) => this.error.set(ApiError.from(error).message)
    });
  }

  private studyRecord(upload: UploadedStudy): ClinicalStudy {
    const response = upload.response;
    const now = new Date().toISOString();
    const category = response.category ?? this.category(upload.file.name);
    const attachment: ClinicalStudyAttachment = {
      id: response.id,
      fileName: response.fileName ?? upload.file.name,
      contentType: response.contentType ?? upload.file.type,
      size: response.size ?? upload.file.size,
      category,
      previewable: response.previewable,
      url: response.url,
      storedName: response.storedName,
      uploadedAt: response.uploadedAt ?? now
    };
    return {
      id: upload.studyId,
      date: now.slice(0, 10),
      datePrecision: 'day',
      type: this.typeLabel(category),
      title: upload.file.name.replace(/\.[^.]+$/, '') || upload.file.name,
      source: 'Carga local',
      summary: '',
      fileName: attachment.fileName,
      fileType: attachment.contentType,
      fileSize: attachment.size,
      fileCategory: category,
      fileUrl: attachment.url,
      attachments: [attachment],
      createdAt: now,
      updatedAt: now
    };
  }

  private persist(document: ClinicalDocument) {
    return this.api.put('/api/hc', document).pipe(
      concatMap(() => this.api.get<ClinicalDocument>('/api/hc')),
      tap((saved) => {
        this.document.set(saved);
        this.selectedStudyId.set(saved.studies?.[0]?.id ?? null);
      })
    );
  }

  private withStudies(document: ClinicalDocument, additions: ClinicalStudy[]): ClinicalDocument {
    const copy = JSON.parse(JSON.stringify(document)) as ClinicalDocument;
    copy.studies = [...additions, ...(copy.studies ?? [])];
    return copy;
  }

  private category(fileName: string): string {
    const extension = fileName.toLocaleLowerCase().split('.').pop() ?? '';
    if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'tif', 'tiff'].includes(extension)) return 'image';
    if (extension === 'pdf') return 'pdf';
    if (['doc', 'docx'].includes(extension)) return 'document';
    if (['ppt', 'pptx'].includes(extension)) return 'presentation';
    if (['mp4', 'webm', 'mov', 'avi', 'mkv'].includes(extension)) return 'video';
    return 'file';
  }

  private typeLabel(category: string): string {
    return ({ image: 'Imagen', pdf: 'Documento PDF', document: 'Documento', presentation: 'Presentación', video: 'Video' } as Record<string, string>)[category] ?? 'Archivo';
  }
}
