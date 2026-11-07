/**
 * #1244 — participants manage their addresses on /profile (public styling): primary address,
 * additional addresses with their state, add / remove / resend, and the copy of event mails per
 * confirmed address (owner decision 2026-11-07: event mails to the primary, copies opt-in).
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) =>
      opts ? `${key}:${JSON.stringify(opts)}` : key,
  }),
}));

const refreshToken = vi.fn();
vi.mock('@/services/auth/authService', () => ({
  authService: { refreshToken: () => refreshToken() },
}));

vi.mock('@/services/api/userAccountApi', () => ({
  addAdditionalEmail: vi.fn(),
  deleteAdditionalEmail: vi.fn(),
  updateAdditionalEmail: vi.fn(),
  resendAdditionalEmailVerification: vi.fn(),
  makeAdditionalEmailPrimary: vi.fn(),
  requestAccountMerge: vi.fn(),
}));

import {
  addAdditionalEmail,
  deleteAdditionalEmail,
  updateAdditionalEmail,
  resendAdditionalEmailVerification,
  makeAdditionalEmailPrimary,
  requestAccountMerge,
} from '@/services/api/userAccountApi';
import { AxiosError, AxiosHeaders } from 'axios';
import { ProfileEmailAddresses } from '../ProfileEmailAddresses';

const confirmed = {
  email: 'private@example.com',
  label: null,
  createdAt: '2026-11-01T10:00:00Z',
  verifiedAt: '2026-11-01T10:05:00Z',
  receiveEventMail: false,
};
const pending = {
  email: 'pending@example.com',
  label: null,
  createdAt: '2026-11-02T10:00:00Z',
  verifiedAt: null,
  receiveEventMail: false,
};

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
    vi.clearAllMocks();
  });

  it('shows the primary address and each additional address with its state', () => {
    renderSection();

    const primary = screen.getByTestId('email-row-primary');
    expect(within(primary).getByText('work@example.com')).toBeInTheDocument();
    expect(within(primary).getByText('profile.emails.primary')).toBeInTheDocument();

    const ok = screen.getByTestId('email-row-private@example.com');
    expect(within(ok).getByText('profile.emails.confirmed')).toBeInTheDocument();
    const waiting = screen.getByTestId('email-row-pending@example.com');
    expect(within(waiting).getByText('profile.emails.pending')).toBeInTheDocument();
  });

  it('offers the copy of event mails only for a confirmed address', () => {
    renderSection();

    expect(screen.getByTestId('event-copy-private@example.com')).toBeEnabled();
    expect(screen.getByTestId('event-copy-pending@example.com')).toBeDisabled();
    expect(screen.queryByTestId('event-copy-work@example.com')).not.toBeInTheDocument();
  });

  it('switches the copy of event mails on for one address', async () => {
    vi.mocked(updateAdditionalEmail).mockResolvedValue({ ...confirmed, receiveEventMail: true });
    const user = userEvent.setup();
    renderSection();

    await user.click(screen.getByTestId('event-copy-private@example.com'));

    await waitFor(() =>
      expect(updateAdditionalEmail).toHaveBeenCalledWith('private@example.com', {
        receiveEventMail: true,
      })
    );
  });

  it('adds an address and removes one', async () => {
    vi.mocked(addAdditionalEmail).mockResolvedValue({ ...pending, email: 'new@example.com' });
    vi.mocked(deleteAdditionalEmail).mockResolvedValue();
    const user = userEvent.setup();
    renderSection();

    await user.type(screen.getByTestId('email-add-input'), 'new@example.com');
    await user.click(screen.getByTestId('email-add-button'));
    await waitFor(() =>
      expect(addAdditionalEmail).toHaveBeenCalledWith({ email: 'new@example.com' })
    );

    await user.click(screen.getByTestId('email-remove-private@example.com'));
    expect(deleteAdditionalEmail).not.toHaveBeenCalled();
    await user.click(screen.getByTestId('email-remove-confirm-private@example.com'));
    await waitFor(() => expect(deleteAdditionalEmail).toHaveBeenCalledWith('private@example.com'));
  });

  it('resends the confirmation only for an unconfirmed address', async () => {
    vi.mocked(resendAdditionalEmailVerification).mockResolvedValue();
    const user = userEvent.setup();
    renderSection();

    expect(screen.queryByTestId('email-resend-private@example.com')).not.toBeInTheDocument();
    await user.click(screen.getByTestId('email-resend-pending@example.com'));
    await waitFor(() =>
      expect(resendAdditionalEmailVerification).toHaveBeenCalledWith('pending@example.com')
    );
  });

  it('explains which address logs in', () => {
    renderSection();
    expect(screen.getByText('profile.emails.loginHint')).toBeInTheDocument();
  });

  // ---------------------------------------------------------------- #1244 step 2

  it('offers "use as primary" only for a confirmed address, after a confirmation step', async () => {
    vi.mocked(makeAdditionalEmailPrimary).mockResolvedValue();
    refreshToken.mockResolvedValue({ success: true });
    const user = userEvent.setup();
    renderSection();

    expect(screen.queryByTestId('email-make-primary-pending@example.com')).not.toBeInTheDocument();
    await user.click(screen.getByTestId('email-make-primary-private@example.com'));
    expect(makeAdditionalEmailPrimary).not.toHaveBeenCalled();
    await user.click(screen.getByTestId('email-make-primary-confirm-private@example.com'));

    await waitFor(() =>
      expect(makeAdditionalEmailPrimary).toHaveBeenCalledWith('private@example.com')
    );
    await waitFor(() => expect(refreshToken).toHaveBeenCalled());
  });

  it('when another account logs in with the address, offers to merge it (step 3)', async () => {
    vi.mocked(makeAdditionalEmailPrimary).mockRejectedValue(conflict('EMAIL_HAS_OTHER_LOGIN'));
    vi.mocked(requestAccountMerge).mockResolvedValue();
    const user = userEvent.setup();
    renderSection();

    await user.click(screen.getByTestId('email-make-primary-private@example.com'));
    await user.click(screen.getByTestId('email-make-primary-confirm-private@example.com'));
    await user.click(await screen.findByTestId('account-merge-send'));

    await waitFor(() => expect(requestAccountMerge).toHaveBeenCalledWith('private@example.com'));
    expect(await screen.findByTestId('account-merge-sent')).toBeInTheDocument();
  });

  // ---------------------------------------------------------------- #1244 step 3

  it('an address that is already registered offers to merge that account', async () => {
    vi.mocked(addAdditionalEmail).mockRejectedValue(conflict('ADDITIONAL_EMAIL_DUPLICATE'));
    vi.mocked(requestAccountMerge).mockResolvedValue();
    const user = userEvent.setup();
    renderSection();

    await user.type(screen.getByTestId('email-add-input'), 'Work.Old@example.com');
    await user.click(screen.getByTestId('email-add-button'));
    await user.click(await screen.findByTestId('account-merge-send'));

    await waitFor(() => expect(requestAccountMerge).toHaveBeenCalledWith('Work.Old@example.com'));
    expect(await screen.findByTestId('account-merge-sent')).toHaveTextContent(
      'Work.Old@example.com'
    );
  });

  it('says so when the address is no other account with a login', async () => {
    vi.mocked(addAdditionalEmail).mockRejectedValue(conflict('ADDITIONAL_EMAIL_DUPLICATE'));
    vi.mocked(requestAccountMerge).mockRejectedValue(failure(404));
    const user = userEvent.setup();
    renderSection();

    await user.type(screen.getByTestId('email-add-input'), 'someone@example.com');
    await user.click(screen.getByTestId('email-add-button'));
    await user.click(await screen.findByTestId('account-merge-send'));

    expect(await screen.findByTestId('account-merge-error')).toHaveTextContent(
      'profile.emails.mergeNotFound'
    );
  });
});

function failure(status: number, data: Record<string, unknown> = {}) {
  return new AxiosError('failed', String(status), undefined, undefined, {
    status,
    statusText: '',
    data,
    headers: {},
    config: { headers: new AxiosHeaders() },
  });
}

function conflict(code: string) {
  return failure(409, { errorCode: code });
}
