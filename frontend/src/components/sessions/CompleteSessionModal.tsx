import { useEffect, useState } from 'react';
import { CheckCircle2 } from 'lucide-react';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { FormError, TextAreaField, TextField } from '../ui/Form';
import { sessionApi } from '../../services/api';
import { ApiError, errorMessage } from '../../services/http';
import type { RevisionRating, SessionActionResponse, StudySession } from '../../types/api';
import { formatTime } from '../../utils/format';

export const RATINGS: { value: RevisionRating; label: string; hint: string; className: string }[] = [
  { value: 'AGAIN', label: 'Again', hint: 'Forgot it', className: 'border-red-300 text-red-700 hover:bg-red-50' },
  { value: 'HARD', label: 'Hard', hint: 'Recalled with effort', className: 'border-amber-300 text-amber-800 hover:bg-amber-50' },
  { value: 'GOOD', label: 'Good', hint: 'Recalled well', className: 'border-blue-300 text-blue-700 hover:bg-blue-50' },
  { value: 'EASY', label: 'Easy', hint: 'Effortless', className: 'border-emerald-300 text-emerald-700 hover:bg-emerald-50' },
];

export function RatingPicker({ value, onChange }: { value: RevisionRating | null; onChange: (rating: RevisionRating) => void }) {
  return (
    <div className="grid grid-cols-2 gap-2 sm:grid-cols-4" role="radiogroup" aria-label="How well did you remember it?">
      {RATINGS.map((rating) => (
        <button
          key={rating.value}
          type="button"
          role="radio"
          aria-checked={value === rating.value}
          onClick={() => onChange(rating.value)}
          className={`rounded-lg border px-3 py-2 text-left text-sm transition-colors ${rating.className} ${
            value === rating.value ? 'ring-2 ring-offset-1 ring-current' : 'bg-white'
          }`}
        >
          <span className="block font-semibold">{rating.label}</span>
          <span className="block text-xs opacity-80">{rating.hint}</span>
        </button>
      ))}
    </div>
  );
}

interface CompleteSessionModalProps {
  session: StudySession | null;
  onClose: () => void;
  onDone: (result: SessionActionResponse) => void;
}

/** Records how much of a session was actually studied (validated by the server against its clock). */
export function CompleteSessionModal({ session, onClose, onDone }: CompleteSessionModalProps) {
  const [minutes, setMinutes] = useState('');
  const [topicCompleted, setTopicCompleted] = useState(false);
  const [rating, setRating] = useState<RevisionRating | null>(null);
  const [notes, setNotes] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (session) {
      setMinutes(String(session.plannedMinutes));
      setTopicCompleted(false);
      setRating(null);
      setNotes('');
      setError(null);
      setFieldErrors({});
    }
  }, [session]);

  if (!session) return null;
  const needsRating = session.sessionType === 'REVISION' && session.topicId !== null;

  const submit = async () => {
    const value = Number(minutes);
    const errors: Record<string, string> = {};
    if (!Number.isInteger(value) || value < 1 || value > 600) errors.actualMinutes = 'Enter whole minutes between 1 and 600.';
    if (needsRating && !rating) errors.revisionRating = 'Choose how well you remembered the topic.';
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;
    setSaving(true);
    setError(null);
    try {
      const result = await sessionApi.complete(session.id, {
        actualMinutes: value,
        topicCompleted: session.sessionType === 'STUDY' && session.topicId !== null ? topicCompleted : null,
        revisionRating: rating,
        notes: notes.trim() || null,
      });
      onDone(result);
    } catch (caught) {
      if (caught instanceof ApiError) setFieldErrors(caught.fieldErrorMap());
      setError(errorMessage(caught));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open
      title={session.status === 'MISSED' ? 'Log this session as studied' : 'Complete session'}
      description={`${session.topicName ?? session.subjectName} · ${formatTime(session.localStartTime)}-${formatTime(session.localEndTime)}`}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button onClick={submit} loading={saving} icon={<CheckCircle2 className="h-4 w-4" aria-hidden />}>
            Save
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <FormError message={error} />
        <TextField
          label="Minutes actually studied"
          type="number"
          min={1}
          max={600}
          value={minutes}
          onChange={(event) => setMinutes(event.target.value)}
          error={fieldErrors.actualMinutes}
          hint={`Planned: ${session.plannedMinutes} min. Under 80% is saved as incomplete and the rest is rescheduled.`}
        />
        {session.sessionType === 'STUDY' && session.topicId !== null && (
          <label className="flex items-start gap-3 rounded-lg border border-slate-200 p-3 text-sm">
            <input
              type="checkbox"
              checked={topicCompleted}
              onChange={(event) => setTopicCompleted(event.target.checked)}
              className="mt-0.5 h-4 w-4 rounded border-slate-300 text-emerald-600 focus:ring-emerald-500"
            />
            <span>
              <span className="font-medium text-slate-800">I finished this topic</span>
              <span className="block text-xs text-slate-500">Marks the topic as completed and schedules its first revision for tomorrow.</span>
            </span>
          </label>
        )}
        {(needsRating || session.sessionType === 'PRACTICE') && (
          <div>
            <p className="mb-1.5 text-sm font-medium text-slate-700">
              How well did you remember it?{needsRating && <span className="ml-0.5 text-red-500">*</span>}
            </p>
            <RatingPicker value={rating} onChange={setRating} />
            {fieldErrors.revisionRating && <p className="mt-1 text-xs text-red-600">{fieldErrors.revisionRating}</p>}
          </div>
        )}
        <TextAreaField label="Notes (optional)" value={notes} maxLength={1000} onChange={(event) => setNotes(event.target.value)} />
      </div>
    </Modal>
  );
}
