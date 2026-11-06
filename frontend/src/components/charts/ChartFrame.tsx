import { useState, type ReactNode } from 'react';
import { BarChart3, Table2 } from 'lucide-react';
import { Card, CardHeader } from '../ui/Card';

export interface LegendItem {
  label: string;
  color: string;
  /** "line" draws a short stroke, "dashed" a dashed stroke, otherwise a square swatch. */
  shape?: 'square' | 'line' | 'dashed';
}

export interface TableColumn<Row> {
  header: string;
  cell: (row: Row) => ReactNode;
  align?: 'left' | 'right';
}

interface ChartFrameProps<Row> {
  title: string;
  description?: string;
  legend?: LegendItem[];
  rows: Row[];
  columns: TableColumn<Row>[];
  children: ReactNode;
  height?: number;
  action?: ReactNode;
  empty?: ReactNode;
}

/**
 * Card wrapper used by every chart: title, legend (text in ink colours, swatch carries identity),
 * and a toggle to an accessible data table so no value is conveyed by colour or hover alone.
 */
export function ChartFrame<Row>({
  title,
  description,
  legend,
  rows,
  columns,
  children,
  height = 260,
  action,
  empty,
}: ChartFrameProps<Row>) {
  const [view, setView] = useState<'chart' | 'table'>('chart');
  return (
    <Card>
      <CardHeader
        title={title}
        description={description}
        action={
          <div className="flex items-center gap-2">
            {action}
            <button
              type="button"
              onClick={() => setView(view === 'chart' ? 'table' : 'chart')}
              className="inline-flex items-center gap-1 rounded-lg px-2 py-1 text-xs font-medium text-slate-500 hover:bg-slate-100 hover:text-slate-800"
              aria-pressed={view === 'table'}
            >
              {view === 'chart' ? <Table2 className="h-3.5 w-3.5" aria-hidden /> : <BarChart3 className="h-3.5 w-3.5" aria-hidden />}
              {view === 'chart' ? 'Table' : 'Chart'}
            </button>
          </div>
        }
      />
      <div className="px-5 pb-4 pt-3">
        {legend && legend.length > 0 && view === 'chart' && (
          <ul className="mb-3 flex flex-wrap gap-x-4 gap-y-1 text-xs text-slate-600" aria-label="Legend">
            {legend.map((item) => (
              <li key={item.label} className="inline-flex items-center gap-1.5">
                {item.shape === 'line' || item.shape === 'dashed' ? (
                  <svg width="16" height="8" aria-hidden>
                    <line
                      x1="1"
                      y1="4"
                      x2="15"
                      y2="4"
                      stroke={item.color}
                      strokeWidth="2"
                      strokeDasharray={item.shape === 'dashed' ? '4 3' : undefined}
                      strokeLinecap="round"
                    />
                  </svg>
                ) : (
                  <span className="h-2.5 w-2.5 rounded-sm" style={{ backgroundColor: item.color }} aria-hidden />
                )}
                {item.label}
              </li>
            ))}
          </ul>
        )}
        {rows.length === 0 && empty ? (
          empty
        ) : view === 'chart' ? (
          <div style={{ height }} className="w-full">
            {children}
          </div>
        ) : (
          <div className="max-h-80 overflow-auto rounded-lg border border-slate-100">
            <table className="w-full text-left text-sm">
              <thead className="sticky top-0 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  {columns.map((column) => (
                    <th key={column.header} className={`px-3 py-2 font-medium ${column.align === 'right' ? 'text-right' : ''}`}>
                      {column.header}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {rows.map((row, index) => (
                  <tr key={index}>
                    {columns.map((column) => (
                      <td
                        key={column.header}
                        className={`px-3 py-2 text-slate-700 ${column.align === 'right' ? 'text-right tabular-nums' : ''}`}
                      >
                        {column.cell(row)}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </Card>
  );
}
