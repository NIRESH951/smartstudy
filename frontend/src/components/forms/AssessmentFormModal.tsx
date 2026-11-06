import { useEffect, useMemo, useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { Modal } from '../ui/Modal';
import { Button, IconButton } from '../ui/Button';
import { FormError, SelectField, TextAreaField, TextField } from '../ui/Form';
import { useExams, useSubjects, useTopicsBySubject } from '../../hooks/queries';
import { assessmentApi } from '../../services/api';
import { ApiError, errorMessage } from '../../services/http';
import { collectErrors, numberInRange, requireText } from '../../utils/validation';
import type { Assessment, AssessmentType } from '../../types/api';

interface TopicRow {
  key: number;
  topicId: string;
  maxMarks: string;
  obtainedMarks: string;
}

interface AssessmentFormModalProps {
  open: boolean;
  today: string;
  onClose: () => void;
  onSaved: (assessment: Assessment) => void;
}

/** Records an exam, quiz, assignment or mock test with optional topic-wise marks (these drive mastery). */
export function AssessmentFormModal({ open, today, onClose, onSaved }: AssessmentFormModalProps) {
  const { data: subjects } = useSubjects();
  const { data: exams } = useExams();
  const [subjectId, setSubjectId] = useState('');
  const topics = useTopicsBySubject(subjectId ? Number(subjectId) : Number.NaN);
  const [examId, setExamId] = useState('');
  const [title, setTitle] = useState('');
  const [type, setType] = useState<AssessmentType>('QUIZ');
  const [assessedOn, setAssessedOn] = useState(today);
  const [maxMarks, setMaxMarks] = useState('');
  const [obtainedMarks, setObtainedMarks] = useState('');
  const [notes, setNotes] = useState('');
  const [rows, setRows] = useState<TopicRow[]>([]);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setSubjectId('');
    setExamId('');
    setTitle('');
    setType('QUIZ');
    setAssessedOn(today);
    setMaxMarks('');
    setObtainedMarks('');
    setNotes('');
    setRows([]);
    setErrors({});
    setFormError(null);
  }, [open, today]);

  const subjectExams = useMemo(() => (exams ?? []).filter((exam) => String(exam.subjectId) === subjectId), [exams, subjectId]);
  const rowTotals = rows.reduce(
    (acc, row) => ({ max: acc.max + (Number(row.maxMarks) || 0), obtained: acc.obtained + (Number(row.obtainedMarks) || 0) }),
    { max: 0, obtained: 0 },
  );

  const updateRow = (key: number, patch: Partial<TopicRow>) =>
    setRows((current) => current.map((row) => (row.key === key ? { ...row, ...patch } : row)));

  const submit = async () => {
    const nextErrors = collectErrors({
      subjectId: subjectId ? null : 'Choose a subject.',
      title: requireText(title, 'Title', 150),
      assessedOn: !assessedOn ? 'Choose a date.' : assessedOn > today ? 'The date cannot be in the future.' : null,
      maxMarks: numberInRange(maxMarks, 'Maximum marks', 0.01, 10000),
      obtainedMarks:
        numberInRange(obtainedMarks, 'Obtained marks', 0, 10000) ??
        (Number(obtainedMarks) > Number(maxMarks) ? 'Obtained marks cannot exceed maximum marks.' : null),
    });
    const topicIds = new Set<string>();
    rows.forEach((row) => {
      if (!row.topicId) nextErrors.topicScores = 'Choose a topic in every topic row (or remove the row).';
      else if (topicIds.has(row.topicId)) nextErrors.topicScores = 'Each topic can appear only once.';
      topicIds.add(row.topicId);
      const max = Number(row.maxMarks);
      const obtained = Number(row.obtainedMarks);
      if (!(max > 0) || !(obtained >= 0) || obtained > max || row.maxMarks === '' || row.obtainedMarks === '') {
        nextErrors.topicScores = 'Every topic row needs maximum marks above 0 and obtained marks between 0 and the maximum.';
      }
    });
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;
    setSaving(true);
    setFormError(null);
    try {
      const saved = await assessmentApi.create({
        subjectId: Number(subjectId),
        examId: examId ? Number(examId) : null,
        title: title.trim(),
        assessmentType: type,
        assessedOn,
        maxMarks: Number(maxMarks),
        obtainedMarks: Number(obtainedMarks),
        notes: notes.trim() || null,
        topicScores: rows.map((row) => ({
          topicId: Number(row.topicId),
          maxMarks: Number(row.maxMarks),
          obtainedMarks: Number(row.obtainedMarks),
        })),
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
      title="Record assessment marks"
      description="Topic-wise marks update topic mastery and weak-topic detection immediately."
      onClose={onClose}
      size="lg"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={saving}>Cancel</Button>
          <Button onClick={submit} loading={saving}>Save marks</Button>
        </>
      }
    >
      <div className="space-y-4">
        <FormError message={formError} />
        <div className="grid gap-4 sm:grid-cols-2">
          <SelectField
            label="Subject"
            value={subjectId}
            onChange={(e) => {
              setSubjectId(e.target.value);
              setExamId('');
              setRows([]);
            }}
            error={errors.subjectId}
            required
          >
            <option value="">Select a subject</option>
            {(subjects ?? []).map((subject) => (
              <option key={subject.id} value={subject.id}>{subject.name}</option>
            ))}
          </SelectField>
          <SelectField label="Type" value={type} onChange={(e) => setType(e.target.value as AssessmentType)}>
            <option value="QUIZ">Quiz / unit test</option>
            <option value="EXAM">Exam</option>
            <option value="ASSIGNMENT">Assignment</option>
            <option value="MOCK_TEST">Mock test</option>
          </SelectField>
        </div>
        <div className="grid gap-4 sm:grid-cols-3">
          <TextField label="Title" value={title} onChange={(e) => setTitle(e.target.value)} error={errors.title} required maxLength={150} containerClassName="sm:col-span-2" placeholder="Unit Test 2" />
          <TextField label="Date" type="date" max={today} value={assessedOn} onChange={(e) => setAssessedOn(e.target.value)} error={errors.assessedOn} required />
        </div>
        <div className="grid gap-4 sm:grid-cols-3">
          <TextField label="Maximum marks" type="number" min={0.01} step="any" value={maxMarks} onChange={(e) => setMaxMarks(e.target.value)} error={errors.maxMarks} required />
          <TextField label="Marks obtained" type="number" min={0} step="any" value={obtainedMarks} onChange={(e) => setObtainedMarks(e.target.value)} error={errors.obtainedMarks} required />
          <SelectField label="For exam (optional)" value={examId} onChange={(e) => setExamId(e.target.value)} disabled={!subjectId} hint={type === 'MOCK_TEST' ? 'Link mock tests to their exam' : undefined}>
            <option value="">None</option>
            {subjectExams.map((exam) => (
              <option key={exam.id} value={exam.id}>{exam.title}</option>
            ))}
          </SelectField>
        </div>

        <fieldset className="rounded-xl border border-slate-200 p-4">
          <legend className="px-1 text-sm font-medium text-slate-700">Topic-wise marks</legend>
          {!subjectId && <p className="text-xs text-slate-500">Choose a subject first.</p>}
          {subjectId && rows.length === 0 && (
            <p className="text-xs text-slate-500">Optional, but recommended: split the marks by topic so SmartStudy can detect weak topics.</p>
          )}
          <div className="space-y-2">
            {rows.map((row) => (
              <div key={row.key} className="grid grid-cols-[1fr_5rem_5rem_auto] items-end gap-2">
                <SelectField label="Topic" value={row.topicId} onChange={(e) => updateRow(row.key, { topicId: e.target.value })}>
                  <option value="">Select</option>
                  {(topics.data ?? []).map((topic) => (
                    <option key={topic.id} value={topic.id}>{topic.name}</option>
                  ))}
                </SelectField>
                <TextField label="Max" type="number" min={0.01} step="any" value={row.maxMarks} onChange={(e) => updateRow(row.key, { maxMarks: e.target.value })} />
                <TextField label="Got" type="number" min={0} step="any" value={row.obtainedMarks} onChange={(e) => updateRow(row.key, { obtainedMarks: e.target.value })} />
                <IconButton label="Remove topic row" tone="danger" className="mb-1" onClick={() => setRows((current) => current.filter((r) => r.key !== row.key))}>
                  <Trash2 className="h-4 w-4" aria-hidden />
                </IconButton>
              </div>
            ))}
          </div>
          {errors.topicScores && <p className="mt-2 text-xs text-red-600">{errors.topicScores}</p>}
          <div className="mt-3 flex flex-wrap items-center gap-2">
            <Button
              size="sm"
              variant="secondary"
              icon={<Plus className="h-3.5 w-3.5" aria-hidden />}
              disabled={!subjectId || (topics.data?.length ?? 0) === 0}
              onClick={() => setRows((current) => [...current, { key: Date.now() + current.length, topicId: '', maxMarks: '', obtainedMarks: '' }])}
            >
              Add topic
            </Button>
            {rows.length > 0 && (
              <Button
                size="sm"
                variant="ghost"
                onClick={() => {
                  setMaxMarks(String(rowTotals.max));
                  setObtainedMarks(String(rowTotals.obtained));
                }}
              >
                Use topic totals ({rowTotals.obtained}/{rowTotals.max})
              </Button>
            )}
          </div>
        </fieldset>
        <TextAreaField label="Notes" value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={1000} />
      </div>
    </Modal>
  );
}
