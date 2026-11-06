/**
 * DeregistrationEntry tests (#1183)
 *
 * A logged-in visitor without a registration must not be offered "cancel registration": on
 * 2026-11-02 a participant clicked it four times, was told each time that a link was on its way,
 * and none could come because there was nothing to cancel. Instead they see that they are not
 * registered under their address, with a way to cancel a registration made with another address.
 */

import { describe, test, expect, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { render } from '@/test/test-utils';
import { DeregistrationEntry } from '../DeregistrationEntry';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) =>
      opts?.email ? `${key}:${String(opts.email)}` : key,
  }),
}));

vi.mock('../DeregistrationByEmailModal', () => ({
  DeregistrationByEmailModal: ({ open }: { open: boolean }) =>
    open ? <div data-testid="deregistration-modal" /> : null,
}));

const base = { eventCode: 'BATbern999', isLoading: false };

describe('DeregistrationEntry', () => {
  test('anonymous visitor gets the cancel link, which opens the e-mail modal', async () => {
    render(<DeregistrationEntry {...base} isAuthenticated={false} status={undefined} />);

    await userEvent.click(
      screen.getByRole('button', { name: 'deregistration.homepage.cancelLink' })
    );

    expect(screen.getByTestId('deregistration-modal')).toBeInTheDocument();
    expect(screen.queryByTestId('not-registered-notice')).not.toBeInTheDocument();
  });

  test('logged-in visitor without registration sees "not registered with <email>", no cancel link', () => {
    render(
      <DeregistrationEntry
        {...base}
        isAuthenticated
        status={null}
        userEmail="someone@example.com"
      />
    );

    expect(screen.getByTestId('not-registered-notice')).toHaveTextContent(
      'deregistration.homepage.notRegistered:someone@example.com'
    );
    expect(
      screen.queryByRole('button', { name: 'deregistration.homepage.cancelLink' })
    ).not.toBeInTheDocument();
  });

  test('logged-in visitor without registration can still ask for a link for another address', async () => {
    render(
      <DeregistrationEntry
        {...base}
        isAuthenticated
        status={null}
        userEmail="someone@example.com"
      />
    );

    await userEvent.click(
      screen.getByRole('button', { name: 'deregistration.homepage.otherAddressLink' })
    );

    expect(screen.getByTestId('deregistration-modal')).toBeInTheDocument();
  });

  test.each(['CONFIRMED', 'REGISTERED', 'WAITLIST'] as const)(
    'logged-in visitor with status %s gets nothing here (the hero offers cancel directly)',
    (status) => {
      const { container } = render(
        <DeregistrationEntry {...base} isAuthenticated status={status} userEmail="a@example.com" />
      );

      expect(container).toBeEmptyDOMElement();
    }
  );

  test('logged-in visitor with a cancelled registration gets nothing here', () => {
    const { container } = render(
      <DeregistrationEntry {...base} isAuthenticated status="CANCELLED" userEmail="a@example.com" />
    );

    expect(container).toBeEmptyDOMElement();
  });

  test('nothing while the registration is still loading', () => {
    const { container } = render(
      <DeregistrationEntry {...base} isAuthenticated isLoading status={undefined} />
    );

    expect(container).toBeEmptyDOMElement();
  });

  // #1238 (E2): no cancellation from the event day on
  test('anonymous visitor gets no cancel link on the event day', () => {
    render(
      <DeregistrationEntry {...base} isAuthenticated={false} status={undefined} canCancel={false} />
    );

    expect(
      screen.queryByRole('button', { name: 'deregistration.homepage.cancelLink' })
    ).not.toBeInTheDocument();
  });

  test('logged-in visitor without a registration keeps the notice but not the cancel link on the event day', () => {
    render(
      <DeregistrationEntry
        {...base}
        isAuthenticated
        status={null}
        userEmail="a@example.com"
        canCancel={false}
      />
    );

    expect(screen.getByTestId('not-registered-notice')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'deregistration.homepage.otherAddressLink' })
    ).not.toBeInTheDocument();
  });
});
