import { http, resetCsrfToken } from './http';
import type {
  Assessment,
  AssessmentRequest,
  AvailabilityRequest,
  AvailabilityResponse,
  CalendarResponse,
  ChangePasswordRequest,
  CompleteRevisionRequest,
  CompleteSessionRequest,
  CreateSessionRequest,
  DashboardOverview,
  Exam,
  ExamRequest,
  FocusCompleteResponse,
  FocusSession,
  FocusStartRequest,
  FocusStats,
  GeneratePlanRequest,
  HealthResponse,
  LoginRequest,
  NotificationPage,
  AppNotification,
  PerformanceSummary,
  PlanResponse,
  PlanSummary,
  PrioritiesResponse,
  Productivity,
  Profile,
  ReadinessOverviewItem,
  ReadinessResponse,
  Recommendation,
  RegisterRequest,
  RevisionDueResponse,
  RevisionLogEntry,
  RevisionResult,
  SessionActionResponse,
  StudySession,
  Subject,
  SubjectAnalytics,
  SubjectRequest,
  TodayResponse,
  Topic,
  TopicHistory,
  TopicRequest,
  TopicStatus,
  UpdateProfileRequest,
  UpdateSessionRequest,
  WeakTopicResponse,
  WeeklyAnalytics,
} from '../types/api';

// One function per backend endpoint. Paths are relative to /api/v1.

export const healthApi = {
  check: () => http.get<HealthResponse>('/health'),
};

export const authApi = {
  me: () => http.get<Profile>('/auth/me'),
  login: async (body: LoginRequest) => {
    const profile = await http.post<Profile>('/auth/login', body);
    resetCsrfToken();
    return profile;
  },
  register: async (body: RegisterRequest) => {
    const profile = await http.post<Profile>('/auth/register', body);
    resetCsrfToken();
    return profile;
  },
  logout: async () => {
    try {
      await http.post<void>('/auth/logout');
    } finally {
      resetCsrfToken();
    }
  },
};

export const profileApi = {
  get: () => http.get<Profile>('/users/me'),
  update: (body: UpdateProfileRequest) => http.put<Profile>('/users/me', body),
  changePassword: (body: ChangePasswordRequest) => http.put<void>('/users/me/password', body),
};

export const availabilityApi = {
  get: () => http.get<AvailabilityResponse>('/availability'),
  replace: (body: AvailabilityRequest) => http.put<AvailabilityResponse>('/availability', body),
};

export const subjectApi = {
  list: () => http.get<Subject[]>('/subjects'),
  get: (id: number) => http.get<Subject>(`/subjects/${id}`),
  create: (body: SubjectRequest) => http.post<Subject>('/subjects', body),
  update: (id: number, body: SubjectRequest) => http.put<Subject>(`/subjects/${id}`, body),
  remove: (id: number) => http.delete(`/subjects/${id}`),
};

export const topicApi = {
  listBySubject: (subjectId: number) => http.get<Topic[]>(`/subjects/${subjectId}/topics`),
  listAll: () => http.get<Topic[]>('/topics'),
  get: (id: number) => http.get<Topic>(`/topics/${id}`),
  create: (subjectId: number, body: TopicRequest) => http.post<Topic>(`/subjects/${subjectId}/topics`, body),
  update: (id: number, body: TopicRequest) => http.put<Topic>(`/topics/${id}`, body),
  setStatus: (id: number, status: TopicStatus) => http.patch<Topic>(`/topics/${id}/status`, { status }),
  remove: (id: number) => http.delete(`/topics/${id}`),
};

export const examApi = {
  list: (upcoming = false) => http.get<Exam[]>(`/exams?upcoming=${upcoming}`),
  get: (id: number) => http.get<Exam>(`/exams/${id}`),
  create: (body: ExamRequest) => http.post<Exam>('/exams', body),
  update: (id: number, body: ExamRequest) => http.put<Exam>(`/exams/${id}`, body),
  remove: (id: number) => http.delete(`/exams/${id}`),
  readiness: (id: number) => http.get<ReadinessResponse>(`/exams/${id}/readiness`),
  readinessOverview: () => http.get<ReadinessOverviewItem[]>('/readiness'),
};

