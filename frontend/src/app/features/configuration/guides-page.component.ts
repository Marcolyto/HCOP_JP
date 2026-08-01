import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { ApiClientService } from '../../core/api/api-client.service';
import { ApiError } from '../../core/api/api-error';

interface Guide { name: string; title: string; site: string; audience: string; source: string; version: string; tags: string[]; description: string; active: boolean; configurationId: string; configurationRevision: string; url: string; size: number; updatedAt: string; }

@Component({
  selector: 'app-guides-page',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './guides-page.component.html',
  styleUrl: './guides-page.component.scss'
})
export class GuidesPageComponent {
  private readonly api = inject(ApiClientService);
  readonly guides = signal<Guide[]>([]);
  readonly selected = signal<Guide | null>(null);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly uploading = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly search = new FormControl('', { nonNullable: true });
  readonly form = new FormGroup({
    configurationId: new FormControl('', { nonNullable: true }),
    revision: new FormControl('', { nonNullable: true }),
    fileName: new FormControl('', { nonNullable: true }),
    title: new FormControl('', { nonNullable: true }),
    category: new FormControl('', { nonNullable: true }),
    audience: new FormControl('', { nonNullable: true }),
    source: new FormControl('', { nonNullable: true }),
    version: new FormControl('', { nonNullable: true }),
    tags: new FormControl('', { nonNullable: true }),
    description: new FormControl('', { nonNullable: true }),
    active: new FormControl(true, { nonNullable: true })
  });

  constructor() { this.search.valueChanges.subscribe(() => this.guides.update((items) => [...items])); this.load(); }

  filtered() {
    const term = this.normalize(this.search.value);
    return this.guides().filter((guide) => !term || this.normalize(`${guide.title} ${guide.name} ${guide.site} ${guide.source} ${guide.tags.join(' ')}`).includes(term));
  }

  load(selectName = '') {
    this.loading.set(true); this.error.set(null);
    this.api.get<{ guides: Guide[] }>('/api/guides', { includeInactive: 1 }).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (response) => { this.guides.set(response.guides ?? []); const pick = response.guides?.find((guide) => guide.name === selectName) ?? this.selected(); if (pick) this.select(pick); },
      error: (error) => this.error.set(ApiError.from(error).message)
    });
  }

  select(guide: Guide) {
    this.selected.set(guide); this.notice.set(null); this.error.set(null);
    this.form.setValue({ configurationId: guide.configurationId ?? '', revision: guide.configurationRevision ?? '', fileName: guide.name, title: guide.title, category: guide.site, audience: guide.audience, source: guide.source, version: guide.version, tags: (guide.tags ?? []).join(', '), description: guide.description, active: guide.active !== false });
  }

  upload(input: Event) {
    const file = (input.target as HTMLInputElement).files?.[0];
    if (!file) return;
    if (!file.name.toLowerCase().endsWith('.pdf') || file.type && file.type !== 'application/pdf') { this.error.set('Seleccione un archivo PDF.'); return; }
    this.uploading.set(true); this.error.set(null); this.notice.set(null);
    this.api.putFile<{ name: string }>('/api/guides/import?name=' + encodeURIComponent(file.name), file).pipe(finalize(() => this.uploading.set(false))).subscribe({
      next: (response) => { this.notice.set('Archivo recibido. Complete los metadatos y guarde la guia.'); this.load(response.name); (input.target as HTMLInputElement).value = ''; },
      error: (error) => this.error.set(ApiError.from(error).message)
    });
  }

  save() {
    const value = this.form.getRawValue();
    if (!value.fileName || !value.title.trim()) { this.error.set('Seleccione un PDF y complete el titulo de la guia.'); return; }
    const body = { key: `guide:${this.slug(value.fileName)}`, name: value.title.trim(), description: value.description.trim(), active: value.active, revision: value.revision ? Number(value.revision) : undefined, definition: { fileName: value.fileName, category: value.category.trim(), audience: value.audience.trim(), source: value.source.trim(), version: value.version.trim(), tags: value.tags.split(',').map((item) => item.trim()).filter(Boolean) } };
    this.saving.set(true); this.error.set(null); this.notice.set(null);
    const request = value.configurationId ? this.api.put<{ item: { id: number } }>(`/api/clinical/configuration/guide/${encodeURIComponent(value.configurationId)}`, body) : this.api.post<{ item: { id: number } }>('/api/clinical/configuration/guide', body);
    request.pipe(finalize(() => this.saving.set(false))).subscribe({ next: () => { this.notice.set('Guia guardada.'); this.load(value.fileName); }, error: (error) => this.error.set(ApiError.from(error).message) });
  }

  private slug(value: string) { return value.toLowerCase().replace(/\.pdf$/i, '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, ''); }
  private normalize(value: string) { return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase(); }
}
