import type { ReactNode } from 'react';
import { BrainCircuit, CalendarCheck2, GraduationCap, LineChart } from 'lucide-react';

const FEATURES = [
  { icon: CalendarCheck2, title: 'Adaptive timetable', text: 'Plans around your free time and re-plans when you miss a session.' },
  { icon: BrainCircuit, title: 'Weak-topic detection', text: 'Turns quiz marks into mastery scores and focuses your time.' },
  { icon: LineChart, title: 'Exam readiness', text: 'Tracks syllabus, mastery, mock tests and revision in one score.' },
];

/** Split-screen layout for the login and registration pages. */
export function AuthLayout({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      <div className="hidden flex-col justify-between bg-navy-900 p-12 text-white lg:flex">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-500">
            <GraduationCap className="h-6 w-6" aria-hidden />
          </div>
          <span className="text-lg font-semibold">SmartStudy</span>
        </div>
        <div>
          <h2 className="max-w-md text-3xl font-semibold leading-tight">
            Study smarter with a plan that adapts to how you are actually doing.
          </h2>
          <ul className="mt-10 space-y-6">
            {FEATURES.map(({ icon: Icon, title: featureTitle, text }) => (
              <li key={featureTitle} className="flex gap-4">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-navy-700 text-emerald-400">
                  <Icon className="h-5 w-5" aria-hidden />
                </div>
                <div>
                  <p className="font-medium">{featureTitle}</p>
                  <p className="text-sm text-navy-200">{text}</p>
                </div>
              </li>
            ))}
          </ul>
        </div>
        <p className="text-xs text-navy-300">Student Study Planner and Progress Tracking System</p>
      </div>
      <div className="flex items-center justify-center px-4 py-12 sm:px-8">
        <div className="w-full max-w-md">
          <div className="mb-8 flex items-center gap-3 lg:hidden">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-navy-900 text-emerald-400">
              <GraduationCap className="h-6 w-6" aria-hidden />
            </div>
            <span className="text-lg font-semibold text-slate-900">SmartStudy</span>
          </div>
          <h1 className="text-2xl font-semibold tracking-tight text-slate-900">{title}</h1>
          <p className="mt-1 text-sm text-slate-500">{subtitle}</p>
          <div className="mt-8">{children}</div>
        </div>
      </div>
    </div>
  );
}
