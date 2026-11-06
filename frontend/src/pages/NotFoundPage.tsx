import { Link } from 'react-router';
import { Compass } from 'lucide-react';

export function NotFoundPage() {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center text-center">
      <div className="mb-4 rounded-full bg-slate-100 p-4 text-slate-400">
        <Compass className="h-8 w-8" aria-hidden />
      </div>
      <h1 className="text-2xl font-semibold text-slate-900">Page not found</h1>
      <p className="mt-2 max-w-md text-sm text-slate-500">The page you are looking for does not exist or has moved.</p>
      <Link
        to="/"
        className="mt-6 inline-flex h-10 items-center rounded-lg bg-emerald-600 px-4 text-sm font-medium text-white shadow-sm hover:bg-emerald-700"
      >
        Back to dashboard
      </Link>
    </div>
  );
}
