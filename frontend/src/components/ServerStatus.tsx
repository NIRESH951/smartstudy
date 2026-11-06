import { useQuery } from '@tanstack/react-query';
import { Database, Server } from 'lucide-react';
import { healthApi } from '../services/api';
import { errorMessage } from '../services/http';

/**
 * Shows the result of GET /api/v1/health: proves the frontend can reach the backend and the
 * backend can reach MySQL (with the Flyway schema version).
 */
export function ServerStatus() {
  const { data, error, isLoading } = useQuery({ queryKey: ['health'], queryFn: healthApi.check, retry: false });

  return (
    <div className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-500" aria-live="polite">
      <span className="inline-flex items-center gap-1.5">
        <Server className="h-3.5 w-3.5" aria-hidden />
        API:{' '}
        {isLoading ? (
          'checking...'
        ) : error ? (
          <span className="font-medium text-red-600">offline - {errorMessage(error)}</span>
        ) : (
          <span className="font-medium text-emerald-700">{data?.status === 'UP' ? 'online' : 'degraded'}</span>
        )}
      </span>
      {data && (
        <span className="inline-flex items-center gap-1.5">
          <Database className="h-3.5 w-3.5" aria-hidden />
          {data.database.product ?? 'Database'} {data.database.productVersion ?? ''}:{' '}
          <span className={`font-medium ${data.database.status === 'UP' ? 'text-emerald-700' : 'text-red-600'}`}>
            {data.database.status === 'UP' ? 'connected' : 'not reachable'}
          </span>
          {data.database.schemaVersion && <span>(schema v{data.database.schemaVersion})</span>}
        </span>
      )}
    </div>
  );
}
