import type { ClinicalRecord, ClinicalState } from '../patients/patient-workspace.models';

export interface ClinicalDiagnosisClassification {
  readonly system: 'ajcc' | 'snomed' | 'cie10';
  readonly label: string;
  readonly code: string;
  readonly display: string;
}

export interface ClinicalDiagnosisEntry {
  readonly key: string;
  readonly record: ClinicalRecord;
  readonly date: string;
  readonly title: string;
  readonly topography: string;
  readonly histology: string;
  readonly stage: string;
  readonly tnm: string;
  readonly classifications: readonly ClinicalDiagnosisClassification[];
  readonly text: string;
  readonly evolutionText: string;
}

const SYSTEMS = [
  ['ajcc', 'AJCC'], ['snomed', 'SNOMED CT'], ['cie10', 'CIE-10']
] as const;

/** Presentation only: stored terminology, TNM and metadata remain untouched. */
export function clinicalDiagnosisEntry(record: ClinicalRecord): ClinicalDiagnosisEntry {
  const value = object(record);
  const classification = object(value['diagnosticClassifications']);
  const tnm = object(value['tnm']);
  const classifications: ClinicalDiagnosisClassification[] = SYSTEMS.flatMap(([system, label]) => {
    const item = object(classification[system]);
    const code = text(item['code']);
    const display = text(item['display']) || text(item['freeText']);
    return code || display ? [{ system, label, code, display }] : [];
  });
  const title = first(value, ['diagnosis', 'diagnostico', 'title', 'nombre', 'name'])
    || classifications.find((item) => item.system === 'snomed')?.display
    || classifications.find((item) => item.system === 'ajcc')?.display || 'Diagnóstico oncológico';
  const date = first(value, ['date', 'diagnosisDate', 'fechaDiagnostico']) || text(tnm['date']);
  const topography = text(value['topography']) || text(tnm['siteDisplay']);
  const histology = text(value['histology']);
  const stage = text(value['stage']) || text(tnm['stage']) || text(tnm['stageGroup']);
  const axes = object(tnm['values']);
  let t = text(tnm['t']) || text(axes['T']);
  const prefix = text(tnm['prefix']);
  // Explicit yc/yp/r takes precedence over a catalog category such as cT1/pT1.
  // Without an explicit prefix, do not infer a clinical/pathological context.
  if (/^(?:c|p|yc|yp|r)$/.test(prefix) && /^(?:(?:yc|yp|c|p|r))?T\S*$/i.test(t)) {
    t = prefix + t.replace(/^(?:yc|yp|c|p|r)?(?=T)/i, '');
  }
  const tnmText = [t, text(tnm['n']) || text(axes['N']), text(tnm['m']) || text(axes['M'])].filter(Boolean).join(' ');
  const body = first(value, ['text', 'summary', 'description', 'descripcion']);
  const extraAxes = Object.entries(axes).filter(([key, value]) => !['T', 'N', 'M'].includes(key) && text(value))
    .sort(([left], [right]) => left.localeCompare(right, 'en'))
    .map(([key, value]) => `${key}: ${text(value)}`).join('; ');
  const clinicalText = [
    title !== 'Diagnóstico oncológico' && `Diagnóstico oncológico: ${title}.`,
    topography && `Topografía: ${topography}.`, histology && `Histología: ${histology}.`,
    ...classifications.filter((item) => item.code || item.display)
      .map((item) => `${item.label}${item.code ? ` ${item.code}` : ''}${item.display ? `: ${item.display}` : ''}.`),
    tnmText && `TNM ${tnmText}.`, stage && `Estadio ${stage}.`, extraAxes && `Factores: ${extraAxes}.`,
    body && body !== title && body
  ].filter(Boolean).join(' ');
  const entry = { record, date, title, topography, histology, stage, tnm: tnmText, classifications,
    text: body !== title ? body : '', evolutionText: clinicalText || body };
  const id = first(value, ['id', 'diagnosisEntryId']);
  return { ...entry, key: id ? `id:${id}` : `diagnosis:${hash(signature(entry))}` };
}

