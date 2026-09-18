import type { ClinicalRecord, ClinicalState } from '../../core/patients/patient-workspace.models';
import { safeStudyImageUrl } from '../../core/clinical/study-image-presentation';
import { clinicalDiagnosisEntry, legacyClinicalDiagnosisRecord } from '../../core/clinical/clinical-diagnosis-projection';
import {
  AjccAxis, AjccCategory, AjccSiteDetail, AjccSiteGroup, AjccSiteSummary, ClinicalAuditStamp,
  DiagnosisCatalogItem, DiagnosisClassification, DiagnosisEditorCatalog, DiagnosisEntryDraft,
  DiagnosisEquivalence, DiagnosisRecord, DiagnosisSystem, DiagnosisValidation,
  DIAGNOSIS_SYSTEM_LABELS, EvolutionEntryDraft, EvolutionImageAttachment
} from './clinical-entry.models';

type JsonRecord = Record<string, unknown>;
const SYSTEMS: readonly DiagnosisSystem[] = Object.freeze(['ajcc', 'snomed', 'cie10']);
const CORE_AXES = new Set(['T', 'N', 'M', 'Classification', 'DescY', 'DescR', 'DescM']);

export function newClinicalEntryId(prefix: 'evolution' | 'diagnosis'): string {
  const token = globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  return `${prefix}-${token}`;
}

export function localIsoDate(value = new Date()): string {
  const shifted = new Date(value.getTime() - value.getTimezoneOffset() * 60_000);
  return shifted.toISOString().slice(0, 10);
}

export function normalizeClinicalText(value: unknown, maximum = 50_000): string {
  return text(value).replaceAll('\r\n', '\n').replaceAll('\r', '\n').trim().slice(0, maximum);
}

/** Carcinoma, cáncer y tumor/neoplasia maligna comparten el mismo espacio de búsqueda. */
export function normalizeDiagnosisSearch(value: unknown): string {
  return text(value).normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('es-AR')
    .replace(/\b(?:tumor|neoplasia)\s+malign[oa]s?\b/g, ' carcinoma ')
    .replace(/\bcancer\b/g, ' carcinoma ').replace(/[^a-z0-9]+/g, ' ').replace(/\s+/g, ' ').trim();
}

export function diagnosisMatchesQuery(
  item: Pick<DiagnosisCatalogItem, 'code' | 'display' | 'group' | 'sourceDisplay'>,
  query: string
): boolean {
  const terms = normalizeDiagnosisSearch(query).split(' ').filter(Boolean);
  const haystack = normalizeDiagnosisSearch([item.code, item.display, item.group, item.sourceDisplay].join(' '));
  return !terms.length || terms.every((term) => haystack.includes(term));
}

export function formatAjccDisplay(group: unknown, name: unknown): string {
  const section = text(group);
  const site = text(name).replace(/^\s*\[[^\]]*]\s*/, '').trim();
  if (!section) return site;
  if (!site) return section;
  const normalizedSection = normalizeDiagnosisSearch(section);
  const normalizedSite = normalizeDiagnosisSearch(site);
  if (normalizedSite === normalizedSection || normalizedSite.startsWith(`${normalizedSection} `)) return site;
  return `${section} - ${site}`;
}

export function normalizeAjccCatalog(payload: unknown): readonly AjccSiteSummary[] {
  const root = record(payload);
  const edition = text(root['edition']) || 'AJCC 8';
  const source = text(root['source']) || 'Catálogo AJCC 8 local';
  return array(root['sites']).map((value) => {
    const item = record(value);
    const id = text(item['id']);
    const name = text(item['name']) || id;
    const group = text(item['group']) || 'Otros';
    return { id, name, group, display: formatAjccDisplay(group, name), edition, source };
  }).filter((item) => item.id && item.name)
    .sort((left, right) => compare(left.group, right.group) || compare(left.name, right.name));
}

export function groupAjccSites(sites: readonly AjccSiteSummary[]): readonly AjccSiteGroup[] {
  const grouped = new Map<string, AjccSiteSummary[]>();
  for (const site of sites) grouped.set(site.group, [...(grouped.get(site.group) || []), site]);
  return [...grouped.entries()]
    .map(([name, items]) => ({ name, sites: items.sort((left, right) => compare(left.name, right.name)) }))
    .sort((left, right) => compare(left.name, right.name));
}

