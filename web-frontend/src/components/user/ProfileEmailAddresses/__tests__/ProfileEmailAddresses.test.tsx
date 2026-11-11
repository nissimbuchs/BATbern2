/**
 * #1244 — participants manage their addresses on /profile (public styling): primary address,
 * additional addresses with their state, add / remove / resend, and the copy of event mails per
 * confirmed address (owner decision 2026-11-07: event mails to the primary, copies opt-in).
 *
 * Runs on the MSW harness with the real services and i18next (en): every request is checked against
 * users-api and recorded. Story 4.10d: an address is addressed by its id, never by the address.
 * Amplify (the session refresh after "use as primary") is the boundary, mocked in setup.ts.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { HttpResponse } from 'msw';
import { fetchAuthSession } from 'aws-amplify/auth';
import i18n from '@/i18n/config';
import { api } from '@/test/msw/api';
import type { AdditionalEmail } from '@/services/api/userAccountApi';
import { ProfileEmailAddresses } from '../ProfileEmailAddresses';

/** The en text of a common key, as the screen renders it. */
const en = (key: string, opts: Record<string, unknown> = {}) =>
  i18n.t(key, { ns: 'common', lng: 'en', ...opts });

const confirmed: AdditionalEmail = {
  id: '3b1f6c2e-8a4d-4f5e-9c7b-1a2b3c4d5e6f',
  email: 'private@example.com',
  label: null,
  createdAt: '2026-11-01T10:00:00Z',
  verifiedAt: '2026-11-01T10:05:00Z',
  receiveEventMail: false,
};
const pending: AdditionalEmail = {
  id: '9d8c7b6a-5f4e-4d3c-8b2a-1f0e9d8c7b6a',
  email: 'pending@example.com',
  label: null,
  createdAt: '2026-11-02T10:00:00Z',
  verifiedAt: null,
  receiveEventMail: false,
};

function problem(status: number, errorCode?: string) {
  return HttpResponse.json(
    {
      timestamp: '2026-11-11T08:00:00Z',
      path: '/api/v1/users/me',
      status,
      error: status === 404 ? 'Not Found' : 'Conflict',
      errorCode,
      message: 'refused',
    },
    { status }
  );
}

function renderSection(additional = [confirmed, pending]) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <ProfileEmailAddresses primaryEmail="work@example.com" additionalEmails={additional} />
    </QueryClientProvider>
  );
}

