import { useEffect, useRef, useState } from 'react';
import type { FocusSession } from '../types/api';

/**
 * Display-only countdown for a server-timed focus session.
 *
 * The server sends `elapsedFocusSeconds` measured with its own clock. We remember the moment
 * that value arrived (monotonic `performance.now()`), and while the session is RUNNING add the
 * time passed since then. Changing the computer clock therefore cannot change the countdown, and
 * the stored duration is always recomputed by the server when the session is completed.
 */
export function useFocusTimer(session: FocusSession | null) {
  const syncRef = useRef<{ id: number; elapsed: number; at: number } | null>(null);
  const [, setTick] = useState(0);

  useEffect(() => {
    if (session) {
      syncRef.current = { id: session.id, elapsed: session.elapsedFocusSeconds, at: performance.now() };
    } else {
      syncRef.current = null;
    }
  }, [session]);

  const running = session?.status === 'RUNNING';
  useEffect(() => {
    if (!running) return;
    const timer = window.setInterval(() => setTick((value) => value + 1), 250);
    return () => window.clearInterval(timer);
  }, [running]);

  if (!session || !syncRef.current || syncRef.current.id !== session.id) {
    const elapsed = session?.elapsedFocusSeconds ?? 0;
    const total = (session?.plannedMinutes ?? 0) * 60;
    return { elapsedSeconds: elapsed, remainingSeconds: Math.max(0, total - elapsed), totalSeconds: total };
  }
  const sync = syncRef.current;
  const elapsed = running ? sync.elapsed + (performance.now() - sync.at) / 1000 : sync.elapsed;
  const total = session.plannedMinutes * 60;
  return { elapsedSeconds: elapsed, remainingSeconds: Math.max(0, total - elapsed), totalSeconds: total };
}

/** Simple local countdown used for breaks (breaks are not study time, so they are not stored). */
export function useCountdown(endsAt: number | null) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (endsAt === null) return;
    const timer = window.setInterval(() => setNow(Date.now()), 250);
    return () => window.clearInterval(timer);
  }, [endsAt]);
  return endsAt === null ? 0 : Math.max(0, (endsAt - now) / 1000);
}
