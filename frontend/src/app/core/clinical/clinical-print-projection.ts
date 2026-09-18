import type { ClinicalPatient, ClinicalRecord, ClinicalState } from '../patients/patient-workspace.models';
import { personalHistoryLegacySnapshot } from './clinical-personal-history-edit';
import { physicalExamLegacySnapshot } from './clinical-physical-exam-edit';
import { clinicalStudyRecords } from './clinical-study-projection';
import { ClinicalTreatmentKind, clinicalSectionTreatments } from './clinical-treatment-projection';
import { clinicalDiagnosisEntries } from './clinical-diagnosis-projection';

export type ClinicalPrintSection =
  | 'diagnosis'
  | 'chiefComplaint'
  | 'currentIllness'
  | 'personalHistory'
  | 'studies'
  | 'physicalExam'
  | 'systemic'
  | 'radiotherapy'
  | 'surgery'
  | 'summary'
  | 'activity';

export interface ClinicalPrintFact {
  readonly label: string;
  readonly value: string;
}

export function clinicalPrintSectionHasContent(
  state: ClinicalState,
  section: ClinicalPrintSection,
  relationalTreatments: readonly ClinicalRecord[] = []
): boolean {
  const narrative = state.narrative || {};
  const exam = state.exam || {};
  if (section === 'diagnosis') {
    return clinicalDiagnosisEntries(state).length > 0;
  }
  if (section === 'chiefComplaint') return hasText(narrative['chiefComplaint']);
  if (section === 'currentIllness') return hasText(narrative['currentIllness']);
  if (section === 'personalHistory') {
    return hasText(
      narrative['backgroundClinical'], narrative['currentMedication'],
      narrative['familyOncology'], narrative['gynecology'],
      personalHistoryLegacySnapshot(state)
    );
  }
  if (section === 'studies') return clinicalStudyRecords(state).length > 0;
  if (section === 'physicalExam') {
    return hasText(
      exam['weightKg'], exam['heightM'], narrative['physicalExam'],
      physicalExamLegacySnapshot(state)
    );
  }
  if (section === 'summary') return hasText(narrative['summary'], narrative['plan']);
  if (section === 'activity') {
    return active([...(state.evolutions || []), ...(state.prescriptions || [])]).length > 0;
  }
  return clinicalSectionTreatments(
    state,
    section as ClinicalTreatmentKind,
    relationalTreatments
  ).length > 0;
}

export function clinicalPrintPatientFacts(patient?: ClinicalPatient | null): ClinicalPrintFact[] {
  if (!patient) return [];
  return [
    fact('HC', patient.medicalRecord),
    fact('DNI', patient.dni),
    fact('Fecha de nacimiento', patient.birthDate),
    fact('Sexo', patient.sex),
    fact('Obra social', patient.insurance),
    fact('N.º de afiliado', patient.affiliateNumber),
    fact('Teléfono', patient.phone),
    fact('Correo', patient.email),
    fact('Domicilio', patient.address)
  ].filter((item): item is ClinicalPrintFact => item !== null);
}

function active(records: readonly ClinicalRecord[] | undefined): ClinicalRecord[] {
  return (records || []).filter((record) => !record.deleted);
}

function fact(label: string, rawValue: unknown): ClinicalPrintFact | null {
  const value = text(rawValue);
  return value ? { label, value } : null;
}

function hasText(...values: unknown[]): boolean {
  return values.some((value) => text(value).length > 0);
}

function text(value: unknown): string {
  if (typeof value === 'string' || typeof value === 'number') return String(value).trim();
  return '';
}