/** Canonical oncology records take precedence; independent imported diagnoses survive. */
export function clinicalDiagnosisEntries(state: ClinicalState | null | undefined): ClinicalDiagnosisEntry[] {
  if (!state) return [];
  const oncology = object(state.oncology);
  const canonical = records(oncology['diagnosisRecords']);
  const source = canonical.length ? canonical : records(oncology['diagnoses']);
  const candidates = [...source, ...(canonical.length ? records(oncology['diagnoses']) : []), ...records(state.diagnoses)];
  if (!source.length) {
    const legacy = legacyClinicalDiagnosisRecord(state);
    if (legacy) candidates.push(legacy);
  }
  const tombstones = new Set(candidates.filter((value) => value.deleted || value['archived'])
    .map((value) => first(object(value), ['id', 'diagnosisEntryId'])).filter(Boolean));
  const visible = candidates.filter((record) => !record.deleted && !record['archived']
    && !tombstones.has(first(object(record), ['id', 'diagnosisEntryId'])))
    .map(clinicalDiagnosisEntry).filter(meaningful);
  const originals = new Set(visible.filter((entry) => !isLegacy(entry.record)).map(signature));
  const unique = new Map<string, ClinicalDiagnosisEntry>();
  const legacySignatures = new Set<string>();
  for (const entry of visible) {
    const fingerprint = signature(entry);
    if (isLegacy(entry.record) && (originals.has(fingerprint) || legacySignatures.has(fingerprint))) continue;
    if (unique.has(entry.key)) continue;
    unique.set(entry.key, entry);
    if (isLegacy(entry.record)) legacySignatures.add(fingerprint);
  }
  return [...unique.values()];
}

/** Materializes only the pre-existing oncology projection before its first append. */
export function legacyClinicalDiagnosisRecord(state: ClinicalState): ClinicalRecord | null {
  const oncology = object(state.oncology);
  const meta = object(state.meta);
  const audits = object(meta['sectionAudit']);
  const audit = object(oncology['diagnosisAudit'] || audits['diagnosticClassifications']);
  const record: ClinicalRecord = {
    date: text(oncology['diagnosisDate']) || text(object(oncology['tnm'])['date']),
    diagnosis: text(oncology['diagnosis']), topography: text(oncology['topography']),
    histology: text(oncology['histology']), stage: text(oncology['stage']),
    diagnosticClassifications: structuredClone(object(oncology['diagnosticClassifications'])),
    tnm: structuredClone(object(oncology['tnm'])), legacyProjection: true
  };
  if (text(oncology['diagnosisDatePrecision'])) record['datePrecision'] = oncology['diagnosisDatePrecision'];
  if (Object.keys(audit).length) record['audit'] = structuredClone(audit);
  const createdAt = text(oncology['diagnosisCreatedAt']) || text(audit['at']);
  if (createdAt) record.createdAt = createdAt;
  const entry = clinicalDiagnosisEntry(record);
  if (!meaningful(entry)) return null;
  record.id = `diagnosis-legacy-${hash(signature(entry))}`;
  return record;
}

function meaningful(entry: ClinicalDiagnosisEntry): boolean {
  return entry.title !== 'Diagnóstico oncológico' || Boolean(entry.evolutionText || entry.topography || entry.histology
    || entry.stage || entry.tnm || entry.classifications.some((item) => item.code || item.display));
}
function isLegacy(record: ClinicalRecord): boolean {
  return Boolean(record['legacyProjection']) || text(record.id).startsWith('diagnosis-legacy-');
}
function signature(entry: Omit<ClinicalDiagnosisEntry, 'key'>): string {
  return JSON.stringify([entry.date, entry.title, entry.topography, entry.histology, entry.stage, entry.tnm,
    entry.classifications, entry.text, entry.evolutionText]);
}
function records(value: unknown): ClinicalRecord[] {
  return Array.isArray(value) ? value.filter((item): item is ClinicalRecord => Boolean(item && typeof item === 'object' && !Array.isArray(item))) : [];
}
function object(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};
}
function text(value: unknown): string {
  return typeof value === 'string' || typeof value === 'number' ? String(value).trim() : '';
}
function first(value: Record<string, unknown>, keys: readonly string[]): string {
  return keys.map((key) => text(value[key])).find(Boolean) || '';
}
function hash(value: string): string {
  let hash = 2166136261;
  for (let index = 0; index < value.length; index++) hash = Math.imul(hash ^ value.charCodeAt(index), 16777619);
  return (hash >>> 0).toString(36);
}
