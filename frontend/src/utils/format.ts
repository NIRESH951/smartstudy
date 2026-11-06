// Formatting helpers. Dates/times from the API are already in the student's time zone
// (localDate, localStartTime) or are UTC instants that we format with the student's zone.

import type { MasteryLevel, ReadinessLevel, SessionStatus, SessionType, TopicStatus } from '../types/api';

/** 135 -> "2 h 15 min", 45 -> "45 min", 0 -> "0 min". */
export function formatMinutes(minutes: number | null | undefined): string {
  const value = Math.max(0, Math.round(minutes ?? 0));
  const hours = Math.floor(value / 60);
  const rest = value % 60;
  if (hours === 0) return `${rest} min`;
  if (rest === 0) return `${hours} h`;
  return `${hours} h ${rest} min`;
}

/** 135 -> "2.3". */
export function toHours(minutes: number | null | undefined): number {
  return Math.round(((minutes ?? 0) / 60) * 10) / 10;
}

/** "18:00:00" or "18:00" -> "18:00". */
export function formatTime(time: string | null | undefined): string {
  if (!time) return '';
  return time.slice(0, 5);
}

/** 1500 -> "25:00". */
export function formatClock(totalSeconds: number): string {
  const seconds = Math.max(0, Math.floor(totalSeconds));
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

/** Local calendar date "YYYY-MM-DD" -> "Mon, 2 Nov". Parsed as a plain date (no time-zone shift). */
export function formatDate(date: string | null | undefined, options: Intl.DateTimeFormatOptions = {}): string {
  if (!date) return '-';
  const [y, m, d] = date.split('-').map(Number);
  const value = new Date(Date.UTC(y, m - 1, d));
  return new Intl.DateTimeFormat('en-IN', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    timeZone: 'UTC',
    ...options,
  }).format(value);
}

/** UTC instant -> date & time in the student's time zone. */
export function formatInstant(instant: string | null | undefined, timeZone: string, withDate = true): string {
  if (!instant) return '-';
  return new Intl.DateTimeFormat('en-IN', {
    timeZone,
    day: withDate ? 'numeric' : undefined,
    month: withDate ? 'short' : undefined,
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(instant));
}

/** Relative description such as "5 min ago", "3 h ago", "2 days ago". */
export function timeAgo(instant: string, now: Date = new Date()): string {
  const diff = Math.max(0, now.getTime() - new Date(instant).getTime());
  const minutes = Math.floor(diff / 60000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} h ago`;
  const days = Math.floor(hours / 24);
  return days === 1 ? 'yesterday' : `${days} days ago`;
}

/** "Today" in a time zone as "YYYY-MM-DD". */
export function todayIn(timeZone: string, now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(now);
}

/** Adds days to a "YYYY-MM-DD" date. */
export function addDays(date: string, days: number): string {
  const [y, m, d] = date.split('-').map(Number);
  const value = new Date(Date.UTC(y, m - 1, d + days));
  return value.toISOString().slice(0, 10);
}

/** ISO day of week (1 = Monday ... 7 = Sunday) of a "YYYY-MM-DD" date. */
export function isoDayOfWeek(date: string): number {
  const [y, m, d] = date.split('-').map(Number);
  const day = new Date(Date.UTC(y, m - 1, d)).getUTCDay();
  return day === 0 ? 7 : day;
}

/** Monday of the week containing the date. */
export function weekStart(date: string): string {
  return addDays(date, 1 - isoDayOfWeek(date));
}

export function formatPercent(value: number | null | undefined, digits = 0): string {
  if (value === null || value === undefined || Number.isNaN(value)) return '-';
  return `${value.toFixed(digits)}%`;
}

export function daysLabel(days: number): string {
  if (days === 0) return 'Today';
  if (days === 1) return 'Tomorrow';
  if (days < 0) return `${Math.abs(days)} days ago`;
  return `In ${days} days`;
}

export const DAY_NAMES = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

export const MASTERY_LABELS: Record<MasteryLevel, string> = {
  WEAK: 'Weak',
  NEEDS_IMPROVEMENT: 'Needs improvement',
  GOOD: 'Good',
  STRONG: 'Strong',
  NOT_ASSESSED: 'Not assessed',
};

export const READINESS_LABELS: Record<ReadinessLevel, string> = {
  WELL_PREPARED: 'Well prepared',
  ON_TRACK: 'On track',
  NEEDS_WORK: 'Needs work',
  AT_RISK: 'At risk',
};

export const TOPIC_STATUS_LABELS: Record<TopicStatus, string> = {
  NOT_STARTED: 'Not started',
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed',
};

export const SESSION_STATUS_LABELS: Record<SessionStatus, string> = {
  PLANNED: 'Planned',
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed',
  INCOMPLETE: 'Incomplete',
  MISSED: 'Missed',
  CANCELLED: 'Cancelled',
};

export const SESSION_TYPE_LABELS: Record<SessionType, string> = {
  STUDY: 'Study',
  REVISION: 'Revision',
  PRACTICE: 'Practice',
};

/** A list of IANA time zones supported by the browser (falls back to a short list). */
export function timeZones(): string[] {
  const withSupported = Intl as unknown as { supportedValuesOf?: (key: string) => string[] };
  try {
    const zones = withSupported.supportedValuesOf?.('timeZone');
    if (zones && zones.length > 0) return zones.includes('UTC') ? zones : ['UTC', ...zones];
  } catch {
    // ignore - older browsers
  }
  return ['UTC', 'Asia/Kolkata', 'Asia/Dubai', 'Asia/Singapore', 'Europe/London', 'Europe/Berlin', 'America/New_York',
    'America/Los_Angeles', 'Australia/Sydney'];
}

export function browserTimeZone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
  } catch {
    return 'UTC';
  }
}
