import { describe, expect, it } from 'vitest';
import { addDays, formatClock, formatMinutes, formatTime, isoDayOfWeek, todayIn, weekStart } from './format';

describe('format helpers', () => {
  it('formats minutes as hours and minutes', () => {
    expect(formatMinutes(0)).toBe('0 min');
    expect(formatMinutes(45)).toBe('45 min');
    expect(formatMinutes(120)).toBe('2 h');
    expect(formatMinutes(135)).toBe('2 h 15 min');
  });

  it('formats the Pomodoro clock', () => {
    expect(formatClock(1500)).toBe('25:00');
    expect(formatClock(61)).toBe('01:01');
    expect(formatClock(-5)).toBe('00:00');
  });

  it('trims seconds from times', () => {
    expect(formatTime('18:00:00')).toBe('18:00');
    expect(formatTime('07:30')).toBe('07:30');
  });

  it('does date arithmetic without time-zone drift', () => {
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
    expect(addDays('2028-02-28', 1)).toBe('2028-02-29');
    expect(isoDayOfWeek('2030-01-07')).toBe(1);
    expect(isoDayOfWeek('2030-01-13')).toBe(7);
    expect(weekStart('2030-01-10')).toBe('2030-01-07');
  });

  it('computes today in the student time zone', () => {
    const instant = new Date('2026-03-10T20:00:00Z');
    expect(todayIn('UTC', instant)).toBe('2026-03-10');
    expect(todayIn('Asia/Kolkata', instant)).toBe('2026-03-11');
  });
});
