import { useEffect, useMemo, useState } from 'react';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { FormError, SelectField, TextAreaField, TextField } from '../ui/Form';
import { useAllTopics, useSubjects } from '../../hooks/queries';
import { sessionApi } from '../../services/api';
import { ApiError, errorMessage } from '../../services/http';
import type { SessionType, StudySession } from '../../types/api';
import { formatTime } from '../../utils/format';
import { collectErrors, numberInRange } from '../../utils/validation';

interface SessionFormModalProps {
  open: boolean;
  /** When set, the modal edits (moves) this planned session; otherwise it creates a manual one. */
  session?: StudySession | null;
  defaultDate: string;
  onClose: () => void;
  onSaved: (session: StudySession) => void;
}

/** Create a manual session or move an existing planned session (conflicts are rejected by the server). */
export function SessionFormModal({ open, session, defaultDate, onClose, onSaved }: SessionFormModalProps) {
  const editing = Boolean(session);
  const { data: subjects } = useSubjects();
  const { data: topics } = useAllTopics();
  const [subjectId, setSubjectId] = useState('');
  const [topicId, setTopicId] = useState('');
  const [sessionType, setSessionType] = useState<SessionType>('STUDY');
  const [date, setDate] = useState(defaultDate);
  const [startTime, setStartTime] = useState('18:00');
  const [duration, setDuration] = useState('50');
  const [notes, setNotes] = useState('');
  const [locked, setLocked] = useState(true);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setErrors({});
    setFormError(null);
    if (session) {
      setSubjectId(String(session.subjectId));
      setTopicId(session.topicId ? String(session.topicId) : '');
      setSessionType(session.sessionType);
      setDate(session.localDate);
      setStartTime(formatTime(session.localStartTime));
      setDuration(String(session.plannedMinutes));
      setNotes(session.notes ?? '');
    } else {
      setSubjectId('');
      setTopicId('');
      setSessionType('STUDY');
      setDate(defaultDate);
      setStartTime('18:00');
      setDuration('50');
      setNotes('');
      setLocked(true);
    }
  }, [open, session, defaultDate]);

  const subjectTopics = useMemo(
    () => (topics ?? []).filter((topic) => String(topic.subjectId) === subjectId),
    [topics, subjectId],
  );

  const submit = async () => {
    const nextErrors = collectErrors({
      subjectId: !editing && !subjectId ? 'Choose a subject.' : null,
      date: !date ? 'Choose a date.' : null,
      startTime: !startTime ? 'Choose a start time.' : null,
      durationMinutes: numberInRange(duration, 'Duration', 15, 480),
    });
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;
    setSaving(true);
    setFormError(null);
    try {
      const saved = session
        ? await sessionApi.update(session.id, {
            date,
            startTime,
            durationMinutes: Number(duration),
            notes: notes.trim() || null,
          })
        : await sessionApi.create({
            subjectId: Number(subjectId),
            topicId: topicId ? Number(topicId) : null,
            sessionType,
            date,
            startTime,
            durationMinutes: Number(duration),
            notes: notes.trim() || null,
            locked,
          });
      onSaved(saved);
    } catch (error) {
      if (error instanceof ApiError) setErrors(error.fieldErrorMap());
      setFormError(errorMessage(error));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      title={editing ? 'Move session' : 'Add a study session'}
      description={editing ? 'Edited sessions are locked so the planner keeps them where you put them.' : 'Manual sessions are locked by default.'}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button onClick={submit} loading={saving}>
            {editing ? 'Save changes' : 'Add session'}
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <FormError message={formError} />
        {!editing && (
          <>
            <div className="grid gap-4 sm:grid-cols-2">
              <SelectField
                label="Subject"
                value={subjectId}
                onChange={(event) => {
                  setSubjectId(event.target.value);
                  setTopicId('');
                }}
                error={errors.subjectId}
                required
              >
                <option value="">Select a subject</option>
                {(subjects ?? []).filter((s) => !s.archived).map((subject) => (
                  <option key={subject.id} value={subject.id}>
                    {subject.name}
                  </option>
                ))}
              </SelectField>
              <SelectField label="Topic (optional)" value={topicId} onChange={(event) => setTopicId(event.target.value)} disabled={!subjectId}>
                <option value="">Whole subject</option>
                {subjectTopics.map((topic) => (
                  <option key={topic.id} value={topic.id}>
                    {topic.name}
                  </option>
                ))}
              </SelectField>
            </div>
            <SelectField label="Type" value={sessionType} onChange={(event) => setSessionType(event.target.value as SessionType)}>
              <option value="STUDY">Study</option>
              <option value="REVISION">Revision</option>
              <option value="PRACTICE">Practice</option>
            </SelectField>
          </>
        )}
        <div className="grid gap-4 sm:grid-cols-3">
          <TextField label="Date" type="date" value={date} onChange={(event) => setDate(event.target.value)} error={errors.date} required />
          <TextField label="Start time" type="time" value={startTime} onChange={(event) => setStartTime(event.target.value)} error={errors.startTime} required />
          <TextField
            label="Minutes"
            type="number"
            min={15}
            max={480}
            value={duration}
            onChange={(event) => setDuration(event.target.value)}
            error={errors.durationMinutes}
            required
          />
        </div>
        <TextAreaField label="Notes (optional)" value={notes} maxLength={1000} onChange={(event) => setNotes(event.target.value)} />
        {!editing && (
          <label className="flex items-center gap-2 text-sm text-slate-700">
            <input
              type="checkbox"
              checked={locked}
              onChange={(event) => setLocked(event.target.checked)}
              className="h-4 w-4 rounded border-slate-300 text-emerald-600 focus:ring-emerald-500"
            />
            Lock this session (the planner will not move it)
          </label>
        )}
      </div>
    </Modal>
  );
}
