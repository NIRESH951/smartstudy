import { useState } from 'react';
import { AlertTriangle, ClipboardPlus, History, Trash2 } from 'lucide-react';
import { useAssessments, usePerformanceSummary, useRefreshAll, useTopicHistory, useWeakTopics } from '../hooks/queries';
import { useUser } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { assessmentApi } from '../services/api';
import { errorMessage } from '../services/http';
import { Card, CardBody, CardHeader, PageHeader, StatCard } from '../components/ui/Card';
import { Button, IconButton } from '../components/ui/Button';
import { Badge, ColorDot, EmptyState, ErrorState, LoadingBlock, ProgressBar } from '../components/ui/Feedback';
import { ConfirmDialog } from '../components/ui/Modal';
import { MasteryBadge, TrendIndicator } from '../components/StatusBadges';
import { MasteryDistribution, TopicHistoryChart } from '../components/charts/StudyCharts';
import { AssessmentFormModal } from '../components/forms/AssessmentFormModal';
import { formatDate, formatPercent, todayIn } from '../utils/format';
import type { Assessment } from '../types/api';

const TYPE_LABELS: Record<Assessment['assessmentType'], string> = {
  EXAM: 'Exam',
  QUIZ: 'Quiz',
  ASSIGNMENT: 'Assignment',
  MOCK_TEST: 'Mock test',
};

