/**
 * DevEmailInboxPage Tests — Simulate Reply panel (Story 10.17 — AC11)
 */

import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import DevEmailInboxPage from './DevEmailInboxPage';
import * as devEmailServiceModule from '@/services/devEmailService';
import type { CapturedEmail } from '@/services/devEmailService';

vi.mock('@/services/devEmailService', () => ({
  devEmailService: {
    fetchAll: vi.fn(),
    clearAll: vi.fn(),
    replyToEmail: vi.fn(),
  },
}));

const MOCK_EMAIL: CapturedEmail = {
  id: 'abc-123',
  to: 'attendee@example.com',
  subject: 'BATbern42 Registration Confirmation',
  htmlBody: '<p>You are registered</p>',
  fromEmail: 'noreply@batbern.ch',
  fromName: 'BATbern Platform',
  capturedAt: new Date().toISOString(),
  attachments: [],
};

describe('DevEmailInboxPage — Simulate Reply panel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(devEmailServiceModule.devEmailService.fetchAll).mockResolvedValue([MOCK_EMAIL]);
    vi.mocked(devEmailServiceModule.devEmailService.replyToEmail).mockResolvedValue(
      'Reply routed: CANCEL'
    );
  });

  it('shows reply panel when an email is selected', async () => {
    render(<DevEmailInboxPage />);
    await waitFor(() => screen.getByText('BATbern42 Registration Confirmation'));

    fireEvent.click(screen.getByText('BATbern42 Registration Confirmation'));

    expect(screen.getByText('Simulate Reply')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'UNSUBSCRIBE' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'CANCEL' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'ACCEPT' })).toBeInTheDocument();
  });

  it('quick-fill button sets the textarea value', async () => {
    render(<DevEmailInboxPage />);
    await waitFor(() => screen.getByText('BATbern42 Registration Confirmation'));
    fireEvent.click(screen.getByText('BATbern42 Registration Confirmation'));

    fireEvent.click(screen.getByRole('button', { name: 'CANCEL' }));

    expect((screen.getByPlaceholderText('Reply body...') as HTMLInputElement).value).toBe('CANCEL');
  });

  it('sends reply and shows success message', async () => {
    render(<DevEmailInboxPage />);
    await waitFor(() => screen.getByText('BATbern42 Registration Confirmation'));
    fireEvent.click(screen.getByText('BATbern42 Registration Confirmation'));

    const textarea = screen.getByPlaceholderText('Reply body...');
    fireEvent.change(textarea, { target: { value: 'CANCEL' } });

    fireEvent.click(screen.getByRole('button', { name: 'Send Reply' }));

    await waitFor(() =>
      expect(devEmailServiceModule.devEmailService.replyToEmail).toHaveBeenCalledWith(
        'abc-123',
        'CANCEL'
      )
    );
    await waitFor(() => screen.getByText('Reply routed: CANCEL'));
  });

  it('shows error message when reply fails', async () => {
    vi.mocked(devEmailServiceModule.devEmailService.replyToEmail).mockRejectedValue(
      new Error('Reply failed: 404')
    );

    render(<DevEmailInboxPage />);
    await waitFor(() => screen.getByText('BATbern42 Registration Confirmation'));
    fireEvent.click(screen.getByText('BATbern42 Registration Confirmation'));

    const textarea = screen.getByPlaceholderText('Reply body...');
    fireEvent.change(textarea, { target: { value: 'CANCEL' } });
    fireEvent.click(screen.getByRole('button', { name: 'Send Reply' }));

    await waitFor(() => screen.getByText(/Reply failed: 404/));
  });

  it('Send Reply button is disabled when textarea is empty', async () => {
    render(<DevEmailInboxPage />);
    await waitFor(() => screen.getByText('BATbern42 Registration Confirmation'));
    fireEvent.click(screen.getByText('BATbern42 Registration Confirmation'));

    const sendButton = screen.getByRole('button', { name: 'Send Reply' });
    expect(sendButton).toBeDisabled();
  });
});
