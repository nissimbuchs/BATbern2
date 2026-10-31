/**
 * MailDetail tests (Story 16.3 AC1/AC4): the mail as sent, failed recipients first. Desktop shows it
 * inline (MailSentList), mobile in the full-screen MailDetailDialog.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { render, screen, within, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MailDetail, MailDetailDialog } from '../MailDetail';

const { getMailMessage } = vi.hoisted(() => ({ getMailMessage: vi.fn() }));

vi.mock('@/services/mailMessageService', () => ({
  getMailMessage: (...args: unknown[]) => getMailMessage(...args),
}));
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: { count?: number } | string) =>
      typeof opts === 'object' && opts.count !== undefined ? `${key}:${opts.count}` : key,
    i18n: { language: 'en' },
  }),
}));

const message = {
  mailKey: 'BATbern60-4',
  sentAt: '2026-10-28T10:00:00Z',
  senderUsername: 'organizer1',
  audience: 'SPEAKERS' as const,
  templateKey: 'speaker-invitation',
  contentSource: 'TEMPLATE' as const,
  subject: 'Invitation',
  recipientCount: 2,
  deliveredCount: 1,
  failedCount: 1,
  messageCount: 1,
  test: false,
};

function renderWithQuery(ui: React.ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

function renderDetail(mailKey: string) {
  renderWithQuery(<MailDetail eventCode="BATbern60" mailKey={mailKey} />);
}

describe('MailDetail', () => {
  beforeEach(() => getMailMessage.mockReset());

  it('lists failed recipients first with their reason and renders the body sandboxed', async () => {
    getMailMessage.mockResolvedValue({
      message,
      bodyHtml: '<p>Hello</p>',
      recipients: [
        { address: 'success+anna@simulator.amazonses.com', kind: 'TO', status: 'DELIVERED' },
        {
          address: 'bounce+bob@simulator.amazonses.com',
          kind: 'CC',
          status: 'FAILED',
          failureReason: 'Permanent/NoEmail',
        },
      ],
    });
    renderDetail('BATbern60-4');

    const list = await screen.findByTestId('mail-detail-recipients');
    const items = within(list).getAllByRole('listitem');
    expect(items[0]).toHaveTextContent('bounce+bob@simulator.amazonses.com');
    expect(items[0]).toHaveTextContent('Permanent/NoEmail');
    expect(items[0]).toHaveTextContent('eventPage.mail.status.FAILED');
    expect(items[1]).toHaveTextContent('eventPage.mail.status.DELIVERED');

    const body = screen.getByTestId('mail-detail-body');
    expect(body).toHaveAttribute('sandbox', '');
    expect(body).toHaveAttribute('srcdoc', '<p>Hello</p>');
    expect(getMailMessage).toHaveBeenCalledWith('BATbern60', 'BATbern60-4');
  });

  it('says so when the text of an old newsletter was not recorded', async () => {
    getMailMessage.mockResolvedValue({
      message: { ...message, mailKey: 'newsletter-1', audience: 'NEWSLETTER', subject: null },
      bodyHtml: null,
      recipients: [],
    });
    renderDetail('newsletter-1');
    expect(await screen.findByText('eventPage.mail.bodyNotRecorded')).toBeInTheDocument();
  });

  it('mobile: shows the mail in a full-screen dialog titled with the subject', async () => {
    getMailMessage.mockResolvedValue({ message, bodyHtml: '<p>Hello</p>', recipients: [] });
    const onClose = vi.fn();
    renderWithQuery(
      <MailDetailDialog
        eventCode="BATbern60"
        mailKey="BATbern60-4"
        title="Invitation"
        onClose={onClose}
      />
    );
    const dialog = await screen.findByRole('dialog', { name: 'Invitation' });
    expect(await within(dialog).findByTestId('mail-detail-body')).toBeInTheDocument();
    fireEvent.click(within(dialog).getByRole('button', { name: 'common:actions.close' }));
    expect(onClose).toHaveBeenCalled();
  });

  it('mobile: the dialog stays closed and fetches nothing without a mail', () => {
    renderWithQuery(<MailDetailDialog eventCode="BATbern60" mailKey={null} onClose={vi.fn()} />);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(getMailMessage).not.toHaveBeenCalled();
  });
});
