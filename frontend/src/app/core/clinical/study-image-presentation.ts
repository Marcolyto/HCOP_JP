import type { ClinicalRecord } from '../patients/patient-workspace.models';

type JsonRecord = Record<string, unknown>;
export interface StudyImageVersion { readonly id: string; readonly url: string; readonly label: string; }
export interface StudyImagePreview {
  readonly id: string;
  readonly sourceUrl: string;
  readonly url: string;
  readonly versionId: string;
  readonly annotated: boolean;
  readonly title: string;
  readonly versions: readonly StudyImageVersion[];
}

export function safeStudyImageUrl(value: unknown): string {
  if (typeof value !== 'string') return '';
  const url = value.trim();
  if (!url || /[\u0000-\u0020\\]/.test(url)) return '';
  if (/^\/(?!\/)/.test(url)) return url;
  if (/^https?:\/\//i.test(url)) {
    try { const parsed = new URL(url); return parsed.username || parsed.password ? '' : url; } catch { return ''; }
  }
  return /^data:image\/(?:png|jpe?g|gif|webp|avif|bmp);base64,[a-z0-9+/=]+$/i.test(url) ? url : '';
}

/** Resolve saved annotations before the original, including images saved by the previous interface. */
export function studyImages(record: ClinicalRecord): StudyImagePreview[] {
  const assets = objects(record['imageAssets']);
  const preferred = urls(record['displayImageUrls']);
  const fallback = [
    ...(String(record['fileType'] || '').startsWith('image/') ? urls([record['dataUrl']]) : []),
    ...urls([record['previewImageUrl']]), ...urls(record['imageUrls']), ...urls(record['documentImageUrls'])
  ];
  if (!preferred.length && !fallback.length) {
    if (record.fileCategory === 'image' || String(record['fileType'] || '').startsWith('image/')) fallback.push(...urls([record.fileUrl]));
    fallback.push(...objects(record.attachments).filter(a => a['category'] === 'image' || String(a['contentType'] || '').startsWith('image/')).flatMap(a => urls([a['url']])));
  }
  const sources = [...new Set([
    ...(preferred.length ? preferred : fallback),
    ...assets.flatMap(a => urls([a['originalUrl'] || a['sourceUrl'] || a['localUrl'] || a['currentUrl']]))
  ])];
  const seen = new Set<string>();
  return sources.flatMap((source, index) => {
    const asset = assets.find(a => [a['originalUrl'], a['sourceUrl'], a['localUrl'], a['currentUrl'], ...objects(a['versions']).map(v => v['url'])].includes(source));
    const sourceUrl = safeStudyImageUrl(asset?.['originalUrl'] || asset?.['sourceUrl']) || source;
    const id = String(asset?.['id'] || `image-${imageHash(sourceUrl)}`);
    if (seen.has(id)) return [];
    seen.add(id);
    const saved = objects(asset?.['versions']).filter(v => safeStudyImageUrl(v['url']));
    const active = saved.find(v => v['id'] === asset?.['activeVersionId']) || saved.at(-1);
    const url = safeStudyImageUrl(active?.['url'] || asset?.['currentUrl'] || asset?.['localUrl']) || sourceUrl;
    return [{
      id, sourceUrl, url, versionId: String(active?.['id'] || 'original'), annotated: Boolean(active),
      title: `${record.title || record.fileName || 'Estudio'} · Imagen ${index + 1}`,
      versions: [{ id: 'original', url: sourceUrl, label: 'Original' }, ...saved.map((v, i) => ({
        id: String(v['id'] || `version-${i + 1}`), url: safeStudyImageUrl(v['url']), label: `Copia anotada ${i + 1}`
      }))]
    }];
  });
}

/** Append a version without changing the original or any attachment already used in an evolution. */
export function appendStudyImageVersion(record: ClinicalRecord, image: StudyImagePreview, url: string, versionId: string, audit: JsonRecord): ClinicalRecord {
  const safeUrl = safeStudyImageUrl(url);
  if (!safeUrl) throw new Error('La copia anotada no tiene una dirección de imagen válida.');
  const next = structuredClone(record);
  const assets = objects(next['imageAssets']);
  let asset = assets.find(a => a['id'] === image.id || (a['originalUrl'] || a['sourceUrl']) === image.sourceUrl);
  if (!asset) { asset = { id: image.id, originalUrl: image.sourceUrl, versions: [] }; assets.push(asset); }
  asset['versions'] = [...objects(asset['versions']), { id: versionId, url: safeUrl, kind: 'annotation', rasterized: true, mime: 'image/png', createdAt: audit['at'], audit: structuredClone(audit) }];
  asset['activeVersionId'] = versionId;
  asset['currentUrl'] = safeUrl;
  asset['updatedAt'] = audit['at'];
  next['imageAssets'] = assets;
  next.updatedAt = String(audit['at'] || new Date().toISOString());
  return next;
}

function objects(value: unknown): JsonRecord[] { return Array.isArray(value) ? value.filter((v): v is JsonRecord => Boolean(v && typeof v === 'object' && !Array.isArray(v))) : []; }
function urls(value: unknown): string[] { return Array.isArray(value) ? value.map(safeStudyImageUrl).filter(Boolean) : []; }
function imageHash(value: string): string { let hash = 0; for (const char of value) hash = ((hash << 5) - hash + char.charCodeAt(0)) | 0; return Math.abs(hash).toString(36); }
