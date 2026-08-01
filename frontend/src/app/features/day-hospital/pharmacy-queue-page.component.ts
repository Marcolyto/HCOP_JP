import { CommonModule } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { debounceTime, distinctUntilChanged, finalize } from 'rxjs';
import { ApiError } from '../../core/api/api-error';
import {
  ApplicationWorkflow,
  MedicationSource
} from '../../core/day-hospital/application-workflow.models';
import { ApplicationWorkflowService } from '../../core/day-hospital/application-workflow.service';

type PharmacyFilter = 'all' | 'pending-validation' | 'rejected' | 'patient' | 'patient-has' | 'received-center' | 'pending-stock' | 'reserved';
type DateScope = 'next-7' | 'today' | 'next-30' | 'all';

@Component({
  selector: 'app-pharmacy-queue-page',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './pharmacy-queue-page.component.html',
  styleUrl: './pharmacy-queue-page.component.scss'
})
export class PharmacyQueuePageComponent {
  private readonly workflows = inject(ApplicationWorkflowService);
  private readonly destroyRef = inject(DestroyRef);

  readonly search = new FormControl('', { nonNullable: true });
  readonly filter = new FormControl<PharmacyFilter>('all', { nonNullable: true });
  readonly dateScope = new FormControl<DateScope>('next-7', { nonNullable: true });
  readonly items = signal<ApplicationWorkflow[]>([]);
  readonly selected = signal<ApplicationWorkflow | null>(null);
  readonly loading = signal(false);
  readonly detailLoading = signal(false);
  readonly actionLoading = signal(false);
  readonly error = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly lastQueryWasGlobal = signal(false);
  readonly validationForm = new FormGroup({
    medicationSource: new FormControl<MedicationSource>('center_stock', { nonNullable: true }),
    notes: new FormControl('', { nonNullable: true })
  });

  readonly visibleItems = computed(() => {
    const filter = this.filter.value;
    const scope = this.dateScope.value;
    const searchActive = this.normalize(this.search.value).length > 0;
    return this.items().filter((item) => {
      if (!searchActive && !this.matchesDateScope(item, scope)) return false;
      const validation = String(item.pharmacyValidationStatus ?? 'pending');
      const source = item.medicationSource;
      const reserved = source === 'center_stock' && item.stockReservationStatus === 'reserved';
      const pendingStock = source === 'pending_supplier' || (source === 'center_stock' && !reserved);
      return filter === 'all'
        || (filter === 'pending-validation' && validation === 'pending')
        || (filter === 'rejected' && ['rejected', 'invalid', 'denied'].includes(validation))
        || (filter === 'patient' && source === 'patient_to_bring')
        || (filter === 'patient-has' && source === 'patient_has_medication')
        || (filter === 'received-center' && source === 'received_center')
        || (filter === 'pending-stock' && pendingStock)
        || (filter === 'reserved' && reserved);
    }).sort((left, right) => this.dateValue(left).localeCompare(this.dateValue(right))
      || String(left.patientName ?? '').localeCompare(String(right.patientName ?? ''), 'es'));
  });

