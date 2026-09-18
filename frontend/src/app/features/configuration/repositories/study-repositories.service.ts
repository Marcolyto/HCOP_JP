import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { RepositoryUpdate, StudyRepository, normalizeRepositories } from './study-repositories.models';

@Injectable({ providedIn: 'root' })
export class StudyRepositoriesService {
  private readonly http = inject(HttpClient);

  load(): Observable<readonly StudyRepository[]> {
    return this.http.get<unknown>('/api/admin/study-repositories', { withCredentials: true }).pipe(map(normalizeRepositories));
  }

  save(id: string, update: RepositoryUpdate): Observable<readonly StudyRepository[]> {
    return this.http.put<unknown>(`/api/admin/study-repositories/${encodeURIComponent(id)}`, update, { withCredentials: true })
      .pipe(map(normalizeRepositories));
  }
}
