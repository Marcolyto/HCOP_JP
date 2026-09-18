import { strict as assert } from 'node:assert';
import { fitRasterSize, isDrawableShape, normalizeCanvasPoint, normalizeTemplateCatalog, normalizedSearch, safePngName, safeStudyImageUrl, shapeGeometry } from './study-template-editor.geometry';
import { ShapeAnnotation } from './study-template-editor.models';
import { StudyTemplateImageSession } from './study-template-editor.state';

const rectangle: ShapeAnnotation = {
  type: 'shape', shape: 'rectangle', start: { x: 90, y: 80 }, end: { x: 20, y: 30 }, color: '#1587c9', width: 3, filled: false
};
assert.deepEqual(shapeGeometry(rectangle), { kind: 'rectangle', x: 20, y: 30, width: 70, height: 50 });
assert.equal(isDrawableShape(rectangle), true);

const circle: ShapeAnnotation = { ...rectangle, shape: 'circle', start: { x: 10, y: 10 }, end: { x: 50, y: 30 } };
assert.deepEqual(shapeGeometry(circle), { kind: 'circle', centerX: 30, centerY: 30, radius: 20 });
assert.equal(isDrawableShape({ ...circle, end: { x: 11, y: 11 } }), false);

const arrow: ShapeAnnotation = { ...rectangle, shape: 'arrow', start: { x: 0, y: 0 }, end: { x: 100, y: 0 }, width: 5 };
const arrowGeometry = shapeGeometry(arrow);
assert.equal(arrowGeometry.kind, 'arrow');
assert.equal(arrowGeometry.kind === 'arrow' ? arrowGeometry.length : 0, 100);
assert.equal(arrowGeometry.kind === 'arrow' ? arrowGeometry.points.length : 0, 7);

assert.deepEqual(normalizeCanvasPoint(110, 70, { left: 10, top: 20, width: 200, height: 100 }, 1000, 500), { x: 500, y: 250 });
assert.deepEqual(normalizeCanvasPoint(-10, 999, { left: 10, top: 20, width: 200, height: 100 }, 1000, 500), { x: 0, y: 500 });
assert.deepEqual(fitRasterSize(8000, 4000), { x: 4096, y: 2048 });
assert.deepEqual(fitRasterSize(1000, 500), { x: 1000, y: 500 });

const catalog = normalizeTemplateCatalog({ templates: [{
  id: 'torax-ap', title: 'Tórax AP', category: 'torax', active: true, available: true,
  tags: ['frente'], fileUrl: '/api/media/images/torax.png', thumbnailUrl: '/api/media/images/thumb-torax.png'
}, { id: 'unsafe', title: 'Remota', fileUrl: 'https://example.test/a.png' }] });
assert.equal(catalog.length, 2);
assert.equal(catalog[0]?.imageUrl, '');
assert.equal(catalog[0]?.available, false);
assert.equal(catalog[1]?.imageUrl, '/api/media/images/torax.png');
assert.equal(safeStudyImageUrl('/api/media/images/../secret'), '');
assert.equal(safeStudyImageUrl('javascript:alert(1)'), '');
assert.equal(safeStudyImageUrl('assets/study-templates/images/pelvis.webp'), '/assets/study-templates/images/pelvis.webp');
assert.equal(safeStudyImageUrl('/assets/study-templates/thumbnails/pelvis.webp'), '/assets/study-templates/thumbnails/pelvis.webp');
assert.equal(normalizedSearch('  TÓRAX ÁP  '), 'torax ap');
assert.equal(safePngName('Pelvis femenina.JPG'), 'Pelvis-femenina-anotada.png');

// A pending decode must not overwrite a newer selection or a closed editor.
const imageSession = new StudyTemplateImageSession();
const firstFile = new Blob(['first'], { type: 'image/png' });
const secondFile = new Blob(['other'], { type: 'image/png' });
assert.equal(firstFile.size, secondFile.size);
assert.equal(imageSession.setSource({ file: firstFile, name: 'plantilla.png' }), true);
const firstRequest = imageSession.beginLoad();
assert.equal(imageSession.setSource({ file: secondFile, name: 'plantilla.png' }), true);
const secondRequest = imageSession.beginLoad();
assert.equal(imageSession.isCurrent(firstRequest), false, 'equal-size files remain distinct image sources');
assert.equal(imageSession.isCurrent(secondRequest), true);
imageSession.cancelLoad();
assert.equal(imageSession.isCurrent(secondRequest), false, 'closing invalidates an in-flight image decode');
assert.equal(imageSession.setSource({ file: secondFile, name: 'plantilla.png' }), false, 'reopening preserves the same source and its draft');
assert.equal(imageSession.setSource(null), true, 'returning to the library starts a separate image context');
assert.equal(imageSession.setSource(null), false, 'reopening the library preserves its selected template');

console.log('study-template-editor geometry: ok');
