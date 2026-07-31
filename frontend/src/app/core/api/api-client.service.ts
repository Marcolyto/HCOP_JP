import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, throwError } from 'rxjs';
import { ApiError } from './api-error';

@Injectable({ providedIn: 'root' })
export class ApiClientService {
  private readonly http = inject(HttpClient);

  get<T>(path: string, query: Record<string, string | number | boolean | null | undefined> = {}): Observable<T> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(query)) {
      if (value !== null && value !== undefined && value !== '') params = params.set(key, String(value));
    }
    return this.http.get<T>(path, { params, withCredentials: true }).pipe(this.asApiError());
  }

  post<T>(path: string, body: unknown = {}): Observable<T> {
    return this.http.post<T>(path, body, { withCredentials: true }).pipe(this.asApiError());
  }

  put<T>(path: string, body: unknown = {}): Observable<T> {
    return this.http.put<T>(path, body, { withCredentials: true }).pipe(this.asApiError());
  }

  private asApiError<T>() {
    return catchError<T, Observable<never>>((error: unknown) => throwError(() => ApiError.from(error)));
  }
}
