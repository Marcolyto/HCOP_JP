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
  meta?: ClinicalDocumentMeta;
  oncology?: ClinicalOncology;
  exam?: ClinicalExam;
  narrative?: ClinicalNarrative;
  evolutions?: ClinicalEvolution[];
  [key: string]: unknown;
}

export interface ClinicalDocumentMeta {
  persistenceRevision?: number;
  updatedAt?: string;
  [key: string]: unknown;
}

export interface ClinicalExam {
  weightKg?: string | number;
  heightM?: string | number;
  [key: string]: unknown;
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
  diagnosticClassifications?: Record<string, unknown>;
  tnm?: Record<string, unknown>;
  [key: string]: unknown;
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
  diagnosticClassifications?: Record<string, unknown>;
  tnm?: Record<string, unknown>;
  createdAt?: string;
  [key: string]: unknown;
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
