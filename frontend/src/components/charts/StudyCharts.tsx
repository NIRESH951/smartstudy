import {
  Bar,
  BarChart,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { ChartFrame } from './ChartFrame';
import { TooltipBox } from './ChartTooltip';
import { AXIS_PROPS, CHART_COLORS, MASTERY_COLORS, SERIES_ORDER, STATUS_COLORS } from './chartTheme';
import { EmptyState } from '../ui/Feedback';
import { formatDate, formatMinutes, MASTERY_LABELS, toHours } from '../../utils/format';
import type { DailyPoint, MasteryLevel, TopicHistory } from '../../types/api';

/** Hour-axis labels: at most one decimal so long values are not clipped (2.25 -> "2.3 h"). */
const hoursTick = (value: number) => `${Number.isInteger(value) ? value : value.toFixed(1)} h`;

const MAX_LINE_SERIES = 8;

// ------------------------------------------------------------------ planned vs actual (bars)

export function StudyTimeChart({
  data,
  title = 'Planned vs actual study time',
  description,
  height = 260,
}: {
  data: DailyPoint[];
  title?: string;
  description?: string;
  height?: number;
}) {
  const rows = data.map((d) => ({ ...d, plannedHours: toHours(d.plannedMinutes), actualHours: toHours(d.actualMinutes) }));
  return (
    <ChartFrame
      title={title}
      description={description}
      height={height}
      legend={[
        { label: 'Actual', color: CHART_COLORS.actual },
        { label: 'Planned', color: CHART_COLORS.planned },
      ]}
      rows={rows}
      columns={[
        { header: 'Day', cell: (r) => formatDate(r.date) },
        { header: 'Planned', cell: (r) => formatMinutes(r.plannedMinutes), align: 'right' },
        { header: 'Actual', cell: (r) => formatMinutes(r.actualMinutes), align: 'right' },
        { header: 'Focus', cell: (r) => formatMinutes(r.focusMinutes), align: 'right' },
      ]}
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={rows} barGap={2} barCategoryGap="28%" margin={{ top: 4, right: 4, left: -16, bottom: 0 }}>
          <CartesianGrid vertical={false} stroke={CHART_COLORS.grid} />
          <XAxis dataKey="label" {...AXIS_PROPS} />
          <YAxis {...AXIS_PROPS} axisLine={false} allowDecimals tickFormatter={hoursTick} width={64} />
          <Tooltip
            cursor={{ fill: 'rgba(15, 27, 51, 0.04)' }}
            content={({ active, payload }) => {
              if (!active || !payload?.length) return null;
              const row = payload[0].payload as (typeof rows)[number];
              return (
                <TooltipBox
                  title={formatDate(row.date, { weekday: 'long' })}
                  rows={[
                    { label: 'Actual', value: formatMinutes(row.actualMinutes), color: CHART_COLORS.actual },
                    { label: 'Planned', value: formatMinutes(row.plannedMinutes), color: CHART_COLORS.planned },
                    { label: 'Focus mode', value: formatMinutes(row.focusMinutes) },
                  ]}
                />
              );
            }}
          />
          <Bar dataKey="actualHours" name="Actual" fill={CHART_COLORS.actual} radius={[4, 4, 0, 0]} maxBarSize={28} />
          <Bar dataKey="plannedHours" name="Planned" fill={CHART_COLORS.planned} radius={[4, 4, 0, 0]} maxBarSize={28} />
        </BarChart>
      </ResponsiveContainer>
    </ChartFrame>
  );
}

// ------------------------------------------------------------------ daily trend (lines)

export function DailyTrendChart({ data, title, description }: { data: DailyPoint[]; title: string; description?: string }) {
  const rows = data.map((d) => ({ ...d, actualHours: toHours(d.actualMinutes), plannedHours: toHours(d.plannedMinutes) }));
  const tickEvery = Math.max(1, Math.ceil(rows.length / 8));
  return (
    <ChartFrame
      title={title}
      description={description}
      legend={[
        { label: 'Actual study', color: CHART_COLORS.actual, shape: 'line' },
        { label: 'Planned', color: CHART_COLORS.axis, shape: 'dashed' },
      ]}
      rows={rows}
      columns={[
        { header: 'Date', cell: (r) => formatDate(r.date) },
        { header: 'Planned', cell: (r) => formatMinutes(r.plannedMinutes), align: 'right' },
        { header: 'Actual', cell: (r) => formatMinutes(r.actualMinutes), align: 'right' },
        { header: 'Completed', cell: (r) => r.completedSessions, align: 'right' },
        { header: 'Missed', cell: (r) => r.missedSessions, align: 'right' },
      ]}
    >
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={rows} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
          <CartesianGrid vertical={false} stroke={CHART_COLORS.grid} />
          <XAxis
            dataKey="date"
            {...AXIS_PROPS}
            interval={tickEvery - 1}
            tickFormatter={(value: string) => formatDate(value, { weekday: undefined })}
          />
          <YAxis {...AXIS_PROPS} axisLine={false} tickFormatter={hoursTick} width={64} />
          <Tooltip
            cursor={{ stroke: CHART_COLORS.axis, strokeWidth: 1 }}
            content={({ active, payload }) => {
              if (!active || !payload?.length) return null;
              const row = payload[0].payload as (typeof rows)[number];
              return (
                <TooltipBox
                  title={formatDate(row.date, { weekday: 'long' })}
                  rows={[
                    { label: 'Actual', value: formatMinutes(row.actualMinutes), color: CHART_COLORS.actual },
                    { label: 'Planned', value: formatMinutes(row.plannedMinutes), color: CHART_COLORS.axis, dashed: true },
                    { label: 'Sessions done / missed', value: `${row.completedSessions} / ${row.missedSessions}` },
                  ]}
                />
              );
            }}
          />
          <Line
            type="monotone"
            dataKey="plannedHours"
            stroke={CHART_COLORS.axis}
            strokeWidth={2}
            strokeDasharray="5 4"
            dot={false}
            activeDot={{ r: 4 }}
          />
          <Line
            type="monotone"
            dataKey="actualHours"
            stroke={CHART_COLORS.actual}
            strokeWidth={2}
            dot={false}
            activeDot={{ r: 5, stroke: '#ffffff', strokeWidth: 2 }}
          />
        </LineChart>
      </ResponsiveContainer>
    </ChartFrame>
  );
}

// ------------------------------------------------------------------ monthly totals

export function MonthlyChart({ data }: { data: { month: string; actualMinutes: number; focusMinutes: number }[] }) {
  const rows = data.map((d) => ({
    ...d,
    label: new Intl.DateTimeFormat('en-IN', { month: 'short', year: 'numeric', timeZone: 'UTC' }).format(
      new Date(`${d.month}-01T00:00:00Z`),
    ),
    actualHours: toHours(d.actualMinutes),
  }));
  return (
    <ChartFrame
      title="Monthly study hours"
      description="Last six months"
      rows={rows}
      columns={[
        { header: 'Month', cell: (r) => r.label },
        { header: 'Study time', cell: (r) => formatMinutes(r.actualMinutes), align: 'right' },
        { header: 'Focus mode', cell: (r) => formatMinutes(r.focusMinutes), align: 'right' },
      ]}
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={rows} margin={{ top: 4, right: 4, left: -16, bottom: 0 }}>
          <CartesianGrid vertical={false} stroke={CHART_COLORS.grid} />
          <XAxis dataKey="label" {...AXIS_PROPS} />
          <YAxis {...AXIS_PROPS} axisLine={false} tickFormatter={hoursTick} width={64} />
          <Tooltip
            cursor={{ fill: 'rgba(15, 27, 51, 0.04)' }}
            content={({ active, payload }) => {
              if (!active || !payload?.length) return null;
              const row = payload[0].payload as (typeof rows)[number];
              return (
                <TooltipBox
                  title={row.label}
                  rows={[
                    { label: 'Study time', value: formatMinutes(row.actualMinutes), color: CHART_COLORS.actual },
                    { label: 'Focus mode', value: formatMinutes(row.focusMinutes) },
                  ]}
                />
              );
            }}
          />
          <Bar dataKey="actualHours" fill={CHART_COLORS.actual} radius={[4, 4, 0, 0]} maxBarSize={40} />
        </BarChart>
      </ResponsiveContainer>
    </ChartFrame>
  );
}

// ------------------------------------------------------------------ focus minutes per day

export function FocusDailyChart({ data }: { data: { date: string; minutes: number; sessions: number }[] }) {
  return (
    <ChartFrame
      title="Focus time - last 7 days"
      rows={data}
      columns={[
        { header: 'Day', cell: (r) => formatDate(r.date) },
        { header: 'Focus', cell: (r) => formatMinutes(r.minutes), align: 'right' },
        { header: 'Pomodoros', cell: (r) => r.sessions, align: 'right' },
      ]}
      height={220}
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 4, right: 4, left: -16, bottom: 0 }}>
          <CartesianGrid vertical={false} stroke={CHART_COLORS.grid} />
          <XAxis dataKey="date" {...AXIS_PROPS} tickFormatter={(value: string) => formatDate(value, { day: undefined, month: undefined })} />
          <YAxis {...AXIS_PROPS} axisLine={false} unit=" m" width={52} />
          <Tooltip
            cursor={{ fill: 'rgba(15, 27, 51, 0.04)' }}
            content={({ active, payload }) => {
              if (!active || !payload?.length) return null;
              const row = payload[0].payload as { date: string; minutes: number; sessions: number };
              return (
                <TooltipBox
                  title={formatDate(row.date, { weekday: 'long' })}
                  rows={[
                    { label: 'Focus', value: formatMinutes(row.minutes), color: CHART_COLORS.focus },
                    { label: 'Pomodoros', value: row.sessions },
                  ]}
                />
              );
            }}
          />
          <Bar dataKey="minutes" fill={CHART_COLORS.focus} radius={[4, 4, 0, 0]} maxBarSize={32} />
        </BarChart>
      </ResponsiveContainer>
    </ChartFrame>
  );
}

