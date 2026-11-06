import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ApiError } from './services/http';
import { AuthProvider } from './context/AuthContext';
import { ToastProvider } from './context/ToastContext';
import { App } from './App';
import './index.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      // Client errors (401, 403, 404, 422...) will not succeed on retry.
      retry: (failureCount, error) =>
        !(error instanceof ApiError && error.status >= 400 && error.status < 500) && failureCount < 1,
    },
  },
});

const root = document.getElementById('root');
if (!root) throw new Error('Root element #root not found');

function start(container: HTMLElement) {
  createRoot(container).render(
    <StrictMode>
      <QueryClientProvider client={queryClient}>
        <ToastProvider>
          <AuthProvider>
            <App />
          </AuthProvider>
        </ToastProvider>
      </QueryClientProvider>
    </StrictMode>,
  );
}

// Demo builds (`npm run build:demo`, used for the hosted preview) answer every API call in the
// browser with sample data. In normal builds this branch is removed and the demo code is not shipped.
if (import.meta.env.VITE_DEMO === 'true') {
  void import('./demo/install').then(() => start(root));
} else {
  start(root);
}
