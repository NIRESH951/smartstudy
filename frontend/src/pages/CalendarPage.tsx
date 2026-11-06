import { useMemo, useState } from 'react';
import { Link } from 'react-router';
import { ChevronLeft, ChevronRight, Plus } from 'lucide-react';
import { useCalendar, useRefreshAll } from '../hooks/queries';
import { useUser } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { Card, CardBody, CardHeader, PageHeader } from '../components/ui/Card';
import { Button, IconButton } from '../components/ui/Button';
import { EmptyState, ErrorState, LoadingBlock, Tabs } from '../components/ui/Feedback';
import { SessionList } from '../components/sessions/SessionList';
import { SessionFormModal } from '../components/sessions/SessionFormModal';
import { addDays, formatDate, formatMinutes, formatTime, todayIn, weekStart } from '../utils/format';
import type { DayPlan, StudySession } from '../types/api';

type View = 'day' | 'week' | 'month';

const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

function monthStart(date: string) {
  return `${date.slice(0, 8)}01`;
}

function monthEnd(date: string) {
  const [y, m] = date.split('-').map(Number);
  const last = new Date(Date.UTC(y, m, 0)).getUTCDate();
  return `${date.slice(0, 8)}${String(last).padStart(2, '0')}`;
}

function shiftMonth(date: string, delta: number) {
  const [y, m] = date.split('-').map(Number);
  const target = new Date(Date.UTC(y, m - 1 + delta, 1));
  return target.toISOString().slice(0, 10);
}

function statusStyle(session: StudySession) {
  switch (session.status) {
    case 'COMPLETED':
      return 'bg-emerald-50 text-emerald-900';
    case 'INCOMPLETE':
      return 'bg-amber-50 text-amber-900';
    case 'MISSED':
      return 'bg-red-50 text-red-900 line-through decoration-red-400';
    case 'IN_PROGRESS':
      return 'bg-blue-50 text-blue-900';
    default:
      return 'bg-white text-slate-800';
  }
}

function SessionChip({ session }: { session: StudySession }) {
  return (
    <div
      className={`rounded-md border border-slate-200 border-l-4 px-2 py-1 text-xs ${statusStyle(session)}`}
      style={{ borderLeftColor: session.subjectColor }}
      title={`${session.topicName ?? session.subjectName} · ${session.status.toLowerCase()}`}
    >
      <p className="font-semibold tabular-nums">{formatTime(session.localStartTime)}</p>
      <p className="truncate">{session.topicName ?? session.subjectName}</p>
    </div>
  );
}

