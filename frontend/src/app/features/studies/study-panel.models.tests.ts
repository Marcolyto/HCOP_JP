import type { ClinicalRecord, ClinicalState } from '../../core/patients/patient-workspace.models';
import { normalizeExternalStudySearch } from '../../core/studies/external-study-search.models';
import { mergeStudyUploads, projectStudyPanel } from './study-panel.models';

let assertions = 0;
function equal(actual: unknown, expected: unknown, message: string): void {
  assertions += 1;
  if (JSON.stringify(actual) !== JSON.stringify(expected)) throw new Error(`${message}\nactual=${JSON.stringify(actual)}\nesperado=${JSON.stringify(expected)}`);
}

const original = '/api/media/studies/original.png';
const annotated = '/api/media/studies/annotated.png';
const localImage: ClinicalRecord = {
  id: 'local-image', title: 'Exploración de mama', date: '2026-09-14', type: 'Plantilla', source: 'Carga local',
  displayImageUrls: [original, '/api/media/studies/second.png'],
  imageAssets: [{ id: 'mama', originalUrl: original, activeVersionId: 'v1', versions: [{ id: 'v1', url: annotated }] }]
};
const localDocument: ClinicalRecord = { id: 'local-document', title: 'Informe patológico', date: '2026-09-15', fileUrl: '/api/media/studies/report.pdf' };
const persisted: ClinicalRecord = { id: 'persisted', sourceId: 'pangea', sourceRef: { externalId: 'external-1' }, title: 'Informe preservado', reportUrl: 'https://pangea.example/report/1', date: '2026-09-12' };
const state: ClinicalState = { studies: [localImage, localDocument], externalStudies: [persisted] };
const queried: ClinicalRecord[] = [
  { id: 'external-1', sourceId: 'pangea', title: 'Respuesta nueva', date: '2026-09-16' },
  { id: 'queried-image', title: 'TAC externa', source: 'San Camilo', date: '2026-09-13', previewImageUrl: '/api/media/studies/remote.png' }
];
const beforeState = structuredClone(state);
const beforeQueried = structuredClone(queried);
const projection = projectStudyPanel(state, queried, '');
equal(projection.listEntries.map(entry => entry.record.id), ['queried-image', 'persisted'], 'ordena remotos sin mezclar imágenes ni archivos locales');
equal(projection.uploadedEntries.map(item => item.entry.key), ['id:local-image', 'id:local-document'], 'conserva identidad y orden persistido de archivos históricos sin fecha de carga');
equal(projection.uploadedEntries[0]?.images.map(image => image.url), [annotated, '/api/media/studies/second.png'], 'incluye todas las imágenes y la edición activa');
equal(projection.uploadedEntries[0]?.images[0]?.versions.map(version => version.url), [original, annotated], 'preserva acceso a original y ediciones');
equal(projection.listEntries.find(entry => entry.record.id === 'persisted')?.record === persisted, true, 'mantiene el registro persistido frente a un resultado nuevo');
equal(projection.listEntries.find(entry => entry.record.id === 'persisted')?.key, 'id:persisted', 'conserva navegación histórica hacia externos persistidos con ID único');
equal(projectStudyPanel(state, [{ id: 'persisted', deleted: true }], '').listEntries.some(entry => entry.record === persisted), true, 'una consulta no elimina un registro persistido');
equal(projectStudyPanel(state, [], '').uploadedEntries.length, 2, 'una consulta vacía no borra imágenes ni documentos locales');
equal(projectStudyPanel(state, [], '').listEntries.map(entry => entry.record.id), ['persisted'], 'una consulta vacía no borra documentos externos persistidos');
equal(projectStudyPanel(state, queried, '  EXPLORACION ').uploadedEntries.length, 1, 'busca títulos de galería sin distinguir acentos o mayúsculas');
equal(projectStudyPanel(state, queried, 'plantilla').uploadedEntries.length, 1, 'busca el tipo de la imagen');
equal(projectStudyPanel(state, queried, 'camilo').listEntries.map(entry => entry.record.id), ['queried-image'], 'busca la fuente remota');
equal(projectStudyPanel(state, queried, 'patologico').uploadedEntries.map(item => item.entry.record.id), ['local-document'], 'busca documentos locales en archivos cargados');
equal(projectStudyPanel(state, queried, 'desconocido'), { listEntries: [], uploadedEntries: [] }, 'filtra ambas secciones');
equal(state, beforeState, 'no modifica la historia ni los assets');
equal(queried, beforeQueried, 'no modifica el resultado consultado');

