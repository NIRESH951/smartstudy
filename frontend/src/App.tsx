import { lazy, Suspense, type ComponentType } from 'react';
import { createBrowserRouter, Navigate, Outlet, RouterProvider, useLocation } from 'react-router';
import { useAuth } from './context/AuthContext';
import { AppLayout } from './layouts/AppLayout';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { LoadingBlock, Spinner } from './components/ui/Feedback';

// Pages behind the login are split into separate chunks so the sign-in screen loads quickly and
// the charting library is only downloaded when it is needed.
function page<T>(loader: () => Promise<T>, name: keyof T & string) {
  const Component = lazy(async () => ({ default: (await loader())[name] as ComponentType }));
  return (
    <Suspense fallback={<LoadingBlock rows={6} className="mt-2" />}>
      <Component />
    </Suspense>
  );
}

function FullPageLoader() {
  return (
    <div className="flex min-h-screen items-center justify-center">
      <Spinner label="Loading SmartStudy" />
    </div>
  );
}

/** Pages that need a signed-in student. */
function RequireAuth() {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <FullPageLoader />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  return <Outlet />;
}

/** Login / register are only shown to visitors who are not signed in. */
function GuestOnly() {
  const { user, loading } = useAuth();
  if (loading) return <FullPageLoader />;
  if (user) return <Navigate to="/" replace />;
  return <Outlet />;
}

const router = createBrowserRouter([
  {
    element: <GuestOnly />,
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/register', element: <RegisterPage /> },
    ],
  },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { path: '/', element: page(() => import('./pages/DashboardPage'), 'DashboardPage') },
          { path: '/planner', element: page(() => import('./pages/PlannerPage'), 'PlannerPage') },
          { path: '/planner/calendar', element: page(() => import('./pages/CalendarPage'), 'CalendarPage') },
          { path: '/subjects', element: page(() => import('./pages/SubjectsPage'), 'SubjectsPage') },
          { path: '/subjects/:subjectId', element: page(() => import('./pages/SubjectDetailPage'), 'SubjectDetailPage') },
          { path: '/exams', element: page(() => import('./pages/ExamsPage'), 'ExamsPage') },
          { path: '/exams/readiness', element: page(() => import('./pages/ReadinessPage'), 'ReadinessPage') },
          { path: '/weak-topics', element: page(() => import('./pages/WeakTopicsPage'), 'WeakTopicsPage') },
          { path: '/revision', element: page(() => import('./pages/RevisionPage'), 'RevisionPage') },
          { path: '/focus', element: page(() => import('./pages/FocusPage'), 'FocusPage') },
          { path: '/analytics', element: page(() => import('./pages/AnalyticsPage'), 'AnalyticsPage') },
          { path: '/settings', element: page(() => import('./pages/SettingsPage'), 'SettingsPage') },
          { path: '*', element: page(() => import('./pages/NotFoundPage'), 'NotFoundPage') },
        ],
      },
    ],
  },
]);

export function App() {
  return <RouterProvider router={router} />;
}