describe('ProfileEmailAddresses', () => {
  beforeEach(() => {
    vi.mocked(fetchAuthSession).mockReset();
  });

  it('shows the primary address and each additional address with its state', () => {
    renderSection();

    const primary = screen.getByTestId('email-row-primary');
    expect(within(primary).getByText('work@example.com')).toBeInTheDocument();
    expect(within(primary).getByText(en('profile.emails.primary'))).toBeInTheDocument();

    const ok = screen.getByTestId('email-row-private@example.com');
    expect(within(ok).getByText(en('profile.emails.confirmed'))).toBeInTheDocument();
    const waiting = screen.getByTestId('email-row-pending@example.com');
    expect(within(waiting).getByText(en('profile.emails.pending'))).toBeInTheDocument();
  });

  it('offers the copy of event mails only for a confirmed address', () => {
    renderSection();

    expect(screen.getByTestId('event-copy-private@example.com')).toBeEnabled();
    expect(screen.getByTestId('event-copy-pending@example.com')).toBeDisabled();
    expect(screen.queryByTestId('event-copy-work@example.com')).not.toBeInTheDocument();
  });

  it('switches the copy of event mails on for one address, by its id', async () => {
    api.on('updateAdditionalEmail', () => ({ ...confirmed, receiveEventMail: true }));
    const user = userEvent.setup();
    renderSection();

    await user.click(screen.getByTestId('event-copy-private@example.com'));

    await waitFor(() => expect(api.calls('updateAdditionalEmail')).toHaveLength(1));
    const [call] = api.calls('updateAdditionalEmail');
    expect(call.params).toEqual({ emailId: confirmed.id });
    expect(call.body).toEqual({ receiveEventMail: true });
  });

  it('adds an address and removes one by its id', async () => {
    api.on('addAdditionalEmail', () => ({ ...pending, email: 'new@example.com' }), {
      status: 201,
    });
    const user = userEvent.setup();
    renderSection();

    await user.type(screen.getByTestId('email-add-input'), 'new@example.com');
    await user.click(screen.getByTestId('email-add-button'));
    await waitFor(() => expect(api.calls('addAdditionalEmail')).toHaveLength(1));
    expect(api.calls('addAdditionalEmail')[0].body).toEqual({ email: 'new@example.com' });

    await user.click(screen.getByTestId('email-remove-private@example.com'));
    expect(api.calls('deleteAdditionalEmail')).toHaveLength(0);
    await user.click(screen.getByTestId('email-remove-confirm-private@example.com'));
    await waitFor(() => expect(api.calls('deleteAdditionalEmail')).toHaveLength(1));
    expect(api.calls('deleteAdditionalEmail')[0].params).toEqual({ emailId: confirmed.id });
  });

  it('resends the confirmation only for an unconfirmed address, by its id', async () => {
    const user = userEvent.setup();
    renderSection();

    expect(screen.queryByTestId('email-resend-private@example.com')).not.toBeInTheDocument();
    await user.click(screen.getByTestId('email-resend-pending@example.com'));

    await waitFor(() => expect(api.calls('resendAdditionalEmailVerification')).toHaveLength(1));
    expect(api.calls('resendAdditionalEmailVerification')[0].params).toEqual({
      emailId: pending.id,
    });
    expect(await screen.findByText(en('profile.emails.resent'))).toBeInTheDocument();
  });

  it('explains which address logs in', () => {
    renderSection();
    expect(screen.getByText(en('profile.emails.loginHint'))).toBeInTheDocument();
  });

  // ---------------------------------------------------------------- #1244 step 2

  it('offers "use as primary" only for a confirmed address, after a confirmation step', async () => {
    const user = userEvent.setup();
    renderSection();

    expect(screen.queryByTestId('email-make-primary-pending@example.com')).not.toBeInTheDocument();
    await user.click(screen.getByTestId('email-make-primary-private@example.com'));
    expect(api.calls('makeAdditionalEmailPrimary')).toHaveLength(0);
    await user.click(screen.getByTestId('email-make-primary-confirm-private@example.com'));

    await waitFor(() => expect(api.calls('makeAdditionalEmailPrimary')).toHaveLength(1));
    expect(api.calls('makeAdditionalEmailPrimary')[0].params).toEqual({ emailId: confirmed.id });
    // the ID token still carries the old address: the session is refreshed
    await waitFor(() => expect(fetchAuthSession).toHaveBeenCalledWith({ forceRefresh: true }));
  });

  it('when another account logs in with the address, offers to merge it (step 3)', async () => {
    api.on('makeAdditionalEmailPrimary', () => problem(409, 'EMAIL_HAS_OTHER_LOGIN'));
    const user = userEvent.setup();
    renderSection();

    await user.click(screen.getByTestId('email-make-primary-private@example.com'));
    await user.click(screen.getByTestId('email-make-primary-confirm-private@example.com'));
    await user.click(await screen.findByTestId('account-merge-send'));

    await waitFor(() => expect(api.calls('requestAccountMerge')).toHaveLength(1));
    expect(api.calls('requestAccountMerge')[0].body).toEqual({ email: 'private@example.com' });
    expect(await screen.findByTestId('account-merge-sent')).toBeInTheDocument();
    expect(fetchAuthSession).not.toHaveBeenCalled();
  });

  // ---------------------------------------------------------------- #1244 step 3

  it('an address that is already registered offers to merge that account', async () => {
    api.on('addAdditionalEmail', () => problem(409, 'ADDITIONAL_EMAIL_DUPLICATE'));
    const user = userEvent.setup();
    renderSection();

    await user.type(screen.getByTestId('email-add-input'), 'Work.Old@example.com');
    await user.click(screen.getByTestId('email-add-button'));
    await user.click(await screen.findByTestId('account-merge-send'));

    await waitFor(() => expect(api.calls('requestAccountMerge')).toHaveLength(1));
    expect(api.calls('requestAccountMerge')[0].body).toEqual({ email: 'Work.Old@example.com' });
    expect(await screen.findByTestId('account-merge-sent')).toHaveTextContent(
      'Work.Old@example.com'
    );
  });

  it('says so when the address is no other account with a login', async () => {
    api.on('addAdditionalEmail', () => problem(409, 'ADDITIONAL_EMAIL_DUPLICATE'));
    api.on('requestAccountMerge', () => problem(404));
    const user = userEvent.setup();
    renderSection();

    await user.type(screen.getByTestId('email-add-input'), 'someone@example.com');
    await user.click(screen.getByTestId('email-add-button'));
    await user.click(await screen.findByTestId('account-merge-send'));

    expect(await screen.findByTestId('account-merge-error')).toHaveTextContent(
      en('profile.emails.mergeNotFound')
    );
  });
});
