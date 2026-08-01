export type MedicationSource =
  | 'center_stock'
  | 'patient_to_bring'
  | 'patient_has_medication'
  | 'received_center'
  | 'pending_supplier';

export interface ApplicationWorkflowDrug {
  drugId?: string;
  drugName?: string;
  sourceItemRef?: string;
  componentKey?: string;
  source?: { sourceItemRef?: string; id?: string };
  calculatedDoseText?: string;
  prescribedDoseText?: string;
  doseUnit?: string;
  unit?: string;
  route?: string;
  [key: string]: unknown;
}

export interface ApplicationAppointment {
  id?: string;
  scheduledAt?: string;
  chair?: string | number;
  confirmed?: boolean;
  [key: string]: unknown;
}

export interface ApplicationWorkflow {
  patientId: string;
  treatmentId: string;
  cycleNumber: number;
  applicationDay: number;
  revision: number;
  patientName?: string;
  patientDni?: string;
  medicalRecord?: string;
  insurance?: string;
  affiliateNumber?: string;
  diagnosis?: string;
  scheme?: string;
  plannedDate?: string;
  durationMinutes?: number;
  drugScheme?: string;
  applicationDrugs?: ApplicationWorkflowDrug[];
  prescriptionStatus?: string;
  medicationSource?: MedicationSource;
  medicationReady?: boolean;
  pharmacyValidationStatus?: string;
  pharmacyValidationNotes?: string;
  pharmacyValidationTraceable?: boolean;
  stockReservationStatus?: string;
  stockReservationNotes?: string;
  clinicalAuthorizationStatus?: string;
  clinicalAuthorizationReason?: string;
  clinicalAssessment?: Record<string, unknown>;
  workflowStatus?: string;
  appointment?: ApplicationAppointment;
  stockReservations?: Array<Record<string, unknown>>;
  [key: string]: unknown;
}

export interface ApplicationWorkflowListResponse {
  ok: boolean;
  queue: string;
  items: ApplicationWorkflow[];
  total: number;
}

export interface ApplicationWorkflowResponse {
  ok: boolean;
  workflow: ApplicationWorkflow;
}
