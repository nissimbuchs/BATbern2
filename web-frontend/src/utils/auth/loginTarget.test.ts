import { beforeEach, describe, expect, it } from 'vitest';
import {
  loginTarget,
  rememberLoginTarget,
  resolveLoginTarget,
  takeRememberedLoginTarget,
} from './loginTarget';

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

/**
 * The router state does not survive leaving the app: "Continue with Google" goes through the
 * Cognito hosted UI to /auth/callback, and sign-up goes through registration and e-mail
 * verification before the login. The target is kept for that trip in this tab only.
 */
describe('remembered login target', () => {
  const NOW = 1_800_000_000_000;
  beforeEach(() => sessionStorage.clear());

  it('gives back the target remembered before leaving, once', () => {
    rememberLoginTarget({ from: { pathname: '/', search: '?nominate=BATbern61' } }, NOW);

    expect(takeRememberedLoginTarget(NOW + 60_000)).toBe('/?nominate=BATbern61');
    expect(takeRememberedLoginTarget(NOW + 60_000)).toBeNull();
  });

  it('remembers nothing without a usable origin', () => {
    rememberLoginTarget(null, NOW);
    rememberLoginTarget({ from: { pathname: '//evil.example.com/x' } }, NOW);

    expect(takeRememberedLoginTarget(NOW)).toBeNull();
  });

  it('forgets a target older than an hour', () => {
    rememberLoginTarget({ from: { pathname: '/claim-profile', search: '?token=abc' } }, NOW);

    expect(takeRememberedLoginTarget(NOW + 61 * 60_000)).toBeNull();
  });

  it('never returns a tampered value that would leave the site', () => {
    sessionStorage.setItem(
      'batbern.loginReturn',
      JSON.stringify({ from: { pathname: '//evil.example.com' }, at: NOW })
    );
    expect(takeRememberedLoginTarget(NOW)).toBeNull();
    sessionStorage.setItem('batbern.loginReturn', 'not json');
    expect(takeRememberedLoginTarget(NOW)).toBeNull();
  });
});

/** What the login page does after a sign-in (password path). */
describe('resolveLoginTarget', () => {
  const NOW = 1_800_000_000_000;
  beforeEach(() => sessionStorage.clear());

  it('prefers the router state and drops an older remembered target', () => {
    rememberLoginTarget({ from: { pathname: '/claim-profile', search: '?token=old' } }, NOW);

    expect(resolveLoginTarget({ from: { pathname: '/archive/BATbern58' } }, NOW)).toBe(
      '/archive/BATbern58'
    );
    expect(takeRememberedLoginTarget(NOW)).toBeNull();
  });

  it('uses the remembered target when the state is gone (back from sign-up and verification)', () => {
    rememberLoginTarget({ from: { pathname: '/', search: '?nominate=BATbern61' } }, NOW);

    expect(resolveLoginTarget(null, NOW + 10 * 60_000)).toBe('/?nominate=BATbern61');
  });

  it('falls back to the dashboard with neither', () => {
    expect(resolveLoginTarget(null, NOW)).toBe('/dashboard');
  });
});
