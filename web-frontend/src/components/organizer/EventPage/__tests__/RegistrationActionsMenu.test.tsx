/**
 * RegistrationActionsMenu tests: the 14.G.4 ⋯ overflow variant, the inline variant's stable testids
 * (#955), and what a cancel or delete refreshes (#824 item 5).
 *
 * The real eventRegistrationService and apiClient run against the MSW harness, with the real
 * i18next `en` bundle (touch rule, #824: this file mocked the service, a hook and i18n before).
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { I18nextProvider } from 'react-i18next';
import { HttpResponse } from 'msw';
import i18n from '@/i18n/config';
import { api } from '@/test/msw/api';
import type { EventParticipant } from '../../../../types/eventParticipant.types';
import RegistrationActionsMenu from '../RegistrationActionsMenu';

const EVENT = 'BATbern54';
const CODE = 'BATbern54-reg-ADA001';

const participant = {
  eventCode: EVENT,
  registrationCode: CODE,
  firstName: 'Ada',
  lastName: 'Lovelace',
  status: 'REGISTERED',
} as unknown as EventParticipant;

/** The query keys a mutation marked stale. */
const invalidatedKeys = (spy: { mock: { calls: unknown[][] } }): unknown[][] =>
  spy.mock.calls.map((call) => (call[0] as { queryKey: unknown[] }).queryKey);

const renderMenu = (variant: 'overflow' | 'inline' = 'overflow', status = 'REGISTERED') => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const spy = vi.spyOn(qc, 'invalidateQueries');
  render(
    <QueryClientProvider client={qc}>
      <I18nextProvider i18n={i18n}>
        <RegistrationActionsMenu
          participant={{ ...participant, status } as unknown as EventParticipant}
          variant={variant}
        />
      </I18nextProvider>
    </QueryClientProvider>
  );
  return { spy };
};

describe('RegistrationActionsMenu — overflow variant (14.G.4)', () => {
  it('renders a single ⋯ button (not inline icons)', () => {
    renderMenu();
    expect(screen.getByTestId('registration-actions-overflow')).toBeInTheDocument();
    expect(screen.queryByTestId('registration-action-cancel')).not.toBeInTheDocument();
  });

  it('opens a menu exposing Resend / Cancel / Delete', () => {
    renderMenu();
    fireEvent.click(screen.getByTestId('registration-actions-overflow'));
    expect(screen.getByText('Resend Confirmation Email')).toBeInTheDocument();
    expect(screen.getByText('Cancel Registration')).toBeInTheDocument();
    expect(screen.getByText('Delete Registration')).toBeInTheDocument();
  });

  it('Delete from the menu opens the confirm dialog', () => {
    renderMenu();
    fireEvent.click(screen.getByTestId('registration-actions-overflow'));
    fireEvent.click(screen.getByText('Delete Registration'));
    expect(screen.getByRole('dialog')).toHaveTextContent('Ada Lovelace');
  });
});

/**
 * Inline variant (desktop table rows) — issue #955.
 *
 * The nightly renders the organizer UI in German (i18n `fallbackLng: 'de'`, and the detector
 * order is ['localStorage','htmlTag'] with no `navigator`, so a fresh browser context always
 * falls back). The E2E spec located these controls by the English accessible name
 * `getByLabel('Cancel Registration')`, which cannot resolve there — 41 consecutive red
 * nightlies. These tests pin the locale-neutral testids the spec now uses, so the E2E
 * locators cannot silently drift again.
 */
describe('RegistrationActionsMenu — inline variant (#955 locale-neutral testids)', () => {
  it('exposes stable testids for resend, cancel and delete', () => {
    renderMenu('inline');
    expect(screen.getByTestId('registration-action-resend')).toBeInTheDocument();
    expect(screen.getByTestId('registration-action-cancel')).toBeInTheDocument();
    expect(screen.getByTestId('registration-action-delete')).toBeInTheDocument();
  });

  it('hides the cancel action for an already-cancelled registration', () => {
    renderMenu('inline', 'CANCELLED');
    expect(screen.queryByTestId('registration-action-cancel')).not.toBeInTheDocument();
    expect(screen.getByTestId('registration-action-delete')).toBeInTheDocument();
  });

  it('exposes a testid on the delete confirm button', () => {
    renderMenu('inline');
    fireEvent.click(screen.getByTestId('registration-action-delete'));
    expect(screen.getByTestId('registration-delete-confirm')).toBeInTheDocument();
    expect(screen.getByTestId('registration-delete-dismiss')).toBeInTheDocument();
  });
});

/**
 * #824 item 5: the Registrations tab reads its counts (confirmed, waitlist, capacity bar) from the
 * event query, not from the registration list. A cancel or delete refreshed only the list, so the
 * counts stayed where they were.
 */
describe('RegistrationActionsMenu — a cancel or delete refreshes the counts (#824 item 5)', () => {
  it('cancelling refreshes the list and the event with its counts', async () => {
    const { spy } = renderMenu('inline');

    fireEvent.click(screen.getByTestId('registration-action-cancel'));

    await waitFor(() => expect(api.calls('updateRegistration')).toHaveLength(1));
    expect(api.calls('updateRegistration')[0].body).toEqual({ status: 'cancelled' });
    await waitFor(() => expect(invalidatedKeys(spy)).toContainEqual(['event', EVENT]));
    expect(invalidatedKeys(spy)).toContainEqual(['event-registrations', EVENT]);
  });

  it('deleting refreshes the list and the event with its counts', async () => {
    api.on('deleteRegistration', () => new HttpResponse(null, { status: 204 }));
    const { spy } = renderMenu('inline');

    fireEvent.click(screen.getByTestId('registration-action-delete'));
    fireEvent.click(screen.getByTestId('registration-delete-confirm'));

    await waitFor(() => expect(api.calls('deleteRegistration')).toHaveLength(1));
    expect(api.calls('deleteRegistration')[0].params).toEqual({
      eventCode: EVENT,
      registrationCode: CODE,
    });
    await waitFor(() => expect(invalidatedKeys(spy)).toContainEqual(['event', EVENT]));
    expect(invalidatedKeys(spy)).toContainEqual(['event-registrations', EVENT]);
  });

  it('resending the confirmation changes no count and refreshes nothing (positive control)', async () => {
    const { spy } = renderMenu('inline');

    fireEvent.click(screen.getByTestId('registration-action-resend'));

    await waitFor(() => expect(api.calls('resendConfirmationEmail')).toHaveLength(1));
    expect(invalidatedKeys(spy)).not.toContainEqual(['event', EVENT]);
  });
});
