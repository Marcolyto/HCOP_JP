import {
  applyDiagnosisRecord, applyEvolutionRecord, buildDiagnosisRecord, buildEvolutionRecord,
  diagnosisMatchesQuery, diagnosisPlainText, formatAjccDisplay, mappedCatalogItems,
  normalizeAjccCatalog, normalizeAjccDetail, normalizeClassification, normalizeDiagnosisEditorCatalog, normalizeEvolutionAttachments,
  validateDiagnosisDraft
} from './clinical-entry.normalizers';
import type { ClinicalAuditStamp, DiagnosisEntryDraft } from './clinical-entry.models';

let assertions = 0;
function equal(actual: unknown, expected: unknown, label: string): void {
  assertions += 1; if (actual !== expected) throw new Error(`${label}: esperado ${String(expected)}, obtenido ${String(actual)}`);
}
function ok(value: unknown, label: string): void { assertions += 1; if (!value) throw new Error(label); }

equal(formatAjccDisplay('Genitourinario', '[corpu] Pene'), 'Genitourinario - Pene', 'ordena sección antes del sitio y limpia prefijo espurio');
const sites = normalizeAjccCatalog({ edition: 'AJCC 8', sites: [
  { id: 'penis', name: 'Pene', group: 'Genitourinario' },
  { id: 'larynx', name: 'Laringe', group: 'Cabeza y cuello' }
] });
equal(sites[0]?.id, 'larynx', 'ordena grupos AJCC');
equal(sites[1]?.display, 'Genitourinario - Pene', 'presenta grupo-sitio');

const carcinoma = normalizeClassification({ code: '1', display: 'Carcinoma de pulmón' }, 'snomed');
ok(diagnosisMatchesQuery({ ...carcinoma, group: '', sourceDisplay: '' }, 'tumor maligno pulmon'), 'equipara tumor maligno con carcinoma');

const rawEquivalence = { items: [{ id: 1, active: true, definition: {
  ajcc: { code: 'penis', display: 'Genitourinario - Pene' },
  snomed: { code: '39937001', display: 'Carcinoma de pene' },
  cie10: { code: 'C60', display: 'Tumor maligno del pene' }, relation: 'exact', confidence: 'high'
} }] };
const catalog = normalizeDiagnosisEditorCatalog({ sites: [{ id: 'penis', name: 'Pene', group: 'Genitourinario' }] }, rawEquivalence,
  { items: [{ key: 'diagnosis-display', definition: { visibleSystems: ['ajcc', 'snomed'] } }] });
equal(catalog.requiredSystems.join(','), 'ajcc,snomed', 'respeta clasificadores obligatorios configurados');
equal(normalizeDiagnosisEditorCatalog({ sites: [] }, { items: [] }, { items: [] }).requiredSystems.join(','),
  'ajcc,snomed,cie10', 'usa los tres sistemas como valor seguro si no puede leer configuracion');
equal(mappedCatalogItems(catalog.equivalences, 'penis', 'cie10')[0]?.code, 'C60', 'vincula terminología por AJCC');

const detail = normalizeAjccDetail({ id: 'penis', name: 'Pene', axes: {
  T: { label: 'Tumor', categories: [{ code: 'cT1', description: 'Limitado' }] },
  N: { label: 'Ganglios', categories: [{ code: 'N0', description: 'Sin ganglios' }] },
  M: { label: 'Metástasis', categories: [{ code: 'M0', description: 'Sin metástasis' }] }
} });
const draft: DiagnosisEntryDraft = {
  id: 'diagnosis-test', date: '2026-08-03', prefix: 'c', site: sites[1]!, detail,
  values: { T: 'cT1', N: 'N0', M: 'M0' }, stage: 'I', stageEdited: true, sourceRow: null,
  classifications: { ajcc: normalizeClassification({ code: 'penis', display: 'Genitourinario - Pene' }, 'ajcc'),
    snomed: normalizeClassification({ code: '39937001', display: 'Carcinoma de pene', freeText: 'carcinoma pene' }, 'snomed'),
    cie10: normalizeClassification({ code: 'C60', display: 'Tumor maligno del pene', freeText: 'tumor pene' }, 'cie10') }
};
equal(validateDiagnosisDraft(draft, ['ajcc', 'snomed'], catalog.equivalences).valid, true, 'permite estadio manual y sistemas configurados');
equal(validateDiagnosisDraft({ ...draft, stage: '' }, ['ajcc', 'snomed'], catalog.equivalences).issues[0]?.field, 'stage', 'exige estadio aunque el cálculo no encuentre combinación');