export function normalizeAjccDetail(payload: unknown): AjccSiteDetail {
  const root = record(payload);
  const axes: Record<string, AjccAxis> = {};
  for (const [key, value] of Object.entries(record(root['axes']))) {
    const axis = normalizeAxis(value, key);
    if (axis.categories.length) axes[key] = axis;
  }
  const id = text(root['id']);
  if (!id || !axes['T'] || !axes['N'] || !axes['M']) {
    throw new Error('El sitio AJCC recibido no contiene T, N y M utilizables.');
  }
  return { id, name: text(root['name']) || id, edition: text(root['edition']) || 'AJCC 8',
    source: text(root['source']) || 'Catálogo AJCC 8 local', guideVersion: text(root['guideVersion']), axes };
}

export function normalizeAjccStage(payload: unknown): { stage: string; missing: readonly string[]; sourceRow: number | null } {
  const root = record(payload);
  const sourceRow = integer(root['sourceRow']);
  return { stage: text(root['stage']).slice(0, 120), missing: array(root['missing']).map(text).filter(Boolean),
    sourceRow: sourceRow !== null && sourceRow > 0 ? sourceRow : null };
}

export function normalizeCatalogResults(payload: unknown, system: DiagnosisSystem): readonly DiagnosisCatalogItem[] {
  const root = record(payload);
  return uniqueCatalogItems(array(root['items']).map((value) => normalizeCatalogItem(value, system, root)).filter(nonNull));
}

export function normalizeDiagnosisEditorCatalog(
  ajccPayload: unknown, equivalencePayload: unknown, settingPayload: unknown
): DiagnosisEditorCatalog {
  const sites = normalizeAjccCatalog(ajccPayload);
  const equivalences = normalizeEquivalences(equivalencePayload);
  const settings = array(record(settingPayload)['items']).map(record);
  const selected = settings.find((item) => text(item['key']) === 'diagnosis-display') || settings[0] || {};
  const visible = array(record(selected['definition'])['visibleSystems']).map(text);
  const configured = visible.some((system) => SYSTEMS.includes(system as DiagnosisSystem));
  const requiredSystems = configured
    ? SYSTEMS.filter((system) => system === 'ajcc' || visible.includes(system))
    : SYSTEMS;
  return { sites, groups: groupAjccSites(sites), equivalences,
    requiredSystems: requiredSystems.length ? requiredSystems : SYSTEMS };
}

export function mappedCatalogItems(
  equivalences: readonly DiagnosisEquivalence[], siteId: string, system: 'snomed' | 'cie10'
): readonly DiagnosisCatalogItem[] {
  const expected = diagnosisKey(siteId);
  return uniqueCatalogItems(equivalences.filter((item) => item.active && diagnosisKey(item.ajcc.code) === expected)
    .map((item) => ({ ...item[system],
      group: [item.relation && `Relación ${item.relation}`, item.confidence && `confianza ${item.confidence}`]
        .filter(Boolean).join(' · '),
      mapAdvice: [item[system].mapAdvice, item.notes].filter(Boolean).join(' · ') })));
}

export function ajccAxisCategories(detail: AjccSiteDetail | null, axisKey: string, prefix: string): readonly AjccCategory[] {
  const categories = detail?.axes[axisKey]?.categories || [];
  if (axisKey !== 'T' || !categories.some((item) => /^[cp]T/.test(item.code))) return categories;
  const classification = prefix.includes('p') ? 'p' : 'c';
  return categories.filter((item) => item.code.startsWith(`${classification}T`));
}

export function ajccExtraAxisKeys(detail: AjccSiteDetail | null): readonly string[] {
  return Object.keys(detail?.axes || {}).filter((key) => !CORE_AXES.has(key));
}

