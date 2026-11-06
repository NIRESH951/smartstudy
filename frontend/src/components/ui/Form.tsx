import { useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react';

const CONTROL =
  'block w-full rounded-lg border bg-white px-3 py-2 text-sm text-slate-900 shadow-sm placeholder:text-slate-400 ' +
  'focus:border-emerald-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/20 disabled:bg-slate-50 disabled:text-slate-500';

function controlClass(error?: string) {
  return `${CONTROL} ${error ? 'border-red-400' : 'border-slate-300'}`;
}

interface FieldProps {
  label: string;
  error?: string;
  hint?: string;
  required?: boolean;
  children: (props: { id: string; describedBy?: string; invalid: boolean }) => ReactNode;
  className?: string;
}

/** Label + control + hint/error, wired with aria attributes. */
export function Field({ label, error, hint, required, children, className = '' }: FieldProps) {
  const id = useId();
  const messageId = `${id}-message`;
  return (
    <div className={className}>
      <label htmlFor={id} className="mb-1.5 block text-sm font-medium text-slate-700">
        {label}
        {required && <span className="ml-0.5 text-red-500">*</span>}
      </label>
      {children({ id, describedBy: error || hint ? messageId : undefined, invalid: Boolean(error) })}
      {error ? (
        <p id={messageId} className="mt-1 text-xs text-red-600">
          {error}
        </p>
      ) : hint ? (
        <p id={messageId} className="mt-1 text-xs text-slate-500">
          {hint}
        </p>
      ) : null}
    </div>
  );
}

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
  hint?: string;
  containerClassName?: string;
}

export function TextField({ label, error, hint, required, containerClassName, ...rest }: TextFieldProps) {
  return (
    <Field label={label} error={error} hint={hint} required={required} className={containerClassName}>
      {({ id, describedBy, invalid }) => (
        <input
          id={id}
          aria-describedby={describedBy}
          aria-invalid={invalid}
          required={required}
          className={controlClass(error)}
          {...rest}
        />
      )}
    </Field>
  );
}

interface SelectFieldProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string;
  error?: string;
  hint?: string;
  containerClassName?: string;
  children: ReactNode;
}

export function SelectField({ label, error, hint, required, containerClassName, children, ...rest }: SelectFieldProps) {
  return (
    <Field label={label} error={error} hint={hint} required={required} className={containerClassName}>
      {({ id, describedBy, invalid }) => (
        <select
          id={id}
          aria-describedby={describedBy}
          aria-invalid={invalid}
          required={required}
          className={controlClass(error)}
          {...rest}
        >
          {children}
        </select>
      )}
    </Field>
  );
}

interface TextAreaFieldProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label: string;
  error?: string;
  hint?: string;
  containerClassName?: string;
}

export function TextAreaField({ label, error, hint, required, containerClassName, ...rest }: TextAreaFieldProps) {
  return (
    <Field label={label} error={error} hint={hint} required={required} className={containerClassName}>
      {({ id, describedBy, invalid }) => (
        <textarea
          id={id}
          aria-describedby={describedBy}
          aria-invalid={invalid}
          required={required}
          rows={3}
          className={controlClass(error)}
          {...rest}
        />
      )}
    </Field>
  );
}

/** Inline banner for form-level errors returned by the API. */
export function FormError({ message }: { message?: string | null }) {
  if (!message) return null;
  return (
    <div role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
      {message}
    </div>
  );
}

/** 1-5 rating selector used for difficulty and importance. */
export function LevelPicker({
  label,
  value,
  onChange,
  labels,
}: {
  label: string;
  value: number;
  onChange: (value: number) => void;
  labels: string[];
}) {
  const id = useId();
  return (
    <fieldset>
      <legend id={id} className="mb-1.5 block text-sm font-medium text-slate-700">
        {label}: <span className="font-normal text-slate-500">{labels[value - 1]}</span>
      </legend>
      <div className="flex gap-1.5" role="radiogroup" aria-labelledby={id}>
        {[1, 2, 3, 4, 5].map((level) => (
          <button
            key={level}
            type="button"
            role="radio"
            aria-checked={value === level}
            aria-label={`${level} - ${labels[level - 1]}`}
            onClick={() => onChange(level)}
            className={`h-9 flex-1 rounded-lg border text-sm font-medium transition-colors ${
              value === level
                ? 'border-emerald-600 bg-emerald-600 text-white'
                : value > level
                  ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
                  : 'border-slate-300 bg-white text-slate-600 hover:bg-slate-50'
            }`}
          >
            {level}
          </button>
        ))}
      </div>
    </fieldset>
  );
}