const uploadedIds = (value: ClinicalState): unknown[] => projectStudyPanel(value, [], '').uploadedEntries.map(item => item.entry.record.id);
const earlyImage: ClinicalRecord = { id: 'early-image', fileName: 'imagen.png', previewImageUrl: original, date: '2026-09-17', createdAt: '2026-09-17T10:00:00Z' };
const laterPdf: ClinicalRecord = { id: 'later-pdf', fileName: 'informe.pdf', fileCategory: 'pdf', fileUrl: '/api/media/studies/informe.pdf', date: '2026-09-17', createdAt: '2026-09-17T12:00:00Z' };
const newestPdf: ClinicalRecord = { id: 'newest-pdf', fileName: 'anterior.pdf', fileUrl: '/api/media/studies/anterior.pdf', date: '1990-01-01', createdAt: '2026-09-17T14:00:00Z' };
const historicalNewestFirst: ClinicalState = { studies: [laterPdf, earlyImage] };
equal(uploadedIds(historicalNewestFirst), ['early-image', 'later-pdf'], 'recupera el orden de carga de arrays históricos newest-first aunque PDF e imagen tienen igual fecha clínica');
equal(uploadedIds(structuredClone(historicalNewestFirst)), ['early-image', 'later-pdf'], 'recargar conserva el orden original de carga');
equal(uploadedIds({ studies: [{ ...laterPdf, date: '1980-01-01', title: 'A' }, { ...earlyImage, date: '2090-01-01', title: 'Z', updatedAt: '2090-01-01T00:00:00Z' }] }), ['early-image', 'later-pdf'], 'cambiar fecha clínica, título o updatedAt no mueve archivos cargados');
equal(uploadedIds({ studies: mergeStudyUploads(historicalNewestFirst.studies!, [newestPdf]) }), ['early-image', 'later-pdf', 'newest-pdf'], 'un nuevo PDF queda al final aunque represente un estudio clínico más antiguo');
const editedImage: ClinicalRecord = { ...earlyImage, previewImageUrl: annotated, updatedAt: '2026-09-18T12:00:00Z', attachments: [{ url: annotated, category: 'image', uploadedAt: '2026-09-18T12:00:00Z' }] };
equal(uploadedIds({ studies: mergeStudyUploads(historicalNewestFirst.studies!, [editedImage]) }), ['early-image', 'later-pdf'], 'editar la imagen mantiene su lugar y la fecha original prevalece sobre la carga de su nueva versión');
equal(projectStudyPanel(historicalNewestFirst, [], 'informe').uploadedEntries.map(item => item.entry.record.id), ['later-pdf'], 'el filtro también encuentra un PDF dentro de la pila de archivos');

const uploadDates: ClinicalState = { studies: [
  { id: 'undated-b', fileName: 'z.pdf', date: '2090-01-01' },
  { id: 'same-time-z', fileName: 'z.pdf', createdAt: '2026-09-17T12:00:00Z' },
  { id: 'undated-a', fileName: 'a.pdf', createdAt: 'fecha inválida', updatedAt: '2090-01-01T00:00:00Z' },
  { id: 'attachment-date', fileName: 'informe.pdf', createdAt: 'fecha inválida', attachments: [{ uploadedAt: '2026-09-17T13:00:00Z' }, { uploadedAt: '2026-09-17T11:00:00Z' }] },
  { id: 'same-time-a', fileName: 'a.pdf', createdAt: '2026-09-17T12:00:00Z' }
] };
equal(uploadedIds(uploadDates), ['undated-b', 'undated-a', 'attachment-date', 'same-time-z', 'same-time-a'], 'sin fecha original queda primero en orden persistido; attachments aporta fecha original y empates conservan el orden del array');
equal(uploadDates.studies?.map(record => record.id), ['undated-b', 'same-time-z', 'undated-a', 'attachment-date', 'same-time-a'], 'ordenar la vista no reescribe el array persistido');