export function validateDiagnosisDraft(
  draft: DiagnosisEntryDraft | null, requiredSystems: readonly DiagnosisSystem[],
  equivalences: readonly DiagnosisEquivalence[]
): DiagnosisValidation {
  const issues: Array<{ field: string; message: string }> = [];
  if (!draft) return invalid([{ field: 'ajcc', message: 'Seleccione el sitio AJCC.' }]);
  if (!draft.site.id || draft.detail.id !== draft.site.id) issues.push({ field: 'ajcc', message: 'Cargue los criterios del sitio AJCC.' });
  if (!/^\d{4}-\d{2}-\d{2}$/.test(draft.date)) issues.push({ field: 'date', message: 'Complete la fecha de estadificación.' });
  for (const axisKey of ['T', 'N', 'M', ...ajccExtraAxisKeys(draft.detail)]) {
    const value = text(draft.values[axisKey]);
    const valid = ajccAxisCategories(draft.detail, axisKey, draft.prefix).some((item) => item.code === value);
    if (!valid) issues.push({ field: `axis:${axisKey}`, message: `Complete ${draft.detail.axes[axisKey]?.label || axisKey}.` });
  }
  if (!text(draft.stage)) issues.push({ field: 'stage', message: 'Complete el estadio AJCC calculado o manual.' });
  for (const system of requiredSystems) {
    const classification = draft.classifications[system];
    if (!classification.code || !classification.display) issues.push({ field: system, message: `Seleccione ${DIAGNOSIS_SYSTEM_LABELS[system]}.` });
  }
  const mappings = equivalences.filter((item) => item.active && diagnosisKey(item.ajcc.code) === diagnosisKey(draft.site.id));
  if (mappings.length && requiredSystems.includes('snomed') && requiredSystems.includes('cie10')) {
    const coherent = mappings.some((item) => diagnosisKey(item.snomed.code) === diagnosisKey(draft.classifications.snomed.code)
      && diagnosisKey(item.cie10.code) === diagnosisKey(draft.classifications.cie10.code));
    if (!coherent) issues.push({ field: 'snomed', message: 'Seleccione una equivalencia coherente AJCC / SNOMED CT / CIE-10.' });
  }
  return issues.length ? invalid(issues) : { valid: true, issues: [], message: 'Diagnóstico completo. Puede guardarlo.' };
}

/** Keeps the selected image version as a snapshot, including attachments saved by the legacy editor. */
export function normalizeEvolutionAttachments(value: unknown): EvolutionImageAttachment[] {
  return array(value).flatMap((candidate, index) => {
    const item = record(candidate);
    const url = safeStudyImageUrl(item['url']);
    if (!url) return [];
    const templateSource = record(item['templateSource']);
    const audit = record(item['audit']);
    return [{
      id: text(item['id']) || `evolution-image-${index + 1}`,
      url,
      thumbnailUrl: safeStudyImageUrl(item['thumbnailUrl']) || url,
      title: normalizeClinicalText(item['title'], 255) || 'Imagen de estudio',
      studyId: text(item['studyId']),
      imageId: text(item['imageId']),
      versionId: text(item['versionId']) || 'original',
      annotated: typeof item['annotated'] === 'boolean' ? item['annotated'] : Boolean(text(item['versionId']) && text(item['versionId']) !== 'original'),
      studyDate: text(item['studyDate']),
      studyType: normalizeClinicalText(item['studyType'], 255) || 'Imagen',
      caption: normalizeClinicalText(item['caption']),
      templateSource: Object.keys(templateSource).length ? structuredClone(templateSource) : null,
      audit: Object.keys(audit).length ? structuredClone(audit) : null,
      createdAt: text(item['createdAt']) || text(audit['at'])
    }];
  });
}

export function buildEvolutionRecord(draft: EvolutionEntryDraft, audit: ClinicalAuditStamp): ClinicalRecord {
  const body = normalizeClinicalText(draft.text);
  if (!body) throw new Error('La evolución no puede estar vacía.');
  const specialty = normalizeClinicalText(draft.specialty, 255) || 'Oncología';
  const attachments = normalizeEvolutionAttachments(draft.attachments)
    .map((attachment) => ({ ...attachment, caption: attachment.caption || body }));
  return { id: draft.id, date: draft.date, datePrecision: 'day',
    author: normalizeClinicalText(draft.author, 255) || audit.lastName,
    reason: specialty, specialty, text: body, type: 'evolution', category: 'evolution',
    attachments,
    linkedStudyIds: [...new Set(attachments.map((attachment) => attachment.studyId).filter(Boolean))],
    highlighted: false, immutable: false, audit, createdAt: audit.at, updatedAt: audit.at };
}

export function applyEvolutionRecord(state: ClinicalState, clinicalRecord: ClinicalRecord): ClinicalState {
  const next = structuredClone(state);
  const records = [...(next.evolutions || [])];
  const existing = records.find((item) => text(item.id) === text(clinicalRecord.id));
  if (existing && stableJson(existing) !== stableJson(clinicalRecord)) throw new Error('Ese identificador de evolución ya pertenece a otro contenido.');
  if (!existing) records.unshift(structuredClone(clinicalRecord));
  next.evolutions = records;
  next.meta = { ...(next.meta || {}), updatedAt: clinicalRecord.updatedAt || clinicalRecord.createdAt || new Date().toISOString() };
  return next;
}

