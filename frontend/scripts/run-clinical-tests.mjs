import { spawnSync } from 'node:child_process';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { build } from 'esbuild';

const outputDirectory = mkdtempSync(join(tmpdir(), 'hcop-clinical-tests-'));
const suites = [
  'src/app/core/clinical/clinical-treatment-projection.tests.ts',
  'src/app/core/clinical/clinical-study-projection.tests.ts',
  'src/app/core/clinical/clinical-print-projection.tests.ts',
  'src/app/core/clinical/clinical-chief-complaint-edit.tests.ts',
  'src/app/core/clinical/clinical-current-illness-edit.tests.ts',
  'src/app/core/clinical/clinical-personal-history-edit.tests.ts',
  'src/app/core/clinical/clinical-physical-exam-edit.tests.ts',
  'src/app/core/clinical/clinical-summary-plan-edit.tests.ts',
  'src/app/core/clinical/clinical-focus.tests.ts',
  'src/app/core/patients/patient-workspace.normalization.tests.ts',
  'src/app/core/patients/clinical-draft-registry.tests.ts',
  'src/app/core/patients/clinical-save-conflict.tests.ts',
  'src/app/core/patients/clinical-conflict-comparison.tests.ts',
  'src/app/core/highlighting/clinical-highlight.engine.tests.ts',
  'src/app/features/research/research.models.tests.ts',
  'src/app/features/clinical-inbox/clinical-inbox.models.tests.ts',
  'src/app/features/configuration/protocols/protocol-configuration.normalizers.tests.ts',
  'src/app/features/configuration/catalogs/configuration-catalogs.normalizers.tests.ts',
  'src/app/features/configuration/operations/configuration-operations.normalizers.tests.ts',
  'src/app/features/oncology-history-entry/oncology-history-entry.state.tests.ts',
  'src/app/features/clinical-entry/clinical-entry.normalizers.tests.ts',
  'src/app/features/treatment-workflow-actions/treatment-workflow-actions.models.tests.ts',
  'src/app/features/treatment-documents/treatment-documents.models.tests.ts',
  'src/app/features/day-hospital/day-hospital-triage.models.tests.ts',
  'src/app/features/day-hospital/day-hospital-pharmacy.models.tests.ts',
  'src/app/features/day-hospital/day-hospital-treatment.models.tests.ts',
  'src/app/features/scheduler/care-scheduler.models.tests.ts',
  'src/app/features/study-template-editor/study-template-editor.geometry.tests.ts',
  'src/app/features/agent/agent-presentation.tests.ts'
];

try {
  for (const [index, entryPoint] of suites.entries()) {
    const outputFile = join(outputDirectory, `clinical-suite-${index}.cjs`);
    await build({
      entryPoints: [entryPoint],
      bundle: true,
      platform: 'node',
      format: 'cjs',
      target: 'node22',
      outfile: outputFile,
      logLevel: 'warning'
    });
    const result = spawnSync(process.execPath, [outputFile], { stdio: 'inherit' });
    if (result.status !== 0) process.exit(result.status ?? 1);
  }
} finally {
  rmSync(outputDirectory, { recursive: true, force: true });
}
