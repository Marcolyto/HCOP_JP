import { CommonModule } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Component, OnChanges, SimpleChanges, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { DayHospitalComponent } from '../day-hospital/day-hospital.component';
import { QrScannerComponent } from '../qr/qr-scanner.component';
import {
  schedulerBlockedReason,
  schedulerInclusiveInfusionRange,
  schedulerMedicationAvailable,
  schedulerMedicationLabel
} from './care-scheduler.models';
import {
  SchedulerAppointmentSegment,
  schedulerAppointmentBridge,
  schedulerAppointmentCornerClasses,
  schedulerAppointmentSegments,
  schedulerGridInclusiveRange,
  schedulerGridLayout,
  schedulerGridPosition
} from './care-scheduler-grid.models';
import { PatientWorkspaceService } from '../../core/patients/patient-workspace.service';
import { AuthService } from '../../core/auth/auth.service';

type JsonObject = Record<string, unknown>;
interface ScheduleSettings { chairCount: number; slotMinutes: number; startTime: string; endTime: string; }
interface ScheduleSlot { index: number; minutes: number; label: string; }
interface ScheduleDrag { type: 'candidate' | 'infusion'; item: JsonObject; }
interface SchedulePlacement { chair: number; slotIndex: number; span: number; valid: boolean; time: string; }
interface SchedulePieceView { className: string; style: Record<string, string>; }
interface ScheduleFragmentView extends SchedulePieceView { corners: readonly SchedulePieceView[]; }
interface ScheduleAppointmentView {
  item: JsonObject;
  range: string;
  tooltip: string;
  fragments: readonly ScheduleFragmentView[];
  bridges: readonly SchedulePieceView[];
  contentClassName: string;
  contentStyle: Record<string, string>;
}
type HospitalMode = 'new-treatment' | 'pharmacy' | 'chairs' | 'triage' | 'preparation' | 'treatments';
type EmbeddedCareView = 'treatments' | 'pharmacy' | 'triage' | 'preparation' | 'administration';

