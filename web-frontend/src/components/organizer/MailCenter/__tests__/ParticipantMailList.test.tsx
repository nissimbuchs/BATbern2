/**
 * ParticipantMailList tests (Story 16.10 AC1, AC4–AC8): the participant's mail in and out across
 * events, its delivery status, the conversation link of a received mail, no newsletter chip, and
 * the empty line.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { ParticipantMailList } from '../ParticipantMailList';
import type { MailMessageSummary } from '@/services/mailMessageService';
import { aMailSummary } from '@/test/fixtures';

const { messages, breakpoints } = vi.hoisted(() => ({
  messages: { listMails: vi.fn(), getMail: vi.fn() },
  breakpoints: { isMobile: false, isTablet: false, isDesktop: true },
}));

vi.mock('@/services/mailMessageService', () => ({
  listMails: (...a: unknown[]) => messages.listMails(...a),
  getMail: (...a: unknown[]) => messages.getMail(...a),
}));
vi.mock('@/hooks/useBreakpoints', () => ({ useBreakpoints: () => breakpoints }));
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) =>
      opts && typeof opts === 'object' && !Array.isArray(opts)
        ? `${key}(${Object.entries(opts)
            .map(([k, v]) => `${k}=${String(v)}`)
            .join(',')})`
        : key,
    i18n: { language: 'en' },
  }),
}));

const pagination = (n: number) => ({
  page: 1,
  limit: 20,
  totalItems: n,
  totalPages: 1,
  hasNext: false,
  hasPrev: false,
});

const base: MailMessageSummary = aMailSummary({
  mailKey: 'BATbern60-1',
  sentAt: '2026-11-01T08:00:00Z',
  audience: 'SPEAKERS',
  contentSource: 'OWN_TEXT',
  subject: 'Your talk',
  recipientCount: 1,
  deliveredCount: 1,
  failedCount: 0,
  messageCount: 1,
  test: false,
  direction: 'OUT',
  eventCode: 'BATbern60',
  senderUsername: 'lead.organizer',
  participantStatus: 'DELIVERED',
});

function renderList() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <ParticipantMailList participant={{ participantUsername: 'anna.example' }} />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe('ParticipantMailList', () => {
  beforeEach(() => vi.clearAllMocks());

  it('should ask for the participant mail in both directions, ungrouped', async () => {
    messages.listMails.mockResolvedValue({ data: [base], pagination: pagination(1) });
    renderList();

    expect(await screen.findByTestId('mail-event-BATbern60-1')).toHaveTextContent('BATbern60');
    expect(screen.getByTestId('mail-participant-status-BATbern60-1')).toHaveTextContent(
      'eventPage.mail.status.DELIVERED'
    );
    expect(messages.listMails).toHaveBeenCalledWith(
      expect.objectContaining({
        participantUsername: 'anna.example',
        direction: 'ANY',
        grouped: false,
      })
    );
    expect(screen.getByText('mailCenter.participant.limits')).toBeInTheDocument();
    expect(screen.queryByTestId('mail-filter-NEWSLETTER')).not.toBeInTheDocument();
  });

  it('should show a received mail with its sender and link to its conversation', async () => {
    const received: MailMessageSummary = {
      ...base,
      mailKey: 'general-7',
      direction: 'IN',
      eventCode: null,
      fromAddress: 'success+anna@simulator.amazonses.com',
      participantStatus: null,
      conversationKey: 'conv-7',
    };
    messages.listMails.mockResolvedValue({ data: [received], pagination: pagination(1) });
    messages.getMail.mockResolvedValue({
      message: received,
      bodyHtml: '<p>Hi</p>',
      recipients: [],
    });
    renderList();

    fireEvent.click(await screen.findByTestId('mail-row-general-7'));
    expect(screen.getByTestId('mail-event-general-7')).toHaveTextContent('mailCenter.noEventChip');
    expect(await screen.findByTestId('mail-detail-conversation-link')).toHaveAttribute(
      'href',
      '/organizer/mail?conversation=conv-7'
    );
    expect(messages.getMail).toHaveBeenCalledWith('general-7');
  });

  it('should show one line when there is no mail', async () => {
    messages.listMails.mockResolvedValue({ data: [], pagination: pagination(0) });
    renderList();

    expect(await screen.findByTestId('mail-sent-empty')).toHaveTextContent(
      'mailCenter.participant.empty'
    );
  });
});
