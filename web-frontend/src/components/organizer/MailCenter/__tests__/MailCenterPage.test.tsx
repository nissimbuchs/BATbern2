/**
 * MailCenterPage tests (Story 16.7 AC1, AC3, AC8, AC9): the sections and their empty lines, the
 * event on every open row, "Assign to event" on the no-event rows, "Not spam", the global "What
 * went out", and the full-screen dialog on mobile.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { MailCenterPage } from '../MailCenterPage';
import type { MailConversationSummary } from '@/services/mailConversationService';
import type { MailMessageSummary } from '@/services/mailMessageService';
import { aConversation, aMailSummary } from '@/test/fixtures';

const { conversations, messages, breakpoints } = vi.hoisted(() => ({
  conversations: {
    listConversations: vi.fn(),
    getConversationCounts: vi.fn(),
    getConversation: vi.fn(),
    patchConversation: vi.fn(),
  },
  messages: {
    listMails: vi.fn(),
    getMail: vi.fn(),
    markNotSpam: vi.fn(),
  },
  breakpoints: { isMobile: false, isTablet: false, isDesktop: true },
}));

vi.mock('@/services/mailConversationService', () => ({
  listConversations: (...a: unknown[]) => conversations.listConversations(...a),
  getConversationCounts: () => conversations.getConversationCounts(),
  getConversation: (...a: unknown[]) => conversations.getConversation(...a),
  patchConversation: (...a: unknown[]) => conversations.patchConversation(...a),
}));
vi.mock('@/services/mailMessageService', () => ({
  listMails: (...a: unknown[]) => messages.listMails(...a),
  getMail: (...a: unknown[]) => messages.getMail(...a),
  markNotSpam: (...a: unknown[]) => messages.markNotSpam(...a),
}));
vi.mock('@/hooks/useEvents', () => ({
  useEvents: () => ({
    data: {
      data: [
        { eventCode: 'BATbern60', eventNumber: 60, title: 'Sixty' },
        { eventCode: 'BATbern61', eventNumber: 61, title: 'Sixty-one' },
      ],
    },
  }),
}));
vi.mock('@/hooks/useBreakpoints', () => ({ useBreakpoints: () => breakpoints }));
vi.mock('@/hooks/useAuth/useAuth', () => ({ useAuth: () => ({ user: { username: 'me' } }) }));
vi.mock('@/hooks/useUserProfile/useUserProfile', () => ({
  useUserProfile: () => ({ userProfile: undefined }),
}));
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) =>
      opts && typeof opts === 'object'
        ? `${key}(${Object.entries(opts)
            .map(([k, v]) => `${k}=${String(v)}`)
            .join(',')})`
        : key,
    i18n: { language: 'en' },
  }),
}));

const pagination = (n: number) => ({
  page: 1,
  limit: 100,
  totalItems: n,
  totalPages: 1,
  hasNext: false,
  hasPrev: false,
});

function conv(overrides: Partial<MailConversationSummary>): MailConversationSummary {
  return {
    ...aConversation(),
    conversationKey: 'conv-1',
    eventCode: 'BATbern60',
    audience: 'SPEAKERS',
    subject: 'Frage zum Beamer',
    counterpartAddress: 'success+speaker@simulator.amazonses.com',
    counterpartName: 'A Speaker',
    status: 'OPEN',
    assigneeUsername: 'lead.organizer',
    via: 'batbern60-moderator@batbern.ch',
    lastMessageAt: '2026-11-02T10:00:00Z',
    messageCount: 1,
    ...overrides,
  };
}

function mail(overrides: Partial<MailMessageSummary>): MailMessageSummary {
  return {
    ...aMailSummary(),
    mailKey: 'general-5',
    sentAt: '2026-11-02T08:00:00Z',
    audience: 'PUBLIC',
    contentSource: 'OWN_TEXT',
    subject: 'You won',
    recipientCount: 0,
    deliveredCount: 0,
    failedCount: 0,
    messageCount: 1,
    test: false,
    direction: 'IN',
    fromAddress: 'success+spammer@simulator.amazonses.com',
    spam: true,
    ...overrides,
  };
}

/** Open list, no-event list, spam list and sent list answer by their parameters. */
function serve({
  open = [] as MailConversationSummary[],
  noEvent = [] as MailConversationSummary[],
  spam = [] as MailMessageSummary[],
  sent = [] as MailMessageSummary[],
} = {}) {
  conversations.listConversations.mockImplementation((params: { noEvent?: boolean }) =>
    Promise.resolve(
      params.noEvent
        ? { data: noEvent, pagination: pagination(noEvent.length) }
        : { data: open, pagination: pagination(open.length) }
    )
  );
  conversations.getConversationCounts.mockResolvedValue({
    open: open.filter((c) => c.status === 'OPEN').length,
    waiting: 0,
    noEvent: noEvent.length,
    spam: spam.length,
  });
  messages.listMails.mockImplementation((params: { spam?: boolean }) =>
    Promise.resolve(
      params.spam
        ? { data: spam, pagination: pagination(spam.length) }
        : { data: sent, pagination: pagination(sent.length) }
    )
  );
}

