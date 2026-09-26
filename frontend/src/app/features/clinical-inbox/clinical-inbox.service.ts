import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import {
  ClinicalInboxMutationResponse,
  ClinicalInboxPage,
  ClinicalInboxResolutionRequest,
  normalizeClinicalInboxItem,
  normalizeClinicalInboxPage
} from './clinical-inbox.models';

@Injectable({ providedIn: 'root' })
export class ClinicalInboxService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/clinical/treatment-workflow-requests';

  load(): Observable<ClinicalInboxPage> {
    const params = new HttpParams().set('t', Date.now().toString());
    return this.http.get<unknown>(`${this.endpoint}/inbox`, {
      params,
      withCredentials: true
    }).pipe(map(normalizeClinicalInboxPage));
  }

  markSeen(id: string): Observable<ClinicalInboxMutationResponse> {
    return this.http.patch<ClinicalInboxMutationResponse>(
      `${this.endpoint}/${encodeURIComponent(id)}/seen`,
      {},
      { withCredentials: true }
    ).pipe(map((response) => ({
      ...response,
      item: normalizeClinicalInboxItem(response.item) ?? response.item
    }) as ClinicalInboxMutationResponse));
  }

  resolve(
    id: string,
    request: ClinicalInboxResolutionRequest
  ): Observable<ClinicalInboxMutationResponse> {
    return this.http.post<ClinicalInboxMutationResponse>(
      `${this.endpoint}/${encodeURIComponent(id)}/resolve`,
      request,
      { withCredentials: true }
    ).pipe(map((response) => ({
      ...response,
      item: normalizeClinicalInboxItem(response.item) ?? response.item
    }) as ClinicalInboxMutationResponse));
  }
}

export function clinicalInboxApiMessage(
  error: unknown,
  fallback = 'No se pudo completar la operación.'
): string {
  if (error instanceof HttpErrorResponse) {
    const body = error.error;
    if (body && typeof body === 'object' && typeof body.error === 'string' && body.error.trim()) {
      return body.error.trim();
    }
    if (typeof body === 'string' && body.trim()) return body.trim();
  }
  return error instanceof Error && error.message.trim() ? error.message : fallback;
}

export function clinicalInboxIsUnauthorized(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 401;
}

export function clinicalInboxRefreshMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if ([0, 502, 503, 504].includes(error.status)) {
      return 'No se pudo conectar con el servidor. Volvé a intentar.';
    }
    if (error.status === 403) return 'No tenés permiso para consultar las solicitudes clínicas.';
  }
  return 'No se pudieron cargar las solicitudes. Volvé a intentar.';
}
