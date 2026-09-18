import type { ClinicalRecord } from '../../core/patients/patient-workspace.models';
import { mergeStudyUploads, projectStudyPanel } from './study-panel.models';
import { appendStudyPdfPageVersion, studyPdfPages } from './study-pdf-page.models';

let assertions = 0;
function equal(actual: unknown, expected: unknown, message: string): void {
  assertions += 1;
  if (JSON.stringify(actual) !== JSON.stringify(expected)) throw new Error(`${message}\nactual=${JSON.stringify(actual)}\nesperado=${JSON.stringify(expected)}`);
}
function rejects(operation: () => unknown, message: string): void {
  assertions += 1;
  try { operation(); } catch { return; }
  throw new Error(message);
}

const document: ClinicalRecord = {
  id: 'pdf-study', title: 'Informe de tres páginas', fileName: 'informe.pdf', fileCategory: 'pdf', fileType: 'application/pdf',
  fileUrl: '/api/media/studies/informe.pdf', reportUrl: '/api/media/studies/informe.pdf', date: '2020-01-01',
  attachments: [{ id: 'pdf-file', url: '/api/media/studies/informe.pdf', contentType: 'application/pdf', category: 'pdf', uploadedAt: '2026-09-17T10:00:00Z' }],
  createdAt: '2026-09-17T10:00:00Z', updatedAt: '2026-09-17T10:00:00Z'
};
const beforeDocument = structuredClone(document);
const audit = { at: '2026-09-17T11:00:00Z', lastName: 'Profesional', license: '1234' };
const snapshotUrl = '/api/media/studies/page-original.png';
const annotationUrl = '/api/media/studies/page-annotation.png';
const laterAnnotationUrl = '/api/media/studies/page-annotation-2.png';

equal(studyPdfPages(document), [], 'un PDF sin copias conserva sus páginas originales en el visor');
const snapshot = appendStudyPdfPageVersion(document, 2, snapshotUrl, 'snapshot-1', audit, 'snapshot');
equal(studyPdfPages(snapshot), [{ pageNumber: 2, versions: [{ id: 'snapshot-1', url: snapshotUrl, kind: 'snapshot', label: 'Copia de la página original' }], activeVersionId: 'original' }], 'guardar una página para evolución no reemplaza la representación original del PDF');
const annotated = appendStudyPdfPageVersion(snapshot, 2, annotationUrl, 'annotation-1', audit);
equal(studyPdfPages(annotated)[0]?.activeVersionId, 'annotation-1', 'una nueva anotación queda activa en su página');
equal(studyPdfPages(annotated)[0]?.versions.map(version => version.label), ['Copia de la página original', 'Copia anotada 1'], 'las copias de página original no alteran numeración de anotaciones');
const withAnotherPage = appendStudyPdfPageVersion(annotated, 1, laterAnnotationUrl, 'page-1-annotation', audit);
equal(studyPdfPages(withAnotherPage).map(page => page.pageNumber), [1, 2], 'las copias quedan asociadas a su número de página');
equal(studyPdfPages(withAnotherPage).find(page => page.pageNumber === 2)?.versions.map(version => version.id), ['snapshot-1', 'annotation-1'], 'editar otra página conserva todas las versiones previas');
const snapshotAfterAnnotation = appendStudyPdfPageVersion(annotated, 2, snapshotUrl, 'snapshot-2', audit, 'snapshot');
equal(studyPdfPages(snapshotAfterAnnotation)[0]?.activeVersionId, 'annotation-1', 'agregar original a evolución no sustituye la copia anotada activa');
equal([withAnotherPage.fileUrl, withAnotherPage.reportUrl, withAnotherPage.fileName, withAnotherPage.fileCategory, withAnotherPage.createdAt, withAnotherPage.date, withAnotherPage.attachments],
  [document.fileUrl, document.reportUrl, document.fileName, document.fileCategory, document.createdAt, document.date, document.attachments], 'edición conserva PDF, metadatos de archivo, fecha clínica y carga original');
equal(withAnotherPage['imageAssets'], undefined, 'las páginas PDF no se incorporan a imageAssets');
equal(document, beforeDocument, 'crear copias nunca modifica el documento de entrada');
equal(audit, { at: '2026-09-17T11:00:00Z', lastName: 'Profesional', license: '1234' }, 'no modifica la auditoría recibida');

