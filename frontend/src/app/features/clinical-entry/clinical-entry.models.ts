import type { ClinicalRecord, ClinicalState } from '../../core/patients/patient-workspace.models';

export type DiagnosisSystem = 'ajcc' | 'snomed' | 'cie10';
export type TnmPrefix = 'c' | 'p' | 'yc' | 'yp' | 'r';

export const DIAGNOSIS_SYSTEM_LABELS: Readonly<Record<DiagnosisSystem, string>> = Object.freeze({
  ajcc: 'AJCC', snomed: 'SNOMED CT', cie10: 'CIE-10'
});

export interface ClinicalAuditStamp {
  readonly action: 'cargado'; readonly lastName: string; readonly license: string; readonly at: string;
}

export interface EvolutionImageAttachment {
  readonly id: string;
  readonly url: string;
  readonly title: string;
  readonly studyId?: string;
  readonly imageId?: string;
  readonly versionId?: string;
  readonly annotated?: boolean;
  readonly thumbnailUrl?: string;
  readonly studyDate?: string;
  readonly studyType?: string;
  readonly caption?: string;
  readonly templateSource?: Readonly<Record<string, unknown>> | null;
  readonly audit?: Readonly<Record<string, unknown>> | ClinicalAuditStamp | null;
  readonly createdAt?: string;
}

export interface EvolutionEntryDraft {
  readonly id: string; readonly date: string; readonly author: string; readonly specialty: string; readonly text: string;
  readonly attachments?: readonly EvolutionImageAttachment[];
}

export interface DiagnosisClassification {
  readonly system: string; readonly freeText: string; readonly code: string; readonly display: string;
  readonly version: string; readonly source: string; readonly sourceConceptId: string;
  readonly sourceDisplay: string; readonly mapAdvice: string;
}

export interface DiagnosisCatalogItem extends DiagnosisClassification { readonly group: string; }

export interface AjccSiteSummary {
  readonly id: string; readonly name: string; readonly group: string; readonly display: string;
  readonly edition: string; readonly source: string;
}
export interface AjccSiteGroup { readonly name: string; readonly sites: readonly AjccSiteSummary[]; }
export interface AjccCategory { readonly code: string; readonly description: string; readonly notes: readonly string[]; }
export interface AjccAxis { readonly label: string; readonly categories: readonly AjccCategory[]; }
export interface AjccSiteDetail {
  readonly id: string; readonly name: string; readonly edition: string; readonly source: string;
  readonly guideVersion: string; readonly axes: Readonly<Record<string, AjccAxis>>;
}
export interface AjccStageResult { readonly stage: string; readonly missing: readonly string[]; readonly sourceRow: number | null; }

export interface DiagnosisEquivalence {
  readonly id: string; readonly active: boolean; readonly ajcc: DiagnosisClassification;
  readonly snomed: DiagnosisClassification; readonly cie10: DiagnosisClassification;
  readonly relation: string; readonly confidence: string; readonly notes: string;
}

export interface DiagnosisEntryDraft {
  readonly id: string; readonly date: string; readonly prefix: TnmPrefix; readonly site: AjccSiteSummary;
  readonly detail: AjccSiteDetail; readonly values: Readonly<Record<string, string>>; readonly stage: string;
  readonly stageEdited: boolean; readonly sourceRow: number | null;
  readonly classifications: Readonly<Record<DiagnosisSystem, DiagnosisClassification>>;
}

export interface DiagnosisValidationIssue { readonly field: string; readonly message: string; }
export interface DiagnosisValidation {
  readonly valid: boolean; readonly issues: readonly DiagnosisValidationIssue[]; readonly message: string;
}

export interface DiagnosisRecord extends ClinicalRecord {
  readonly id: string; readonly date: string; readonly datePrecision: 'day'; readonly diagnosis: string;
  readonly topography: string; readonly stage: string;
  readonly diagnosticClassifications: Readonly<Record<DiagnosisSystem, DiagnosisClassification>>;
  readonly tnm: Readonly<Record<string, unknown>>; readonly legacyProjection: false;
  readonly audit: ClinicalAuditStamp; readonly createdAt: string;
}

export interface ClinicalEntrySaveResult<TRecord extends ClinicalRecord = ClinicalRecord> {
  readonly record: TRecord; readonly state: ClinicalState; readonly revision: number;
  readonly linked: boolean; readonly warning: string;
}
export interface ClinicalEntryApiFailure { readonly status: number; readonly code: string; readonly message: string; }
export interface DiagnosisEditorCatalog {
  readonly sites: readonly AjccSiteSummary[]; readonly groups: readonly AjccSiteGroup[];
  readonly equivalences: readonly DiagnosisEquivalence[]; readonly requiredSystems: readonly DiagnosisSystem[];
}