const fileVariants: ClinicalRecord[] = [
  { id: 'pdf', fileUrl: '/api/media/studies/file.pdf' },
  { id: 'word', fileName: 'file.docx', fileType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' },
  { id: 'video', fileCategory: 'video', studyUrl: '/api/media/studies/video.mp4' },
  { id: 'dicom', fileName: 'scan.dcm', fileType: 'application/dicom', fileCategory: 'file', studyUrl: '/api/media/studies/scan.dcm' },
  { id: 'legacy-data', dataUrl: 'data:application/pdf;base64,JVBERg==' },
  { id: 'attachment-only', attachments: [{ url: '/api/media/studies/legacy.doc', contentType: 'application/msword', uploadedAt: '2026-09-17T10:00:00Z' }] },
  { id: 'template', type: 'Plantilla', imageAssets: [{ id: 'template-asset', originalUrl: original, versions: [] }] }
];
const classified = projectStudyPanel({ studies: [...fileVariants, { id: 'clinical-only', type: 'Documento PDF', title: 'Registro clínico sin archivo' }], externalStudies: [{ id: 'remote-pdf', fileUrl: 'https://provider.example/report.pdf' }] }, [{ id: 'query-pdf', fileName: 'query.pdf', fileUrl: 'https://provider.example/query.pdf' }], '');
equal(classified.uploadedEntries.map(item => item.entry.record.id).sort(), fileVariants.map(record => record.id).sort(), 'PDF, Word, video, DICOM, dataUrl legacy, adjuntos y plantillas se muestran debajo como cargas locales');
equal(classified.listEntries.map(entry => entry.record.id).sort(), ['clinical-only', 'query-pdf', 'remote-pdf'], 'el clínico sin archivo y todos los remotos permanecen en la lista');
equal(classified.uploadedEntries.find(item => item.entry.record.id === 'pdf')?.images, [], 'un documento cargado admite tarjeta sin preview de imagen');
equal(projectStudyPanel({ studies: fileVariants }, [], 'docx').uploadedEntries.map(item => item.entry.record.id), ['word'], 'filtra cargas por nombre y extensión del archivo');

const dicomUrl = '/api/media/studies/scan.dcm';
const heicUrl = '/api/media/studies/photo.heic';
const unsupportedUploads: ClinicalRecord[] = [
  { id: 'legacy-dicom', fileName: 'scan.dcm', fileCategory: 'image', fileUrl: dicomUrl, attachments: [{ url: dicomUrl, category: 'image', contentType: 'application/dicom', previewable: false }] },
  { id: 'heic', fileName: 'photo.heic', fileCategory: 'image', fileType: 'image/heic', fileUrl: heicUrl, attachments: [{ url: heicUrl, category: 'image', contentType: 'image/heic', previewable: false }] }
];
const beforeUnsupported = structuredClone(unsupportedUploads);
const unsupportedProjection = projectStudyPanel({ studies: unsupportedUploads }, [], '');
equal(unsupportedProjection.uploadedEntries.map(item => [item.entry.record.id, item.images.length]), [['legacy-dicom', 0], ['heic', 0]], 'DICOM y HEIC marcados no previewables conservan tarjeta genérica sin imagen rota');
equal(unsupportedProjection.listEntries, [], 'los archivos no previewables permanecen en cargas locales');
equal(projectStudyPanel({ studies: [{ id: 'legacy-no-flag', fileCategory: 'image', fileUrl: original, attachments: [{ url: original, category: 'image' }] }] }, [], '').uploadedEntries[0]?.images.map(image => image.url), [original], 'los adjuntos históricos sin flag de preview conservan su imagen');
equal(projectStudyPanel({ studies: [{ id: 'flag-true', fileCategory: 'image', fileUrl: original, attachments: [{ url: original, category: 'image', previewable: true }] }] }, [], '').uploadedEntries[0]?.images.map(image => image.url), [original], 'una imagen marcada previewable sigue visible');
const mixedUpload: ClinicalRecord = { ...unsupportedUploads[0], attachments: [...unsupportedUploads[0]!.attachments!, { url: original, category: 'image', previewable: true }] };
equal(projectStudyPanel({ studies: [mixedUpload] }, [], '').uploadedEntries[0]?.images.map(image => image.url), [original], 'un adjunto no previewable no oculta otras imágenes del mismo estudio');
const annotatedDicom: ClinicalRecord = { ...unsupportedUploads[0], imageAssets: [{ id: 'dicom-image', originalUrl: dicomUrl, activeVersionId: 'readable-version', versions: [{ id: 'readable-version', url: annotated }] }] };
const convertedImage = projectStudyPanel({ studies: [annotatedDicom] }, [], '').uploadedEntries[0]?.images[0];
equal(convertedImage?.url, annotated, 'una copia anotada válida de un original no previewable continúa visible');
equal(convertedImage?.versions.map(version => version.url), [annotated], 'el selector de versiones no ofrece el original que produciría una imagen rota');
equal(convertedImage?.sourceUrl, dicomUrl, 'conserva identidad original para futuras ediciones sin reescribir el estudio');
const invalidActiveVersion: ClinicalRecord = { ...annotatedDicom, imageAssets: [{ id: 'dicom-image', originalUrl: dicomUrl, activeVersionId: 'unsupported-version', versions: [{ id: 'readable-version', url: annotated }, { id: 'unsupported-version', url: heicUrl }] }], attachments: [...unsupportedUploads[0]!.attachments!, ...unsupportedUploads[1]!.attachments!] };
const fallbackImage = projectStudyPanel({ studies: [invalidActiveVersion] }, [], '').uploadedEntries[0]?.images[0];
equal([fallbackImage?.url, fallbackImage?.versionId, fallbackImage?.annotated], [annotated, 'readable-version', true], 'si la versión activa no es previewable recupera la última copia válida');
equal(unsupportedUploads, beforeUnsupported, 'la selección visual no modifica descriptores ni flags guardados');

const existingUploads: ClinicalRecord[] = [earlyImage, { id: 'unrelated-deleted', deleted: true }, laterPdf];
const incomingUploads: ClinicalRecord[] = [editedImage, newestPdf, { ...newestPdf, title: 'Última copia del mismo archivo' }, { title: 'Sin ID', fileName: 'legacy.txt' }];
const beforeExisting = structuredClone(existingUploads);
const beforeIncoming = structuredClone(incomingUploads);
const merged = mergeStudyUploads(existingUploads, incomingUploads);
equal(merged.map(record => record.id ?? record.title), ['early-image', 'unrelated-deleted', 'later-pdf', 'newest-pdf', 'Sin ID'], 'merge reemplaza en su sitio, conserva tombstones y anexa nuevos una sola vez');
equal(merged[0] === editedImage, true, 'merge utiliza la edición entrante para el ID existente');
equal(merged[2] === laterPdf, true, 'merge conserva registros ajenos sin reconstruirlos');
equal(merged[3]?.title, 'Última copia del mismo archivo', 'merge conserva la última versión de un ID repetido en el lote');
equal(existingUploads, beforeExisting, 'merge no muta la historia anterior');
equal(incomingUploads, beforeIncoming, 'merge no muta el lote de carga');
equal(mergeStudyUploads(existingUploads, []), existingUploads, 'un lote vacío conserva todos los registros y su orden');
equal(mergeStudyUploads([], [newestPdf, earlyImage]).map(record => record.id), ['newest-pdf', 'early-image'], 'merge persiste el orden del lote sin ordenar por fechas ni títulos');

equal(uploadedIds({ studies: [earlyImage, laterPdf, { ...earlyImage, title: 'Edición histórica duplicada' }] }), ['early-image', 'later-pdf'], 'los duplicados locales conservan un solo archivo y su posición inicial');
equal(projectStudyPanel({ studies: [earlyImage, { ...earlyImage, deleted: true }] }, [], '').uploadedEntries, [], 'un tombstone también oculta cargas duplicadas');

const duplicates: ClinicalState = {
  studies: [
    { id: 'deleted-id', deleted: true },
    { id: 'deleted-local', sourceId: 'pangea', sourceRef: { externalId: 'deleted-remote' }, reportUrl: '/reports/deleted', deleted: true },
    { id: 'kept-local', sourceId: 'pangea', sourceRef: { externalId: 'kept-remote' }, title: 'Local con edición', previewImageUrl: original },
    { title: 'Imagen histórica sin ID', previewImageUrl: '/api/media/studies/legacy.png' }
  ],
  externalStudies: [
    { id: 'deleted-id', title: 'No revivir por ID' },
    { id: 'deleted-remote', sourceId: 'pangea', studyUrl: '/studies/deleted' },
    { id: 'kept-remote', sourceId: 'pangea', title: 'No duplicar local' },
    { id: 'persisted-bridge', sourceId: 'pangea', reportUrl: '/reports/shared', title: 'Informe de referencia' },
    { id: 'external-deleted', deleted: true },
    { title: 'Externo sin identidad' }
  ]
};
const duplicateQueries: ClinicalRecord[] = [
  { id: 'deleted-id' },
  { id: 'deleted-by-url', reportUrl: '/reports/deleted' },
  { id: 'deleted-by-bridge', studyUrl: '/studies/deleted' },
  { id: 'from-other-source', reportUrl: '/reports/shared', studyUrl: '/studies/shared' },
  { id: 'third-source', studyUrl: '/studies/shared' },
  { id: 'external-deleted' },
  { id: 'kept-local', title: 'No sustituir local por consulta' },
  { title: 'Consulta sin identidad' }
];
const deduplicated = projectStudyPanel(duplicates, duplicateQueries, '');
equal(deduplicated.listEntries.map(entry => entry.record.title).sort(), ['Consulta sin identidad', 'Externo sin identidad', 'Informe de referencia'], 'deduplica fuentes transitivamente por ID y URLs y respeta tombstones');
equal(deduplicated.uploadedEntries.map(item => item.entry.key).sort(), ['id:kept-local', 'local:3'], 'conserva keys históricas y todas las imágenes locales');
equal(projectStudyPanel(duplicates, duplicateQueries, 'sustituir'), { listEntries: [], uploadedEntries: [] }, 'filtrar no hace reaparecer una copia remota de un local oculto por búsqueda');
equal(projectStudyPanel(undefined, [], ''), { listEntries: [], uploadedEntries: [] }, 'admite ausencia de historia');
equal(projectStudyPanel(null, [{ id: 'only-remote', previewImageUrl: original }], '').uploadedEntries, [], 'las imágenes consultadas nunca entran en la galería');
equal(projectStudyPanel({ studies: [{ id: 'same', title: 'Anterior' }, { id: 'same', title: 'Actual' }] }, [], '').listEntries.map(entry => entry.record.title), ['Actual'], 'respeta la preferencia local existente para IDs repetidos');
equal(projectStudyPanel({ studies: [{ id: 'same', title: 'Anterior' }, { id: 'same', deleted: true }] }, [], '').listEntries, [], 'un tombstone local también oculta versiones locales con el mismo ID');

const distinctSourceResults: ClinicalRecord[] = [
  { id: '1', sourceId: 'pangea', title: 'Pangea uno' },
  { id: '2', sourceId: 'pangea', title: 'Pangea dos' },
  { id: '1', sourceId: 'sancamilo', title: 'San Camilo uno' }
];
const distinctSources = projectStudyPanel({}, distinctSourceResults, '').listEntries;
equal(distinctSources.length, 3, 'sourceId identifica la fuente: IDs distintos de la misma fuente y el mismo ID de otra fuente son estudios separados');
equal(new Set(distinctSources.map(entry => entry.key)).size, 3, 'fuentes distintas con el mismo ID tienen keys diferentes');
equal(projectStudyPanel({ externalStudies: distinctSourceResults }, [], '').listEntries.length, 3, 'tampoco colapsa registros persistidos de fuentes distintas');
equal(new Set(projectStudyPanel({ externalStudies: distinctSourceResults }, [], '').listEntries.map(entry => entry.key)).size, 3, 'desambigua keys de persistidos sólo cuando comparten ID');
equal(projectStudyPanel({ externalStudies: [{ id: '1', title: 'Informe legacy' }] }, [{ id: '1', sourceId: 'pangea', title: 'Respuesta' }], '').listEntries.map(entry => entry.record.title), ['Informe legacy'], 'legacy sin fuente prevalece cuando el ID remoto identifica una sola fuente');
equal(projectStudyPanel({ externalStudies: [{ id: '1', source: 'Pangea', title: 'Informe legacy Pangea' }] }, distinctSourceResults, '').listEntries.map(entry => entry.record.title).sort(), ['Informe legacy Pangea', 'Pangea dos', 'San Camilo uno'], 'reconoce la fuente legacy y no absorbe el mismo ID de otra fuente');
equal(projectStudyPanel({ externalStudies: [{ id: '1', title: 'Fuente desconocida' }] }, distinctSourceResults, '').listEntries.length, 4, 'un ID legacy ambiguo no une proveedores distintos');
equal(projectStudyPanel({ studies: [{ id: '1', sourceId: 'pangea', title: 'Local Pangea' }] }, [{ id: '1', sourceId: 'sancamilo', title: 'Remoto San Camilo' }], '').listEntries.map(entry => entry.key).sort(), ['external:sancamilo:id:1', 'id:1'], 'preserva navegación local y evita colisión de key con otra fuente');
equal(projectStudyPanel({}, [{ sourceId: 'pangea', title: 'Sin ID A' }, { sourceId: 'pangea', title: 'Sin ID B' }], '').listEntries.length, 2, 'no deduplica por fuente aislada cuando falta ID');
equal(projectStudyPanel({ externalStudies: [{ id: 'local-persisted', sourceId: 'pangea', sourceRef: { externalId: '7' } }] }, [{ id: '7', sourceId: 'sancamilo' }], '').listEntries.length, 2, 'externalId también queda acotado a su fuente');

const sevenSources = [
  ['fuesmen', 'FUESMEN', '2026-09-10'], ['idm', 'IDM', '2026-09-18'],
  ['centro', 'Centro del Diagnóstico', '2026-09-12'], ['espanol', 'Hospital Español', '2026-09-16'],
  ['malargue', 'Hospital Malargüe', '2026-09-19'], ['labo', 'Laboratorio Hospital Schestakow', '2026-09-11'],
  ['patologia', 'Patología', '2026-09-15']
] as const;
const sourceMixPayload = {
  patientId: 'synthetic-mixed-sources', partial: false,
  sources: sevenSources.map(([id, name]) => ({ id, name, status: 'ok', count: 1 })),
  studies: sevenSources.map(([sourceId, source, date]) => ({
    id: '73', sourceId, source, date, title: `Estudio sintético ${source}`, type: 'TC',
    reportUrl: `https://reports.example/${sourceId}/informe.pdf?study=73&format=pdf`,
    studyUrl: sourceId === 'labo' || sourceId === 'patologia' ? '' : `https://viewer.example/${sourceId}/study/73?series=1&mode=view`
  }))
};
const sourceMix = normalizeExternalStudySearch(sourceMixPayload, 'synthetic-mixed-sources');
const mixedProjection = projectStudyPanel({ studies: [localImage, localDocument] }, sourceMix.studies, '');
equal(mixedProjection.listEntries.map(entry => entry.record['sourceId']), ['malargue', 'idm', 'espanol', 'patologia', 'centro', 'labo', 'fuesmen'], 'las siete fuentes se intercalan en orden global descendente por fecha, sin agrupar por cuenta ni proveedor');
equal(mixedProjection.listEntries.length, 7, 'accession 73 de siete centros distintos representa siete estudios');
equal(new Set(mixedProjection.listEntries.map(entry => entry.key)).size, 7, 'los siete centros tienen identidades de navegación diferentes aunque compartan accession');
equal(mixedProjection.uploadedEntries.map(item => item.entry.record.id), ['local-image', 'local-document'], 'la búsqueda de siete fuentes no mueve ni absorbe archivos locales');
equal(mixedProjection.listEntries.map(entry => [entry.record['sourceId'], entry.record.reportUrl, entry.record.studyUrl]),
  ['malargue', 'idm', 'espanol', 'patologia', 'centro', 'labo', 'fuesmen'].map(sourceId => {
    const record = sourceMix.studies.find(study => study['sourceId'] === sourceId)!;
    return [sourceId, record.reportUrl, record.studyUrl];
  }), 'ordenar y deduplicar conserva ambos enlaces de cada centro y las fuentes con sólo informe');
equal(projectStudyPanel({}, sourceMix.studies, 'MALARGUE').listEntries.map(entry => entry.record['sourceId']), ['malargue'], 'encuentra Hospital Malargüe sin exigir diéresis');
equal(projectStudyPanel({}, sourceMix.studies, 'Español').listEntries.map(entry => entry.record['sourceId']), ['espanol'], 'filtra la cuenta Español sin confundirla con Centro o Malargüe');

const malargueFirst = sourceMix.studies.find(study => study['sourceId'] === 'malargue')!;
const malargueSecond: ClinicalRecord = { ...malargueFirst, id: '74', date: '2026-09-17', title: 'Segundo estudio sintético Malargüe', reportUrl: 'https://reports.example/malargue/informe.pdf?study=74', studyUrl: 'https://viewer.example/malargue/study/74' };
const persistedMalargue: ClinicalRecord = { ...malargueFirst, title: 'Estudio Malargüe ya conservado' };
const mixedState: ClinicalState = { studies: [localImage, localDocument], externalStudies: [persistedMalargue] };
const mixedQueries = [...sourceMix.studies, { ...malargueFirst }, malargueSecond];
const mixedStateBefore = structuredClone(mixedState);
const mixedQueriesBefore = structuredClone(mixedQueries);
const mergedSources = projectStudyPanel(mixedState, mixedQueries, '');
equal(mergedSources.listEntries.map(entry => `${entry.record['sourceId']}:${entry.record.id}`), ['malargue:73', 'idm:73', 'malargue:74', 'espanol:73', 'patologia:73', 'centro:73', 'labo:73', 'fuesmen:73'], 'deduplica respuestas repetidas del mismo centro y conserva dos accessions de Malargüe en su fecha global');
equal(mergedSources.listEntries.find(entry => entry.record['sourceId'] === 'malargue' && entry.record.id === '73')?.record === persistedMalargue, true, 'el estudio Malargüe ya persistido conserva su registro y enlaces ante una consulta duplicada');
equal(projectStudyPanel({ externalStudies: sourceMix.studies.map(study => ({ ...study })) }, sourceMix.studies, '').listEntries.length, 7, 'recargar las siete fuentes persistidas y volver a consultar no duplica ni colapsa centros');
equal(projectStudyPanel({ studies: [{ id: '73', sourceId: 'malargue', deleted: true }] }, mixedQueries, '').listEntries.map(entry => `${entry.record['sourceId']}:${entry.record.id}`), ['idm:73', 'malargue:74', 'espanol:73', 'patologia:73', 'centro:73', 'labo:73', 'fuesmen:73'], 'el borrado de accession 73 de Malargüe no oculta accession 73 de otra institución ni accession 74 de la misma');

const partialSources = normalizeExternalStudySearch({
  ...sourceMixPayload, studies: mixedQueries.filter(study => study['sourceId'] !== 'centro'),
  sources: sourceMixPayload.sources.map(source => source.id === 'centro' ? { ...source, status: 'error', count: 0, message: 'Fuente temporalmente no disponible.' } : source)
}, 'synthetic-mixed-sources');
equal(partialSources.partial, true, 'el fallo de Centro llega como estado parcial a la proyección');
equal(projectStudyPanel(mixedState, partialSources.studies, '').listEntries.map(entry => `${entry.record['sourceId']}:${entry.record.id}`), ['malargue:73', 'idm:73', 'malargue:74', 'espanol:73', 'patologia:73', 'labo:73', 'fuesmen:73'], 'el error de una de siete fuentes no pierde estudios de las restantes ni el persistido');
equal(projectStudyPanel(mixedState, partialSources.studies, '').uploadedEntries.map(item => item.entry.record.id), ['local-image', 'local-document'], 'un error remoto no elimina las imágenes y PDF locales');
equal(mixedState, mixedStateBefore, 'mezclar siete fuentes no reescribe registros persistidos');
equal(mixedQueries, mixedQueriesBefore, 'deduplicar centros no modifica la respuesta consultada');

console.log(`study-panel.models: ${assertions} aserciones OK`);
