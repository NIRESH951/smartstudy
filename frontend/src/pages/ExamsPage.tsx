import { useState } from 'react';
import { Link } from 'react-router';
import { CalendarPlus, Gauge, MapPin, Pencil, Trash2 } from 'lucide-react';
import { useExams, useReadinessOverview, useRefreshAll } from '../hooks/queries';
import { useUser } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { examApi } from '../services/api';
import { errorMessage } from '../services/http';
import { Card, CardBody, CardHeader, PageHeader } from '../components/ui/Card';
import { Button, IconButton } from '../components/ui/Button';
import { Badge, ColorDot, EmptyState, ErrorState, LoadingBlock } from '../components/ui/Feedback';
import { ConfirmDialog } from '../components/ui/Modal';
import { ReadinessBadge } from '../components/StatusBadges';
import { EXAM_TYPE_LABELS, ExamFormModal } from '../components/forms/ExamFormModal';
import { addDays, daysLabel, formatDate, formatTime, todayIn } from '../utils/format';
import type { Exam } from '../types/api';

export function ExamsPage() {
  const user = useUser();
  const { data, isLoading, error, refetch } = useExams();
  const readiness = useReadinessOverview();
  const refreshAll = useRefreshAll();
  const toast = useToast();
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Exam | null>(null);
  const [deleting, setDeleting] = useState<Exam | null>(null);
  const [busy, setBusy] = useState(false);

  const upcoming = (data ?? []).filter((exam) => exam.upcoming);
  const past = (data ?? []).filter((exam) => !exam.upcoming).reverse();
  const readinessByExam = new Map((readiness.data ?? []).map((item) => [item.examId, item]));

  const renderRow = (exam: Exam) => {
    const ready = readinessByExam.get(exam.id);
    return (
      <li key={exam.id} className="flex flex-col gap-3 py-4 sm:flex-row sm:items-center">
        <div className="flex w-20 shrink-0 flex-col items-center rounded-xl border border-slate-200 py-2">
          <span className="text-xs font-medium uppercase text-slate-500">
            {new Intl.DateTimeFormat('en-IN', { month: 'short', timeZone: 'UTC' }).format(new Date(`${exam.examDate}T00:00:00Z`))}
          </span>
          <span className="text-xl font-semibold text-slate-900">{Number(exam.examDate.slice(8))}</span>
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <p className="text-sm font-semibold text-slate-900">{exam.title}</p>
            <Badge>{EXAM_TYPE_LABELS[exam.examType]}</Badge>
            {exam.upcoming && <span className="text-xs font-medium text-amber-700">{daysLabel(exam.daysRemaining)}</span>}
          </div>
          <p className="mt-0.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-slate-500">
            <span className="inline-flex items-center gap-1.5"><ColorDot color={exam.subjectColor} />{exam.subjectName}</span>
            <span>{formatDate(exam.examDate)}{exam.startTime && ` · ${formatTime(exam.startTime)}`}{exam.durationMinutes && ` · ${exam.durationMinutes} min`}</span>
            {exam.location && <span className="inline-flex items-center gap-1"><MapPin className="h-3 w-3" aria-hidden />{exam.location}</span>}
            {exam.targetScore !== null && <span>Target {exam.targetScore}%</span>}
          </p>
        </div>
        <div className="flex items-center gap-2">
          {exam.upcoming && ready && (
            <span className="flex items-center gap-2 text-sm">
              <span className="font-semibold tabular-nums text-slate-900">{ready.readinessScore === null ? '--' : `${Math.round(ready.readinessScore)}%`}</span>
              <ReadinessBadge level={ready.level} />
            </span>
          )}
          <Link to={`/exams/readiness?examId=${exam.id}`}>
            <Button size="sm" variant="secondary" icon={<Gauge className="h-3.5 w-3.5" aria-hidden />}>Readiness</Button>
          </Link>
          <IconButton label={`Edit ${exam.title}`} onClick={() => { setEditing(exam); setFormOpen(true); }}>
            <Pencil className="h-4 w-4" aria-hidden />
          </IconButton>
          <IconButton label={`Delete ${exam.title}`} tone="danger" onClick={() => setDeleting(exam)}>
            <Trash2 className="h-4 w-4" aria-hidden />
          </IconButton>
        </div>
      </li>
    );
  };

  return (
    <>
      <PageHeader
        title="Examinations"
        description="Exam dates drive urgency in your study plan and readiness tracking."
        actions={
          <>
            <Link to="/exams/readiness"><Button variant="secondary" icon={<Gauge className="h-4 w-4" aria-hidden />}>Readiness dashboard</Button></Link>
            <Button icon={<CalendarPlus className="h-4 w-4" aria-hidden />} onClick={() => { setEditing(null); setFormOpen(true); }}>Add exam</Button>
          </>
        }
      />
      {isLoading && <Card className="p-6"><LoadingBlock rows={4} /></Card>}
      {error && <Card><ErrorState error={error} onRetry={() => refetch()} /></Card>}
      {data && (
        <div className="space-y-6">
          <Card>
            <CardHeader title="Upcoming exams" description={`${upcoming.length} scheduled`} />
            <CardBody>
              {upcoming.length === 0 ? (
                <EmptyState title="No upcoming exams" description="Add your exam timetable so the planner can prioritise." action={<Button onClick={() => setFormOpen(true)}>Add exam</Button>} />
              ) : (
                <ul className="divide-y divide-slate-100">{upcoming.map(renderRow)}</ul>
              )}
            </CardBody>
          </Card>
          {past.length > 0 && (
            <Card>
              <CardHeader title="Past exams" />
              <CardBody>
                <ul className="divide-y divide-slate-100">{past.map(renderRow)}</ul>
              </CardBody>
            </Card>
          )}
        </div>
      )}

      <ExamFormModal
        open={formOpen}
        exam={editing}
        defaultDate={addDays(todayIn(user.timeZone), 14)}
        onClose={() => setFormOpen(false)}
        onSaved={async (saved) => {
          setFormOpen(false);
          toast.success(editing ? `${saved.title} updated.` : `${saved.title} added.`);
          await refreshAll();
        }}
      />
      <ConfirmDialog
        open={deleting !== null}
        title={`Delete ${deleting?.title ?? 'exam'}?`}
        message="Its readiness history is deleted. Assessments linked to it are kept."
        confirmLabel="Delete exam"
        loading={busy}
        onCancel={() => setDeleting(null)}
        onConfirm={async () => {
          if (!deleting) return;
          setBusy(true);
          try {
            await examApi.remove(deleting.id);
            toast.success('Exam deleted.');
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
