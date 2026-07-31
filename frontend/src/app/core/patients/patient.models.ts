export interface PatientSummary {
  id: string;
  fullName: string;
  dni?: string;
  medicalRecord?: string;
  birthDate?: string;
  sex?: string;
  insurance?: string;
  affiliateNumber?: string;
}

export interface PatientSearchResponse {
  ok: boolean;
  patients: PatientSummary[];
  total: number;
}

export interface PatientWorkspaceResponse {
  ok: boolean;
  patientId: string;
  patient: PatientSummary;
  state: ClinicalDocument;
  counts: Record<string, number>;
  treatments: { oncology: TreatmentSummary[] };
  infusions: InfusionSummary[];
}

export interface ClinicalDocument {
  oncology?: ClinicalOncology;
  narrative?: ClinicalNarrative;
  evolutions?: ClinicalEvolution[];
}

export interface ClinicalOncology {
  diagnosis?: string;
  diagnosisRecords?: ClinicalDiagnosis[];
  diagnoses?: ClinicalDiagnosis[];
  topography?: string;
  histology?: string;
  diagnosisDate?: string;
  stage?: string;
  intent?: string;
  status?: string;
  performanceStatus?: string;
  biomarkers?: string;
}

export interface ClinicalDiagnosis {
  id?: string;
  diagnosis?: string;
  diagnostico?: string;
  snomed?: string;
  cie10?: string;
  topography?: string;
  date?: string;
  stage?: string;
  estadio?: string;
  classifications?: Record<string, unknown>;
}

export interface ClinicalNarrative {
  chiefComplaint?: string;
  currentIllness?: string;
  backgroundClinical?: string;
  currentMedication?: string;
  familyOncology?: string;
  gynecology?: string;
  physicalExam?: string;
  summary?: string;
  plan?: string;
}

export interface ClinicalEvolution {
  id?: string;
  date?: string;
  author?: string;
  reason?: string;
  specialty?: string;
  text?: string;
  highlighted?: boolean;
}

export interface TreatmentSummary {
  id?: string;
  diagnosis?: string;
  scheme?: string;
  status?: string;
  cycles?: number;
  [key: string]: unknown;
}

export interface InfusionSummary {
  id?: string;
  status?: string;
  scheduledAt?: string;
  [key: string]: unknown;
}
