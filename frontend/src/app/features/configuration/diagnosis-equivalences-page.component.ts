import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { debounceTime, finalize } from 'rxjs';
import { ApiClientService } from '../../core/api/api-client.service';
import { ApiError } from '../../core/api/api-error';

type System = 'snomed' | 'cie10' | 'ajcc';
interface Concept { code?: string; display?: string; version?: string; source?: string; sourceConceptId?: string; }
interface Equivalence { id: string; key?: string; name: string; description: string; active: boolean; revision: number; definition: { snomed?: Concept; cie10?: Concept; ajcc?: Concept; relation?: string; confidence?: string; notes?: string; visibleSystems?: System[] }; }
interface CatalogResult { code: string; display: string; group?: string; version?: string; source?: string; sourceConceptId?: string; }

@Component({
  selector: 'app-diagnosis-equivalences-page',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './diagnosis-equivalences-page.component.html',
  styleUrl: './diagnosis-equivalences-page.component.scss'
})
export class DiagnosisEquivalencesPageComponent {
  private readonly api = inject(ApiClientService);
  readonly systems: System[] = ['snomed', 'cie10', 'ajcc'];
  readonly labels: Record<System, string> = { snomed: 'SNOMED', cie10: 'CIE-10', ajcc: 'AJCC' };
  readonly items = signal<Equivalence[]>([]);
  readonly selected = signal<Equivalence | null>(null);
  readonly catalogResults = signal<Record<System, CatalogResult[]>>({ snomed: [], cie10: [], ajcc: [] });
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly search = new FormControl('', { nonNullable: true });
  readonly display = new FormGroup({ id: new FormControl('', { nonNullable: true }), revision: new FormControl('', { nonNullable: true }), snomed: new FormControl(true, { nonNullable: true }), cie10: new FormControl(true, { nonNullable: true }), ajcc: new FormControl(true, { nonNullable: true }) });
  readonly form = new FormGroup({ id: new FormControl('', { nonNullable: true }), revision: new FormControl('', { nonNullable: true }), name: new FormControl('', { nonNullable: true }), active: new FormControl(true, { nonNullable: true }), relation: new FormControl('exact', { nonNullable: true }), confidence: new FormControl('medium', { nonNullable: true }), notes: new FormControl('', { nonNullable: true }), snomedSearch: new FormControl('', { nonNullable: true }), snomedCode: new FormControl('', { nonNullable: true }), snomedDisplay: new FormControl('', { nonNullable: true }), cie10Search: new FormControl('', { nonNullable: true }), cie10Code: new FormControl('', { nonNullable: true }), cie10Display: new FormControl('', { nonNullable: true }), ajccSearch: new FormControl('', { nonNullable: true }), ajccCode: new FormControl('', { nonNullable: true }), ajccDisplay: new FormControl('', { nonNullable: true }) });

  constructor() { this.search.valueChanges.pipe(debounceTime(150)).subscribe(() => this.items.update((items) => [...items])); this.load(); }
  filtered() { const query = this.normalized(this.search.value); return this.items().filter((item) => !query || this.normalized(`${item.name} ${item.description} ${this.concept(item,'snomed').code} ${this.concept(item,'snomed').display} ${this.concept(item,'cie10').code} ${this.concept(item,'cie10').display} ${this.concept(item,'ajcc').code} ${this.concept(item,'ajcc').display}`).includes(query)); }
  concept(item: Equivalence, system: System) { return item.definition?.[system] ?? {}; }
  label(system: System) { return this.labels[system]; }

  load(selectId = '') {
    this.loading.set(true); this.error.set(null);
    this.api.get<{ items: Equivalence[] }>('/api/clinical/configuration/diagnosis-equivalence', { includeInactive: 1 }).pipe(finalize(() => this.loading.set(false))).subscribe({ next: (response) => { this.items.set(response.items ?? []); const next = response.items?.find((item) => item.id === selectId) ?? this.selected(); if (next) this.select(next); }, error: (error) => this.error.set(ApiError.from(error).message) });
    this.api.get<{ items: Equivalence[] }>('/api/clinical/configuration/diagnosis-setting', { includeInactive: 1 }).subscribe({ next: (response) => { const setting = response.items?.find((item) => item.key === 'diagnosis-display') ?? response.items?.[0]; const visible = setting?.definition?.visibleSystems ?? this.systems; this.display.setValue({ id: setting?.id ?? '', revision: String(setting?.revision ?? ''), snomed: visible.includes('snomed'), cie10: visible.includes('cie10'), ajcc: visible.includes('ajcc') }); }, error: () => undefined });
  }

