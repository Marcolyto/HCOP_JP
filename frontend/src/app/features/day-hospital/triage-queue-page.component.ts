import { CommonModule } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { debounceTime, distinctUntilChanged, finalize } from 'rxjs';
import { ApiError } from '../../core/api/api-error';
import { ApplicationWorkflow } from '../../core/day-hospital/application-workflow.models';
import { ApplicationWorkflowService } from '../../core/day-hospital/application-workflow.service';

type TriageFilter = 'all' | 'pending' | 'pass' | 'fail';

@Component({
  selector: 'app-triage-queue-page',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './triage-queue-page.component.html',
  styleUrl: './triage-queue-page.component.scss'
})
export class TriageQueuePageComponent {
  private readonly workflows = inject(ApplicationWorkflowService);
  private readonly destroyRef = inject(DestroyRef);

  readonly date = new FormControl(this.today(), { nonNullable: true });
  readonly search = new FormControl('', { nonNullable: true });
  readonly filter = new FormControl<TriageFilter>('all', { nonNullable: true });
  readonly items = signal<ApplicationWorkflow[]>([]);
  readonly selected = signal<ApplicationWorkflow | null>(null);
  readonly loading = signal(false);
  readonly detailLoading = signal(false);
  readonly actionLoading = signal(false);
  readonly error = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly form = new FormGroup({
    labDate: new FormControl('', { nonNullable: true }),
    neutrophils: new FormControl('', { nonNullable: true }),
    platelets: new FormControl('', { nonNullable: true }),
    creatinine: new FormControl('', { nonNullable: true }),
    bilirubin: new FormControl('', { nonNullable: true }),
    hepaticFunction: new FormControl('', { nonNullable: true }),
    weightKg: new FormControl('', { nonNullable: true }),
    bloodPressure: new FormControl('', { nonNullable: true }),
    temperatureC: new FormControl('', { nonNullable: true }),
    heartRate: new FormControl('', { nonNullable: true }),
    oxygenSaturation: new FormControl('', { nonNullable: true }),
    ecog: new FormControl('', { nonNullable: true }),
    toxicityGrade: new FormControl('', { nonNullable: true }),
    clinicalNotes: new FormControl('', { nonNullable: true }),
    passReason: new FormControl('', { nonNullable: true }),
    failReason: new FormControl('', { nonNullable: true }),
    rescheduledDate: new FormControl('', { nonNullable: true })
  });

  readonly visibleItems = computed(() => this.items().filter((item) => {
    const status = this.status(item);
    return this.filter.value === 'all' || this.filter.value === status;
  }));