const audit: ClinicalAuditStamp = { action: 'cargado', lastName: 'Prueba', license: 'MP 1', at: '2026-08-03T12:00:00.000Z' };
const diagnosis = buildDiagnosisRecord(draft, audit);
let state = applyDiagnosisRecord({ oncology: { diagnosisRecords: [] }, evolutions: [], meta: {} }, diagnosis);
state = applyDiagnosisRecord(state, diagnosis);
equal(((state.oncology?.['diagnosisRecords'] as unknown[]) || []).length, 1, 'reintento diagnóstico idempotente');
equal(state.oncology?.['stage'], 'I', 'proyecta estadio a cabecera');
ok(diagnosisPlainText(diagnosis).includes('SNOMED CT 39937001'), 'conserva códigos en texto clínico');

const legacyAudit = { action: 'modificado', lastName: 'Previo', license: 'MP 2', at: '2026-07-01T08:00:00.000Z' };
const legacyClassification = { ajcc: { code: 'old-site', display: 'Sitio previo', source: 'Catálogo previo' },
  snomed: { code: 'old-snomed', display: 'Diagnóstico previo', sourceConceptId: 'original-concept' },
  cie10: { code: 'C00', display: 'Código previo', mapAdvice: 'Conservar consejo' } };
const legacyTnm = { t: 'T2', n: 'N1', m: 'M0', prefix: 'p', stage: 'II', siteId: 'old-site',
  date: '2026-07-01', edition: 'AJCC previa', values: { T: 'T2', N: 'N1', M: 'M0', G: 'G2' }, sourceRow: 9 };
const legacyState = { oncology: { diagnosis: 'Diagnóstico previo', diagnosisDate: '2026-07-01', diagnosisDatePrecision: 'day',
  topography: 'Sitio previo', histology: 'Histología previa', stage: 'II',
  diagnosticClassifications: legacyClassification, tnm: legacyTnm }, meta: { sectionAudit: { diagnosticClassifications: legacyAudit } },
  evolutions: [{ id: 'evo-previa', text: 'Evolución conservada' }] };
const legacyBefore = JSON.stringify(legacyState);
const appended = applyDiagnosisRecord(legacyState, diagnosis);
const appendedRecords = appended.oncology?.['diagnosisRecords'] as Record<string, unknown>[];
equal(appendedRecords.length, 2, 'primer alta conserva snapshot previo y agrega nuevo');
equal(appendedRecords[0]?.['diagnosis'], 'Diagnóstico previo', 'snapshot queda antes del alta nueva');
equal(JSON.stringify(appendedRecords[0]?.['diagnosticClassifications']), JSON.stringify(legacyClassification), 'snapshot conserva todos los metadatos de códigos');
equal(JSON.stringify(appendedRecords[0]?.['tnm']), JSON.stringify(legacyTnm), 'snapshot conserva TNM y ejes específicos');
equal(JSON.stringify(appendedRecords[0]?.['audit']), JSON.stringify(legacyAudit), 'snapshot respeta autoría previa');
equal(appendedRecords[0]?.['createdAt'], legacyAudit.at, 'snapshot no atribuye fecha del alta nueva');
equal(appendedRecords[0]?.['datePrecision'], 'day', 'snapshot conserva precisión de fecha');
equal(appendedRecords[0]?.['legacyProjection'], true, 'marca snapshot heredado');
equal(appendedRecords[1]?.['id'], diagnosis.id, 'nuevo mantiene id solicitado');
equal(appended.evolutions?.length, 1, 'alta diagnóstico no agrega evolución automáticamente');
equal(JSON.stringify(legacyState), legacyBefore, 'alta no modifica estado recibido');
const reloadedAppend = applyDiagnosisRecord(JSON.parse(JSON.stringify(appended)), diagnosis);
equal((reloadedAppend.oncology?.['diagnosisRecords'] as unknown[]).length, 2, 'reintento tras recarga no duplica snapshot ni nuevo');
equal((reloadedAppend.oncology?.['diagnosisRecords'] as Record<string, unknown>[])[0]?.['id'], appendedRecords[0]?.['id'], 'snapshot conserva identidad tras recarga');
const oldRecord = { id: 'already-saved', diagnosis: 'Otro diagnóstico anterior', diagnosticClassifications: legacyClassification, tnm: legacyTnm };
const withCanonical = applyDiagnosisRecord({ oncology: { ...legacyState.oncology, diagnosisRecords: [oldRecord] } }, diagnosis);
equal((withCanonical.oncology?.['diagnosisRecords'] as unknown[]).length, 2, 'registros existentes evitan crear snapshot duplicado de proyección');
equal(JSON.stringify((withCanonical.oncology?.['diagnosisRecords'] as unknown[])[0]), JSON.stringify(oldRecord), 'registros previos quedan intactos');
const aliasOne = { ...oldRecord, id: 'alias-1', date: '2026-07-01', diagnosis: 'Diagnóstico previo',
  topography: 'Sitio previo', histology: 'Histología previa', stage: 'II', audit: legacyAudit };
