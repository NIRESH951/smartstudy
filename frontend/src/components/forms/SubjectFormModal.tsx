import { useEffect, useState } from 'react';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { FormError, LevelPicker, TextAreaField, TextField } from '../ui/Form';
import { subjectApi } from '../../services/api';
import { ApiError, errorMessage } from '../../services/http';
import { collectErrors, numberInRange, requireText } from '../../utils/validation';
import type { Subject } from '../../types/api';

export const SUBJECT_COLORS = ['#3B82F6', '#10B981', '#F59E0B', '#8B5CF6', '#EF4444', '#EC4899', '#14B8A6', '#6366F1', '#84CC16', '#F97316'];
const IMPORTANCE_LABELS = ['Low', 'Below average', 'Normal', 'High', 'Critical'];

interface SubjectFormModalProps {
  open: boolean;
  subject?: Subject | null;
  onClose: () => void;
  onSaved: (subject: Subject) => void;
}

export function SubjectFormModal({ open, subject, onClose, onSaved }: SubjectFormModalProps) {
  const [name, setName] = useState('');
  const [code, setCode] = useState('');
  const [color, setColor] = useState(SUBJECT_COLORS[0]);
  const [importance, setImportance] = useState(3);
  const [targetScore, setTargetScore] = useState('');
  const [description, setDescription] = useState('');
  const [archived, setArchived] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setErrors({});
    setFormError(null);
    setName(subject?.name ?? '');
    setCode(subject?.code ?? '');
    setColor(subject?.color ?? SUBJECT_COLORS[0]);
    setImportance(subject?.importance ?? 3);
    setTargetScore(subject?.targetScore !== null && subject?.targetScore !== undefined ? String(subject.targetScore) : '');
    setDescription(subject?.description ?? '');
    setArchived(subject?.archived ?? false);
  }, [open, subject]);

  const submit = async () => {
    const nextErrors = collectErrors({
      name: requireText(name, 'Name', 100),
      code: code.length > 20 ? 'Code must be at most 20 characters.' : null,
      targetScore: numberInRange(targetScore, 'Target score', 0, 100, false),
    });
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;
    setSaving(true);
    setFormError(null);
    const body = {
      name: name.trim(),
      code: code.trim() || null,
      color,
      importance,
      targetScore: targetScore.trim() === '' ? null : Number(targetScore),
      description: description.trim() || null,
      archived,
    };
    try {
      const saved = subject ? await subjectApi.update(subject.id, body) : await subjectApi.create(body);
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
      title={subject ? 'Edit subject' : 'Add a subject'}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={saving}>Cancel</Button>
          <Button onClick={submit} loading={saving}>{subject ? 'Save changes' : 'Add subject'}</Button>
        </>
      }
    >
      <div className="space-y-4">
        <FormError message={formError} />
        <div className="grid gap-4 sm:grid-cols-3">
          <TextField label="Subject name" value={name} onChange={(e) => setName(e.target.value)} error={errors.name} required maxLength={100} containerClassName="sm:col-span-2" />
          <TextField label="Code" value={code} onChange={(e) => setCode(e.target.value)} error={errors.code} maxLength={20} placeholder="CS301" />
        </div>
        <LevelPicker label="Importance" value={importance} onChange={setImportance} labels={IMPORTANCE_LABELS} />
        <div>
          <p className="mb-1.5 text-sm font-medium text-slate-700">Colour</p>
          <div className="flex flex-wrap gap-2" role="radiogroup" aria-label="Subject colour">
            {SUBJECT_COLORS.map((option) => (
              <button
                key={option}
                type="button"
                role="radio"
                aria-checked={color === option}
                aria-label={`Colour ${option}`}
                onClick={() => setColor(option)}
                className={`h-8 w-8 rounded-full border-2 ${color === option ? 'border-slate-900 ring-2 ring-white ring-inset' : 'border-transparent'}`}
                style={{ backgroundColor: option }}
              />
            ))}
          </div>
        </div>
        <TextField label="Target score (%)" type="number" min={0} max={100} value={targetScore} onChange={(e) => setTargetScore(e.target.value)} error={errors.targetScore} hint="Optional" />
        <TextAreaField label="Description" value={description} onChange={(e) => setDescription(e.target.value)} maxLength={500} error={errors.description} />
        {subject && (
          <label className="flex items-center gap-2 text-sm text-slate-700">
            <input type="checkbox" checked={archived} onChange={(e) => setArchived(e.target.checked)} className="h-4 w-4 rounded border-slate-300 text-emerald-600 focus:ring-emerald-500" />
            Archived (excluded from planning and analytics)
          </label>
        )}
      </div>
    </Modal>
  );
}