@Component({ selector: 'app-care-scheduler', imports: [CommonModule, FormsModule, DayHospitalComponent, QrScannerComponent], templateUrl: './care-scheduler.component.html', styleUrl: './care-scheduler.component.scss' })
export class CareSchedulerComponent implements OnChanges {
  readonly open = input(false);
  readonly closed = output<void>();
  private readonly http = inject(HttpClient);
  private readonly workspace = inject(PatientWorkspaceService);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private requestVersion = 0;
  readonly date = signal(this.localDate());
  readonly loading = signal(false);
  readonly error = signal('');
  readonly candidates = signal<JsonObject[]>([]);
  readonly infusions = signal<JsonObject[]>([]);
  readonly settings = signal<ScheduleSettings>({ chairCount: 6, slotMinutes: 10, startTime: '08:00', endTime: '16:00' });
  readonly search = signal('');
  readonly filter = signal('prescribed');
  readonly selectedCandidateId = signal('');
  readonly drag = signal<ScheduleDrag | null>(null);
  readonly dropTarget = signal<SchedulePlacement | null>(null);
  readonly busy = signal(false);
  readonly actionMessage = signal('');
  readonly activeMode = signal<HospitalMode>('chairs');
  readonly chairSurface = signal<'agenda' | 'room'>('agenda');
  readonly detailOpen = signal(false);
  readonly detailLoading = signal(false);
  readonly detailItem = signal<JsonObject | null>(null);
  readonly detailWorkflow = signal<JsonObject>({});
  readonly detailMessage = signal('');
  readonly qrOpen = signal(false);
  readonly qrWorkflowRequest = signal<JsonObject | null>(null);
  readonly removalRequested = signal(false);
  readonly removalReason = signal('Turno retirado de la agenda');
  readonly chairOffset = signal(0);
  readonly visibleChairCount = signal(6);
  readonly canViewDayHospital = computed(() => this.auth.hasPermission('section.day-hospital.view'));
  readonly canManageSchedule = computed(() => this.auth.hasPermission('application.schedule.manage'));
  readonly canManageAdministration = computed(() => this.auth.hasPermission('application.administration.manage'));
  readonly canEditPrescriptions = computed(() => this.auth.hasPermission('section.prescriptions.edit'));
  readonly canViewConfiguration = computed(() => this.auth.hasPermission('section.configuration.view'));
  readonly visibleChairs = computed(() => {
    const total = this.settings().chairCount;
    const count = Math.min(this.visibleChairCount(), total);
    const start = Math.min(this.chairOffset(), Math.max(0, total - count));
    return Array.from({ length: count }, (_, index) => start + index + 1);
  });
  readonly gridLayout = computed(() => schedulerGridLayout(this.settings()));
  readonly chairColumnCount = computed(() => this.visibleChairs().length * this.gridLayout().columnsPerChair);
  readonly slots = computed<ScheduleSlot[]>(() => {
    const layout = this.gridLayout();
    return Array.from({ length: layout.totalSlots }, (_, index) => ({
      index,
      minutes: layout.start + index * layout.slotMinutes,
      label: this.clockLabel(layout.start + index * layout.slotMinutes)
    }));
  });
  readonly filteredCandidates = computed(() => {
    const query = this.normalize(this.search());
    const filter = this.filter();
    return this.candidates().filter(item => {
      if (filter === 'prescription-confirmed' && !this.flag(item, 'prescriptionConfirmed')) return false;
      if (filter === 'missing-prescription' && this.flag(item, 'prescriptionConfirmed')) return false;
      if (filter === 'missing-medication' && this.medicationAvailable(item)) return false;
      if (filter === 'medication-received' && !this.flag(item, 'medicationReceived')) return false;
      if (filter === 'medication-with-patient' && !this.flag(item, 'medicationWithPatient')) return false;
      return !query || this.searchText(item).includes(query);
    }).sort((left, right) => String(left['suggestedDate'] || '').localeCompare(String(right['suggestedDate'] || '')));
  });
  readonly visibleInfusions = computed(() => {
    const chairs = new Set(this.visibleChairs().map(String));
    return this.infusions().filter(item => chairs.has(String(item['chair'] || '').replace(/\D/g, '')));
  });
  readonly appointmentViews = computed<readonly ScheduleAppointmentView[]>(() =>
    this.visibleInfusions()
      .map(item => this.appointmentView(item))
      .filter((view): view is ScheduleAppointmentView => Boolean(view))
  );
  readonly weekday = computed(() => new Intl.DateTimeFormat('es-AR', { weekday: 'long', timeZone: 'UTC' }).format(new Date(`${this.date()}T12:00:00Z`)).replace(/^./, value => value.toUpperCase()));
  readonly chairRange = computed(() => { const chairs = this.visibleChairs(); return chairs.length ? `Sillones ${chairs[0]}–${chairs[chairs.length - 1]}` : 'Sin sillones configurados'; });
  readonly selectedCandidate = computed(() => this.candidates().find(item => this.itemId(item) === this.selectedCandidateId()) || null);
  readonly moduleView = computed<EmbeddedCareView>(() => ({
    'new-treatment': 'treatments', pharmacy: 'pharmacy', triage: 'triage', preparation: 'preparation', treatments: 'treatments'
  } as Record<Exclude<HospitalMode, 'chairs'>, EmbeddedCareView>)[this.activeMode() as Exclude<HospitalMode, 'chairs'>] || 'treatments');

