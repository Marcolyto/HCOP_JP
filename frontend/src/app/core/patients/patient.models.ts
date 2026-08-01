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
  studies?: ClinicalStudy[];
  [key: string]: unknown;
}

export interface ClinicalStudyAttachment {
  id?: string;
  fileName?: string;
  contentType?: string;
  size?: number;
  category?: string;
  previewable?: boolean;
  url?: string;
  uploadedAt?: string;
  storedName?: string;
  [key: string]: unknown;
}

export interface ClinicalStudy {
  id?: string;
  date?: string;
  datePrecision?: string;
  type?: string;
  title?: string;
  source?: string;
  summary?: string;
  fileName?: string;
  fileType?: string;
  fileSize?: number;
  fileCategory?: string;
  fileUrl?: string;
  attachments?: ClinicalStudyAttachment[];
  createdAt?: string;
  updatedAt?: string;
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
  type?: string;
  oncologist?: string;
  cycles?: number;
  cycleCount?: number;
  createdDate?: string;
  date?: string;
  estimatedDurationText?: string;
  estimatedDurationMinutes?: number;
  consentStatus?: string;
  [key: string]: unknown;
}

export interface TreatmentListResponse {
  ok: boolean;
  patientId: string;
  oncology?: TreatmentSummary[];
  treatments?: TreatmentSummary[];
  total?: number;
}

export interface TreatmentDrug {
  drugId?: string;
  drugName?: string;
  prescribedDoseText?: string;
  calculatedDoseText?: string;
  doseUnit?: string;
  applicationDays?: string;
  route?: string;
  administrationTime?: string;
  careSetting?: string;
  [key: string]: unknown;
}

export interface TreatmentMedication extends TreatmentDrug {
  actualDoseText?: string;
  status?: string;
}

export interface TreatmentApplication {
  applicationId?: string;
  applicationDay?: number;
  state?: string;
  status?: string;
  clinicalStatus?: string;
  administrationStatus?: string;
  scheduledAt?: string;
  date?: string;
  medications?: TreatmentMedication[];
  [key: string]: unknown;
}

export interface TreatmentDay {
  day?: number;
  status?: string;
  plannedDate?: string;
  date?: string;
  applicationId?: string;
  medications?: TreatmentMedication[];
  [key: string]: unknown;
}

export interface TreatmentCycle {
  number?: number;
  state?: string;
  status?: string;
  plannedDate?: string;
  date?: string;
  drugs?: TreatmentDrug[];
  days?: TreatmentDay[];
  applications?: TreatmentApplication[];
  [key: string]: unknown;
}

export interface TreatmentDetail {
  treatmentId?: string;
  patientId?: string;
  activeCycle?: number;
  cycles?: TreatmentCycle[];
  actions?: Record<string, unknown>;
  documentAvailability?: Record<string, unknown>;
  [key: string]: unknown;
}

export interface TreatmentDetailResponse {
  ok: boolean;
  patientId: string;
  treatmentId: string;
  detail: TreatmentDetail;
}

export interface InfusionSummary {
  id?: string;
  status?: string;
  scheduledAt?: string;
  [key: string]: unknown;
}