// ------------------------------------------------------------------ readiness trend (multi-line)

export interface TrendSeries {
  id: number;
  name: string;
  points: { date: string; score: number | null }[];
}

export function ReadinessTrendChart({ series, title = 'Exam readiness trend', description }: {
  series: TrendSeries[];
  title?: string;
  description?: string;
}) {
  const visible = series.slice(0, MAX_LINE_SERIES);
  const dates = Array.from(new Set(visible.flatMap((s) => s.points.map((p) => p.date)))).sort();
  const rows = dates.map((date) => {
    const row: Record<string, string | number | null> = { date };
    for (const s of visible) {
      row[`s${s.id}`] = s.points.find((p) => p.date === date)?.score ?? null;
    }
    return row;
  });
  const colorOf = (index: number) => SERIES_ORDER[index];
  return (
    <ChartFrame
      title={title}
      description={description}
      legend={visible.map((s, index) => ({ label: s.name, color: colorOf(index), shape: 'line' as const }))}
      rows={rows}
      columns={[
        { header: 'Date', cell: (r) => formatDate(String(r.date)) },
        ...visible.map((s) => ({
          header: s.name,
          cell: (r: Record<string, string | number | null>) => (r[`s${s.id}`] === null ? '-' : `${r[`s${s.id}`]}%`),
          align: 'right' as const,
        })),
      ]}
      empty={<EmptyState compact title="No readiness history yet" description="Open an exam's readiness report to record the first snapshot." />}
    >
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={rows} margin={{ top: 8, right: 12, left: -16, bottom: 0 }}>
          <CartesianGrid vertical={false} stroke={CHART_COLORS.grid} />
          <XAxis dataKey="date" {...AXIS_PROPS} tickFormatter={(value: string) => formatDate(value, { weekday: undefined })} />
          <YAxis {...AXIS_PROPS} axisLine={false} domain={[0, 100]} unit="%" width={64} />
          <Tooltip
            cursor={{ stroke: CHART_COLORS.axis, strokeWidth: 1 }}
            content={({ active, payload, label }) => {
              if (!active || !payload?.length) return null;
              return (
                <TooltipBox
                  title={formatDate(String(label))}
                  rows={visible.map((s, index) => {
                    const value = payload.find((p) => p.dataKey === `s${s.id}`)?.value;
                    return {
                      label: s.name,
                      value: value === null || value === undefined ? '-' : `${value}%`,
                      color: colorOf(index),
                    };
                  })}
                />
              );
            }}
          />
          {visible.map((s, index) => (
            <Line
              key={s.id}
              type="monotone"
              dataKey={`s${s.id}`}
              stroke={colorOf(index)}
              strokeWidth={2}
              connectNulls
              dot={{ r: 3, strokeWidth: 0, fill: colorOf(index) }}
              activeDot={{ r: 5, stroke: '#ffffff', strokeWidth: 2 }}
            />
          ))}
        </LineChart>
      </ResponsiveContainer>
    </ChartFrame>
  );
}

