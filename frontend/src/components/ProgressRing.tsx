/** Circular progress indicator used for readiness and completion scores. */
export function ProgressRing({
  value,
  size = 120,
  stroke = 10,
  label,
  sublabel,
  color = '#10b981',
  muted = false,
}: {
  value: number | null;
  size?: number;
  stroke?: number;
  label?: string;
  sublabel?: string;
  color?: string;
  muted?: boolean;
}) {
  const radius = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radius;
  const clamped = value === null ? 0 : Math.max(0, Math.min(100, value));
  const offset = circumference * (1 - clamped / 100);
  return (
    <div className="relative inline-flex items-center justify-center" style={{ width: size, height: size }}>
      <svg width={size} height={size} className="-rotate-90" role="img" aria-label={`${label ?? 'Score'}: ${value === null ? 'no data' : `${Math.round(clamped)} percent`}`}>
        <circle cx={size / 2} cy={size / 2} r={radius} fill="none" stroke="#e2e8f0" strokeWidth={stroke} />
        {value !== null && (
          <circle
            cx={size / 2}
            cy={size / 2}
            r={radius}
            fill="none"
            stroke={muted ? '#94a3b8' : color}
            strokeWidth={stroke}
            strokeDasharray={circumference}
            strokeDashoffset={offset}
            strokeLinecap="round"
            className="transition-all duration-700"
          />
        )}
      </svg>
      <div className="absolute flex flex-col items-center">
        <span className={`font-semibold tabular-nums text-slate-900 ${size >= 110 ? 'text-2xl' : 'text-lg'}`}>
          {value === null ? '--' : `${Math.round(clamped)}%`}
        </span>
        {sublabel && <span className="text-[11px] text-slate-500">{sublabel}</span>}
      </div>
    </div>
  );
}

/** Colour for a readiness / mastery score. */
export function scoreColor(score: number | null): string {
  if (score === null) return '#94a3b8';
  if (score >= 80) return '#059669';
  if (score >= 60) return '#2563eb';
  if (score >= 40) return '#d97706';
  return '#dc2626';
}
