import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { Bell, CalendarClock, CheckCheck, RefreshCcw, RotateCcw, XCircle } from 'lucide-react';
import { useNotifications, useRefreshAll } from '../hooks/queries';
import { notificationApi } from '../services/api';
import { errorMessage } from '../services/http';
import { useToast } from '../context/ToastContext';
import { timeAgo } from '../utils/format';
import type { AppNotification } from '../types/api';
import { Spinner } from './ui/Feedback';

function NotificationIcon({ type }: { type: AppNotification['type'] }) {
  const className = 'h-4 w-4';
  switch (type) {
    case 'REVISION_DUE':
      return <RotateCcw className={`${className} text-violet-600`} aria-hidden />;
    case 'SESSION_MISSED':
      return <XCircle className={`${className} text-red-600`} aria-hidden />;
    case 'SESSION_RESCHEDULED':
      return <RefreshCcw className={`${className} text-blue-600`} aria-hidden />;
    case 'EXAM_UPCOMING':
      return <CalendarClock className={`${className} text-amber-600`} aria-hidden />;
    default:
      return <Bell className={`${className} text-slate-500`} aria-hidden />;
  }
}

/** Bell with unread counter and a dropdown list of the latest notifications. */
export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const { data, isLoading } = useNotifications();
  const refreshAll = useRefreshAll();
  const toast = useToast();
  const navigate = useNavigate();
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onClick = (event: MouseEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) setOpen(false);
    };
    const onKey = (event: KeyboardEvent) => event.key === 'Escape' && setOpen(false);
    document.addEventListener('mousedown', onClick);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onClick);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const unread = data?.unreadCount ?? 0;

  const openNotification = async (notification: AppNotification) => {
    try {
      if (!notification.read) {
        await notificationApi.markRead(notification.id);
        await refreshAll();
      }
      setOpen(false);
      if (notification.link) navigate(notification.link);
    } catch (error) {
      toast.error(errorMessage(error));
    }
  };

  const markAll = async () => {
    try {
      await notificationApi.markAllRead();
      await refreshAll();
    } catch (error) {
      toast.error(errorMessage(error));
    }
  };

  return (
    <div className="relative" ref={containerRef}>
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        className="relative rounded-lg p-2 text-slate-500 hover:bg-slate-100 hover:text-slate-800"
        aria-label={unread > 0 ? `Notifications, ${unread} unread` : 'Notifications'}
        aria-expanded={open}
        aria-haspopup="true"
      >
        <Bell className="h-5 w-5" aria-hidden />
        {unread > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-red-500 px-1 text-[10px] font-semibold text-white">
            {unread > 9 ? '9+' : unread}
          </span>
        )}
      </button>
      {open && (
        <div className="absolute right-0 z-40 mt-2 w-[22rem] max-w-[calc(100vw-2rem)] rounded-xl border border-slate-200 bg-white shadow-xl">
          <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
            <p className="text-sm font-semibold text-slate-900">Notifications</p>
            <button
              type="button"
              onClick={markAll}
              disabled={unread === 0}
              className="inline-flex items-center gap-1 text-xs font-medium text-emerald-700 hover:text-emerald-800 disabled:text-slate-300"
            >
              <CheckCheck className="h-3.5 w-3.5" aria-hidden />
              Mark all read
            </button>
          </div>
          <div className="max-h-96 overflow-y-auto scrollbar-thin">
            {isLoading && (
              <div className="p-4">
                <Spinner />
              </div>
            )}
            {!isLoading && (data?.items.length ?? 0) === 0 && (
              <p className="px-4 py-8 text-center text-sm text-slate-500">You're all caught up.</p>
            )}
            {data?.items.map((notification) => (
              <button
                type="button"
                key={notification.id}
                onClick={() => openNotification(notification)}
                className={`flex w-full gap-3 border-b border-slate-50 px-4 py-3 text-left hover:bg-slate-50 ${
                  notification.read ? '' : 'bg-emerald-50/40'
                }`}
              >
                <div className="mt-0.5">
                  <NotificationIcon type={notification.type} />
                </div>
                <div className="min-w-0 flex-1">
                  <p className={`text-sm ${notification.read ? 'text-slate-700' : 'font-semibold text-slate-900'}`}>
                    {notification.title}
                  </p>
                  <p className="mt-0.5 line-clamp-3 text-xs text-slate-500">{notification.message}</p>
                  <p className="mt-1 text-[11px] text-slate-400">{timeAgo(notification.createdAt)}</p>
                </div>
                {!notification.read && <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-emerald-500" aria-label="Unread" />}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