const aliasTwo = { ...oldRecord, id: 'alias-2', date: '2025-01-01', diagnosis: 'Segundo diagnóstico anterior' };
const withAliases = { oncology: { ...legacyState.oncology, diagnoses: [aliasOne, aliasTwo, aliasOne] }, diagnoses: [{ id: 'external-independent', diagnosis: 'Importado independiente' }] };
const aliasesBefore = JSON.stringify(withAliases);
const appendedAliases = applyDiagnosisRecord(withAliases, diagnosis);
const materialized = appendedAliases.oncology?.['diagnosisRecords'] as Record<string, unknown>[];
equal(materialized.map((item) => item['id']).join(','), `alias-1,alias-2,${diagnosis.id}`, 'materializa dos alias y nuevo sin duplicar proyección actual');
equal(JSON.stringify(materialized[0]), JSON.stringify(aliasOne), 'alias original mantiene codificación, TNM y auditoría');
equal(JSON.stringify(appendedAliases.oncology?.['diagnoses']), JSON.stringify(withAliases.oncology.diagnoses), 'conserva arreglo alias original');
equal(appendedAliases.diagnoses?.[0]?.id, 'external-independent', 'mantiene importados aparte del selector canónico');
equal(JSON.stringify(withAliases), aliasesBefore, 'materialización alias no muta estado recibido');
equal((applyDiagnosisRecord(JSON.parse(JSON.stringify(appendedAliases)), diagnosis).oncology?.['diagnosisRecords'] as unknown[]).length, 3, 'reintento no duplica alias ni nuevo');

const evolution = buildEvolutionRecord({ id: 'evolution-test', date: '2026-08-03', author: 'Médico', specialty: 'Oncología', text: 'Control clínico.' }, audit);
let evolutionState = applyEvolutionRecord({ evolutions: [], meta: {} }, evolution);
evolutionState = applyEvolutionRecord(evolutionState, evolution);
equal(evolutionState.evolutions?.length, 1, 'reintento evolución idempotente');
equal(evolutionState.evolutions?.[0]?.audit && (evolutionState.evolutions[0]!.audit as { license?: string }).license, 'MP 1', 'conserva auditoría');

const attachmentInput = [{ id: 'image-1', studyId: 'study-1', imageId: 'source-1', versionId: 'annotated-2',
  url: '/api/media/studies/anotada.png', thumbnailUrl: '/api/media/studies/anotada.png', title: '  Mama derecha  ',
  studyDate: '2026-08-02', studyType: 'Plantilla anatómica', caption: 'Marca conservada',
  templateSource: { id: 'mama', license: 'CC BY', attribution: 'Atlas' }, audit, createdAt: audit.at }];
