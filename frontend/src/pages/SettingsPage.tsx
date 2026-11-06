import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router';
import { CalendarClock, KeyRound, Plus, Sparkles, Timer, Trash2, UserRound } from 'lucide-react';
import { useAvailability } from '../hooks/queries';
import { useAuth, useUser } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { availabilityApi, profileApi } from '../services/api';
import { ApiError, errorMessage } from '../services/http';
import { Card, CardBody, CardHeader, PageHeader } from '../components/ui/Card';
import { Button, IconButton } from '../components/ui/Button';
import { ErrorState, LoadingBlock } from '../components/ui/Feedback';
import { FormError, SelectField, TextField } from '../components/ui/Form';
import { collectErrors, numberInRange, requireText, validatePassword } from '../utils/validation';
import { DAY_NAMES, formatDate, formatMinutes, timeZones } from '../utils/format';
import type { AvailabilityResponse } from '../types/api';

// ------------------------------------------------------------------ profile + pomodoro

function ProfileSection() {
  const user = useUser();
  const { setUser } = useAuth();
  const toast = useToast();
  const zones = useMemo(() => {
    const list = timeZones();
    return list.includes(user.timeZone) ? list : [user.timeZone, ...list];
  }, [user.timeZone]);

  const [form, setForm] = useState({
    fullName: user.fullName,
    timeZone: user.timeZone,
    focusMinutes: String(user.focusMinutes),
    shortBreakMinutes: String(user.shortBreakMinutes),
    longBreakMinutes: String(user.longBreakMinutes),
    longBreakInterval: String(user.longBreakInterval),
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const set = (key: keyof typeof form) => (value: string) => setForm((current) => ({ ...current, [key]: value }));

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const found = collectErrors({
      fullName: requireText(form.fullName, 'Name', 100),
      timeZone: form.timeZone ? null : 'Time zone is required.',
      focusMinutes: numberInRange(form.focusMinutes, 'Focus length', 5, 120),
      shortBreakMinutes: numberInRange(form.shortBreakMinutes, 'Short break', 1, 60),
      longBreakMinutes: numberInRange(form.longBreakMinutes, 'Long break', 1, 120),
      longBreakInterval: numberInRange(form.longBreakInterval, 'Sessions before a long break', 2, 12),
    });
    setErrors(found);
    setFormError(null);
    if (Object.keys(found).length > 0) return;
    setSaving(true);
    try {
      const profile = await profileApi.update({
        fullName: form.fullName.trim(),
        timeZone: form.timeZone,
        focusMinutes: Number(form.focusMinutes),
        shortBreakMinutes: Number(form.shortBreakMinutes),
        longBreakMinutes: Number(form.longBreakMinutes),
        longBreakInterval: Number(form.longBreakInterval),
      });
      setUser(profile);
      toast.success('Profile saved.');
    } catch (error) {
      if (error instanceof ApiError && error.fieldErrors.length > 0) setErrors(error.fieldErrorMap());
      setFormError(errorMessage(error));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card>
      <CardHeader title="Profile & Pomodoro" description="Your time zone decides what 'today' means for plans and streaks." icon={<UserRound className="h-5 w-5" aria-hidden />} />
      <CardBody>
        <form onSubmit={submit} className="space-y-4" noValidate>
          <FormError message={formError} />
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField label="Full name" required value={form.fullName} error={errors.fullName} maxLength={100} onChange={(e) => set('fullName')(e.target.value)} />
            <TextField label="Email" value={user.email} disabled hint="Email is your sign-in name and cannot be changed." />
          </div>
          <SelectField label="Time zone" required value={form.timeZone} error={errors.timeZone} onChange={(e) => set('timeZone')(e.target.value)}>
            {zones.map((zone) => (
              <option key={zone} value={zone}>{zone}</option>
            ))}
          </SelectField>
          <div>
            <h3 className="mb-2 flex items-center gap-1.5 text-sm font-semibold text-slate-800">
              <Timer className="h-4 w-4 text-slate-400" aria-hidden /> Pomodoro timer
            </h3>
            <div className="grid gap-4 sm:grid-cols-4">
              <TextField label="Focus (min)" type="number" min={5} max={120} value={form.focusMinutes} error={errors.focusMinutes} onChange={(e) => set('focusMinutes')(e.target.value)} />
              <TextField label="Short break (min)" type="number" min={1} max={60} value={form.shortBreakMinutes} error={errors.shortBreakMinutes} onChange={(e) => set('shortBreakMinutes')(e.target.value)} />
              <TextField label="Long break (min)" type="number" min={1} max={120} value={form.longBreakMinutes} error={errors.longBreakMinutes} onChange={(e) => set('longBreakMinutes')(e.target.value)} />
              <TextField label="Long break after" type="number" min={2} max={12} value={form.longBreakInterval} error={errors.longBreakInterval} hint="focus sessions" onChange={(e) => set('longBreakInterval')(e.target.value)} />
            </div>
          </div>
          <div className="flex justify-end">
            <Button type="submit" loading={saving}>Save profile</Button>
          </div>
        </form>
      </CardBody>
    </Card>
  );
}

// ------------------------------------------------------------------ availability

interface WindowDraft {
  key: string;
  dayOfWeek: number;
  startTime: string;
  endTime: string;
}

let draftCounter = 0;
const nextKey = () => `w${++draftCounter}`;

function minutesOf(time: string): number {
  const [h, m] = time.split(':').map(Number);
  return (h ?? 0) * 60 + (m ?? 0);
}

function validateWindows(windows: WindowDraft[]): string | null {
  for (const w of windows) {
    if (!w.startTime || !w.endTime) return 'Every window needs a start and an end time.';
    if (minutesOf(w.endTime) <= minutesOf(w.startTime)) return `${DAY_NAMES[w.dayOfWeek - 1]}: the end time must be after the start time.`;
    if (minutesOf(w.endTime) - minutesOf(w.startTime) < 15) return `${DAY_NAMES[w.dayOfWeek - 1]}: each window must be at least 15 minutes.`;
  }
  for (let day = 1; day <= 7; day++) {
    const sorted = windows.filter((w) => w.dayOfWeek === day).sort((a, b) => minutesOf(a.startTime) - minutesOf(b.startTime));
    for (let i = 1; i < sorted.length; i++) {
      if (minutesOf(sorted[i].startTime) < minutesOf(sorted[i - 1].endTime)) return `${DAY_NAMES[day - 1]}: windows must not overlap.`;
    }
  }
  return null;
}

function AvailabilityEditor({ initial }: { initial: AvailabilityResponse }) {
  const toast = useToast();
  const { setUser } = useAuth();
  const [budgets, setBudgets] = useState({
    dailyStudyMinutes: String(initial.dailyStudyMinutes),
    weeklyStudyMinutes: String(initial.weeklyStudyMinutes),
    preferredSessionMinutes: String(initial.preferredSessionMinutes),
    breakMinutes: String(initial.breakMinutes),
  });
  const [windows, setWindows] = useState<WindowDraft[]>(() =>
    initial.windows.map((w) => ({ key: nextKey(), dayOfWeek: w.dayOfWeek, startTime: w.startTime.slice(0, 5), endTime: w.endTime.slice(0, 5) })),
  );
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  const totalWindowMinutes = windows.reduce((sum, w) => {
    const minutes = minutesOf(w.endTime) - minutesOf(w.startTime);
    return sum + (Number.isFinite(minutes) && minutes > 0 ? minutes : 0);
  }, 0);

  const setBudget = (key: keyof typeof budgets) => (value: string) => setBudgets((current) => ({ ...current, [key]: value }));
  const updateWindow = (key: string, patch: Partial<WindowDraft>) =>
    setWindows((current) => current.map((w) => (w.key === key ? { ...w, ...patch } : w)));
  const addWindow = (dayOfWeek: number) => {
    const sameDay = windows.filter((w) => w.dayOfWeek === dayOfWeek);
    const lastEnd = sameDay.reduce((max, w) => Math.max(max, minutesOf(w.endTime)), 0);
    const start = sameDay.length === 0 ? 18 * 60 : Math.min(lastEnd + 30, 22 * 60);
    const end = Math.min(start + 120, 23 * 60 + 59);
    const fmt = (m: number) => `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
    setWindows((current) => [...current, { key: nextKey(), dayOfWeek, startTime: fmt(start), endTime: fmt(end) }]);
  };
  const copyWeekdays = () => {
    const monday = windows.filter((w) => w.dayOfWeek === 1);
    if (monday.length === 0) {
      toast.info('Add at least one Monday window first.');
      return;
    }
    const rest = windows.filter((w) => w.dayOfWeek > 5);
    const copies = [1, 2, 3, 4, 5].flatMap((day) => monday.map((w) => ({ ...w, key: nextKey(), dayOfWeek: day })));
    setWindows([...copies, ...rest]);
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSaved(false);
    const found = collectErrors({
      dailyStudyMinutes: numberInRange(budgets.dailyStudyMinutes, 'Daily study time', 0, 1440),
      weeklyStudyMinutes: numberInRange(budgets.weeklyStudyMinutes, 'Weekly study time', 0, 10080),
      preferredSessionMinutes: numberInRange(budgets.preferredSessionMinutes, 'Session length', 15, 240),
      breakMinutes: numberInRange(budgets.breakMinutes, 'Break between sessions', 0, 60),
    });
    if (!found.dailyStudyMinutes && !found.weeklyStudyMinutes && Number(budgets.dailyStudyMinutes) > Number(budgets.weeklyStudyMinutes)) {
      found.dailyStudyMinutes = 'Daily study time cannot exceed weekly study time.';
    }
    setErrors(found);
    const windowError = validateWindows(windows);
    setFormError(windowError);
    if (Object.keys(found).length > 0 || windowError) return;

    setSaving(true);
    try {
      await availabilityApi.replace({
        dailyStudyMinutes: Number(budgets.dailyStudyMinutes),
        weeklyStudyMinutes: Number(budgets.weeklyStudyMinutes),
        preferredSessionMinutes: Number(budgets.preferredSessionMinutes),
        breakMinutes: Number(budgets.breakMinutes),
        windows: windows.map(({ dayOfWeek, startTime, endTime }) => ({ dayOfWeek, startTime, endTime })),
      });
      // The profile carries the same study-time budgets; keep the signed-in user in sync.
      setUser(await profileApi.get());
      setSaved(true);
      toast.success('Availability saved.');
    } catch (error) {
      if (error instanceof ApiError && error.fieldErrors.length > 0) setErrors(error.fieldErrorMap());
      setFormError(errorMessage(error));
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={submit} className="space-y-5" noValidate>
      <FormError message={formError} />
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <TextField label="Daily limit (min)" type="number" min={0} max={1440} value={budgets.dailyStudyMinutes} error={errors.dailyStudyMinutes} hint={formatMinutes(Number(budgets.dailyStudyMinutes) || 0)} onChange={(e) => setBudget('dailyStudyMinutes')(e.target.value)} />
        <TextField label="Weekly limit (min)" type="number" min={0} max={10080} value={budgets.weeklyStudyMinutes} error={errors.weeklyStudyMinutes} hint={formatMinutes(Number(budgets.weeklyStudyMinutes) || 0)} onChange={(e) => setBudget('weeklyStudyMinutes')(e.target.value)} />
        <TextField label="Session length (min)" type="number" min={15} max={240} value={budgets.preferredSessionMinutes} error={errors.preferredSessionMinutes} onChange={(e) => setBudget('preferredSessionMinutes')(e.target.value)} />
        <TextField label="Break between (min)" type="number" min={0} max={60} value={budgets.breakMinutes} error={errors.breakMinutes} onChange={(e) => setBudget('breakMinutes')(e.target.value)} />
      </div>

      <div>
        <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
          <h3 className="text-sm font-semibold text-slate-800">Weekly study windows</h3>
          <div className="flex items-center gap-3">
            <span className="text-xs text-slate-500">{formatMinutes(totalWindowMinutes)} available per week</span>
            <Button size="sm" variant="secondary" onClick={copyWeekdays}>Copy Monday to weekdays</Button>
          </div>
        </div>
        <ul className="divide-y divide-slate-100 rounded-xl border border-slate-200">
          {DAY_NAMES.map((dayName, index) => {
            const day = index + 1;
            const dayWindows = windows.filter((w) => w.dayOfWeek === day).sort((a, b) => a.startTime.localeCompare(b.startTime));
            return (
              <li key={day} className="flex flex-col gap-2 px-4 py-3 sm:flex-row sm:items-start">
                <span className="w-28 shrink-0 pt-1.5 text-sm font-medium text-slate-700">{dayName}</span>
                <div className="flex flex-1 flex-col gap-2">
                  {dayWindows.length === 0 && <span className="pt-1.5 text-sm text-slate-400">Not available</span>}
                  {dayWindows.map((w) => (
                    <div key={w.key} className="flex flex-wrap items-center gap-2">
                      <input
                        type="time"
                        aria-label={`${dayName} window start`}
                        value={w.startTime}
                        onChange={(e) => updateWindow(w.key, { startTime: e.target.value })}
                        className="h-9 rounded-lg border border-slate-300 px-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/20"
                      />
                      <span className="text-sm text-slate-400">to</span>
                      <input
                        type="time"
                        aria-label={`${dayName} window end`}
                        value={w.endTime}
                        onChange={(e) => updateWindow(w.key, { endTime: e.target.value })}
                        className="h-9 rounded-lg border border-slate-300 px-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/20"
                      />
                      <IconButton label={`Remove ${dayName} window`} tone="danger" onClick={() => setWindows((current) => current.filter((x) => x.key !== w.key))}>
                        <Trash2 className="h-4 w-4" aria-hidden />
                      </IconButton>
                    </div>
                  ))}
                </div>
                <Button size="sm" variant="ghost" icon={<Plus className="h-4 w-4" aria-hidden />} onClick={() => addWindow(day)} disabled={dayWindows.length >= 6}>
                  Add window
                </Button>
              </li>
            );
          })}
        </ul>
        <p className="mt-2 text-xs text-slate-500">Times are in your time zone. The planner never schedules outside these windows or beyond your daily and weekly limits.</p>
      </div>

      <div className="flex flex-wrap items-center justify-end gap-3">
        {saved && (
          <Link to="/planner" className="text-sm font-medium text-emerald-700 hover:underline">
            Regenerate your plan with the new availability →
          </Link>
        )}
        <Button type="submit" loading={saving}>Save availability</Button>
      </div>
    </form>
  );
}

function AvailabilitySection() {
  const availability = useAvailability();
  return (
    <Card>
      <CardHeader title="Study availability" description="When you can study and how much - the adaptive planner fills only these windows." icon={<CalendarClock className="h-5 w-5" aria-hidden />} />
      <CardBody>
        {availability.isLoading && <LoadingBlock rows={6} />}
        {availability.error && <ErrorState error={availability.error} onRetry={() => availability.refetch()} />}
        {availability.data && <AvailabilityEditor key={availability.dataUpdatedAt} initial={availability.data} />}
      </CardBody>
    </Card>
  );
}

// ------------------------------------------------------------------ password

function PasswordSection() {
  const toast = useToast();
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const found = collectErrors({
      currentPassword: form.currentPassword ? null : 'Current password is required.',
      newPassword: validatePassword(form.newPassword),
      confirmPassword: form.confirmPassword === form.newPassword ? null : 'Passwords do not match.',
    });
    setErrors(found);
    setFormError(null);
    if (Object.keys(found).length > 0) return;
    setSaving(true);
    try {
      await profileApi.changePassword({ currentPassword: form.currentPassword, newPassword: form.newPassword });
      setForm({ currentPassword: '', newPassword: '', confirmPassword: '' });
      toast.success('Password changed.');
    } catch (error) {
      if (error instanceof ApiError && error.fieldErrors.length > 0) setErrors(error.fieldErrorMap());
      setFormError(errorMessage(error));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card>
      <CardHeader title="Change password" icon={<KeyRound className="h-5 w-5" aria-hidden />} />
      <CardBody>
        <form onSubmit={submit} className="space-y-4" noValidate>
          <FormError message={formError} />
          <TextField label="Current password" type="password" autoComplete="current-password" value={form.currentPassword} error={errors.currentPassword} onChange={(e) => setForm({ ...form, currentPassword: e.target.value })} />
          <TextField label="New password" type="password" autoComplete="new-password" value={form.newPassword} error={errors.newPassword} hint="8-72 characters with at least one letter and one digit." onChange={(e) => setForm({ ...form, newPassword: e.target.value })} />
          <TextField label="Confirm new password" type="password" autoComplete="new-password" value={form.confirmPassword} error={errors.confirmPassword} onChange={(e) => setForm({ ...form, confirmPassword: e.target.value })} />
          <div className="flex justify-end">
            <Button type="submit" loading={saving}>Change password</Button>
          </div>
        </form>
      </CardBody>
    </Card>
  );
}

// ------------------------------------------------------------------ page

export function SettingsPage() {
  const user = useUser();
  const [params, setParams] = useSearchParams();
  const welcome = params.get('welcome') === '1';
  const [showWelcome, setShowWelcome] = useState(welcome);

  useEffect(() => {
    if (welcome) {
      // Keep the banner visible but clean the URL so a refresh does not show it again.
      setParams({}, { replace: true });
    }
  }, [welcome, setParams]);

  return (
    <>
      <PageHeader title="Settings" description={`Signed in as ${user.email} · member since ${formatDate(user.createdAt.slice(0, 10), { weekday: undefined, year: 'numeric' })}`} />

      {showWelcome && (
        <div className="mb-6 flex items-start gap-3 rounded-xl border border-emerald-200 bg-emerald-50 p-4">
          <Sparkles className="mt-0.5 h-5 w-5 shrink-0 text-emerald-600" aria-hidden />
          <div className="flex-1 text-sm text-emerald-900">
            <p className="font-semibold">Welcome to SmartStudy, {user.fullName.split(' ')[0]}!</p>
            <p className="mt-1">
              Start by telling us when you can study below. Then add your <Link to="/subjects" className="font-medium underline">subjects and topics</Link>,
              your <Link to="/exams" className="font-medium underline">exam dates</Link>, and generate your first plan in the{' '}
              <Link to="/planner" className="font-medium underline">Study Planner</Link>.
            </p>
          </div>
          <Button size="sm" variant="ghost" onClick={() => setShowWelcome(false)}>Dismiss</Button>
        </div>
      )}

      <div className="grid gap-6 xl:grid-cols-5">
        <div className="space-y-6 xl:col-span-3">
          <AvailabilitySection />
        </div>
        <div className="space-y-6 xl:col-span-2">
          <ProfileSection />
          <PasswordSection />
        </div>
      </div>
    </>
  );
}
