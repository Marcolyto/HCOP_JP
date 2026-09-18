import { clinicalDiagnosisEntries, clinicalDiagnosisEntry, legacyClinicalDiagnosisRecord } from './clinical-diagnosis-projection';
import type { ClinicalRecord, ClinicalState } from '../patients/patient-workspace.models';

let assertions = 0;
function equal(actual: unknown, expected: unknown, message: string): void {
  assertions += 1;
  if (JSON.stringify(actual) !== JSON.stringify(expected)) throw new Error(`${message}: esperado ${JSON.stringify(expected)}, recibido ${JSON.stringify(actual)}`);
}
function ok(value: unknown, message: string): void { assertions += 1; if (!value) throw new Error(message); }

const classifications = {
  ajcc: { code: 'penis', display: 'Genitourinario - Pene', version: 'AJCC 8', source: 'Guía' },
  snomed: { code: '39937001', display: 'Carcinoma de pene', sourceConceptId: '39937001' },
  cie10: { code: 'C60', display: 'Tumor maligno del pene', mapAdvice: 'Equivalencia confirmada' }
};
const tnm = { t: 'pT1', n: 'N0', m: 'M0', prefix: 'yp', stage: 'I', siteId: 'penis',
  siteDisplay: 'Genitourinario - Pene', date: '2026-07-01', edition: 'AJCC 8', sourceRow: 3,
  values: { T: 'pT1', N: 'N0', M: 'M0', Grade: 'G2', Marker: 'Positivo' } };
const diagnosis: ClinicalRecord = { id: 'diagnosis-1', date: '2026-07-01', diagnosis: 'Carcinoma de pene',
  topography: 'Genitourinario - Pene', histology: 'Carcinoma escamoso', stage: 'I',
  diagnosticClassifications: classifications, tnm, legacyProjection: false };
const initial = JSON.stringify(diagnosis);
const entry = clinicalDiagnosisEntry(diagnosis);
equal(entry.key, 'id:diagnosis-1', 'clave de diagnóstico estable');
equal(entry.classifications.map((item) => item.system), ['ajcc', 'snomed', 'cie10'], 'presenta tres clasificadores en orden');
equal(entry.classifications.map((item) => item.code), ['penis', '39937001', 'C60'], 'conserva códigos explícitos');
equal(entry.tnm, 'ypT1 N0 M0', 'conserva prefijo postratamiento frente a categoría pT');
equal(entry.stage, 'I', 'conserva estadio');
equal(entry.histology, 'Carcinoma escamoso', 'conserva histología');
equal(entry.text, '', 'no duplica resumen en cuerpo narrativo');
equal(clinicalDiagnosisEntry({ ...diagnosis, text: 'Nota clínica adicional' }).text, 'Nota clínica adicional', 'cuerpo conserva únicamente narrativa guardada');
for (const snippet of ['SNOMED CT 39937001', 'CIE-10 C60', 'AJCC penis', 'TNM ypT1 N0 M0', 'Estadio I', 'Histología: Carcinoma escamoso', 'Grade: G2', 'Marker: Positivo']) {
  ok(entry.evolutionText.includes(snippet), `texto de evolución incluye ${snippet}`);
}
equal(JSON.stringify(diagnosis), initial, 'presentación no muta registro');

const independent = { id: 'imported', date: '2025-01-01', diagnosis: 'Diagnóstico previo independiente' };
const state: ClinicalState = { oncology: { diagnosisRecords: [diagnosis], diagnosis: diagnosis.diagnosis,
  diagnosticClassifications: classifications, tnm }, diagnoses: [independent, { ...diagnosis, diagnosis: 'Copia que no prevalece' }] };
const entries = clinicalDiagnosisEntries(state);
equal(entries.map((item) => item.record.id), ['diagnosis-1', 'imported'], 'prefiere canónico por id y conserva importados independientes');
equal(entries[0]?.title, 'Carcinoma de pene', 'duplicado secundario no reemplaza canónico');
equal(entries[0]?.key, clinicalDiagnosisEntry(entries[0]!.record).key, 'clave individual coincide con listado');
equal(clinicalDiagnosisEntries(JSON.parse(JSON.stringify(state))).map((item) => item.evolutionText), entries.map((item) => item.evolutionText), 'recarga mantiene texto/códigos/TNM');

const legacyState: ClinicalState = { oncology: { diagnosis: diagnosis.diagnosis, diagnosisDate: diagnosis.date,
  diagnosisDatePrecision: 'day', topography: diagnosis.topography, histology: diagnosis['histology'], stage: 'I',
  diagnosticClassifications: classifications, tnm }, meta: { sectionAudit: { diagnosticClassifications: {
    action: 'cargado', lastName: 'Autor previo', license: 'MP 2', at: '2026-07-01T12:00:00.000Z' } } } };
