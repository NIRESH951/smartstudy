import { useState } from 'react';
import { Link } from 'react-router';
import { AlertTriangle, CalendarDays, CalendarPlus, Info, Plus, RefreshCcw, Sparkles } from 'lucide-react';
import { usePriorities, useRefreshAll, useToday } from '../hooks/queries';
import { useUser } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { plannerApi } from '../services/api';
import { errorMessage } from '../services/http';
import { Card, CardBody, CardHeader, PageHeader } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { EmptyState, ErrorState, LoadingBlock, ProgressBar } from '../components/ui/Feedback';
import { Modal } from '../components/ui/Modal';
import { FormError, TextField } from '../components/ui/Form';
import { SessionList } from '../components/sessions/SessionList';
import { SessionFormModal } from '../components/sessions/SessionFormModal';
import { RecommendationList } from '../components/RecommendationList';
import { formatDate, formatMinutes, todayIn } from '../utils/format';
import type { PlanResponse } from '../types/api';

function PlannerTabs() {
  return (
    <div className="mb-4 inline-flex rounded-lg bg-slate-100 p-1" role="tablist" aria-label="Planner views">
      <span role="tab" aria-selected="true" className="rounded-md bg-white px-3 py-1.5 text-sm font-medium text-slate-900 shadow-sm">
        Plan
      </span>
      <Link role="tab" aria-selected="false" to="/planner/calendar" className="rounded-md px-3 py-1.5 text-sm font-medium text-slate-500 hover:text-slate-800">
        Calendar
      </Link>
    </div>
  );
}

