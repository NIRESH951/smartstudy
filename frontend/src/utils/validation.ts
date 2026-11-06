// Client-side validation mirrors the server rules so students get instant feedback.
// The server validates again; its field errors are shown when they differ.

export function validatePassword(password: string): string | null {
  if (password.length < 8 || password.length > 72) return 'Use 8 to 72 characters.';
  if (!/[A-Za-z]/.test(password) || !/\d/.test(password)) return 'Include at least one letter and one digit.';
  return null;
}

export function requireText(value: string, label: string, max: number): string | null {
  if (!value.trim()) return `${label} is required.`;
  if (value.trim().length > max) return `${label} must be at most ${max} characters.`;
  return null;
}

export function numberInRange(value: string, label: string, min: number, max: number, required = true): string | null {
  if (value.trim() === '') return required ? `${label} is required.` : null;
  const number = Number(value);
  if (!Number.isFinite(number)) return `${label} must be a number.`;
  if (number < min || number > max) return `${label} must be between ${min} and ${max}.`;
  return null;
}

/** Removes null entries so the result can be passed to setErrors. */
export function collectErrors(entries: Record<string, string | null>): Record<string, string> {
  const result: Record<string, string> = {};
  for (const [key, message] of Object.entries(entries)) {
    if (message) result[key] = message;
  }
  return result;
}