const retried = appendStudyPdfPageVersion(annotated, 2, annotationUrl, 'annotation-1', { at: '2026-09-18T11:00:00Z' });
equal(retried, annotated, 'reintentar tras falla de guardado conserva URL, versión y auditoría sin duplicar ni cambiar fecha');
rejects(() => appendStudyPdfPageVersion(annotated, 2, laterAnnotationUrl, 'annotation-1', audit), 'un mismo ID no puede sobrescribir la versión ya guardada con otro URL');
rejects(() => appendStudyPdfPageVersion(annotated, 2, annotationUrl, 'annotation-1', audit, 'snapshot'), 'un mismo ID no cambia silenciosamente su tipo de copia');
const concurrent = appendStudyPdfPageVersion(document, 3, laterAnnotationUrl, 'concurrent-3', audit);
const rebased = appendStudyPdfPageVersion(concurrent, 2, annotationUrl, 'annotation-1', audit);
equal(studyPdfPages(rebased).map(page => [page.pageNumber, page.versions[0]?.id]), [[2, 'annotation-1'], [3, 'concurrent-3']], 'rebasar una carga pendiente conserva páginas guardadas mientras falló el guardado');
equal(appendStudyPdfPageVersion(rebased, 2, annotationUrl, 'annotation-1', audit), rebased, 'rebasar nuevamente sigue siendo idempotente');

const image: ClinicalRecord = { id: 'image-study', fileName: 'imagen.png', fileCategory: 'image', fileUrl: '/api/media/studies/imagen.png', createdAt: '2026-09-17T12:00:00Z' };
const existing = [document, image];
const merged = mergeStudyUploads(existing, [withAnotherPage]);
const presentation = projectStudyPanel({ studies: merged }, [], '');
equal(merged.map(record => record.id), ['pdf-study', 'image-study'], 'guardar páginas mantiene posición del PDF y no crea estudios nuevos');
equal(presentation.uploadedEntries.map(item => item.entry.record.id), ['pdf-study', 'image-study'], 'la galería sigue mostrando un solo archivo PDF en su lugar original');
equal(presentation.uploadedEntries[0]?.images, [], 'las copias PDF no aparecen como imágenes sueltas en la galería');
equal(presentation.listEntries, [], 'editar PDF no lo mueve a la lista de fuentes');
equal(presentation.uploadedEntries[0]?.entry.record.fileUrl, document.fileUrl, 'el visor conserva la URL original después de editar y recargar');
equal(projectStudyPanel({ studies: structuredClone(merged) }, [], '').uploadedEntries.map(item => item.entry.record.id), ['pdf-study', 'image-study'], 'recargar las copias no reordena archivos');

equal(studyPdfPages({ pdfPageAssets: [
  { pageNumber: -1, versions: [{ id: 'invalid-page', url: annotationUrl }] },
  { pageNumber: 1.5, versions: [{ id: 'fractional-page', url: annotationUrl }] },
  { pageNumber: 2, activeVersionId: 'missing', versions: [{ id: 'bad-js', url: 'javascript:alert(1)' }, { id: 'bad-blob', url: 'blob:http://localhost/test' }, { id: 'original', url: annotationUrl }, { id: 'ok', url: annotationUrl }] }
] }), [{ pageNumber: 2, versions: [{ id: 'ok', url: annotationUrl, kind: 'annotation', label: 'Copia anotada 1' }], activeVersionId: 'ok' }], 'normaliza páginas válidas, omite URLs inseguras y recupera una versión activa válida');
equal(studyPdfPages({ pdfPageAssets: [{ pageNumber: 1, versions: [{ id: 'same', url: annotationUrl }, { id: 'same', url: laterAnnotationUrl }, { id: 'next', url: snapshotUrl }] }] })[0]?.versions.map(version => [version.id, version.url, version.label]),
  [['same', laterAnnotationUrl, 'Copia anotada 1'], ['next', snapshotUrl, 'Copia anotada 2']], 'desduplica IDs y numera sólo anotaciones únicas');
for (const pageNumber of [0, -1, 1.5, Number.NaN, Number.POSITIVE_INFINITY]) rejects(() => appendStudyPdfPageVersion(document, pageNumber, annotationUrl, 'bad', audit), `rechaza página inválida ${pageNumber}`);
for (const url of ['javascript:alert(1)', 'blob:http://localhost/test', 'data:text/html;base64,PHNjcmlwdD4=']) rejects(() => appendStudyPdfPageVersion(document, 1, url, 'bad', audit), 'rechaza URL no segura');
rejects(() => appendStudyPdfPageVersion(document, 1, annotationUrl, 'original', audit), 'reserva el ID original para la página del documento');

console.log(`study-pdf-page.models: ${assertions} aserciones OK`);
