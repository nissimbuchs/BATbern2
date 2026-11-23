/**
 * MailCenterPage tests (Story 16.7 AC1, AC3, AC8, AC9): the sections and their empty lines, the
 * event on every open row, "Assign to event" on the no-event rows, "Not spam", the global "What
 * went out", the full-screen dialog on mobile, and "All conversations": every conversation that
 * came in, with or without event, done ones included.
 *
 * Real hooks, services and en i18n on the MSW harness; the user comes from `renderWithAuth`, the
 * mobile layout from a `window.matchMedia` stub (jsdom has none).
 */
import { describe, it, expect, afterEach } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { api } from '@/test/msw/api';
import { renderWithAuth, signedInAs } from '@/test/auth';
import { aConversation, aMailSummary } from '@/test/fixtures';
import type { MailConversationSummary } from '@/services/mailConversationService';
import type { MailMessageSummary } from '@/services/mailMessageService';
import { MailCenterPage } from '../MailCenterPage';

const pagination = (n: number, page = 1, limit = 100) => ({
  page,
  limit,
  totalItems: n,
  totalPages: Math.max(1, Math.ceil(n / limit)),
  hasNext: page * limit < n,
  hasPrev: page > 1,
});

function conv(overrides: Partial<MailConversationSummary>): MailConversationSummary {
  return aConversation({
    conversationKey: 'conv-1',
    eventCode: 'BATbern60',
    subject: 'Frage zum Beamer',
    counterpartName: 'A Speaker',
    status: 'OPEN',
    assigneeUsername: 'lead.organizer',
    via: 'batbern60-moderator@batbern.ch',
    lastMessageAt: '2026-11-02T10:00:00Z',
    ...overrides,
  });
}

function mail(overrides: Partial<MailMessageSummary>): MailMessageSummary {
  return aMailSummary({
    mailKey: 'general-5',
    sentAt: '2026-11-02T08:00:00Z',
    audience: 'PUBLIC',
    contentSource: 'OWN_TEXT',
    subject: 'You won',
    recipientCount: 0,
    deliveredCount: 0,
    direction: 'IN',
    fromAddress: 'success+spammer@simulator.amazonses.com',
    spam: true,
    ...overrides,
  });
}

/**
 * The page's lists answer by their parameters, as EMS does: `status` = the open list, `noEvent=true`
 * = the no-event list, neither = all conversations (filtered by `eventCode`, paged).
 */
function serve({
  open = [] as MailConversationSummary[],
  noEvent = [] as MailConversationSummary[],
  all = [] as MailConversationSummary[],
  spam = [] as MailMessageSummary[],
  sent = [] as MailMessageSummary[],
} = {}) {
  api.on('listConversations', ({ query }) => {
    if (query.getAll('status').length > 0) {
      return { data: open, pagination: pagination(open.length) };
    }
    if (query.get('noEvent') === 'true') {
      return { data: noEvent, pagination: pagination(noEvent.length) };
    }
    const eventCode = query.get('eventCode');
    const page = Number(query.get('page') ?? 1);
    const limit = Number(query.get('limit') ?? 100);
    const matching = all.filter((c) => !eventCode || c.eventCode === eventCode);
    return {
      data: matching.slice((page - 1) * limit, page * limit),
      pagination: pagination(matching.length, page, limit),
    };
  });
  api.on('getConversationCounts', () => ({
    open: open.filter((c) => c.status === 'OPEN').length,
    waiting: 0,
    noEvent: noEvent.length,
    spam: spam.length,
  }));
  api.on('listMails', ({ query }) =>
    query.get('spam') === 'true'
      ? { data: spam, pagination: pagination(spam.length) }
      : { data: sent, pagination: pagination(sent.length) }
  );
}

function renderPage(url = '/organizer/mail') {
  renderWithAuth(<MailCenterPage />, {
    auth: signedInAs('organizer', { username: 'me' }),
    initialEntries: [url],
  });
}

/** A `window.matchMedia` that evaluates min-/max-width queries for a viewport `width` px wide. */
const viewport = (width: number) => {
  window.matchMedia = ((query: string) => {
    const min = /min-width:\s*([\d.]+)px/.exec(query);
    const max = /max-width:\s*([\d.]+)px/.exec(query);
    const matches = (!min || width >= Number(min[1])) && (!max || width <= Number(max[1]));
    return {
      matches,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    };
  }) as typeof window.matchMedia;
};
const originalMatchMedia = window.matchMedia;

