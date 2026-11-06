import { useState } from 'react';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { ColorDot } from './ui/Feedback';
import { MasteryBadge, SessionTypeBadge } from './StatusBadges';
import { formatMinutes } from '../utils/format';
import type { PriorityFactors, Recommendation } from '../types/api';

const FACTORS: { key: keyof PriorityFactors; label: string }[] = [
  { key: 'weakness', label: 'Weakness' },
  { key: 'examUrgency', label: 'Exam urgency' },
  { key: 'difficulty', label: 'Difficulty' },
  { key: 'revisionOverdue', label: 'Revision overdue' },
  { key: 'subjectImportance', label: 'Subject importance' },
];

/** Shows each factor of the priority formula (0-100) and, when weights are given, its weighted contribution. */
export function PriorityBreakdown({ factors, weights }: { factors: PriorityFactors; weights?: PriorityFactors }) {
  const totalWeight = weights ? FACTORS.reduce((sum, f) => sum + weights[f.key], 0) : 0;
  return (
    <dl className="grid gap-2 sm:grid-cols-5">
      {FACTORS.map((factor) => (
        <div key={factor.key}>
          <dt className="text-[11px] font-medium uppercase tracking-wide text-slate-500">{factor.label}</dt>
          <dd className="mt-1">
            <div className="h-1.5 w-full overflow-hidden rounded-full bg-slate-100" aria-hidden>
              <div className="h-full rounded-full bg-blue-500" style={{ width: `${factors[factor.key]}%` }} />
            </div>
            <span className="mt-0.5 block text-xs tabular-nums text-slate-700">
              {Math.round(factors[factor.key])}
              {weights && totalWeight > 0 && (
                <span className="text-slate-400">
                  {' '}
                  × {weights[factor.key].toFixed(2)} = {((factors[factor.key] * weights[factor.key]) / totalWeight).toFixed(1)}
                </span>
              )}
            </span>
          </dd>
        </div>
      ))}
    </dl>
  );
}

/** "What to study next and why": ranked topics with their priority explanation. */
export function RecommendationList({ items, weights }: { items: Recommendation[]; weights?: PriorityFactors }) {
  const [expanded, setExpanded] = useState<string | null>(null);
  return (
    <ol className="divide-y divide-slate-100">
      {items.map((item, index) => {
        const key = `${item.topicId}-${item.sessionType}`;
        const open = expanded === key;
        return (
          <li key={key} className="py-3">
            <div className="flex items-start gap-3">
              <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-navy-900 text-xs font-semibold text-white">
                {index + 1}
              </span>
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <ColorDot color={item.subjectColor} />
                  <p className="text-sm font-medium text-slate-900">{item.topicName}</p>
                  <SessionTypeBadge type={item.sessionType} />
                  <MasteryBadge level={item.masteryLevel} score={item.masteryScore} />
                </div>
                <p className="mt-0.5 text-xs text-slate-500">
                  {item.subjectName}
                  {item.sessionType === 'STUDY' && ` · ${formatMinutes(item.remainingMinutes)} left`}
                </p>
                <ul className="mt-1.5 flex flex-wrap gap-1.5">
                  {item.reasons.map((reason) => (
                    <li key={reason} className="rounded-md bg-slate-100 px-2 py-0.5 text-xs text-slate-600">
                      {reason}
                    </li>
                  ))}
                </ul>
                {open && (
                  <div className="mt-3 rounded-lg border border-slate-100 bg-slate-50 p-3">
                    <PriorityBreakdown factors={item.factors} weights={weights} />
                  </div>
                )}
              </div>
              <div className="flex shrink-0 flex-col items-end">
                <span className="text-lg font-semibold tabular-nums text-slate-900">{item.priority.toFixed(1)}</span>
                <span className="text-[11px] text-slate-500">priority</span>
                <button
                  type="button"
                  onClick={() => setExpanded(open ? null : key)}
                  className="mt-1 inline-flex items-center gap-0.5 text-xs font-medium text-emerald-700 hover:text-emerald-800"
                  aria-expanded={open}
                >
                  {open ? 'Hide' : 'Why?'}
                  {open ? <ChevronUp className="h-3 w-3" aria-hidden /> : <ChevronDown className="h-3 w-3" aria-hidden />}
                </button>
              </div>
            </div>
          </li>
        );
      })}
    </ol>
  );
}
