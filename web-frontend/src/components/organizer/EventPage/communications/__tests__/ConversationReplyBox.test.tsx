/**
 * ConversationReplyBox tests (Story 16.6 AC1–AC3): Send disabled while empty or pending, the inline
 * confirm, "Mark as done", the 409 message, and the refresh after a send.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConversationReplyBox } from '../ConversationReplyBox';

const { replyToConversation, auth } = vi.hoisted(() => ({
  replyToConversation: vi.fn(),
  auth: { user: { username: 'anna.muster' } as { username: string } | null },
}));

vi.mock('@/services/mailConversationService', () => ({
  replyToConversation: (...args: unknown[]) => replyToConversation(...args),
}));
vi.mock('@/hooks/useAuth/useAuth', () => ({ useAuth: () => auth }));
vi.mock('@/hooks/useUserProfile/useUserProfile', () => ({
  useUserProfile: () => ({ userProfile: { firstName: 'Anna', lastName: 'Muster' } }),
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

function renderBox(client = new QueryClient({ defaultOptions: { queries: { retry: false } } })) {
  const invalidate = vi.spyOn(client, 'invalidateQueries');
  render(
    <QueryClientProvider client={client}>
      <ConversationReplyBox eventCode="BATbern60" conversationKey="conv-7" />
    </QueryClientProvider>
  );
  return { invalidate };
}

const sendButton = () => screen.getByTestId('conversation-reply-send');

describe('ConversationReplyBox', () => {
  beforeEach(() => {
    replyToConversation.mockReset();
  });

  it('should_disableSend_when_textEmpty', () => {
    renderBox();
    expect(sendButton()).toBeDisabled();
    fireEvent.change(screen.getByTestId('conversation-reply-text').querySelector('textarea')!, {
      target: { value: '   ' },
    });
    expect(sendButton()).toBeDisabled();
  });

  it('should_showSenderHint_when_rendered', () => {
    renderBox();
    expect(screen.getByTestId('conversation-reply-hint').textContent).toContain('name=Anna Muster');
  });

  it('should_confirmThenSend_withMarkDone_and_refresh', async () => {
    replyToConversation.mockResolvedValue({ message: { mailKey: 'BATbern60-9' }, recipients: [] });
    const { invalidate } = renderBox();
    fireEvent.change(screen.getByTestId('conversation-reply-text').querySelector('textarea')!, {
      target: { value: 'Bis Freitag, danke.' },
    });
    fireEvent.click(screen.getByTestId('conversation-reply-done'));
    fireEvent.click(sendButton());

    expect(replyToConversation).not.toHaveBeenCalled();
    fireEvent.click(screen.getByTestId('conversation-reply-confirm-yes'));

    await waitFor(() =>
      expect(replyToConversation).toHaveBeenCalledWith('conv-7', {
        text: 'Bis Freitag, danke.',
        markDone: true,
      })
    );
    await waitFor(() => expect(screen.getByTestId('conversation-reply-sent')).toBeInTheDocument());
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['conversations', 'conv-7'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['mails', 'BATbern60'] });
    expect(
      (
        screen
          .getByTestId('conversation-reply-text')
          .querySelector('textarea') as HTMLTextAreaElement
      ).value
    ).toBe('');
  });

  it('should_goBack_when_confirmCancelled', () => {
    renderBox();
    fireEvent.change(screen.getByTestId('conversation-reply-text').querySelector('textarea')!, {
      target: { value: 'Hallo' },
    });
    fireEvent.click(sendButton());
    fireEvent.click(screen.getByTestId('conversation-reply-confirm-back'));
    expect(replyToConversation).not.toHaveBeenCalled();
    expect(sendButton()).toBeEnabled();
  });

  it('should_showNoAddressMessage_when_409', async () => {
    replyToConversation.mockRejectedValue({ response: { status: 409 } });
    renderBox();
    fireEvent.change(screen.getByTestId('conversation-reply-text').querySelector('textarea')!, {
      target: { value: 'Hallo' },
    });
    fireEvent.click(sendButton());
    fireEvent.click(screen.getByTestId('conversation-reply-confirm-yes'));
    const alert = await screen.findByTestId('conversation-reply-error');
    expect(alert.textContent).toContain('eventPage.mail.conversations.reply.noAddress');
    expect(
      (
        screen
          .getByTestId('conversation-reply-text')
          .querySelector('textarea') as HTMLTextAreaElement
      ).value
    ).toBe('Hallo');
  });
});
