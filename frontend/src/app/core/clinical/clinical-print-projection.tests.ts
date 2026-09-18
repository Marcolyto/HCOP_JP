import {
  clinicalPrintPatientFacts,
  clinicalPrintSectionHasContent
} from './clinical-print-projection';

interface TestCase { readonly name: string; readonly run: () => void; }
const tests: TestCase[] = [];
let assertions = 0;

function test(name: string, run: () => void): void { tests.push({ name, run }); }
function equal(actual: unknown, expected: unknown, message = ''): void {
  assertions += 1;
  if (!Object.is(actual, expected)) {
    throw new Error(`${message ? `${message}: ` : ''}esperado ${String(expected)}, recibido ${String(actual)}.`);
  }
}

test('una hoja vacía no imprime secciones de relleno', () => {
  for (const section of [
    'diagnosis', 'chiefComplaint', 'currentIllness', 'personalHistory', 'studies',
    'physicalExam', 'systemic', 'radiotherapy', 'surgery', 'summary', 'activity'
  ] as const) {
    equal(clinicalPrintSectionHasContent({}, section), false, section);
  }
});

test('una hoja parcial imprime únicamente las secciones con contenido clínico', () => {
  const state = {
    narrative: { chiefComplaint: 'Control oncológico', summary: 'Continúa seguimiento.' },
    exam: { weightKg: '75' },
    studies: [{ id: 'deleted', deleted: true }],
    evolutions: [{ id: 'e1', text: 'Evolución documentada' }]
  };
  equal(clinicalPrintSectionHasContent(state, 'chiefComplaint'), true);
  equal(clinicalPrintSectionHasContent(state, 'summary'), true);
  equal(clinicalPrintSectionHasContent(state, 'physicalExam'), true);
  equal(clinicalPrintSectionHasContent(state, 'activity'), true);
  equal(clinicalPrintSectionHasContent(state, 'studies'), false);
  equal(clinicalPrintSectionHasContent(state, 'currentIllness'), false);
});

test('los estudios externos comparten la misma proyección de impresión', () => {
  equal(clinicalPrintSectionHasContent({
    externalStudies: [{ id: 'remote-study', date: '2026-08-03', title: 'TAC externa' }]
  }, 'studies'), true);
  equal(clinicalPrintSectionHasContent({
    externalStudies: [{ id: 'remote-study', deleted: true }]
  }, 'studies'), false);
});

test('diagnóstico estructurado sin proyección textual sigue presente en impresión', () => {
  equal(clinicalPrintSectionHasContent({ oncology: { diagnosisRecords: [{ id: 'd1',
    diagnosticClassifications: { snomed: { code: '39937001', display: 'Diagnóstico codificado' } },
    tnm: { t: 'T1', n: 'N0', m: 'M0' } }] } }, 'diagnosis'), true);
  equal(clinicalPrintSectionHasContent({ oncology: { diagnosticClassifications: { cie10: { code: 'C60' } } } }, 'diagnosis'), true);
  equal(clinicalPrintSectionHasContent({ oncology: { tnm: { t: 'T1' } } }, 'diagnosis'), true);
  equal(clinicalPrintSectionHasContent({ oncology: { diagnosisRecords: [{ id: 'd1', diagnosis: 'Borrado', deleted: true }] } }, 'diagnosis'), false);
});

test('imprime la ultima instantanea legacy sin revivirla en modo estructurado', () => {
  const legacy = {
    narrative: {},
    meta: {
      liraImport: { origin: 'migration' },
      sectionVersions: { personalHistory: [{ content: 'Antecedentes importados' }] },
      sectionFormModes: {}
    }
  };
  equal(clinicalPrintSectionHasContent(legacy, 'personalHistory'), true);
  equal(clinicalPrintSectionHasContent({
    ...legacy,
    narrative: { backgroundClinical: { formato: 'legacy-invalido' } },
    meta: { ...legacy.meta, sectionFormModes: { personalHistory: 'structured' } }
  }, 'personalHistory'), false);
});

test('imprime el examen físico legacy sin revivirlo en modo estructurado', () => {
  const legacy = {
    exam: { weightKg: { formato: 'legacy' } }, narrative: {},
    meta: {
      liraImport: { origin: 'migration' },
      sectionVersions: { physicalExam: [{ content: 'Estado general: importado' }] },
      sectionFormModes: {}
    }
  };
  equal(clinicalPrintSectionHasContent(legacy, 'physicalExam'), true);
  equal(clinicalPrintSectionHasContent({
    ...legacy,
    meta: { ...legacy.meta, sectionFormModes: { physicalExam: 'structured' } }
  }, 'physicalExam'), false);
});

test('la impresión reutiliza la proyección unificada de tratamientos', () => {
  const state = {
    evolutions: [
      { id: 's1', category: 'surgery', title: 'Colectomía' },
      { id: 'r1', category: 'radiotherapy', title: 'IMRT' }
    ]
  };
  equal(clinicalPrintSectionHasContent(state, 'surgery'), true);
  equal(clinicalPrintSectionHasContent(state, 'radiotherapy'), true);
  equal(clinicalPrintSectionHasContent(state, 'systemic'), false);
  equal(clinicalPrintSectionHasContent({}, 'systemic', [{ id: '88', type: 'Quimioterapia' }]), true);
});

test('la cabecera ampliada omite datos ausentes sin inventar guiones', () => {
  const facts = clinicalPrintPatientFacts({
    id: '1', fullName: 'Paciente de prueba', dni: '99000111', medicalRecord: 'HC-8',
    insurance: 'Cobertura', affiliateNumber: '', phone: '2604000000'
  });
  equal(facts.length, 4);
  equal(facts.map((item) => item.label).join('|'), 'HC|DNI|Obra social|Teléfono');
  equal(facts.some((item) => item.value === '—'), false);
});

for (const item of tests) item.run();
console.log(`clinical-print-projection: ${tests.length} casos, ${assertions} aserciones OK`);
