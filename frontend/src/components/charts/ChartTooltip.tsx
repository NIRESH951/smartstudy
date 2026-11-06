import type { ReactNode } from 'react';

export interface TooltipRow {
  label: string;
  value: ReactNode;
  color?: string;
  dashed?: boolean;
}

/** Tooltip body shared by all charts: title + rows with a colour key and an ink-coloured value. */
export function TooltipBox({ title, rows }: { title: ReactNode; rows: TooltipRow[] }) {
  return (
    <div className="min-w-40 rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs shadow-lg">
      <p className="mb-1.5 font-semibold text-slate-800">{title}</p>
      <ul className="space-y-1">
        {rows.map((row) => (
          <li key={row.label} className="flex items-center justify-between gap-4">
            <span className="inline-flex items-center gap-1.5 text-slate-500">
              {row.color && (
                <span
                  className="inline-block h-2 w-2 rounded-sm"
                  style={{ backgroundColor: row.dashed ? 'transparent' : row.color, border: row.dashed ? `1.5px dashed ${row.color}` : undefined }}
                  aria-hidden
                />
              )}
              {row.label}
            </span>
            <span className="font-medium tabular-nums text-slate-800">{row.value}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
