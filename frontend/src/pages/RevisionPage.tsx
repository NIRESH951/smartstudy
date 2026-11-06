import { useState } from 'react';
import { CheckCircle2, Info, RotateCcw } from 'lucide-react';
import { useRefreshAll, useRevisionHistory, useRevisionsDue } from '../hooks/queries';
import { useToast } from '../context/ToastContext';
import { revisionApi } from '../services/api';
import { ApiError, errorMessage } from '../services/http';
import { Card, CardBody, CardHeader, PageHeader, StatCard } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { Badge, ColorDot, EmptyState, ErrorState, LoadingBlock } from '../components/ui/Feedback';
import { Modal } from '../components/ui/Modal';
import { FormError, TextAreaField, TextField } from '../components/ui/Form';
import { MasteryBadge } from '../components/StatusBadges';
import { RatingPicker } from '../components/sessions/CompleteSessionModal';
import { formatDate } from '../utils/format';
import type { RevisionItem, RevisionRating, RevisionResult } from '../types/api';

function RevisionRow({ item, onRevise }: { item: RevisionItem; onRevise: (item: RevisionItem) => void }) {
  return (
    <li className="flex flex-col gap-3 py-3 sm:flex-row sm:items-center">
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <ColorDot color={item.subjectColor} />
          <p className="text-sm font-medium text-slate-900">{item.topicName}</p>
          <MasteryBadge level={item.masteryLevel} score={item.masteryScore} />
        </div>
        <p className="mt-0.5 text-xs text-slate-500">
          {item.subjectName} · review #{item.repetitions + 1} · current interval {item.intervalDays} day{item.intervalDays === 1 ? '' : 's'} · ease {item.easeFactor.toFixed(2)}
          {item.lastRevisedOn && ` · last revised ${formatDate(item.lastRevisedOn)}`}
        </p>
      </div>
      <div className="flex items-center gap-3">
        {item.dueStatus === 'OVERDUE' && <Badge tone="red">{item.daysOverdue} day{item.daysOverdue === 1 ? '' : 's'} overdue</Badge>}
        {item.dueStatus === 'DUE_TODAY' && <Badge tone="violet">Due today</Badge>}
        {item.dueStatus === 'UPCOMING' && <Badge>Due {formatDate(item.nextRevisionDate)}</Badge>}
        <Button size="sm" variant={item.dueStatus === 'UPCOMING' ? 'secondary' : 'primary'} icon={<RotateCcw className="h-3.5 w-3.5" aria-hidden />} onClick={() => onRevise(item)}>
          Revise
        </Button>
      </div>
    </li>
  );
}