export function WeakTopicsPage() {
  const user = useUser();
  const toast = useToast();
  const refreshAll = useRefreshAll();
  const weak = useWeakTopics();
  const summary = usePerformanceSummary();
  const assessments = useAssessments();
  const [historyTopicId, setHistoryTopicId] = useState<number | null>(null);
  const history = useTopicHistory(historyTopicId);
  const [formOpen, setFormOpen] = useState(false);
  const [deleting, setDeleting] = useState<Assessment | null>(null);
  const [busy, setBusy] = useState(false);

  return (
    <>
      <PageHeader
        title="Weak Topics"
        description="Mastery = 0.60 × latest score + 0.40 × average of all scores. Topics without marks are 'not assessed', never 0%."
        actions={<Button icon={<ClipboardPlus className="h-4 w-4" aria-hidden />} onClick={() => setFormOpen(true)}>Record marks</Button>}
      />

      <div className="grid gap-4 sm:grid-cols-3">
        <StatCard
          label="Overall mastery"
          value={summary.data?.overallMastery === null || summary.data?.overallMastery === undefined ? '-' : formatPercent(summary.data.overallMastery)}
          hint="Average of assessed topics"
          accent="blue"
        />
        <StatCard label="Weak / needs improvement" value={weak.data?.items.length ?? '-'} hint="Mastery below 65%" accent="red" icon={<AlertTriangle className="h-5 w-5" aria-hidden />} />
        <StatCard label="Not assessed" value={weak.data?.notAssessedTopics ?? '-'} hint={`of ${weak.data?.totalTopics ?? 0} topics`} accent="slate" />
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-3">
        <Card className="xl:col-span-2">
          <CardHeader title="Topics that need attention" description="Sorted from weakest. The planner already gives these extra priority." />
          <CardBody>
            {weak.isLoading && <LoadingBlock rows={4} />}
            {weak.error && <ErrorState error={weak.error} onRetry={() => weak.refetch()} />}
            {weak.data && weak.data.items.length === 0 && (
              <EmptyState
                title={weak.data.assessedTopics === 0 ? 'No topic has been assessed yet' : 'No weak topics - great job!'}
                description={weak.data.assessedTopics === 0 ? 'Record quiz or test marks split by topic to detect weak areas.' : 'All assessed topics have at least 65% mastery.'}
              />
            )}
            {weak.data && weak.data.items.length > 0 && (
              <ul className="space-y-3">
                {weak.data.items.map((item) => (
                  <li key={item.topicId} className="rounded-xl border border-slate-200 p-4">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div className="min-w-0">
                        <p className="text-sm font-semibold text-slate-900">{item.topicName}</p>
                        <p className="flex items-center gap-1.5 text-xs text-slate-500">
                          <ColorDot color={item.subjectColor} />
                          {item.subjectName} · {item.assessmentCount} assessment{item.assessmentCount === 1 ? '' : 's'} · last on {formatDate(item.lastAssessedOn)}
                        </p>
                      </div>
                      <div className="flex items-center gap-2">
                        <TrendIndicator trend={item.trend} />
                        <MasteryBadge level={item.masteryLevel} score={item.masteryScore} />
                      </div>
                    </div>
                    <div className="mt-3 grid gap-3 text-xs sm:grid-cols-3">
                      <div>
                        <p className="text-slate-500">Mastery</p>
                        <ProgressBar value={item.masteryScore} tone={item.masteryLevel === 'WEAK' ? 'red' : 'amber'} className="mt-1" label={`${item.topicName} mastery`} />
                      </div>
                      <div>
                        <p className="text-slate-500">Latest score</p>
                        <p className="mt-0.5 font-semibold text-slate-800">{formatPercent(item.latestScore)}</p>
                      </div>
                      <div>
                        <p className="text-slate-500">Historical average</p>
                        <p className="mt-0.5 font-semibold text-slate-800">{formatPercent(item.historicalAverage)}</p>
                      </div>
                    </div>
                    <p className="mt-3 rounded-lg bg-slate-50 p-2.5 text-xs text-slate-700">{item.recommendation}</p>
                    <Button size="sm" variant="ghost" className="mt-2" icon={<History className="h-3.5 w-3.5" aria-hidden />} onClick={() => setHistoryTopicId(item.topicId)}>
                      Performance history
                    </Button>
                  </li>
                ))}
              </ul>
            )}
          </CardBody>
        </Card>

        <div className="space-y-6">
          <Card>
            <CardHeader title="Mastery distribution" />
            <CardBody>
              {summary.data ? <MasteryDistribution counts={summary.data.levelCounts} /> : <LoadingBlock rows={2} />}
            </CardBody>
          </Card>
          <Card>
            <CardHeader title="Weak subject analysis" description="Average mastery by subject" />
            <CardBody>
              {summary.data && summary.data.subjects.length === 0 && <EmptyState compact title="No subjects yet" />}
              <ul className="space-y-3">
                {[...(summary.data?.subjects ?? [])]
                  .sort((a, b) => (a.averageMastery ?? 101) - (b.averageMastery ?? 101))
                  .map((subject) => (
                    <li key={subject.subjectId}>
                      <div className="mb-1 flex items-center justify-between gap-2 text-sm">
                        <span className="flex items-center gap-1.5 truncate font-medium text-slate-800">
                          <ColorDot color={subject.subjectColor} />
                          {subject.subjectName}
                        </span>
                        <span className="text-xs text-slate-600">
                          {subject.averageMastery === null ? 'Not assessed' : formatPercent(subject.averageMastery)}
                          {subject.weakTopics > 0 && <span className="ml-1 text-red-700">· {subject.weakTopics} weak</span>}
                        </span>
                      </div>
                      <ProgressBar
                        value={subject.averageMastery ?? 0}
                        tone={subject.averageMastery === null ? 'blue' : subject.averageMastery < 40 ? 'red' : subject.averageMastery < 65 ? 'amber' : 'emerald'}
                        label={`${subject.subjectName} average mastery`}
                      />
                      <p className="mt-0.5 text-[11px] text-slate-500">
                        {subject.assessedTopics}/{subject.totalTopics} topics assessed
                        {subject.averageAssessmentScore !== null && ` · test average ${formatPercent(subject.averageAssessmentScore)}`}
                      </p>
                    </li>
                  ))}
              </ul>
            </CardBody>
          </Card>
        </div>
      </div>

      {historyTopicId !== null && (
        <div className="mt-6">
          {history.isLoading && <Card className="p-6"><LoadingBlock /></Card>}
          {history.error && <Card><ErrorState error={history.error} /></Card>}
          {history.data && <TopicHistoryChart history={history.data} />}
        </div>
      )}

      <Card className="mt-6">
        <CardHeader title="Recorded assessments" description="Exams, quizzes, assignments and mock tests" />
        <CardBody className="p-0">
          {assessments.isLoading && <div className="p-5"><LoadingBlock /></div>}
          {assessments.error && <ErrorState error={assessments.error} onRetry={() => assessments.refetch()} />}
          {assessments.data && assessments.data.length === 0 && (
            <EmptyState title="No marks recorded yet" action={<Button onClick={() => setFormOpen(true)}>Record marks</Button>} />
          )}
          {assessments.data && assessments.data.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[720px] text-left text-sm">
                <thead className="border-b border-slate-100 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="px-5 py-3 font-medium">Assessment</th>
                    <th className="px-3 py-3 font-medium">Date</th>
                    <th className="px-3 py-3 text-right font-medium">Marks</th>
                    <th className="px-3 py-3 font-medium">Topic scores</th>
                    <th className="px-5 py-3 text-right font-medium"><span className="sr-only">Actions</span></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {assessments.data.map((assessment) => (
                    <tr key={assessment.id}>
                      <td className="px-5 py-3">
                        <p className="font-medium text-slate-900">{assessment.title}</p>
                        <p className="flex items-center gap-1.5 text-xs text-slate-500">
                          <ColorDot color={assessment.subjectColor} />
                          {assessment.subjectName} · <Badge>{TYPE_LABELS[assessment.assessmentType]}</Badge>
                          {assessment.examTitle && ` · ${assessment.examTitle}`}
                        </p>
                      </td>
                      <td className="px-3 py-3 text-xs text-slate-600">{formatDate(assessment.assessedOn)}</td>
                      <td className="px-3 py-3 text-right tabular-nums">
                        <span className="font-semibold text-slate-900">{formatPercent(assessment.scorePercent, 1)}</span>
                        <span className="block text-xs text-slate-500">{assessment.obtainedMarks}/{assessment.maxMarks}</span>
                      </td>
                      <td className="px-3 py-3">
                        <div className="flex flex-wrap gap-1">
                          {assessment.topicScores.length === 0 && <span className="text-xs text-slate-400">Subject total only</span>}
                          {assessment.topicScores.map((score) => (
                            <button
                              key={score.topicId}
                              type="button"
                              onClick={() => setHistoryTopicId(score.topicId)}
                              className="rounded-md bg-slate-100 px-2 py-0.5 text-xs text-slate-700 hover:bg-slate-200"
                              title="Show performance history"
                            >
                              {score.topicName}: {Math.round(score.scorePercent)}%
                            </button>
                          ))}
                        </div>
                      </td>
                      <td className="px-5 py-3 text-right">
                        <IconButton label={`Delete ${assessment.title}`} tone="danger" onClick={() => setDeleting(assessment)}>
                          <Trash2 className="h-4 w-4" aria-hidden />
                        </IconButton>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardBody>
      </Card>

      <AssessmentFormModal
        open={formOpen}
        today={todayIn(user.timeZone)}
        onClose={() => setFormOpen(false)}
        onSaved={async (saved) => {
          setFormOpen(false);
          toast.success(`${saved.title} saved (${Math.round(saved.scorePercent)}%). Mastery updated.`);
          await refreshAll();
        }}
      />
      <ConfirmDialog
        open={deleting !== null}
        title={`Delete ${deleting?.title ?? 'assessment'}?`}
        message="Topic mastery is recalculated without these marks."
        confirmLabel="Delete"
        loading={busy}
        onCancel={() => setDeleting(null)}
        onConfirm={async () => {
          if (!deleting) return;
          setBusy(true);
          try {
            await assessmentApi.remove(deleting.id);
            toast.success('Assessment deleted and mastery recalculated.');
            setDeleting(null);
            await refreshAll();
          } catch (caught) {
            toast.error(errorMessage(caught));
          } finally {
            setBusy(false);
          }
        }}
      />
    </>
  );
}
