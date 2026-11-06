import { describe, expect, it } from 'vitest';
import { collectErrors, numberInRange, requireText, validatePassword } from './validation';

describe('validation helpers', () => {
  it('validates passwords the same way as the server', () => {
    expect(validatePassword('short1')).toMatch(/8 to 72/);
    expect(validatePassword('onlyletters')).toMatch(/letter and one digit/);
    expect(validatePassword('12345678')).toMatch(/letter and one digit/);
    expect(validatePassword('Demo@12345')).toBeNull();
    expect(validatePassword('a1'.repeat(37))).toMatch(/8 to 72/);
  });

  it('requires non-blank text within the limit', () => {
    expect(requireText('   ', 'Name', 10)).toBe('Name is required.');
    expect(requireText('abcdefghijk', 'Name', 10)).toBe('Name must be at most 10 characters.');
    expect(requireText(' Physics ', 'Name', 10)).toBeNull();
  });

  it('checks numeric ranges and optional values', () => {
    expect(numberInRange('', 'Score', 0, 100)).toBe('Score is required.');
    expect(numberInRange('', 'Score', 0, 100, false)).toBeNull();
    expect(numberInRange('abc', 'Score', 0, 100)).toBe('Score must be a number.');
    expect(numberInRange('101', 'Score', 0, 100)).toBe('Score must be between 0 and 100.');
    expect(numberInRange('55.5', 'Score', 0, 100)).toBeNull();
  });

  it('collects only the failing fields', () => {
    expect(collectErrors({ a: null, b: 'Required', c: null })).toEqual({ b: 'Required' });
  });
});
