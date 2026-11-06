import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { UserPlus } from 'lucide-react';
import { AuthLayout } from '../layouts/AuthLayout';
import { useAuth } from '../context/AuthContext';
import { Button } from '../components/ui/Button';
import { FormError, SelectField, TextField } from '../components/ui/Form';
import { ApiError, errorMessage } from '../services/http';
import { browserTimeZone, timeZones } from '../utils/format';
import { validatePassword } from '../utils/validation';

export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({
    fullName: '',
    email: '',
    password: '',
    confirmPassword: '',
    timeZone: browserTimeZone(),
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const zones = timeZones();

  const update = (field: keyof typeof form) => (value: string) => setForm((current) => ({ ...current, [field]: value }));

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    const nextErrors: Record<string, string> = {};
    if (!form.fullName.trim()) nextErrors.fullName = 'Enter your name.';
    if (!/^\S+@\S+\.\S+$/.test(form.email.trim())) nextErrors.email = 'Enter a valid email address.';
    const passwordError = validatePassword(form.password);
    if (passwordError) nextErrors.password = passwordError;
    if (form.password !== form.confirmPassword) nextErrors.confirmPassword = 'Passwords do not match.';
    setErrors(nextErrors);
    setFormError(null);
    if (Object.keys(nextErrors).length > 0) return;

    setSubmitting(true);
    try {
      await register({
        fullName: form.fullName.trim(),
        email: form.email.trim(),
        password: form.password,
        timeZone: form.timeZone,
      });
      navigate('/settings?welcome=1', { replace: true });
    } catch (error) {
      if (error instanceof ApiError && error.fieldErrors.length > 0) setErrors(error.fieldErrorMap());
      setFormError(errorMessage(error));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthLayout title="Create your account" subtitle="Set up SmartStudy in under a minute.">
      <form className="space-y-4" onSubmit={onSubmit} noValidate>
        <FormError message={formError} />
        <TextField
          label="Full name"
          autoComplete="name"
          value={form.fullName}
          onChange={(event) => update('fullName')(event.target.value)}
          error={errors.fullName}
          required
        />
        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          value={form.email}
          onChange={(event) => update('email')(event.target.value)}
          error={errors.email}
          required
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            label="Password"
            type="password"
            autoComplete="new-password"
            value={form.password}
            onChange={(event) => update('password')(event.target.value)}
            error={errors.password}
            hint="8+ characters with a letter and a digit"
            required
          />
          <TextField
            label="Confirm password"
            type="password"
            autoComplete="new-password"
            value={form.confirmPassword}
            onChange={(event) => update('confirmPassword')(event.target.value)}
            error={errors.confirmPassword}
            required
          />
        </div>
        <SelectField
          label="Time zone"
          value={form.timeZone}
          onChange={(event) => update('timeZone')(event.target.value)}
          error={errors.timeZone}
          hint="Your study schedule is shown in this time zone."
        >
          {zones.map((zone) => (
            <option key={zone} value={zone}>
              {zone}
            </option>
          ))}
        </SelectField>
        <Button type="submit" className="w-full" loading={submitting} icon={<UserPlus className="h-4 w-4" aria-hidden />}>
          Create account
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-slate-500">
        Already have an account?{' '}
        <Link to="/login" className="font-medium text-emerald-700 hover:text-emerald-800">
          Sign in
        </Link>
      </p>
    </AuthLayout>
  );
}
