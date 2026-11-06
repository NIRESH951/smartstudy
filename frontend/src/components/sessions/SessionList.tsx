import { useState } from 'react';
import { useNavigate } from 'react-router';
import { CheckCircle2, Lock, LockOpen, Pencil, Play, Timer, Trash2, XCircle } from 'lucide-react';
import { Button, IconButton } from '../ui/Button';
import { ColorDot } from '../ui/Feedback';
import { ConfirmDialog } from '../ui/Modal';
import { LockedBadge, SessionStatusBadge, SessionTypeBadge } from '../StatusBadges';
import { CompleteSessionModal } from './CompleteSessionModal';
import { SessionFormModal } from './SessionFormModal';
import { sessionApi } from '../../services/api';
import { errorMessage } from '../../services/http';
import { useToast } from '../../context/ToastContext';
import { useRefreshAll } from '../../hooks/queries';
import { formatDate, formatTime } from '../../utils/format';
import type { SessionActionResponse, StudySession } from '../../types/api';

interface SessionListProps {
  sessions: StudySession[];
  showDate?: boolean;
  compact?: boolean;
}

/**
 * List of study sessions with every life-cycle action: start, complete, mark missed,
 * lock / unlock, move and remove. All actions call the API and refresh the affected views.
 */
export function SessionList({ sessions, showDate = false, compact = false }: SessionListProps) {
  const toast = useToast();
  const refreshAll = useRefreshAll();
  const navigate = useNavigate();
  const [busyId, setBusyId] = useState<number | null>(null);
  const [completing, setCompleting] = useState<StudySession | null>(null);
  const [editing, setEditing] = useState<StudySession | null>(null);
  const [missing, setMissing] = useState<StudySession | null>(null);
  const [removing, setRemoving] = useState<StudySession | null>(null);

  const run = async (session: StudySession, action: () => Promise<unknown>, success: string) => {
    setBusyId(session.id);
    try {
      await action();
      toast.success(success);
      await refreshAll();
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setBusyId(null);
    }
  };

  const afterAction = async (result: SessionActionResponse) => {
    toast.success(result.message);
    await refreshAll();
  };

  return (
    <>
      <ul className="divide-y divide-slate-100">
        {sessions.map((session) => {
          const busy = busyId === session.id;
          const planned = session.status === 'PLANNED';
          const active = session.status === 'IN_PROGRESS';
          return (
            <li key={session.id} className={`flex flex-col gap-3 py-3 sm:flex-row sm:items-center ${compact ? '' : 'sm:py-4'}`}>
              <div className="flex w-28 shrink-0 flex-row items-baseline gap-2 sm:flex-col sm:gap-0">
                {showDate && <span className="text-xs font-medium text-slate-500">{formatDate(session.localDate)}</span>}
                <span className="text-sm font-semibold tabular-nums text-slate-900">
                  {formatTime(session.localStartTime)}-{formatTime(session.localEndTime)}
                </span>
                <span className="text-xs text-slate-500">{session.plannedMinutes} min</span>
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <ColorDot color={session.subjectColor} />
                  <p className="truncate text-sm font-medium text-slate-900">{session.topicName ?? session.subjectName}</p>
                  <SessionTypeBadge type={session.sessionType} />
                  <SessionStatusBadge status={session.status} />
                  {session.locked && planned && <LockedBadge />}
                </div>
                <p className="mt-0.5 truncate text-xs text-slate-500">
                  {session.topicName ? session.subjectName : 'Whole subject'}
                  {session.actualMinutes !== null && ` · studied ${session.actualMinutes} min`}
                  {session.rescheduledFromId !== null && ' · rescheduled from a missed session'}
                </p>
                {!compact && session.reason && (
                  <p className="mt-1 text-xs text-slate-600">
                    <span className="font-medium text-slate-700">Why: </span>
                    {session.reason}
                  </p>
                )}
              </div>
              <div className="flex flex-wrap items-center gap-1 sm:justify-end">
                {planned && (
                  <Button size="sm" variant="secondary" icon={<Play className="h-3.5 w-3.5" aria-hidden />} loading={busy}
                    onClick={() => run(session, () => sessionApi.start(session.id), 'Session started. Good luck!')}>
                    Start
                  </Button>
                )}
                {(planned || active) && (
                  <Button size="sm" icon={<CheckCircle2 className="h-3.5 w-3.5" aria-hidden />} onClick={() => setCompleting(session)}>
                    Complete
                  </Button>
                )}
                {session.status === 'MISSED' && (
                  <Button size="sm" variant="secondary" onClick={() => setCompleting(session)}>
                    Log as done
                  </Button>
                )}
                {active && (
                  <IconButton label="Open focus timer" onClick={() => navigate(`/focus?studySessionId=${session.id}`)}>
                    <Timer className="h-4 w-4" aria-hidden />
                  </IconButton>
                )}
                {(planned || active) && (
                  <IconButton label="Mark as missed" tone="danger" onClick={() => setMissing(session)}>
                    <XCircle className="h-4 w-4" aria-hidden />
                  </IconButton>
                )}
                {planned && (
                  <>
                    <IconButton
                      label={session.locked ? 'Unlock (planner may move it)' : 'Lock (planner keeps it)'}
                      disabled={busy}
                      onClick={() =>
                        run(session, () => sessionApi.lock(session.id, !session.locked), session.locked ? 'Session unlocked.' : 'Session locked.')
                      }
                    >
                      {session.locked ? <Lock className="h-4 w-4" aria-hidden /> : <LockOpen className="h-4 w-4" aria-hidden />}
                    </IconButton>
                    <IconButton label="Move session" onClick={() => setEditing(session)}>
                      <Pencil className="h-4 w-4" aria-hidden />
                    </IconButton>
                    <IconButton label="Remove session" tone="danger" onClick={() => setRemoving(session)}>
                      <Trash2 className="h-4 w-4" aria-hidden />
                    </IconButton>
                  </>
                )}
              </div>
            </li>
          );
        })}
      </ul>

      <CompleteSessionModal
        session={completing}
        onClose={() => setCompleting(null)}
        onDone={async (result) => {
          setCompleting(null);
          await afterAction(result);
        }}
      />
      <SessionFormModal
        open={editing !== null}
        session={editing}
        defaultDate={editing?.localDate ?? ''}
        onClose={() => setEditing(null)}
        onSaved={async () => {
          setEditing(null);
          toast.success('Session moved and locked.');
          await refreshAll();
        }}
      />
      <ConfirmDialog
        open={missing !== null}
        title="Mark session as missed?"
        message="The session's work will be rescheduled automatically into your free time."
        confirmLabel="Mark as missed"
        loading={busyId !== null}
        onCancel={() => setMissing(null)}
        onConfirm={async () => {
          const session = missing;
          if (!session) return;
          setBusyId(session.id);
          try {
            const result = await sessionApi.miss(session.id);
            setMissing(null);
            await afterAction(result);
          } catch (error) {
            toast.error(errorMessage(error));
          } finally {
            setBusyId(null);
          }
        }}
      />
      <ConfirmDialog
        open={removing !== null}
        title="Remove this session?"
        message="The session is cancelled. Its topic will be planned again the next time you regenerate your plan."
        confirmLabel="Remove"
        loading={busyId !== null}
        onCancel={() => setRemoving(null)}
        onConfirm={async () => {
          const session = removing;
          if (!session) return;
          await run(session, () => sessionApi.cancel(session.id), 'Session removed.');
          setRemoving(null);
        }}
      />
    </>
  );
}