  select(item: Equivalence) { const definition = item.definition ?? {}; this.selected.set(item); this.form.setValue({ id: item.id, revision: String(item.revision), name: item.name, active: item.active !== false, relation: definition.relation ?? 'exact', confidence: definition.confidence ?? 'medium', notes: definition.notes ?? item.description ?? '', snomedSearch: '', snomedCode: definition.snomed?.code ?? '', snomedDisplay: definition.snomed?.display ?? '', cie10Search: '', cie10Code: definition.cie10?.code ?? '', cie10Display: definition.cie10?.display ?? '', ajccSearch: '', ajccCode: definition.ajcc?.code ?? '', ajccDisplay: definition.ajcc?.display ?? '' }); this.catalogResults.set({ snomed: [], cie10: [], ajcc: [] }); }
  newItem() { this.selected.set(null); this.form.setValue({ id: '', revision: '', name: '', active: true, relation: 'exact', confidence: 'medium', notes: '', snomedSearch: '', snomedCode: '', snomedDisplay: '', cie10Search: '', cie10Code: '', cie10Display: '', ajccSearch: '', ajccCode: '', ajccDisplay: '' }); this.catalogResults.set({ snomed: [], cie10: [], ajcc: [] }); }

  lookup(system: System) { const query = this.form.controls[`${system}Search`].value.trim(); if (query.length < 2) { this.catalogResults.update((current) => ({ ...current, [system]: [] })); return; } this.api.get<{ items: CatalogResult[] }>('/api/diagnosis-catalogs/search', { system, q: query, limit: 40 }).subscribe({ next: (response) => this.catalogResults.update((current) => ({ ...current, [system]: response.items ?? [] })), error: (error) => this.error.set(ApiError.from(error).message) }); }
  apply(system: System, result: CatalogResult) { this.form.controls[`${system}Code`].setValue(result.code); this.form.controls[`${system}Display`].setValue(result.display); if (!this.form.controls.name.value && system === 'snomed') this.form.controls.name.setValue(result.display); this.catalogResults.update((current) => ({ ...current, [system]: [] })); }

  saveDisplay() { const value = this.display.getRawValue(); const visibleSystems = this.systems.filter((system) => value[system]); if (!visibleSystems.length) { this.error.set('Seleccione al menos una clasificación visible.'); return; } const body = { key: 'diagnosis-display', name: 'diagnosis-display', active: true, revision: value.revision ? Number(value.revision) : undefined, definition: { schemaVersion: 1, visibleSystems } }; this.saving.set(true); this.api[value.id ? 'put' : 'post'](value.id ? `/api/clinical/configuration/diagnosis-setting/${value.id}` : '/api/clinical/configuration/diagnosis-setting', body).pipe(finalize(() => this.saving.set(false))).subscribe({ next: () => { this.notice.set('Clasificaciones visibles actualizadas.'); this.load(); }, error: (error) => this.error.set(ApiError.from(error).message) }); }
  save() { const value = this.form.getRawValue(); const concepts = this.systems.map((system) => ({ system, code: value[`${system}Code`].trim(), display: value[`${system}Display`].trim() })); const incomplete = concepts.find((concept) => !concept.code || !concept.display); if (!value.name.trim() || (value.active && incomplete) || (!value.active && (!value.ajccCode || !value.ajccDisplay))) { this.error.set('Complete el nombre y código/descripción de cada clasificación activa. AJCC es obligatorio aun en borrador.'); return; } const body = { name: value.name.trim(), description: value.notes.trim(), active: value.active, revision: value.revision ? Number(value.revision) : undefined, definition: { schemaVersion: 1, snomed: { code: value.snomedCode, display: value.snomedDisplay }, cie10: { code: value.cie10Code, display: value.cie10Display }, ajcc: { code: value.ajccCode, display: value.ajccDisplay }, relation: value.relation, confidence: value.confidence, notes: value.notes.trim() } }; this.saving.set(true); this.error.set(null); const request = value.id ? this.api.put<{ item: Equivalence }>(`/api/clinical/configuration/diagnosis-equivalence/${value.id}`, body) : this.api.post<{ item: Equivalence }>('/api/clinical/configuration/diagnosis-equivalence', body); request.pipe(finalize(() => this.saving.set(false))).subscribe({ next: (response) => { this.notice.set('Equivalencia guardada.'); this.load(response.item.id); }, error: (error) => this.error.set(ApiError.from(error).message) }); }
  archive() { const id = this.form.controls.id.value; if (!id) return; this.saving.set(true); this.api.delete(`/api/clinical/configuration/diagnosis-equivalence/${id}`).pipe(finalize(() => this.saving.set(false))).subscribe({ next: () => { this.notice.set('Equivalencia desactivada, se conservaron sus versiones.'); this.newItem(); this.load(); }, error: (error) => this.error.set(ApiError.from(error).message) }); }
  private normalized(value: string) { return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase(); }
}
