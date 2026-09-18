import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, OnDestroy, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import {
  ExternalStudySearchResult, externalStudyRequestIsCurrent, normalizeExternalStudySearch, normalizedStudyDni
} from './external-study-search.models';

/** Read-only search scoped to the currently displayed patient. */
@Injectable()
export class ExternalStudySearchService implements OnDestroy {
  private readonly http = inject(HttpClient);
  private subscription: Subscription | null = null;
  private patientId = '';
  private dni = '';
  private requestRevision = 0;

  readonly result = signal<ExternalStudySearchResult | null>(null);
  readonly loading = signal(false);
  readonly error = signal('');

  setContext(patientId: string, dni: unknown): void {
    const normalizedDni = normalizedStudyDni(dni);
    if (this.patientId === patientId && this.dni === normalizedDni) return;
    this.cancel();
    this.patientId = patientId;
    this.dni = normalizedDni;
    this.result.set(null);
    this.error.set('');
    if (patientId && normalizedDni) this.refresh();
  }

  refresh(): void {
    this.cancel();
    const patientId = this.patientId;
    const dni = this.dni;
    if (!patientId || !dni) return;
    const revision = this.requestRevision;
    const isCurrent = () => externalStudyRequestIsCurrent(patientId, dni, revision, this.patientId, this.dni, this.requestRevision);
    this.loading.set(true);
    this.error.set('');
    this.subscription = this.http.get<unknown>(`/api/patients/${encodeURIComponent(patientId)}/external-studies`, { withCredentials: true })
      .subscribe({
        next: payload => {
          if (!isCurrent()) return;
          try { this.result.set(normalizeExternalStudySearch(payload, patientId)); }
          catch { this.error.set('La búsqueda no pudo confirmarse para este paciente. Vuelva a intentarlo.'); }
          this.loading.set(false);
        },
        error: (failure: unknown) => {
          if (!isCurrent()) return;
          this.error.set(failure instanceof HttpErrorResponse && failure.status === 403
            ? 'Su usuario no tiene permiso para consultar las fuentes de estudios.'
            : 'No se pudo actualizar la búsqueda. Los estudios y las imágenes cargados siguen disponibles.');
          this.loading.set(false);
        }
      });
  }

  ngOnDestroy(): void { this.cancel(); }

  private cancel(): void {
    this.requestRevision++;
    this.subscription?.unsubscribe();
    this.subscription = null;
    this.loading.set(false);
  }
}
