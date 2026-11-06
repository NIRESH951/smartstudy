import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { LogIn } from 'lucide-react';
import { AuthLayout } from '../layouts/AuthLayout';
import { useAuth } from '../context/AuthContext';
import { Button } from '../components/ui/Button';
import { FormError, TextField } from '../components/ui/Form';
import { ApiError, errorMessage } from '../services/http';
import { ServerStatus } from '../components/ServerStatus';

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const redirectTo = (location.state as { from?: string } | null)?.from ?? '/';

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    const nextErrors: Record<string, string> = {};
    if (!email.trim()) nextErrors.email = 'Enter your email address.';
    if (!password) nextErrors.password = 'Enter your password.';
    setErrors(nextErrors);
    setFormError(null);
    if (Object.keys(nextErrors).length > 0) return;

    setSubmitting(true);
    try {
      await login({ email: email.trim(), password });
      navigate(redirectTo, { replace: true });
    } catch (error) {
      if (error instanceof ApiError && error.fieldErrors.length > 0) setErrors(error.fieldErrorMap());
      setFormError(errorMessage(error));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthLayout title="Sign in" subtitle="Welcome back! Sign in to continue your study plan.">
      <form className="space-y-5" onSubmit={onSubmit} noValidate>
        <FormError message={formError} />
        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          error={errors.email}
          required
        />
        <TextField
          label="Password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          error={errors.password}
          required
        />
        <Button type="submit" className="w-full" loading={submitting} icon={<LogIn className="h-4 w-4" aria-hidden />}>
          Sign in
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-slate-500">
        New to SmartStudy?{' '}
        <Link to="/register" className="font-medium text-emerald-700 hover:text-emerald-800">
          Create an account
        </Link>
      </p>
      <div className="mt-8 rounded-xl border border-slate-200 bg-slate-50 p-4 text-xs text-slate-600">
        <p className="font-medium text-slate-700">Demo account (after loading database/sample-data.sql)</p>
        <p className="mt-1">
          Email <code className="rounded bg-white px-1">demo@smartstudy.dev</code> · Password{' '}
          <code className="rounded bg-white px-1">Demo@12345</code>
        </p>
      </div>
      <ServerStatus />
    </AuthLayout>
  );
}