// ------------------------------------------------------------------ topic score history

export function TopicHistoryChart({ history }: { history: TopicHistory }) {
  const rows = history.points.map((p, index) => ({ ...p, index: index + 1 }));
  return (
    <ChartFrame
      title={`${history.topicName}: performance history`}
      description="Each assessment score and the mastery after it"
      legend={[
        { label: 'Assessment score', color: CHART_COLORS.actual, shape: 'line' },
        { label: 'Mastery', color: CHART_COLORS.focus, shape: 'line' },
      ]}
      rows={rows}
      columns={[
        { header: 'Date', cell: (r) => formatDate(r.assessedOn) },
        { header: 'Assessment', cell: (r) => r.title },
        { header: 'Score', cell: (r) => `${r.scorePercent}%`, align: 'right' },
        { header: 'Mastery after', cell: (r) => `${r.masteryAfter}%`, align: 'right' },
      ]}
      height={240}
      empty={<EmptyState compact title="No scores recorded for this topic" />}
    >
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={rows} margin={{ top: 8, right: 12, left: -16, bottom: 0 }}>
          <CartesianGrid vertical={false} stroke={CHART_COLORS.grid} />
          <XAxis dataKey="assessedOn" {...AXIS_PROPS} tickFormatter={(value: string) => formatDate(value, { weekday: undefined })} />
          <YAxis {...AXIS_PROPS} axisLine={false} domain={[0, 100]} unit="%" width={64} />
          <Tooltip
            cursor={{ stroke: CHART_COLORS.axis, strokeWidth: 1 }}
            content={({ active, payload }) => {
              if (!active || !payload?.length) return null;
              const row = payload[0].payload as (typeof rows)[number];
              return (
                <TooltipBox
                  title={`${row.title} · ${formatDate(row.assessedOn)}`}
                  rows={[
                    { label: 'Score', value: `${row.scorePercent}%`, color: CHART_COLORS.actual },
                    { label: 'Mastery after', value: `${row.masteryAfter}%`, color: CHART_COLORS.focus },
                  ]}
                />
              );
            }}
          />
          <Line type="monotone" dataKey="scorePercent" stroke={CHART_COLORS.actual} strokeWidth={2} dot={{ r: 4, strokeWidth: 0, fill: CHART_COLORS.actual }} />
          <Line type="monotone" dataKey="masteryAfter" stroke={CHART_COLORS.focus} strokeWidth={2} dot={{ r: 4, strokeWidth: 0, fill: CHART_COLORS.focus }} />
        </LineChart>
      </ResponsiveContainer>
    </ChartFrame>
  );
}