export function buildDiagnosisRecord(draft: DiagnosisEntryDraft, audit: ClinicalAuditStamp): DiagnosisRecord {
  const classifications = Object.fromEntries(
    SYSTEMS.map((system) => [system, normalizeClassification(draft.classifications[system], system)])
  ) as Record<DiagnosisSystem, DiagnosisClassification>;
  const values = Object.fromEntries(Object.entries(draft.values).map(([key, value]) => [key, text(value).slice(0, 160)]));
  const t = text(values['T']).slice(0, 40); const n = text(values['N']).slice(0, 40); const m = text(values['M']).slice(0, 40);
  const tnm = { t, n, m, stage: text(draft.stage).slice(0, 120), substage: '', siteId: draft.site.id,
    siteDisplay: draft.site.display, prefix: draft.prefix, date: draft.date,
    edition: draft.detail.edition || draft.site.edition, source: draft.detail.source || draft.site.source,
    guideVersion: draft.detail.guideVersion, sourceRow: draft.stageEdited ? null : draft.sourceRow,
    calculatedAt: !draft.stageEdited && draft.sourceRow ? audit.at : '', values: { ...values, T: t, N: n, M: m } };
  return { id: draft.id, date: draft.date, datePrecision: 'day',
    diagnosis: classifications.snomed.display || classifications.ajcc.display,
    topography: draft.site.display, stage: text(draft.stage).slice(0, 120), diagnosticClassifications: classifications,
    tnm, legacyProjection: false, audit, createdAt: audit.at };
}

export function applyDiagnosisRecord(state: ClinicalState, diagnosis: DiagnosisRecord): ClinicalState {
  const next = structuredClone(state);
  const oncology = recordObject(next.oncology);
  const source = Array.isArray(oncology['diagnosisRecords']) ? oncology['diagnosisRecords'] as unknown[] : [];
  const records = source.map(recordObject);
  if (!source.length) {
    const aliases = Array.isArray(oncology['diagnoses']) ? oncology['diagnoses'] as unknown[] : [];
    for (const value of aliases) {
      const item = recordObject(value);
      const id = text(item['id'] || item['diagnosisEntryId']);
      if (!id || !records.some((record) => text(record['id'] || record['diagnosisEntryId']) === id)) records.push(item);
    }
    const legacy = legacyClinicalDiagnosisRecord(next);
    if (legacy) {
      const snapshot = clinicalDiagnosisEntry(legacy);
      const represented = records.some((item) => {
        const existing = clinicalDiagnosisEntry(item);
        return existing.key === snapshot.key || existing.date === snapshot.date && existing.evolutionText === snapshot.evolutionText;
      });
      if (!represented) records.push(legacy as JsonRecord);
    }
  }
  const existing = records.find((item) => text(item['id']) === diagnosis.id);
  if (existing && diagnosisFingerprint(existing) !== diagnosisFingerprint(diagnosis)) throw new Error('Ese identificador de diagnóstico ya pertenece a otro contenido.');
  if (!existing) records.push(structuredClone(diagnosis) as unknown as JsonRecord);
  oncology['diagnosisRecords'] = records;
  oncology['diagnosticClassifications'] = structuredClone(diagnosis.diagnosticClassifications);
  oncology['tnm'] = structuredClone(diagnosis.tnm);
  oncology['diagnosis'] = diagnosis.diagnosis;
  oncology['diagnosisDate'] = diagnosis.date;
  oncology['diagnosisDatePrecision'] = 'day';
  oncology['topography'] = diagnosis.topography;
  oncology['stage'] = diagnosis.stage;
  next.oncology = oncology;
  const meta = recordObject(next.meta);
  const sectionAudit = recordObject(meta['sectionAudit']);
  sectionAudit['diagnosticClassifications'] = structuredClone(diagnosis.audit);
  meta['sectionAudit'] = sectionAudit; meta['updatedAt'] = diagnosis.createdAt; next.meta = meta;
  return next;
}

export function diagnosisPlainText(diagnosis: DiagnosisRecord): string {
  return clinicalDiagnosisEntry(diagnosis).evolutionText;
}

