/**
 * In-browser demo backend for the SmartStudy UI preview.
 *
 * It intercepts window.fetch for /api/v1/* and answers with the same JSON shapes as the real
 * Spring Boot API (typed against src/types/api.ts), using the same formulas: weighted priority,
 * greedy timetable, 80 % completion rule, mastery, SM-2 and readiness. State lives in memory
 * only and resets when the page reloads.
 */
import type {
  Assessment, AssessmentRequest, AssessmentType, AvailabilityRequest, AvailabilityResponse, CalendarResponse,
  CompleteRevisionRequest, CompleteSessionRequest, CreateSessionRequest, DailyPoint, DashboardOverview, DataSufficiency, Exam,
  ExamRequest, ExamType, FocusCompleteResponse, FocusSession, FocusStartRequest, FocusStats, MasteryLevel, AppNotification,
  NotificationType, PerformanceSummary, PlanResponse, PlanSummary, PrioritiesResponse, Productivity, Profile, ReadinessLevel,
  ReadinessOverviewItem, ReadinessResponse, Recommendation, RevisionDueResponse, RevisionItem, RevisionLogEntry,
  RevisionRating, RevisionResult, SessionActionResponse, SessionStatus, SessionType, StudySession, Subject,
  SubjectAnalytics, SubjectRequest, Topic, TopicHistory, TopicRequest, TopicStatus, Trend, UnscheduledItem,
  UpdateProfileRequest, UpdateSessionRequest, WeakTopicResponse, WeeklyAnalytics,
} from '../types/api';

// ------------------------------------------------------------------ time helpers

