import { CommonModule } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormArray, FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged, finalize, switchMap } from 'rxjs';
import { ApiClientService } from '../../core/api/api-client.service';
import { ApiError } from '../../core/api/api-error';

interface ProtocolComponentDraft {
  drugId: string;
  drugName: string;
  day: string;
  prescribedDoseText: string;
  doseUnit: string;
  doseCalculationMethod: string;
  route: string;
  administrationTime: string;
  dayHospital: boolean;
}

interface DrugOption { id?: string; name?: string; nombre?: string; presentation?: string; brand?: string; }

@Component({
  selector: 'app-protocol-editor-page',
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './protocol-editor-page.component.html',
  styleUrl: './protocol-editor-page.component.scss'
})
export class ProtocolEditorPageComponent {
  private readonly api = inject(ApiClientService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly id = signal<string | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly drugOptions = signal<DrugOption[]>([]);
  private readonly drugSearch = new Subject<string>();
  readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true }),
    category: new FormControl('', { nonNullable: true }),
    description: new FormControl('', { nonNullable: true }),
    cycleDays: new FormControl('21', { nonNullable: true }),
    durationMinutes: new FormControl('60', { nonNullable: true }),
    coirSchemeId: new FormControl('', { nonNullable: true }),
    revision: new FormControl(0, { nonNullable: true }),
    components: new FormArray<FormGroup>([])
  });

  get components() { return this.form.controls.components; }

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const id = params.get('id');
      this.id.set(id);
      if (id) this.load(id);
      else if (!this.components.length) this.add();
    });
    this.drugSearch.pipe(
      debounceTime(180),
      distinctUntilChanged(),
      switchMap((query) => query.trim().length < 2
        ? [({ drugs: [] } as { drugs: DrugOption[] })]
        : this.api.get<{ drugs: DrugOption[] }>('/api/clinical/drugs', { q: query }))
    ).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (response) => this.drugOptions.set(response.drugs.slice(0, 25)),
      error: () => this.drugOptions.set([])
    });
  }

  add() { this.components.push(this.row()); }
  remove(index: number) { this.components.removeAt(index); }
  findDrugs(query: string) { this.drugSearch.next(query); }

  selectDrug(index: number, name: string) {
    const selected = this.drugOptions().find((drug) => this.normalized(drug.name ?? drug.nombre ?? '') === this.normalized(name));
    if (!selected) return;
    this.components.at(index).patchValue({
      drugId: selected.id ?? '',
      drugName: selected.name ?? selected.nombre ?? name
    });
  }

  save() {
    const raw = this.form.getRawValue();
    if (!raw.name.trim() || Number(raw.cycleDays) < 1 || Number(raw.durationMinutes) < 1 || !this.components.length) {
      this.error.set('Nombre, ciclo, duracion y al menos una droga son obligatorios.');
      return;
    }
    const components = this.components.getRawValue().map((component) => ({
      drugId: String(component['drugId'] ?? ''),
      drugName: String(component['drugName'] ?? ''),
      day: String(component['day'] ?? ''),
      prescribedDoseText: String(component['prescribedDoseText'] ?? ''),
      doseUnit: String(component['doseUnit'] ?? ''),
      doseCalculationMethod: String(component['doseCalculationMethod'] ?? ''),
      route: String(component['route'] ?? ''),
      administrationTime: String(component['administrationTime'] ?? ''),
      dayHospital: String(component['dayHospital']) === 'true'
    })) as ProtocolComponentDraft[];
    if (components.some((component) => !component.drugName || !component.day || !component.prescribedDoseText || !component.doseUnit)) {
      this.error.set('Cada droga requiere nombre, dia, dosis y unidad.');
      return;
    }

    const body = {
      name: raw.name,
      category: raw.category,
      description: raw.description,
      cycleDays: Number(raw.cycleDays),
      durationMinutes: Number(raw.durationMinutes),
      coirSchemeId: raw.coirSchemeId,
      active: true,
      components,
      preparations: [],
      revision: raw.revision
    };
    this.saving.set(true);
    this.error.set(null);
    const id = this.id();
    const request = id
      ? this.api.put(`/api/clinical/protocols/${encodeURIComponent(id)}`, body)
      : this.api.post('/api/clinical/protocols', body);
    request.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: () => this.router.navigateByUrl('/protocols'),
      error: (error) => this.error.set(ApiError.from(error).message)
    });
  }

  private load(id: string) {
    this.loading.set(true);
    this.api.get<{ protocol: Record<string, unknown> }>(`/api/clinical/protocols/${encodeURIComponent(id)}`)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response) => {
          const protocol = response.protocol;
          if (protocol['catalogOnly']) {
            this.error.set('Los esquemas COIR son de solo lectura. Cree un protocolo local para personalizarlos.');
            return;
          }
          this.form.patchValue({
            name: String(protocol['name'] ?? ''),
            category: String(protocol['category'] ?? ''),
            description: String(protocol['description'] ?? ''),
            cycleDays: String(protocol['cycleDays'] ?? 21),
            durationMinutes: String(protocol['durationMinutes'] ?? 60),
            coirSchemeId: String(protocol['coirSchemeId'] ?? ''),
            revision: Number(protocol['revision'] ?? 0)
          });
          this.components.clear();
          const items = Array.isArray(protocol['components']) ? protocol['components'] as Array<Record<string, unknown>> : [];
          for (const component of items) this.components.push(this.row(component));
          if (!this.components.length) this.add();
        },
        error: (error) => this.error.set(ApiError.from(error).message)
      });
  }

  private row(component: Record<string, unknown> = {}) {
    return new FormGroup({
      drugId: new FormControl(String(component['drugId'] ?? ''), { nonNullable: true }),
      drugName: new FormControl(String(component['drugName'] ?? component['name'] ?? ''), { nonNullable: true }),
      day: new FormControl(String(component['day'] ?? component['applicationDays'] ?? '1'), { nonNullable: true }),
      prescribedDoseText: new FormControl(String(component['prescribedDoseText'] ?? component['dose'] ?? ''), { nonNullable: true }),
      doseUnit: new FormControl(String(component['doseUnit'] ?? ''), { nonNullable: true }),
      doseCalculationMethod: new FormControl(String(component['doseCalculationMethod'] ?? ''), { nonNullable: true }),
      route: new FormControl(String(component['route'] ?? ''), { nonNullable: true }),
      administrationTime: new FormControl(String(component['administrationTime'] ?? ''), { nonNullable: true }),
      dayHospital: new FormControl(String(component['dayHospital'] ?? 'true'), { nonNullable: true })
    });
  }

  private normalized(value: string) {
    return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
  }
}
