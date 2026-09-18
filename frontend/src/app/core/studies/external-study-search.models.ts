import type { ClinicalRecord } from '../patients/patient-workspace.models';

export interface ExternalStudySourceResult {
  readonly id: string;
  readonly name: string;
  readonly status: 'ok' | 'empty' | 'error';
  readonly count: number;
  readonly message: string;
}

export interface ExternalStudySearchResult {
  readonly patientId: string;
  readonly searchedAt: string;
  readonly studies: readonly ClinicalRecord[];
  readonly sources: readonly ExternalStudySourceResult[];
  readonly partial: boolean;
  readonly total: number;
}

export function normalizedStudyDni(value: unknown): string {
  const dni = typeof value === 'string' || typeof value === 'number' ? String(value).replace(/[.\s-]/g, '') : '';
  return /^\d{5,10}$/.test(dni) ? dni : '';
}

export function safeExternalStudyUrl(value: unknown): string {
  if (typeof value !== 'string') return '';
  const url = value.trim();
  if (!url || /[\u0000-\u0020\\]/.test(url)) return '';
  if (/^\/(?!\/)/.test(url)) return url;
  if (!/^https?:\/\//i.test(url)) return '';
  try {
    const parsed = new URL(url);
    return parsed.hostname && !parsed.username && !parsed.password ? url : '';
  } catch { return ''; }
}

export function externalStudyRequestIsCurrent(
  expectedPatientId: string, expectedDni: string, expectedRevision: number,
  currentPatientId: string, currentDni: string, currentRevision: number
): boolean {
  return Boolean(expectedPatientId && expectedDni && expectedPatientId === currentPatientId
    && expectedDni === currentDni && expectedRevision === currentRevision);
}

export function normalizeExternalStudySearch(value: unknown, expectedPatientId: string): ExternalStudySearchResult {
  const result = asRecord(value);
  if (text(result['patientId']) !== expectedPatientId) throw new Error('La búsqueda recibida no corresponde al paciente activo.');
  const studies = asArray(result['studies']).map((value, index): ClinicalRecord => {
    const item = asRecord(value);
    return {
      id: text(item['id']) || `external-${text(item['sourceId']) || 'source'}-${index + 1}`,
      date: text(item['date']), type: text(item['type']) || 'Estudio',
      title: text(item['title']) || 'Estudio sin descripción', source: text(item['source']) || 'Fuente externa',
      sourceId: text(item['sourceId']), reportUrl: safeExternalStudyUrl(item['reportUrl']),
      studyUrl: safeExternalStudyUrl(item['studyUrl']), summary: text(item['summary'])
    };
  });
  const sources = asArray(result['sources']).map((value, index): ExternalStudySourceResult => {
    const item = asRecord(value);
    const status = item['status'] === 'ok' || item['status'] === 'empty' ? item['status'] : 'error';
    return {
      id: text(item['id']) || `source-${index + 1}`, name: text(item['name']) || 'Fuente externa', status,
      count: Math.max(0, Number.isFinite(Number(item['count'])) ? Math.trunc(Number(item['count'])) : 0),
      message: text(item['message'])
    };
  });
  return {
    patientId: expectedPatientId, searchedAt: text(result['searchedAt']), studies, sources,
    partial: result['partial'] === true || sources.some(source => source.status === 'error'), total: studies.length
  };
}

function text(value: unknown): string { return typeof value === 'string' || typeof value === 'number' ? String(value).trim() : ''; }
function asRecord(value: unknown): Record<string, unknown> { return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}; }
function asArray(value: unknown): unknown[] { return Array.isArray(value) ? value : []; }
