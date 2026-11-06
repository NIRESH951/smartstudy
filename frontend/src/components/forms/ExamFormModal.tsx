import { useEffect, useState } from 'react';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { FormError, SelectField, TextAreaField, TextField } from '../ui/Form';
import { useSubjects } from '../../hooks/queries';
import { examApi } from '../../services/api';
import { ApiError, errorMessage } from '../../services/http';
import { collectErrors, numberInRange, requireText } from '../../utils/validation';
import { formatTime } from '../../utils/format';
import type { Exam, ExamType } from '../../types/api';

const EXAM_TYPES: { value: ExamType; label: string }[] = [
  { value: 'FINAL', label: 'Final / end-semester' },
  { value: 'MIDTERM', label: 'Mid-term' },
  { value: 'QUIZ', label: 'Quiz / unit test' },
  { value: 'PRACTICAL', label: 'Practical / lab' },
  { value: 'ENTRANCE', label: 'Entrance / competitive' },
  { value: 'OTHER', label: 'Other' },
];

export const EXAM_TYPE_LABELS = Object.fromEntries(EXAM_TYPES.map((t) => [t.value, t.label])) as Record<ExamType, string>;

interface ExamFormModalProps {
  open: boolean;
  exam?: Exam | null;
  defaultDate: string;
  onClose: () => void;
  onSaved: (exam: Exam) => void;
}

export function ExamFormModal({ open, exam, defaultDate, onClose, onSaved }: ExamFormModalProps) {
  const { data: subjects } = useSubjects();
  const [subjectId, setSubjectId] = useState('');
  const [title, setTitle] = useState('');
  const [examType, setExamType] = useState<ExamType>('FINAL');
  const [examDate, setExamDate] = useState(defaultDate);
  const [startTime, setStartTime] = useState('');
  const [duration, setDuration] = useState('');
  const [location, setLocation] = useState('');
  const [targetScore, setTargetScore] = useState('');
  const [notes, setNotes] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setErrors({});
    setFormError(null);
    setSubjectId(exam ? String(exam.subjectId) : '');
    setTitle(exam?.title ?? '');
    setExamType(exam?.examType ?? 'FINAL');
    setExamDate(exam?.examDate ?? defaultDate);
    setStartTime(exam?.startTime ? formatTime(exam.startTime) : '');
    setDuration(exam?.durationMinutes ? String(exam.durationMinutes) : '');
    setLocation(exam?.location ?? '');
    setTargetScore(exam?.targetScore !== null && exam?.targetScore !== undefined ? String(exam.targetScore) : '');
    setNotes(exam?.notes ?? '');
  }, [open, exam, defaultDate]);

  const submit = async () => {
    const nextErrors = collectErrors({
      subjectId: subjectId ? null : 'Choose a subject.',
      title: requireText(title, 'Title', 150),
      examDate: examDate ? null : 'Choose the exam date.',
      durationMinutes: numberInRange(duration, 'Duration', 1, 1440, false),
      targetScore: numberInRange(targetScore, 'Target score', 0, 100, false),
    });
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;
    setSaving(true);
    setFormError(null);
    const body = {
      subjectId: Number(subjectId),
      title: title.trim(),
      examType,
      examDate,
      startTime: startTime || null,
      durationMinutes: duration ? Number(duration) : null,
      location: location.trim() || null,
      targetScore: targetScore ? Number(targetScore) : null,
      notes: notes.trim() || null,
    };
    try {
      const saved = exam ? await examApi.update(exam.id, body) : await examApi.create(body);
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
      title={exam ? 'Edit exam' : 'Add an exam'}
      description="The planner finishes a subject's preparation before its latest upcoming exam."
      onClose={onClose}
      size="lg"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={saving}>Cancel</Button>
          <Button onClick={submit} loading={saving}>{exam ? 'Save changes' : 'Add exam'}</Button>
        </>
      }
    >
      <div className="space-y-4">
        <FormError message={formError} />
        <div className="grid gap-4 sm:grid-cols-2">
          <SelectField label="Subject" value={subjectId} onChange={(e) => setSubjectId(e.target.value)} error={errors.subjectId} required>
            <option value="">Select a subject</option>
            {(subjects ?? []).map((subject) => (
              <option key={subject.id} value={subject.id}>{subject.name}</option>
            ))}
          </SelectField>
          <SelectField label="Type" value={examType} onChange={(e) => setExamType(e.target.value as ExamType)}>
            {EXAM_TYPES.map((type) => (
              <option key={type.value} value={type.value}>{type.label}</option>
            ))}
          </SelectField>
        </div>
        <TextField label="Title" value={title} onChange={(e) => setTitle(e.target.value)} error={errors.title} required maxLength={150} placeholder="End-Semester Exam" />
        <div className="grid gap-4 sm:grid-cols-3">
          <TextField label="Date" type="date" value={examDate} onChange={(e) => setExamDate(e.target.value)} error={errors.examDate} required />
          <TextField label="Start time" type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} hint="Optional" />
          <TextField label="Duration (min)" type="number" min={1} max={1440} value={duration} onChange={(e) => setDuration(e.target.value)} error={errors.durationMinutes} />
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Location" value={location} onChange={(e) => setLocation(e.target.value)} maxLength={150} />
          <TextField label="Target score (%)" type="number" min={0} max={100} value={targetScore} onChange={(e) => setTargetScore(e.target.value)} error={errors.targetScore} />
        </div>
        <TextAreaField label="Notes" value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={1000} />
      </div>
    </Modal>
  );
}
