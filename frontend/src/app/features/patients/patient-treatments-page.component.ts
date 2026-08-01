import { CommonModule } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { ApiClientService } from '../../core/api/api-client.service';
import { ApiError } from '../../core/api/api-error';
import {
  TreatmentCycle,
  TreatmentDetail,
  TreatmentDetailResponse,
  TreatmentListResponse,
  TreatmentSummary
} from '../../core/patients/patient.models';

interface DayHospitalSettings {
  chairCount: number;
  slotMinutes: number;
  startTime: string;
  endTime: string;
}

interface ConfigurationResponse {
  items?: Array<{ definition?: Partial<DayHospitalSettings> }>;
}

interface ScheduleTarget {
  treatmentId: string;
  cycleNumber: number;
  applicationDay: number;
  plannedDate: string;
  durationMinutes: number;
}

interface ApplicationWorkflowDrug {
  drugId?: string;
  drugName?: string;
  sourceItemRef?: string;
  componentKey?: string;
  source?: { sourceItemRef?: string; id?: string };
  calculatedDoseText?: string;
  prescribedDoseText?: string;
  doseUnit?: string;
  unit?: string;
  [key: string]: unknown;
}

interface ApplicationWorkflow {
  revision: number;
  workflowStatus?: string;
  currentStep?: string;
  prescriptionStatus?: string;
  pharmacyValidationStatus?: string;
  pharmacyValidationNotes?: string;
  medicationSource?: string;
  medicationReady?: boolean;
  stockReservationStatus?: string;
  clinicalAuthorizationStatus?: string;
  preparationStatus?: string;
  administrationStatus?: string;
  durationMinutes?: number;
  applicationDrugs?: ApplicationWorkflowDrug[];
  [key: string]: unknown;
}

interface ApplicationWorkflowResponse {
  ok: boolean;
  workflow: ApplicationWorkflow;
}

