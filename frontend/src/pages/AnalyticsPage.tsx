import { useMemo, useState } from 'react';
import { Link } from 'react-router';
import { CalendarCheck, ChevronLeft, ChevronRight, Flame, RotateCcw, Target, Timer } from 'lucide-react';
import { useAnalyticsSubjects, useAnalyticsWeekly, useProductivity } from '../hooks/queries';
import { useUser } from '../context/AuthContext';
import { Card, CardBody, CardHeader, PageHeader, StatCard } from '../components/ui/Card';
import { IconButton } from '../components/ui/Button';
import { ColorDot, EmptyState, ErrorState, LoadingBlock, ProgressBar, Tabs } from '../components/ui/Feedback';
import {
  DailyTrendChart,
  MonthlyChart,
  ReadinessTrendChart,
  SessionOutcomeBars,
  StudyTimeChart,
} from '../components/charts/StudyCharts';
import { addDays, formatDate, formatMinutes, formatPercent, todayIn, weekStart } from '../utils/format';

type Range = '7' | '30' | '90';

const RANGES: { id: Range; label: string }[] = [
  { id: '7', label: '7 days' },
  { id: '30', label: '30 days' },
  { id: '90', label: '90 days' },
];

function WeeklySection({ timeZone }: { timeZone: string }) {
  const thisWeek = weekStart(todayIn(timeZone));
  const [anchor, setAnchor] = useState(thisWeek);
  const weekly = useAnalyticsWeekly(anchor);

  return (
    <Card>
      <CardHeader
        title="Weekly report"
        description={weekly.data ? `${formatDate(weekly.data.weekStart)} – ${formatDate(weekly.data.weekEnd)}` : 'Loading week'}
        action={
          <div className="flex items-center gap-1">
            <IconButton label="Previous week" onClick={() => setAnchor(addDays(anchor, -7))}>
              <ChevronLeft className="h-4 w-4" aria-hidden />
            </IconButton>
            <IconButton label="Next week" disabled={anchor >= thisWeek} onClick={() => setAnchor(addDays(anchor, 7))}>
              <ChevronRight className="h-4 w-4" aria-hidden />
            </IconButton>
          </div>
        }
      />
      <CardBody>
        {weekly.isLoading && <LoadingBlock rows={5} />}
        {weekly.error && <ErrorState error={weekly.error} onRetry={() => weekly.refetch()} />}
        {weekly.data && (
          <div className="space-y-5">
            <dl className="grid grid-cols-2 gap-4 sm:grid-cols-4">
              <div>
                <dt className="text-xs text-slate-500">Planned</dt>
                <dd className="text-lg font-semibold text-slate-900">{formatMinutes(weekly.data.plannedMinutes)}</dd>
              </div>
              <div>
                <dt className="text-xs text-slate-500">Studied</dt>
                <dd className="text-lg font-semibold text-slate-900">{formatMinutes(weekly.data.actualMinutes)}</dd>
              </div>
              <div>
                <dt className="text-xs text-slate-500">Focus time</dt>
                <dd className="text-lg font-semibold text-slate-900">{formatMinutes(weekly.data.focusMinutes)}</dd>
              </div>
              <div>
                <dt className="text-xs text-slate-500">Completion rate</dt>
                <dd className="text-lg font-semibold text-slate-900">{formatPercent(weekly.data.completionRate)}</dd>
              </div>
            </dl>
            <StudyTimeChart data={weekly.data.days} title="Planned vs actual by day" height={220} />
            {weekly.data.bySubject.length > 0 && (
              <div>
                <h3 className="mb-2 text-sm font-semibold text-slate-800">By subject</h3>
                <ul className="space-y-2.5">
                  {weekly.data.bySubject.map((row) => {
                    const percent = row.plannedMinutes > 0 ? (row.actualMinutes / row.plannedMinutes) * 100 : row.actualMinutes > 0 ? 100 : 0;
                    return (
                      <li key={row.subjectId}>
                        <div className="mb-1 flex items-center justify-between gap-2 text-sm">
                          <span className="flex min-w-0 items-center gap-1.5 truncate text-slate-700">
                            <ColorDot color={row.subjectColor} />
                            {row.subjectName}
                          </span>
                          <span className="shrink-0 text-xs tabular-nums text-slate-500">
                            {formatMinutes(row.actualMinutes)} of {formatMinutes(row.plannedMinutes)}
                          </span>
                        </div>
                        <ProgressBar value={percent} tone={percent >= 80 ? 'emerald' : percent >= 50 ? 'amber' : 'red'} label={`${row.subjectName} completion`} />
                      </li>
                    );
                  })}
                </ul>
              </div>
            )}
          </div>
        )}
      </CardBody>
    </Card>
  );
}

