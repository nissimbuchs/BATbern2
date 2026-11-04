import { describe, expect, it } from 'vitest';
import { loginTarget } from './loginTarget';

describe('loginTarget', () => {
  it('returns to the page that sent the user to the login (ProtectedRoute, Q&A link)', () => {
    expect(
      loginTarget({ from: { pathname: '/claim-profile', search: '?token=abc', hash: '' } })
    ).toBe('/claim-profile?token=abc');
    expect(loginTarget({ from: { pathname: '/', search: '' } })).toBe('/');
    expect(loginTarget({ from: { pathname: '/archive/BATbern58', hash: '#q' } })).toBe(
      '/archive/BATbern58#q'
    );
  });

  it('falls back to the dashboard without a usable origin', () => {
    expect(loginTarget(null)).toBe('/dashboard');
    expect(loginTarget(undefined)).toBe('/dashboard');
    expect(loginTarget({})).toBe('/dashboard');
    expect(loginTarget({ from: { pathname: 42 } })).toBe('/dashboard');
    expect(loginTarget({ from: { pathname: '/login' } })).toBe('/dashboard');
    expect(loginTarget({ from: { pathname: '/logout' } })).toBe('/dashboard');
  });

  it('never leaves the site', () => {
    expect(loginTarget({ from: { pathname: '//evil.example.com/x' } })).toBe('/dashboard');
    expect(loginTarget({ from: { pathname: 'https://evil.example.com' } })).toBe('/dashboard');
    expect(loginTarget({ from: { pathname: '/\\evil.example.com' } })).toBe('/dashboard');
  });
});
