import { Link } from 'react-router';
import {
  AlertTriangle,
  ArrowRight,
  BookOpen,
  CalendarClock,
  CheckCircle2,
  Clock,
  Flame,
  ListChecks,
  RotateCcw,
  Target,
  Timer,
} from 'lucide-react';
import { useOverview, useSubjects } from '../hooks/queries';
import { useUser } from '../context/AuthContext';
import { Card, CardBody, CardHeader, PageHeader, StatCard } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { ColorDot, EmptyState, ErrorState, LoadingBlock, ProgressBar } from '../components/ui/Feedback';
import { MasteryBadge, ReadinessBadge } from '../components/StatusBadges';
import { StudyTimeChart } from '../components/charts/StudyCharts';
import { ProgressRing, scoreColor } from '../components/ProgressRing';
import { daysLabel, formatDate, formatMinutes, timeAgo, toHours } from '../utils/format';

const ACTIVITY_ICONS: Record<string, typeof CheckCircle2> = {
  SESSION_COMPLETED: CheckCircle2,
  SESSION_INCOMPLETE: Clock,
  REVISION: RotateCcw,
  FOCUS: Timer,
  ASSESSMENT: ListChecks,
};

function GettingStarted() {
  const steps = [
    { to: '/subjects', title: 'Add your subjects and topics', text: 'Set importance and difficulty so the planner knows what matters.' },
    { to: '/exams', title: 'Enter your exam dates', text: 'Approaching exams raise the priority of their topics.' },
    { to: '/settings', title: 'Set your weekly availability', text: 'Tell SmartStudy when you can study and for how long.' },
    { to: '/planner', title: 'Generate your adaptive plan', text: 'A conflict-free timetable that re-plans itself when you miss work.' },
  ];
  return (
    <Card className="mb-6 border-emerald-200 bg-gradient-to-br from-emerald-50 to-white">
      <CardHeader title="Get started in four steps" description="Your dashboard fills up as soon as you add your first subject." />
      <CardBody>
        <ol className="grid gap-3 md:grid-cols-4">
          {steps.map((step, index) => (
            <li key={step.to}>
              <Link to={step.to} className="block h-full rounded-xl border border-slate-200 bg-white p-4 hover:border-emerald-300 hover:shadow-sm">
                <span className="text-xs font-semibold text-emerald-700">Step {index + 1}</span>
                <p className="mt-1 text-sm font-medium text-slate-900">{step.title}</p>
                <p className="mt-1 text-xs text-slate-500">{step.text}</p>
              </Link>
            </li>
          ))}
        </ol>
      </CardBody>
    </Card>
  );
}

