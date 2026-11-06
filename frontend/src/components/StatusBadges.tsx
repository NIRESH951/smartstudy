import { AlertTriangle, CheckCircle2, Circle, CircleDashed, Clock, Lock, TrendingDown, TrendingUp, XCircle } from 'lucide-react';
import { Badge } from './ui/Feedback';
import {
  MASTERY_LABELS,
  READINESS_LABELS,
  SESSION_STATUS_LABELS,
  SESSION_TYPE_LABELS,
  TOPIC_STATUS_LABELS,
} from '../utils/format';
import type {
  DataSufficiency,
  MasteryLevel,
  ReadinessLevel,
  SessionStatus,
  SessionType,
  TopicStatus,
  Trend,
} from '../types/api';

// Every status badge pairs colour with an icon and a text label, so meaning never depends on colour alone.

export function MasteryBadge({ level, score }: { level: MasteryLevel; score?: number | null }) {
  const tone = { WEAK: 'red', NEEDS_IMPROVEMENT: 'amber', GOOD: 'blue', STRONG: 'emerald', NOT_ASSESSED: 'slate' } as const;
  const icon =
    level === 'WEAK' ? (
      <AlertTriangle className="h-3 w-3" aria-hidden />
    ) : level === 'NOT_ASSESSED' ? (
      <CircleDashed className="h-3 w-3" aria-hidden />
    ) : level === 'STRONG' ? (
      <CheckCircle2 className="h-3 w-3" aria-hidden />
    ) : null;
  return (
    <Badge tone={tone[level]} icon={icon}>
      {MASTERY_LABELS[level]}
      {score !== undefined && score !== null ? ` · ${Math.round(score)}%` : ''}
    </Badge>
  );
}

export function ReadinessBadge({ level }: { level: ReadinessLevel | null }) {
  if (!level) return <Badge tone="slate">No data</Badge>;
  const tone = { WELL_PREPARED: 'emerald', ON_TRACK: 'blue', NEEDS_WORK: 'amber', AT_RISK: 'red' } as const;
  return <Badge tone={tone[level]}>{READINESS_LABELS[level]}</Badge>;
}

export function SufficiencyBadge({ value }: { value: DataSufficiency }) {
  if (value === 'SUFFICIENT') return <Badge tone="emerald" icon={<CheckCircle2 className="h-3 w-3" aria-hidden />}>Complete data</Badge>;
  if (value === 'PARTIAL') return <Badge tone="amber" icon={<AlertTriangle className="h-3 w-3" aria-hidden />}>Partial data</Badge>;
  return <Badge tone="red" icon={<AlertTriangle className="h-3 w-3" aria-hidden />}>Insufficient data</Badge>;
}

export function TopicStatusBadge({ status }: { status: TopicStatus }) {
  if (status === 'COMPLETED') return <Badge tone="emerald" icon={<CheckCircle2 className="h-3 w-3" aria-hidden />}>{TOPIC_STATUS_LABELS[status]}</Badge>;
  if (status === 'IN_PROGRESS') return <Badge tone="blue" icon={<Clock className="h-3 w-3" aria-hidden />}>{TOPIC_STATUS_LABELS[status]}</Badge>;
  return <Badge tone="slate" icon={<Circle className="h-3 w-3" aria-hidden />}>{TOPIC_STATUS_LABELS[status]}</Badge>;
}

export function SessionStatusBadge({ status }: { status: SessionStatus }) {
  const tone = {
    PLANNED: 'slate',
    IN_PROGRESS: 'blue',
    COMPLETED: 'emerald',
    INCOMPLETE: 'amber',
    MISSED: 'red',
    CANCELLED: 'slate',
  } as const;
  const icon =
    status === 'COMPLETED' ? <CheckCircle2 className="h-3 w-3" aria-hidden /> :
    status === 'MISSED' ? <XCircle className="h-3 w-3" aria-hidden /> :
    status === 'IN_PROGRESS' ? <Clock className="h-3 w-3" aria-hidden /> :
    status === 'INCOMPLETE' ? <AlertTriangle className="h-3 w-3" aria-hidden /> : null;
  return <Badge tone={tone[status]} icon={icon}>{SESSION_STATUS_LABELS[status]}</Badge>;
}

export function SessionTypeBadge({ type }: { type: SessionType }) {
  const tone = { STUDY: 'blue', REVISION: 'violet', PRACTICE: 'orange' } as const;
  return <Badge tone={tone[type]}>{SESSION_TYPE_LABELS[type]}</Badge>;
}

export function LockedBadge() {
  return <Badge tone="slate" icon={<Lock className="h-3 w-3" aria-hidden />}>Locked</Badge>;
}

export function TrendIndicator({ trend }: { trend: Trend }) {
  if (trend === 'IMPROVING') {
    return <span className="inline-flex items-center gap-1 text-xs font-medium text-emerald-700"><TrendingUp className="h-3.5 w-3.5" aria-hidden />Improving</span>;
  }
  if (trend === 'DECLINING') {
    return <span className="inline-flex items-center gap-1 text-xs font-medium text-red-700"><TrendingDown className="h-3.5 w-3.5" aria-hidden />Declining</span>;
  }
  if (trend === 'STABLE') return <span className="text-xs font-medium text-slate-500">Stable</span>;
  return <span className="text-xs font-medium text-slate-500">First result</span>;
}