  ngOnChanges(changes: SimpleChanges): void { if (changes['open']?.currentValue) this.refresh(); }
  close(): void { this.closed.emit(); }
  openScheduleConfiguration(): void {
    if (!this.canViewConfiguration()) return;
    void this.router.navigate(['/configuration'], { queryParams: { tab: 'day-hospital' } });
  }
  selectMode(mode: HospitalMode): void {
    if (!this.canViewDayHospital()) return;
    if (mode === 'new-treatment' && !this.canEditPrescriptions()) return;
    this.activeMode.set(mode);
    if (mode === 'chairs') this.refresh();
  }
  openQrAdministration(payload: JsonObject): void {
    if (!this.canManageAdministration()) return;
    const patient = this.object(payload['patient']); const treatment = this.object(payload['treatment']); const infusion = this.object(payload['infusion']);
    const patientId = String(patient['id'] || infusion['patientId'] || ''); const treatmentId = String(treatment['id'] || infusion['treatmentId'] || '');
    if (!patientId || !treatmentId || Number(infusion['cycleNumber'] || 0) < 1 || Number(infusion['applicationDay'] || 0) < 1) { this.actionMessage.set('El QR no devolvió una aplicación completa.'); return; }
    this.qrOpen.set(false); this.activeMode.set('chairs'); this.chairSurface.set('room');
    this.qrWorkflowRequest.set({ ...infusion, patientId, treatmentId });
    this.workspace.activateById(patientId); this.refresh();
  }
  refresh(): void {
    if (!this.canViewDayHospital()) {
      this.candidates.set([]); this.infusions.set([]); this.loading.set(false); this.error.set('');
      return;
    }
    const requestVersion = ++this.requestVersion;
    this.loading.set(true); this.error.set('');
    let completed = 0;
    const finish = (): void => { if (requestVersion !== this.requestVersion) return; completed += 1; if (completed === 3) this.loading.set(false); };
    this.http.get<{ items?: JsonObject[] }>('/api/clinical/configuration/day-hospital-settings', { withCredentials: true }).subscribe({ next: response => { if (requestVersion === this.requestVersion) this.applySettings(response.items?.[0]); finish(); }, error: () => { if (requestVersion === this.requestVersion) this.applySettings(undefined); finish(); } });
    this.http.get<{ infusions?: JsonObject[] }>('/api/clinical/infusions', { params: new HttpParams().set('date', this.date()), withCredentials: true }).subscribe({ next: response => { if (requestVersion === this.requestVersion) this.infusions.set(response.infusions || []); finish(); }, error: response => { if (requestVersion === this.requestVersion) this.error.set(response?.error?.error || 'No se pudo abrir la agenda.'); finish(); } });
    this.http.get<{ candidates?: JsonObject[] }>('/api/clinical/infusion-candidates', { params: new HttpParams().set('includeScheduled', 'false').set('onlySchedulingEligible', 'false'), withCredentials: true }).subscribe({ next: response => { if (requestVersion === this.requestVersion) this.candidates.set(response.candidates || []); finish(); }, error: response => { if (requestVersion === this.requestVersion) this.error.set(response?.error?.error || 'No se pudo abrir la lista de espera.'); finish(); } });
  }
  changeDate(value: string): void { if (/^\d{4}-\d{2}-\d{2}$/.test(value)) { this.date.set(value); this.refresh(); } }
  shiftDate(days: number): void { const next = new Date(`${this.date()}T12:00:00Z`); next.setUTCDate(next.getUTCDate() + days); this.date.set(next.toISOString().slice(0, 10)); this.refresh(); }
  today(): void { this.date.set(this.localDate()); this.refresh(); }
  shiftChairs(direction: number): void { const step = Math.max(1, this.visibleChairCount() - 1); const maximum = Math.max(0, this.settings().chairCount - this.visibleChairCount()); this.chairOffset.set(Math.max(0, Math.min(maximum, this.chairOffset() + direction * step))); }
  zoom(direction: number): void { const total = this.settings().chairCount; this.visibleChairCount.set(Math.max(1, Math.min(total, this.visibleChairCount() + direction))); this.chairOffset.set(Math.min(this.chairOffset(), Math.max(0, total - this.visibleChairCount()))); }
  selectCandidate(item: JsonObject): void {
    const reason = this.blockedReason(item);
    if (reason) { this.actionMessage.set(reason); this.selectedCandidateId.set(''); return; }
    const id = this.itemId(item);
    this.selectedCandidateId.set(this.selectedCandidateId() === id ? '' : id);
    this.actionMessage.set(this.selectedCandidateId() ? `${item['patientName'] || 'Paciente'} · ${this.durationLabel(item)} · lugares disponibles en celeste` : '');
  }
  beginCandidateDrag(event: DragEvent, item: JsonObject): void {
    if (!this.canManageSchedule()) { event.preventDefault(); return; }
    const reason = this.blockedReason(item);
    if (reason || this.busy()) { event.preventDefault(); this.actionMessage.set(reason || 'La agenda está guardando otro cambio.'); return; }
    this.drag.set({ type: 'candidate', item }); this.selectedCandidateId.set(this.itemId(item)); this.dropTarget.set(null);
    if (event.dataTransfer) { event.dataTransfer.effectAllowed = 'move'; event.dataTransfer.setData('text/plain', this.itemId(item)); }
  }
  beginInfusionDrag(event: DragEvent, item: JsonObject): void {
    if (!this.canManageSchedule()) { event.preventDefault(); return; }
    if (this.busy()) { event.preventDefault(); return; }
    this.drag.set({ type: 'infusion', item }); this.dropTarget.set(null);
    if (event.dataTransfer) { event.dataTransfer.effectAllowed = 'move'; event.dataTransfer.setData('text/plain', this.itemId(item)); }
  }
  dragOver(event: DragEvent, slot: ScheduleSlot, chair: number): void {
    if (!this.canManageSchedule()) return;
    const drag = this.drag(); if (!drag) return;
    event.preventDefault();
    const target = this.placement(drag.item, drag.type, chair, slot.index);
    this.dropTarget.set(target);
    if (event.dataTransfer) event.dataTransfer.dropEffect = target.valid ? 'move' : 'none';
  }
  clearDrag(): void { this.drag.set(null); this.dropTarget.set(null); }
  drop(event: DragEvent): void {
    event.preventDefault();
    if (!this.canManageSchedule()) { this.clearDrag(); return; }
    const drag = this.drag(); const target = this.dropTarget();
    this.clearDrag();
    if (!drag || !target?.valid || this.busy()) return;
    const previousInfusions = this.infusions(); const previousCandidates = this.candidates();
    const scheduledAt = `${this.date()}T${target.time}:00-03:00`;
    const durationMinutes = this.duration(drag.item);
    this.busy.set(true); this.actionMessage.set('Guardando turno...');
    if (drag.type === 'candidate') {
      const optimistic: JsonObject = { ...drag.item, id: `optimistic:${Date.now()}`, scheduledAt, chair: String(target.chair), durationMinutes, clinicalStatus: 'planned', appointmentConfirmed: false, optimistic: true };
      this.candidates.set(previousCandidates.filter(item => this.itemId(item) !== this.itemId(drag.item)));
      this.infusions.set([...previousInfusions, optimistic]);
      const body = {
        patientId: drag.item['patientId'], treatmentId: drag.item['treatmentId'], cycleNumber: drag.item['cycleNumber'],
        applicationDay: Number(drag.item['applicationDay'] || 1), scheduledAt, chair: String(target.chair), durationMinutes,
        clinicalStatus: 'planned', pharmacyStatus: 'pending', administrationStatus: 'not_started', appointmentConfirmed: false,
        notes: 'Turno asignado desde el turnero Angular por sillón', medications: drag.item['applicationDrugs'] || drag.item['medications'] || [],
        sourceRef: { scheduler: { scheme: drag.item['scheme'] || '', drugScheme: drag.item['drugScheme'] || drag.item['scheme'] || '', applicationDay: Number(drag.item['applicationDay'] || 1), durationSource: drag.item['durationSource'] || '', timeBasis: 'local-wall-clock-v2', prescriptionConfirmed: this.flag(drag.item, 'prescriptionConfirmed'), medicationReceived: this.flag(drag.item, 'medicationReceived'), medicationWithPatient: this.flag(drag.item, 'medicationWithPatient'), appointmentConfirmed: false } }
      };
      this.http.post('/api/clinical/infusions', body, { withCredentials: true }).subscribe({
        next: () => this.completePlacement('Turno asignado.'),
        error: response => this.rollbackPlacement(previousInfusions, previousCandidates, response)
      });
    } else {
      this.infusions.set(previousInfusions.map(item => this.itemId(item) === this.itemId(drag.item) ? { ...item, scheduledAt, chair: String(target.chair), durationMinutes, optimistic: true } : item));
      this.http.patch(`/api/clinical/infusions/${encodeURIComponent(this.itemId(drag.item))}`, { expectedVersion: drag.item['revision'] || drag.item['version'], scheduledAt, chair: String(target.chair), durationMinutes }, { withCredentials: true }).subscribe({
        next: () => this.completePlacement('Turno reprogramado.'),
        error: response => this.rollbackPlacement(previousInfusions, previousCandidates, response)
      });
    }
  }
  slotClass(slot: ScheduleSlot, chair: number): string {
    const index = slot.index; const target = this.dropTarget();
    if (target && target.chair === chair && index >= target.slotIndex && index < target.slotIndex + target.span) return target.valid ? 'is-drag-target' : 'is-drag-invalid';
    const candidate = this.selectedCandidate();
    return candidate && this.placement(candidate, 'candidate', chair, index).valid ? 'is-candidate-fit' : '';
  }
  chairHeaderStyle(chair: number): Record<string, string> {
    const firstChair = this.visibleChairs()[0] || 1;
    const columnStart = 1 + (chair - firstChair) * this.gridLayout().columnsPerChair;
    return { 'grid-column': `${columnStart} / span ${this.gridLayout().columnsPerChair}`, 'grid-row': '1' };
  }
  slotStyle(slot: ScheduleSlot, chair: number): Record<string, string> {
    const position = schedulerGridPosition(slot.index, chair, this.gridLayout(), this.visibleChairs()[0] || 1);
    return { 'grid-column': String(position.column), 'grid-row': String(position.row) };
  }
  slotStructuralClass(slot: ScheduleSlot, chair: number): string {
    const layout = this.gridLayout();
    const position = schedulerGridPosition(slot.index, chair, layout, this.visibleChairs()[0] || 1);
    return [
      slot.index % layout.slotsPerHour < layout.columnsPerChair ? 'is-hour-start' : '',
      position.subColumn === 0 ? 'is-chair-start' : '',
      position.subColumn === layout.columnsPerChair - 1 ? 'is-chair-end' : '',
      chair === this.visibleChairs()[0] ? 'is-viewport-start' : '',
      this.slotClass(slot, chair)
    ].filter(Boolean).join(' ');
  }
  candidateSelected(item: JsonObject): boolean { return this.itemId(item) === this.selectedCandidateId(); }
  candidateDisabled(item: JsonObject): boolean { return Boolean(this.blockedReason(item)); }
  openDetail(item: JsonObject, remove = false): void {
    this.detailOpen.set(true); this.detailItem.set(item); this.detailWorkflow.set({});
    this.detailMessage.set(''); this.removalRequested.set(remove); this.removalReason.set('Turno retirado de la agenda');
    this.detailLoading.set(true);
    const path = `/api/clinical/application-workflows/${encodeURIComponent(String(item['patientId'] || ''))}/${encodeURIComponent(String(item['treatmentId'] || ''))}/${Number(item['cycleNumber'] || 1)}/${Number(item['applicationDay'] || 1)}`;
    this.http.get<{ workflow?: JsonObject }>(path, { withCredentials: true }).subscribe({
      next: response => { this.detailWorkflow.set(response.workflow || {}); this.detailLoading.set(false); },
      error: response => { this.detailLoading.set(false); this.detailMessage.set(response?.error?.error || 'Se muestran los datos disponibles del turno.'); }
    });
  }
  closeDetail(): void { if (!this.busy()) { this.detailOpen.set(false); this.detailItem.set(null); this.removalRequested.set(false); } }
  confirmAppointment(): void {
    if (!this.canManageSchedule()) return;
    const item = this.detailItem(); if (!item || this.busy()) return;
    this.busy.set(true); this.detailMessage.set('Confirmando turno...');
    this.http.patch<{ infusion?: JsonObject }>(`/api/clinical/infusions/${encodeURIComponent(this.itemId(item))}`, { expectedVersion: item['revision'] || item['version'], appointmentConfirmed: true }, { withCredentials: true }).subscribe({
      next: response => {
        const updated = { ...item, ...(response.infusion || {}), appointmentConfirmed: true };
        this.infusions.update(rows => rows.map(row => this.itemId(row) === this.itemId(item) ? updated : row));
        this.detailItem.set(updated); this.busy.set(false); this.detailMessage.set('Turno confirmado.'); this.loadDetailWorkflow(updated);
      },
      error: response => { this.busy.set(false); this.detailMessage.set(response?.error?.error || 'No se pudo confirmar el turno.'); this.refresh(); }
    });
  }
  removeAppointment(): void {
    if (!this.canManageSchedule()) return;
    const item = this.detailItem(); const reason = this.removalReason().trim();
    if (!item || this.busy()) return;
    if (!reason) { this.detailMessage.set('Indique por qué se quita el turno.'); return; }
    this.busy.set(true); this.detailMessage.set('Quitando turno...');
    this.http.patch(`/api/clinical/infusions/${encodeURIComponent(this.itemId(item))}`, { expectedVersion: item['revision'] || item['version'], scheduledAt: null, chair: null, clinicalStatus: 'cancelled', reason }, { withCredentials: true }).subscribe({
      next: () => { this.busy.set(false); this.detailOpen.set(false); this.detailItem.set(null); this.removalRequested.set(false); this.actionMessage.set('Turno quitado; la aplicación volvió a la lista de espera.'); this.refresh(); },
      error: response => { this.busy.set(false); this.detailMessage.set(response?.error?.error || 'No se pudo quitar el turno.'); this.refresh(); }
    });
  }
  currentDetail(): JsonObject { return this.detailItem() || {}; }
  detailAppointment(): JsonObject { const appointment = this.detailWorkflow()['appointment']; return appointment && typeof appointment === 'object' ? appointment as JsonObject : this.currentDetail(); }
  detailDrugs(): JsonObject[] { const drugs = this.detailWorkflow()['applicationDrugs'] || this.currentDetail()['medications']; return Array.isArray(drugs) ? drugs as JsonObject[] : []; }
  detailDrugLabel(item: JsonObject): string { return [item['drugName'] || item['name'] || item['nombre'], item['prescribedDoseText'] || item['dose'] || item['dosis'], item['route'] || item['via']].filter(Boolean).join(' · '); }
  candidateDate(item: JsonObject): string { return this.dateLabel(String(item['suggestedDate'] || '')); }
  candidateDays(item: JsonObject): string { const value = String(item['suggestedDate'] || ''); if (!value) return 'Sin fecha'; const difference = Math.ceil((new Date(`${value}T12:00:00Z`).getTime() - new Date(`${this.localDate()}T12:00:00Z`).getTime()) / 86400000); return difference <= 0 ? (difference === 0 ? 'Hoy' : `${Math.abs(difference)} d. vencido`) : `${difference} d.`; }
  candidateNear(item: JsonObject): boolean { const value = String(item['suggestedDate'] || ''); return Boolean(value) && (new Date(`${value}T12:00:00Z`).getTime() - new Date(`${this.localDate()}T12:00:00Z`).getTime()) / 86400000 < 5; }
  medicationLabel(item: JsonObject): string { return schedulerMedicationLabel(item); }
  medicationReady(item: JsonObject): boolean { return schedulerMedicationAvailable(item); }
  infusionMatchesSearch(item: JsonObject): boolean {
    const query = this.normalize(this.search());
    return Boolean(query) && this.searchText(item).includes(query);
  }
  prescriptionLabel(item: JsonObject): string { return this.flag(item, 'prescriptionConfirmed') ? 'Prescripción confirmada' : 'Falta prescripción'; }
  infusionRange(item: JsonObject): string { return schedulerInclusiveInfusionRange(item['scheduledAt'], item['durationMinutes']); }
  infusionClass(item: JsonObject): string { return this.flag(item, 'appointmentConfirmed') ? 'is-confirmed' : 'is-pending'; }
  trackSlot(_: number, slot: ScheduleSlot): number { return slot.index; }
  trackItem(_: number, item: JsonObject): string { return String(item['id'] || `${item['patientId']}:${item['treatmentId']}:${item['cycleNumber']}:${item['applicationDay']}`); }