export function DashboardPage() {
  const user = useUser();
  const { data, isLoading, error, refetch } = useOverview();
  const { data: subjects } = useSubjects();

  return (
    <>
      <PageHeader
        title="Dashboard"
        description={data ? `${formatDate(data.date, { weekday: 'long', year: 'numeric', month: 'long' })} · ${user.timeZone}` : undefined}
        actions={
          <Link to="/planner">
            <Button icon={<CalendarClock className="h-4 w-4" aria-hidden />}>Today's plan</Button>
          </Link>
        }
      />

      {subjects && subjects.length === 0 && <GettingStarted />}

      {isLoading && (
        <Card className="p-6">
          <LoadingBlock rows={6} />
        </Card>
      )}
      {error && (
        <Card>
          <ErrorState error={error} onRetry={() => refetch()} />
        </Card>
      )}

      {data && (
        <div className="space-y-6">
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <StatCard
              label="Planned today"
              value={`${toHours(data.today.plannedMinutes)} h`}
              hint={`${data.today.sessionsPlanned} session${data.today.sessionsPlanned === 1 ? '' : 's'}`}
              icon={<CalendarClock className="h-5 w-5" aria-hidden />}
              accent="blue"
            />
            <StatCard
              label="Studied today"
              value={`${toHours(data.today.completedMinutes)} h`}
              hint={`${data.today.sessionsCompleted} completed · ${data.today.sessionsMissed} missed`}
              icon={<CheckCircle2 className="h-5 w-5" aria-hidden />}
            />
            <Card className="p-5">
              <p className="text-xs font-medium uppercase tracking-wide text-slate-500">Today's completion</p>
              <p className="mt-2 text-2xl font-semibold text-slate-900">{Math.round(data.today.completionPercent)}%</p>
              <ProgressBar value={data.today.completionPercent} className="mt-3" label="Today's completion" />
            </Card>
            <StatCard
              label="Study streak"
              value={`${data.streakDays} day${data.streakDays === 1 ? '' : 's'}`}
              hint={`${Math.round(data.consistencyPercent)}% of the last 14 days`}
              icon={<Flame className="h-5 w-5" aria-hidden />}
              accent="amber"
            />
          </div>

          <div className="grid gap-6 xl:grid-cols-3">
            <div className="xl:col-span-2">
              <StudyTimeChart
                data={data.weeklyProductivity}
                title="Weekly productivity"
                description={`Last 7 days · this week ${formatMinutes(data.week.actualMinutes)} studied of ${formatMinutes(data.week.plannedMinutes)} planned`}
              />
            </div>
            <Card>
              <CardHeader
                title="Upcoming exams"
                icon={<Target className="h-4 w-4" aria-hidden />}
                action={<Link to="/exams" className="text-xs font-medium text-emerald-700 hover:text-emerald-800">All exams</Link>}
              />
              <CardBody>
                {data.upcomingExams.length === 0 ? (
                  <EmptyState compact title="No upcoming exams" action={<Link to="/exams"><Button size="sm" variant="secondary">Add an exam</Button></Link>} />
                ) : (
                  <ul className="space-y-4">
                    {data.upcomingExams.map((exam) => (
                      <li key={exam.examId}>
                        <Link to={`/exams/readiness?examId=${exam.examId}`} className="flex items-center gap-3 rounded-lg p-1 hover:bg-slate-50">
                          <ProgressRing value={exam.readinessScore} size={56} stroke={6} color={scoreColor(exam.readinessScore)} muted={exam.dataSufficiency === 'INSUFFICIENT'} />
                          <div className="min-w-0 flex-1">
                            <p className="truncate text-sm font-medium text-slate-900">{exam.title}</p>
                            <p className="flex items-center gap-1.5 truncate text-xs text-slate-500">
                              <ColorDot color={exam.subjectColor} />
                              {exam.subjectName}
                            </p>
                            <div className="mt-1 flex flex-wrap items-center gap-2">
                              <span className="text-xs font-medium text-slate-700">{daysLabel(exam.daysRemaining)}</span>
                              <ReadinessBadge level={exam.readinessLevel} />
                            </div>
                          </div>
                        </Link>
                      </li>
                    ))}
                  </ul>
                )}
              </CardBody>
            </Card>
          </div>

          <div className="grid gap-6 lg:grid-cols-3">
            <Card>
              <CardHeader
                title="Weak topics"
                icon={<AlertTriangle className="h-4 w-4" aria-hidden />}
                action={<Link to="/weak-topics" className="text-xs font-medium text-emerald-700 hover:text-emerald-800">Analyse</Link>}
              />
              <CardBody>
                {data.weakTopics.length === 0 ? (
                  <EmptyState compact icon={<CheckCircle2 className="h-5 w-5" aria-hidden />} title="No weak topics detected" description="Record quiz and test marks to measure topic mastery." />
                ) : (
                  <ul className="space-y-3">
                    {data.weakTopics.map((topic) => (
                      <li key={topic.topicId} className="flex items-center justify-between gap-3">
                        <div className="min-w-0">
                          <p className="truncate text-sm font-medium text-slate-900">{topic.topicName}</p>
                          <p className="flex items-center gap-1.5 truncate text-xs text-slate-500">
                            <ColorDot color={topic.subjectColor} />
                            {topic.subjectName}
                          </p>
                        </div>
                        <MasteryBadge level={topic.masteryLevel} score={topic.masteryScore} />
                      </li>
                    ))}
                  </ul>
                )}
              </CardBody>
            </Card>

            <Card>
              <CardHeader
                title="Revision reminders"
                description={`${data.revisions.dueCount} due · ${data.revisions.overdueCount} overdue`}
                icon={<RotateCcw className="h-4 w-4" aria-hidden />}
                action={<Link to="/revision" className="text-xs font-medium text-emerald-700 hover:text-emerald-800">Revise</Link>}
              />
              <CardBody>
                {data.revisions.items.length === 0 ? (
                  <EmptyState compact icon={<CheckCircle2 className="h-5 w-5" aria-hidden />} title="Nothing to revise today" />
                ) : (
                  <ul className="space-y-3">
                    {data.revisions.items.map((item) => (
                      <li key={item.topicId} className="flex items-center justify-between gap-3">
                        <div className="min-w-0">
                          <p className="truncate text-sm font-medium text-slate-900">{item.topicName}</p>
                          <p className="truncate text-xs text-slate-500">{item.subjectName}</p>
                        </div>
                        <span className={`text-xs font-medium ${item.daysOverdue > 0 ? 'text-red-700' : 'text-violet-700'}`}>
                          {item.daysOverdue > 0 ? `${item.daysOverdue} d overdue` : 'Due today'}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </CardBody>
            </Card>

            <Card>
              <CardHeader title="Recent activity" icon={<BookOpen className="h-4 w-4" aria-hidden />} />
              <CardBody>
                {data.recentActivity.length === 0 ? (
                  <EmptyState compact title="No activity yet" description="Completed sessions, revisions and focus blocks appear here." />
                ) : (
                  <ul className="space-y-3">
                    {data.recentActivity.map((activity, index) => {
                      const Icon = ACTIVITY_ICONS[activity.type] ?? CheckCircle2;
                      return (
                        <li key={`${activity.type}-${index}`} className="flex gap-3">
                          <div className="mt-0.5 rounded-lg bg-slate-100 p-1.5 text-slate-600">
                            <Icon className="h-3.5 w-3.5" aria-hidden />
                          </div>
                          <div className="min-w-0 flex-1">
                            <p className="truncate text-sm text-slate-800">{activity.title}</p>
                            <p className="truncate text-xs text-slate-500">{activity.detail}</p>
                          </div>
                          <span className="shrink-0 text-[11px] text-slate-400">{timeAgo(activity.occurredAt)}</span>
                        </li>
                      );
                    })}
                  </ul>
                )}
              </CardBody>
            </Card>
          </div>

          <div className="flex justify-end">
            <Link to="/analytics" className="inline-flex items-center gap-1 text-sm font-medium text-emerald-700 hover:text-emerald-800">
              Detailed analytics <ArrowRight className="h-4 w-4" aria-hidden />
            </Link>
          </div>
        </div>
      )}
    </>
  );
}