export function PlannerPage() {
  const user = useUser();
  const toast = useToast();
  const refreshAll = useRefreshAll();
  const today = todayIn(user.timeZone);
  const { data, isLoading, error, refetch } = useToday();
  const priorities = usePriorities();
  const [generateOpen, setGenerateOpen] = useState(false);
  const [addOpen, setAddOpen] = useState(false);
  const [startDate, setStartDate] = useState(today);
  const [days, setDays] = useState('7');
  const [formError, setFormError] = useState<string | null>(null);
  const [working, setWorking] = useState(false);
  const [lastResult, setLastResult] = useState<PlanResponse | null>(null);
  const [showAllPriorities, setShowAllPriorities] = useState(false);

  const generate = async () => {
    const dayCount = Number(days);
    if (!Number.isInteger(dayCount) || dayCount < 1 || dayCount > 28) {
      setFormError('Choose between 1 and 28 days.');
      return;
    }
    setWorking(true);
    setFormError(null);
    try {
      const result = await plannerApi.generate({ startDate, days: dayCount });
      setLastResult(result);
      setGenerateOpen(false);
      toast.success(`Plan ready: ${result.sessionCount} sessions (${formatMinutes(result.totalPlannedMinutes)}).`);
      await refreshAll();
    } catch (caught) {
      setFormError(errorMessage(caught));
    } finally {
      setWorking(false);
    }
  };

  const regenerate = async () => {
    setWorking(true);
    try {
      const result = await plannerApi.regenerate();
      setLastResult(result);
      toast.success('Plan updated with your latest progress.');
      await refreshAll();
    } catch (caught) {
      toast.error(errorMessage(caught));
    } finally {
      setWorking(false);
    }
  };

  const plan = data?.activePlan ?? null;

  return (
    <>
      <PageHeader
        title="Study Planner"
        description="Your adaptive timetable. Missed or unfinished work is rescheduled automatically."
        actions={
          <>
            <Button variant="secondary" icon={<Plus className="h-4 w-4" aria-hidden />} onClick={() => setAddOpen(true)}>
              Add session
            </Button>
            {plan && (
              <Button variant="secondary" icon={<RefreshCcw className="h-4 w-4" aria-hidden />} onClick={regenerate} loading={working && !generateOpen}>
                Regenerate
              </Button>
            )}
            <Button
              icon={<Sparkles className="h-4 w-4" aria-hidden />}
              onClick={() => {
                setStartDate(today);
                setFormError(null);
                setGenerateOpen(true);
              }}
            >
              Generate plan
            </Button>
          </>
        }
      />
      <PlannerTabs />

      {lastResult && (lastResult.warnings.length > 0 || lastResult.unscheduled.length > 0) && (
        <Card className="mb-6 border-amber-200">
          <CardHeader
            title="Notes about your new plan"
            icon={<AlertTriangle className="h-4 w-4 text-amber-600" aria-hidden />}
            action={<Button size="sm" variant="ghost" onClick={() => setLastResult(null)}>Dismiss</Button>}
          />
          <CardBody className="space-y-3 text-sm">
            {lastResult.warnings.map((warning) => (
              <p key={warning} className="text-slate-700">{warning}</p>
            ))}
            {lastResult.unscheduled.length > 0 && (
              <ul className="divide-y divide-slate-100 rounded-lg border border-slate-100">
                {lastResult.unscheduled.map((item) => (
                  <li key={`${item.topicId}-${item.sessionType}`} className="flex flex-col gap-1 px-3 py-2 sm:flex-row sm:items-center sm:justify-between">
                    <span className="font-medium text-slate-800">
                      {item.topicName} <span className="font-normal text-slate-500">· {item.subjectName}</span>
                    </span>
                    <span className="text-xs text-slate-500">
                      {formatMinutes(item.remainingMinutes)} not scheduled - {item.reason}
                    </span>
                  </li>
                ))}
              </ul>
            )}
            {lastResult.warnings.some((w) => w.includes('availability')) && (
              <Link to="/settings" className="inline-block text-sm font-medium text-emerald-700 hover:text-emerald-800">
                Set your availability →
              </Link>
            )}
          </CardBody>
        </Card>
      )}

      {isLoading && <Card className="p-6"><LoadingBlock rows={5} /></Card>}
      {error && <Card><ErrorState error={error} onRetry={() => refetch()} /></Card>}

      {data && (
        <div className="grid gap-6 xl:grid-cols-3">
          <div className="space-y-6 xl:col-span-2">
            <Card>
              <CardHeader
                title={`Today · ${formatDate(data.date, { weekday: 'long' })}`}
                description={`${formatMinutes(data.completedMinutes)} of ${formatMinutes(data.plannedMinutes)} studied`}
                icon={<CalendarDays className="h-4 w-4" aria-hidden />}
                action={<span className="text-sm font-semibold text-slate-900">{Math.round(data.completionPercent)}%</span>}
              />
              <CardBody>
                <ProgressBar value={data.completionPercent} className="mb-2" label="Today's completion" />
                {data.sessions.length === 0 ? (
                  <EmptyState
                    icon={<CalendarPlus className="h-5 w-5" aria-hidden />}
                    title={plan ? 'No sessions today' : 'No study plan yet'}
                    description={
                      plan
                        ? 'Enjoy the break, or add a manual session.'
                        : 'Generate a plan and SmartStudy will fit your topics into your free time.'
                    }
                    action={!plan && <Button onClick={() => setGenerateOpen(true)} icon={<Sparkles className="h-4 w-4" aria-hidden />}>Generate plan</Button>}
                  />
                ) : (
                  <SessionList sessions={data.sessions} />
                )}
              </CardBody>
            </Card>

            <Card>
              <CardHeader
                title="Priority ranking"
                description="Every open topic scored with the weighted priority formula"
                icon={<Info className="h-4 w-4" aria-hidden />}
              />
              <CardBody>
                {priorities.data && (
                  <div className="mb-4 rounded-xl bg-navy-900 p-4 text-sm text-navy-100">
                    <p className="font-mono text-xs sm:text-sm">
                      Priority = {priorities.data.weights.weakness.toFixed(2)}×Weakness + {priorities.data.weights.examUrgency.toFixed(2)}×Exam urgency +{' '}
                      {priorities.data.weights.difficulty.toFixed(2)}×Difficulty + {priorities.data.weights.revisionOverdue.toFixed(2)}×Revision overdue +{' '}
                      {priorities.data.weights.subjectImportance.toFixed(2)}×Importance
                    </p>
                    <p className="mt-2 text-xs text-navy-300">
                      Each factor is normalised to 0-100. Topics without any assessment get a neutral weakness of 50 instead of being treated as 0% mastery.
                    </p>
                  </div>
                )}
                {priorities.isLoading && <LoadingBlock />}
                {priorities.error && <ErrorState error={priorities.error} onRetry={() => priorities.refetch()} />}
                {priorities.data && priorities.data.items.length === 0 && (
                  <EmptyState compact title="Nothing to prioritise" description="Add topics to your subjects to see their priorities." />
                )}
                {priorities.data && priorities.data.items.length > 0 && (
                  <>
                    <RecommendationList
                      items={showAllPriorities ? priorities.data.items : priorities.data.items.slice(0, 6)}
                      weights={priorities.data.weights}
                    />
                    {priorities.data.items.length > 6 && (
                      <Button variant="ghost" size="sm" className="mt-2" onClick={() => setShowAllPriorities((value) => !value)}>
                        {showAllPriorities ? 'Show fewer' : `Show all ${priorities.data.items.length}`}
                      </Button>
                    )}
                  </>
                )}
              </CardBody>
            </Card>
          </div>

          <div className="space-y-6">
            <Card>
              <CardHeader title="Active plan" icon={<Sparkles className="h-4 w-4" aria-hidden />} />
              <CardBody>
                {plan ? (
                  <dl className="space-y-3 text-sm">
                    <div className="flex justify-between gap-3">
                      <dt className="text-slate-500">Period</dt>
                      <dd className="font-medium text-slate-900">
                        {formatDate(plan.startDate)} - {formatDate(plan.endDate)}
                      </dd>
                    </div>
                    <div className="flex justify-between gap-3">
                      <dt className="text-slate-500">Sessions</dt>
                      <dd className="font-medium text-slate-900">{plan.sessionCount}</dd>
                    </div>
                    <div className="flex justify-between gap-3">
                      <dt className="text-slate-500">Planned time</dt>
                      <dd className="font-medium text-slate-900">{formatMinutes(plan.totalPlannedMinutes)}</dd>
                    </div>
                    {plan.strategySummary && <p className="rounded-lg bg-slate-50 p-3 text-xs text-slate-600">{plan.strategySummary}</p>}
                    <Link to="/planner/calendar" className="inline-block text-sm font-medium text-emerald-700 hover:text-emerald-800">
                      Open calendar →
                    </Link>
                  </dl>
                ) : (
                  <EmptyState compact title="No active plan" description="Generate one to start." />
                )}
              </CardBody>
            </Card>

            <Card>
              <CardHeader title="What to study next" description="Top topics right now, and why" />
              <CardBody>
                {data.recommendations.length === 0 ? (
                  <EmptyState compact title="All caught up" description="No open topics or due revisions." />
                ) : (
                  <RecommendationList items={data.recommendations} />
                )}
              </CardBody>
            </Card>
          </div>
        </div>
      )}

      <Modal
        open={generateOpen}
        title="Generate a study plan"
        description="Unlocked future sessions are replaced. Completed, in-progress and locked sessions are kept."
        onClose={() => setGenerateOpen(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setGenerateOpen(false)} disabled={working}>
              Cancel
            </Button>
            <Button onClick={generate} loading={working} icon={<Sparkles className="h-4 w-4" aria-hidden />}>
              Generate
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          <FormError message={formError} />
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField label="Start date" type="date" min={today} value={startDate} onChange={(event) => setStartDate(event.target.value)} />
            <TextField label="Number of days" type="number" min={1} max={28} value={days} onChange={(event) => setDays(event.target.value)} hint="1 to 28 days" />
          </div>
          <p className="text-xs text-slate-500">
            The planner uses your weekly availability, daily and weekly limits, exam dates, topic difficulty, mastery and due revisions.
          </p>
        </div>
      </Modal>

      <SessionFormModal
        open={addOpen}
        defaultDate={today}
        onClose={() => setAddOpen(false)}
        onSaved={async () => {
          setAddOpen(false);
          toast.success('Session added.');
          await refreshAll();
        }}
      />
    </>
  );
}