const MIN = 60_000;
const DAY_MS = 86_400_000;
const browserZone = (() => {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
  } catch {
    return 'UTC';
  }
})();
const formatters = new Map<string, Intl.DateTimeFormat>();
function parts(ms: number, tz: string): { date: string; time: string } {
  let f = formatters.get(tz);
  if (!f) {
    f = new Intl.DateTimeFormat('en-CA', { timeZone: tz, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' });
    formatters.set(tz, f);
  }
  const p: Record<string, string> = {};
  for (const x of f.formatToParts(new Date(ms))) p[x.type] = x.value;
  return { date: `${p.year}-${p.month}-${p.day}`, time: `${p.hour}:${p.minute}:${p.second}` };
}
function offsetOf(ms: number, tz: string) {
  const p = parts(ms, tz);
  return Date.parse(`${p.date}T${p.time}Z`) - Math.floor(ms / 1000) * 1000;
}
function toInstant(date: string, hhmm: string, tz: string) {
  const guess = Date.parse(`${date}T${hhmm.slice(0, 5)}:00Z`);
  const first = guess - offsetOf(guess, tz);
  return guess - offsetOf(first, tz);
}
const iso = (ms: number) => new Date(ms).toISOString();
function addDays(date: string, n: number) {
  const d = new Date(`${date}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
}
const diffDays = (from: string, to: string) => Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / DAY_MS);
const isoDow = (date: string) => ((new Date(`${date}T00:00:00Z`).getUTCDay() + 6) % 7) + 1;
const weekStartOf = (date: string) => addDays(date, 1 - isoDow(date));
const round1 = (n: number) => Math.round(n * 10) / 10;
const round2 = (n: number) => Math.round(n * 100) / 100;
const clamp = (n: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, n));
const hhmm = (minutes: number) => `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`;
const minutesOf = (time: string) => Number(time.slice(0, 2)) * 60 + Number(time.slice(3, 5));
const DAY_LABELS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

// ------------------------------------------------------------------ state

interface SubjectRec { id: number; name: string; code: string | null; color: string; importance: number; targetScore: number | null; description: string | null; archived: boolean; createdAt: number }
interface MasteryRec { score: number; latest: number; average: number; previous: number | null; level: MasteryLevel; trend: Trend; count: number; lastOn: string }
interface TopicRec {
  id: number; subjectId: number; name: string; description: string | null; difficulty: number; estimatedMinutes: number; status: TopicStatus;
  completedAt: number | null; sortOrder: number; mastery: MasteryRec | null; ef: number; interval: number; reps: number;
  nextRevisionDate: string | null; lastRevisedOn: string | null; createdAt: number;
}
interface ExamRec { id: number; subjectId: number; title: string; examType: ExamType; examDate: string; startTime: string | null; durationMinutes: number | null; location: string | null; targetScore: number | null; notes: string | null; createdAt: number }
interface SessionRec {
  id: number; planId: number | null; subjectId: number; topicId: number | null; sessionType: SessionType; source: 'GENERATED' | 'MANUAL'; status: SessionStatus;
  startAt: number; endAt: number; plannedMinutes: number; actualMinutes: number | null; startedAt: number | null; completedAt: number | null;
  locked: boolean; priorityScore: number | null; reason: string | null; notes: string | null; rescheduledFromId: number | null;
}
interface AssessmentRec { id: number; subjectId: number; examId: number | null; title: string; assessmentType: AssessmentType; assessedOn: string; maxMarks: number; obtainedMarks: number; notes: string | null; topicScores: { topicId: number; maxMarks: number; obtainedMarks: number }[]; createdAt: number }
interface RevisionLogRec { id: number; topicId: number; rating: RevisionRating; revisedOn: string; dueDate: string | null; durationMinutes: number | null; prevInterval: number; newInterval: number; prevEf: number; newEf: number; reps: number; next: string; at: number }
interface FocusRec { id: number; status: FocusSession['status']; subjectId: number | null; topicId: number | null; studySessionId: number | null; plannedMinutes: number; startedAt: number; pausedAt: number | null; pausedSeconds: number; pauseCount: number; endedAt: number | null; actualSeconds: number | null; notes: string | null }

const DEMO_EMAIL = 'demo@smartstudy.dev';
let password = 'Demo@12345';
let loggedIn = false;
let nextId = 1000;
const newId = () => ++nextId;

const profile: Profile = {
  id: 1, fullName: 'Demo Student', email: DEMO_EMAIL, role: 'STUDENT', timeZone: browserZone,
  dailyStudyMinutes: 180, weeklyStudyMinutes: 900, preferredSessionMinutes: 50, breakMinutes: 10,
  focusMinutes: 25, shortBreakMinutes: 5, longBreakMinutes: 15, longBreakInterval: 4,
  createdAt: iso(Date.now() - 45 * DAY_MS),
};
let windows: { id: number; dayOfWeek: number; startTime: string; endTime: string }[] = [];
const subjects: SubjectRec[] = [];
const topics: TopicRec[] = [];
const exams: ExamRec[] = [];
let sessions: SessionRec[] = [];
const assessments: AssessmentRec[] = [];
const revisionLogs: RevisionLogRec[] = [];
const focus: FocusRec[] = [];
const notifications: AppNotification[] = [];
const snapshots = new Map<number, Map<string, number | null>>();
let plan: PlanSummary | null = null;

const tz = () => profile.timeZone;
const now = () => Date.now();
const today = () => parts(now(), tz()).date;
const localDate = (ms: number) => parts(ms, tz()).date;

// ------------------------------------------------------------------ errors

class HttpError extends Error {
  constructor(public status: number, public code: string, message: string, public fieldErrors: { field: string; message: string }[] = []) {
    super(message);
  }
}
const notFound = (what: string): never => {
  throw new HttpError(404, 'NOT_FOUND', `${what} not found.`);
};
const rule = (message: string): never => {
  throw new HttpError(422, 'BUSINESS_RULE', message);
};
const invalid = (field: string, message: string): never => {
  throw new HttpError(400, 'VALIDATION_FAILED', 'Please correct the highlighted fields.', [{ field, message }]);
};

// ------------------------------------------------------------------ lookups and views

const subjectOf = (id: number) => subjects.find((s) => s.id === id) ?? notFound('Subject');
const topicOf = (id: number) => topics.find((t) => t.id === id) ?? notFound('Topic');
const examOf = (id: number) => exams.find((e) => e.id === id) ?? notFound('Exam');
const sessionOf = (id: number) => sessions.find((s) => s.id === id) ?? notFound('Study session');
const focusOf = (id: number) => focus.find((f) => f.id === id) ?? notFound('Focus session');

function levelOf(score: number): MasteryLevel {
  if (score < 40) return 'WEAK';
  if (score < 65) return 'NEEDS_IMPROVEMENT';
  if (score < 80) return 'GOOD';
  return 'STRONG';
}
const studied = (topicId: number) =>
  sessions.filter((s) => s.topicId === topicId && s.sessionType !== 'REVISION' && (s.status === 'COMPLETED' || s.status === 'INCOMPLETE'))
    .reduce((sum, s) => sum + (s.actualMinutes ?? 0), 0);
const reserved = (topicId: number) =>
  sessions.filter((s) => s.topicId === topicId && s.sessionType === 'STUDY' && (s.status === 'PLANNED' || s.status === 'IN_PROGRESS'))
    .reduce((sum, s) => sum + s.plannedMinutes, 0);
const upcomingExam = (subjectId: number) =>
  exams.filter((e) => e.subjectId === subjectId && e.examDate >= today()).sort((a, b) => a.examDate.localeCompare(b.examDate))[0] ?? null;

function topicView(t: TopicRec): Topic {
  const s = subjectOf(t.subjectId);
  return {
    id: t.id, subjectId: s.id, subjectName: s.name, subjectColor: s.color, name: t.name, description: t.description,
    difficulty: t.difficulty, estimatedMinutes: t.estimatedMinutes, studiedMinutes: studied(t.id), status: t.status,
    completedAt: t.completedAt ? iso(t.completedAt) : null, sortOrder: t.sortOrder, masteryScore: t.mastery ? t.mastery.score : null,
    masteryLevel: t.mastery ? t.mastery.level : 'NOT_ASSESSED', assessmentCount: t.mastery?.count ?? 0, nextRevisionDate: t.nextRevisionDate,
    lastRevisedOn: t.lastRevisedOn, revisionIntervalDays: t.interval, revisionRepetitions: t.reps, easeFactor: t.ef, createdAt: iso(t.createdAt),
  };
}
function subjectView(s: SubjectRec): Subject {
  const ts = topics.filter((t) => t.subjectId === s.id);
  const assessed = ts.filter((t) => t.mastery);
  const done = ts.filter((t) => t.status === 'COMPLETED').length;
  const exam = upcomingExam(s.id);
  return {
    id: s.id, name: s.name, code: s.code, color: s.color, importance: s.importance, targetScore: s.targetScore, description: s.description,
    archived: s.archived, topicCount: ts.length, completedTopicCount: done, syllabusCompletion: ts.length ? round2((done * 100) / ts.length) : 0,
    averageMastery: assessed.length ? round2(assessed.reduce((a, t) => a + t.mastery!.score, 0) / assessed.length) : null,
    assessedTopicCount: assessed.length, weakTopicCount: assessed.filter((t) => t.mastery!.level === 'WEAK').length,
    nextExamId: exam?.id ?? null, nextExamTitle: exam?.title ?? null, nextExamDate: exam?.examDate ?? null,
    daysUntilNextExam: exam ? diffDays(today(), exam.examDate) : null, createdAt: iso(s.createdAt),
  };
}
function examView(e: ExamRec): Exam {
  const s = subjectOf(e.subjectId);
  const days = diffDays(today(), e.examDate);
  return {
    id: e.id, subjectId: s.id, subjectName: s.name, subjectColor: s.color, title: e.title, examType: e.examType, examDate: e.examDate,
    startTime: e.startTime, durationMinutes: e.durationMinutes, location: e.location, targetScore: e.targetScore, notes: e.notes,
    daysRemaining: days, upcoming: days >= 0, createdAt: iso(e.createdAt),
  };
}
function sessionView(x: SessionRec): StudySession {
  const s = subjectOf(x.subjectId);
  const t = x.topicId ? topics.find((y) => y.id === x.topicId) ?? null : null;
  const start = parts(x.startAt, tz());
  const end = parts(x.endAt, tz());
  return {
    id: x.id, planId: x.planId, subjectId: s.id, subjectName: s.name, subjectColor: s.color, topicId: t?.id ?? null, topicName: t?.name ?? null,
    sessionType: x.sessionType, source: x.source, status: x.status, startAt: iso(x.startAt), endAt: iso(x.endAt), localDate: start.date,
    localStartTime: start.time, localEndTime: end.time, plannedMinutes: x.plannedMinutes, actualMinutes: x.actualMinutes,
    startedAt: x.startedAt ? iso(x.startedAt) : null, completedAt: x.completedAt ? iso(x.completedAt) : null, locked: x.locked,
    priorityScore: x.priorityScore, reason: x.reason, notes: x.notes, rescheduledFromId: x.rescheduledFromId,
  };
}
function notify(type: NotificationType, title: string, message: string, link: string | null) {
  notifications.unshift({ id: newId(), type, title, message, link, read: false, createdAt: iso(now()) });
}

// ------------------------------------------------------------------ priority (same formula as PriorityCalculator)

const WEIGHTS = { weakness: 0.3, examUrgency: 0.25, difficulty: 0.2, revisionOverdue: 0.15, subjectImportance: 0.1 };
function recommendation(t: TopicRec): Recommendation | null {
  const s = subjectOf(t.subjectId);
  if (s.archived) return null;
  const day = today();
  const exam = upcomingExam(s.id);
  const daysToExam = exam ? diffDays(day, exam.examDate) : null;
  const revisionDue = t.status === 'COMPLETED' && t.nextRevisionDate !== null && t.nextRevisionDate <= day;
  const remaining = Math.max(0, t.estimatedMinutes - studied(t.id) - reserved(t.id));
  let sessionType: SessionType;
  if (t.status !== 'COMPLETED') {
    if (remaining < 10) return null;
    sessionType = 'STUDY';
  } else if (revisionDue) sessionType = 'REVISION';
  else if (t.mastery && t.mastery.level === 'WEAK') sessionType = 'PRACTICE';
  else return null;
  const overdue = revisionDue ? diffDays(t.nextRevisionDate!, day) : 0;
  const factors = {
    weakness: t.mastery ? round1(100 - t.mastery.score) : 50,
    examUrgency: daysToExam === null ? 0 : round1(daysToExam <= 0 ? 100 : daysToExam >= 30 ? 0 : (100 * (30 - daysToExam)) / 30),
    difficulty: (t.difficulty - 1) * 25,
    revisionOverdue: revisionDue ? round1(20 + (80 * Math.min(overdue, 14)) / 14) : 0,
    subjectImportance: (s.importance - 1) * 25,
  };
  const priority = round1(
    WEIGHTS.weakness * factors.weakness + WEIGHTS.examUrgency * factors.examUrgency + WEIGHTS.difficulty * factors.difficulty +
      WEIGHTS.revisionOverdue * factors.revisionOverdue + WEIGHTS.subjectImportance * factors.subjectImportance,
  );
  const reasons: [number, string][] = [
    [WEIGHTS.weakness * factors.weakness, t.mastery ? `${t.mastery.level === 'WEAK' ? 'Weak topic' : 'Mastery'} (mastery ${Math.round(t.mastery.score)}%)` : 'Not assessed yet (neutral weakness 50)'],
    [WEIGHTS.examUrgency * factors.examUrgency, daysToExam === null ? '' : daysToExam === 0 ? 'Exam today' : `Exam in ${daysToExam} day${daysToExam === 1 ? '' : 's'}`],
    [WEIGHTS.difficulty * factors.difficulty, t.difficulty >= 4 ? `Hard topic (difficulty ${t.difficulty}/5)` : `Difficulty ${t.difficulty}/5`],
    [WEIGHTS.revisionOverdue * factors.revisionOverdue, revisionDue ? (overdue === 0 ? 'Revision due today' : `Revision ${overdue} day${overdue === 1 ? '' : 's'} overdue`) : ''],
    [WEIGHTS.subjectImportance * factors.subjectImportance, s.importance >= 4 ? `Important subject (${s.importance}/5)` : ''],
  ];
  return {
    topicId: t.id, topicName: t.name, subjectId: s.id, subjectName: s.name, subjectColor: s.color, sessionType, priority, factors,
    reasons: reasons.filter(([v, r]) => r && v > 0).sort((a, b) => b[0] - a[0]).map(([, r]) => r),
    masteryScore: t.mastery ? t.mastery.score : null, masteryLevel: t.mastery ? t.mastery.level : 'NOT_ASSESSED',
    daysUntilExam: daysToExam, nextRevisionDate: t.nextRevisionDate,
    remainingMinutes: sessionType === 'STUDY' ? remaining : sessionType === 'REVISION' ? 30 : 45,
  };
}
const ranking = () => topics.map(recommendation).filter((r): r is Recommendation => r !== null).sort((a, b) => b.priority - a.priority);

// ------------------------------------------------------------------ timetable generation (greedy, like TimetableGenerator)

function markOverdueMissed(): SessionRec[] {
  const missed: SessionRec[] = [];
  for (const s of sessions) {
    if (s.status === 'PLANNED' && s.endAt + 30 * MIN < now()) {
      s.status = 'MISSED';
      missed.push(s);
    }
  }
  return missed;
}

function generate(startDate: string, days: number): PlanResponse {
  const t0 = now();
  // Replace only future, unlocked, planned sessions; everything else is kept.
  sessions = sessions.filter((s) => !(s.status === 'PLANNED' && !s.locked && s.startAt >= t0));
  const endDate = addDays(startDate, days - 1);
  const planId = newId();
  const warnings: string[] = [];
  if (windows.length === 0) warnings.push('You have no weekly availability yet - add study windows in Settings.');
  const kept = sessions.filter((s) => s.status !== 'CANCELLED' && s.status !== 'MISSED');
  const pref = profile.preferredSessionMinutes;
  const brk = profile.breakMinutes;

  interface Task { rec: Recommendation; minutes: number; earliest: string; deadline: number | null; given: number }
  const tasks: Task[] = ranking().map((rec) => {
    const t = topicOf(rec.topicId);
    const exam = upcomingExam(rec.subjectId);
    return {
      rec, minutes: rec.remainingMinutes, given: 0,
      earliest: rec.sessionType === 'REVISION' && t.nextRevisionDate ? t.nextRevisionDate : startDate,
      deadline: exam ? toInstant(exam.examDate, exam.startTime ?? '09:00', tz()) : null,
    };
  });
  // Revisions that become due inside the period
  for (const t of topics) {
    if (t.status === 'COMPLETED' && t.nextRevisionDate && t.nextRevisionDate > today() && t.nextRevisionDate <= endDate) {
      const base = recommendation({ ...t, nextRevisionDate: today() });
      if (base) tasks.push({ rec: { ...base, sessionType: 'REVISION', remainingMinutes: 30 }, minutes: 30, earliest: t.nextRevisionDate, deadline: null, given: 0 });
    }
  }

  const created: SessionRec[] = [];
  const weekUsed = new Map<string, number>();
  for (let d = 0; d < days; d++) {
    const date = addDays(startDate, d);
    const wk = weekStartOf(date);
    const keptToday = kept.filter((s) => localDate(s.startAt) === date);
    let dayUsed = keptToday.reduce((a, s) => a + s.plannedMinutes, 0);
    if (!weekUsed.has(wk)) {
      weekUsed.set(wk, kept.filter((s) => weekStartOf(localDate(s.startAt)) === wk).reduce((a, s) => a + s.plannedMinutes, 0));
    }
    const perTopicToday = new Map<number, number>();
    let lastSubject: number | null = null;
    const dayWindows = windows.filter((w) => w.dayOfWeek === isoDow(date)).sort((a, b) => a.startTime.localeCompare(b.startTime));
    for (const w of dayWindows) {
      let cursor = toInstant(date, w.startTime, tz());
      const windowEnd = toInstant(date, w.endTime, tz());
      const earliestStart = Math.ceil((t0 + 5 * MIN) / (5 * MIN)) * 5 * MIN;
      if (cursor < earliestStart) cursor = earliestStart;
      while (cursor + 25 * MIN <= windowEnd) {
        const clash = kept.concat(created).find((s) => s.startAt < cursor + pref * MIN + brk * MIN && cursor < s.endAt + brk * MIN);
        if (clash) {
          cursor = clash.endAt + brk * MIN;
          continue;
        }
        const room = Math.min(pref, (windowEnd - cursor) / MIN, profile.dailyStudyMinutes - dayUsed, profile.weeklyStudyMinutes - (weekUsed.get(wk) ?? 0));
        if (room < 25) break;
        let best: Task | null = null;
        let bestScore = -1;
        for (const task of tasks) {
          if (task.minutes < 10 || task.earliest > date) continue;
          if ((perTopicToday.get(task.rec.topicId) ?? 0) >= 2) continue;
          const len = Math.min(room, task.minutes);
          if (task.deadline !== null && cursor + len * MIN > task.deadline) continue;
          const score = task.rec.priority * Math.pow(0.8, task.given) * (task.rec.subjectId === lastSubject ? 0.9 : 1);
          if (score > bestScore) {
            bestScore = score;
            best = task;
          }
        }
        if (!best) break;
        const len = Math.max(10, Math.min(room, best.minutes));
        const rec: SessionRec = {
          id: newId(), planId, subjectId: best.rec.subjectId, topicId: best.rec.topicId, sessionType: best.rec.sessionType, source: 'GENERATED',
          status: 'PLANNED', startAt: cursor, endAt: cursor + len * MIN, plannedMinutes: Math.round(len), actualMinutes: null, startedAt: null,
          completedAt: null, locked: false, priorityScore: best.rec.priority, reason: best.rec.reasons.slice(0, 3).join('; '), notes: null, rescheduledFromId: null,
        };
        created.push(rec);
        best.minutes -= len;
        best.given += 1;
        perTopicToday.set(best.rec.topicId, (perTopicToday.get(best.rec.topicId) ?? 0) + 1);
        lastSubject = best.rec.subjectId;
        dayUsed += len;
        weekUsed.set(wk, (weekUsed.get(wk) ?? 0) + len);
        cursor += (len + brk) * MIN;
      }
    }
  }
  sessions.push(...created);
  const unscheduled: UnscheduledItem[] = tasks
    .filter((t) => t.minutes >= 10 && t.rec.sessionType === 'STUDY')
    .map((t) => ({
      topicId: t.rec.topicId, topicName: t.rec.topicName, subjectName: t.rec.subjectName, sessionType: t.rec.sessionType, remainingMinutes: Math.round(t.minutes),
      reason: t.deadline !== null && t.deadline < toInstant(endDate, '23:59', tz())
        ? 'Not enough free time before the exam - add availability or raise your daily limit.'
        : 'Did not fit into this planning period; it will be planned in the next timetable.',
    }));
  const total = created.reduce((a, s) => a + s.plannedMinutes, 0);
  const focusNames = [...new Set(created.map((s) => topicOf(s.topicId!).name))].slice(0, 3);
  plan = {
    planId, startDate, endDate, generatedAt: iso(t0), totalPlannedMinutes: total, sessionCount: created.length, status: 'ACTIVE',
    strategySummary: created.length
      ? `${created.length} sessions (${Math.floor(total / 60)} h ${total % 60} min) for ${new Set(created.map((s) => s.topicId)).size} topics. Focus: ${focusNames.join(', ')}.`
      : 'Nothing could be scheduled in this period.',
  };
  return { ...plan, strategySummary: plan.strategySummary ?? '', sessions: created.sort((a, b) => a.startAt - b.startAt).map(sessionView), unscheduled, warnings };
}

/** Re-plans the rest of the active plan period from today and links the replacement session. */
function reschedule(from: SessionRec): SessionRec | null {
  const start = today();
  const end = plan ? (plan.endDate >= start ? plan.endDate : addDays(start, 6)) : addDays(start, 6);
  generate(start, Math.min(28, diffDays(start, end) + 1));
  const replacement = sessions
    .filter((s) => s.topicId === from.topicId && s.status === 'PLANNED' && s.startAt > now() && s.rescheduledFromId === null)
    .sort((a, b) => a.startAt - b.startAt)[0] ?? null;
  if (replacement) replacement.rescheduledFromId = from.id;
  return replacement;
}

function detectMissed() {
  const missed = markOverdueMissed();
  for (const m of missed) {
    const replacement = reschedule(m);
    const name = m.topicId ? topicOf(m.topicId).name : subjectOf(m.subjectId).name;
    if (replacement) {
      const p = parts(replacement.startAt, tz());
      notify('SESSION_RESCHEDULED', 'Missed study session rescheduled', `You missed ${name}. It was moved to ${p.date} at ${p.time.slice(0, 5)}.`, '/planner');
    } else {
      notify('SESSION_MISSED', 'Missed study session', `You missed ${name}. There was no free slot left in this plan.`, '/planner');
    }
  }
}

// ------------------------------------------------------------------ SM-2 and mastery

function sm2(t: TopicRec, rating: RevisionRating, day: string) {
  const prevEf = t.ef;
  const prevInterval = t.interval;
  let ef = t.ef + (rating === 'AGAIN' ? -0.2 : rating === 'HARD' ? -0.15 : rating === 'EASY' ? 0.15 : 0);
  ef = round2(clamp(ef, 1.3, 5));
  let reps = t.reps;
  let interval: number;
  if (rating === 'AGAIN') {
    reps = 0;
    interval = 1;
  } else if (rating === 'HARD') {
    interval = reps === 0 ? 1 : Math.max(1, Math.round(t.interval * 1.2));
    reps += 1;
  } else if (rating === 'GOOD') {
    interval = reps === 0 ? 1 : reps === 1 ? 6 : Math.round(t.interval * ef);
    reps += 1;
  } else {
    interval = reps === 0 ? 4 : reps === 1 ? 8 : Math.round(t.interval * ef * 1.3);
    reps += 1;
  }
  if ((rating === 'GOOD' || rating === 'EASY') && reps > 1) interval = Math.max(interval, prevInterval + 1);
  interval = clamp(interval, 1, 365);
  const next = addDays(day, interval);
  const log: RevisionLogRec = { id: newId(), topicId: t.id, rating, revisedOn: day, dueDate: t.nextRevisionDate, durationMinutes: null, prevInterval, newInterval: interval, prevEf, newEf: ef, reps, next, at: now() };
  t.ef = ef;
  t.interval = interval;
  t.reps = reps;
  t.nextRevisionDate = next;
  t.lastRevisedOn = day;
  revisionLogs.unshift(log);
  return log;
}

function recomputeMastery(topicId: number) {
  const t = topicOf(topicId);
  const scores = assessments
    .flatMap((a) => a.topicScores.filter((s) => s.topicId === topicId).map((s) => ({ on: a.assessedOn, id: a.id, pct: (s.obtainedMarks * 100) / s.maxMarks })))
    .sort((a, b) => a.on.localeCompare(b.on) || a.id - b.id);
  if (scores.length === 0) {
    t.mastery = null;
    return;
  }
  const latest = scores[scores.length - 1].pct;
  const average = scores.reduce((a, s) => a + s.pct, 0) / scores.length;
  const score = round2(clamp(0.6 * latest + 0.4 * average, 0, 100));
  const previous = t.mastery ? t.mastery.score : null;
  const trend: Trend = previous === null ? 'NEW' : score > previous + 2 ? 'IMPROVING' : score < previous - 2 ? 'DECLINING' : 'STABLE';
  t.mastery = { score, latest: round2(latest), average: round2(average), previous, level: levelOf(score), trend, count: scores.length, lastOn: scores[scores.length - 1].on };
}

// ------------------------------------------------------------------ readiness (same formula as ReadinessCalculator)

const DISCLAIMER = 'This is an estimate of your preparation based on your recorded progress, not a prediction or guarantee of your actual exam result.';
function readiness(e: ExamRec, store = true): ReadinessResponse {
  const s = subjectOf(e.subjectId);
  const ts = topics.filter((t) => t.subjectId === s.id);
  const done = ts.filter((t) => t.status === 'COMPLETED');
  const assessed = ts.filter((t) => t.mastery);
  const mocks = assessments.filter((a) => a.subjectId === s.id && a.assessmentType === 'MOCK_TEST').sort((a, b) => b.assessedOn.localeCompare(a.assessedOn)).slice(0, 3);
  const revised = done.filter((t) => t.reps > 0 && (t.nextRevisionDate === null || t.nextRevisionDate >= today()));
  const components = [
    { key: 'syllabusCompletion', label: 'Syllabus completion', weight: 0.3, score: ts.length ? (done.length * 100) / ts.length : null, detail: ts.length ? `${done.length} of ${ts.length} topics completed` : 'No topics added yet' },
    { key: 'topicMastery', label: 'Topic mastery', weight: 0.3, score: assessed.length ? assessed.reduce((a, t) => a + t.mastery!.score, 0) / assessed.length : null, detail: assessed.length ? `Average of ${assessed.length} assessed topic${assessed.length === 1 ? '' : 's'}` : 'No topic has been assessed yet' },
    { key: 'mockTestPerformance', label: 'Mock test performance', weight: 0.25, score: mocks.length ? mocks.reduce((a, m) => a + (m.obtainedMarks * 100) / m.maxMarks, 0) / mocks.length : null, detail: mocks.length ? `Average of the latest ${mocks.length} mock test${mocks.length === 1 ? '' : 's'}` : 'No mock test recorded yet' },
    { key: 'revisionCompletion', label: 'Revision completion', weight: 0.15, score: done.length ? (revised.length * 100) / done.length : null, detail: done.length ? `${revised.length} of ${done.length} completed topics revised and not overdue` : 'No topic completed yet' },
  ].map((c) => ({ ...c, score: c.score === null ? null : round2(c.score), available: c.score !== null }));
  const available = components.filter((c) => c.available);
  const weightSum = available.reduce((a, c) => a + c.weight, 0);
  const score = weightSum > 0 ? round1(available.reduce((a, c) => a + c.weight * c.score!, 0) / weightSum) : null;
  const confidence = round1(weightSum * 100);
  const sufficiency: DataSufficiency = available.length === 4 ? 'SUFFICIENT' : confidence >= 55 ? 'PARTIAL' : 'INSUFFICIENT';
  const level: ReadinessLevel | null = score === null ? null : score >= 80 ? 'WELL_PREPARED' : score >= 60 ? 'ON_TRACK' : score >= 40 ? 'NEEDS_WORK' : 'AT_RISK';
  if (store) {
    if (!snapshots.has(e.id)) snapshots.set(e.id, new Map());
    snapshots.get(e.id)!.set(today(), score);
  }
  const focusAreas: ReadinessResponse['focusAreas'] = [];
  for (const t of assessed.filter((x) => x.mastery!.level === 'WEAK')) focusAreas.push({ type: 'WEAK_TOPIC', title: t.name, detail: `Mastery ${Math.round(t.mastery!.score)}% (weak)`, topicId: t.id });
  const pending = ts.filter((t) => t.status !== 'COMPLETED');
  if (pending.length) focusAreas.push({ type: 'PENDING_TOPICS', title: `${pending.length} topic${pending.length === 1 ? '' : 's'} not completed`, detail: 'Finish the remaining syllabus before the exam.', topicId: null });
  if (!mocks.length) focusAreas.push({ type: 'NO_MOCK_TEST', title: 'No mock test yet', detail: 'Attempt a full mock test under exam conditions.', topicId: null });
  const overdue = done.filter((t) => t.nextRevisionDate && t.nextRevisionDate < today());
  if (overdue.length) focusAreas.push({ type: 'OVERDUE_REVISION', title: `${overdue.length} revision${overdue.length === 1 ? '' : 's'} overdue`, detail: 'Revise them to keep what you learned.', topicId: null });
  const hist = [...(snapshots.get(e.id) ?? new Map<string, number | null>()).entries()].sort((a, b) => a[0].localeCompare(b[0])).map(([date, sc]) => ({ date, score: sc }));
  return {
    examId: e.id, examTitle: e.title, subjectId: s.id, subjectName: s.name, subjectColor: s.color, examDate: e.examDate, daysRemaining: diffDays(today(), e.examDate),
    readinessScore: score, level, dataConfidence: confidence, dataSufficiency: sufficiency, components,
    pendingTopics: pending.map((t) => ({ topicId: t.id, name: t.name, status: t.status, difficulty: t.difficulty, masteryScore: t.mastery ? t.mastery.score : null })),
    focusAreas, history: hist, calculatedOn: today(), disclaimer: DISCLAIMER,
  };
}

// ------------------------------------------------------------------ focus timing (server-side rule)

function focusElapsed(f: FocusRec, at = now()) {
  const end = f.endedAt ?? (f.status === 'PAUSED' && f.pausedAt ? f.pausedAt : at);
  return Math.max(0, Math.floor((end - f.startedAt) / 1000) - f.pausedSeconds);
}
function focusView(f: FocusRec): FocusSession {
  const s = f.subjectId ? subjects.find((x) => x.id === f.subjectId) ?? null : null;
  const t = f.topicId ? topics.find((x) => x.id === f.topicId) ?? null : null;
  const elapsed = Math.min(focusElapsed(f), f.plannedMinutes * 60);
  return {
    id: f.id, status: f.status, subjectId: s?.id ?? null, subjectName: s?.name ?? null, subjectColor: s?.color ?? null, topicId: t?.id ?? null,
    topicName: t?.name ?? null, studySessionId: f.studySessionId, plannedMinutes: f.plannedMinutes, startedAt: iso(f.startedAt),
    pausedAt: f.pausedAt ? iso(f.pausedAt) : null, totalPausedSeconds: f.pausedSeconds, pauseCount: f.pauseCount,
    endedAt: f.endedAt ? iso(f.endedAt) : null, actualFocusSeconds: f.actualSeconds, elapsedFocusSeconds: elapsed,
    remainingSeconds: Math.max(0, f.plannedMinutes * 60 - elapsed), notes: f.notes, serverTime: iso(now()),
  };
}
const completedFocusToday = () => focus.filter((f) => f.status === 'COMPLETED' && f.endedAt && localDate(f.endedAt) === today()).length;

// ------------------------------------------------------------------ analytics helpers

function dailyPoint(date: string): DailyPoint {
  const ss = sessions.filter((s) => localDate(s.startAt) === date && s.status !== 'CANCELLED');
  return {
    date, label: DAY_LABELS[isoDow(date) - 1],
    plannedMinutes: ss.reduce((a, s) => a + s.plannedMinutes, 0),
    actualMinutes: ss.filter((s) => s.status === 'COMPLETED' || s.status === 'INCOMPLETE').reduce((a, s) => a + (s.actualMinutes ?? 0), 0),
    focusMinutes: Math.floor(focus.filter((f) => f.status === 'COMPLETED' && localDate(f.startedAt) === date).reduce((a, f) => a + (f.actualSeconds ?? 0), 0) / 60),
    completedSessions: ss.filter((s) => s.status === 'COMPLETED').length,
    missedSessions: ss.filter((s) => s.status === 'MISSED').length,
  };
}
const range = (from: string, to: string) => {
  const out: string[] = [];
  for (let d = from; d <= to; d = addDays(d, 1)) out.push(d);
  return out;
};
function streak(): { current: number; longest: number } {
  const active = new Set(sessions.filter((s) => s.status === 'COMPLETED' || s.status === 'INCOMPLETE').map((s) => localDate(s.startAt)));
  let current = 0;
  let d = active.has(today()) ? today() : addDays(today(), -1);
  while (active.has(d)) {
    current++;
    d = addDays(d, -1);
  }
  let longest = 0;
  let run = 0;
  for (const day of range(addDays(today(), -120), today())) {
    run = active.has(day) ? run + 1 : 0;
    longest = Math.max(longest, run);
  }
  return { current, longest };
}
function calendar(from: string, to: string): CalendarResponse {
  const days = range(from, to).map((date) => {
    const ss = sessions.filter((s) => localDate(s.startAt) === date && s.status !== 'CANCELLED').sort((a, b) => a.startAt - b.startAt);
    return {
      date, plannedMinutes: ss.reduce((a, s) => a + s.plannedMinutes, 0),
      completedMinutes: ss.filter((s) => s.status === 'COMPLETED' || s.status === 'INCOMPLETE').reduce((a, s) => a + (s.actualMinutes ?? 0), 0),
      sessions: ss.map(sessionView),
    };
  });
  return { from, to, timeZone: tz(), totalPlannedMinutes: days.reduce((a, d) => a + d.plannedMinutes, 0), totalCompletedMinutes: days.reduce((a, d) => a + d.completedMinutes, 0), days };
}
function revisionItem(t: TopicRec): RevisionItem {
  const s = subjectOf(t.subjectId);
  const overdue = diffDays(t.nextRevisionDate!, today());
  return {
    topicId: t.id, topicName: t.name, subjectId: s.id, subjectName: s.name, subjectColor: s.color, nextRevisionDate: t.nextRevisionDate!,
    daysOverdue: Math.max(0, overdue), dueStatus: overdue > 0 ? 'OVERDUE' : overdue === 0 ? 'DUE_TODAY' : 'UPCOMING', intervalDays: t.interval,
    repetitions: t.reps, easeFactor: t.ef, lastRevisedOn: t.lastRevisedOn, masteryScore: t.mastery ? t.mastery.score : null, masteryLevel: t.mastery ? t.mastery.level : 'NOT_ASSESSED',
  };
}
function assessmentView(a: AssessmentRec): Assessment {
  const s = subjectOf(a.subjectId);
  return {
    id: a.id, subjectId: s.id, subjectName: s.name, subjectColor: s.color, examId: a.examId, examTitle: a.examId ? exams.find((e) => e.id === a.examId)?.title ?? null : null,
    title: a.title, assessmentType: a.assessmentType, assessedOn: a.assessedOn, maxMarks: a.maxMarks, obtainedMarks: a.obtainedMarks,
    scorePercent: round2((a.obtainedMarks * 100) / a.maxMarks), notes: a.notes,
    topicScores: a.topicScores.map((x) => ({ ...x, topicName: topics.find((t) => t.id === x.topicId)?.name ?? 'Deleted topic', scorePercent: round2((x.obtainedMarks * 100) / x.maxMarks) })),
    createdAt: iso(a.createdAt),
  };
}
function availabilityView(): AvailabilityResponse {
  const ws = [...windows].sort((a, b) => a.dayOfWeek - b.dayOfWeek || a.startTime.localeCompare(b.startTime))
    .map((w) => ({ ...w, startTime: `${w.startTime.slice(0, 5)}:00`, endTime: `${w.endTime.slice(0, 5)}:00`, minutes: minutesOf(w.endTime) - minutesOf(w.startTime) }));
  return {
    dailyStudyMinutes: profile.dailyStudyMinutes, weeklyStudyMinutes: profile.weeklyStudyMinutes, preferredSessionMinutes: profile.preferredSessionMinutes,
    breakMinutes: profile.breakMinutes, totalWeeklyAvailableMinutes: ws.reduce((a, w) => a + w.minutes, 0), windows: ws,
  };
}

// ------------------------------------------------------------------ request handling

type Body = Record<string, unknown>;
type Handler = (m: RegExpMatchArray, body: Body, q: URLSearchParams) => unknown;
const routes: [string, RegExp, Handler][] = [];
const on = (method: string, pattern: string, handler: Handler) => routes.push([method, new RegExp(`^${pattern.replace(/:id/g, '(\\d+)')}$`), handler]);
const num = (v: unknown) => Number(v);
const requireText = (v: unknown, field: string, label: string) => {
  if (typeof v !== 'string' || !v.trim()) invalid(field, `${label} is required.`);
  return (v as string).trim();
};

on('GET', '/health', () => ({
  status: 'UP', application: 'SmartStudy API (demo)', version: '1.0.0', timestamp: iso(now()),
  database: { status: 'UP', product: 'In-browser demo', productVersion: '', databaseName: 'smartstudy', schemaVersion: '1', message: null },
}));
on('GET', '/auth/csrf', () => ({ headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'demo-token' }));
on('GET', '/auth/me', () => profile);
on('POST', '/auth/login', (_m, b) => {
  if (String(b.email ?? '').trim().toLowerCase() !== profile.email.toLowerCase() || b.password !== password) {
    throw new HttpError(401, 'INVALID_CREDENTIALS', `Invalid email or password. In this demo use ${profile.email} / ${password}.`);
  }
  loggedIn = true;
  return profile;
});
on('POST', '/auth/register', (_m, b) => {
  profile.fullName = requireText(b.fullName, 'fullName', 'Full name');
  profile.email = requireText(b.email, 'email', 'Email').toLowerCase();
  password = String(b.password);
  if (typeof b.timeZone === 'string' && b.timeZone) profile.timeZone = b.timeZone;
  loggedIn = true;
  notify('SYSTEM', 'Welcome to the SmartStudy demo', 'Your demo account starts with sample subjects so every page has data to show.', '/');
  return profile;
});
on('POST', '/auth/logout', () => {
  loggedIn = false;
  return undefined;
});
on('GET', '/users/me', () => profile);
on('PUT', '/users/me', (_m, b) => {
  const r = b as unknown as UpdateProfileRequest;
  Object.assign(profile, { fullName: requireText(r.fullName, 'fullName', 'Full name'), timeZone: r.timeZone, focusMinutes: r.focusMinutes, shortBreakMinutes: r.shortBreakMinutes, longBreakMinutes: r.longBreakMinutes, longBreakInterval: r.longBreakInterval });
  return profile;
});
on('PUT', '/users/me/password', (_m, b) => {
  if (b.currentPassword !== password) invalid('currentPassword', 'Current password is incorrect.');
  password = String(b.newPassword);
  return undefined;
});
on('GET', '/availability', () => availabilityView());
on('PUT', '/availability', (_m, b) => {
  const r = b as unknown as AvailabilityRequest;
  Object.assign(profile, { dailyStudyMinutes: r.dailyStudyMinutes, weeklyStudyMinutes: r.weeklyStudyMinutes, preferredSessionMinutes: r.preferredSessionMinutes, breakMinutes: r.breakMinutes });
  windows = r.windows.map((w) => ({ id: newId(), dayOfWeek: w.dayOfWeek, startTime: w.startTime, endTime: w.endTime }));
  return availabilityView();
});

// subjects
on('GET', '/subjects', () => subjects.map(subjectView));
on('GET', '/subjects/:id', (m) => subjectView(subjectOf(num(m[1]))));
function applySubject(target: SubjectRec | null, b: Body): SubjectRec {
  const r = b as unknown as SubjectRequest;
  const name = requireText(r.name, 'name', 'Name');
  if (subjects.some((s) => s.id !== target?.id && s.name.toLowerCase() === name.toLowerCase())) throw new HttpError(409, 'CONFLICT', `You already have a subject named "${name}".`);
  const rec: SubjectRec = target ?? { id: newId(), name, code: null, color: '#10B981', importance: 3, targetScore: null, description: null, archived: false, createdAt: now() };
  Object.assign(rec, { name, code: r.code || null, color: r.color || '#10B981', importance: r.importance, targetScore: r.targetScore ?? null, description: r.description || null, archived: !!r.archived });
  return rec;
}
on('POST', '/subjects', (_m, b) => {
  const s = applySubject(null, b);
  subjects.push(s);
  return subjectView(s);
});
on('PUT', '/subjects/:id', (m, b) => subjectView(applySubject(subjectOf(num(m[1])), b)));
on('DELETE', '/subjects/:id', (m) => {
  const id = subjectOf(num(m[1])).id;
  const topicIds = new Set(topics.filter((t) => t.subjectId === id).map((t) => t.id));
  subjects.splice(subjects.findIndex((s) => s.id === id), 1);
  for (let i = topics.length - 1; i >= 0; i--) if (topics[i].subjectId === id) topics.splice(i, 1);
  for (let i = exams.length - 1; i >= 0; i--) if (exams[i].subjectId === id) exams.splice(i, 1);
  for (let i = assessments.length - 1; i >= 0; i--) if (assessments[i].subjectId === id) assessments.splice(i, 1);
  sessions = sessions.filter((s) => s.subjectId !== id);
  for (const f of focus) {
    if (f.subjectId === id) f.subjectId = null;
    if (f.topicId && topicIds.has(f.topicId)) f.topicId = null;
  }
  return undefined;
});

// topics
on('GET', '/subjects/:id/topics', (m) => {
  const id = subjectOf(num(m[1])).id;
  return topics.filter((t) => t.subjectId === id).sort((a, b) => a.sortOrder - b.sortOrder).map(topicView);
});
on('GET', '/topics', () => topics.map(topicView));
on('GET', '/topics/:id', (m) => topicView(topicOf(num(m[1]))));
function setTopicStatus(t: TopicRec, status: TopicStatus) {
  if (status === 'COMPLETED' && t.status !== 'COMPLETED') {
    t.completedAt = now();
    if (!t.nextRevisionDate) t.nextRevisionDate = addDays(today(), 1);
  }
  if (status !== 'COMPLETED') {
    t.completedAt = null;
    t.nextRevisionDate = null;
  }
  t.status = status;
}
function applyTopic(t: TopicRec, b: Body) {
  const r = b as unknown as TopicRequest;
  const name = requireText(r.name, 'name', 'Name');
  if (topics.some((x) => x.id !== t.id && x.subjectId === t.subjectId && x.name.toLowerCase() === name.toLowerCase())) throw new HttpError(409, 'CONFLICT', `This subject already has a topic named "${name}".`);
  Object.assign(t, { name, description: r.description || null, difficulty: r.difficulty, estimatedMinutes: r.estimatedMinutes });
  setTopicStatus(t, r.status ?? t.status);
}
on('POST', '/subjects/:id/topics', (m, b) => {
  const s = subjectOf(num(m[1]));
  const t: TopicRec = { id: newId(), subjectId: s.id, name: '', description: null, difficulty: 3, estimatedMinutes: 120, status: 'NOT_STARTED', completedAt: null, sortOrder: topics.length + 1, mastery: null, ef: 2.5, interval: 0, reps: 0, nextRevisionDate: null, lastRevisedOn: null, createdAt: now() };
  applyTopic(t, b);
  topics.push(t);
  return topicView(t);
});
on('PUT', '/topics/:id', (m, b) => {
  const t = topicOf(num(m[1]));
  applyTopic(t, b);
  return topicView(t);
});
on('PATCH', '/topics/:id/status', (m, b) => {
  const t = topicOf(num(m[1]));
  setTopicStatus(t, b.status as TopicStatus);
  return topicView(t);
});
on('DELETE', '/topics/:id', (m) => {
  const id = topicOf(num(m[1])).id;
  topics.splice(topics.findIndex((t) => t.id === id), 1);
  sessions = sessions.filter((s) => s.topicId !== id);
  for (const a of assessments) a.topicScores = a.topicScores.filter((x) => x.topicId !== id);
  return undefined;
});

// exams and readiness
on('GET', '/exams', (_m, _b, q) => exams.filter((e) => q.get('upcoming') !== 'true' || e.examDate >= today()).sort((a, b) => a.examDate.localeCompare(b.examDate)).map(examView));
on('GET', '/exams/:id', (m) => examView(examOf(num(m[1]))));
function applyExam(e: ExamRec | null, b: Body): ExamRec {
  const r = b as unknown as ExamRequest;
  subjectOf(num(r.subjectId));
  const rec: ExamRec = e ?? { id: newId(), subjectId: 0, title: '', examType: 'FINAL', examDate: today(), startTime: null, durationMinutes: null, location: null, targetScore: null, notes: null, createdAt: now() };
  Object.assign(rec, { subjectId: num(r.subjectId), title: requireText(r.title, 'title', 'Title'), examType: r.examType, examDate: r.examDate, startTime: r.startTime || null, durationMinutes: r.durationMinutes ?? null, location: r.location || null, targetScore: r.targetScore ?? null, notes: r.notes || null });
  return rec;
}
on('POST', '/exams', (_m, b) => {
  const e = applyExam(null, b);
  exams.push(e);
  return examView(e);
});
on('PUT', '/exams/:id', (m, b) => examView(applyExam(examOf(num(m[1])), b)));
on('DELETE', '/exams/:id', (m) => {
  const id = examOf(num(m[1])).id;
  exams.splice(exams.findIndex((e) => e.id === id), 1);
  snapshots.delete(id);
  for (const a of assessments) if (a.examId === id) a.examId = null;
  return undefined;
});
on('GET', '/exams/:id/readiness', (m) => readiness(examOf(num(m[1]))));
on('GET', '/readiness', (): ReadinessOverviewItem[] =>
  exams.filter((e) => e.examDate >= today()).sort((a, b) => a.examDate.localeCompare(b.examDate)).map((e) => {
    const r = readiness(e);
    return { examId: r.examId, examTitle: r.examTitle, subjectId: r.subjectId, subjectName: r.subjectName, subjectColor: r.subjectColor, examDate: r.examDate, daysRemaining: r.daysRemaining, readinessScore: r.readinessScore, level: r.level, dataSufficiency: r.dataSufficiency };
  }));

// assessments and performance
on('GET', '/assessments', (_m, _b, q) => assessments.filter((a) => !q.get('subjectId') || a.subjectId === num(q.get('subjectId'))).sort((a, b) => b.assessedOn.localeCompare(a.assessedOn)).map(assessmentView));
on('POST', '/assessments', (_m, b) => {
  const r = b as unknown as AssessmentRequest;
  subjectOf(num(r.subjectId));
  if (r.obtainedMarks > r.maxMarks) invalid('obtainedMarks', 'Obtained marks cannot exceed maximum marks.');
  const a: AssessmentRec = { id: newId(), subjectId: num(r.subjectId), examId: r.examId ?? null, title: requireText(r.title, 'title', 'Title'), assessmentType: r.assessmentType, assessedOn: r.assessedOn, maxMarks: r.maxMarks, obtainedMarks: r.obtainedMarks, notes: r.notes || null, topicScores: r.topicScores.map((x) => ({ topicId: num(x.topicId), maxMarks: x.maxMarks, obtainedMarks: x.obtainedMarks })), createdAt: now() };
  assessments.push(a);
  for (const x of a.topicScores) recomputeMastery(x.topicId);
  return assessmentView(a);
});
on('DELETE', '/assessments/:id', (m) => {
  const i = assessments.findIndex((a) => a.id === num(m[1]));
  if (i < 0) notFound('Assessment');
  const [a] = assessments.splice(i, 1);
  for (const x of a.topicScores) if (topics.some((t) => t.id === x.topicId)) recomputeMastery(x.topicId);
  return undefined;
});
const RECOMMEND: Record<MasteryLevel, string> = {
  WEAK: 'Re-study the fundamentals, then practise questions; the planner gives this topic extra sessions.',
  NEEDS_IMPROVEMENT: 'Practise more questions and review your mistakes.',
  GOOD: 'Keep it fresh with spaced revision.',
  STRONG: 'Maintain with occasional revision.',
  NOT_ASSESSED: 'Record a quiz score to measure mastery.',
};
on('GET', '/performance/weak-topics', (): WeakTopicResponse => {
  const assessed = topics.filter((t) => t.mastery);
  return {
    totalTopics: topics.length, assessedTopics: assessed.length, notAssessedTopics: topics.length - assessed.length,
    items: assessed.filter((t) => t.mastery!.score < 65).sort((a, b) => a.mastery!.score - b.mastery!.score).map((t) => {
      const s = subjectOf(t.subjectId);
      const ms = t.mastery!;
      return { topicId: t.id, topicName: t.name, subjectId: s.id, subjectName: s.name, subjectColor: s.color, masteryScore: ms.score, masteryLevel: ms.level, latestScore: ms.latest, historicalAverage: ms.average, previousMasteryScore: ms.previous, trend: ms.trend, assessmentCount: ms.count, lastAssessedOn: ms.lastOn, topicStatus: t.status, recommendation: RECOMMEND[ms.level] };
    }),
  };
});
on('GET', '/performance/summary', (): PerformanceSummary => {
  const counts: Record<MasteryLevel, number> = { WEAK: 0, NEEDS_IMPROVEMENT: 0, GOOD: 0, STRONG: 0, NOT_ASSESSED: 0 };
  for (const t of topics) counts[t.mastery ? t.mastery.level : 'NOT_ASSESSED']++;
  const assessed = topics.filter((t) => t.mastery);
  return {
    overallMastery: assessed.length ? round2(assessed.reduce((a, t) => a + t.mastery!.score, 0) / assessed.length) : null,
    levelCounts: counts,
    subjects: subjects.map((s) => {
      const v = subjectView(s);
      const as = assessments.filter((a) => a.subjectId === s.id);
      return { subjectId: s.id, subjectName: s.name, subjectColor: s.color, averageMastery: v.averageMastery, assessedTopics: v.assessedTopicCount, totalTopics: v.topicCount, weakTopics: v.weakTopicCount, averageAssessmentScore: as.length ? round2(as.reduce((a, x) => a + (x.obtainedMarks * 100) / x.maxMarks, 0) / as.length) : null, assessmentCount: as.length };
    }),
  };
});
on('GET', '/performance/topics/:id/history', (m): TopicHistory => {
  const t = topicOf(num(m[1]));
  const s = subjectOf(t.subjectId);
  const rows = assessments.flatMap((a) => a.topicScores.filter((x) => x.topicId === t.id).map((x) => ({ a, pct: (x.obtainedMarks * 100) / x.maxMarks }))).sort((x, y) => x.a.assessedOn.localeCompare(y.a.assessedOn) || x.a.id - y.a.id);
  let sum = 0;
  return {
    topicId: t.id, topicName: t.name, subjectName: s.name, currentMastery: t.mastery ? t.mastery.score : null, masteryLevel: t.mastery ? t.mastery.level : 'NOT_ASSESSED',
    points: rows.map((r, i) => {
      sum += r.pct;
      return { assessmentId: r.a.id, title: r.a.title, assessmentType: r.a.assessmentType, assessedOn: r.a.assessedOn, scorePercent: round2(r.pct), masteryAfter: round2(0.6 * r.pct + (0.4 * sum) / (i + 1)) };
    }),
  };
});

// planner
on('GET', '/planner/today', () => {
  detectMissed();
  const day = calendar(today(), today()).days[0];
  const done = day.sessions.filter((s) => s.status === 'COMPLETED').length;
  return {
    date: today(), timeZone: tz(), plannedMinutes: day.plannedMinutes, completedMinutes: day.completedMinutes,
    completionPercent: day.plannedMinutes ? round1(Math.min(100, (day.completedMinutes * 100) / day.plannedMinutes)) : 0,
    sessionsPlanned: day.sessions.length, sessionsCompleted: done, sessionsMissed: day.sessions.filter((s) => s.status === 'MISSED').length,
    sessions: day.sessions, recommendations: ranking().slice(0, 5), activePlan: plan,
  };
});
on('GET', '/planner/weekly', (_m, _b, q) => {
  const ws = weekStartOf(q.get('date') || today());
  return calendar(ws, addDays(ws, 6));
});
on('GET', '/planner/calendar', (_m, _b, q) => calendar(q.get('from') || today(), q.get('to') || today()));
on('GET', '/planner/recommendations', (_m, _b, q) => ranking().slice(0, num(q.get('limit') || 5)));
on('GET', '/planner/priorities', (): PrioritiesResponse => ({ weights: WEIGHTS, items: ranking() }));
on('GET', '/planner/active', () => plan ?? undefined);
on('POST', '/planner/generate', (_m, b) => {
  detectMissed();
  const start = typeof b.startDate === 'string' && b.startDate ? b.startDate : today();
  const plannerResult = generate(start < today() ? today() : start, clamp(num(b.days ?? 7) || 7, 1, 28));
  notify('PLAN_GENERATED', 'New study plan', plannerResult.strategySummary, '/planner');
  return plannerResult;
});
on('POST', '/planner/regenerate', () => {
  detectMissed();
  const start = today();
  const end = plan && plan.endDate >= start ? plan.endDate : addDays(start, 6);
  return generate(start, diffDays(start, end) + 1);
});

// sessions
function checkConflict(start: number, end: number, ignoreId: number | null) {
  const clash = sessions.find((s) => s.id !== ignoreId && (s.status === 'PLANNED' || s.status === 'IN_PROGRESS' || s.status === 'COMPLETED') && s.startAt < end && start < s.endAt);
  if (clash) {
    const p = parts(clash.startAt, tz());
    throw new HttpError(409, 'CONFLICT', `This time overlaps another study session on ${p.date} at ${p.time.slice(0, 5)}.`);
  }
}
on('POST', '/sessions', (_m, b) => {
  const r = b as unknown as CreateSessionRequest;
  const topic = r.topicId ? topicOf(num(r.topicId)) : null;
  const subjectId = topic ? topic.subjectId : r.subjectId ? subjectOf(num(r.subjectId)).id : invalid('subjectId', 'Choose a subject or topic.');
  const start = toInstant(r.date, r.startTime, tz());
  const end = start + r.durationMinutes * MIN;
  checkConflict(start, end, null);
  const s: SessionRec = { id: newId(), planId: null, subjectId, topicId: topic?.id ?? null, sessionType: r.sessionType, source: 'MANUAL', status: 'PLANNED', startAt: start, endAt: end, plannedMinutes: r.durationMinutes, actualMinutes: null, startedAt: null, completedAt: null, locked: r.locked ?? true, priorityScore: null, reason: 'Added manually', notes: r.notes || null, rescheduledFromId: null };
  sessions.push(s);
  return sessionView(s);
});
on('GET', '/sessions/:id', (m) => sessionView(sessionOf(num(m[1]))));
on('PUT', '/sessions/:id', (m, b) => {
  const s = sessionOf(num(m[1]));
  if (s.status !== 'PLANNED') rule('Only planned sessions can be moved.');
  const r = b as unknown as UpdateSessionRequest;
  const start = toInstant(r.date, r.startTime, tz());
  checkConflict(start, start + r.durationMinutes * MIN, s.id);
  Object.assign(s, { startAt: start, endAt: start + r.durationMinutes * MIN, plannedMinutes: r.durationMinutes, notes: r.notes || null, locked: true });
  return sessionView(s);
});
on('DELETE', '/sessions/:id', (m) => {
  const s = sessionOf(num(m[1]));
  sessions = sessions.filter((x) => x.id !== s.id);
  return undefined;
});
on('PATCH', '/sessions/:id/lock', (m, b) => {
  const s = sessionOf(num(m[1]));
  s.locked = !!b.locked;
  return sessionView(s);
});
on('POST', '/sessions/:id/start', (m) => {
  const s = sessionOf(num(m[1]));
  if (s.status !== 'PLANNED') rule('Only planned sessions can be started.');
  if (now() < s.startAt - 60 * MIN) rule('You can start a session at most 60 minutes before its planned start.');
  s.status = 'IN_PROGRESS';
  s.startedAt = now();
  return sessionView(s);
});
on('POST', '/sessions/:id/complete', (m, b): SessionActionResponse => {
  const s = sessionOf(num(m[1]));
  const r = b as CompleteSessionRequest;
  if (s.status !== 'PLANNED' && s.status !== 'IN_PROGRESS' && !(s.status === 'MISSED' && now() - s.endAt < 48 * 3600_000)) rule('This session can no longer be completed.');
  const begin = s.startedAt ?? s.startAt;
  if (now() < begin) rule('This session has not started yet. Use Start when you begin, or move it to now.');
  if (s.sessionType === 'REVISION' && !r.revisionRating) invalid('revisionRating', 'Choose how well you remembered the topic.');
  const elapsed = Math.floor((now() - begin) / MIN);
  const actual = r.actualMinutes ?? Math.min(s.plannedMinutes, elapsed);
  if (actual > Math.max(elapsed, 1)) invalid('actualMinutes', `You can record at most ${Math.max(elapsed, 1)} minutes - the time since the session started.`);
  s.actualMinutes = actual;
  s.completedAt = now();
  s.notes = r.notes || s.notes;
  const topic = s.topicId ? topicOf(s.topicId) : null;
  if (topic && s.sessionType === 'REVISION' && r.revisionRating) sm2(topic, r.revisionRating, today());
  if (topic && r.topicCompleted) setTopicStatus(topic, 'COMPLETED');
  else if (topic && topic.status === 'NOT_STARTED' && s.sessionType === 'STUDY') topic.status = 'IN_PROGRESS';
  if (actual >= s.plannedMinutes * 0.8 || s.sessionType === 'REVISION') {
    s.status = 'COMPLETED';
    return { session: sessionView(s), rescheduledTo: null, message: `Session completed - ${actual} minutes recorded.` };
  }
  s.status = 'INCOMPLETE';
  const replacement = topic && topic.status !== 'COMPLETED' ? reschedule(s) : null;
  const p = replacement ? parts(replacement.startAt, tz()) : null;
  return {
    session: sessionView(s), rescheduledTo: replacement ? sessionView(replacement) : null,
    message: `Saved as incomplete (${actual} of ${s.plannedMinutes} min).${p ? ` The rest was rescheduled to ${p.date} at ${p.time.slice(0, 5)}.` : ''}`,
  };
});
on('POST', '/sessions/:id/miss', (m): SessionActionResponse => {
  const s = sessionOf(num(m[1]));
  if (s.status !== 'PLANNED' && s.status !== 'IN_PROGRESS') rule('Only planned sessions can be marked as missed.');
  s.status = 'MISSED';
  const replacement = reschedule(s);
  const p = replacement ? parts(replacement.startAt, tz()) : null;
  const name = s.topicId ? topicOf(s.topicId).name : subjectOf(s.subjectId).name;
  if (p) notify('SESSION_RESCHEDULED', 'Missed study session rescheduled', `You missed ${name}. It was moved to ${p.date} at ${p.time.slice(0, 5)}.`, '/planner');
  return { session: sessionView(s), rescheduledTo: replacement ? sessionView(replacement) : null, message: p ? `Marked as missed and rescheduled to ${p.date} at ${p.time.slice(0, 5)}.` : 'Marked as missed. No free slot was left in this plan.' };
});

// revisions
on('GET', '/revisions/due', (): RevisionDueResponse => {
  const items = topics.filter((t) => t.status === 'COMPLETED' && t.nextRevisionDate && t.nextRevisionDate <= addDays(today(), 7)).map(revisionItem).sort((a, b) => a.nextRevisionDate.localeCompare(b.nextRevisionDate));
  const ws = weekStartOf(today());
  return {
    today: today(), dueCount: items.filter((i) => i.dueStatus !== 'UPCOMING').length, overdueCount: items.filter((i) => i.dueStatus === 'OVERDUE').length,
    upcomingCount: items.filter((i) => i.dueStatus === 'UPCOMING').length, completedThisWeek: revisionLogs.filter((l) => l.revisedOn >= ws).length,
    overdue: items.filter((i) => i.dueStatus === 'OVERDUE'), dueToday: items.filter((i) => i.dueStatus === 'DUE_TODAY'), upcoming: items.filter((i) => i.dueStatus === 'UPCOMING'),
  };
});
on('POST', '/revisions/:id/complete', (m, b): RevisionResult => {
  const t = topicOf(num(m[1]));
  if (t.status !== 'COMPLETED') rule('Only completed topics can be revised. Mark the topic as completed first.');
  const r = b as unknown as CompleteRevisionRequest;
  const log = sm2(t, r.rating, today());
  log.durationMinutes = r.durationMinutes ?? null;
  return {
    topicId: t.id, topicName: t.name, rating: r.rating, revisedOn: log.revisedOn, previousIntervalDays: log.prevInterval, newIntervalDays: log.newInterval,
    previousEaseFactor: log.prevEf, newEaseFactor: log.newEf, repetitions: log.reps, nextRevisionDate: log.next, completedSessionId: null,
    explanation: `Rated ${r.rating}: interval ${log.prevInterval} -> ${log.newInterval} days, ease factor ${log.prevEf.toFixed(2)} -> ${log.newEf.toFixed(2)}. Next revision on ${log.next}.`,
  };
});
on('GET', '/revisions/history', (_m, _b, q): RevisionLogEntry[] =>
  revisionLogs.filter((l) => !q.get('topicId') || l.topicId === num(q.get('topicId'))).filter((l) => topics.some((t) => t.id === l.topicId)).slice(0, num(q.get('limit') || 50)).map((l) => {
    const t = topicOf(l.topicId);
    const s = subjectOf(t.subjectId);
    return { id: l.id, topicId: t.id, topicName: t.name, subjectName: s.name, subjectColor: s.color, rating: l.rating, revisedOn: l.revisedOn, dueDate: l.dueDate, onTime: l.dueDate === null || l.revisedOn <= l.dueDate, durationMinutes: l.durationMinutes, previousIntervalDays: l.prevInterval, newIntervalDays: l.newInterval, previousEaseFactor: l.prevEf, newEaseFactor: l.newEf, repetitions: l.reps, nextRevisionDate: l.next, revisedAt: iso(l.at) };
  }));

// focus
const activeFocus = () => focus.find((f) => f.status === 'RUNNING' || f.status === 'PAUSED') ?? null;
on('POST', '/focus/start', (_m, b) => {
  if (activeFocus()) throw new HttpError(409, 'CONFLICT', 'A focus session is already running.');
  const r = b as FocusStartRequest;
  const topic = r.topicId ? topicOf(num(r.topicId)) : null;
  const f: FocusRec = { id: newId(), status: 'RUNNING', subjectId: topic ? topic.subjectId : r.subjectId ? subjectOf(num(r.subjectId)).id : null, topicId: topic?.id ?? null, studySessionId: r.studySessionId ?? null, plannedMinutes: clamp(r.plannedMinutes ?? profile.focusMinutes, 1, 120), startedAt: now(), pausedAt: null, pausedSeconds: 0, pauseCount: 0, endedAt: null, actualSeconds: null, notes: null };
  focus.unshift(f);
  return focusView(f);
});
on('GET', '/focus/active', () => {
  const f = activeFocus();
  return f ? focusView(f) : undefined;
});
on('POST', '/focus/:id/pause', (m) => {
  const f = focusOf(num(m[1]));
  if (f.status !== 'RUNNING') rule('Only a running session can be paused.');
  f.status = 'PAUSED';
  f.pausedAt = now();
  f.pauseCount += 1;
  return focusView(f);
});
on('POST', '/focus/:id/resume', (m) => {
  const f = focusOf(num(m[1]));
  if (f.status !== 'PAUSED' || !f.pausedAt) rule('Only a paused session can be resumed.');
  f.pausedSeconds += Math.floor((now() - f.pausedAt!) / 1000);
  f.pausedAt = null;
  f.status = 'RUNNING';
  return focusView(f);
});
on('POST', '/focus/:id/complete', (m, b): FocusCompleteResponse => {
  const f = focusOf(num(m[1]));
  if (f.status !== 'RUNNING' && f.status !== 'PAUSED') rule('This focus session has already ended.');
  const server = Math.min(focusElapsed(f), f.plannedMinutes * 60);
  const client = typeof b.clientReportedSeconds === 'number' ? b.clientReportedSeconds : null;
  if (f.status === 'PAUSED' && f.pausedAt) {
    f.pausedSeconds += Math.floor((now() - f.pausedAt) / 1000);
    f.pausedAt = null;
  }
  f.actualSeconds = client === null ? server : Math.max(0, Math.min(server, Math.round(client)));
  f.endedAt = now();
  f.status = 'COMPLETED';
  const done = completedFocusToday();
  const long = done % profile.longBreakInterval === 0;
  return { session: focusView(f), completedToday: done, nextBreak: long ? 'LONG' : 'SHORT', breakMinutes: long ? profile.longBreakMinutes : profile.shortBreakMinutes };
});
on('POST', '/focus/:id/cancel', (m) => {
  const f = focusOf(num(m[1]));
  f.status = 'CANCELLED';
  f.endedAt = now();
  return focusView(f);
});
on('GET', '/focus/history', (_m, _b, q) => {
  const from = addDays(today(), -num(q.get('days') || 30));
  return focus.filter((f) => localDate(f.startedAt) >= from).map(focusView);
});
on('GET', '/focus/stats', (): FocusStats => {
  const done = focus.filter((f) => f.status === 'COMPLETED');
  const mins = (list: FocusRec[]) => Math.floor(list.reduce((a, f) => a + (f.actualSeconds ?? 0), 0) / 60);
  const last30 = done.filter((f) => localDate(f.startedAt) >= addDays(today(), -29));
  const todays = completedFocusToday();
  const inCycle = todays % profile.longBreakInterval;
  return {
    todayFocusMinutes: mins(done.filter((f) => localDate(f.startedAt) === today())), todayCompletedSessions: todays,
    weekFocusMinutes: mins(done.filter((f) => localDate(f.startedAt) >= weekStartOf(today()))), last30DaysSessions: last30.length,
    last30DaysFocusMinutes: mins(last30), averageSessionMinutes: last30.length ? round1(mins(last30) / last30.length) : 0,
    completedInCurrentCycle: inCycle, longBreakInterval: profile.longBreakInterval, nextBreak: inCycle === profile.longBreakInterval - 1 ? 'LONG' : 'SHORT',
    focusMinutes: profile.focusMinutes, shortBreakMinutes: profile.shortBreakMinutes, longBreakMinutes: profile.longBreakMinutes,
    daily: range(addDays(today(), -6), today()).map((date) => {
      const list = done.filter((f) => localDate(f.startedAt) === date);
      return { date, minutes: mins(list), sessions: list.length };
    }),
    bySubject: subjects.map((s) => ({ subjectId: s.id, subjectName: s.name, subjectColor: s.color, minutes: mins(last30.filter((f) => f.subjectId === s.id)) })).filter((x) => x.minutes > 0),
  };
});

// analytics
on('GET', '/analytics/overview', (): DashboardOverview => {
  detectMissed();
  const day = dailyPoint(today());
  const ws = weekStartOf(today());
  const week = range(ws, addDays(ws, 6)).map(dailyPoint);
  const activeDays = range(addDays(today(), -13), today()).filter((d) => dailyPoint(d).actualMinutes > 0).length;
  const due = topics.filter((t) => t.status === 'COMPLETED' && t.nextRevisionDate && t.nextRevisionDate <= today());
  const todaySessions = sessions.filter((s) => localDate(s.startAt) === today() && s.status !== 'CANCELLED');
  const activity = [
    ...sessions.filter((s) => s.completedAt && (s.status === 'COMPLETED' || s.status === 'INCOMPLETE')).map((s) => ({ type: s.status === 'COMPLETED' ? 'SESSION_COMPLETED' : 'SESSION_INCOMPLETE', title: s.topicId ? topicOf(s.topicId).name : subjectOf(s.subjectId).name, detail: `${s.actualMinutes} min ${s.sessionType === 'REVISION' ? 'revision' : 'study'}`, at: s.completedAt! })),
    ...focus.filter((f) => f.status === 'COMPLETED').map((f) => ({ type: 'FOCUS', title: 'Focus session', detail: `${Math.floor((f.actualSeconds ?? 0) / 60)} min`, at: f.endedAt! })),
    ...revisionLogs.map((l) => ({ type: 'REVISION', title: topics.find((t) => t.id === l.topicId)?.name ?? 'Topic', detail: `Rated ${l.rating.toLowerCase()} - next in ${l.newInterval} days`, at: l.at })),
  ].sort((a, b) => b.at - a.at).slice(0, 8).map((x) => ({ type: x.type, title: x.title, detail: x.detail, occurredAt: iso(x.at) }));
  return {
    date: today(), timeZone: tz(),
    today: {
      plannedMinutes: day.plannedMinutes, completedMinutes: day.actualMinutes,
      completionPercent: day.plannedMinutes ? round1(Math.min(100, (day.actualMinutes * 100) / day.plannedMinutes)) : 0,
      sessionsPlanned: todaySessions.length, sessionsCompleted: day.completedSessions, sessionsMissed: day.missedSessions,
    },
    week: { plannedMinutes: week.reduce((a, d) => a + d.plannedMinutes, 0), actualMinutes: week.reduce((a, d) => a + d.actualMinutes, 0), focusMinutes: week.reduce((a, d) => a + d.focusMinutes, 0), completedSessions: week.reduce((a, d) => a + d.completedSessions, 0), missedSessions: week.reduce((a, d) => a + d.missedSessions, 0) },
    streakDays: streak().current, consistencyPercent: round1((activeDays * 100) / 14),
    upcomingExams: exams.filter((e) => e.examDate >= today()).sort((a, b) => a.examDate.localeCompare(b.examDate)).slice(0, 4).map((e) => {
      const r = readiness(e);
      return { examId: e.id, title: e.title, subjectName: r.subjectName, subjectColor: r.subjectColor, examDate: e.examDate, daysRemaining: r.daysRemaining, readinessScore: r.readinessScore, readinessLevel: r.level, dataSufficiency: r.dataSufficiency };
    }),
    weakTopics: topics.filter((t) => t.mastery && t.mastery.score < 65).sort((a, b) => a.mastery!.score - b.mastery!.score).slice(0, 5).map((t) => {
      const s = subjectOf(t.subjectId);
      return { topicId: t.id, topicName: t.name, subjectName: s.name, subjectColor: s.color, masteryScore: t.mastery!.score, masteryLevel: t.mastery!.level };
    }),
    revisions: { dueCount: due.length, overdueCount: due.filter((t) => t.nextRevisionDate! < today()).length, items: due.slice(0, 5).map((t) => ({ topicId: t.id, topicName: t.name, subjectName: subjectOf(t.subjectId).name, nextRevisionDate: t.nextRevisionDate!, daysOverdue: diffDays(t.nextRevisionDate!, today()) })) },
    weeklyProductivity: week, recentActivity: activity, unreadNotifications: notifications.filter((n) => !n.read).length,
  };
});
on('GET', '/analytics/weekly', (_m, _b, q): WeeklyAnalytics => {
  const ws = weekStartOf(q.get('date') || today());
  const we = addDays(ws, 6);
  const days = range(ws, we).map(dailyPoint);
  const ss = sessions.filter((s) => { const d = localDate(s.startAt); return d >= ws && d <= we && s.status !== 'CANCELLED'; });
  const planned = days.reduce((a, d) => a + d.plannedMinutes, 0);
  const closed = ss.filter((s) => s.status === 'COMPLETED' || s.status === 'INCOMPLETE' || s.status === 'MISSED');
  return {
    weekStart: ws, weekEnd: we, plannedMinutes: planned, actualMinutes: days.reduce((a, d) => a + d.actualMinutes, 0), focusMinutes: days.reduce((a, d) => a + d.focusMinutes, 0),
    completedSessions: days.reduce((a, d) => a + d.completedSessions, 0), missedSessions: days.reduce((a, d) => a + d.missedSessions, 0),
    completionRate: closed.length ? round1((closed.filter((s) => s.status === 'COMPLETED').length * 100) / closed.length) : 0, days,
    bySubject: subjects.map((s) => {
      const list = ss.filter((x) => x.subjectId === s.id);
      return { subjectId: s.id, subjectName: s.name, subjectColor: s.color, plannedMinutes: list.reduce((a, x) => a + x.plannedMinutes, 0), actualMinutes: list.reduce((a, x) => a + (x.actualMinutes ?? 0), 0) };
    }).filter((x) => x.plannedMinutes > 0 || x.actualMinutes > 0),
  };
});
on('GET', '/analytics/subjects', (): SubjectAnalytics[] =>
  subjects.map((s) => {
    const v = subjectView(s);
    const done = sessions.filter((x) => x.subjectId === s.id && (x.status === 'COMPLETED' || x.status === 'INCOMPLETE'));
    const as = assessments.filter((a) => a.subjectId === s.id);
    const last = done.sort((a, b) => b.startAt - a.startAt)[0];
    return {
      subjectId: s.id, subjectName: s.name, subjectColor: s.color, totalTopics: v.topicCount, completedTopics: v.completedTopicCount, syllabusCompletion: v.syllabusCompletion,
      averageMastery: v.averageMastery, weakTopics: v.weakTopicCount, studiedMinutesTotal: done.reduce((a, x) => a + (x.actualMinutes ?? 0), 0),
      studiedMinutesLast30Days: done.filter((x) => localDate(x.startAt) >= addDays(today(), -29)).reduce((a, x) => a + (x.actualMinutes ?? 0), 0),
      plannedMinutesNext7Days: sessions.filter((x) => x.subjectId === s.id && x.status === 'PLANNED' && localDate(x.startAt) <= addDays(today(), 6)).reduce((a, x) => a + x.plannedMinutes, 0),
      assessmentAverage: as.length ? round2(as.reduce((a, x) => a + (x.obtainedMarks * 100) / x.maxMarks, 0) / as.length) : null,
      lastStudiedOn: last ? localDate(last.startAt) : null,
    };
  }));
on('GET', '/analytics/productivity', (_m, _b, q): Productivity => {
  const n = clamp(num(q.get('days') || 30) || 30, 7, 365);
  const from = addDays(today(), -(n - 1));
  const daily = range(from, today()).map(dailyPoint);
  const ss = sessions.filter((s) => { const d = localDate(s.startAt); return d >= from && d <= today(); });
  const completed = ss.filter((s) => s.status === 'COMPLETED').length;
  const incomplete = ss.filter((s) => s.status === 'INCOMPLETE').length;
  const missed = ss.filter((s) => s.status === 'MISSED').length;
  const planned = daily.reduce((a, d) => a + d.plannedMinutes, 0);
  const actual = daily.reduce((a, d) => a + d.actualMinutes, 0);
  const logs = revisionLogs.filter((l) => l.revisedOn >= from);
  const onTime = logs.filter((l) => l.dueDate === null || l.revisedOn <= l.dueDate).length;
  const fdone = focus.filter((f) => f.status === 'COMPLETED' && localDate(f.startedAt) >= from);
  const fmins = Math.floor(fdone.reduce((a, f) => a + (f.actualSeconds ?? 0), 0) / 60);
  const months = new Map<string, { actualMinutes: number; focusMinutes: number }>();
  for (let i = 5; i >= 0; i--) {
    const d = new Date(`${today().slice(0, 7)}-01T00:00:00Z`);
    d.setUTCMonth(d.getUTCMonth() - i);
    months.set(d.toISOString().slice(0, 7), { actualMinutes: 0, focusMinutes: 0 });
  }
  for (const s of sessions) if ((s.status === 'COMPLETED' || s.status === 'INCOMPLETE') && months.has(localDate(s.startAt).slice(0, 7))) months.get(localDate(s.startAt).slice(0, 7))!.actualMinutes += s.actualMinutes ?? 0;
  for (const f of focus) if (f.status === 'COMPLETED' && months.has(localDate(f.startedAt).slice(0, 7))) months.get(localDate(f.startedAt).slice(0, 7))!.focusMinutes += Math.floor((f.actualSeconds ?? 0) / 60);
  const st = streak();
  const activeDays = daily.filter((d) => d.actualMinutes > 0).length;
  return {
    days: n, totalPlannedMinutes: planned, totalActualMinutes: actual, plannedVsActualPercent: planned ? round1((actual * 100) / planned) : 0,
    consistencyPercent: round1((activeDays * 100) / n), activeDays, currentStreak: st.current, longestStreak: st.longest,
    sessions: { completed, incomplete, missed, completionRate: completed + incomplete + missed ? round1((completed * 100) / (completed + incomplete + missed)) : 0 },
    revisions: { completed: logs.length, onTime, onTimeRate: logs.length ? round1((onTime * 100) / logs.length) : 0, overdueNow: topics.filter((t) => t.status === 'COMPLETED' && t.nextRevisionDate && t.nextRevisionDate < today()).length },
    focus: { sessions: fdone.length, totalMinutes: fmins, averageMinutes: fdone.length ? round1(fmins / fdone.length) : 0 },
    daily, monthly: [...months.entries()].map(([month, v]) => ({ month, ...v })),
    weakSubjects: subjects.map(subjectView).filter((v) => v.averageMastery !== null).sort((a, b) => a.averageMastery! - b.averageMastery!).slice(0, 4)
      .map((v) => ({ subjectId: v.id, subjectName: v.name, subjectColor: v.color, averageMastery: v.averageMastery, weakTopics: v.weakTopicCount, assessedTopics: v.assessedTopicCount, totalTopics: v.topicCount })),
    readinessTrends: exams.filter((e) => e.examDate >= today()).map((e) => {
      readiness(e);
      const s = subjectOf(e.subjectId);
      return { examId: e.id, examTitle: e.title, subjectName: s.name, subjectColor: s.color, examDate: e.examDate, points: [...(snapshots.get(e.id) ?? new Map<string, number | null>()).entries()].sort((a, b) => a[0].localeCompare(b[0])).filter(([d]) => d >= from).map(([date, score]) => ({ date, score })) };
    }),
  };
});

// notifications
on('GET', '/notifications', (_m, _b, q) => ({
  unreadCount: notifications.filter((x) => !x.read).length,
  items: notifications.filter((x) => q.get('unreadOnly') !== 'true' || !x.read).slice(0, num(q.get('limit') || 20)),
}));
on('POST', '/notifications/:id/read', (m) => {
  const n = notifications.find((x) => x.id === num(m[1])) ?? notFound('Notification');
  n.read = true;
  return n;
});
on('POST', '/notifications/read-all', () => {
  const unread = notifications.filter((x) => !x.read);
  unread.forEach((x) => (x.read = true));
  return { updated: unread.length };
});

const PUBLIC = new Set(['/health', '/auth/csrf', '/auth/login', '/auth/register']);
function handle(method: string, path: string, query: URLSearchParams, body: Body): { status: number; body?: unknown } {
  if (!PUBLIC.has(path) && !loggedIn) throw new HttpError(401, 'UNAUTHENTICATED', 'Please sign in to continue.');
  for (const [m, re, h] of routes) {
    if (m !== method) continue;
    const match = path.match(re);
    if (!match) continue;
    const result = h(match, body, query);
    if (result === undefined) return { status: 204 };
    return { status: method === 'POST' && (path === '/subjects' || path === '/exams' || path === '/assessments' || path === '/sessions' || path === '/focus/start' || path === '/auth/register' || /\/topics$/.test(path)) ? 201 : 200, body: result };
  }
  throw new HttpError(404, 'NOT_FOUND', 'Not found.');
}

// ------------------------------------------------------------------ seed data

function seed() {
  const day = today();
  const t = now();
  const addSubject = (name: string, code: string, color: string, importance: number, target: number) => {
    const s: SubjectRec = { id: newId(), name, code, color, importance, targetScore: target, description: null, archived: false, createdAt: t - 40 * DAY_MS };
    subjects.push(s);
    return s;
  };
  const addTopic = (s: SubjectRec, name: string, difficulty: number, minutes: number, status: TopicStatus) => {
    const rec: TopicRec = { id: newId(), subjectId: s.id, name, description: null, difficulty, estimatedMinutes: minutes, status, completedAt: status === 'COMPLETED' ? t - 12 * DAY_MS : null, sortOrder: topics.length + 1, mastery: null, ef: 2.5, interval: 0, reps: 0, nextRevisionDate: null, lastRevisedOn: null, createdAt: t - 40 * DAY_MS };
    topics.push(rec);
    return rec;
  };
  const ds = addSubject('Data Structures', 'CS201', '#3B82F6', 5, 85);
  const db = addSubject('Database Systems', 'CS202', '#10B981', 4, 80);
  const os = addSubject('Operating Systems', 'CS301', '#8B5CF6', 4, 75);
  const ma = addSubject('Engineering Mathematics', 'MA201', '#F59E0B', 3, 70);
  const T = {
    arrays: addTopic(ds, 'Arrays & Linked Lists', 2, 120, 'COMPLETED'),
    stacks: addTopic(ds, 'Stacks & Queues', 2, 90, 'COMPLETED'),
    trees: addTopic(ds, 'Binary Search Trees', 3, 150, 'COMPLETED'),
    avl: addTopic(ds, 'AVL Trees', 4, 180, 'IN_PROGRESS'),
    graphs: addTopic(ds, 'Graph Algorithms', 5, 240, 'NOT_STARTED'),
    er: addTopic(db, 'ER Modelling', 2, 90, 'COMPLETED'),
    sql: addTopic(db, 'SQL Joins & Subqueries', 3, 150, 'COMPLETED'),
    norm: addTopic(db, 'Normalization', 4, 150, 'IN_PROGRESS'),
    tx: addTopic(db, 'Transactions & Concurrency', 4, 180, 'NOT_STARTED'),
    idx: addTopic(db, 'Indexing & B+ Trees', 3, 120, 'NOT_STARTED'),
    proc: addTopic(os, 'Processes & Threads', 2, 120, 'COMPLETED'),
    sched: addTopic(os, 'CPU Scheduling', 3, 150, 'COMPLETED'),
    dead: addTopic(os, 'Deadlocks', 4, 150, 'IN_PROGRESS'),
    mem: addTopic(os, 'Memory Management', 4, 180, 'NOT_STARTED'),
    fs: addTopic(os, 'File Systems', 3, 120, 'NOT_STARTED'),
    lin: addTopic(ma, 'Linear Algebra', 3, 150, 'COMPLETED'),
    calc: addTopic(ma, 'Multivariable Calculus', 4, 180, 'IN_PROGRESS'),
    prob: addTopic(ma, 'Probability', 3, 150, 'NOT_STARTED'),
    lap: addTopic(ma, 'Laplace Transforms', 4, 120, 'NOT_STARTED'),
    num: addTopic(ma, 'Numerical Methods', 3, 120, 'NOT_STARTED'),
  };
  const exam = (s: SubjectRec, title: string, type: ExamType, inDays: number) => exams.push({ id: newId(), subjectId: s.id, title, examType: type, examDate: addDays(day, inDays), startTime: '10:00', durationMinutes: 180, location: 'Exam Hall B', targetScore: s.targetScore, notes: null, createdAt: t - 30 * DAY_MS });
  exam(db, 'DBMS Mid-term', 'MIDTERM', 6);
  exam(ds, 'Data Structures End Semester', 'FINAL', 12);
  exam(ma, 'Mathematics Unit Test', 'QUIZ', 9);
  exam(os, 'Operating Systems Final', 'FINAL', 20);

  // Assessments with topic scores (mastery is computed from them, exactly like the backend)
  const assess = (s: SubjectRec, title: string, type: AssessmentType, ago: number, scores: [TopicRec, number][]) => {
    const max = scores.length * 20;
    const got = scores.reduce((a, [, p]) => a + Math.round((p / 100) * 20), 0);
    assessments.push({ id: newId(), subjectId: s.id, examId: null, title, assessmentType: type, assessedOn: addDays(day, -ago), maxMarks: max || 50, obtainedMarks: max ? got : 0, notes: null, topicScores: scores.map(([tp, p]) => ({ topicId: tp.id, maxMarks: 20, obtainedMarks: Math.round((p / 100) * 20) })), createdAt: t - ago * DAY_MS });
  };
  assess(ds, 'DS Quiz 1', 'QUIZ', 24, [[T.arrays, 70], [T.stacks, 75], [T.trees, 55]]);
  assess(ds, 'DS Quiz 2', 'QUIZ', 10, [[T.arrays, 85], [T.trees, 65], [T.avl, 35]]);
  assess(ds, 'DS Mock Test 1', 'MOCK_TEST', 4, [[T.trees, 70], [T.avl, 30], [T.stacks, 80]]);
  assess(db, 'DBMS Quiz 1', 'QUIZ', 18, [[T.er, 80], [T.sql, 60]]);
  assess(db, 'DBMS Assignment', 'ASSIGNMENT', 7, [[T.sql, 70], [T.norm, 35]]);
  assess(os, 'OS Quiz 1', 'QUIZ', 15, [[T.proc, 90], [T.sched, 75]]);
  assess(os, 'OS Quiz 2', 'QUIZ', 5, [[T.sched, 80], [T.dead, 45]]);
  assess(ma, 'Maths Test 1', 'EXAM', 12, [[T.lin, 60], [T.calc, 40]]);
  for (const tp of topics) recomputeMastery(tp.id);
  // Revision state (SM-2) for completed topics
  const rev = (tp: TopicRec, history: [RevisionRating, number][]) => {
    for (const [rating, ago] of history) sm2(tp, rating, addDays(day, -ago));
  };
  rev(T.arrays, [['GOOD', 20], ['GOOD', 19], ['EASY', 13]]);
  rev(T.stacks, [['GOOD', 18], ['HARD', 17]]);
  rev(T.trees, [['GOOD', 11], ['GOOD', 10]]);
  rev(T.er, [['GOOD', 16], ['GOOD', 15]]);
  rev(T.sql, [['HARD', 9], ['GOOD', 8]]);
  rev(T.proc, [['EASY', 14]]);
  rev(T.sched, [['GOOD', 9]]);
  T.lin.nextRevisionDate = addDays(day, 0);
  for (const l of revisionLogs) l.at = Date.parse(`${l.revisedOn}T13:00:00Z`);

  // Weekly availability
  windows = [1, 2, 3, 4, 5].map((d) => ({ id: newId(), dayOfWeek: d, startTime: '18:00', endTime: '21:00' }));
  windows.push({ id: newId(), dayOfWeek: 6, startTime: '09:00', endTime: '12:30' }, { id: newId(), dayOfWeek: 7, startTime: '10:00', endTime: '12:00' });

  // Past study sessions (last 3 weeks): mostly completed, a few incomplete or missed
  const pastTopics = [T.trees, T.avl, T.sql, T.norm, T.sched, T.dead, T.lin, T.calc, T.er, T.proc, T.arrays, T.stacks];
  let k = 0;
  for (let ago = 21; ago >= 1; ago--) {
    const date = addDays(day, -ago);
    const count = isoDow(date) >= 6 ? 3 : (ago % 5 === 0 ? 1 : 2);
    for (let i = 0; i < count; i++) {
      const tp = pastTopics[k++ % pastTopics.length];
      const startMin = (isoDow(date) >= 6 ? 9 * 60 + 30 : 18 * 60) + i * 60;
      const start = toInstant(date, hhmm(startMin), tz());
      const roll = (ago * 7 + i * 3) % 11;
      const status: SessionStatus = roll === 0 ? 'MISSED' : roll === 4 ? 'INCOMPLETE' : 'COMPLETED';
      const actual = status === 'COMPLETED' ? 45 + (roll % 3) * 5 : status === 'INCOMPLETE' ? 25 : null;
      sessions.push({ id: newId(), planId: null, subjectId: tp.subjectId, topicId: tp.id, sessionType: 'STUDY', source: 'GENERATED', status, startAt: start, endAt: start + 50 * MIN, plannedMinutes: 50, actualMinutes: actual, startedAt: actual ? start : null, completedAt: actual ? start + actual * MIN : null, locked: false, priorityScore: 60, reason: 'Generated plan', notes: null, rescheduledFromId: null });
    }
  }
  // Focus sessions on most of the last 14 days
  for (let ago = 13; ago >= 0; ago--) {
    if (ago % 4 === 3) continue;
    const date = addDays(day, -ago);
    const n = ago === 0 ? 1 : 1 + (ago % 3);
    for (let i = 0; i < n; i++) {
      const start = toInstant(date, hhmm(7 * 60 + i * 30), tz());
      if (start + 25 * MIN > t) continue;
      const tp = pastTopics[(ago + i) % pastTopics.length];
      focus.push({ id: newId(), status: 'COMPLETED', subjectId: tp.subjectId, topicId: tp.id, studySessionId: null, plannedMinutes: 25, startedAt: start, pausedAt: null, pausedSeconds: 0, pauseCount: 0, endedAt: start + 25 * MIN, actualSeconds: 1500, notes: null });
    }
  }
  focus.sort((a, b) => b.startedAt - a.startedAt);

  // Readiness history: a gently rising trend for the last 10 days
  for (const e of exams) {
    const current = readiness(e, false).readinessScore ?? 40;
    const map = new Map<string, number | null>();
    for (let ago = 10; ago >= 1; ago--) map.set(addDays(day, -ago), round1(Math.max(5, current - ago * 1.6 + ((ago * 13) % 5) - 2)));
    snapshots.set(e.id, map);
  }

  // Plan for the next 7 days
  generate(day, 7);
  notify('REVISION_DUE', 'Revisions due', 'You have topics due for revision today - open the Revision page.', '/revision');
  notify('EXAM_UPCOMING', 'Exam in 6 days', 'DBMS Mid-term is coming up. Your planner already prioritises its weak topics.', '/exams');
}

// ------------------------------------------------------------------ fetch interception

const API = '/api/v1';
export function installMockServer() {
  seed();
  const realFetch = window.fetch.bind(window);
  window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.href : input.url, window.location.href);
    const at = url.pathname.indexOf(API);
    if (at < 0) return realFetch(input, init);
    const path = url.pathname.slice(at + API.length) || '/';
    const method = (init?.method ?? 'GET').toUpperCase();
    let body: Body = {};
    if (typeof init?.body === 'string' && init.body) {
      try {
        body = JSON.parse(init.body) as Body;
      } catch {
        body = {};
      }
    }
    await new Promise((r) => setTimeout(r, 120)); // feel like a network call
    try {
      const result = handle(method, path, url.searchParams, body);
      if (result.status === 204) return new Response(null, { status: 204 });
      return new Response(JSON.stringify(result.body), { status: result.status, headers: { 'Content-Type': 'application/json' } });
    } catch (e) {
      const err = e instanceof HttpError ? e : new HttpError(500, 'INTERNAL_ERROR', 'Something went wrong in the demo.');
      if (!(e instanceof HttpError)) console.error(e);
      return new Response(
        JSON.stringify({ timestamp: iso(now()), status: err.status, error: 'Error', code: err.code, message: err.message, path: API + path, fieldErrors: err.fieldErrors }),
        { status: err.status, headers: { 'Content-Type': 'application/json' } },
      );
    }
  };
}