  constructor() {
    this.search.valueChanges.pipe(debounceTime(280), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.load());
    this.date.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.load());
    this.load();
  }

  load(): void {
    this.loading.set(true); this.error.set(null);
    this.workflows.list('triage', this.date.value, this.search.value.trim()).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (response) => this.items.set(response.items ?? []),
      error: (error: unknown) => this.error.set(ApiError.from(error).message)
    });
  }

  open(item: ApplicationWorkflow): void {
    this.detailLoading.set(true); this.actionError.set(null);
    this.workflows.get(item).pipe(finalize(() => this.detailLoading.set(false))).subscribe({
      next: (response) => { this.selected.set(response.workflow); this.fill(response.workflow); },
      error: (error: unknown) => this.actionError.set(ApiError.from(error).message)
    });
  }

  close(): void { this.selected.set(null); this.actionError.set(null); }

  pass(): void {
    const item = this.selected();
    if (!item || this.actionLoading()) return;
    const missing = ['labDate', 'neutrophils', 'platelets', 'creatinine', 'weightKg', 'bloodPressure', 'temperatureC', 'toxicityGrade']
      .filter((name) => !this.form.get(name)?.value);
    if (missing.length) { this.actionError.set('Complete laboratorio, signos vitales y toxicidad antes de emitir PASS.'); return; }
    this.submit('PASS');
  }

  fail(): void {
    if (!this.form.controls.failReason.value.trim()) { this.actionError.set('Indique el motivo clinico o analitico de la postergacion.'); return; }
    this.submit('FAIL');
  }

  status(item: ApplicationWorkflow): Exclude<TriageFilter, 'all'> {
    const value = String(item.clinicalAuthorizationStatus ?? 'pending').toLowerCase();
    if (['approved', 'pass', 'authorized'].includes(value)) return 'pass';
    if (['failed', 'fail', 'rejected', 'withheld'].includes(value)) return 'fail';
    return 'pending';
  }

  statusLabel(item: ApplicationWorkflow): string { return ({ pending: 'Pendiente', pass: 'Apto', fail: 'Postergado' })[this.status(item)]; }
  trackItem(_: number, item: ApplicationWorkflow): string { return `${item.patientId}-${item.treatmentId}-${item.cycleNumber}-${item.applicationDay}`; }
  dateLabel(value: unknown): string { const raw = String(value ?? ''); if (!raw) return 'Sin fecha'; const date = new Date(raw.length === 10 ? `${raw}T00:00:00` : raw); return Number.isNaN(date.getTime()) ? raw : new Intl.DateTimeFormat('es-AR', { dateStyle: 'medium' }).format(date); }
  timeLabel(value: unknown): string { const date = new Date(String(value ?? '')); return Number.isNaN(date.getTime()) ? 'Sin turno' : new Intl.DateTimeFormat('es-AR', { hour: '2-digit', minute: '2-digit' }).format(date); }

  private submit(decision: 'PASS' | 'FAIL'): void {
    const item = this.selected(); if (!item) return;
    const value = this.form.getRawValue();
    const number = (name: keyof typeof value) => { const parsed = Number(value[name]); return Number.isFinite(parsed) ? parsed : null; };
    this.actionLoading.set(true); this.actionError.set(null);
    this.workflows.clinicalAuthorization(item, {
      decision,
      laboratory: { date: value.labDate || null, neutrophils: number('neutrophils'), platelets: number('platelets'), creatinine: number('creatinine'), bilirubin: number('bilirubin'), hepaticFunction: value.hepaticFunction },
      vitalSigns: { weightKg: number('weightKg'), bloodPressure: value.bloodPressure, temperatureC: number('temperatureC'), heartRate: number('heartRate'), oxygenSaturation: number('oxygenSaturation') },
      toxicity: { grade: number('toxicityGrade'), ecog: number('ecog'), notes: value.clinicalNotes },
      reason: decision === 'FAIL' ? value.failReason.trim() : value.passReason.trim(),
      rescheduledDate: decision === 'FAIL' ? value.rescheduledDate || null : null
    }).pipe(finalize(() => this.actionLoading.set(false))).subscribe({
      next: (response) => { this.selected.set(response.workflow); this.items.update((items) => items.map((current) => this.same(current, response.workflow) ? response.workflow : current)); },
      error: (error: unknown) => this.actionError.set(ApiError.from(error).message)
    });
  }

  private fill(item: ApplicationWorkflow): void {
    const clinical = (item['clinicalAssessment'] as Record<string, Record<string, unknown>> | undefined) ?? {};
    const lab = clinical['laboratory'] ?? {}; const vitals = clinical['vitalSigns'] ?? {}; const toxicity = clinical['toxicity'] ?? {};
    const text = (value: unknown) => value == null ? '' : String(value);
    this.form.setValue({ labDate: text(lab['date']), neutrophils: text(lab['neutrophils']), platelets: text(lab['platelets']), creatinine: text(lab['creatinine']), bilirubin: text(lab['bilirubin']), hepaticFunction: text(lab['hepaticFunction']), weightKg: text(vitals['weightKg']), bloodPressure: text(vitals['bloodPressure']), temperatureC: text(vitals['temperatureC']), heartRate: text(vitals['heartRate']), oxygenSaturation: text(vitals['oxygenSaturation']), ecog: text(toxicity['ecog']), toxicityGrade: text(toxicity['grade']), clinicalNotes: text(toxicity['notes']), passReason: '', failReason: text(item['clinicalAuthorizationReason']), rescheduledDate: '' });
  }

  private same(left: ApplicationWorkflow, right: ApplicationWorkflow): boolean { return left.patientId === right.patientId && left.treatmentId === right.treatmentId && left.cycleNumber === right.cycleNumber && left.applicationDay === right.applicationDay; }
  private today(): string { return new Date().toLocaleDateString('en-CA', { timeZone: 'America/Argentina/Buenos_Aires' }); }
}
