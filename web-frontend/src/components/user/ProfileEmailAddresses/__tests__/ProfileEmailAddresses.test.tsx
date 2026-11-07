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
  useTranslation: () => ({ t: (key: string) => key }),
}));

vi.mock('@/services/api/userAccountApi', () => ({
  addAdditionalEmail: vi.fn(),
  deleteAdditionalEmail: vi.fn(),
  updateAdditionalEmail: vi.fn(),
  resendAdditionalEmailVerification: vi.fn(),
}));

import {
  addAdditionalEmail,
  deleteAdditionalEmail,
  updateAdditionalEmail,
  resendAdditionalEmailVerification,
} from '@/services/api/userAccountApi';
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
});
