/**
 * MailSentList tests (Story 16.3): "What went out" list, filters, collapsed automatic mails.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MailSentList } from '../MailSentList';
import type { MailMessageSummary } from '@/services/mailMessageService';

const { listMailMessages } = vi.hoisted(() => ({ listMailMessages: vi.fn() }));

vi.mock('@/services/mailMessageService', () => ({
  listMailMessages: (...args: unknown[]) => listMailMessages(...args),
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

function row(overrides: Partial<MailMessageSummary>): MailMessageSummary {
  return {
    mailKey: 'BATbern60-1',
    sentAt: '2026-10-28T10:00:00Z',
    senderUsername: 'organizer1',
    audience: 'SPEAKERS',
    templateKey: 'speaker-reminder-content-1',
    contentSource: 'TEMPLATE',
    subject: 'Your slides, please',
    recipientCount: 2,
    deliveredCount: 1,
    failedCount: 1,
    messageCount: 1,
    test: false,
    ...overrides,
  };
}

function page(data: MailMessageSummary[], totalPages = 1) {
  return {
    data,
    pagination: {
      page: 1,
      limit: 20,
      totalItems: data.length,
      totalPages,
      hasNext: false,
      hasPrev: false,
    },
  };
}

function renderList(onOpen = vi.fn()) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <MailSentList eventCode="BATbern60" onOpen={onOpen} />
    </QueryClientProvider>
  );
  return { onOpen };
}

describe('MailSentList', () => {
  beforeEach(() => listMailMessages.mockReset());

  it('shows a one-line empty state when nothing went out', async () => {
    listMailMessages.mockResolvedValue(page([]));
    renderList();
    expect(await screen.findByTestId('mail-sent-empty')).toHaveTextContent(
      'eventPage.mail.emptyAll'
    );
  });

  it('shows sender, audience, source, subject and delivered/failed counts', async () => {
    listMailMessages.mockResolvedValue(page([row({})]));
    renderList();
    const item = await screen.findByTestId('mail-row-BATbern60-1');
    expect(item).toHaveTextContent('organizer1');
    expect(item).toHaveTextContent('eventPage.mail.filters.SPEAKERS');
    expect(item).toHaveTextContent('eventPage.mail.template');
    expect(item).toHaveTextContent('Your slides, please');
    expect(item).toHaveTextContent('eventPage.mail.delivered(count=1)');
    expect(item).toHaveTextContent('eventPage.mail.failed(count=1)');
  });

  it('opens a single mail on click', async () => {
    listMailMessages.mockResolvedValue(page([row({})]));
    const { onOpen } = renderList();
    fireEvent.click(await screen.findByTestId('mail-row-BATbern60-1'));
    expect(onOpen).toHaveBeenCalledWith('BATbern60-1');
  });

  it('filters by audience and shows the per-group empty state', async () => {
    listMailMessages.mockResolvedValueOnce(page([row({})])).mockResolvedValueOnce(page([]));
    renderList();
    await screen.findByTestId('mail-row-BATbern60-1');

    fireEvent.click(screen.getByTestId('mail-filter-VENUE'));

    expect(await screen.findByTestId('mail-sent-empty')).toHaveTextContent(
      'eventPage.mail.emptyFilter'
    );
    expect(listMailMessages).toHaveBeenLastCalledWith(
      'BATbern60',
      expect.objectContaining({ audience: 'VENUE', page: 1 })
    );
  });

  it('expands a collapsed automatic entry into its single mails', async () => {
    const collapsed = row({
      mailKey: 'BATbern60-9',
      senderUsername: null,
      audience: 'REGISTRANTS',
      templateKey: 'registration-confirmation',
      messageCount: 14,
      sentAt: '2026-10-28T23:30:00Z',
    });
    listMailMessages
      .mockResolvedValueOnce(page([collapsed]))
      .mockResolvedValueOnce(
        page([row({ mailKey: 'BATbern60-9' }), row({ mailKey: 'BATbern60-8' })])
      );
    const { onOpen } = renderList();

    const item = await screen.findByTestId('mail-row-BATbern60-9');
    expect(item).toHaveTextContent('eventPage.mail.automatic');
    expect(item).toHaveTextContent(
      'eventPage.mail.groupTitle(template=registration-confirmation,count=14)'
    );

    fireEvent.click(item);
    expect(onOpen).not.toHaveBeenCalled();
    await waitFor(() =>
      expect(listMailMessages).toHaveBeenLastCalledWith(
        'BATbern60',
        // 23:30 UTC is 00:30 the next day in Zurich (CET): the server groups by the Zurich day.
        expect.objectContaining({
          templateKey: 'registration-confirmation',
          date: '2026-10-29',
          grouped: false,
        })
      )
    );
    fireEvent.click(await screen.findByTestId('mail-row-BATbern60-8'));
    expect(onOpen).toHaveBeenCalledWith('BATbern60-8');
  });

  it('shows the progress of a newsletter that is still sending', async () => {
    listMailMessages.mockResolvedValue(
      page([
        row({
          audience: 'NEWSLETTER',
          newsletterStatus: 'IN_PROGRESS',
          recipientCount: 1060,
          deliveredCount: 400,
          failedCount: 2,
        }),
      ])
    );
    renderList();
    expect(await screen.findByTestId('mail-row-BATbern60-1')).toHaveTextContent(
      'eventPage.mail.sendingProgress(sent=402,total=1060)'
    );
  });
});
