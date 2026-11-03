/**
 * ConversationDetail tests (owner feedback 2026-11-03): the newest message first and open, older ones
 * collapsed to one line with sender, time and the start of the text; a click or "Show all" opens
 * them; the answer box stays below the conversation.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConversationDetail } from '../ConversationDetail';

const { getConversation } = vi.hoisted(() => ({ getConversation: vi.fn() }));

vi.mock('@/services/mailConversationService', () => ({
  getConversation: (...args: unknown[]) => getConversation(...args),
  patchConversation: vi.fn(),
  replyToConversation: vi.fn(),
  getConversationAttachmentDownloadUrl: vi.fn(),
}));
vi.mock('@/hooks/useAuth/useAuth', () => ({ useAuth: () => ({ user: { username: 'me' } }) }));
vi.mock('@/hooks/useUserProfile/useUserProfile', () => ({
  useUserProfile: () => ({ userProfile: undefined }),
}));
vi.mock('@/hooks/useEvents', () => ({ useEvents: () => ({ data: { data: [] } }) }));
vi.mock('@/components/shared/OrganizerSelect/OrganizerSelect', () => ({
  OrganizerSelect: () => <select data-testid="conversation-assignee" />,
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

function message(mailKey: string, sentAt: string, direction: 'IN' | 'OUT', body: string) {
  return {
    message: {
      mailKey,
      sentAt,
      audience: 'SPEAKERS',
      contentSource: 'OWN_TEXT',
      subject: 'Frage zum Beamer',
      recipientCount: direction === 'OUT' ? 1 : 0,
      deliveredCount: 0,
      failedCount: 0,
      messageCount: 1,
      test: false,
      direction,
      fromAddress: direction === 'IN' ? 'success+speaker@simulator.amazonses.com' : undefined,
      fromName: direction === 'IN' ? 'A Speaker' : undefined,
      senderUsername: direction === 'OUT' ? 'me' : undefined,
      attachments: [],
    },
    bodyHtml: `<p>${body}</p>`,
    recipients: [],
  };
}

function serve() {
  // The API sends oldest first.
  getConversation.mockResolvedValue({
    conversation: {
      conversationKey: 'conv-1',
      eventCode: 'BATbern60',
      audience: 'SPEAKERS',
      subject: 'Frage zum Beamer',
      counterpartAddress: 'success+speaker@simulator.amazonses.com',
      counterpartName: 'A Speaker',
      status: 'OPEN',
      assigneeUsername: 'me',
      via: 'batbern60-moderator@batbern.ch',
      lastMessageAt: '2026-11-03T09:00:00Z',
      messageCount: 3,
    },
    messages: [
      message('BATbern60-1', '2026-11-02T08:00:00Z', 'IN', 'Gibt es HDMI im Saal?'),
      message('BATbern60-2', '2026-11-02T12:00:00Z', 'OUT', 'Ja, HDMI und USB-C.'),
      message('BATbern60-3', '2026-11-03T09:00:00Z', 'IN', 'Danke, dann bringe ich den Adapter.'),
    ],
  });
}

function renderDetail() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <ConversationDetail conversationKey="conv-1" />
    </QueryClientProvider>
  );
  return client;
}

describe('ConversationDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    serve();
  });

  it('should list the newest message first', async () => {
    renderDetail();
    await screen.findByTestId('conversation-message-BATbern60-3');
    const keys = screen
      .getAllByTestId(/^conversation-message-BATbern60-\d$/)
      .map((el) => el.getAttribute('data-testid'));
    expect(keys).toEqual([
      'conversation-message-BATbern60-3',
      'conversation-message-BATbern60-2',
      'conversation-message-BATbern60-1',
    ]);
  });

  it('should open only the newest message and show older ones as one line with the start of the text', async () => {
    renderDetail();
    const newest = await screen.findByTestId('conversation-message-BATbern60-3');
    expect(newest.querySelector('iframe')).not.toBeNull();

    const older = screen.getByTestId('conversation-message-BATbern60-1');
    expect(older.querySelector('iframe')).toBeNull();
    expect(within(older).getByTestId('conversation-message-preview-BATbern60-1')).toHaveTextContent(
      'Gibt es HDMI im Saal?'
    );
    expect(older).toHaveTextContent('A Speaker');
  });

  it('should open an older message on click and close it again', async () => {
    renderDetail();
    const toggle = await screen.findByTestId('conversation-message-toggle-BATbern60-2');
    expect(toggle).toHaveAttribute('aria-expanded', 'false');

    fireEvent.click(toggle);
    expect(toggle).toHaveAttribute('aria-expanded', 'true');
    expect(
      screen.getByTestId('conversation-message-BATbern60-2').querySelector('iframe')
    ).not.toBeNull();

    fireEvent.click(toggle);
    expect(
      screen.getByTestId('conversation-message-BATbern60-2').querySelector('iframe')
    ).toBeNull();
  });

  it('should open every message with "Show all"', async () => {
    renderDetail();
    fireEvent.click(await screen.findByTestId('conversation-expand-all'));
    for (const key of ['BATbern60-1', 'BATbern60-2', 'BATbern60-3']) {
      expect(
        screen.getByTestId(`conversation-message-${key}`).querySelector('iframe')
      ).not.toBeNull();
    }
    expect(screen.queryByTestId('conversation-expand-all')).not.toBeInTheDocument();
  });

  it('should not offer "Show all" when there is only one message', async () => {
    getConversation.mockResolvedValue({
      ...(await getConversation()),
      messages: [message('BATbern60-1', '2026-11-02T08:00:00Z', 'IN', 'Hallo')],
    });
    renderDetail();
    await screen.findByTestId('conversation-message-BATbern60-1');
    expect(screen.queryByTestId('conversation-expand-all')).not.toBeInTheDocument();
  });

  it('should keep the answer box below the conversation', async () => {
    renderDetail();
    const oldest = await screen.findByTestId('conversation-message-BATbern60-1');
    const box = screen.getByTestId('conversation-reply-box');
    expect(oldest.compareDocumentPosition(box) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('should open a message that arrives while the conversation is open', async () => {
    const client = renderDetail();
    await screen.findByTestId('conversation-message-BATbern60-3');
    const current = await getConversation();
    getConversation.mockResolvedValue({
      ...current,
      messages: [
        ...current.messages,
        message('BATbern60-4', '2026-11-03T10:00:00Z', 'IN', 'Noch eine Frage.'),
      ],
    });
    await client.invalidateQueries();

    const arrived = await screen.findByTestId('conversation-message-BATbern60-4');
    expect(arrived.querySelector('iframe')).not.toBeNull();
    expect(
      screen.getByTestId('conversation-message-BATbern60-3').querySelector('iframe')
    ).toBeNull();
  });

  it('should leave quoted text out of the one-line preview and keep words apart', async () => {
    const current = await getConversation();
    getConversation.mockResolvedValue({
      ...current,
      messages: [
        {
          ...message('BATbern60-1', '2026-11-02T08:00:00Z', 'OUT', ''),
          bodyHtml:
            '<p>By Friday, thanks.</p><p>Anna</p><p>On 2.11.2026, A Speaker wrote:</p><blockquote>When?</blockquote>',
        },
        message('BATbern60-2', '2026-11-03T09:00:00Z', 'IN', 'Ok'),
      ],
    });
    renderDetail();
    const line = await screen.findByTestId('conversation-message-preview-BATbern60-1');
    expect(line).toHaveTextContent('By Friday, thanks. Anna On 2.11.2026, A Speaker wrote:');
    expect(line).not.toHaveTextContent('When?');
  });
});