// ------------------------------------------------------------------ simple HTML bar charts

/** Completed / incomplete / missed sessions: status colours, always with labels and counts. */
export function SessionOutcomeBars({ completed, incomplete, missed }: { completed: number; incomplete: number; missed: number }) {
  const total = Math.max(1, completed + incomplete + missed);
  const items = [
    { label: 'Completed', value: completed, color: STATUS_COLORS.good },
    { label: 'Incomplete', value: incomplete, color: STATUS_COLORS.warning },
    { label: 'Missed', value: missed, color: STATUS_COLORS.critical },
  ];
  return (
    <div className="space-y-3">
      <div className="flex h-3 w-full overflow-hidden rounded-full bg-slate-100" role="img" aria-label={items.map((i) => `${i.label} ${i.value}`).join(', ')}>
        {items.map((item) =>
          item.value > 0 ? (
            <div key={item.label} style={{ width: `${(item.value / total) * 100}%`, backgroundColor: item.color }} className="h-full border-r-2 border-white last:border-r-0" />
          ) : null,
        )}
      </div>
      <ul className="grid grid-cols-3 gap-2 text-xs">
        {items.map((item) => (
          <li key={item.label} className="flex flex-col">
            <span className="inline-flex items-center gap-1.5 text-slate-500">
              <span className="h-2 w-2 rounded-sm" style={{ backgroundColor: item.color }} aria-hidden />
              {item.label}
            </span>
            <span className="text-base font-semibold text-slate-900 tabular-nums">{item.value}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** Distribution of topics over mastery levels as one segmented bar with a labelled legend. */
export function MasteryDistribution({ counts }: { counts: Record<MasteryLevel, number> }) {
  const order: MasteryLevel[] = ['STRONG', 'GOOD', 'NEEDS_IMPROVEMENT', 'WEAK', 'NOT_ASSESSED'];
  const total = Math.max(1, order.reduce((sum, level) => sum + (counts[level] ?? 0), 0));
  return (
    <div className="space-y-3">
      <div className="flex h-3 w-full overflow-hidden rounded-full bg-slate-100" role="img"
        aria-label={order.map((level) => `${MASTERY_LABELS[level]} ${counts[level] ?? 0}`).join(', ')}>
        {order.map((level) =>
          (counts[level] ?? 0) > 0 ? (
            <div key={level} className="h-full border-r-2 border-white last:border-r-0"
              style={{ width: `${((counts[level] ?? 0) / total) * 100}%`, backgroundColor: MASTERY_COLORS[level] }} />
          ) : null,
        )}
      </div>
      <ul className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-slate-600">
        {order.map((level) => (
          <li key={level} className="inline-flex items-center gap-1.5">
            <span className="h-2 w-2 rounded-sm" style={{ backgroundColor: MASTERY_COLORS[level] }} aria-hidden />
            {MASTERY_LABELS[level]} <span className="font-semibold text-slate-800">{counts[level] ?? 0}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
