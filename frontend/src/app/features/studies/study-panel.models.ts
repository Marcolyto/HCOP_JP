import { clinicalStudyEntries, type ClinicalStudyEntry } from '../../core/clinical/clinical-study-projection';
import { safeStudyImageUrl, studyImages, type StudyImagePreview } from '../../core/clinical/study-image-presentation';
import type { ClinicalRecord, ClinicalState } from '../../core/patients/patient-workspace.models';

export interface StudyPanelUploadEntry {
  readonly entry: ClinicalStudyEntry;
  readonly images: readonly StudyImagePreview[];
}

export interface StudyPanelProjection {
  readonly listEntries: readonly ClinicalStudyEntry[];
  readonly uploadedEntries: readonly StudyPanelUploadEntry[];
}

interface RemoteCandidate {
  readonly entry: ClinicalStudyEntry;
  readonly blocked: boolean;
}

/** Local uploads form the gallery; external results remain document entries. */
export function projectStudyPanel(
  state: ClinicalState | null | undefined,
  queried: readonly ClinicalRecord[],
  query: string
): StudyPanelProjection {
  const localEntries = clinicalStudyEntries({ studies: state?.studies });
  const listEntries: ClinicalStudyEntry[] = [];
  const uploadedEntries: StudyPanelUploadEntry[] = [];
  const localOrder = persistedLocalOrder(state?.studies);
  const term = normalizeSearch(query.trim());
  for (const entry of localEntries) {
    if (!matches(entry.record, term)) continue;
    const images = uploadImages(entry.record);
    if (images.length || hasUploadedFile(entry.record)) uploadedEntries.push({ entry, images });
    else listEntries.push(entry);
  }
  listEntries.push(...remoteEntries(state, queried, new Set(localEntries.map(entry => entry.key))).filter(entry => matches(entry.record, term)));
  listEntries.sort(compareEntries);
  // Older clients prepended uploads. Recover their original upload chronology,
  // without allowing clinical dates or later edits to move a saved file.
  // Undated legacy files stay first; ties retain their persisted array position.
  uploadedEntries.sort((left, right) => originalUploadTime(left.entry.record) - originalUploadTime(right.entry.record)
    || (localOrder.get(left.entry.key) ?? 0) - (localOrder.get(right.entry.key) ?? 0));
  return { listEntries, uploadedEntries };
}

/** Replace edited uploads in place and append new files without mutating state. */
export function mergeStudyUploads(existing: readonly ClinicalRecord[], incoming: readonly ClinicalRecord[]): ClinicalRecord[] {
  const replacements = new Map<string, ClinicalRecord>();
  for (const record of incoming) {
    const id = text(record.id);
    if (id) replacements.set(id, record);
  }
  const knownIds = new Set(existing.map(record => text(record.id)).filter(Boolean));
  const merged = existing.map(record => replacements.get(text(record.id)) ?? record);
  for (const record of incoming) {
    const id = text(record.id);
    if (id && knownIds.has(id)) continue;
    merged.push(id ? replacements.get(id)! : record);
    if (id) knownIds.add(id);
  }
  return merged;
}

function hasUploadedFile(record: ClinicalRecord): boolean {
  return [record.fileUrl, record.fileName, record['fileType'], record.fileCategory, record['dataUrl']].some(value => Boolean(text(value)))
    || records(record.attachments).some(attachment => [attachment['url'], attachment.fileName, attachment['contentType'], attachment.category].some(value => Boolean(text(value))));
}

function uploadImages(record: ClinicalRecord): StudyImagePreview[] {
  const unpreviewable = new Set(records(record.attachments)
    .filter(attachment => attachment['previewable'] === false)
    .map(attachment => safeStudyImageUrl(attachment['url'])).filter(Boolean));
  const images = studyImages(record);
  if (!unpreviewable.size) return images;
  return images.flatMap(image => {
    // A DICOM/HEIC original can still have a saved, browser-readable annotated
    // copy. Exclude only known unsupported URLs, including version choices.
    const versions = image.versions.filter(version => !unpreviewable.has(version.url));
    const fallback = unpreviewable.has(image.url) ? versions.at(-1) : undefined;
    if (unpreviewable.has(image.url) && !fallback) return [];
    return [{ ...image, versions, ...(fallback ? {
      url: fallback.url, versionId: fallback.id, annotated: fallback.id !== 'original'
    } : {}) }];
  });
}

function originalUploadTime(record: ClinicalRecord): number {
  const createdAt = Date.parse(text(record.createdAt));
  if (Number.isFinite(createdAt)) return createdAt;
  const uploadedAt = records(record.attachments).map(attachment => Date.parse(text(attachment['uploadedAt']))).filter(Number.isFinite);
  return uploadedAt.length ? Math.min(...uploadedAt) : Number.NEGATIVE_INFINITY;
}

function persistedLocalOrder(value: unknown): ReadonlyMap<string, number> {
  const order = new Map<string, number>();
  if (!Array.isArray(value)) return order;
  value.forEach((record, index) => {
    if (!record || typeof record !== 'object' || Array.isArray(record)) return;
    const id = text(record.id);
    const key = id ? `id:${id}` : `local:${index}`;
    if (!order.has(key)) order.set(key, index);
  });
  return order;
}

