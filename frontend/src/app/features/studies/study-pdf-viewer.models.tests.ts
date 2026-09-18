import { safeStudyPdfUrl, studyPdfRasterSize } from './study-pdf-viewer.models';
let assertions = 0;
function equal(actual: unknown, expected: unknown, message: string): void {
  assertions += 1;
  if (JSON.stringify(actual) !== JSON.stringify(expected)) throw new Error(`${message}: ${JSON.stringify(actual)}`);
}
const origin = 'http://localhost:5181';
equal(safeStudyPdfUrl('/api/media/studies/file.pdf', origin), '/api/media/studies/file.pdf', 'acepta archivo local');
equal(safeStudyPdfUrl(`${origin}/api/media/studies/abc-123.pdf`, origin), '/api/media/studies/abc-123.pdf', 'acepta mismo origen absoluto');
for (const invalid of [
  'https://remote.example/api/media/studies/file.pdf', '//localhost:5181/api/media/studies/file.pdf',
  'javascript:alert(1)', 'data:application/pdf;base64,AA==', 'blob:temporary',
  '/api/media/images/file.pdf', '/api/media/studies/', '/api/media/studies/../secret.pdf',
  '/api/media/studies/folder/../file.pdf', '/api/media/studies/%2e%2e%2fsecret.pdf',
  '/api/media/studies/file.pdf?token=secret', '/api/media/studies/file.pdf#page=1',
  'http://user:secret@localhost:5181/api/media/studies/file.pdf', '/api/media/studies\\file.pdf',
  'api/media/studies/file.pdf'
]) equal(safeStudyPdfUrl(invalid, origin), '', 'rechaza URL fuera del archivo permitido');
const standard = studyPdfRasterSize(600, 600, 900, 3);
equal(standard, { cssWidth: 600, cssHeight: 900, outputScale: 2, pixelWidth: 1200, pixelHeight: 1800 }, 'adapta ancho sin recortar altura y acota DPR');
const landscape = studyPdfRasterSize(800, 1200, 600, 1);
equal(landscape.cssHeight, 400, 'conserva proporción de páginas horizontales');
const huge = studyPdfRasterSize(5000, 500, 20000, 2);
equal(huge.cssHeight, 200000, 'no recorta una página muy alta');
equal(huge.pixelWidth * huge.pixelHeight <= 8_000_000 && Math.max(huge.pixelWidth, huge.pixelHeight) <= 8192, true, 'acota memoria sin ocultar páginas');
equal(studyPdfRasterSize(0, 0, NaN, NaN), { cssWidth: 1, cssHeight: 1, outputScale: 1, pixelWidth: 1, pixelHeight: 1 }, 'dimensiones inválidas no producen canvas infinitos');
console.log(`study-pdf-viewer.models: ${assertions} aserciones OK`);
