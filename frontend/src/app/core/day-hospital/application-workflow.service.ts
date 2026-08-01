import { Injectable, inject } from '@angular/core';
import { ApiClientService } from '../api/api-client.service';
import {
  ApplicationWorkflow,
  ApplicationWorkflowDrug,
  ApplicationWorkflowListResponse,
  ApplicationWorkflowResponse,
  MedicationSource
} from './application-workflow.models';

@Injectable({ providedIn: 'root' })
export class ApplicationWorkflowService {
  private readonly api = inject(ApiClientService);

  listPharmacy(query = '', medicationSource = '') {
    return this.api.get<ApplicationWorkflowListResponse>('/api/clinical/application-workflows', {
      queue: 'pharmacy', q: query, medicationSource
    });
  }

  list(queue: 'pharmacy' | 'triage' | 'preparation' | 'administration', date = '', query = '') {
    return this.api.get<ApplicationWorkflowListResponse>('/api/clinical/application-workflows', { queue, date, q: query });
  }

  preparationUsers() {
    return this.api.get<{ items?: Array<{ id?: string | number; userId?: string | number; username?: string; displayName?: string; active?: boolean }> }>('/api/clinical/users', {
      permission: 'application.preparation.manage', capability: 'application.preparation.manage'
    });
  }

  get(item: Pick<ApplicationWorkflow, 'patientId' | 'treatmentId' | 'cycleNumber' | 'applicationDay'>) {
    return this.api.get<ApplicationWorkflowResponse>(this.path(item));
  }

  validatePharmacy(
    item: ApplicationWorkflow,
    validated: boolean,
    medicationSource: MedicationSource,
    notes: string
  ) {
    return this.api.post<ApplicationWorkflowResponse>(`${this.path(item)}/pharmacy-validation`, {
      expectedRevision: item.revision,
      idempotencyKey: `angular.pharmacy.${crypto.randomUUID()}`,
      validated,
      medicationSource,
      notes
    });
  }

  reserveCenterStock(item: ApplicationWorkflow, notes: string) {
    const components = (item.applicationDrugs ?? [])
      .map((drug, index) => this.stockComponent(drug, index + 1))
      .filter((component): component is Record<string, unknown> => component !== null);
    return this.api.post<ApplicationWorkflowResponse>(`${this.path(item)}/stock-reservation`, {
      expectedRevision: item.revision,
      idempotencyKey: `angular.stock.${crypto.randomUUID()}`,
      reserved: true,
      medicationSource: 'center_stock',
      verificationMethod: 'manual',
      notes,
      components
    });
  }

  clinicalAuthorization(item: ApplicationWorkflow, body: Record<string, unknown>) {
    return this.api.post<ApplicationWorkflowResponse>(`${this.path(item)}/clinical-authorization`, {
      expectedRevision: item.revision,
      idempotencyKey: `angular.triage.${crypto.randomUUID()}`,
      ...body
    });
  }

  preparationStart(item: ApplicationWorkflow, notes = '') {
    return this.api.post<ApplicationWorkflowResponse>(`${this.path(item)}/preparation/start`, {
      expectedRevision: item.revision, idempotencyKey: `angular.preparation.start.${crypto.randomUUID()}`, notes
    });
  }

  preparationComplete(item: ApplicationWorkflow, body: Record<string, unknown>) {
    return this.api.post<ApplicationWorkflowResponse>(`${this.path(item)}/preparation/complete`, {
      expectedRevision: item.revision, idempotencyKey: `angular.preparation.complete.${crypto.randomUUID()}`, ...body
    });
  }

  preparationRelease(item: ApplicationWorkflow, notes = '') {
    return this.api.post<ApplicationWorkflowResponse>(`${this.path(item)}/preparation/release`, {
      expectedRevision: item.revision, idempotencyKey: `angular.preparation.release.${crypto.randomUUID()}`, notes
    });
  }

  administrationStart(item: ApplicationWorkflow, body: Record<string, unknown>) { return this.command(item, 'administration/start', 'start', body); }
  administrationComplete(item: ApplicationWorkflow, body: Record<string, unknown>) { return this.command(item, 'administration/complete', 'complete', body); }
  administrationInterrupt(item: ApplicationWorkflow, body: Record<string, unknown>) { return this.command(item, 'administration/interrupt', 'interrupt', body); }
  administrationResolve(item: ApplicationWorkflow, body: Record<string, unknown>) { return this.command(item, 'administration/resolve', 'resolve', body); }

  administrationUsers() {
    return this.api.get<{ items?: Array<{ id?: string | number; userId?: string | number; username?: string; displayName?: string; active?: boolean }> }>('/api/clinical/users', {
      permission: 'application.administration.manage', capability: 'application.administration.manage'
    });
  }

  canBuildReservation(item: ApplicationWorkflow): boolean {
    const drugs = item.applicationDrugs ?? [];
    return drugs.length > 0 && drugs.every((drug, index) => this.stockComponent(drug, index + 1) !== null);
  }

  private path(item: Pick<ApplicationWorkflow, 'patientId' | 'treatmentId' | 'cycleNumber' | 'applicationDay'>): string {
    return `/api/clinical/application-workflows/${encodeURIComponent(item.patientId)}/${encodeURIComponent(item.treatmentId)}/${item.cycleNumber}/${item.applicationDay}`;
  }

  private command(item: ApplicationWorkflow, suffix: string, action: string, body: Record<string, unknown>) {
    return this.api.post<ApplicationWorkflowResponse>(`${this.path(item)}/${suffix}`, {
      expectedRevision: item.revision, idempotencyKey: `angular.administration.${action}.${crypto.randomUUID()}`, ...body
    });
  }

  private stockComponent(drug: ApplicationWorkflowDrug, ordinal: number): Record<string, unknown> | null {
    const doseText = String(drug.calculatedDoseText ?? drug.prescribedDoseText ?? '');
    const numeric = doseText.match(/[+-]?\d+(?:[.,]\d+)?/);
    const drugName = String(drug.drugName ?? '').trim();
    if (!numeric || !drugName) return null;
    const requestedQuantity = Number(numeric[0].replace(',', '.'));
    if (!Number.isFinite(requestedQuantity) || requestedQuantity <= 0) return null;
    const explicit = String(drug.sourceItemRef ?? drug.componentKey ?? drug.source?.sourceItemRef ?? drug.source?.id ?? '').trim();
    const stem = explicit || String(drug.drugId ?? '').trim() || this.slug(drugName) || 'component';
    const componentKey = explicit || `${stem}-${ordinal}`;
    const unit = String(drug.doseUnit ?? drug.unit ?? 'mg').trim() || 'mg';
    return {
      componentKey,
      drugId: drug.drugId ?? null,
      drugName,
      requestedQuantity,
      requestedQuantityText: `${requestedQuantity} ${unit}`,
      unit,
      inventoryLotId: null
    };
  }

  private slug(value: string): string {
    return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '')
      .toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
  }
}