function remoteEntries(state: ClinicalState | null | undefined, queried: readonly ClinicalRecord[], localKeys: ReadonlySet<string>): ClinicalStudyEntry[] {
  // Local identities, including tombstones, suppress every remote copy. Persisted
  // external records take precedence over a fresh response without being rewritten.
  const candidates: RemoteCandidate[] = [
    ...records(state?.studies).map((record, index) => ({ entry: entryFor(record, 'local', index), blocked: true })),
    ...records(state?.externalStudies).map((record, index) => ({ entry: entryFor(record, 'external', index), blocked: Boolean(record.deleted) })).reverse(),
    ...records(queried).map((record, index) => ({ entry: entryFor(record, 'queried', index), blocked: false }))
      .filter(candidate => !candidate.entry.record.deleted)
  ];
  // An unqualified legacy ID can match a known source only when that source is
  // unambiguous. Otherwise a legacy record would join unrelated providers.
  const sourcesById = new Map<string, Set<string>>();
  for (const { entry } of candidates) {
    const source = studySource(entry.record);
    if (!source) continue;
    for (const id of studyIds(entry.record)) {
      const sources = sourcesById.get(id) || new Set<string>();
      sources.add(source);
      sourcesById.set(id, sources);
    }
  }
  const parents = candidates.map((_, index) => index);
  const aliases = new Map<string, number>();
  const root = (index: number): number => {
    while (parents[index] !== index) {
      parents[index] = parents[parents[index]!]!;
      index = parents[index]!;
    }
    return index;
  };
  candidates.forEach((candidate, index) => {
    for (const alias of identityAliases(candidate.entry.record, sourcesById)) {
      const prior = aliases.get(alias);
      if (prior !== undefined) parents[root(index)] = root(prior);
      else aliases.set(alias, index);
    }
  });
  const groups = new Map<number, { blocked: boolean; entry?: ClinicalStudyEntry }>();
  candidates.forEach((candidate, index) => {
    const key = root(index);
    const group: { blocked: boolean; entry?: ClinicalStudyEntry } = groups.get(key) || { blocked: false };
    group.blocked ||= candidate.blocked;
    if (!candidate.blocked && !group.entry) group.entry = candidate.entry;
    groups.set(key, group);
  });
  const entries = [...groups.values()].flatMap(group => !group.blocked && group.entry ? [group.entry] : []);
  const keyCounts = new Map<string, number>();
  for (const entry of entries) keyCounts.set(entry.key, (keyCounts.get(entry.key) || 0) + 1);
  return entries.map(entry => localKeys.has(entry.key) || keyCounts.get(entry.key)! > 1
    ? { ...entry, key: remoteKey(entry.record) }
    : entry);
}

function identityAliases(record: ClinicalRecord, sourcesById: ReadonlyMap<string, ReadonlySet<string>>): string[] {
  const source = studySource(record);
  const urls = [record.reportUrl, record.studyUrl, record.fileUrl];
  return [
    ...studyIds(record).map(id => {
      const sources = sourcesById.get(id);
      const effectiveSource = source || (sources?.size === 1 ? [...sources][0]! : '');
      return `id:${JSON.stringify([effectiveSource, id])}`;
    }),
    ...urls.map(text).filter(Boolean).map(value => `url:${value}`)
  ];
}

function entryFor(record: ClinicalRecord, namespace: string, index: number): ClinicalStudyEntry {
  const id = text(record.id);
  const key = namespace === 'queried' && id
    ? remoteKey(record)
    : id ? `id:${id}` : `${namespace}:${index}`;
  return { key, record };
}

function remoteKey(record: ClinicalRecord): string {
  return `external:${encodeURIComponent(studySource(record))}:id:${encodeURIComponent(text(record.id))}`;
}

function studyIds(record: ClinicalRecord): string[] {
  return [record.id, record.sourceRef?.['externalId']].map(text).filter(Boolean);
}

function studySource(record: ClinicalRecord): string {
  return normalizeSearch(text(record['sourceId']) || text(record.sourceRef?.['sourceId']) || text(record.source));
}

function matches(record: ClinicalRecord, term: string): boolean {
  if (!term) return true;
  return normalizeSearch([
    record.title, record.studyName, record.fileName, record.summary, record.source,
    record.type, record.modality, record.category, record.date, record.notes,
    record['sourceId'], record['sourceLabel'], record['sourceName'], record['studyType'], record.reportUrl, record.studyUrl
  ].map(text).join(' ')).includes(term);
}

function normalizeSearch(value: string): string {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('es-AR');
}

function compareEntries(left: ClinicalStudyEntry, right: ClinicalStudyEntry): number {
  const date = (record: ClinicalRecord): string => String(record.date || record.createdAt || record.updatedAt || '');
  const title = (record: ClinicalRecord): string => String(record.title || record.fileName || '');
  return date(right.record).localeCompare(date(left.record)) || title(right.record).localeCompare(title(left.record), 'es-AR');
}

function text(value: unknown): string {
  return typeof value === 'string' || typeof value === 'number' ? String(value).trim() : '';
}

function records(value: unknown): ClinicalRecord[] {
  return Array.isArray(value) ? value.filter((record): record is ClinicalRecord => Boolean(record && typeof record === 'object' && !Array.isArray(record))) : [];
}
