// Chart colours and shared axis styling.
// Categorical slots follow a validated, fixed order (CVD-checked); "planned" is a neutral
// reference colour, and outcome colours reuse the status palette (always paired with labels).

export const CHART_COLORS = {
  actual: '#2a78d6',
  focus: '#eb6834',
  third: '#1baf7a',
  fourth: '#eda100',
  planned: '#c3c2b7',
  grid: '#e1e0d9',
  axis: '#898781',
  ink: '#52514e',
} as const;

/** Fixed categorical order for multi-series line charts (never cycled - extra series are cut). */
export const SERIES_ORDER = ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300', '#6250d6', '#e34948'];

export const STATUS_COLORS = {
  good: '#0ca30c',
  warning: '#fab219',
  serious: '#ec835a',
  critical: '#d03b3b',
} as const;

export const MASTERY_COLORS: Record<string, string> = {
  STRONG: STATUS_COLORS.good,
  GOOD: '#2a78d6',
  NEEDS_IMPROVEMENT: STATUS_COLORS.warning,
  WEAK: STATUS_COLORS.critical,
  NOT_ASSESSED: '#c3c2b7',
};

export const AXIS_PROPS = {
  stroke: CHART_COLORS.axis,
  tick: { fill: CHART_COLORS.axis, fontSize: 12 },
  tickLine: false,
  axisLine: { stroke: '#c3c2b7' },
} as const;
