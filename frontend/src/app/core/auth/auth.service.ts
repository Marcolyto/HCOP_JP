import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, of, shareReplay, tap } from 'rxjs';
import { ApiClientService } from '../api/api-client.service';
import { LoginCommand, SessionResponse } from './auth.models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiClientService);
  private readonly sessionState = signal<SessionResponse | null>(null);
  private pendingLoad?: Observable<SessionResponse>;

  readonly session = this.sessionState.asReadonly();
  readonly authenticated = computed(() => this.sessionState()?.authenticated === true);

  loadSession(force = false): Observable<SessionResponse> {
    if (!force && this.sessionState()) return of(this.sessionState()!);
    if (!force && this.pendingLoad) return this.pendingLoad;
    const load = this.api.get<SessionResponse>('/api/auth/me').pipe(
      catchError(() => of({ ok: true, authenticated: false, loginRequired: true, autoLoginEnabled: false })),
      tap((session) => this.sessionState.set(session)),
      finalize(() => { this.pendingLoad = undefined; }),
      shareReplay({ bufferSize: 1, refCount: false })
    );
    this.pendingLoad = load;
    return load;
  }

  login(command: LoginCommand): Observable<SessionResponse> {
    return this.api.post<SessionResponse>('/api/auth/login', command).pipe(tap((session) => this.sessionState.set(session)));
  }

  logout(): Observable<SessionResponse> {
    return this.api.post<SessionResponse>('/api/auth/logout').pipe(
      tap((session) => this.sessionState.set(session)),
      catchError(() => {
        const session = { ok: true, authenticated: false, loginRequired: true, autoLoginEnabled: false };
        this.sessionState.set(session);
        return of(session);
      })
    );
  }

  setActivePatient(patientId: string | null): Observable<{ ok: boolean; activePatientId: string }> {
    return this.api.put<{ ok: boolean; activePatientId: string }>('/api/auth/active-patient', { patientId: patientId ? Number(patientId) : null }).pipe(
      tap(() => this.sessionState.update((session) => session ? { ...session, activePatientId: patientId } : session))
    );
  }
}