export const plannerApi = {
  today: () => http.get<TodayResponse>('/planner/today'),
  weekly: (date?: string) => http.get<CalendarResponse>(`/planner/weekly${date ? `?date=${date}` : ''}`),
  calendar: (from: string, to: string) => http.get<CalendarResponse>(`/planner/calendar?from=${from}&to=${to}`),
  recommendations: (limit = 5) => http.get<Recommendation[]>(`/planner/recommendations?limit=${limit}`),
  priorities: () => http.get<PrioritiesResponse>('/planner/priorities'),
  active: () => http.get<PlanSummary | undefined>('/planner/active'),
  generate: (body: GeneratePlanRequest) => http.post<PlanResponse>('/planner/generate', body),
  regenerate: () => http.post<PlanResponse>('/planner/regenerate'),
};

export const sessionApi = {
  get: (id: number) => http.get<StudySession>(`/sessions/${id}`),
  create: (body: CreateSessionRequest) => http.post<StudySession>('/sessions', body),
  update: (id: number, body: UpdateSessionRequest) => http.put<StudySession>(`/sessions/${id}`, body),
  cancel: (id: number) => http.delete(`/sessions/${id}`),
  lock: (id: number, locked: boolean) => http.patch<StudySession>(`/sessions/${id}/lock`, { locked }),
  start: (id: number) => http.post<StudySession>(`/sessions/${id}/start`),
  complete: (id: number, body: CompleteSessionRequest) =>
    http.post<SessionActionResponse>(`/sessions/${id}/complete`, body),
  miss: (id: number) => http.post<SessionActionResponse>(`/sessions/${id}/miss`),
};

export const assessmentApi = {
  list: (subjectId?: number) => http.get<Assessment[]>(`/assessments${subjectId ? `?subjectId=${subjectId}` : ''}`),
  create: (body: AssessmentRequest) => http.post<Assessment>('/assessments', body),
  remove: (id: number) => http.delete(`/assessments/${id}`),
};

export const performanceApi = {
  weakTopics: () => http.get<WeakTopicResponse>('/performance/weak-topics'),
  summary: () => http.get<PerformanceSummary>('/performance/summary'),
  topicHistory: (topicId: number) => http.get<TopicHistory>(`/performance/topics/${topicId}/history`),
};

export const revisionApi = {
  due: () => http.get<RevisionDueResponse>('/revisions/due'),
  complete: (topicId: number, body: CompleteRevisionRequest) =>
    http.post<RevisionResult>(`/revisions/${topicId}/complete`, body),
  history: (topicId?: number, limit = 50) =>
    http.get<RevisionLogEntry[]>(`/revisions/history?limit=${limit}${topicId ? `&topicId=${topicId}` : ''}`),
};

export const focusApi = {
  start: (body: FocusStartRequest) => http.post<FocusSession>('/focus/start', body),
  pause: (id: number) => http.post<FocusSession>(`/focus/${id}/pause`),
  resume: (id: number) => http.post<FocusSession>(`/focus/${id}/resume`),
  complete: (id: number, clientReportedSeconds: number | null, notes?: string) =>
    http.post<FocusCompleteResponse>(`/focus/${id}/complete`, { clientReportedSeconds, notes: notes ?? null }),
  cancel: (id: number) => http.post<FocusSession>(`/focus/${id}/cancel`),
  active: () => http.get<FocusSession | undefined>('/focus/active'),
  history: (days = 30) => http.get<FocusSession[]>(`/focus/history?days=${days}`),
  stats: () => http.get<FocusStats>('/focus/stats'),
};

export const analyticsApi = {
  overview: () => http.get<DashboardOverview>('/analytics/overview'),
  weekly: (date?: string) => http.get<WeeklyAnalytics>(`/analytics/weekly${date ? `?date=${date}` : ''}`),
  subjects: () => http.get<SubjectAnalytics[]>('/analytics/subjects'),
  productivity: (days = 30) => http.get<Productivity>(`/analytics/productivity?days=${days}`),
};

export const notificationApi = {
  list: (unreadOnly = false, limit = 20) =>
    http.get<NotificationPage>(`/notifications?unreadOnly=${unreadOnly}&limit=${limit}`),
  markRead: (id: number) => http.post<AppNotification>(`/notifications/${id}/read`),
  markAllRead: () => http.post<{ updated: number }>('/notifications/read-all'),
};
