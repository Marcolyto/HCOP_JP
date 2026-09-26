import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';

const frontendRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const generatedRoot = resolve(frontendRoot, 'src', 'generated', 'legacy-visual-contract');
const sourceCandidates = [
  resolve(frontendRoot, '..', 'src', 'main', 'resources', 'static'),
  resolve(frontendRoot, 'src', 'main', 'resources', 'static')
];
const visualFiles = [
  ['styles.css', 'styles.css'],
  ['care-scheduler.css', 'care-scheduler.css'],
  ['care-scheduler-modal.css', 'care-scheduler-modal.css'],
  [join('help', 'help.css'), 'help.css']
];
const sourceRoot = sourceCandidates.find((candidate) =>
  visualFiles.every(([source]) => existsSync(join(candidate, source))));
assert(sourceRoot, 'No se encontró el contrato visual fuente.');

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

console.log('OK · contrato visual y lector QR empaquetados por Angular');