export function CalendarPage() {
  const user = useUser();
  const toast = useToast();
  const refreshAll = useRefreshAll();
  const today = todayIn(user.timeZone);
  const [view, setView] = useState<View>('week');
  const [anchor, setAnchor] = useState(today);
  const [selected, setSelected] = useState(today);
  const [addOpen, setAddOpen] = useState(false);

  const [from, to] = useMemo<[string, string]>(() => {
    if (view === 'day') return [anchor, anchor];
    if (view === 'week') {
      const start = weekStart(anchor);
      return [start, addDays(start, 6)];
    }
    return [weekStart(monthStart(anchor)), addDays(weekStart(monthEnd(anchor)), 6)];
  }, [view, anchor]);

  const { data, isLoading, error, refetch } = useCalendar(from, to);
  const byDate = useMemo(() => new Map<string, DayPlan>((data?.days ?? []).map((day) => [day.date, day])), [data]);

  const move = (direction: number) => {
    if (view === 'day') {
      const next = addDays(anchor, direction);
      setAnchor(next);
      setSelected(next);
    } else if (view === 'week') {
      setAnchor(addDays(anchor, 7 * direction));
    } else {
      setAnchor(shiftMonth(anchor, direction));
    }
  };

  const title =
    view === 'day'
      ? formatDate(anchor, { weekday: 'long', year: 'numeric', month: 'long' })
      : view === 'week'
        ? `${formatDate(from)} - ${formatDate(to, { year: 'numeric' })}`
        : new Intl.DateTimeFormat('en-IN', { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(new Date(`${anchor}T00:00:00Z`));

  const selectedDay = byDate.get(view === 'day' ? anchor : selected);

  return (
    <>
      <PageHeader
        title="Study Planner"
        description="Daily, weekly and monthly view of your study sessions."
        actions={
          <Button variant="secondary" icon={<Plus className="h-4 w-4" aria-hidden />} onClick={() => setAddOpen(true)}>
            Add session
          </Button>
        }
      />
      <div className="mb-4 inline-flex rounded-lg bg-slate-100 p-1" role="tablist" aria-label="Planner views">
        <Link role="tab" aria-selected="false" to="/planner" className="rounded-md px-3 py-1.5 text-sm font-medium text-slate-500 hover:text-slate-800">
          Plan
        </Link>
        <span role="tab" aria-selected="true" className="rounded-md bg-white px-3 py-1.5 text-sm font-medium text-slate-900 shadow-sm">
          Calendar
        </span>
      </div>

      <Card>
        <div className="flex flex-col gap-3 border-b border-slate-100 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-2">
            <IconButton label="Previous" onClick={() => move(-1)}>
              <ChevronLeft className="h-4 w-4" aria-hidden />
            </IconButton>
            <IconButton label="Next" onClick={() => move(1)}>
              <ChevronRight className="h-4 w-4" aria-hidden />
            </IconButton>
            <Button size="sm" variant="secondary" onClick={() => { setAnchor(today); setSelected(today); }}>
              Today
            </Button>
            <h2 className="ml-2 text-sm font-semibold text-slate-900">{title}</h2>
          </div>
          <div className="flex items-center gap-3">
            {data && (
              <span className="text-xs text-slate-500">
                {formatMinutes(data.totalCompletedMinutes)} done / {formatMinutes(data.totalPlannedMinutes)} planned
              </span>
            )}
            <Tabs<View>
              label="Calendar range"
              value={view}
              onChange={setView}
              tabs={[
                { id: 'day', label: 'Day' },
                { id: 'week', label: 'Week' },
                { id: 'month', label: 'Month' },
              ]}
            />
          </div>
        </div>
        <CardBody>
          {isLoading && <LoadingBlock rows={5} />}
          {error && <ErrorState error={error} onRetry={() => refetch()} />}

          {data && view === 'week' && (
            <div className="grid grid-cols-1 gap-2 md:grid-cols-7">
              {data.days.map((day, index) => (
                <button
                  key={day.date}
                  type="button"
                  onClick={() => setSelected(day.date)}
                  className={`flex min-h-36 flex-col rounded-xl border p-2 text-left transition-colors ${
                    selected === day.date ? 'border-emerald-400 ring-2 ring-emerald-100' : 'border-slate-200 hover:border-slate-300'
                  } ${day.date === today ? 'bg-emerald-50/40' : 'bg-white'}`}
                  aria-pressed={selected === day.date}
                >
                  <div className="mb-2 flex items-baseline justify-between">
                    <span className="text-xs font-medium uppercase text-slate-500">{WEEKDAYS[index]}</span>
                    <span className={`text-sm font-semibold ${day.date === today ? 'text-emerald-700' : 'text-slate-900'}`}>
                      {Number(day.date.slice(8))}
                    </span>
                  </div>
                  <div className="flex-1 space-y-1">
                    {day.sessions.slice(0, 4).map((session) => (
                      <SessionChip key={session.id} session={session} />
                    ))}
                    {day.sessions.length > 4 && <p className="text-xs text-slate-500">+{day.sessions.length - 4} more</p>}
                  </div>
                  {day.plannedMinutes > 0 && <p className="mt-2 text-[11px] text-slate-500">{formatMinutes(day.plannedMinutes)}</p>}
                </button>
              ))}
            </div>
          )}

          {data && view === 'month' && (
            <div>
              <div className="mb-1 grid grid-cols-7 gap-1 text-center text-xs font-medium uppercase text-slate-500">
                {WEEKDAYS.map((day) => (
                  <span key={day}>{day}</span>
                ))}
              </div>
              <div className="grid grid-cols-7 gap-1">
                {data.days.map((day) => {
                  const inMonth = day.date.slice(0, 7) === anchor.slice(0, 7);
                  const done = day.sessions.filter((s) => s.status === 'COMPLETED' || s.status === 'INCOMPLETE').length;
                  const missed = day.sessions.filter((s) => s.status === 'MISSED').length;
                  return (
                    <button
                      key={day.date}
                      type="button"
                      onClick={() => setSelected(day.date)}
                      aria-pressed={selected === day.date}
                      aria-label={`${formatDate(day.date)}: ${day.sessions.length} sessions`}
                      className={`flex h-20 flex-col rounded-lg border p-1.5 text-left text-xs sm:h-24 ${
                        selected === day.date ? 'border-emerald-400 ring-2 ring-emerald-100' : 'border-slate-200'
                      } ${inMonth ? 'bg-white' : 'bg-slate-50 text-slate-400'}`}
                    >
                      <span className={`font-semibold ${day.date === today ? 'text-emerald-700' : ''}`}>{Number(day.date.slice(8))}</span>
                      {day.sessions.length > 0 && (
                        <span className="mt-auto space-y-0.5">
                          <span className="block text-slate-600">{day.sessions.length} session{day.sessions.length === 1 ? '' : 's'}</span>
                          <span className="hidden text-slate-500 sm:block">{formatMinutes(day.plannedMinutes)}</span>
                          {(done > 0 || missed > 0) && (
                            <span className="block">
                              {done > 0 && <span className="text-emerald-700">✓{done} </span>}
                              {missed > 0 && <span className="text-red-600">✗{missed}</span>}
                            </span>
                          )}
                        </span>
                      )}
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          {data && view === 'day' && (
            selectedDay && selectedDay.sessions.length > 0 ? (
              <SessionList sessions={selectedDay.sessions} />
            ) : (
              <EmptyState title="No sessions on this day" action={<Button size="sm" variant="secondary" onClick={() => setAddOpen(true)}>Add a session</Button>} />
            )
          )}
        </CardBody>
      </Card>

      {data && view !== 'day' && (
        <Card className="mt-6">
          <CardHeader
            title={formatDate(selected, { weekday: 'long', year: 'numeric', month: 'long' })}
            description={selectedDay ? `${formatMinutes(selectedDay.completedMinutes)} studied of ${formatMinutes(selectedDay.plannedMinutes)} planned` : 'Outside the visible range'}
          />
          <CardBody>
            {selectedDay && selectedDay.sessions.length > 0 ? (
              <SessionList sessions={selectedDay.sessions} />
            ) : (
              <EmptyState compact title="No sessions on this day" />
            )}
          </CardBody>
        </Card>
      )}

      <SessionFormModal
        open={addOpen}
        defaultDate={view === 'day' ? anchor : selected}
        onClose={() => setAddOpen(false)}
        onSaved={async () => {
          setAddOpen(false);
          toast.success('Session added.');
          await refreshAll();
        }}
      />
    </>
  );
}
