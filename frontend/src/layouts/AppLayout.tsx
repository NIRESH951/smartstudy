import { useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router';
import {
  AlertTriangle,
  BarChart3,
  BookOpen,
  CalendarDays,
  GraduationCap,
  LayoutDashboard,
  LogOut,
  Menu,
  RotateCcw,
  Settings,
  Timer,
  X,
  ClipboardList,
} from 'lucide-react';
import { useAuth, useUser } from '../context/AuthContext';
import { NotificationBell } from '../components/NotificationBell';

const NAV_ITEMS = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/planner', label: 'Study Planner', icon: CalendarDays, end: false },
  { to: '/subjects', label: 'My Subjects', icon: BookOpen, end: false },
  { to: '/exams', label: 'Examinations', icon: ClipboardList, end: false },
  { to: '/weak-topics', label: 'Weak Topics', icon: AlertTriangle, end: false },
  { to: '/revision', label: 'Revision', icon: RotateCcw, end: false },
  { to: '/focus', label: 'Focus Mode', icon: Timer, end: false },
  { to: '/analytics', label: 'Analytics', icon: BarChart3, end: false },
  { to: '/settings', label: 'Settings', icon: Settings, end: false },
];

function Sidebar({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <div className="flex h-full flex-col bg-navy-900 text-navy-100">
      <div className="flex items-center gap-3 px-5 py-5">
        <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-emerald-500 text-white">
          <GraduationCap className="h-5 w-5" aria-hidden />
        </div>
        <div>
          <p className="text-base font-semibold text-white">SmartStudy</p>
          <p className="text-xs text-navy-300">Adaptive study planner</p>
        </div>
      </div>
      <nav className="mt-2 flex-1 space-y-1 px-3" aria-label="Main navigation">
        {NAV_ITEMS.map(({ to, label, icon: Icon, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            onClick={onNavigate}
            className={({ isActive }) =>
              `flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors ${
                isActive ? 'bg-navy-700 text-white' : 'text-navy-200 hover:bg-navy-800 hover:text-white'
              }`
            }
          >
            {({ isActive }) => (
              <>
                <Icon className={`h-[18px] w-[18px] ${isActive ? 'text-emerald-400' : ''}`} aria-hidden />
                {label}
              </>
            )}
          </NavLink>
        ))}
      </nav>
      <div className="m-3 rounded-xl bg-navy-800 p-4 text-xs text-navy-200">
        <p className="font-semibold text-white">Tip</p>
        <p className="mt-1">Record quiz marks after every test - the planner uses them to find your weak topics.</p>
      </div>
    </div>
  );
}

/** Authenticated application shell: dark navy sidebar, top bar with notifications and the page outlet. */
export function AppLayout() {
  const user = useUser();
  const { logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [mobileOpen, setMobileOpen] = useState(false);

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  const initials = user.fullName
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('');

  return (
    <div className="min-h-screen">
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 lg:block">
        <Sidebar />
      </aside>

      {mobileOpen && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div className="absolute inset-0 bg-slate-900/50" onClick={() => setMobileOpen(false)} aria-hidden />
          <div className="absolute inset-y-0 left-0 w-64 shadow-xl">
            <button
              type="button"
              onClick={() => setMobileOpen(false)}
              className="absolute right-3 top-5 rounded-lg p-1 text-navy-200 hover:bg-navy-800"
              aria-label="Close menu"
            >
              <X className="h-5 w-5" aria-hidden />
            </button>
            <Sidebar onNavigate={() => setMobileOpen(false)} />
          </div>
        </div>
      )}

      <div className="lg:pl-64">
        <header className="sticky top-0 z-20 flex h-16 items-center gap-3 border-b border-slate-200 bg-white/90 px-4 backdrop-blur sm:px-6">
          <button
            type="button"
            onClick={() => setMobileOpen(true)}
            className="rounded-lg p-2 text-slate-600 hover:bg-slate-100 lg:hidden"
            aria-label="Open menu"
          >
            <Menu className="h-5 w-5" aria-hidden />
          </button>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm text-slate-500">
              Welcome back, <span className="font-medium text-slate-800">{user.fullName.split(' ')[0]}</span>
            </p>
          </div>
          <NotificationBell />
          <div className="hidden items-center gap-3 border-l border-slate-200 pl-3 sm:flex">
            <div className="flex h-9 w-9 items-center justify-center rounded-full bg-navy-900 text-xs font-semibold text-white">
              {initials || 'S'}
            </div>
            <div className="hidden text-right md:block">
              <p className="text-sm font-medium text-slate-800">{user.fullName}</p>
              <p className="text-xs text-slate-500">{user.timeZone}</p>
            </div>
          </div>
          <button
            type="button"
            onClick={handleLogout}
            className="inline-flex items-center gap-1.5 rounded-lg px-2.5 py-2 text-sm text-slate-600 hover:bg-slate-100 hover:text-slate-900"
          >
            <LogOut className="h-4 w-4" aria-hidden />
            <span className="hidden sm:inline">Log out</span>
          </button>
        </header>
        <main key={location.pathname} className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
