// TypeScript mirror of the backend DTOs (com.smartstudy.*.*Request / *Response).
// Field names match the Java records exactly. Dates: "YYYY-MM-DD"; times: "HH:mm" or "HH:mm:ss";
// instants: ISO-8601 UTC strings such as "2026-10-31T06:00:00Z".

export type IsoDate = string;
export type IsoTime = string;
export type IsoInstant = string;

// ------------------------------------------------------------------ common
export interface FieldErrorDetail {
  field: string;
  message: string;
}

export interface ApiErrorBody {
  timestamp: IsoInstant;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
  fieldErrors: FieldErrorDetail[];
}

export interface HealthResponse {
  status: 'UP' | 'DOWN';
  application: string;
  version: string;
  timestamp: IsoInstant;
  database: {
    status: 'UP' | 'DOWN';
    product: string | null;
    productVersion: string | null;
    databaseName: string | null;
    schemaVersion: string | null;
    message: string | null;
  };
}

// ------------------------------------------------------------------ auth & profile
export interface CsrfTokenResponse {
  headerName: string;
  parameterName: string;
  token: string;
}

export interface Profile {
  id: number;
  fullName: string;
  email: string;
  role: string;
  timeZone: string;
  dailyStudyMinutes: number;
  weeklyStudyMinutes: number;
  preferredSessionMinutes: number;
  breakMinutes: number;
  focusMinutes: number;
  shortBreakMinutes: number;
  longBreakMinutes: number;
  longBreakInterval: number;
  createdAt: IsoInstant;
}