  private applySettings(item?: JsonObject): void {
    const definition = (item?.['definition'] && typeof item['definition'] === 'object' ? item['definition'] : {}) as JsonObject;
    const configuredSlot = Number(definition['slotMinutes'] || 10); const allowed = [5, 10, 15, 20, 30];
    const settings = { chairCount: Math.max(1, Math.min(60, Number(definition['chairCount'] || 6))), slotMinutes: allowed.includes(configuredSlot) ? configuredSlot : 10, startTime: String(definition['startTime'] || '08:00'), endTime: String(definition['endTime'] || '16:00') };
    this.settings.set(settings); this.visibleChairCount.set(Math.min(6, settings.chairCount)); this.chairOffset.set(0);
  }
  private searchText(item: JsonObject): string { return this.normalize([item['patientName'], item['patientDni'], item['dni'], item['scheme'], item['drugScheme'], item['diagnosis'], item['chair']].join(' ')); }
  private appointmentView(item: JsonObject): ScheduleAppointmentView | null {
    if (String(item['clinicalStatus'] || '') === 'cancelled' || !item['scheduledAt']) return null;
    const chair = Number(String(item['chair'] || '').replace(/\D/g, ''));
    const firstChair = this.visibleChairs()[0] || 1;
    if (!Number.isInteger(chair) || chair < firstChair || chair > (this.visibleChairs().at(-1) || firstChair)) return null;
    const layout = this.gridLayout();
    const minutes = this.wallClockMinutes(item['scheduledAt']);
    const slotOffset = (minutes - layout.start) / layout.slotMinutes;
    if (!Number.isInteger(slotOffset)) return null;
    const span = Math.max(1, Math.ceil(this.duration(item) / layout.slotMinutes));
    if (slotOffset < 0 || slotOffset + span > layout.totalSlots) return null;
    const segments = schedulerAppointmentSegments(slotOffset, span, chair, layout, firstChair);
    if (!segments.length) return null;
    const range = schedulerGridInclusiveRange(slotOffset, span, layout)?.label || this.infusionRange(item);
    const chairColumnStart = 1 + (chair - firstChair) * layout.columnsPerChair;
    const statusClass = this.flag(item, 'appointmentConfirmed') ? 'is-confirmed is-appointment-confirmed' : 'is-pending is-appointment-pending';
    const transientClasses = [
      item['optimistic'] ? 'is-saving' : '',
      this.drag()?.type === 'infusion' && this.itemId(this.drag()!.item) === this.itemId(item) ? 'is-drag-source' : '',
      this.search() ? (this.infusionMatchesSearch(item) ? 'is-search-match' : 'is-search-muted') : ''
    ].filter(Boolean).join(' ');
    const fragments: ScheduleFragmentView[] = [];
    const bridges: SchedulePieceView[] = [];
    segments.forEach((segment, index) => {
      const style = this.segmentStyle(segment);
      const cornerClasses = schedulerAppointmentCornerClasses(segments, index);
      const hasChairGap = chair > firstChair && segment.columnStart === chairColumnStart;
      const commonClasses = `${statusClass} ${transientClasses}${hasChairGap ? ' has-chair-gap' : ''}`.trim();
      const bridge = schedulerAppointmentBridge(segments[index - 1], segment);
      if (bridge) {
        const bridgeHasChairGap = chair > firstChair && bridge.columnStart === chairColumnStart;
        bridges.push({
          className: `angular-appointment-bridge ${statusClass} ${transientClasses}${bridgeHasChairGap ? ' has-chair-gap' : ''}`.trim(),
          style: this.segmentStyle(bridge)
        });
      }
      fragments.push({
        className: [
          'angular-appointment-fragment',
          index === 0 ? 'is-appointment-start' : '',
          index === segments.length - 1 ? 'is-appointment-end' : '',
          ...cornerClasses,
          commonClasses
        ].filter(Boolean).join(' '),
        style,
        corners: cornerClasses
          .filter(name => name.startsWith('has-concave-'))
          .map(name => ({ className: `angular-concave-corner ${name} ${commonClasses}`, style }))
      });
    });
    const primaryIndex = segments.reduce((winner, segment, index) => segment.slotCount > segments[winner].slotCount ? index : winner, 0);
    const primary = segments[primaryIndex];
    const contentHasChairGap = chair > firstChair && primary.columnStart === chairColumnStart;
    return {
      item,
      range,
      tooltip: this.appointmentTooltip(item, range),
      fragments,
      bridges,
      contentClassName: `angular-scheduled-appointment angular-appointment-content${segments.length > 1 ? ' is-fragmented' : ''} ${statusClass} ${transientClasses}${contentHasChairGap ? ' has-chair-gap' : ''}`.trim(),
      contentStyle: this.segmentStyle(primary)
    };
  }
  private segmentStyle(segment: Pick<SchedulerAppointmentSegment, 'columnStart' | 'columnEnd' | 'row' | 'rowEnd'>): Record<string, string> {
    return { 'grid-column': `${segment.columnStart} / ${segment.columnEnd}`, 'grid-row': `${segment.row} / ${segment.rowEnd}` };
  }
  private appointmentTooltip(item: JsonObject, range: string): string {
    return [
      item['patientName'] || 'Paciente',
      item['patientDni'] || item['dni'] ? `DNI ${item['patientDni'] || item['dni']}` : '',
      item['scheme'] || item['drugScheme'],
      item['diagnosis'],
      range,
      this.durationLabel(item),
      this.prescriptionLabel(item),
      this.medicationLabel(item),
      this.flag(item, 'appointmentConfirmed') ? 'Turno confirmado' : 'Turno sin confirmar'
    ].filter(Boolean).join(' · ');
  }
  private wallClockMinutes(value: unknown): number {
    const match = /T(\d{2}):(\d{2})/.exec(String(value || ''));
    if (match) return Number(match[1]) * 60 + Number(match[2]);
    const date = new Date(String(value || ''));
    return Number.isNaN(date.getTime()) ? Number.NaN : date.getHours() * 60 + date.getMinutes();
  }
  private placement(item: JsonObject, type: 'candidate' | 'infusion', chair: number, slotIndex: number): SchedulePlacement {
    const span = Math.max(1, Math.ceil(this.duration(item) / this.settings().slotMinutes));
    const start = this.slots()[slotIndex]?.minutes ?? this.clockMinutes(this.settings().startTime);
    const end = start + span * this.settings().slotMinutes;
    const conflict = this.infusions().some(infusion => {
      if (type === 'infusion' && this.itemId(infusion) === this.itemId(item)) return false;
      if (String(infusion['clinicalStatus'] || '') === 'cancelled' || Number(String(infusion['chair'] || '').replace(/\D/g, '')) !== chair || !infusion['scheduledAt']) return false;
      const date = new Date(String(infusion['scheduledAt']));
      const infusionStart = date.getHours() * 60 + date.getMinutes();
      const infusionEnd = infusionStart + Math.ceil(this.duration(infusion) / this.settings().slotMinutes) * this.settings().slotMinutes;
      return infusionStart < end && infusionEnd > start;
    });
    return { chair, slotIndex, span, valid: slotIndex >= 0 && slotIndex + span <= this.slots().length && !conflict, time: this.clockLabel(start) };
  }
  private completePlacement(message: string): void { this.busy.set(false); this.selectedCandidateId.set(''); this.actionMessage.set(message); this.refresh(); }
  private rollbackPlacement(infusions: JsonObject[], candidates: JsonObject[], response: { error?: { error?: string; code?: string } }): void { this.infusions.set(infusions); this.candidates.set(candidates); this.busy.set(false); this.actionMessage.set(response?.error?.code === 'CHAIR_SCHEDULE_CONFLICT' ? 'Ese lugar acaba de ser ocupado. La agenda se actualizó.' : response?.error?.error || 'No se pudo guardar el turno.'); this.refresh(); }
  private itemId(item: JsonObject): string { return String(item['id'] || `${item['patientId']}:${item['treatmentId']}:${item['cycleNumber']}:${item['applicationDay']}`); }
  private duration(item: JsonObject): number { return Math.max(this.settings().slotMinutes, Number(item['durationMinutes'] || this.settings().slotMinutes)); }
  durationLabel(item: JsonObject): string { const minutes = this.duration(item); return minutes >= 60 && minutes % 60 === 0 ? `${minutes / 60} h` : `${minutes} min`; }
  private blockedReason(item: JsonObject): string {
    return schedulerBlockedReason(item);
  }
  private loadDetailWorkflow(item: JsonObject): void {
    const path = `/api/clinical/application-workflows/${encodeURIComponent(String(item['patientId'] || ''))}/${encodeURIComponent(String(item['treatmentId'] || ''))}/${Number(item['cycleNumber'] || 1)}/${Number(item['applicationDay'] || 1)}`;
    this.http.get<{ workflow?: JsonObject }>(path, { withCredentials: true }).subscribe({ next: response => this.detailWorkflow.set(response.workflow || {}), error: () => undefined });
  }
  private medicationAvailable(item: JsonObject): boolean { return schedulerMedicationAvailable(item); }
  private flag(item: JsonObject, key: string): boolean { return Boolean(item[key]); }
  private object(value: unknown): JsonObject { return value && typeof value === 'object' && !Array.isArray(value) ? value as JsonObject : {}; }
  private normalize(value: unknown): string { return String(value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim(); }
  private clockMinutes(value: string): number { const [hours, minutes] = value.split(':').map(Number); return (hours || 0) * 60 + (minutes || 0); }
  private clockLabel(minutes: number): string { return `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`; }
  private dateLabel(value: string): string { const match = value.match(/^(\d{4})-(\d{2})-(\d{2})/); return match ? `${match[3]}/${match[2]}/${match[1]}` : 'Sin fecha'; }
  private timeLabel(value: Date): string { return `${String(value.getHours()).padStart(2, '0')}:${String(value.getMinutes()).padStart(2, '0')}`; }
  private localDate(): string { const now = new Date(); return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 10); }
}
