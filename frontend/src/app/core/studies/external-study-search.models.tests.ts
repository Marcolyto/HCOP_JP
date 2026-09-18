import { equal, deepStrictEqual, throws } from 'node:assert/strict';
import { externalStudyRequestIsCurrent, normalizeExternalStudySearch, normalizedStudyDni, safeExternalStudyUrl } from './external-study-search.models';

equal(normalizedStudyDni('12.345.678'), '12345678');
equal(normalizedStudyDni(' 12345 '), '12345');
equal(normalizedStudyDni('1234567890'), '1234567890');
equal(normalizedStudyDni('1234'), '');
equal(normalizedStudyDni('DNI12345678'), '');
equal(safeExternalStudyUrl('https://hospital.example/informe?id=1'), 'https://hospital.example/informe?id=1');
equal(safeExternalStudyUrl('/api/media/studies/informe.pdf'), '/api/media/studies/informe.pdf');
for (const unsafe of ['javascript:alert(1)', 'data:text/html,abc', '//elsewhere.example/document', 'https://user:pass@host.example/doc', '/\\elsewhere', 'https://host.example/\nscript']) equal(safeExternalStudyUrl(unsafe), '');

const payload = {
  patientId: '42', searchedAt: '2026-09-17T12:00:00Z', partial: false, total: 99,
  studies: [{ id: 'study-1', sourceId: 'pangea', source: 'Institución', date: '2026-09-17', type: 'TC', title: '<b>Estudio</b>',
    reportUrl: 'javascript:alert(1)', studyUrl: 'https://hospital.example/view/1' }],
  sources: [{ id: 'pangea', name: 'Institución', status: 'ok', count: 1 }, { id: 'offline', name: 'Otra fuente', status: 'error', count: 0, message: 'No disponible' }]
};
const snapshot = structuredClone(payload);
const result = normalizeExternalStudySearch(payload, '42');
equal(result.partial, true, 'una fuente fallida no descarta resultados de otras fuentes');
equal(result.total, 1, 'cuenta los estudios recibidos');
equal(result.studies[0]?.reportUrl, '', 'no permite esquemas ejecutables');
equal(result.studies[0]?.studyUrl, 'https://hospital.example/view/1');
equal(result.studies[0]?.title, '<b>Estudio</b>', 'mantiene texto para interpolación Angular, sin interpretar HTML');
equal(result.sources.length, 2);
deepStrictEqual(payload, snapshot, 'normalización no escribe datos externos ni historia');
throws(() => normalizeExternalStudySearch(payload, '7'), /paciente activo/);

equal(externalStudyRequestIsCurrent('42', '12345678', 3, '42', '12345678', 3), true);
equal(externalStudyRequestIsCurrent('42', '12345678', 3, '7', '12345678', 3), false, 'ignora respuesta de otro paciente');
equal(externalStudyRequestIsCurrent('42', '12345678', 3, '42', '87654321', 3), false, 'ignora respuesta luego de corregir DNI');
equal(externalStudyRequestIsCurrent('42', '12345678', 3, '42', '12345678', 4), false, 'respuesta de búsqueda anterior no sustituye refresco');

const sevenSourceNames = [
  ['fuesmen', 'FUESMEN'], ['idm', 'IDM'], ['centro', 'Centro del Diagnóstico'],
  ['espanol', 'Hospital Español'], ['malargue', 'Hospital Malargüe'],
  ['labo', 'Laboratorio Hospital Schestakow'], ['patologia', 'Patología']
] as const;
const sevenSourcePayload = {
  patientId: 'synthetic-seven-sources', searchedAt: '2026-09-19T10:00:00Z', partial: false, total: 700,
  studies: sevenSourceNames.map(([sourceId, source], index) => ({
    id: '73', sourceId, source, date: `2026-09-${10 + index}`, type: 'Estudio de prueba', title: `Estudio sintético ${source}`,
    reportUrl: `https://reports.example/${sourceId}/report.pdf?study=73&format=pdf`,
    studyUrl: sourceId === 'labo' || sourceId === 'patologia' ? '' : `https://viewer.example/${sourceId}/study/73?series=1&mode=view`
  })),
  sources: sevenSourceNames.map(([id, name]) => ({ id, name, status: 'ok', count: 1, message: '' }))
};
const sevenSourceSnapshot = structuredClone(sevenSourcePayload);
const sevenSourceResult = normalizeExternalStudySearch(sevenSourcePayload, 'synthetic-seven-sources');
equal(sevenSourceResult.total, 7, 'siete fuentes recibidas no se truncan a las seis fuentes anteriores');
equal(sevenSourceResult.partial, false, 'siete fuentes exitosas representan una búsqueda completa');
deepStrictEqual(sevenSourceResult.sources.map(source => [source.id, source.name]), sevenSourceNames, 'conserva identidad y nombres de las siete fuentes incluida Malargüe');
deepStrictEqual(sevenSourceResult.studies.map(study => [study['sourceId'], study.id]), sevenSourceNames.map(([id]) => [id, '73']), 'normalizar no fusiona accessions iguales de centros diferentes');
deepStrictEqual(sevenSourceResult.studies.map(study => [study.reportUrl, study.studyUrl]), sevenSourcePayload.studies.map(study => [study.reportUrl, study.studyUrl]), 'conserva separados informe y visor, sus parámetros y las fuentes sin visor');
deepStrictEqual(sevenSourcePayload, sevenSourceSnapshot, 'no modifica respuestas de ninguna de las siete fuentes');

const oneCenterUnavailable = normalizeExternalStudySearch({
  ...sevenSourcePayload, studies: sevenSourcePayload.studies.filter(study => study.sourceId !== 'espanol'),
  sources: sevenSourcePayload.sources.map(source => source.id === 'espanol' ? { ...source, status: 'error', count: 0, message: 'Fuente no disponible temporalmente.' } : source)
}, 'synthetic-seven-sources');
equal(oneCenterUnavailable.partial, true, 'el error de una cuenta Informe Médico marca resultado parcial');
equal(oneCenterUnavailable.total, 6, 'el error de Español no descarta Malargüe, Centro ni otras fuentes');
deepStrictEqual(oneCenterUnavailable.studies, sevenSourceResult.studies.filter(study => study['sourceId'] !== 'espanol'), 'los estudios y enlaces de las otras seis fuentes permanecen intactos');
deepStrictEqual(oneCenterUnavailable.sources.find(source => source.id === 'espanol'), { id: 'espanol', name: 'Hospital Español', status: 'error', count: 0, message: 'Fuente no disponible temporalmente.' }, 'conserva el estado de la fuente que falló sin convertirlo en vacío exitoso');

const partialCenterWithRows = normalizeExternalStudySearch({
  ...sevenSourcePayload,
  sources: sevenSourcePayload.sources.map(source => source.id === 'malargue' ? { ...source, status: 'error', message: 'Algunos registros no pudieron consultarse.' } : source)
}, 'synthetic-seven-sources');
equal(partialCenterWithRows.partial, true, 'una fuente incompleta marca parcial aunque ya haya devuelto estudios');
deepStrictEqual(partialCenterWithRows.studies, sevenSourceResult.studies, 'preserva resultados válidos de Malargüe aunque esa cuenta termine con error parcial');
console.log('external-study-search.models: normalización, siete fuentes, fallos parciales, URLs e identidad OK');
