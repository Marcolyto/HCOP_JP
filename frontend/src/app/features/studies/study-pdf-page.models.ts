import { safeStudyImageUrl } from '../../core/clinical/study-image-presentation';
import type { ClinicalRecord } from '../../core/patients/patient-workspace.models';

export interface StudyPdfPageVersion {
  readonly id: string;
  readonly url: string;
  readonly label: string;
  readonly kind: 'annotation' | 'snapshot';
}

export interface StudyPdfPagePreview {
  readonly pageNumber: number;
  readonly versions: readonly StudyPdfPageVersion[];
  readonly activeVersionId: string;
}

export interface StudyPdfPageAction {
  readonly pageNumber: number;
  readonly patientId: string;
  readonly documentUrl: string;
  readonly versionId: string;
  readonly url?: string;
  readonly file?: File;
}

type JsonRecord = Record<string, unknown>;

/** Page copies remain separate from the original PDF and ordinary image assets. */
export function studyPdfPages(record: ClinicalRecord): StudyPdfPagePreview[] {
  const pages = new Map<number, { versions: Map<string, StudyPdfPageVersion>; activeVersionId: string }>();
  for (const asset of objects(record['pdfPageAssets'])) {
    const pageNumber = Number(asset['pageNumber']);
    if (!validPage(pageNumber)) continue;
    const page = pages.get(pageNumber) || { versions: new Map<string, StudyPdfPageVersion>(), activeVersionId: 'original' };
    for (const version of objects(asset['versions'])) {
      const id = text(version['id']);
      const url = safeStudyImageUrl(version['url']);
      if (!id || id === 'original' || !url) continue;
      const kind = version['kind'] === 'snapshot' ? 'snapshot' : 'annotation';
      page.versions.set(id, { id, url, kind, label: '' });
    }
    const activeId = text(asset['activeVersionId']);
    page.activeVersionId = activeId === 'original' || page.versions.has(activeId)
      ? activeId : [...page.versions.values()].at(-1)?.id || 'original';
    pages.set(pageNumber, page);
  }
  return [...pages.entries()].sort(([left], [right]) => left - right).map(([pageNumber, page]) => {
    let annotations = 0;
    const versions = [...page.versions.values()].map(version => ({ ...version,
      label: version.kind === 'snapshot' ? 'Copia de la página original' : `Copia anotada ${++annotations}` }));
    return { pageNumber, versions, activeVersionId: page.activeVersionId };
  });
}

export function appendStudyPdfPageVersion(
  record: ClinicalRecord, pageNumber: number, url: string, id: string,
  audit: JsonRecord, kind: 'annotation' | 'snapshot' = 'annotation'
): ClinicalRecord {
  const safeUrl = safeStudyImageUrl(url);
  const versionId = text(id);
  if (!validPage(pageNumber) || !safeUrl || !versionId || versionId === 'original') {
    throw new Error('La copia de la página PDF no es válida.');
  }
  const next = structuredClone(record);
  const assets = objects(next['pdfPageAssets']);
  const existing = assets.filter(asset => Number(asset['pageNumber']) === pageNumber)
    .flatMap(asset => objects(asset['versions'])).find(version => text(version['id']) === versionId);
  if (existing) {
    if (safeStudyImageUrl(existing['url']) !== safeUrl || (existing['kind'] === 'snapshot' ? 'snapshot' : 'annotation') !== kind) {
      throw new Error('La versión de la página PDF ya existe con otro contenido.');
    }
    return next;
  }
  let asset = assets.find(candidate => Number(candidate['pageNumber']) === pageNumber);
  if (!asset) { asset = { pageNumber, versions: [], activeVersionId: 'original' }; assets.push(asset); }
  const at = text(audit['at']) || new Date().toISOString();
  asset['versions'] = [...objects(asset['versions']), { id: versionId, url: safeUrl, kind, mime: 'image/png', createdAt: at, audit: structuredClone(audit) }];
  if (kind === 'annotation') asset['activeVersionId'] = versionId;
  asset['updatedAt'] = at;
  next['pdfPageAssets'] = assets;
  next.updatedAt = at;
  return next;
}

function validPage(value: number): boolean { return Number.isSafeInteger(value) && value > 0; }
function text(value: unknown): string { return typeof value === 'string' ? value.trim() : ''; }
function objects(value: unknown): JsonRecord[] { return Array.isArray(value) ? value.filter((item): item is JsonRecord => Boolean(item && typeof item === 'object' && !Array.isArray(item))) : []; }