const attachments = normalizeEvolutionAttachments(attachmentInput);
equal(attachments[0]?.versionId, 'annotated-2', 'conserva la versión anotada elegida');
equal(attachments[0]?.annotated, true, 'conserva la indicación de copia anotada de adjuntos históricos');
const originalPdfSnapshot = normalizeEvolutionAttachments([{ url: '/api/media/studies/pagina-original.png',
  studyId: 'pdf-1', imageId: 'pdf-page-2', versionId: 'pdf-snapshot-1', annotated: false }])[0];
equal(originalPdfSnapshot?.annotated, false, 'una captura de página original no se presenta como anotada');
equal(originalPdfSnapshot?.versionId, 'pdf-snapshot-1', 'la captura original conserva su versión persistida');
equal(normalizeEvolutionAttachments([JSON.parse(JSON.stringify(originalPdfSnapshot))])[0]?.annotated, false, 'la indicación de página original persiste al recargar');
equal(attachments[0]?.url, '/api/media/studies/anotada.png', 'no reemplaza copia anotada por el original');
equal(attachments[0]?.title, 'Mama derecha', 'normaliza título del adjunto');
equal(attachments[0]?.templateSource?.['license'], 'CC BY', 'conserva procedencia de plantilla histórica');
equal(attachments[0]?.audit?.at, audit.at, 'conserva auditoría de imagen');
attachmentInput[0]!.templateSource.attribution = 'Modificada';
equal(attachments[0]?.templateSource?.['attribution'], 'Atlas', 'separa metadatos adjuntos del borrador de origen');

const safeAttachments = normalizeEvolutionAttachments([
  { url: 'javascript:alert(1)' }, { url: 'blob:http://localhost/temporary' },
  { url: 'data:text/html;base64,PHNjcmlwdD4=' },
  { url: '/api/media/studies/original.png', thumbnailUrl: 'javascript:alert(1)' },
  { url: 'https://hospital.example/image.png' }
]);
equal(safeAttachments.length, 2, 'descarta protocolos ejecutables y URLs temporales no persistibles');
equal(safeAttachments[0]?.thumbnailUrl, '/api/media/studies/original.png', 'sustituye miniatura insegura por imagen válida');
equal(safeAttachments[0]?.versionId, 'original', 'soporta adjuntos históricos sin versión');
equal(normalizeEvolutionAttachments(null).length, 0, 'acepta evolución antigua sin adjuntos');

const illustratedEvolution = buildEvolutionRecord({ id: 'evolution-illustrated', date: '2026-08-03', author: 'Médico',
  specialty: 'Oncología', text: 'Hallazgo revisado.', attachments: [...attachments,
    { id: 'image-2', url: '/api/media/studies/segunda.png', title: 'Segunda vista', studyId: 'study-1' }] }, audit);
equal(illustratedEvolution.attachments?.length, 2, 'incluye imágenes al guardar la evolución');
equal((illustratedEvolution.linkedStudyIds as string[]).join(','), 'study-1', 'vincula cada estudio una sola vez');
equal(illustratedEvolution.attachments?.[0]?.['caption'], 'Marca conservada', 'preserva comentario de adjunto existente');
equal(illustratedEvolution.attachments?.[1]?.['caption'], 'Hallazgo revisado.', 'completa comentario de imagen con texto clínico');
const illustratedState = applyEvolutionRecord({ evolutions: [], studies: [{ id: 'study-1', title: 'Plantilla' }] }, illustratedEvolution);
const repeatedIllustratedState = applyEvolutionRecord(illustratedState, illustratedEvolution);
equal(repeatedIllustratedState.evolutions?.length, 1, 'reintento con adjuntos es idempotente');
equal(repeatedIllustratedState.studies?.length, 1, 'conserva el estudio de origen sin duplicarlo');
let emptyIllustratedRejected = false;
try {
  buildEvolutionRecord({ id: 'empty-illustrated', date: '2026-08-03', author: 'Médico', specialty: 'Oncología',
    text: '  ', attachments }, audit);
} catch { emptyIllustratedRejected = true; }
ok(emptyIllustratedRejected, 'una imagen no sustituye el texto clínico obligatorio');

console.log(`Entradas clínicas: ${assertions} aserciones aprobadas.`);