  constructor() {
    this.search.valueChanges.pipe(
      debounceTime(280),
      distinctUntilChanged(),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(() => this.load());
    this.load();
  }

  load(): void {
    const query = this.search.value.trim();
    this.loading.set(true);
    this.error.set(null);
    this.lastQueryWasGlobal.set(Boolean(query));
    this.workflows.listPharmacy(query).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (response) => {
        this.items.set(response.items ?? []);
        const current = this.selected();
        if (current) {
          const replacement = response.items.find((item) => this.sameItem(item, current));
          if (replacement) this.selected.set(replacement);
        }
      },
      error: (error: unknown) => this.error.set(ApiError.from(error).message)
    });
  }

  open(item: ApplicationWorkflow): void {
    this.detailLoading.set(true);
    this.actionError.set(null);
    this.workflows.get(item).pipe(finalize(() => this.detailLoading.set(false))).subscribe({
      next: (response) => {
        this.selected.set(response.workflow);
        const source = response.workflow.medicationSource;
        if (source) this.validationForm.controls.medicationSource.setValue(source);
        this.validationForm.controls.notes.setValue(response.workflow.pharmacyValidationNotes ?? '');
      },
      error: (error: unknown) => this.actionError.set(ApiError.from(error).message)
    });
  }

  close(): void {
    this.selected.set(null);
    this.actionError.set(null);
  }

  validate(approved: boolean): void {
    const item = this.selected();
    if (!item || this.actionLoading()) return;
    const value = this.validationForm.getRawValue();
    if (!approved && !value.notes.trim()) {
      this.actionError.set('Explique el motivo del rechazo para dejar una traza útil al médico y a Farmacia.');
      return;
    }
    this.actionLoading.set(true);
    this.actionError.set(null);
    this.workflows.validatePharmacy(item, approved, value.medicationSource, value.notes.trim()).pipe(
      finalize(() => this.actionLoading.set(false))
    ).subscribe({
      next: (response) => this.replaceSelected(response.workflow),
      error: (error: unknown) => this.actionError.set(ApiError.from(error).message)
    });
  }

  reserve(): void {
    const item = this.selected();
    if (!item || this.actionLoading()) return;
    if (!this.workflows.canBuildReservation(item)) {
      this.actionError.set('No se puede armar la reserva: revise que cada droga tenga nombre, dosis y unidad explícitos.');
      return;
    }
    const notes = this.validationForm.controls.notes.value.trim()
      || 'Disponibilidad física constatada por Farmacia en la interfaz clínica.';
    this.actionLoading.set(true);
    this.actionError.set(null);
    this.workflows.reserveCenterStock(item, notes).pipe(finalize(() => this.actionLoading.set(false))).subscribe({
      next: (response) => this.replaceSelected(response.workflow),
      error: (error: unknown) => this.actionError.set(ApiError.from(error).message)
    });
  }

  sourceLabel(value: MedicationSource | undefined): string {
    return {
      center_stock: 'Stock del centro',
      patient_to_bring: 'Debe traerla el paciente',
      patient_has_medication: 'La tiene el paciente',
      received_center: 'Recibida en el centro',
      pending_supplier: 'Pendiente de proveedor'
    }[value ?? 'pending_supplier'] ?? 'Pendiente de definir';
  }

  validationLabel(value: unknown): string {
    return ({ approved: 'Validada', rejected: 'Rechazada', invalid: 'Rechazada', denied: 'Rechazada', pending: 'Pendiente' })[String(value ?? 'pending')] ?? 'Pendiente';
  }

  nextAction(item: ApplicationWorkflow): string {
    if (['rejected', 'invalid', 'denied'].includes(String(item.pharmacyValidationStatus))) return 'Revalidar orden';
    if (item.pharmacyValidationStatus !== 'approved') return 'Validar orden';
    if (item.medicationSource === 'center_stock' && item.stockReservationStatus !== 'reserved') return 'Reservar stock';
    if (item.medicationSource === 'pending_supplier') return 'Actualizar procedencia';
    if (item.medicationSource === 'patient_to_bring') return 'Confirmar disponibilidad';
    return 'Ver detalle';
  }

  dateLabel(value: unknown): string {
    const raw = String(value ?? '').trim();
    if (!raw) return 'Fecha pendiente';
    const date = new Date(raw.length === 10 ? `${raw}T00:00:00` : raw);
    return Number.isNaN(date.getTime()) ? raw : new Intl.DateTimeFormat('es-AR', { dateStyle: 'medium' }).format(date);
  }

  timeLabel(value: unknown): string {
    const raw = String(value ?? '').trim();
    if (!raw) return 'Turno pendiente';
    const date = new Date(raw);
    return Number.isNaN(date.getTime()) ? 'Turno pendiente' : new Intl.DateTimeFormat('es-AR', { hour: '2-digit', minute: '2-digit' }).format(date);
  }

  drugLabel(item: ApplicationWorkflow): string {
    return item.drugScheme || (item.applicationDrugs ?? []).map((drug) => drug.drugName).filter(Boolean).join(' · ') || 'Sin drogas informadas';
  }

  reservationReady(item: ApplicationWorkflow | null): boolean {
    return item?.medicationSource === 'center_stock' && item.stockReservationStatus === 'reserved';
  }

  trackItem(_: number, item: ApplicationWorkflow): string {
    return `${item.patientId}-${item.treatmentId}-${item.cycleNumber}-${item.applicationDay}`;
  }

  private replaceSelected(workflow: ApplicationWorkflow): void {
    this.selected.set(workflow);
    this.items.update((items) => items.map((item) => this.sameItem(item, workflow) ? workflow : item));
  }

  private sameItem(left: ApplicationWorkflow, right: ApplicationWorkflow): boolean {
    return left.patientId === right.patientId && left.treatmentId === right.treatmentId
      && left.cycleNumber === right.cycleNumber && left.applicationDay === right.applicationDay;
  }

  private matchesDateScope(item: ApplicationWorkflow, scope: DateScope): boolean {
    if (scope === 'all') return true;
    const raw = this.dateValue(item);
    if (!raw) return true;
    const target = new Date(`${raw.slice(0, 10)}T00:00:00`);
    if (Number.isNaN(target.getTime())) return true;
    const today = new Date(); today.setHours(0, 0, 0, 0);
    if (scope === 'today') return target.getTime() === today.getTime();
    const days = scope === 'next-7' ? 7 : 30;
    const limit = new Date(today); limit.setDate(limit.getDate() + days);
    return target <= limit;
  }

  private dateValue(item: ApplicationWorkflow): string {
    return String(item.plannedDate ?? item.appointment?.scheduledAt ?? '');
  }

  private normalize(value: string): string {
    return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
  }
}
