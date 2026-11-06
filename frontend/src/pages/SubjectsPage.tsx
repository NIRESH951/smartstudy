import { useState } from 'react';
import { Link } from 'react-router';
import { Archive, BookOpen, CalendarClock, MoreHorizontal, Pencil, Plus, Trash2 } from 'lucide-react';
import { useRefreshAll, useSubjects } from '../hooks/queries';
import { useToast } from '../context/ToastContext';
import { subjectApi } from '../services/api';
import { errorMessage } from '../services/http';
import { Card, PageHeader } from '../components/ui/Card';
import { Button, IconButton } from '../components/ui/Button';
import { Badge, EmptyState, ErrorState, LoadingBlock, ProgressBar } from '../components/ui/Feedback';
import { ConfirmDialog } from '../components/ui/Modal';
import { SubjectFormModal } from '../components/forms/SubjectFormModal';
import { daysLabel, formatDate, formatPercent } from '../utils/format';
import type { Subject } from '../types/api';

export function SubjectsPage() {
  const { data, isLoading, error, refetch } = useSubjects();
  const refreshAll = useRefreshAll();
  const toast = useToast();
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Subject | null>(null);
  const [deleting, setDeleting] = useState<Subject | null>(null);
  const [busy, setBusy] = useState(false);
  const [showArchived, setShowArchived] = useState(false);

  const visible = (data ?? []).filter((subject) => showArchived || !subject.archived);
  const archivedCount = (data ?? []).filter((subject) => subject.archived).length;

  return (
    <>
      <PageHeader
        title="My Subjects"
        description="Subjects and their syllabus progress. Open a subject to manage its topics."
        actions={
          <Button icon={<Plus className="h-4 w-4" aria-hidden />} onClick={() => { setEditing(null); setFormOpen(true); }}>
            Add subject
          </Button>
        }
      />

      {isLoading && <Card className="p-6"><LoadingBlock rows={4} /></Card>}
      {error && <Card><ErrorState error={error} onRetry={() => refetch()} /></Card>}
      {data && data.length === 0 && (
        <Card>
          <EmptyState
            icon={<BookOpen className="h-5 w-5" aria-hidden />}
            title="No subjects yet"
            description="Add the subjects you are studying this semester."
            action={<Button onClick={() => setFormOpen(true)} icon={<Plus className="h-4 w-4" aria-hidden />}>Add your first subject</Button>}
          />
        </Card>
      )}

      {visible.length > 0 && (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {visible.map((subject) => (
            <Card key={subject.id} className={`flex flex-col overflow-hidden ${subject.archived ? 'opacity-70' : ''}`}>
              <div className="h-1.5" style={{ backgroundColor: subject.color }} aria-hidden />
              <div className="flex flex-1 flex-col p-5">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <Link to={`/subjects/${subject.id}`} className="text-base font-semibold text-slate-900 hover:text-emerald-700">
                      {subject.name}
                    </Link>
                    <p className="text-xs text-slate-500">
                      {subject.code ?? 'No code'} · Importance {subject.importance}/5
                    </p>
                  </div>
                  <div className="flex items-center">
                    {subject.archived && <Badge icon={<Archive className="h-3 w-3" aria-hidden />}>Archived</Badge>}
                    <IconButton label={`Edit ${subject.name}`} onClick={() => { setEditing(subject); setFormOpen(true); }}>
                      <Pencil className="h-4 w-4" aria-hidden />
                    </IconButton>
                    <IconButton label={`Delete ${subject.name}`} tone="danger" onClick={() => setDeleting(subject)}>
                      <Trash2 className="h-4 w-4" aria-hidden />
                    </IconButton>
                  </div>
                </div>

                <div className="mt-4">
                  <div className="mb-1 flex justify-between text-xs">
                    <span className="text-slate-500">Syllabus</span>
                    <span className="font-medium text-slate-700">
                      {subject.completedTopicCount}/{subject.topicCount} topics · {formatPercent(subject.syllabusCompletion)}
                    </span>
                  </div>
                  <ProgressBar value={subject.syllabusCompletion} label={`${subject.name} syllabus completion`} />
                </div>

                <dl className="mt-4 grid grid-cols-2 gap-3 text-xs">
                  <div>
                    <dt className="text-slate-500">Average mastery</dt>
                    <dd className="mt-0.5 text-sm font-semibold text-slate-900">
                      {subject.averageMastery === null ? 'Not assessed' : formatPercent(subject.averageMastery)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-slate-500">Weak topics</dt>
                    <dd className={`mt-0.5 text-sm font-semibold ${subject.weakTopicCount > 0 ? 'text-red-700' : 'text-slate-900'}`}>
                      {subject.weakTopicCount}
                    </dd>
                  </div>
                </dl>

                <div className="mt-auto pt-4">
                  {subject.nextExamDate ? (
                    <p className="flex items-center gap-1.5 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-900">
                      <CalendarClock className="h-3.5 w-3.5" aria-hidden />
                      {subject.nextExamTitle} · {formatDate(subject.nextExamDate)} ({daysLabel(subject.daysUntilNextExam ?? 0)})
                    </p>
                  ) : (
                    <p className="rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-500">No upcoming exam</p>
                  )}
                  <Link to={`/subjects/${subject.id}`} className="mt-3 inline-flex items-center gap-1 text-sm font-medium text-emerald-700 hover:text-emerald-800">
                    <MoreHorizontal className="h-4 w-4" aria-hidden /> Manage topics
                  </Link>
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}

      {archivedCount > 0 && (
        <Button variant="ghost" size="sm" className="mt-4" onClick={() => setShowArchived((value) => !value)}>
          {showArchived ? 'Hide archived subjects' : `Show ${archivedCount} archived subject${archivedCount === 1 ? '' : 's'}`}
        </Button>
      )}

      <SubjectFormModal
        open={formOpen}
        subject={editing}
        onClose={() => setFormOpen(false)}
        onSaved={async (saved) => {
          setFormOpen(false);
          toast.success(editing ? `${saved.name} updated.` : `${saved.name} added. Now add its topics.`);
          await refreshAll();
        }}
      />
      <ConfirmDialog
        open={deleting !== null}
        title={`Delete ${deleting?.name ?? 'subject'}?`}
        message="All topics, exams, sessions and assessments of this subject are deleted permanently. Consider archiving it instead."
        confirmLabel="Delete subject"
        loading={busy}
        onCancel={() => setDeleting(null)}
        onConfirm={async () => {
          if (!deleting) return;
          setBusy(true);
          try {
            await subjectApi.remove(deleting.id);
            toast.success(`${deleting.name} deleted.`);
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