export function RevisionPage() {
  const due = useRevisionsDue();
  const history = useRevisionHistory();
  const refreshAll = useRefreshAll();
  const toast = useToast();
  const [active, setActive] = useState<RevisionItem | null>(null);
  const [rating, setRating] = useState<RevisionRating | null>(null);
  const [duration, setDuration] = useState('25');
  const [notes, setNotes] = useState('');
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [lastResult, setLastResult] = useState<RevisionResult | null>(null);

  const open = (item: RevisionItem) => {
    setActive(item);
    setRating(null);
    setDuration('25');
    setNotes('');
    setFormError(null);
  };

  const submit = async () => {
    if (!active) return;
    if (!rating) {
      setFormError('Choose how well you remembered the topic.');
      return;
    }
    const minutes = duration.trim() === '' ? null : Number(duration);
    if (minutes !== null && (!Number.isInteger(minutes) || minutes < 1 || minutes > 600)) {
      setFormError('Duration must be whole minutes between 1 and 600.');
      return;
    }
    setSaving(true);
    setFormError(null);
    try {
      const result = await revisionApi.complete(active.topicId, { rating, durationMinutes: minutes, notes: notes.trim() || null });
      setLastResult(result);
      setActive(null);
      toast.success(`${result.topicName}: next revision on ${formatDate(result.nextRevisionDate)}.`);
      await refreshAll();
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : errorMessage(error));
    } finally {
      setSaving(false);
    }
  };

  const data = due.data;

  return (
    <>
      <PageHeader title="Revision" description="Spaced repetition (simplified SM-2): the better you remember a topic, the longer until you see it again." />

      {due.isLoading && <Card className="p-6"><LoadingBlock rows={4} /></Card>}
      {due.error && <Card><ErrorState error={due.error} onRetry={() => due.refetch()} /></Card>}

      {data && (
        <>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <StatCard label="Due now" value={data.dueCount} hint="Today plus overdue" accent="emerald" />
            <StatCard label="Overdue" value={data.overdueCount} accent="red" />
            <StatCard label="Next 7 days" value={data.upcomingCount} accent="blue" />
            <StatCard label="Revised this week" value={data.completedThisWeek} accent="amber" />
          </div>

          {lastResult && (
            <Card className="mt-6 border-emerald-200 bg-emerald-50/50">
              <CardBody className="flex items-start gap-3">
                <CheckCircle2 className="mt-0.5 h-5 w-5 text-emerald-600" aria-hidden />
                <div className="text-sm">
                  <p className="font-medium text-slate-900">{lastResult.topicName} revised</p>
                  <p className="text-slate-700">{lastResult.explanation}</p>
                </div>
              </CardBody>
            </Card>
          )}

          <div className="mt-6 grid gap-6 xl:grid-cols-3">
            <div className="space-y-6 xl:col-span-2">
              <Card>
                <CardHeader title="Due and overdue" description={`As of ${formatDate(data.today, { weekday: 'long' })}`} />
                <CardBody>
                  {data.overdue.length + data.dueToday.length === 0 ? (
                    <EmptyState compact icon={<CheckCircle2 className="h-5 w-5" aria-hidden />} title="Nothing due right now" description="Completed topics enter the revision cycle automatically." />
                  ) : (
                    <ul className="divide-y divide-slate-100">
                      {[...data.overdue, ...data.dueToday].map((item) => (
                        <RevisionRow key={item.topicId} item={item} onRevise={open} />
                      ))}
                    </ul>
                  )}
                </CardBody>
              </Card>
              <Card>
                <CardHeader title="Coming up (next 7 days)" />
                <CardBody>
                  {data.upcoming.length === 0 ? (
                    <EmptyState compact title="No revisions scheduled this week" />
                  ) : (
                    <ul className="divide-y divide-slate-100">
                      {data.upcoming.map((item) => (
                        <RevisionRow key={item.topicId} item={item} onRevise={open} />
                      ))}
                    </ul>
                  )}
                </CardBody>
              </Card>
            </div>

            <div className="space-y-6">
              <Card>
                <CardHeader title="How intervals are calculated" icon={<Info className="h-4 w-4" aria-hidden />} />
                <CardBody>
                  <table className="w-full text-xs">
                    <thead className="text-left text-slate-500">
                      <tr>
                        <th className="pb-2 font-medium">Rating</th>
                        <th className="pb-2 font-medium">Ease</th>
                        <th className="pb-2 font-medium">Next interval</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100 text-slate-700">
                      <tr><td className="py-1.5 font-medium">Again</td><td>-0.20</td><td>1 day, cycle restarts</td></tr>
                      <tr><td className="py-1.5 font-medium">Hard</td><td>-0.15</td><td>interval × 1.2</td></tr>
                      <tr><td className="py-1.5 font-medium">Good</td><td>±0</td><td>1 → 6 → interval × ease</td></tr>
                      <tr><td className="py-1.5 font-medium">Easy</td><td>+0.15</td><td>4 → 8 → interval × ease × 1.3</td></tr>
                    </tbody>
                  </table>
                  <p className="mt-3 text-xs text-slate-500">
                    Ease stays between 1.30 and 5.00; intervals are capped at 365 days. A late review is scheduled from the day you actually revise.
                  </p>
                </CardBody>
              </Card>
              <Card>
                <CardHeader title="Recent revisions" />
                <CardBody>
                  {history.isLoading && <LoadingBlock />}
                  {history.data && history.data.length === 0 && <EmptyState compact title="No revisions yet" />}
                  <ul className="space-y-3">
                    {(history.data ?? []).slice(0, 10).map((entry) => (
                      <li key={entry.id} className="text-sm">
                        <div className="flex items-center justify-between gap-2">
                          <span className="truncate font-medium text-slate-800">{entry.topicName}</span>
                          <Badge tone={entry.rating === 'AGAIN' ? 'red' : entry.rating === 'HARD' ? 'amber' : entry.rating === 'GOOD' ? 'blue' : 'emerald'}>
                            {entry.rating.charAt(0) + entry.rating.slice(1).toLowerCase()}
                          </Badge>
                        </div>
                        <p className="text-xs text-slate-500">
                          {formatDate(entry.revisedOn)} · {entry.previousIntervalDays} → {entry.newIntervalDays} days · {entry.onTime ? 'on time' : 'late'}
                        </p>
                      </li>
                    ))}
                  </ul>
                </CardBody>
              </Card>
            </div>
          </div>
        </>
      )}

      <Modal
        open={active !== null}
        title={`Revise: ${active?.topicName ?? ''}`}
        description="After reviewing your notes, rate honestly how well you remembered the topic."
        onClose={() => setActive(null)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setActive(null)} disabled={saving}>Cancel</Button>
            <Button onClick={submit} loading={saving}>Save revision</Button>
          </>
        }
      >
        <div className="space-y-4">
          <FormError message={formError} />
          <RatingPicker value={rating} onChange={setRating} />
          <TextField label="Minutes spent" type="number" min={1} max={600} value={duration} onChange={(e) => setDuration(e.target.value)} hint="Optional" />
          <TextAreaField label="Notes" value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={500} />
        </div>
      </Modal>
    </>
  );
}
