/**
 * MailSentList tests (Story 16.3): "What went out" list, filters, collapsed automatic mails, and
 * the open mail: inline below its row on desktop, a full-screen dialog on mobile.
 * Story 16.8b: the highlighted new row, "sending …", Retry on a PARTIAL/FAILED newsletter, and
 * the "show test sends" switch.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MailSentList } from '../MailSentList';
import type { MailMessageSummary } from '@/services/mailMessageService';

const { listMailMessages, getMailMessage, retryMail, breakpoints } = vi.hoisted(() => ({
  listMailMessages: vi.fn(),
  getMailMessage: vi.fn(),
  retryMail: vi.fn(),
  breakpoints: { isMobile: false, isTablet: false, isDesktop: true },
}));

vi.mock('@/services/mailMessageService', () => ({
  listMailMessages: (...args: unknown[]) => listMailMessages(...args),
  getMailMessage: (...args: unknown[]) => getMailMessage(...args),
  retryMail: (...args: unknown[]) => retryMail(...args),
}));
vi.mock('@/hooks/useBreakpoints', () => ({ useBreakpoints: () => breakpoints }));
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

function renderList(highlightKeys?: string[]) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <MailSentList eventCode="BATbern60" highlightKeys={highlightKeys} />
    </QueryClientProvider>
  );
}

function detailOf(summary: MailMessageSummary) {
  return { message: summary, bodyHtml: `<p>${summary.mailKey}</p>`, recipients: [] };
}

describe('MailSentList', () => {
  beforeEach(() => {
    listMailMessages.mockReset();
    getMailMessage
      .mockReset()
      .mockImplementation((_code: string, key: string) =>
        Promise.resolve(detailOf(row({ mailKey: key })))
      );
    breakpoints.isMobile = false;
  });

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

  it('desktop: opens a mail inline below its row, one at a time, and closes it on a second click', async () => {
    listMailMessages.mockResolvedValue(
      page([row({}), row({ mailKey: 'BATbern60-2', subject: 'Agenda' })])
    );
    renderList();
    const first = await screen.findByTestId('mail-row-BATbern60-1');

    fireEvent.click(first);
    expect(first).toHaveAttribute('aria-expanded', 'true');
    const inline = await screen.findByTestId('mail-inline-BATbern60-1');
    expect(await within(inline).findByTestId('mail-detail-body')).toHaveAttribute(
      'srcdoc',
      '<p>BATbern60-1</p>'
    );
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    fireEvent.click(screen.getByTestId('mail-row-BATbern60-2'));
    expect(await screen.findByTestId('mail-inline-BATbern60-2')).toBeInTheDocument();
    expect(first).toHaveAttribute('aria-expanded', 'false');
    await waitFor(() =>
      expect(screen.queryByTestId('mail-inline-BATbern60-1')).not.toBeInTheDocument()
    );

    fireEvent.click(screen.getByTestId('mail-row-BATbern60-2'));
    await waitFor(() =>
      expect(screen.queryByTestId('mail-inline-BATbern60-2')).not.toBeInTheDocument()
    );
  });

  it('mobile: opens a mail in a full-screen dialog titled with its subject', async () => {
    breakpoints.isMobile = true;
    listMailMessages.mockResolvedValue(page([row({})]));
    renderList();
    fireEvent.click(await screen.findByTestId('mail-row-BATbern60-1'));

    const dialog = await screen.findByRole('dialog', { name: 'Your slides, please' });
    expect(await within(dialog).findByTestId('mail-detail-body')).toBeInTheDocument();
    expect(screen.queryByTestId('mail-inline-BATbern60-1')).not.toBeInTheDocument();
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
    renderList();

    const item = await screen.findByTestId('mail-row-BATbern60-9');
    expect(item).toHaveTextContent('eventPage.mail.automatic');
    expect(item).toHaveTextContent(
      'eventPage.mail.groupTitle(template=registration-confirmation,count=14)'
    );

    fireEvent.click(item);
    expect(item).toHaveAttribute('aria-expanded', 'true');
    expect(getMailMessage).not.toHaveBeenCalled();
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
    expect(await screen.findByTestId('mail-inline-BATbern60-8')).toBeInTheDocument();
    expect(getMailMessage).toHaveBeenCalledWith('BATbern60', 'BATbern60-8');
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

  // ── Story 16.8b ─────────────────────────────────────────────────────────────

  it('highlights the row of a mail just sent, showing "sending …" until it has progress (AC13)', async () => {
    listMailMessages.mockResolvedValue(
      page([
        row({ mailKey: 'BATbern60-2', audience: 'NEWSLETTER', newsletterStatus: 'PENDING' }),
        row({}),
      ])
    );
    renderList(['BATbern60-2']);
    const fresh = await screen.findByTestId('mail-row-BATbern60-2');
    expect(fresh).toHaveAttribute('data-highlighted', 'true');
    expect(fresh).toHaveTextContent('eventPage.mail.sending');
    expect(screen.getByTestId('mail-row-BATbern60-1')).not.toHaveAttribute('data-highlighted');
  });

  it.each(['PARTIAL', 'FAILED'])(
    'offers Retry on a newsletter that ended %s (AC8)',
    async (status) => {
      retryMail.mockResolvedValue({
        mailKeys: ['BATbern60-1'],
        target: 'RECIPIENTS',
        recipientCount: 3,
        skipped: [],
      });
      listMailMessages.mockResolvedValue(
        page([row({ audience: 'NEWSLETTER', newsletterStatus: status, failedCount: 3 })])
      );
      renderList();
      fireEvent.click(await screen.findByTestId('mail-retry-BATbern60-1'));
      await waitFor(() => expect(retryMail).toHaveBeenCalledWith('BATbern60', 'BATbern60-1'));
      // The retry button does not also open the mail.
      expect(screen.queryByTestId('mail-inline-BATbern60-1')).not.toBeInTheDocument();
    }
  );

  it('offers no Retry on a completed newsletter or another audience', async () => {
    listMailMessages.mockResolvedValue(
      page([
        row({ mailKey: 'BATbern60-3', audience: 'NEWSLETTER', newsletterStatus: 'COMPLETED' }),
        row({ mailKey: 'BATbern60-4', audience: 'REGISTRANTS', newsletterStatus: 'PARTIAL' }),
      ])
    );
    renderList();
    await screen.findByTestId('mail-row-BATbern60-3');
    expect(screen.queryByTestId('mail-retry-BATbern60-3')).not.toBeInTheDocument();
    expect(screen.queryByTestId('mail-retry-BATbern60-4')).not.toBeInTheDocument();
  });

  it('hides test sends unless "show test sends" is switched on (AC9)', async () => {
    listMailMessages.mockResolvedValue(page([row({})]));
    renderList();
    await screen.findByTestId('mail-row-BATbern60-1');
    expect(listMailMessages).toHaveBeenLastCalledWith(
      'BATbern60',
      expect.objectContaining({ includeTests: false })
    );

    fireEvent.click(within(screen.getByTestId('mail-show-tests')).getByRole('switch'));

    await waitFor(() =>
      expect(listMailMessages).toHaveBeenLastCalledWith(
        'BATbern60',
        expect.objectContaining({ includeTests: true, page: 1 })
      )
    );
  });
});
