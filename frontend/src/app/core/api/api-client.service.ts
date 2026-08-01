import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
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

  getBlob(path: string): Observable<Blob> {
    return this.http.get(path, { responseType: 'blob', withCredentials: true }).pipe(this.asApiError());
  }

  post<T>(path: string, body: unknown = {}): Observable<T> {
    return this.http.post<T>(path, body, { withCredentials: true }).pipe(this.asApiError());
  }

  upload<T>(path: string, file: File): Observable<T> {
    const headers = new HttpHeaders({ 'Content-Type': file.type || 'application/octet-stream' });
    return this.http.post<T>(path, file, { headers, withCredentials: true }).pipe(this.asApiError());
  }

  putFile<T>(path: string, file: File): Observable<T> {
    const headers = new HttpHeaders({ 'Content-Type': file.type || 'application/octet-stream' });
    return this.http.put<T>(path, file, { headers, withCredentials: true }).pipe(this.asApiError());
  }

  delete<T>(path: string, headers: Record<string, string> = {}): Observable<T> {
    return this.http.delete<T>(path, { headers: new HttpHeaders(headers), withCredentials: true }).pipe(this.asApiError());
  }

  put<T>(path: string, body: unknown = {}): Observable<T> {
    return this.http.put<T>(path, body, { withCredentials: true }).pipe(this.asApiError());
  }

  patch<T>(path: string, body: unknown = {}): Observable<T> {
    return this.http.patch<T>(path, body, { withCredentials: true }).pipe(this.asApiError());
  }

  private asApiError<T>() {
    return catchError<T, Observable<never>>((error: unknown) => throwError(() => ApiError.from(error)));
  }
}