export interface RegisterRequest {
  fullName: string;
  email: string;
  password: string;
  timeZone: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface UpdateProfileRequest {
  fullName: string;
  timeZone: string;
  focusMinutes: number;
  shortBreakMinutes: number;
  longBreakMinutes: number;
  longBreakInterval: number;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface AvailabilityWindowRequest {
  dayOfWeek: number;
  startTime: IsoTime;
  endTime: IsoTime;
}

export interface AvailabilityRequest {
  dailyStudyMinutes: number;
  weeklyStudyMinutes: number;
  preferredSessionMinutes: number;
  breakMinutes: number;
  windows: AvailabilityWindowRequest[];
}

export interface AvailabilityResponse {
  dailyStudyMinutes: number;
  weeklyStudyMinutes: number;
  preferredSessionMinutes: number;
  breakMinutes: number;
  totalWeeklyAvailableMinutes: number;
  windows: { id: number; dayOfWeek: number; startTime: IsoTime; endTime: IsoTime; minutes: number }[];
}

// ------------------------------------------------------------------ subjects, topics, exams
export interface SubjectRequest {
  name: string;
  code: string | null;
  color: string;
  importance: number;
  targetScore: number | null;
  description: string | null;
  archived: boolean | null;
}

export interface Subject {
  id: number;
  name: string;
  code: string | null;
  color: string;
  importance: number;
  targetScore: number | null;
  description: string | null;
  archived: boolean;
  topicCount: number;
  completedTopicCount: number;
  syllabusCompletion: number;
  averageMastery: number | null;
  assessedTopicCount: number;
  weakTopicCount: number;
  nextExamId: number | null;
  nextExamTitle: string | null;
  nextExamDate: IsoDate | null;
  daysUntilNextExam: number | null;
  createdAt: IsoInstant;
}

export type TopicStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED';
export type MasteryLevel = 'WEAK' | 'NEEDS_IMPROVEMENT' | 'GOOD' | 'STRONG' | 'NOT_ASSESSED';

export interface TopicRequest {
  name: string;
  description: string | null;
  difficulty: number;
  estimatedMinutes: number;
  status: TopicStatus | null;
}

export interface Topic {
  id: number;
  subjectId: number;
  subjectName: string;
  subjectColor: string;
  name: string;
  description: string | null;
  difficulty: number;
  estimatedMinutes: number;
  studiedMinutes: number;
  status: TopicStatus;
  completedAt: IsoInstant | null;
  sortOrder: number;
  masteryScore: number | null;
  masteryLevel: MasteryLevel;
  assessmentCount: number;
  nextRevisionDate: IsoDate | null;
  lastRevisedOn: IsoDate | null;
  revisionIntervalDays: number;
  revisionRepetitions: number;
  easeFactor: number;
  createdAt: IsoInstant;
}

export type ExamType = 'MIDTERM' | 'FINAL' | 'QUIZ' | 'PRACTICAL' | 'ENTRANCE' | 'OTHER';

export interface ExamRequest {
  subjectId: number;
  title: string;
  examType: ExamType;
  examDate: IsoDate;
  startTime: IsoTime | null;
  durationMinutes: number | null;
  location: string | null;
  targetScore: number | null;
  notes: string | null;
}

export interface Exam {
  id: number;
  subjectId: number;
  subjectName: string;
  subjectColor: string;
  title: string;
  examType: ExamType;
  examDate: IsoDate;
  startTime: IsoTime | null;
  durationMinutes: number | null;
  location: string | null;
  targetScore: number | null;
  notes: string | null;
  daysRemaining: number;
  upcoming: boolean;
  createdAt: IsoInstant;
}

// ------------------------------------------------------------------ planner
export type SessionType = 'STUDY' | 'REVISION' | 'PRACTICE';
export type SessionSource = 'GENERATED' | 'MANUAL';
export type SessionStatus = 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'INCOMPLETE' | 'MISSED' | 'CANCELLED';
export type RevisionRating = 'AGAIN' | 'HARD' | 'GOOD' | 'EASY';

export interface StudySession {
  id: number;
  planId: number | null;
  subjectId: number;
  subjectName: string;
  subjectColor: string;
  topicId: number | null;
  topicName: string | null;
  sessionType: SessionType;
  source: SessionSource;
  status: SessionStatus;
  startAt: IsoInstant;
  endAt: IsoInstant;
  localDate: IsoDate;
  localStartTime: IsoTime;
  localEndTime: IsoTime;
  plannedMinutes: number;
  actualMinutes: number | null;
  startedAt: IsoInstant | null;
  completedAt: IsoInstant | null;
  locked: boolean;
  priorityScore: number | null;
  reason: string | null;
  notes: string | null;
  rescheduledFromId: number | null;
}

export interface UnscheduledItem {
  topicId: number;
  topicName: string;
  subjectName: string;
  sessionType: SessionType;
  remainingMinutes: number;
  reason: string;
}

export interface PlanResponse {
  planId: number;
  startDate: IsoDate;
  endDate: IsoDate;
  generatedAt: IsoInstant;
  totalPlannedMinutes: number;
  sessionCount: number;
  strategySummary: string;
  sessions: StudySession[];
  unscheduled: UnscheduledItem[];
  warnings: string[];
}

export interface PlanSummary {
  planId: number;
  startDate: IsoDate;
  endDate: IsoDate;
  generatedAt: IsoInstant;
  totalPlannedMinutes: number;
  sessionCount: number;
  strategySummary: string | null;
  status: 'ACTIVE' | 'ARCHIVED';
}

export interface GeneratePlanRequest {
  startDate?: IsoDate | null;
  days?: number | null;
}

export interface DayPlan {
  date: IsoDate;
  plannedMinutes: number;
  completedMinutes: number;
  sessions: StudySession[];
}

export interface CalendarResponse {
  from: IsoDate;
  to: IsoDate;
  timeZone: string;
  totalPlannedMinutes: number;
  totalCompletedMinutes: number;
  days: DayPlan[];
}

export interface PriorityFactors {
  weakness: number;
  examUrgency: number;
  difficulty: number;
  revisionOverdue: number;
  subjectImportance: number;
}

export interface Recommendation {
  topicId: number;
  topicName: string;
  subjectId: number;
  subjectName: string;
  subjectColor: string;
  sessionType: SessionType;
  priority: number;
  factors: PriorityFactors;
  reasons: string[];
  masteryScore: number | null;
  masteryLevel: MasteryLevel;
  daysUntilExam: number | null;
  nextRevisionDate: IsoDate | null;
  remainingMinutes: number;
}

export interface PrioritiesResponse {
  weights: PriorityFactors;
  items: Recommendation[];
}

export interface TodayResponse {
  date: IsoDate;
  timeZone: string;
  plannedMinutes: number;
  completedMinutes: number;
  completionPercent: number;
  sessionsPlanned: number;
  sessionsCompleted: number;
  sessionsMissed: number;
  sessions: StudySession[];
  recommendations: Recommendation[];
  activePlan: PlanSummary | null;
}

export interface CreateSessionRequest {
  subjectId: number | null;
  topicId: number | null;
  sessionType: SessionType;
  date: IsoDate;
  startTime: IsoTime;
  durationMinutes: number;
  notes: string | null;
  locked: boolean;
}

export interface UpdateSessionRequest {
  date: IsoDate;
  startTime: IsoTime;
  durationMinutes: number;
  notes: string | null;
}

export interface CompleteSessionRequest {
  actualMinutes?: number | null;
  topicCompleted?: boolean | null;
  revisionRating?: RevisionRating | null;
  notes?: string | null;
}

export interface SessionActionResponse {
  session: StudySession;
  rescheduledTo: StudySession | null;
  message: string;
}

// ------------------------------------------------------------------ assessments & performance
export type AssessmentType = 'EXAM' | 'QUIZ' | 'ASSIGNMENT' | 'MOCK_TEST';

export interface AssessmentRequest {
  subjectId: number;
  examId: number | null;
  title: string;
  assessmentType: AssessmentType;
  assessedOn: IsoDate;
  maxMarks: number;
  obtainedMarks: number;
  notes: string | null;
  topicScores: { topicId: number; maxMarks: number; obtainedMarks: number }[];
}

export interface Assessment {
  id: number;
  subjectId: number;
  subjectName: string;
  subjectColor: string;
  examId: number | null;
  examTitle: string | null;
  title: string;
  assessmentType: AssessmentType;
  assessedOn: IsoDate;
  maxMarks: number;
  obtainedMarks: number;
  scorePercent: number;
  notes: string | null;
  topicScores: { topicId: number; topicName: string; maxMarks: number; obtainedMarks: number; scorePercent: number }[];
  createdAt: IsoInstant;
}

export type Trend = 'IMPROVING' | 'DECLINING' | 'STABLE' | 'NEW';

export interface WeakTopicItem {
  topicId: number;
  topicName: string;
  subjectId: number;
  subjectName: string;
  subjectColor: string;
  masteryScore: number;
  masteryLevel: MasteryLevel;
  latestScore: number;
  historicalAverage: number;
  previousMasteryScore: number | null;
  trend: Trend;
  assessmentCount: number;
  lastAssessedOn: IsoDate;
  topicStatus: TopicStatus;
  recommendation: string;
}

export interface WeakTopicResponse {
  totalTopics: number;
  assessedTopics: number;
  notAssessedTopics: number;
  items: WeakTopicItem[];
}

export interface PerformanceSummary {
  overallMastery: number | null;
  levelCounts: Record<MasteryLevel, number>;
  subjects: {
    subjectId: number;
    subjectName: string;
    subjectColor: string;
    averageMastery: number | null;
    assessedTopics: number;
    totalTopics: number;
    weakTopics: number;
    averageAssessmentScore: number | null;
    assessmentCount: number;
  }[];
}

export interface TopicHistory {
  topicId: number;
  topicName: string;
  subjectName: string;
  currentMastery: number | null;
  masteryLevel: MasteryLevel;
  points: {
    assessmentId: number;
    title: string;
    assessmentType: AssessmentType;
    assessedOn: IsoDate;
    scorePercent: number;
    masteryAfter: number;
  }[];
}

// ------------------------------------------------------------------ revision
export interface RevisionItem {
  topicId: number;
  topicName: string;
  subjectId: number;
  subjectName: string;
  subjectColor: string;
  nextRevisionDate: IsoDate;
  daysOverdue: number;
  dueStatus: 'OVERDUE' | 'DUE_TODAY' | 'UPCOMING';
  intervalDays: number;
  repetitions: number;
  easeFactor: number;
  lastRevisedOn: IsoDate | null;
  masteryScore: number | null;
  masteryLevel: MasteryLevel;
}

export interface RevisionDueResponse {
  today: IsoDate;
  dueCount: number;
  overdueCount: number;
  upcomingCount: number;
  completedThisWeek: number;
  overdue: RevisionItem[];
  dueToday: RevisionItem[];
  upcoming: RevisionItem[];
}

export interface CompleteRevisionRequest {
  rating: RevisionRating;
  durationMinutes?: number | null;
  studySessionId?: number | null;
  notes?: string | null;
}

export interface RevisionResult {
  topicId: number;
  topicName: string;
  rating: RevisionRating;
  revisedOn: IsoDate;
  previousIntervalDays: number;
  newIntervalDays: number;
  previousEaseFactor: number;
  newEaseFactor: number;
  repetitions: number;
  nextRevisionDate: IsoDate;
  explanation: string;
  completedSessionId: number | null;
}

export interface RevisionLogEntry {
  id: number;
  topicId: number;
  topicName: string;
  subjectName: string;
  subjectColor: string;
  rating: RevisionRating;
  revisedOn: IsoDate;
  dueDate: IsoDate | null;
  onTime: boolean;
  durationMinutes: number | null;
  previousIntervalDays: number;
  newIntervalDays: number;
  previousEaseFactor: number;
  newEaseFactor: number;
  repetitions: number;
  nextRevisionDate: IsoDate;
  revisedAt: IsoInstant;
}

// ------------------------------------------------------------------ readiness
export type ReadinessLevel = 'WELL_PREPARED' | 'ON_TRACK' | 'NEEDS_WORK' | 'AT_RISK';
export type DataSufficiency = 'SUFFICIENT' | 'PARTIAL' | 'INSUFFICIENT';

export interface ReadinessResponse {
  examId: number;
  examTitle: string;
  subjectId: number;
  subjectName: string;
  subjectColor: string;
  examDate: IsoDate;
  daysRemaining: number;
  readinessScore: number | null;
  level: ReadinessLevel | null;
  dataConfidence: number;
  dataSufficiency: DataSufficiency;
  components: { key: string; label: string; score: number | null; weight: number; available: boolean; detail: string }[];
  pendingTopics: { topicId: number; name: string; status: TopicStatus; difficulty: number; masteryScore: number | null }[];
  focusAreas: { type: string; title: string; detail: string; topicId: number | null }[];
  history: { date: IsoDate; score: number | null }[];
  calculatedOn: IsoDate;
  disclaimer: string;
}

export interface ReadinessOverviewItem {
  examId: number;
  examTitle: string;
  subjectId: number;
  subjectName: string;
  subjectColor: string;
  examDate: IsoDate;
  daysRemaining: number;
  readinessScore: number | null;
  level: ReadinessLevel | null;
  dataSufficiency: DataSufficiency;
}

// ------------------------------------------------------------------ focus
export type FocusStatus = 'RUNNING' | 'PAUSED' | 'COMPLETED' | 'CANCELLED';

export interface FocusStartRequest {
  subjectId?: number | null;
  topicId?: number | null;
  studySessionId?: number | null;
  plannedMinutes?: number | null;
}

export interface FocusSession {
  id: number;
  status: FocusStatus;
  subjectId: number | null;
  subjectName: string | null;
  subjectColor: string | null;
  topicId: number | null;
  topicName: string | null;
  studySessionId: number | null;
  plannedMinutes: number;
  startedAt: IsoInstant;
  pausedAt: IsoInstant | null;
  totalPausedSeconds: number;
  pauseCount: number;
  endedAt: IsoInstant | null;
  actualFocusSeconds: number | null;
  elapsedFocusSeconds: number;
  remainingSeconds: number;
  notes: string | null;
  serverTime: IsoInstant;
}

export interface FocusCompleteResponse {
  session: FocusSession;
  completedToday: number;
  nextBreak: 'SHORT' | 'LONG';
  breakMinutes: number;
}

export interface FocusStats {
  todayFocusMinutes: number;
  todayCompletedSessions: number;
  weekFocusMinutes: number;
  last30DaysSessions: number;
  last30DaysFocusMinutes: number;
  averageSessionMinutes: number;
  completedInCurrentCycle: number;
  longBreakInterval: number;
  nextBreak: 'SHORT' | 'LONG';
  focusMinutes: number;
  shortBreakMinutes: number;
  longBreakMinutes: number;
  daily: { date: IsoDate; minutes: number; sessions: number }[];
  bySubject: { subjectId: number; subjectName: string; subjectColor: string; minutes: number }[];
}

// ------------------------------------------------------------------ analytics
export interface DailyPoint {
  date: IsoDate;
  label: string;
  plannedMinutes: number;
  actualMinutes: number;
  focusMinutes: number;
  completedSessions: number;
  missedSessions: number;
}

export interface DashboardOverview {
  date: IsoDate;
  timeZone: string;
  today: {
    plannedMinutes: number;
    completedMinutes: number;
    completionPercent: number;
    sessionsPlanned: number;
    sessionsCompleted: number;
    sessionsMissed: number;
  };
  week: { plannedMinutes: number; actualMinutes: number; focusMinutes: number; completedSessions: number; missedSessions: number };
  streakDays: number;
  consistencyPercent: number;
  upcomingExams: {
    examId: number;
    title: string;
    subjectName: string;
    subjectColor: string;
    examDate: IsoDate;
    daysRemaining: number;
    readinessScore: number | null;
    readinessLevel: ReadinessLevel | null;
    dataSufficiency: DataSufficiency;
  }[];
  weakTopics: { topicId: number; topicName: string; subjectName: string; subjectColor: string; masteryScore: number; masteryLevel: MasteryLevel }[];
  revisions: {
    dueCount: number;
    overdueCount: number;
    items: { topicId: number; topicName: string; subjectName: string; nextRevisionDate: IsoDate; daysOverdue: number }[];
  };
  weeklyProductivity: DailyPoint[];
  recentActivity: { type: string; title: string; detail: string; occurredAt: IsoInstant }[];
  unreadNotifications: number;
}

export interface WeeklyAnalytics {
  weekStart: IsoDate;
  weekEnd: IsoDate;
  plannedMinutes: number;
  actualMinutes: number;
  focusMinutes: number;
  completedSessions: number;
  missedSessions: number;
  completionRate: number;
  days: DailyPoint[];
  bySubject: { subjectId: number; subjectName: string; subjectColor: string; plannedMinutes: number; actualMinutes: number }[];
}

export interface SubjectAnalytics {
  subjectId: number;
  subjectName: string;
  subjectColor: string;
  totalTopics: number;
  completedTopics: number;
  syllabusCompletion: number;
  averageMastery: number | null;
  weakTopics: number;
  studiedMinutesTotal: number;
  studiedMinutesLast30Days: number;
  plannedMinutesNext7Days: number;
  assessmentAverage: number | null;
  lastStudiedOn: IsoDate | null;
}

export interface Productivity {
  days: number;
  totalPlannedMinutes: number;
  totalActualMinutes: number;
  plannedVsActualPercent: number;
  consistencyPercent: number;
  activeDays: number;
  currentStreak: number;
  longestStreak: number;
  sessions: { completed: number; incomplete: number; missed: number; completionRate: number };
  revisions: { completed: number; onTime: number; onTimeRate: number; overdueNow: number };
  focus: { sessions: number; totalMinutes: number; averageMinutes: number };
  daily: DailyPoint[];
  monthly: { month: string; actualMinutes: number; focusMinutes: number }[];
  weakSubjects: {
    subjectId: number;
    subjectName: string;
    subjectColor: string;
    averageMastery: number | null;
    weakTopics: number;
    assessedTopics: number;
    totalTopics: number;
  }[];
  readinessTrends: {
    examId: number;
    examTitle: string;
    subjectName: string;
    subjectColor: string;
    examDate: IsoDate;
    points: { date: IsoDate; score: number | null }[];
  }[];
}

// ------------------------------------------------------------------ notifications
export type NotificationType =
  | 'REVISION_DUE'
  | 'SESSION_MISSED'
  | 'SESSION_RESCHEDULED'
  | 'EXAM_UPCOMING'
  | 'PLAN_GENERATED'
  | 'SYSTEM';

export interface AppNotification {
  id: number;
  type: NotificationType;
  title: string;
  message: string;
  link: string | null;
  read: boolean;
  createdAt: IsoInstant;
}

export interface NotificationPage {
  unreadCount: number;
  items: AppNotification[];
}
