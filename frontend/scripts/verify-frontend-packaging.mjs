import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';

const frontendRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const generatedRoot = resolve(frontendRoot, 'src', 'generated', 'legacy-visual-contract');
const sourceRoot = resolve(frontendRoot, 'src', 'legacy-visual-contract');
const visualFiles = [
  ['styles.css', 'styles.css'],
  ['care-scheduler.css', 'care-scheduler.css'],
  ['care-scheduler-modal.css', 'care-scheduler-modal.css'],
  ['help.css', 'help.css']
];
assert(existsSync(sourceRoot), `No se encontró el contrato visual fuente en ${sourceRoot}.`);

for (const [source, target] of visualFiles) {
  assert.deepEqual(
    readFileSync(join(generatedRoot, target)),
    readFileSync(join(sourceRoot, source)),
    `${target} no coincide byte a byte con su fuente visual`
  );
}

const angular = JSON.parse(readFileSync(resolve(frontendRoot, 'angular.json'), 'utf8'));
const configuredStyles = angular.projects['hcop-jp-angular'].architect.build.options.styles;
assert.deepEqual(configuredStyles, [
  'src/styles.scss',
  'src/generated/legacy-visual-contract/styles.css',
  'src/generated/legacy-visual-contract/care-scheduler.css',
  'src/generated/legacy-visual-contract/care-scheduler-modal.css',
  'src/generated/legacy-visual-contract/help.css'
], 'El orden del contrato visual global cambió');

const appSource = readFileSync(resolve(frontendRoot, 'src', 'app', 'app.component.ts'), 'utf8');
assert(!appSource.includes('LegacyVisualContractService'), 'AppComponent todavía carga CSS dinámicamente');
assert(!existsSync(resolve(frontendRoot, 'src', 'app', 'core', 'visual', 'legacy-visual-contract.service.ts')),
  'El cargador dinámico de CSS no fue retirado');

const qrSource = readFileSync(resolve(frontendRoot, 'src', 'app', 'features', 'qr', 'qr-scanner.component.ts'), 'utf8');
assert(qrSource.includes("import('jsqr')"), 'jsQR no se importa de forma lazy');
assert(!/vendor\/jsQR\.js|createElement\(['"]script['"]\)|\.jsQR\b/.test(qrSource),
  'El lector QR conserva una ruta o inyección de script global');
const packageJson = JSON.parse(readFileSync(resolve(frontendRoot, 'package.json'), 'utf8'));
assert(packageJson.dependencies?.jsqr, 'jsqr no está declarado como dependencia reproducible');

if (process.argv.includes('--dist')) {
  const browserRoot = resolve(frontendRoot, 'dist', 'hcop-jp-angular', 'browser');
  const styleBundles = readdirSync(browserRoot).filter((file) => /^styles-.*\.css$/.test(file));
  assert.equal(styleBundles.length, 1, 'La compilación debe producir un único bundle visual global');
  const bundlePath = join(browserRoot, styleBundles[0]);
  const bundle = readFileSync(bundlePath, 'utf8');
  for (const marker of ['--page: #f2f1ee', '.care-schedule-view', '.care-schedule-backdrop', '.hcop-help-launcher']) {
    assert(bundle.includes(marker), `El bundle visual no contiene ${marker}`);
  }
  assert(statSync(bundlePath).size > 250_000, 'El bundle visual parece incompleto');

  const javascriptBundles = readdirSync(browserRoot).filter((file) => file.endsWith('.js'));
  const mainBundle = javascriptBundles.find((file) => file.startsWith('main-'));
  assert(mainBundle, 'No se encontró el bundle inicial de Angular');
  assert(statSync(join(browserRoot, mainBundle)).size < 250_000,
    'El shell clínico dejó de ser lazy y volvió a inflar la carga inicial');
  const qrBundle = javascriptBundles.find((file) =>
    !file.startsWith('main-') && readFileSync(join(browserRoot, file), 'utf8').includes('inversionAttempts'));
  assert(qrBundle, 'jsQR no quedó aislado en un fragmento lazy');
  const indexHtml = readFileSync(join(browserRoot, 'index.html'), 'utf8');
  assert(!/vendor\/jsQR\.js|styles\.css|care-scheduler\.css|care-scheduler-modal\.css|help\/help\.css/.test(indexHtml),
    'El HTML compilado todavía referencia activos visuales o QR fuera del bundle');
}

// Guardián de estáticos: los templates de estudio y los underlays de formularios sistémicos
// referencian rutas /assets/** que el navegador pide directo a nginx, no al backend. Si un
// activo falta acá, la falla es silenciosa (plantilla en blanco, sin error en consola/logs).
const publicRoot = resolve(frontendRoot, 'public');
const catalogsRoot = resolve(frontendRoot, '..', 'backend', 'runtime', 'catalogs');

// El contexto de build Docker del frontend es SOLO frontend/ (backend/ no existe ahí a
// propósito: son servicios independientes). Este cruce solo puede correr donde el repo
// completo está presente (checkout local, job "frontend" de CI) — se omite en el build
// de imagen, que valida el contrato de otra forma (tests + guardián de la propia imagen).
if (existsSync(catalogsRoot)) {
  const manifest = JSON.parse(
    readFileSync(resolve(catalogsRoot, 'study-templates', 'manifest.json'), 'utf8'));
  for (const template of manifest.templates) {
    for (const key of ['file', 'thumbnail']) {
      const relativePath = template[key];
      assert(
        existsSync(resolve(publicRoot, relativePath)),
        `study-templates/manifest.json referencia ${relativePath} (${key} de "${template.id}") ` +
          `que no existe en frontend/public/`
      );
    }
  }

  const underlays = JSON.parse(
    readFileSync(resolve(catalogsRoot, 'systemic-form-underlays.json'), 'utf8'));
  for (const pagePath of Object.keys(underlays.pages)) {
    const relativePath = pagePath.replace(/^\//, '');
    assert(
      existsSync(resolve(publicRoot, relativePath)),
      `systemic-form-underlays.json referencia la página ${pagePath} que no existe en frontend/public/`
    );
  }
} else {
  console.log(
    `(omitido) ${catalogsRoot} no existe en este contexto de build — ` +
      'el cruce con los catálogos del backend solo corre con el repo completo.');
}

console.log('OK · contrato visual, lector QR y activos de catálogo empaquetados por Angular');
