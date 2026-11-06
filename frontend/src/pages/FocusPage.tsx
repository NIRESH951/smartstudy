import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router';
import { Coffee, Pause, Play, Square, Timer, X } from 'lucide-react';
import { useAllTopics, useFocusActive, useFocusHistory, useFocusStats, useRefreshAll, useSubjects } from '../hooks/queries';
import { useCountdown, useFocusTimer } from '../hooks/useFocusTimer';
import { useUser } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { focusApi } from '../services/api';
import { errorMessage } from '../services/http';
import { Card, CardBody, CardHeader, PageHeader, StatCard } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { ColorDot, EmptyState, ErrorState, LoadingBlock } from '../components/ui/Feedback';
import { SelectField, TextField } from '../components/ui/Form';
import { FocusDailyChart } from '../components/charts/StudyCharts';
import { formatClock, formatInstant, formatMinutes } from '../utils/format';
import type { FocusSession } from '../types/api';

interface BreakState {
  kind: 'SHORT' | 'LONG';
  endsAt: number;
  minutes: number;
}

function TimerFace({ seconds, total, label, tone }: { seconds: number; total: number; label: string; tone: 'focus' | 'break' | 'paused' }) {
  const size = 260;
  const stroke = 12;
  const radius = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radius;
  const progress = total > 0 ? 1 - seconds / total : 0;
  const color = tone === 'break' ? '#2a78d6' : tone === 'paused' ? '#94a3b8' : '#10b981';
  return (
    <div className="relative mx-auto" style={{ width: size, height: size }}>
      <svg width={size} height={size} className="-rotate-90" aria-hidden>
        <circle cx={size / 2} cy={size / 2} r={radius} fill="none" stroke="#e2e8f0" strokeWidth={stroke} />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke={color}
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={circumference * (1 - Math.min(1, Math.max(0, progress)))}
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center" role="timer" aria-live="off" aria-label={`${label}: ${formatClock(seconds)} remaining`}>
        <span className="text-5xl font-semibold tabular-nums text-slate-900">{formatClock(seconds)}</span>
        <span className="mt-1 text-sm font-medium text-slate-500">{label}</span>
      </div>
    </div>
  );
}

