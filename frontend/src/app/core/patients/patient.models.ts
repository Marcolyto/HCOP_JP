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
  counts: Record<string, number>;
  treatments: { oncology: TreatmentSummary[] };
  infusions: InfusionSummary[];
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
