/** Types mirroring the CareFlow backend DTO contracts. */

export type UserRole = 'ADMIN' | 'CARE_MANAGER' | 'PATIENT';

export type RiskLevel = 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH';
//this is a comment
export type MedicationFrequency =
  | 'ONCE_DAILY'
  | 'TWICE_DAILY'
  | 'THREE_TIMES_DAILY'
  | 'FOUR_TIMES_DAILY'
  | 'EVERY_OTHER_DAY'
  | 'WEEKLY'
  | 'AS_NEEDED';

export type FollowUpStatus =
  | 'SCHEDULED'
  | 'PENDING'
  | 'COMPLETED'
  | 'MISSED'
  | 'ESCALATED';

export type FollowUpType =
  | 'POST_DISCHARGE_CHECK'
  | 'MEDICATION_REVIEW'
  | 'SYMPTOM_CHECK'
  | 'REFILL_CHECK';

export type CarePlanType = 'STANDARD' | 'HIGH_RISK' | 'POST_DISCHARGE';

export type CarePlanStatus = 'DRAFT' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED';

export type EscalationStatus = 'PENDING' | 'ASSIGNED' | 'IN_REVIEW' | 'RESOLVED';

export type EscalationSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export type RiskSignalCode =
  | 'MULTIPLE_MISSED_DOSES'
  | 'SYMPTOMS_REPORTED'
  | 'REFILL_NEEDED'
  | 'MEDICATION_NOT_TAKEN'
  | 'LOW_ADHERENCE'
  | 'FOLLOW_UP_MISSED';

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path?: string;
  requestId?: string;
  fieldErrors?: Record<string, string>;
}

export interface UserProfile {
  id: number;
  email: string;
  fullName: string;
  role: UserRole;
  patientId?: number | null;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserProfile;
}

export interface Patient {
  id: number;
  medicalRecordNumber: string;
  firstName: string;
  lastName: string;
  fullName: string;
  dateOfBirth: string;
  age: number;
  phone?: string | null;
  email?: string | null;
  primaryCondition?: string | null;
  dischargeDate?: string | null;
  currentRiskLevel: RiskLevel;
  active: boolean;
  careManagerId?: number | null;
  careManagerName?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Medication {
  id: number;
  patientId: number;
  medicineName: string;
  dosage: string;
  frequency: MedicationFrequency;
  frequencyLabel: string;
  startDate: string;
  endDate?: string | null;
  instructions?: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CarePlan {
  id: number;
  patientId: number;
  patientName: string;
  startDate: string;
  endDate?: string | null;
  planType: CarePlanType;
  status: CarePlanStatus;
  notes?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface FollowUp {
  id: number;
  patientId: number;
  patientName: string;
  carePlanId?: number | null;
  scheduledDate: string;
  completedDate?: string | null;
  status: FollowUpStatus;
  type: FollowUpType;
  dayOffset?: number | null;
  title?: string | null;
  overdue: boolean;
  createdAt: string;
}

export interface AdherencePoint {
  date: string;
  expectedDoses: number;
  takenDoses: number;
  missedDoses: number;
  adherencePercentage: number;
}

export interface AdherenceSummary {
  patientId: number;
  patientName: string;
  expectedDoses: number;
  takenDoses: number;
  missedDoses: number;
  adherencePercentage: number;
  belowThreshold: boolean;
  history: AdherencePoint[];
}

export interface RiskEvaluationResult {
  riskLevel: RiskLevel;
  signals: RiskSignalCode[];
  signalDescriptions: string[];
  requiresHumanReview: boolean;
  summary: string;
}

export interface Escalation {
  id: number;
  patientId: number;
  patientName: string;
  medicalRecordNumber: string;
  severity: EscalationSeverity;
  reason: string;
  status: EscalationStatus;
  assignedCareManagerId?: number | null;
  assignedCareManagerName?: string | null;
  assignedAt?: string | null;
  resolvedAt?: string | null;
  resolvedByName?: string | null;
  resolutionNotes?: string | null;
  requiresHumanReview: boolean;
  createdAt: string;
}

export interface CaseSummary {
  summary: string;
  source: string;
}

export interface AuditEvent {
  id: number;
  patientId?: number | null;
  actorId?: number | null;
  actorName?: string | null;
  action: string;
  description: string;
  metadata?: string | null;
  timestamp: string;
}

export interface DashboardSummary {
  totalPatients: number;
  activeCarePlans: number;
  todayFollowUps: number;
  adherenceRate: number;
  pendingEscalations: number;
  highRiskPatients: number;
  overdueFollowUps: number;
}

export interface AdherenceBand {
  label: string;
  patientCount: number;
}

export interface AdherenceOverview {
  overallAdherenceRate: number;
  lowThresholdPercentage: number;
  patientsBelowThreshold: number;
  distribution: AdherenceBand[];
}

export interface PatientResponsePayload {
  medicationTaken: boolean;
  missedDoses: number;
  symptomsReported: boolean;
  symptoms: string[];
  refillNeeded: boolean;
  notes?: string;
}

export interface PatientResponseResult {
  responseId: number;
  followUp: FollowUp;
  riskEvaluation: RiskEvaluationResult;
  adherence: AdherenceSummary;
  escalation?: Escalation | null;
}

export interface SystemVersion {
  application: string;
  version: string;
  environment: string;
  commit: string;
  deployedAt: string;
}