function renderPage(url = '/organizer/mail') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[url]}>
        <MailCenterPage />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe('MailCenterPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    breakpoints.isMobile = false;
  });

  it('should show one line per empty section when nothing is there', async () => {
    serve();
    renderPage();

    expect(await screen.findByTestId('mail-center-open-empty')).toHaveTextContent(
      'mailCenter.nothingOpen'
    );
    expect(await screen.findByTestId('mail-center-no-event-empty')).toBeInTheDocument();
    expect(screen.queryByRole('list')).not.toBeInTheDocument();
  });

  it('should ask for open and waiting conversations with an event, and name the event per row', async () => {
    serve({ open: [conv({}), conv({ conversationKey: 'conv-2', eventCode: 'BATbern61' })] });
    renderPage();

    expect(await screen.findByTestId('conversation-event-conv-2')).toHaveTextContent('BATbern61');
    expect(screen.getByTestId('conversation-event-conv-1')).toHaveTextContent('BATbern60');
    expect(conversations.listConversations).toHaveBeenCalledWith(
      expect.objectContaining({ status: ['OPEN', 'WAITING'], noEvent: false })
    );
  });

  it('should move a no-event conversation to an event when assigned (AC1)', async () => {
    serve({
      noEvent: [conv({ conversationKey: 'conv-9', eventCode: null, via: 'info@batbern.ch' })],
    });
    conversations.patchConversation.mockResolvedValue(conv({ conversationKey: 'conv-9' }));
    renderPage();

    const row = await screen.findByTestId('conversation-row-conv-9');
    const select = within(row).getByRole('combobox');
    fireEvent.mouseDown(select);
    fireEvent.click(await screen.findByRole('option', { name: /BATbern61/ }));

    await waitFor(() =>
      expect(conversations.patchConversation).toHaveBeenCalledWith('conv-9', {
        eventCode: 'BATbern61',
      })
    );
    // the lists refresh, so the row moves
    await waitFor(() =>
      expect(conversations.listConversations.mock.calls.length).toBeGreaterThan(2)
    );
  });

  it('should list spam collapsed with its count and clear it with "Not spam" (AC3)', async () => {
    serve({ spam: [mail({})] });
    messages.markNotSpam.mockResolvedValue({ message: mail({ spam: false }), recipients: [] });
    renderPage();

    const spam = await screen.findByTestId('mail-center-spam');
    await waitFor(() => expect(within(spam).getByText('1')).toBeInTheDocument());
    fireEvent.click(within(spam).getByText('mailCenter.spam'));
    fireEvent.click(await screen.findByTestId('spam-not-spam-general-5'));

    await waitFor(() => expect(messages.markNotSpam).toHaveBeenCalledWith('general-5'));
    expect(messages.listMails).toHaveBeenCalledWith(
      expect.objectContaining({ direction: 'IN', spam: true })
    );
  });

  it('should list what went out across events with the event on each row (AC9)', async () => {
    serve({
      sent: [
        mail({
          mailKey: 'BATbern60-3',
          direction: 'OUT',
          spam: false,
          eventCode: 'BATbern60',
          audience: 'REGISTRANTS',
          subject: 'Reminder',
          senderUsername: 'me',
          recipientCount: 2,
        }),
      ],
    });
    renderPage();

    fireEvent.click(await screen.findByText('mailCenter.whatWentOut'));
    expect(await screen.findByTestId('mail-event-BATbern60-3')).toHaveTextContent('BATbern60');
  });

  it('should open a linked conversation in a full-screen dialog on mobile', async () => {
    breakpoints.isMobile = true;
    serve({ noEvent: [conv({ conversationKey: 'conv-9', eventCode: null })] });
    conversations.getConversation.mockResolvedValue({
      conversation: conv({ conversationKey: 'conv-9', eventCode: null }),
      messages: [],
    });
    renderPage('/organizer/mail?conversation=conv-9');

    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    await waitFor(() => expect(conversations.getConversation).toHaveBeenCalledWith('conv-9'));
    expect(screen.getAllByRole('dialog')).toHaveLength(1);
  });
});
