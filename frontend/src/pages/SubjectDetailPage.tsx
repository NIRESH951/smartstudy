import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { ArrowLeft, ListPlus, Pencil, Trash2 } from 'lucide-react';
import { useRefreshAll, useSubject, useTopicsBySubject } from '../hooks/queries';
import { useToast } from '../context/ToastContext';
import { topicApi } from '../services/api';
import { errorMessage } from '../services/http';
import { Card, CardBody, CardHeader, PageHeader, StatCard } from '../components/ui/Card';
import { Button, IconButton } from '../components/ui/Button';
import { EmptyState, ErrorState, LoadingBlock, ProgressBar } from '../components/ui/Feedback';
import { ConfirmDialog } from '../components/ui/Modal';
import { MasteryBadge } from '../components/StatusBadges';
import { TopicFormModal } from '../components/forms/TopicFormModal';
import { formatDate, formatMinutes, formatPercent } from '../utils/format';
import type { Topic, TopicStatus } from '../types/api';

export function SubjectDetailPage() {
  const params = useParams();
  const subjectId = Number(params.subjectId);
  const subject = useSubject(subjectId);
  const topics = useTopicsBySubject(subjectId);
  const refreshAll = useRefreshAll();
  const toast = useToast();
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Topic | null>(null);
  const [deleting, setDeleting] = useState<Topic | null>(null);
  const [busy, setBusy] = useState(false);

  const changeStatus = async (topic: Topic, status: TopicStatus) => {
    try {
      await topicApi.setStatus(topic.id, status);
      toast.success(
        status === 'COMPLETED'
          ? `${topic.name} completed - first revision scheduled for tomorrow.`
          : `${topic.name} marked as ${status === 'IN_PROGRESS' ? 'in progress' : 'not started'}.`,
      );
      await refreshAll();
    } catch (error) {
      toast.error(errorMessage(error));
    }
  };

  if (subject.error) {
    return (
      <Card>
        <ErrorState error={subject.error} />
        <div className="pb-6 text-center">
          <Link to="/subjects" className="text-sm font-medium text-emerald-700">Back to subjects</Link>
        </div>
      </Card>
    );
  }

  return (
    <>
      <Link to="/subjects" className="mb-3 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800">
        <ArrowLeft className="h-4 w-4" aria-hidden /> All subjects
      </Link>
      <PageHeader
        title={subject.data?.name ?? 'Subject'}
        description={subject.data?.description ?? 'Manage the syllabus topics of this subject.'}
        actions={<Button icon={<ListPlus className="h-4 w-4" aria-hidden />} onClick={() => { setEditing(null); setFormOpen(true); }}>Add topic</Button>}
      />

      {subject.data && (
        <div className="mb-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <StatCard label="Syllabus completion" value={formatPercent(subject.data.syllabusCompletion)} hint={`${subject.data.completedTopicCount} of ${subject.data.topicCount} topics`} />
          <StatCard label="Average mastery" value={subject.data.averageMastery === null ? '-' : formatPercent(subject.data.averageMastery)} hint={`${subject.data.assessedTopicCount} assessed topics`} accent="blue" />
          <StatCard label="Weak topics" value={subject.data.weakTopicCount} hint="Mastery below 40%" accent="red" />
          <StatCard label="Next exam" value={subject.data.nextExamDate ? formatDate(subject.data.nextExamDate) : '-'} hint={subject.data.nextExamTitle ?? 'None scheduled'} accent="amber" />
        </div>
      )}

      <Card>
        <CardHeader title="Topics" description="Difficulty and status feed the priority engine; mastery comes from your assessment marks." />
        <CardBody className="p-0">
          {topics.isLoading && <div className="p-5"><LoadingBlock rows={4} /></div>}
          {topics.error && <ErrorState error={topics.error} onRetry={() => topics.refetch()} />}
          {topics.data && topics.data.length === 0 && (
            <EmptyState
              title="No topics yet"
              description="Break the syllabus into topics (chapters or units) so the planner can schedule them."
              action={<Button onClick={() => setFormOpen(true)} icon={<ListPlus className="h-4 w-4" aria-hidden />}>Add a topic</Button>}
            />
          )}
          {topics.data && topics.data.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[760px] text-left text-sm">
                <thead className="border-b border-slate-100 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="px-5 py-3 font-medium">Topic</th>
                    <th className="px-3 py-3 font-medium">Difficulty</th>
                    <th className="px-3 py-3 font-medium">Progress</th>
                    <th className="px-3 py-3 font-medium">Mastery</th>
                    <th className="px-3 py-3 font-medium">Next revision</th>
                    <th className="px-3 py-3 font-medium">Status</th>
                    <th className="px-5 py-3 text-right font-medium">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {topics.data.map((topic) => {
                    const progress = Math.min(100, (topic.studiedMinutes / topic.estimatedMinutes) * 100);
                    return (
                      <tr key={topic.id} className="align-middle">
                        <td className="px-5 py-3">
                          <p className="font-medium text-slate-900">{topic.name}</p>
                          {topic.description && <p className="max-w-xs truncate text-xs text-slate-500">{topic.description}</p>}
                        </td>
                        <td className="px-3 py-3 text-slate-700">{topic.difficulty}/5</td>
                        <td className="w-40 px-3 py-3">
                          <ProgressBar value={topic.status === 'COMPLETED' ? 100 : progress} tone="blue" label={`${topic.name} progress`} />
                          <p className="mt-1 text-xs text-slate-500">
                            {formatMinutes(topic.studiedMinutes)} / {formatMinutes(topic.estimatedMinutes)}
                          </p>
                        </td>
                        <td className="px-3 py-3">
                          <MasteryBadge level={topic.masteryLevel} score={topic.masteryScore} />
                        </td>
                        <td className="px-3 py-3 text-xs text-slate-600">
                          {topic.nextRevisionDate ? formatDate(topic.nextRevisionDate) : <span className="text-slate-400">After completion</span>}
                        </td>
                        <td className="px-3 py-3">
                          <label className="sr-only" htmlFor={`status-${topic.id}`}>Status of {topic.name}</label>
                          <select
                            id={`status-${topic.id}`}
                            value={topic.status}
                            onChange={(event) => changeStatus(topic, event.target.value as TopicStatus)}
                            className="rounded-lg border border-slate-300 bg-white px-2 py-1 text-xs focus:border-emerald-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/20"
                          >
                            <option value="NOT_STARTED">Not started</option>
                            <option value="IN_PROGRESS">In progress</option>
                            <option value="COMPLETED">Completed</option>
                          </select>
                        </td>
                        <td className="px-5 py-3 text-right">
                          <IconButton label={`Edit ${topic.name}`} onClick={() => { setEditing(topic); setFormOpen(true); }}>
                            <Pencil className="h-4 w-4" aria-hidden />
                          </IconButton>
                          <IconButton label={`Delete ${topic.name}`} tone="danger" onClick={() => setDeleting(topic)}>
                            <Trash2 className="h-4 w-4" aria-hidden />
                          </IconButton>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </CardBody>
      </Card>

      <TopicFormModal
        open={formOpen}
        subjectId={subjectId}
        topic={editing}
        onClose={() => setFormOpen(false)}
        onSaved={async (saved) => {
          setFormOpen(false);
          toast.success(editing ? `${saved.name} updated.` : `${saved.name} added.`);
          await refreshAll();
        }}
      />
      <ConfirmDialog
        open={deleting !== null}
        title={`Delete ${deleting?.name ?? 'topic'}?`}
        message="Its sessions, marks and revision history are deleted as well."
        confirmLabel="Delete topic"
        loading={busy}
        onCancel={() => setDeleting(null)}
        onConfirm={async () => {
          if (!deleting) return;
          setBusy(true);
          try {
            await topicApi.remove(deleting.id);
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
