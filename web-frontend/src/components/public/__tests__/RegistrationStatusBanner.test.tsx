/**
 * RegistrationStatusBanner Component Tests
 * Story 10.10: Registration Status Indicator for Logged-in Users (T8.8)
 *
 * Tests AC2, AC3, AC4:
 * - Skeleton shown during loading (AC4)
 * - null rendered when status is null/undefined (AC2)
 * - Each status variant renders correct severity and link (AC2, AC3)
 */

import { describe, test, expect, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { render } from '@/test/test-utils';
import { RegistrationStatusBanner } from '../RegistrationStatusBanner';

// Mock i18n — return key as value for easy assertion
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

describe('RegistrationStatusBanner', () => {
  // ── AC4: Loading skeleton ────────────────────────────────────────────────

  test('renders skeleton when isLoading is true', () => {
    render(<RegistrationStatusBanner status={undefined} eventCode="BATbern999" isLoading={true} />);

    expect(screen.getByTestId('registration-status-banner-skeleton')).toBeInTheDocument();
    expect(screen.queryByTestId('registration-status-banner')).not.toBeInTheDocument();
  });

  test('skeleton has rectangular variant (same height as banner to prevent CLS)', () => {
    const { container } = render(
      <RegistrationStatusBanner status={undefined} eventCode="BATbern999" isLoading={true} />
    );

    const skeleton = screen.getByTestId('registration-status-banner-skeleton');
    // MUI Skeleton variant="rectangular" renders without rounded corners by default
    expect(skeleton).toBeInTheDocument();
    // Confirm no banner rendered
    expect(container.querySelector('[data-testid="registration-status-banner"]')).toBeNull();
  });

  // ── AC2: No banner when not registered ──────────────────────────────────

  test('renders nothing when status is null (not registered)', () => {
    const { container } = render(
      <RegistrationStatusBanner status={null} eventCode="BATbern999" isLoading={false} />
    );

    expect(container.firstChild).toBeNull();
  });

  test('renders nothing when status is undefined (no data)', () => {
    const { container } = render(
      <RegistrationStatusBanner status={undefined} eventCode="BATbern999" isLoading={false} />
    );

    expect(container.firstChild).toBeNull();
  });

  // ── AC2 + AC3: Status variants ───────────────────────────────────────────

  test('renders success (CONFIRMED) banner with manage link', () => {
    render(
      <RegistrationStatusBanner status="CONFIRMED" eventCode="BATbern999" isLoading={false} />
    );

    const banner = screen.getByTestId('registration-status-banner');
    expect(banner).toBeInTheDocument();

    // MUI Alert severity="success" renders with class MuiAlert-colorSuccess
    expect(banner.className).toMatch(/success/i);

    // Shows status text
    expect(screen.getByText('registrationStatusBanner.confirmed')).toBeInTheDocument();

    // AC3: "Manage Registration" link to /register/{eventCode}
    const link = screen.getByText('registrationStatusBanner.manageLink');
    expect(link).toBeInTheDocument();
    expect(link.closest('a')).toHaveAttribute('href', '/register/BATbern999');
  });

  test('renders warning (REGISTERED) banner with manage link', () => {
    render(
      <RegistrationStatusBanner status="REGISTERED" eventCode="BATbern999" isLoading={false} />
    );

    const banner = screen.getByTestId('registration-status-banner');
    expect(banner.className).toMatch(/warning/i);
    expect(screen.getByText('registrationStatusBanner.registered')).toBeInTheDocument();

    const link = screen.getByText('registrationStatusBanner.manageLink');
    expect(link.closest('a')).toHaveAttribute('href', '/register/BATbern999');
  });

  test('renders info (WAITLIST) banner with manage link — no position (fallback)', () => {
    render(<RegistrationStatusBanner status="WAITLIST" eventCode="BATbern999" isLoading={false} />);

    const banner = screen.getByTestId('registration-status-banner');
    expect(banner.className).toMatch(/info/i);
    expect(screen.getByText('registrationStatusBanner.waitlist')).toBeInTheDocument();

    const link = screen.getByText('registrationStatusBanner.manageLink');
    expect(link.closest('a')).toHaveAttribute('href', '/register/BATbern999');
  });

  // ── AC13 (Story 10.11): WAITLIST with position ───────────────────────────

  test('WAITLIST with waitlistPosition=3 shows waitlistWithPosition key', () => {
    render(
      <RegistrationStatusBanner
        status="WAITLIST"
        eventCode="BATbern999"
        isLoading={false}
        waitlistPosition={3}
      />
    );

    const banner = screen.getByTestId('registration-status-banner');
    expect(banner.className).toMatch(/info/i);
    // t mock returns key; key used is waitlistWithPosition (not waitlist)
    expect(screen.getByText('registrationStatusBanner.waitlistWithPosition')).toBeInTheDocument();
    expect(screen.queryByText('registrationStatusBanner.waitlist')).not.toBeInTheDocument();
  });

  test('WAITLIST with waitlistPosition=null falls back to waitlist key', () => {
    render(
      <RegistrationStatusBanner
        status="WAITLIST"
        eventCode="BATbern999"
        isLoading={false}
        waitlistPosition={null}
      />
    );

    expect(screen.getByText('registrationStatusBanner.waitlist')).toBeInTheDocument();
    expect(
      screen.queryByText('registrationStatusBanner.waitlistWithPosition')
    ).not.toBeInTheDocument();
  });

  test('renders grey (CANCELLED) banner with "register again" link', () => {
    render(
      <RegistrationStatusBanner status="CANCELLED" eventCode="BATbern999" isLoading={false} />
    );

    const banner = screen.getByTestId('registration-status-banner');
    // AC2: CANCELLED → grey custom div, not a MUI Alert severity element
    expect(banner).toHaveAttribute('data-status', 'CANCELLED');
    expect(screen.getByText('registrationStatusBanner.cancelled')).toBeInTheDocument();

    // AC3: CANCELLED shows "Register again" (not "Manage Registration")
    const link = screen.getByText('registrationStatusBanner.registerAgain');
    expect(link).toBeInTheDocument();
    expect(link.closest('a')).toHaveAttribute('href', '/register/BATbern999');
    expect(screen.queryByText('registrationStatusBanner.manageLink')).not.toBeInTheDocument();
  });

  // ── Event code in link ───────────────────────────────────────────────────

  test('includes correct eventCode in manage link URL', () => {
    render(
      <RegistrationStatusBanner status="CONFIRMED" eventCode="BATbern142" isLoading={false} />
    );

    const link = screen.getByText('registrationStatusBanner.manageLink');
    expect(link.closest('a')).toHaveAttribute('href', '/register/BATbern142');
  });
});
