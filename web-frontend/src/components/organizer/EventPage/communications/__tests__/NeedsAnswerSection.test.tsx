/**
 * NeedsAnswerSection tests (Story 16.2a AC9–AC12): the empty state, order and greyed DONE rows,
 * the badge that counts OPEN only, inline vs full-screen detail, status, assignee and "I'll take it".
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { NeedsAnswerSection } from '../NeedsAnswerSection';
import { initials } from '../initials';
import type { MailConversationSummary } from '@/services/mailConversationService';

const {
  listEventConversations,
  getConversation,
  patchConversation,
  getConversationAttachmentDownloadUrl,
  breakpoints,
  auth,
} = vi.hoisted(() => ({
  getConversationAttachmentDownloadUrl: vi.fn(),
  listEventConversations: vi.fn(),
  getConversation: vi.fn(),
  patchConversation: vi.fn(),
  breakpoints: { isMobile: false, isTablet: false, isDesktop: true },
  auth: { user: { username: 'me.organizer' } as { username: string } | null },
}));

vi.mock('@/services/mailConversationService', () => ({
  listEventConversations: (...args: unknown[]) => listEventConversations(...args),
  getConversation: (...args: unknown[]) => getConversation(...args),
  patchConversation: (...args: unknown[]) => patchConversation(...args),
  getConversationAttachmentDownloadUrl: (...args: unknown[]) =>
    getConversationAttachmentDownloadUrl(...args),
}));
vi.mock('@/hooks/useBreakpoints', () => ({ useBreakpoints: () => breakpoints }));
vi.mock('@/hooks/useAuth/useAuth', () => ({ useAuth: () => auth }));
vi.mock('@/hooks/useUserProfile/useUserProfile', () => ({
  useUserProfile: () => ({ userProfile: undefined }),
}));
vi.mock('@/components/shared/OrganizerSelect/OrganizerSelect', () => ({
  OrganizerSelect: ({ value, onChange }: { value: string; onChange: (v: string) => void }) => (
    <select
      data-testid="conversation-assignee"
      value={value}
      onChange={(e) => onChange(e.target.value)}
    >
      <option value="">unassigned</option>
      <option value="lead.organizer">lead.organizer</option>
      <option value="other.organizer">other.organizer</option>
    </select>
  ),
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

function conv(overrides: Partial<MailConversationSummary>): MailConversationSummary {
  return {
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

function page(data: MailConversationSummary[]) {
  return {
    data,
    pagination: {
      page: 1,
      limit: 100,
      totalItems: data.length,
      totalPages: 1,
      hasNext: false,
      hasPrev: false,
    },
  };
}

const detail = (status = 'OPEN', assigneeUsername: string | null = 'lead.organizer') => ({
  conversation: conv({ status: status as MailConversationSummary['status'], assigneeUsername }),
  messages: [
    {
      message: {
        mailKey: 'BATbern60-30',
        sentAt: '2026-11-02T09:00:00Z',
        audience: 'SPEAKERS',
        contentSource: 'OWN_TEXT',
        subject: 'Frage zum Beamer',
        recipientCount: 0,
        deliveredCount: 0,
        failedCount: 0,
        messageCount: 1,
        test: false,
        direction: 'IN',
        fromAddress: 'success+speaker@simulator.amazonses.com',
        fromName: 'A Speaker',
        attachments: [
          { name: 'slides.pdf', contentType: 'application/pdf', size: 12, kept: true },
          { name: 'video.mp4', contentType: 'video/mp4', size: 99999999, kept: false },
        ],
      },
      bodyHtml: '<p>HDMI?</p>',
      recipients: [],
    },
  ],
});

function renderSection(openKey: string | null = null, onOpenChange = vi.fn()) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const view = render(
    <QueryClientProvider client={queryClient}>
      <NeedsAnswerSection
        eventCode="BATbern60"
        openKey={openKey}
        onOpenChange={onOpenChange}
        headerAction={<button>new-mail</button>}
      />
    </QueryClientProvider>
  );
  return { onOpenChange, rerender: view.rerender, queryClient };
}

describe('NeedsAnswerSection', () => {
  beforeEach(() => {
    listEventConversations.mockReset();
    getConversation.mockReset().mockResolvedValue(detail());
    patchConversation.mockReset().mockResolvedValue(conv({}));
    breakpoints.isMobile = false;
    auth.user = { username: 'me.organizer' };
  });

  it('AC10: with no conversation it shows one line and no list', async () => {
    listEventConversations.mockResolvedValue(page([]));
    renderSection();
    expect(await screen.findByTestId('needs-answer-empty')).toHaveTextContent(
      'eventPage.mail.conversations.nothingOpen'
    );
    expect(screen.queryByTestId('needs-answer-list')).not.toBeInTheDocument();
    expect(screen.queryByTestId('needs-answer-badge')).not.toBeInTheDocument();
    expect(screen.getByText('new-mail')).toBeInTheDocument();
  });

  it('AC9: rows in server order, DONE greyed, the badge counts OPEN only', async () => {
    listEventConversations.mockResolvedValue(
      page([
        conv({ conversationKey: 'conv-1', status: 'OPEN' }),
        conv({ conversationKey: 'conv-2', status: 'WAITING' }),
        conv({ conversationKey: 'conv-4', status: 'OPEN', via: 'reply', messageCount: 3 }),
        conv({ conversationKey: 'conv-3', status: 'DONE' }),
      ])
    );
    renderSection();
    const list = await screen.findByTestId('needs-answer-list');
    const keys = within(list)
      .getAllByTestId(/^conversation-row-/)
      .map((el) => el.getAttribute('data-testid'));
    expect(keys).toEqual([
      'conversation-row-conv-1',
      'conversation-row-conv-2',
      'conversation-row-conv-4',
      'conversation-row-conv-3',
    ]);
    expect(screen.getByTestId('conversation-row-conv-3')).toHaveAttribute('data-status', 'DONE');
    expect(screen.getByTestId('conversation-row-conv-3')).toHaveStyle({ opacity: '0.55' });
    expect(screen.getByTestId('conversation-row-conv-1')).toHaveStyle({ opacity: '1' });
    expect(screen.getByTestId('needs-answer-badge')).toHaveTextContent('2');
    expect(screen.getByTestId('conversation-row-conv-4')).toHaveTextContent(
      'eventPage.mail.conversations.viaReply'
    );
    expect(screen.getByTestId('conversation-row-conv-1')).toHaveTextContent(
      'eventPage.mail.conversations.via(address=batbern60-moderator@batbern.ch)'
    );
    expect(screen.getAllByTestId('conversation-assignee-avatar')[0]).toHaveTextContent('LO');
  });

  it('16.2b AC9: a reply names the mail it answers; info@ shows its address', async () => {
    listEventConversations.mockResolvedValue(
      page([
        conv({ conversationKey: 'conv-5', via: 'reply', audience: 'REGISTRANTS' }),
        conv({ conversationKey: 'conv-6', via: 'reply', audience: 'NEWSLETTER' }),
        conv({ conversationKey: 'conv-7', via: 'info@batbern.ch', audience: 'PUBLIC' }),
      ])
    );
    renderSection();
    await screen.findByTestId('needs-answer-list');
    expect(screen.getByTestId('conversation-row-conv-5')).toHaveTextContent(
      'eventPage.mail.conversations.viaReplyRegistrants'
    );
    expect(screen.getByTestId('conversation-row-conv-6')).toHaveTextContent(
      'eventPage.mail.conversations.viaReplyNewsletter'
    );
    expect(screen.getByTestId('conversation-row-conv-7')).toHaveTextContent(
      'eventPage.mail.conversations.via(address=info@batbern.ch)'
    );
  });

  it('AC11 desktop: a click asks to open the row, the open row shows its messages inline', async () => {
    listEventConversations.mockResolvedValue(page([conv({})]));
    const { onOpenChange } = renderSection();
    fireEvent.click(await screen.findByTestId('conversation-open-conv-1'));
    expect(onOpenChange).toHaveBeenCalledWith('conv-1');
  });

  it('AC11: the open conversation shows every message with sender, body frame and attachment names', async () => {
    listEventConversations.mockResolvedValue(page([conv({})]));
    const { onOpenChange } = renderSection('conv-1');
    const inline = await screen.findByTestId('conversation-inline-conv-1');
    const message = await within(inline).findByTestId('conversation-message-BATbern60-30');
    expect(message).toHaveTextContent('A Speaker');
    expect(message.querySelector('iframe')).toHaveAttribute('sandbox', '');
    expect(within(message).getByTestId('conversation-attachments')).toHaveTextContent('slides.pdf');
    expect(within(message).getByTestId('conversation-attachments')).toHaveTextContent(
      'video.mp4 (eventPage.mail.conversations.nameOnly)'
    );
    // A second click closes it.
    fireEvent.click(screen.getByTestId('conversation-open-conv-1'));
    expect(onOpenChange).toHaveBeenCalledWith(null);
  });

  it('16.6: a kept attachment opens through its presigned URL; a failure shows an error', async () => {
    listEventConversations.mockResolvedValue(page([conv({})]));
    getConversationAttachmentDownloadUrl.mockResolvedValueOnce('https://s3.example.test/slides');
    const open = vi.spyOn(window, 'open').mockReturnValue(null);
    renderSection('conv-1');
    const link = await screen.findByTestId('conversation-attachment-BATbern60-30-0');
    fireEvent.click(link);
    await waitFor(() =>
      expect(open).toHaveBeenCalledWith(
        'https://s3.example.test/slides',
        '_blank',
        'noopener,noreferrer'
      )
    );
    expect(getConversationAttachmentDownloadUrl).toHaveBeenCalledWith('conv-1', 'BATbern60-30', 0);
    expect(screen.queryByTestId('conversation-attachment-BATbern60-30-1')).toBeNull();

    getConversationAttachmentDownloadUrl.mockRejectedValueOnce(new Error('404'));
    fireEvent.click(link);
    expect(await screen.findByTestId('conversation-attachment-error')).toBeInTheDocument();
    open.mockRestore();
  });

  it('AC11 mobile: the open conversation is a full-screen dialog, not inline', async () => {
    breakpoints.isMobile = true;
    listEventConversations.mockResolvedValue(page([conv({})]));
    renderSection('conv-1');
    const dialog = await screen.findByRole('dialog');
    expect(await within(dialog).findByTestId('conversation-detail')).toBeInTheDocument();
    expect(screen.queryByTestId('conversation-inline-conv-1')).not.toBeInTheDocument();
  });

  it('AC12: "I\'ll take it" on an unassigned row assigns me', async () => {
    listEventConversations.mockResolvedValue(page([conv({ assigneeUsername: null })]));
    renderSection();
    fireEvent.click(await screen.findByTestId('conversation-take-conv-1'));
    await waitFor(() =>
      expect(patchConversation).toHaveBeenCalledWith('conv-1', {
        assigneeUsername: 'me.organizer',
      })
    );
    expect(screen.queryByTestId('conversation-assignee-avatar')).not.toBeInTheDocument();
  });

  it('AC12: an assigned row offers no "I\'ll take it"', async () => {
    listEventConversations.mockResolvedValue(page([conv({})]));
    renderSection();
    await screen.findByTestId('conversation-row-conv-1');
    expect(screen.queryByTestId('conversation-take-conv-1')).not.toBeInTheDocument();
  });

  it('AC12: status and assignee changes are saved, "unassigned" clears it', async () => {
    listEventConversations.mockResolvedValue(page([conv({})]));
    renderSection('conv-1');
    const inline = await screen.findByTestId('conversation-inline-conv-1');
    await within(inline).findByTestId('conversation-detail');

    fireEvent.mouseDown(
      within(inline).getByRole('combobox', { name: 'eventPage.mail.conversations.statusLabel' })
    );
    fireEvent.click(await screen.findByTestId('conversation-status-WAITING'));
    await waitFor(() =>
      expect(patchConversation).toHaveBeenCalledWith('conv-1', { status: 'WAITING' })
    );

    fireEvent.change(within(inline).getByTestId('conversation-assignee'), {
      target: { value: 'other.organizer' },
    });
    await waitFor(() =>
      expect(patchConversation).toHaveBeenCalledWith('conv-1', {
        assigneeUsername: 'other.organizer',
      })
    );
    fireEvent.change(within(inline).getByTestId('conversation-assignee'), {
      target: { value: '' },
    });
    await waitFor(() =>
      expect(patchConversation).toHaveBeenCalledWith('conv-1', { clearAssignee: true })
    );
  });

  it('initials skip numeric suffixes', () => {
    expect(initials('werner.graf')).toBe('WG');
    expect(initials('daniel.iten.2')).toBe('DI');
    expect(initials('nissim')).toBe('N');
  });
});