@Component({
  selector: 'app-patient-treatments-page',
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './patient-treatments-page.component.html',
  styleUrl: './patient-treatments-page.component.scss'
})
export class PatientTreatmentsPageComponent {
  private readonly api = inject(ApiClientService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  readonly query = new FormControl('', { nonNullable: true });
  readonly patientId = signal<string | null>(null);
  readonly treatments = signal<TreatmentSummary[]>([]);
  readonly selected = signal<TreatmentSummary | null>(null);
  readonly detail = signal<TreatmentDetail | null>(null);
  readonly loading = signal(false);
  readonly detailLoading = signal(false);
  readonly error = signal<string | null>(null);
  readonly scheduleError = signal<string | null>(null);
  readonly scheduling = signal(false);
  readonly scheduleTarget = signal<ScheduleTarget | null>(null);
  readonly workflow = signal<ApplicationWorkflow | null>(null);
  readonly workflowLoading = signal(false);
  readonly workflowAction = signal(false);
  readonly workflowError = signal<string | null>(null);
  readonly scheduleSettings = signal<DayHospitalSettings>({
    chairCount: 6, slotMinutes: 10, startTime: '08:00', endTime: '16:00'
  });
  readonly scheduleForm = new FormGroup({
    scheduledAt: new FormControl('', { nonNullable: true }),
    chair: new FormControl('1', { nonNullable: true }),
    durationMinutes: new FormControl('', { nonNullable: true }),
    medicationSource: new FormControl('center_stock', { nonNullable: true })
  });
  readonly chairs = computed(() => Array.from(
    { length: this.scheduleSettings().chairCount }, (_, index) => String(index + 1)
  ));
  readonly filteredTreatments = computed(() => {
    const search = this.normalize(this.query.value);
    if (!search) return this.treatments();
    return this.treatments().filter((item) => this.normalize([
      item.scheme, item.diagnosis, item.status, item.oncologist, item.type
    ].filter(Boolean).join(' ')).includes(search));
  });

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const patientId = params.get('patientId');
      if (patientId) {
        this.patientId.set(patientId);
        this.load(patientId);
      }
    });
  }

  load(patientId = this.patientId()): void {
    if (!patientId) return;
    this.loading.set(true);
    this.error.set(null);
    this.api.get<TreatmentListResponse>(`/api/clinical/patients/${encodeURIComponent(patientId)}/treatments`).pipe(
      finalize(() => this.loading.set(false))
    ).subscribe({
      next: (response) => {
        const items = response.oncology ?? response.treatments ?? [];
        this.treatments.set(items);
        const current = this.selected();
        if (current && !items.some((item) => item.id === current.id)) this.closeDetail();
      },
      error: (error: unknown) => this.error.set(ApiError.from(error).message)
    });
  }

  openDetail(treatment: TreatmentSummary): void {
    const patientId = this.patientId();
    if (!patientId || !treatment.id) return;
    this.selected.set(treatment);
    this.detail.set(null);
    this.detailLoading.set(true);
    this.error.set(null);
    this.api.get<TreatmentDetailResponse>(
      `/api/clinical/patients/${encodeURIComponent(patientId)}/treatments/${encodeURIComponent(treatment.id)}/detail`
    ).pipe(finalize(() => this.detailLoading.set(false))).subscribe({
      next: (response) => this.detail.set(response.detail),
      error: (error: unknown) => this.error.set(ApiError.from(error).message)
    });
  }

  closeDetail(): void {
    this.selected.set(null);
    this.detail.set(null);
    this.closeSchedule();
  }

  hasConsentDocument(treatment: TreatmentSummary): boolean {
    if (treatment['consentAvailable'] === true) return true;
    const status = this.normalize(String(treatment.consentStatus ?? ''));
    return status.includes('firmado') || status.includes('signed') || status.includes('disponible');
  }

  hasPrescriptionDocument(detail: TreatmentDetail | null): boolean {
    return detail?.documentAvailability?.['prescription'] === true;
  }

  openSchedule(cycle: TreatmentCycle, day: { day?: number; plannedDate?: string; date?: string }): void {
    const treatment = this.selected();
    const cycleNumber = Number(cycle.number ?? 0);
    const applicationDay = Number(day.day ?? 0);
    const durationMinutes = Number(treatment?.estimatedDurationMinutes ?? 0);
    if (!treatment?.id || !cycleNumber || !applicationDay || !durationMinutes) {
      this.scheduleError.set('Este protocolo no tiene una duración calculada para asignar el turno.');
      return;
    }
    const plannedDate = String(day.plannedDate || day.date || cycle.plannedDate || cycle.date || this.today());
    const target: ScheduleTarget = {
      treatmentId: treatment.id,
      cycleNumber,
      applicationDay,
      plannedDate,
      durationMinutes
    };
    this.scheduleTarget.set(target);
    this.scheduleError.set(null);
    this.workflow.set(null);
    this.workflowError.set(null);
    this.scheduleForm.setValue({
      scheduledAt: `${plannedDate.slice(0, 10)}T09:00`,
      chair: '1',
      durationMinutes: String(durationMinutes),
      medicationSource: 'center_stock'
    });
    this.loadScheduleSettings();
    this.loadWorkflow(target);
  }

  closeSchedule(): void {
    this.scheduleTarget.set(null);
    this.scheduleError.set(null);
    this.workflow.set(null);
    this.workflowError.set(null);
    this.scheduling.set(false);
    this.workflowLoading.set(false);
    this.workflowAction.set(false);
  }

  saveSchedule(): void {
    const patientId = this.patientId();
    const target = this.scheduleTarget();
    const treatment = this.selected();
    const value = this.scheduleForm.getRawValue();
    if (!patientId || !target || !treatment?.id || this.scheduling()) return;
    if (!value.scheduledAt || !value.chair || Number(value.durationMinutes) !== target.durationMinutes) {
      this.scheduleError.set('Complete fecha, sillón y la duración calculada del esquema.');
      return;
    }
    if (!this.isSchedulingEligible(this.workflow())) {
      this.scheduleError.set(this.workflowGateMessage(this.workflow()));
      return;
    }
    this.scheduling.set(true);
    this.scheduleError.set(null);
    this.api.post(`/api/clinical/infusions`, {
      patientId: Number(patientId),
      treatmentId: treatment.id,
      cycleNumber: target.cycleNumber,
      applicationDay: target.applicationDay,
      scheduledAt: this.localDateTimeToIso(value.scheduledAt),
      chair: value.chair,
      durationMinutes: target.durationMinutes,
      clinicalStatus: 'planned',
      pharmacyStatus: 'pending',
      administrationStatus: 'not_started',
      appointmentConfirmed: false,
      sourceRef: { scheduler: { prescriptionConfirmed: true, medicationReceived: false } }
    }).pipe(finalize(() => this.scheduling.set(false))).subscribe({
      next: () => {
        this.closeSchedule();
        this.openDetail(treatment);
      },
      error: (error: unknown) => this.scheduleError.set(ApiError.from(error).message)
    });
  }

  validatePharmacy(): void {
    const patientId = this.patientId();
    const target = this.scheduleTarget();
    const current = this.workflow();
    const source = this.scheduleForm.controls.medicationSource.value;
    if (!patientId || !target || !current || this.workflowAction()) return;
    this.workflowAction.set(true);
    this.workflowError.set(null);
    if (current.pharmacyValidationStatus === 'approved' && current.medicationSource === source) {
      if (source === 'center_stock') this.reserveCenterStock(patientId, target, current);
      else {
        this.workflow.set(current);
        this.workflowAction.set(false);
      }
      return;
    }
    const path = this.workflowPath(patientId, target);
    this.api.post<ApplicationWorkflowResponse>(`${path}/pharmacy-validation`, {
      expectedRevision: current.revision,
      idempotencyKey: `angular.pharmacy.${Date.now()}`,
      validated: true,
      medicationSource: source,
      notes: 'Orden, dosis, vía, intervalo y premedicación verificados en la interfaz clínica.'
    }).subscribe({
      next: (response) => {
        if (source !== 'center_stock') {
          this.workflow.set(response.workflow);
          this.workflowAction.set(false);
          return;
        }
        this.reserveCenterStock(patientId, target, response.workflow);
      },
      error: (error: unknown) => {
        this.workflowError.set(ApiError.from(error).message);
        this.workflowAction.set(false);
      }
    });
  }

  isSchedulingEligible(workflow: ApplicationWorkflow | null): boolean {
    if (!workflow) return false;
    if (workflow.prescriptionStatus && workflow.prescriptionStatus !== 'confirmed') return false;
    if (workflow.pharmacyValidationStatus !== 'approved') return false;
    if (workflow.medicationSource === 'center_stock' && workflow.stockReservationStatus !== 'reserved') return false;
    if (workflow.medicationSource === 'pending_supplier') return false;
    const clinicalStatus = workflow.clinicalAuthorizationStatus ?? 'pending';
    const preparationStatus = workflow.preparationStatus ?? 'not_started';
    const administrationStatus = workflow.administrationStatus ?? 'not_started';
    return ['pending', 'failed'].includes(clinicalStatus)
      && ['not_started', 'cancelled'].includes(preparationStatus)
      && administrationStatus === 'not_started';
  }

  workflowGateMessage(workflow: ApplicationWorkflow | null): string {
    if (!workflow) return 'Verificando la orden y la disponibilidad antes de asignar el turno.';
    if (workflow.prescriptionStatus && workflow.prescriptionStatus !== 'confirmed') return 'La prescripción debe estar confirmada por el médico.';
    if (workflow.pharmacyValidationStatus !== 'approved') return 'Farmacia debe validar esta aplicación antes de asignar el turno.';
    if (workflow.medicationSource === 'center_stock' && workflow.stockReservationStatus !== 'reserved') return 'Farmacia debe reservar el stock del centro antes de asignar el turno.';
    if (workflow.medicationSource === 'pending_supplier') return 'Defina la procedencia de la medicación antes de asignar el turno.';
    return 'La aplicación ya avanzó en el circuito y no admite un turno nuevo.';
  }

  workflowStatusLabel(value: unknown): string {
    const normalized = this.normalize(String(value ?? ''));
    const labels: Record<string, string> = {
      approved: 'Validada', pending: 'Pendiente', reserved: 'Reservado',
      released: 'Liberado', center_stock: 'Stock del centro',
      patient_to_bring: 'La trae el paciente', patient_has_medication: 'La tiene el paciente',
      received_center: 'Recibida en el centro', pending_supplier: 'Proveedor pendiente'
    };
    return labels[normalized] ?? String(value || 'Pendiente');
  }

  scheduleStep(): number {
    return this.scheduleSettings().slotMinutes * 60;
  }

  private loadScheduleSettings(): void {
    this.api.get<ConfigurationResponse>('/api/clinical/configuration/day-hospital-settings').subscribe({
      next: (response) => {
        const definition = response.items?.find((item) => item.definition)?.definition;
        if (!definition) return;
        const chairCount = Number(definition.chairCount ?? 6);
        const slotMinutes = Number(definition.slotMinutes ?? 10);
        this.scheduleSettings.set({
          chairCount: chairCount >= 1 && chairCount <= 200 ? chairCount : 6,
          slotMinutes: [5, 10, 15, 20, 30].includes(slotMinutes) ? slotMinutes : 10,
          startTime: definition.startTime || '08:00',
          endTime: definition.endTime || '16:00'
        });
      },
      error: () => { /* La agenda conserva valores seguros si el usuario no ve Configuración. */ }
    });
  }

  private loadWorkflow(target: ScheduleTarget): void {
    const patientId = this.patientId();
    if (!patientId) return;
    this.workflowLoading.set(true);
    this.api.get<ApplicationWorkflowResponse>(this.workflowPath(patientId, target)).pipe(
      finalize(() => this.workflowLoading.set(false))
    ).subscribe({
      next: (response) => {
        this.workflow.set(response.workflow);
        const workflowDuration = Number(response.workflow.durationMinutes ?? 0);
        if (workflowDuration > 0 && workflowDuration !== target.durationMinutes) {
          const updatedTarget = { ...target, durationMinutes: workflowDuration };
          this.scheduleTarget.set(updatedTarget);
          this.scheduleForm.controls.durationMinutes.setValue(String(workflowDuration));
        }
        const source = response.workflow.medicationSource;
        if (source && ['center_stock', 'patient_to_bring', 'patient_has_medication', 'received_center', 'pending_supplier'].includes(source)) {
          this.scheduleForm.controls.medicationSource.setValue(source);
        }
      },
      error: (error: unknown) => this.workflowError.set(ApiError.from(error).message)
    });
  }

  private reserveCenterStock(patientId: string, target: ScheduleTarget, workflow: ApplicationWorkflow): void {
    const drugs = workflow.applicationDrugs ?? [];
    const components = drugs.map((drug, index) => this.stockComponent(drug, index + 1)).filter(Boolean);
    if (components.length !== drugs.length || components.length === 0) {
      this.workflowError.set('No se pudieron interpretar las dosis de todas las drogas para reservar stock. Revise la aplicación en Farmacia.');
      this.workflowAction.set(false);
      return;
    }
    this.api.post<ApplicationWorkflowResponse>(`${this.workflowPath(patientId, target)}/stock-reservation`, {
      expectedRevision: workflow.revision,
      idempotencyKey: `angular.stock.${Date.now()}`,
      reserved: true,
      medicationSource: 'center_stock',
      verificationMethod: 'manual',
      notes: 'Disponibilidad física verificada por Farmacia desde el circuito de aplicación.',
      components
    }).subscribe({
      next: (response) => {
        this.workflow.set(response.workflow);
        this.workflowAction.set(false);
      },
      error: (error: unknown) => {
        this.workflowError.set(ApiError.from(error).message);
        this.workflowAction.set(false);
      }
    });
  }

  private stockComponent(drug: ApplicationWorkflowDrug, ordinal: number): Record<string, unknown> | null {
    const doseText = String(drug.calculatedDoseText ?? drug.prescribedDoseText ?? '');
    const match = doseText.match(/[+-]?\d+(?:[.,]\d+)?/);
    const drugName = String(drug.drugName ?? '').trim();
    if (!match || !drugName) return null;
    const quantity = Number(match[0].replace(',', '.'));
    if (!Number.isFinite(quantity) || quantity <= 0) return null;
    const explicit = String(drug.sourceItemRef ?? drug.componentKey ?? drug.source?.sourceItemRef ?? drug.source?.id ?? '').trim();
    const stem = explicit || String(drug.drugId ?? '').trim() || this.normalize(drugName).replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'component';
    const componentKey = explicit || `${stem}-${ordinal}`;
    const unit = String(drug.doseUnit ?? drug.unit ?? 'mg').trim() || 'mg';
    return {
      componentKey,
      drugId: drug.drugId ?? null,
      drugName,
      requestedQuantity: quantity,
      requestedQuantityText: `${quantity} ${unit}`,
      unit,
      inventoryLotId: null
    };
  }

  private workflowPath(patientId: string, target: ScheduleTarget): string {
    return `/api/clinical/application-workflows/${encodeURIComponent(patientId)}/${encodeURIComponent(target.treatmentId)}/${target.cycleNumber}/${target.applicationDay}`;
  }

  private localDateTimeToIso(value: string): string {
    const normalized = value.length === 16 ? `${value}:00` : value;
    return new Date(`${normalized}-03:00`).toISOString();
  }

  private today(): string {
    return new Date().toISOString().slice(0, 10);
  }

  statusClass(value: unknown): string {
    const status = this.normalize(String(value ?? 'pending')).replace(/\s+/g, '-');
    return `status-${status || 'pending'}`;
  }

  statusLabel(value: unknown): string {
    const status = this.normalize(String(value ?? 'pending'));
    const labels: Record<string, string> = {
      completed: 'Completado', current: 'Actual', partial: 'Parcial', pending: 'Pendiente',
      cancelled: 'Cancelado', canceled: 'Cancelado', administered: 'Administrado',
      planned: 'Planificado', withheld: 'No administrado'
    };
    return labels[status] ?? String(value || 'Pendiente');
  }

  dateLabel(value: unknown): string {
    const raw = String(value ?? '').trim();
    if (!raw) return 'Sin fecha';
    const date = new Date(raw.length === 10 ? `${raw}T00:00:00` : raw);
    if (Number.isNaN(date.getTime())) return raw;
    return new Intl.DateTimeFormat('es-AR', { dateStyle: 'medium' }).format(date);
  }

  applicationLabel(cycle: TreatmentCycle): string {
    const count = cycle.applications?.length ?? 0;
    return count === 1 ? '1 aplicación registrada' : `${count} aplicaciones registradas`;
  }

  trackCycle(index: number, cycle: TreatmentCycle): string | number {
    return cycle.number ?? index;
  }

  private normalize(value: string): string {
    return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
  }
}