describe('MailCenterPage', () => {
  afterEach(() => {
    window.matchMedia = originalMatchMedia;
    localStorage.clear();
  });

  it('should collapse and expand every section from its header', async () => {
    const row = conv({ conversationKey: 'conv-9', eventCode: null });
    serve({ open: [conv({})], noEvent: [row], all: [row] });
    renderPage();

    for (const section of ['mail-center-open', 'mail-center-no-event', 'mail-center-all']) {
      // positive control: expanded by default
      expect(await screen.findByTestId(`${section}-list`)).toBeVisible();
      const header = screen.getByTestId(`${section}-toggle`);
      expect(header).toHaveAttribute('aria-expanded', 'true');
      await userEvent.click(header);
      expect(header).toHaveAttribute('aria-expanded', 'false');
      await waitFor(() => expect(screen.getByTestId(`${section}-list`)).not.toBeVisible());
      await userEvent.click(header);
      expect(header).toHaveAttribute('aria-expanded', 'true');
    }
    for (const section of ['mail-center-spam', 'mail-center-sent']) {
      expect(screen.getByTestId(`${section}-toggle`)).toHaveAttribute('aria-expanded', 'false');
    }
  });

  it('should remember a collapsed section for the next visit', async () => {
    serve({ open: [conv({})] });
    const { unmount } = renderWithAuth(<MailCenterPage />, {
      auth: signedInAs('organizer', { username: 'me' }),
      initialEntries: ['/organizer/mail'],
    });
    await screen.findByTestId('mail-center-open-list');
    await userEvent.click(screen.getByTestId('mail-center-open-toggle'));
    unmount();

    renderPage();
    expect(await screen.findByTestId('mail-center-open-toggle')).toHaveAttribute(
      'aria-expanded',
      'false'
    );
  });

  it('should show one line per empty section when nothing is there', async () => {
    serve();
    renderPage();

    expect(await screen.findByTestId('mail-center-open-empty')).toHaveTextContent(
      'Nothing needs an answer.'
    );
    expect(await screen.findByTestId('mail-center-no-event-empty')).toBeInTheDocument();
    expect(await screen.findByTestId('mail-center-all-empty')).toHaveTextContent(
      'No conversations yet.'
    );
    expect(screen.queryByRole('list')).not.toBeInTheDocument();
  });

  it('should ask for open and waiting conversations with an event, and name the event per row', async () => {
    serve({ open: [conv({}), conv({ conversationKey: 'conv-2', eventCode: 'BATbern61' })] });
    renderPage();

    const open = await screen.findByTestId('mail-center-open-list');
    expect(within(open).getByTestId('conversation-event-conv-2')).toHaveTextContent('BATbern61');
    expect(within(open).getByTestId('conversation-event-conv-1')).toHaveTextContent('BATbern60');
    const openCall = api
      .calls('listConversations')
      .find((call) => call.query.getAll('status').length > 0);
    expect(openCall?.query.getAll('status')).toEqual(['OPEN', 'WAITING']);
    expect(openCall?.query.get('noEvent')).toBe('false');
  });

  it('should list a done conversation of an event under all conversations', async () => {
    const done = conv({
      conversationKey: 'conv-61',
      eventCode: 'BATbern61',
      status: 'DONE',
      subject: 'Referent für FinOps',
      via: 'info@batbern.ch',
    });
    const withoutEvent = conv({ conversationKey: 'conv-9', eventCode: null, status: 'OPEN' });
    serve({ all: [done, withoutEvent], noEvent: [withoutEvent] });
    renderPage();

    const all = await screen.findByTestId('mail-center-all-list');
    // positive control: the open list does not have it, only "All conversations" does
    expect(await screen.findByTestId('mail-center-open-empty')).toBeInTheDocument();
    expect(within(all).getByTestId('conversation-row-conv-61')).toHaveAttribute(
      'data-status',
      'DONE'
    );
    expect(within(all).getByTestId('conversation-event-conv-61')).toHaveTextContent('BATbern61');
    expect(within(all).getByTestId('conversation-row-conv-9')).toBeInTheDocument();

    const allCall = api
      .calls('listConversations')
      .find(
        (call) => call.query.getAll('status').length === 0 && call.query.get('noEvent') === null
      );
    expect(allCall).toBeDefined();
  });

  it('should filter all conversations by event and page through them', async () => {
    const many = Array.from({ length: 30 }, (_, i) =>
      conv({
        conversationKey: `conv-${i}`,
        eventCode: i === 29 ? 'BATbern60' : 'BATbern62',
        status: 'DONE',
        lastMessageAt: `2026-10-${String((i % 28) + 1).padStart(2, '0')}T10:00:00Z`,
      })
    );
    serve({ all: many });
    renderPage();

    const all = await screen.findByTestId('mail-center-all-list');
    expect(within(all).getAllByRole('listitem')).toHaveLength(25);
    await userEvent.click(
      within(screen.getByTestId('mail-center-all')).getByRole('button', { name: /page 2/i })
    );
    await waitFor(() =>
      expect(
        within(screen.getByTestId('mail-center-all-list')).getAllByRole('listitem')
      ).toHaveLength(5)
    );

    await userEvent.click(within(screen.getByTestId('mail-center-all')).getByRole('combobox'));
    await userEvent.click(await screen.findByRole('option', { name: /BATbern60/ }));

    await waitFor(() =>
      expect(
        within(screen.getByTestId('mail-center-all-list')).getAllByRole('listitem')
      ).toHaveLength(1)
    );
    const last = api.calls('listConversations').at(-1);
    expect(last?.query.get('eventCode')).toBe('BATbern60');
    expect(last?.query.get('page')).toBe('1');
  });

  it('should move a no-event conversation to an event when assigned (AC1)', async () => {
    const row = conv({ conversationKey: 'conv-9', eventCode: null, via: 'info@batbern.ch' });
    serve({ noEvent: [row] });
    api.on('patchConversation', () => conv({ conversationKey: 'conv-9', eventCode: 'BATbern61' }));
    renderPage();

    const noEvent = await screen.findByTestId('mail-center-no-event-list');
    await userEvent.click(within(noEvent).getByRole('combobox'));
    const option = await screen.findByRole('option', { name: /BATbern61/ });
    const before = api.calls('listConversations').length;
    await userEvent.click(option);

    await waitFor(() => expect(api.calls('patchConversation')).toHaveLength(1));
    const patch = api.calls('patchConversation')[0];
    expect(patch.params.conversationKey).toBe('conv-9');
    expect(patch.body).toEqual({ eventCode: 'BATbern61' });
    // the lists refresh, so the row moves
    await waitFor(() => expect(api.calls('listConversations').length).toBeGreaterThan(before));
  });

  it('should list spam collapsed with its count and clear it with "Not spam" (AC3)', async () => {
    serve({ spam: [mail({})] });
    renderPage();

    const spam = await screen.findByTestId('mail-center-spam');
    await waitFor(() => expect(within(spam).getByText('1')).toBeInTheDocument());
    await userEvent.click(within(spam).getByText('Spam'));
    await userEvent.click(await screen.findByTestId('spam-not-spam-general-5'));

    await waitFor(() => expect(api.calls('patchMail')).toHaveLength(1));
    expect(api.calls('patchMail')[0].params.mailKey).toBe('general-5');
    expect(api.calls('patchMail')[0].body).toEqual({ spam: false });
    const spamCall = api.calls('listMails').find((call) => call.query.get('spam') === 'true');
    expect(spamCall?.query.get('direction')).toBe('IN');
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

    await userEvent.click(await screen.findByText('What went out (all events)'));
    expect(await screen.findByTestId('mail-event-BATbern60-3')).toHaveTextContent('BATbern60');
  });

  it('should open a linked conversation in a full-screen dialog on mobile', async () => {
    viewport(400);
    const row = conv({ conversationKey: 'conv-9', eventCode: null });
    serve({ noEvent: [row], all: [row] });
    api.on('getConversation', () => ({ conversation: row, messages: [] }));
    renderPage('/organizer/mail?conversation=conv-9');

    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    await waitFor(() => expect(api.calls('getConversation')).not.toHaveLength(0));
    expect(api.calls('getConversation')[0].params.conversationKey).toBe('conv-9');
    expect(screen.getAllByRole('dialog')).toHaveLength(1);
  });
});
