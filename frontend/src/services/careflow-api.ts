import { apiRequest } from './api-client';
import type {
  AdherenceOverview,
  AdherenceSummary,
  AuditEvent,
  AuthResponse,
  CarePlan,
  CarePlanType,
  CaseSummary,
  DashboardSummary,
  Escalation,
  EscalationSeverity,
  EscalationStatus,
  FollowUp,
  FollowUpStatus,
  Medication,
  PageResponse,
  Patient,
  PatientResponsePayload,
  PatientResponseResult,
  RiskLevel,
  SystemVersion,
  UserProfile,
} from '@/types/api';

export const authApi = {
  login: (email: string, password: string) =>
    apiRequest<AuthResponse>('/api/auth/login', {
      method: 'POST',
      body: { email, password },
    }),

  me: () => apiRequest<UserProfile>('/api/auth/me'),
};

export interface PatientSearchParams {
  search?: string;
  riskLevel?: RiskLevel;
  active?: boolean;
  page?: number;
  size?: number;
  sort?: string;
}

export const patientApi = {
  search: (params: PatientSearchParams = {}) =>
    apiRequest<PageResponse<Patient>>('/api/patients', {
      query: {
        search: params.search,
        riskLevel: params.riskLevel,
        active: params.active,
        page: params.page ?? 0,
        size: params.size ?? 20,
        sort: params.sort,
      },
    }),

  getById: (id: number) => apiRequest<Patient>(`/api/patients/${id}`),

  medications: (patientId: number) =>
    apiRequest<Medication[]>(`/api/patients/${patientId}/medications`),

  carePlan: (patientId: number) =>
    apiRequest<CarePlan>(`/api/patients/${patientId}/care-plan`),

  followUps: (patientId: number) =>
    apiRequest<FollowUp[]>(`/api/patients/${patientId}/follow-ups`),

  adherence: (patientId: number) =>
    apiRequest<AdherenceSummary>(`/api/patients/${patientId}/adherence`),

  auditLog: (patientId: number, page = 0, size = 50) =>
    apiRequest<PageResponse<AuditEvent>>(`/api/patients/${patientId}/audit-log`, {
      query: { page, size },
    }),

  discharge: (patientId: number, dischargeDate: string, followUpPlan: CarePlanType) =>
    apiRequest<unknown>(`/api/patients/${patientId}/discharge`, {
      method: 'POST',
      body: { dischargeDate, followUpPlan },
    }),
};

export const followUpApi = {
  getById: (id: number) => apiRequest<FollowUp>(`/api/follow-ups/${id}`),

  submitResponse: (id: number, payload: PatientResponsePayload) =>
    apiRequest<PatientResponseResult>(`/api/follow-ups/${id}/response`, {
      method: 'POST',
      body: payload,
    }),
};

export interface EscalationSearchParams {
  status?: EscalationStatus;
  severity?: EscalationSeverity;
  assignedToMe?: boolean;
  page?: number;
  size?: number;
}

export const escalationApi = {
  search: (params: EscalationSearchParams = {}) =>
    apiRequest<PageResponse<Escalation>>('/api/escalations', {
      query: {
        status: params.status,
        severity: params.severity,
        assignedToMe: params.assignedToMe,
        page: params.page ?? 0,
        size: params.size ?? 20,
      },
    }),

  getById: (id: number) => apiRequest<Escalation>(`/api/escalations/${id}`),

  summary: (id: number) => apiRequest<CaseSummary>(`/api/escalations/${id}/summary`),

  assign: (id: number, careManagerId: number) =>
    apiRequest<Escalation>(`/api/escalations/${id}/assign`, {
      method: 'POST',
      body: { careManagerId },
    }),

  markInReview: (id: number) =>
    apiRequest<Escalation>(`/api/escalations/${id}/review`, { method: 'POST' }),

  resolve: (id: number, resolutionNotes: string) =>
    apiRequest<Escalation>(`/api/escalations/${id}/resolve`, {
      method: 'POST',
      body: { resolutionNotes },
    }),
};

export const dashboardApi = {
  summary: () => apiRequest<DashboardSummary>('/api/dashboard/summary'),

  followUps: (status?: FollowUpStatus, size = 10) =>
    apiRequest<PageResponse<FollowUp>>('/api/dashboard/follow-ups', {
      query: { status, size },
    }),

  escalations: (size = 10) =>
    apiRequest<PageResponse<Escalation>>('/api/dashboard/escalations', {
      query: { size },
    }),

  adherence: () => apiRequest<AdherenceOverview>('/api/dashboard/adherence'),

  activity: () => apiRequest<AuditEvent[]>('/api/dashboard/activity'),
};

export const systemApi = {
  version: () => apiRequest<SystemVersion>('/api/system/version'),

  health: () => apiRequest<{ status: string }>('/actuator/health'),
};