export function FocusPage() {
  const user = useUser();
  const toast = useToast();
  const refreshAll = useRefreshAll();
  const [params] = useSearchParams();
  const linkedSessionId = params.get('studySessionId');
  const active = useFocusActive();
  const stats = useFocusStats();
  const history = useFocusHistory();
  const { data: subjects } = useSubjects();
  const { data: topics } = useAllTopics();

  const [subjectId, setSubjectId] = useState('');
  const [topicId, setTopicId] = useState('');
  const [minutes, setMinutes] = useState(String(user.focusMinutes));
  const [working, setWorking] = useState(false);
  const [breakState, setBreakState] = useState<BreakState | null>(null);
  const completingRef = useRef(false);
  const autoCompletedIdRef = useRef<number | null>(null);

  const session: FocusSession | null = active.data ?? null;
  const { remainingSeconds, totalSeconds, elapsedSeconds } = useFocusTimer(session);
  const breakRemaining = useCountdown(breakState?.endsAt ?? null);

  const subjectTopics = useMemo(() => (topics ?? []).filter((topic) => String(topic.subjectId) === subjectId), [topics, subjectId]);

  const complete = useCallback(
    async (current: FocusSession, clientSeconds: number | null, automatic: boolean) => {
      if (completingRef.current) return;
      completingRef.current = true;
      setWorking(true);
      try {
        const result = await focusApi.complete(current.id, clientSeconds);
        const focused = Math.floor((result.session.actualFocusSeconds ?? 0) / 60);
        toast.success(
          `${automatic ? 'Pomodoro finished' : 'Focus session saved'}: ${focused} min recorded. Time for a ${result.nextBreak === 'LONG' ? 'long' : 'short'} break.`,
        );
        setBreakState({ kind: result.nextBreak, minutes: result.breakMinutes, endsAt: Date.now() + result.breakMinutes * 60_000 });
        await refreshAll();
      } catch (error) {
        toast.error(errorMessage(error));
        await refreshAll();
      } finally {
        completingRef.current = false;
        setWorking(false);
      }
    },
    [refreshAll, toast],
  );

  // Finish automatically when the countdown reaches zero.
  useEffect(() => {
    if (session && session.status === 'RUNNING' && totalSeconds > 0 && remainingSeconds <= 0 && autoCompletedIdRef.current !== session.id) {
      // Only one automatic attempt per session; a failure leaves the manual "Finish now" button available.
      autoCompletedIdRef.current = session.id;
      void complete(session, Math.round(elapsedSeconds), true);
    }
  }, [session, remainingSeconds, totalSeconds, elapsedSeconds, complete]);

  // Announce the end of a break.
  useEffect(() => {
    if (breakState && breakRemaining <= 0) {
      toast.info('Break is over - ready for the next focus session?');
      setBreakState(null);
    }
  }, [breakState, breakRemaining, toast]);

  // Show the countdown in the browser tab.
  useEffect(() => {
    const original = 'SmartStudy';
    if (session && session.status !== 'COMPLETED') {
      document.title = `${formatClock(remainingSeconds)} · Focus · SmartStudy`;
    } else if (breakState) {
      document.title = `${formatClock(breakRemaining)} · Break · SmartStudy`;
    } else {
      document.title = original;
    }
    return () => {
      document.title = original;
    };
  }, [session, remainingSeconds, breakState, breakRemaining]);

  const start = async () => {
    const planned = Number(minutes);
    if (!Number.isInteger(planned) || planned < 1 || planned > 120) {
      toast.error('Focus length must be between 1 and 120 minutes.');
      return;
    }
    setWorking(true);
    try {
      await focusApi.start({
        subjectId: subjectId ? Number(subjectId) : null,
        topicId: topicId ? Number(topicId) : null,
        studySessionId: linkedSessionId ? Number(linkedSessionId) : null,
        plannedMinutes: planned,
      });
      setBreakState(null);
      await refreshAll();
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setWorking(false);
    }
  };

  const act = async (action: () => Promise<unknown>, message?: string) => {
    setWorking(true);
    try {
      await action();
      if (message) toast.info(message);
      await refreshAll();
    } catch (error) {
      toast.error(errorMessage(error));
      await refreshAll();
    } finally {
      setWorking(false);
    }
  };

  const cycleTarget = stats.data?.longBreakInterval ?? user.longBreakInterval;
  const inCycle = stats.data?.completedInCurrentCycle ?? 0;

  return (
    <>
      <PageHeader title="Focus Mode" description="Pomodoro timer. Start, pause and finish are timed by the server, so recorded focus time is always accurate." />

      <div className="grid gap-6 xl:grid-cols-3">
        <Card className="xl:col-span-2">
          <CardBody className="py-8">
            {active.isLoading && <LoadingBlock rows={4} />}
            {active.error && <ErrorState error={active.error} onRetry={() => active.refetch()} />}

            {!active.isLoading && !active.error && (
              <div className="flex flex-col items-center">
                {session ? (
                  <TimerFace
                    seconds={remainingSeconds}
                    total={totalSeconds}
                    label={session.status === 'PAUSED' ? 'Paused' : 'Focus'}
                    tone={session.status === 'PAUSED' ? 'paused' : 'focus'}
                  />
                ) : breakState ? (
                  <TimerFace seconds={breakRemaining} total={breakState.minutes * 60} label={breakState.kind === 'LONG' ? 'Long break' : 'Short break'} tone="break" />
                ) : (
                  <TimerFace seconds={Number(minutes) * 60 || 0} total={Number(minutes) * 60 || 1} label="Ready" tone="paused" />
                )}

                <div className="mt-4 flex items-center gap-2" aria-label={`${inCycle} of ${cycleTarget} focus sessions in this cycle`}>
                  {Array.from({ length: cycleTarget }).map((_, index) => (
                    <span key={index} className={`h-2.5 w-2.5 rounded-full ${index < inCycle ? 'bg-emerald-500' : 'bg-slate-200'}`} />
                  ))}
                  <span className="ml-2 text-xs text-slate-500">long break after {cycleTarget}</span>
                </div>

                {session && (
                  <p className="mt-3 flex items-center gap-1.5 text-sm text-slate-600">
                    {session.subjectColor && <ColorDot color={session.subjectColor} />}
                    {session.topicName ?? session.subjectName ?? 'General focus'} · started {formatInstant(session.startedAt, user.timeZone, false)}
                    {session.pauseCount > 0 && ` · ${session.pauseCount} pause${session.pauseCount === 1 ? '' : 's'}`}
                  </p>
                )}

                <div className="mt-6 flex flex-wrap justify-center gap-2">
                  {session?.status === 'RUNNING' && (
                    <>
                      <Button variant="secondary" icon={<Pause className="h-4 w-4" aria-hidden />} loading={working} onClick={() => act(() => focusApi.pause(session.id))}>
                        Pause
                      </Button>
                      <Button icon={<Square className="h-4 w-4" aria-hidden />} disabled={working} onClick={() => complete(session, Math.round(elapsedSeconds), false)}>
                        Finish now
                      </Button>
                    </>
                  )}
                  {session?.status === 'PAUSED' && (
                    <>
                      <Button icon={<Play className="h-4 w-4" aria-hidden />} loading={working} onClick={() => act(() => focusApi.resume(session.id))}>
                        Resume
                      </Button>
                      <Button variant="secondary" icon={<Square className="h-4 w-4" aria-hidden />} disabled={working} onClick={() => complete(session, Math.round(elapsedSeconds), false)}>
                        Finish now
                      </Button>
                    </>
                  )}
                  {session && (
                    <Button variant="ghost" icon={<X className="h-4 w-4" aria-hidden />} disabled={working} onClick={() => act(() => focusApi.cancel(session.id), 'Focus session cancelled - it does not count as study time.')}>
                      Cancel
                    </Button>
                  )}
                  {!session && breakState && (
                    <Button variant="secondary" icon={<Coffee className="h-4 w-4" aria-hidden />} onClick={() => setBreakState(null)}>
                      Skip break
                    </Button>
                  )}
                </div>

                {!session && (
                  <div className="mt-8 w-full max-w-xl">
                    {linkedSessionId && (
                      <p className="mb-3 rounded-lg bg-blue-50 px-3 py-2 text-xs text-blue-800">
                        This focus block will be linked to your current study session; its time is used when you complete that session.
                      </p>
                    )}
                    <div className="grid gap-3 sm:grid-cols-3">
                      <SelectField label="Subject" value={subjectId} onChange={(e) => { setSubjectId(e.target.value); setTopicId(''); }}>
                        <option value="">Any</option>
                        {(subjects ?? []).filter((s) => !s.archived).map((subject) => (
                          <option key={subject.id} value={subject.id}>{subject.name}</option>
                        ))}
                      </SelectField>
                      <SelectField label="Topic" value={topicId} onChange={(e) => setTopicId(e.target.value)} disabled={!subjectId}>
                        <option value="">Any</option>
                        {subjectTopics.map((topic) => (
                          <option key={topic.id} value={topic.id}>{topic.name}</option>
                        ))}
                      </SelectField>
                      <TextField label="Minutes" type="number" min={1} max={120} value={minutes} onChange={(e) => setMinutes(e.target.value)} />
                    </div>
                    <Button className="mt-4 w-full" icon={<Play className="h-4 w-4" aria-hidden />} loading={working} onClick={start}>
                      Start focus session
                    </Button>
                  </div>
                )}
              </div>
            )}
          </CardBody>
        </Card>

        <div className="space-y-4">
          <StatCard label="Focus today" value={formatMinutes(stats.data?.todayFocusMinutes ?? 0)} hint={`${stats.data?.todayCompletedSessions ?? 0} pomodoros`} icon={<Timer className="h-5 w-5" aria-hidden />} />
          <StatCard label="This week" value={formatMinutes(stats.data?.weekFocusMinutes ?? 0)} accent="blue" />
          <StatCard
            label="Average session"
            value={`${Math.round(stats.data?.averageSessionMinutes ?? 0)} min`}
            hint={`${stats.data?.last30DaysSessions ?? 0} sessions in 30 days`}
            accent="amber"
          />
          <Card>
            <CardHeader title="Your Pomodoro settings" />
            <CardBody className="text-sm text-slate-600">
              Focus {user.focusMinutes} min · short break {user.shortBreakMinutes} min · long break {user.longBreakMinutes} min after every {user.longBreakInterval} sessions.
              <p className="mt-1 text-xs text-slate-500">Change them in Settings.</p>
            </CardBody>
          </Card>
        </div>
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-2">
        {stats.data && <FocusDailyChart data={stats.data.daily} />}
        <Card>
          <CardHeader title="Focus history" description="Last 14 days" />
          <CardBody>
            {history.isLoading && <LoadingBlock />}
            {history.data && history.data.length === 0 && <EmptyState compact title="No focus sessions yet" />}
            <ul className="max-h-72 divide-y divide-slate-100 overflow-y-auto scrollbar-thin">
              {(history.data ?? []).map((item) => (
                <li key={item.id} className="flex items-center justify-between gap-3 py-2 text-sm">
                  <div className="min-w-0">
                    <p className="flex items-center gap-1.5 truncate font-medium text-slate-800">
                      {item.subjectColor && <ColorDot color={item.subjectColor} />}
                      {item.topicName ?? item.subjectName ?? 'General focus'}
                    </p>
                    <p className="text-xs text-slate-500">{formatInstant(item.startedAt, user.timeZone)}</p>
                  </div>
                  <span className={`shrink-0 text-xs font-medium ${item.status === 'COMPLETED' ? 'text-emerald-700' : item.status === 'CANCELLED' ? 'text-slate-400' : 'text-blue-700'}`}>
                    {item.status === 'COMPLETED'
                      ? `${Math.floor((item.actualFocusSeconds ?? 0) / 60)} min`
                      : item.status === 'CANCELLED'
                        ? 'Cancelled'
                        : 'In progress'}
                  </span>
                </li>
              ))}
            </ul>
          </CardBody>
        </Card>
      </div>
    </>
  );
}
