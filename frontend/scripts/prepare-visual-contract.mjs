import { copyFileSync, existsSync, mkdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const frontendRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const files = [
  ['styles.css', 'styles.css'],
  ['care-scheduler.css', 'care-scheduler.css'],
  ['care-scheduler-modal.css', 'care-scheduler-modal.css'],
  [join('help', 'help.css'), 'help.css']
];
const sourceCandidates = [
  resolve(frontendRoot, '..', 'src', 'main', 'resources', 'static'),
  resolve(frontendRoot, 'src', 'main', 'resources', 'static')
];
const sourceRoot = sourceCandidates.find((candidate) =>
  files.every(([source]) => existsSync(join(candidate, source))));

if (!sourceRoot) {
  throw new Error(`No se encontró el contrato visual fuente en: ${sourceCandidates.join(', ')}`);
}

const targetRoot = resolve(frontendRoot, 'src', 'generated', 'legacy-visual-contract');
mkdirSync(targetRoot, { recursive: true });
for (const [source, target] of files) copyFileSync(join(sourceRoot, source), join(targetRoot, target));

console.log(`Contrato visual Angular preparado desde ${sourceRoot}.`);
