import { useEffect, useState } from 'react';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { FormError, LevelPicker, SelectField, TextAreaField, TextField } from '../ui/Form';
import { topicApi } from '../../services/api';
import { ApiError, errorMessage } from '../../services/http';
import { collectErrors, numberInRange, requireText } from '../../utils/validation';
import type { Topic, TopicStatus } from '../../types/api';

const DIFFICULTY_LABELS = ['Very easy', 'Easy', 'Medium', 'Hard', 'Very hard'];

interface TopicFormModalProps {
  open: boolean;
  subjectId: number;
  topic?: Topic | null;
  onClose: () => void;
  onSaved: (topic: Topic) => void;
}

export function TopicFormModal({ open, subjectId, topic, onClose, onSaved }: TopicFormModalProps) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [difficulty, setDifficulty] = useState(3);
  const [hours, setHours] = useState('2');
  const [status, setStatus] = useState<TopicStatus>('NOT_STARTED');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setErrors({});
    setFormError(null);
    setName(topic?.name ?? '');
    setDescription(topic?.description ?? '');
    setDifficulty(topic?.difficulty ?? 3);
    setHours(topic ? String(Math.round((topic.estimatedMinutes / 60) * 4) / 4) : '2');
    setStatus(topic?.status ?? 'NOT_STARTED');
  }, [open, topic]);

  const submit = async () => {
    const nextErrors = collectErrors({
      name: requireText(name, 'Name', 150),
      estimatedMinutes: numberInRange(hours, 'Estimated hours', 0.25, 100),
    });
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;
    setSaving(true);
    setFormError(null);
    const body = {
      name: name.trim(),
      description: description.trim() || null,
      difficulty,
      estimatedMinutes: Math.round(Number(hours) * 60),
      status,
    };
    try {
      const saved = topic ? await topicApi.update(topic.id, body) : await topicApi.create(subjectId, body);
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
      title={topic ? 'Edit topic' : 'Add a topic'}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={saving}>Cancel</Button>
          <Button onClick={submit} loading={saving}>{topic ? 'Save changes' : 'Add topic'}</Button>
        </>
      }
    >
      <div className="space-y-4">
        <FormError message={formError} />
        <TextField label="Topic name" value={name} onChange={(e) => setName(e.target.value)} error={errors.name} required maxLength={150} />
        <LevelPicker label="Difficulty" value={difficulty} onChange={setDifficulty} labels={DIFFICULTY_LABELS} />
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            label="Estimated study time (hours)"
            type="number"
            min={0.25}
            max={100}
            step={0.25}
            value={hours}
            onChange={(e) => setHours(e.target.value)}
            error={errors.estimatedMinutes}
            hint="Time needed to cover the topic once"
          />
          <SelectField label="Status" value={status} onChange={(e) => setStatus(e.target.value as TopicStatus)}>
            <option value="NOT_STARTED">Not started</option>
            <option value="IN_PROGRESS">In progress</option>
            <option value="COMPLETED">Completed</option>
          </SelectField>
        </div>
        <TextAreaField label="Notes / description" value={description} onChange={(e) => setDescription(e.target.value)} maxLength={1000} />
      </div>
    </Modal>
  );
}
