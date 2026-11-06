import { useEffect } from 'react';
import { Link, useSearchParams } from 'react-router';
import { AlertTriangle, ArrowLeft, Info, ListTodo, Target } from 'lucide-react';
import { useReadiness, useReadinessOverview } from '../hooks/queries';
import { Card, CardBody, CardHeader, PageHeader } from '../components/ui/Card';
import { ColorDot, EmptyState, ErrorState, LoadingBlock, ProgressBar } from '../components/ui/Feedback';
import { MasteryBadge, ReadinessBadge, SufficiencyBadge, TopicStatusBadge } from '../components/StatusBadges';
import { ProgressRing, scoreColor } from '../components/ProgressRing';
import { ReadinessTrendChart } from '../components/charts/StudyCharts';
import { Button } from '../components/ui/Button';
import { daysLabel, formatDate } from '../utils/format';
import type { MasteryLevel } from '../types/api';

function masteryLevelOf(score: number | null): MasteryLevel {
  if (score === null) return 'NOT_ASSESSED';
  if (score < 40) return 'WEAK';
  if (score < 65) return 'NEEDS_IMPROVEMENT';
  if (score < 80) return 'GOOD';
  return 'STRONG';
}

export function ReadinessPage() {
  const [params, setParams] = useSearchParams();
  const overview = useReadinessOverview();
  const examIdParam = params.get('examId');
  const examId = examIdParam ? Number(examIdParam) : null;
  const detail = useReadiness(examId);

  // Select the nearest upcoming exam when none is chosen.
  useEffect(() => {
    if (examId === null && overview.data && overview.data.length > 0) {
      setParams({ examId: String(overview.data[0].examId) }, { replace: true });
    }
  }, [examId, overview.data, setParams]);

  return (
    <>
      <Link to="/exams" className="mb-3 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800">
        <ArrowLeft className="h-4 w-4" aria-hidden /> Examinations
      </Link>
      <PageHeader title="Exam readiness" description="An estimate of how prepared you are, built from your real progress." />

      {overview.isLoading && <Card className="p-6"><LoadingBlock /></Card>}
      {overview.error && <Card><ErrorState error={overview.error} onRetry={() => overview.refetch()} /></Card>}
      {overview.data && overview.data.length === 0 && examId === null && (
        <Card>
          <EmptyState icon={<Target className="h-5 w-5" aria-hidden />} title="No upcoming exams" description="Add an exam to see your readiness." action={<Link to="/exams"><Button>Add an exam</Button></Link>} />
        </Card>
      )}

      {overview.data && overview.data.length > 0 && (
        <div className="mb-6 flex gap-3 overflow-x-auto pb-1 scrollbar-thin" role="tablist" aria-label="Upcoming exams">
          {overview.data.map((item) => (
            <button
              key={item.examId}
              type="button"
              role="tab"
              aria-selected={item.examId === examId}
              onClick={() => setParams({ examId: String(item.examId) })}
              className={`flex min-w-56 items-center gap-3 rounded-xl border bg-white p-3 text-left shadow-sm transition-colors ${
                item.examId === examId ? 'border-emerald-400 ring-2 ring-emerald-100' : 'border-slate-200 hover:border-slate-300'
              }`}
            >
              <ProgressRing value={item.readinessScore} size={52} stroke={6} color={scoreColor(item.readinessScore)} muted={item.dataSufficiency === 'INSUFFICIENT'} />
              <div className="min-w-0">
                <p className="truncate text-sm font-medium text-slate-900">{item.examTitle}</p>
                <p className="flex items-center gap-1.5 truncate text-xs text-slate-500"><ColorDot color={item.subjectColor} />{item.subjectName}</p>
                <p className="text-xs font-medium text-amber-700">{daysLabel(item.daysRemaining)}</p>
              </div>
            </button>
          ))}
        </div>
      )}

      {detail.isLoading && examId !== null && <Card className="p-6"><LoadingBlock rows={6} /></Card>}
      {detail.error && <Card><ErrorState error={detail.error} onRetry={() => detail.refetch()} /></Card>}

      {detail.data && (
        <div className="space-y-6">
          <div className="grid gap-6 lg:grid-cols-3">
            <Card>
              <CardBody className="flex flex-col items-center py-6 text-center">
                <ProgressRing
                  value={detail.data.readinessScore}
                  size={160}
                  stroke={14}
                  color={scoreColor(detail.data.readinessScore)}
                  muted={detail.data.dataSufficiency === 'INSUFFICIENT'}
                  sublabel="readiness"
                  label={`${detail.data.examTitle} readiness`}
                />
                <p className="mt-4 text-base font-semibold text-slate-900">{detail.data.examTitle}</p>
                <p className="text-xs text-slate-500">
                  {detail.data.subjectName} · {formatDate(detail.data.examDate)} · {daysLabel(detail.data.daysRemaining)}
                </p>
                <div className="mt-3 flex flex-wrap justify-center gap-2">
                  <ReadinessBadge level={detail.data.level} />
                  <SufficiencyBadge value={detail.data.dataSufficiency} />
                </div>
                <p className="mt-3 text-xs text-slate-500">Data confidence: {Math.round(detail.data.dataConfidence)}% of the formula has real data</p>
              </CardBody>
            </Card>

            <Card className="lg:col-span-2">
              <CardHeader
                title="Readiness components"
                description="Readiness = 0.30 Syllabus + 0.30 Mastery + 0.25 Mock tests + 0.15 Revision"
                icon={<Info className="h-4 w-4" aria-hidden />}
              />
              <CardBody className="space-y-4">
                {detail.data.dataSufficiency !== 'SUFFICIENT' && (
                  <div className="flex gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs text-amber-900" role="note">
                    <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
                    <p>
                      Some components have no data yet. They are left out (not counted as zero) and the remaining weights are re-balanced,
                      so treat this score as a rough estimate until you record more assessments and revisions.
                    </p>
                  </div>
                )}
                {detail.data.components.map((component) => (
                  <div key={component.key}>
                    <div className="mb-1 flex items-center justify-between gap-3 text-sm">
                      <span className="font-medium text-slate-800">
                        {component.label} <span className="font-normal text-slate-400">· weight {Math.round(component.weight * 100)}%</span>
                      </span>
                      <span className="font-semibold tabular-nums text-slate-900">
                        {component.available && component.score !== null ? `${Math.round(component.score)}%` : 'No data'}
                      </span>
                    </div>
                    {component.available && component.score !== null ? (
                      <ProgressBar
                        value={component.score}
                        tone={component.score >= 60 ? 'emerald' : component.score >= 40 ? 'amber' : 'red'}
                        label={component.label}
                      />
                    ) : (
                      <div className="h-2 rounded-full border border-dashed border-slate-300" aria-hidden />
                    )}
                    <p className="mt-1 text-xs text-slate-500">{component.detail}</p>
                  </div>
                ))}
              </CardBody>
            </Card>
          </div>

          <div className="grid gap-6 lg:grid-cols-2">
            <Card>
              <CardHeader title="Areas requiring more preparation" icon={<AlertTriangle className="h-4 w-4" aria-hidden />} />
              <CardBody>
                {detail.data.focusAreas.length === 0 ? (
                  <EmptyState compact title="Looking good" description="No specific problem areas detected." />
                ) : (
                  <ul className="space-y-3">
                    {detail.data.focusAreas.map((area, index) => (
                      <li key={`${area.type}-${index}`} className="rounded-lg border border-slate-100 p-3">
                        <p className="text-sm font-medium text-slate-900">{area.title}</p>
                        <p className="mt-0.5 text-xs text-slate-600">{area.detail}</p>
                      </li>
                    ))}
                  </ul>
                )}
              </CardBody>
            </Card>
            <Card>
              <CardHeader title="Pending topics" description={`${detail.data.pendingTopics.length} not completed yet`} icon={<ListTodo className="h-4 w-4" aria-hidden />} />
              <CardBody>
                {detail.data.pendingTopics.length === 0 ? (
                  <EmptyState compact title="Syllabus complete" description="Keep revising to stay sharp." />
                ) : (
                  <ul className="divide-y divide-slate-100">
                    {detail.data.pendingTopics.map((topic) => (
                      <li key={topic.topicId} className="flex flex-wrap items-center justify-between gap-2 py-2.5">
                        <div>
                          <p className="text-sm font-medium text-slate-900">{topic.name}</p>
                          <p className="text-xs text-slate-500">Difficulty {topic.difficulty}/5</p>
                        </div>
                        <div className="flex gap-2">
                          <MasteryBadge level={masteryLevelOf(topic.masteryScore)} score={topic.masteryScore} />
                          <TopicStatusBadge status={topic.status} />
                        </div>
                      </li>
                    ))}
                  </ul>
                )}
              </CardBody>
            </Card>
          </div>

          <ReadinessTrendChart
            title="Readiness history"
            description="One snapshot per day"
            series={[{ id: detail.data.examId, name: detail.data.examTitle, points: detail.data.history }]}
          />
          <p className="text-center text-xs text-slate-500">{detail.data.disclaimer}</p>
        </div>
      )}
    </>
  );
}