const before = JSON.stringify(legacyState);
const snapshot = legacyClinicalDiagnosisRecord(legacyState)!;
equal(snapshot['diagnosticClassifications'], classifications, 'snapshot no reduce metadatos de codificación');
equal(snapshot['tnm'], tnm, 'snapshot conserva TNM completo y ejes adicionales');
equal(snapshot.audit, (legacyState.meta?.['sectionAudit'] as Record<string, unknown>)['diagnosticClassifications'], 'snapshot conserva auditoría original');
equal(snapshot.createdAt, '2026-07-01T12:00:00.000Z', 'fecha original de auditoría');
equal(snapshot['datePrecision'], 'day', 'precisión original de fecha');
equal(snapshot['legacyProjection'], true, 'identifica snapshot legado');
ok(String(snapshot.id).startsWith('diagnosis-legacy-'), 'id estable legado');
equal(legacyClinicalDiagnosisRecord(JSON.parse(before))?.id, snapshot.id, 'id estable al recargar JSON');
equal(JSON.stringify(legacyState), before, 'snapshot no modifica clínica original');
const legacyEntries = clinicalDiagnosisEntries(legacyState);
equal(legacyEntries.length, 1, 'fallback estructurado legacy');
equal(legacyEntries[0]?.key, clinicalDiagnosisEntry(legacyEntries[0]!.record).key, 'clave snapshot individual coincide con listado');
equal(clinicalDiagnosisEntries({ oncology: { diagnosisRecords: [snapshot, diagnosis] } }).length, 1, 'no duplica snapshot legado equivalente');
equal(clinicalDiagnosisEntries({ oncology: { diagnosisRecords: [snapshot] }, diagnoses: [snapshot] }).length, 1, 'no duplica snapshot por id');
equal(clinicalDiagnosisEntries({ oncology: { diagnosisRecords: [diagnosis] }, diagnoses: [{ ...diagnosis, id: 'independent-same-content' }] }).length, 2, 'conserva dos registros explícitos independientes con contenido igual');

const onlyText = clinicalDiagnosisEntries({ oncology: { diagnosis: 'Diagnóstico importado sin código' } });
equal(onlyText.length, 1, 'fallback textual legacy');
equal(onlyText[0]?.classifications, [], 'no inventa códigos ni muestra tarjetas vacías');
equal(onlyText[0]?.tnm, '', 'no inventa TNM');
equal(onlyText[0]?.date, '', 'no inventa fecha');
equal(onlyText[0]?.record.audit, undefined, 'no inventa autor ni auditoría');
equal(clinicalDiagnosisEntry({ diagnosis: 'Texto', tnm: { t: 'T2', n: 'N1', m: 'M0' } }).tnm, 'T2 N1 M0', 'sin prefijo no infiere c/p');
equal(clinicalDiagnosisEntry({ tnm: { values: { T: 'T1', N: 'N0', M: 'M0' }, prefix: 'r' } }).tnm, 'rT1 N0 M0', 'admite ejes conservados en values');
equal(clinicalDiagnosisEntries({ oncology: { diagnosticClassifications: { cie10: { code: 'C60' } } } }).length, 1, 'código solo sigue visible');
equal(clinicalDiagnosisEntries({ oncology: { tnm: { t: 'T1' } } }).length, 1, 'TNM solo sigue visible');
equal(clinicalDiagnosisEntries({ oncology: { diagnosisRecords: [diagnosis] }, diagnoses: [{ id: diagnosis.id, deleted: true }] }).length, 0, 'tombstone no revive duplicado');
equal(clinicalDiagnosisEntries({ oncology: { diagnosisRecords: [{ ...diagnosis, archived: true }], diagnosis: 'proyección' } }).length, 0, 'no revive proyección de registro archivado');
equal(clinicalDiagnosisEntries({ oncology: { diagnoses: [diagnosis] } }).length, 1, 'admite arreglo legado oncology.diagnoses');
equal(clinicalDiagnosisEntries({ oncology: { diagnosisRecords: [diagnosis], diagnoses: [independent] } }).length, 2, 'alta nueva no oculta arreglo legado independiente');
for (const empty of [undefined, null, {}, { oncology: { intent: 'En estudio', status: 'En estudio' } }, { oncology: { diagnosticClassifications: {}, tnm: {} } }]) {
  equal(clinicalDiagnosisEntries(empty).length, 0, 'clínica vacía no produce diagnóstico');
}
console.log(`clinical-diagnosis-projection: ${assertions} aserciones`);