export function AnalyticsPage() {
  const user = useUser();
  const [range, setRange] = useState<Range>('30');
  const productivity = useProductivity(Number(range));
  const subjects = useAnalyticsSubjects();

  const trendSeries = useMemo(
    () =>
      (productivity.data?.readinessTrends ?? []).map((trend) => ({
        id: trend.examId,
        name: `${trend.examTitle} (${trend.subjectName})`,
        points: trend.points,
      })),
    [productivity.data],
  );

  const data = productivity.data;

  return (
    <>
      <PageHeader
        title="Analytics"
        description="How your planned study compares with what you actually did, and where your preparation stands."
        actions={<Tabs tabs={RANGES} value={range} onChange={setRange} label="Time range" />}
      />

      {productivity.isLoading && (
        <Card>
          <CardBody>
            <LoadingBlock rows={6} />
          </CardBody>
        </Card>
      )}
      {productivity.error && (
        <Card>
          <CardBody>
            <ErrorState error={productivity.error} onRetry={() => productivity.refetch()} />
          </CardBody>
        </Card>
      )}

      {data && (
        <>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <StatCard
              label="Planned vs actual"
              value={formatPercent(data.plannedVsActualPercent)}
              hint={`${formatMinutes(data.totalActualMinutes)} studied of ${formatMinutes(data.totalPlannedMinutes)} planned`}
              icon={<Target className="h-5 w-5" aria-hidden />}
            />
            <StatCard
              label="Consistency"
              value={formatPercent(data.consistencyPercent)}
              hint={`Studied on ${data.activeDays} of ${data.days} days`}
              icon={<CalendarCheck className="h-5 w-5" aria-hidden />}
              accent="blue"
            />
            <StatCard
              label="Study streak"
              value={`${data.currentStreak} day${data.currentStreak === 1 ? '' : 's'}`}
              hint={`Longest: ${data.longestStreak} day${data.longestStreak === 1 ? '' : 's'}`}
              icon={<Flame className="h-5 w-5" aria-hidden />}
              accent="amber"
            />
            <StatCard
              label="Focus sessions"
              value={String(data.focus.sessions)}
              hint={`${formatMinutes(data.focus.totalMinutes)} total · avg ${Math.round(data.focus.averageMinutes)} min`}
              icon={<Timer className="h-5 w-5" aria-hidden />}
              accent="slate"
            />
          </div>

          <div className="mt-6 grid gap-6 xl:grid-cols-3">
            <div className="xl:col-span-2">
              <DailyTrendChart data={data.daily} title="Daily study trend" description={`Last ${data.days} days, in hours`} />
            </div>
            <div className="space-y-6">
              <Card>
                <CardHeader title="Session outcomes" description={`Completion rate ${formatPercent(data.sessions.completionRate)}`} />
                <CardBody>
                  <SessionOutcomeBars completed={data.sessions.completed} incomplete={data.sessions.incomplete} missed={data.sessions.missed} />
                </CardBody>
              </Card>
              <Card>
                <CardHeader title="Revision discipline" />
                <CardBody className="space-y-3 text-sm">
                  <div className="flex items-center justify-between">
                    <span className="flex items-center gap-1.5 text-slate-600">
                      <RotateCcw className="h-4 w-4 text-slate-400" aria-hidden /> Revisions completed
                    </span>
                    <span className="font-semibold tabular-nums text-slate-900">{data.revisions.completed}</span>
                  </div>
                  <div>
                    <div className="mb-1 flex items-center justify-between text-slate-600">
                      <span>Done on time</span>
                      <span className="font-semibold tabular-nums text-slate-900">{formatPercent(data.revisions.onTimeRate)}</span>
                    </div>
                    <ProgressBar value={data.revisions.onTimeRate} tone="blue" label="Revisions done on time" />
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-slate-600">Overdue right now</span>
                    {data.revisions.overdueNow > 0 ? (
                      <Link to="/revision" className="font-semibold text-red-700 hover:underline">
                        {data.revisions.overdueNow} topic{data.revisions.overdueNow === 1 ? '' : 's'}
                      </Link>
                    ) : (
                      <span className="font-semibold text-emerald-700">None</span>
                    )}
                  </div>
                </CardBody>
              </Card>
            </div>
          </div>

          <div className="mt-6 grid gap-6 xl:grid-cols-2">
            <WeeklySection timeZone={user.timeZone} />
            <div className="space-y-6">
              <ReadinessTrendChart series={trendSeries} description="Snapshot taken whenever readiness is recalculated" />
              <MonthlyChart data={data.monthly} />
            </div>
          </div>

          <div className="mt-6 grid gap-6 xl:grid-cols-3">
            <Card className="xl:col-span-2">
              <CardHeader title="Subject progress" description="Syllabus completion, mastery and study time per subject" />
              <CardBody className="overflow-x-auto">
                {subjects.isLoading && <LoadingBlock rows={4} />}
                {subjects.error && <ErrorState error={subjects.error} onRetry={() => subjects.refetch()} />}
                {subjects.data && subjects.data.length === 0 && (
                  <EmptyState compact title="No subjects yet" description="Add subjects and topics to see progress here." />
                )}
                {subjects.data && subjects.data.length > 0 && (
                  <table className="w-full min-w-[640px] text-sm">
                    <thead>
                      <tr className="border-b border-slate-200 text-left text-xs uppercase tracking-wide text-slate-500">
                        <th className="py-2 pr-3 font-medium">Subject</th>
                        <th className="py-2 pr-3 font-medium">Syllabus</th>
                        <th className="py-2 pr-3 text-right font-medium">Mastery</th>
                        <th className="py-2 pr-3 text-right font-medium">Weak</th>
                        <th className="py-2 pr-3 text-right font-medium">Studied (30d)</th>
                        <th className="py-2 text-right font-medium">Next 7d</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {subjects.data.map((row) => (
                        <tr key={row.subjectId}>
                          <td className="py-2.5 pr-3">
                            <Link to={`/subjects/${row.subjectId}`} className="flex items-center gap-1.5 font-medium text-slate-800 hover:text-emerald-700">
                              <ColorDot color={row.subjectColor} />
                              {row.subjectName}
                            </Link>
                            <span className="text-xs text-slate-500">
                              Last studied {row.lastStudiedOn ? formatDate(row.lastStudiedOn) : 'never'}
                            </span>
                          </td>
                          <td className="w-40 py-2.5 pr-3">
                            <ProgressBar value={row.syllabusCompletion} label={`${row.subjectName} syllabus completion`} />
                            <span className="text-xs text-slate-500">
                              {row.completedTopics}/{row.totalTopics} topics · {formatPercent(row.syllabusCompletion)}
                            </span>
                          </td>
                          <td className="py-2.5 pr-3 text-right tabular-nums">{row.averageMastery === null ? <span className="text-slate-400">Not assessed</span> : formatPercent(row.averageMastery)}</td>
                          <td className="py-2.5 pr-3 text-right tabular-nums">
                            {row.weakTopics > 0 ? <span className="font-medium text-red-700">{row.weakTopics}</span> : <span className="text-slate-400">0</span>}
                          </td>
                          <td className="py-2.5 pr-3 text-right tabular-nums">{formatMinutes(row.studiedMinutesLast30Days)}</td>
                          <td className="py-2.5 text-right tabular-nums">{formatMinutes(row.plannedMinutesNext7Days)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </CardBody>
            </Card>

            <Card>
              <CardHeader title="Subjects needing attention" description="Lowest average mastery first" />
              <CardBody>
                {data.weakSubjects.length === 0 ? (
                  <EmptyState compact title="Nothing flagged" description="Record assessment scores to find weak subjects." />
                ) : (
                  <ul className="space-y-3">
                    {data.weakSubjects.map((row) => (
                      <li key={row.subjectId} className="rounded-lg border border-slate-200 p-3">
                        <div className="flex items-center justify-between gap-2">
                          <span className="flex min-w-0 items-center gap-1.5 truncate text-sm font-medium text-slate-800">
                            <ColorDot color={row.subjectColor} />
                            {row.subjectName}
                          </span>
                          <span className="text-sm font-semibold tabular-nums text-slate-900">
                            {row.averageMastery === null ? '—' : formatPercent(row.averageMastery)}
                          </span>
                        </div>
                        <p className="mt-1 text-xs text-slate-500">
                          {row.weakTopics} weak topic{row.weakTopics === 1 ? '' : 's'} · {row.assessedTopics} of {row.totalTopics} assessed
                        </p>
                      </li>
                    ))}
                  </ul>
                )}
                <Link to="/weak-topics" className="mt-4 inline-block text-sm font-medium text-emerald-700 hover:underline">
                  Open weak topic analysis →
                </Link>
              </CardBody>
            </Card>
          </div>
        </>
      )}
    </>
  );
}