export function normalizeClassification(value: unknown, system: DiagnosisSystem): DiagnosisClassification {
  const source = record(value);
  return { system: DIAGNOSIS_SYSTEM_LABELS[system], freeText: text(source['freeText'] ?? source['queryText'] ?? source['display']).slice(0, 2_000),
    code: text(source['code']).slice(0, 80), display: text(source['display']).slice(0, 500),
    version: text(source['version']).slice(0, 160), source: text(source['source']).slice(0, 240),
    sourceConceptId: text(source['sourceConceptId']).slice(0, 80), sourceDisplay: text(source['sourceDisplay']).slice(0, 500),
    mapAdvice: text(source['mapAdvice']).slice(0, 500) };
}
export function blankClassification(system: DiagnosisSystem): DiagnosisClassification { return normalizeClassification({}, system); }

export function diagnosisFingerprint(value: unknown): string {
  const source = record(value);
  return stableJson({ date: text(source['date']), diagnosis: text(source['diagnosis']), topography: text(source['topography']),
    stage: text(source['stage']), diagnosticClassifications: source['diagnosticClassifications'], tnm: source['tnm'] });
}

function normalizeEquivalences(payload: unknown): readonly DiagnosisEquivalence[] {
  return array(record(payload)['items']).map((value) => {
    const item = record(value); const definition = record(item['definition']);
    return { id: text(item['id']), active: item['active'] !== false, ajcc: normalizeClassification(definition['ajcc'], 'ajcc'),
      snomed: normalizeClassification(definition['snomed'], 'snomed'), cie10: normalizeClassification(definition['cie10'], 'cie10'),
      relation: text(definition['relation']), confidence: text(definition['confidence']), notes: text(definition['notes']) };
  }).filter((item) => item.active && item.ajcc.code);
}
function normalizeCatalogItem(value: unknown, system: DiagnosisSystem, root: JsonRecord): DiagnosisCatalogItem | null {
  const item = record(value); const code = text(item['code'] ?? item['id']); const display = text(item['display'] ?? item['name'] ?? item['term']);
  if (!code || !display) return null;
  return { ...normalizeClassification({ ...item, code, display, version: item['version'] ?? root['version'] ?? root['edition'],
      source: item['source'] ?? root['source'] }, system), group: text(item['group']) };
}
function normalizeAxis(value: unknown, key: string): AjccAxis {
  const item = record(value); const categories = array(item['categories']).map(normalizeCategory).filter((entry) => entry.code);
  return { label: text(item['label']) || key, categories };
}
function normalizeCategory(value: unknown): AjccCategory {
  const item = record(value); return { code: text(item['code']), description: text(item['description']), notes: array(item['notes']).map(text).filter(Boolean) };
}
function uniqueCatalogItems(items: readonly DiagnosisCatalogItem[]): readonly DiagnosisCatalogItem[] {
  return [...new Map(items.map((item) => [`${diagnosisKey(item.code)}\0${normalizeDiagnosisSearch(item.display)}`, item])).values()]
    .sort((left, right) => compare(left.group, right.group) || compare(left.display, right.display));
}
function diagnosisKey(value: unknown): string { return text(value).toLocaleLowerCase('en-US'); }
function invalid(issues: Array<{ field: string; message: string }>): DiagnosisValidation {
  return { valid: false, issues, message: issues.map((item) => item.message).slice(0, 5).join(' · ') };
}
function nonNull<T>(value: T | null): value is T { return value !== null; }
function stableJson(value: unknown): string { return JSON.stringify(stableValue(value)); }
function stableValue(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(stableValue); if (!value || typeof value !== 'object') return value;
  return Object.fromEntries(Object.keys(value as JsonRecord).sort().map((key) => [key, stableValue((value as JsonRecord)[key])]));
}
function compare(left: string, right: string): number { return left.localeCompare(right, 'es-AR', { sensitivity: 'base', numeric: true }); }
function array(value: unknown): readonly unknown[] { return Array.isArray(value) ? value : []; }
function record(value: unknown): JsonRecord { return value !== null && typeof value === 'object' && !Array.isArray(value) ? value as JsonRecord : {}; }
function recordObject(value: unknown): JsonRecord { return structuredClone(record(value)); }
function text(value: unknown): string { return typeof value === 'string' || typeof value === 'number' ? String(value).trim() : ''; }
function integer(value: unknown): number | null { const number = Number(value); return Number.isInteger(number) ? number : null; }
