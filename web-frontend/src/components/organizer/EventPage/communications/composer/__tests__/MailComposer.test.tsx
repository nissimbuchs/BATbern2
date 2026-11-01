/**
 * MailComposer tests (Story 16.8b, AC11, AC13, AC14, AC16).
 *
 * The composer talks to the real service layer; MSW answers the HTTP calls (preview, send,
 * templates, venue config), so the request bodies the backend would get are what is asserted.
 * Nothing leaves the test process.
 */
import { describe, it, expect, vi, beforeAll, afterAll, afterEach, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { setupServer } from 'msw/node';
import { http, HttpResponse } from 'msw';
import { MailComposer } from '../MailComposer';
import type {
  ComposeAudience,
  MailComposeRequest,
  MailPreviewResponse,
  MailSendRequest,
} from '@/services/mailMessageService';

const { breakpoints } = vi.hoisted(() => ({
  breakpoints: { isMobile: false, isTablet: false, isDesktop: true },
}));
vi.mock('@/hooks/useBreakpoints', () => ({ useBreakpoints: () => breakpoints }));
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown> | string) =>
      opts && typeof opts === 'object'
        ? `${key}(${Object.entries(opts)
            .filter(([k]) => k !== 'defaultValue')
            .map(([k, v]) => `${k}=${String(v)}`)
            .join(',')})`
        : key,
    i18n: { language: 'en' },
  }),
}));

// ── fake backend ──────────────────────────────────────────────────────────────

const TEMPLATES: Record<string, string[]> = {
  NEWSLETTER: ['newsletter-event', 'newsletter-special'],
  REGISTRANT_NOTICE: ['slides-online'],
  SPEAKER: [
    'speaker-invitation',
    'speaker-reminder-response-tier1',
    'speaker-reminder-response-tier2',
    'speaker-reminder-response-tier3',
    'speaker-reminder-content-tier1',
    'speaker-reminder-content-tier2',
    'speaker-reminder-content-tier3',
    'qna-new-questions',
  ],
  VENUE_COORDINATION: ['venue-timetable'],
};

const VENUE_CONFIG = JSON.stringify({
  venue: { salutation: 'Frau', name: 'Vera Venue', email: 'venue@example.com' },
  catering: { salutation: 'Herr', name: 'Carl Catering', email: 'catering@example.com' },
  coordinatorUsername: 'organizer1',
});

let previews: MailComposeRequest[] = [];
let sends: MailSendRequest[] = [];
let previewOverride: Partial<MailPreviewResponse> = {};
let sendStatus = 202;
let sendBody: unknown = null;

function previewFor(req: MailComposeRequest): MailPreviewResponse {
  const personalized = req.audience === 'REGISTRANTS' || req.audience === 'SPEAKERS';
  const count = personalized ? 3 : req.audience === 'VENUE' ? 1 : 120;
  return {
    subject: `Subject ${req.audience} #${req.recipientIndex}`,
    html: `<p>Hello recipient ${req.recipientIndex}</p>`,
    recipientIndex: req.recipientIndex,
    recipientCount: count,
    recipient: {
      displayName: `Person ${req.recipientIndex}`,
      address: `p${req.recipientIndex}@example.com`,
      cc: [],
    },
    sampleRecipients: Array.from({ length: Math.min(count, 8) }, (_, i) => ({
      displayName: `Person ${i}`,
    })),
    personalized,
    templateKey: req.templateKey ?? 'speaker-reminder-response-tier2',
    locale: req.locale,
    replyTo: 'batbern60-moderator@example.com',
    organizerCount: 2,
    skipped: [],
    unknownPlaceholders: [],
    ...previewOverride,
  };
}

const server = setupServer(
  http.get('*/email-templates', ({ request }) => {
    const category = new URL(request.url).searchParams.get('category') ?? '';
    return HttpResponse.json(
      (TEMPLATES[category] ?? []).flatMap((key) =>
        ['de', 'en'].map((locale) => ({ templateKey: key, locale, category, isLayout: false }))
      )
    );
  }),
  http.get('*/admin/settings/:key', () =>
    HttpResponse.json({ key: 'venue.coordination.config', value: VENUE_CONFIG })
  ),
  http.post('*/events/:code/mails/preview', async ({ request }) => {
    const body = (await request.json()) as MailComposeRequest;
    previews.push(body);
    return HttpResponse.json(previewFor(body));
  }),
  http.post('*/events/:code/mails', async ({ request }) => {
    const body = (await request.json()) as MailSendRequest;
    sends.push(body);
    if (sendStatus !== 202) return HttpResponse.json(sendBody, { status: sendStatus });
    return HttpResponse.json(
      sendBody ?? {
        mailKeys: ['BAT1-5'],
        target: body.target,
        recipientCount: body.target === 'ORGANIZERS' ? 2 : 3,
        skipped: [],
      },
      { status: 202 }
    );
  })
);

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterAll(() => server.close());
afterEach(() => server.resetHandlers());

beforeEach(() => {
  previews = [];
  sends = [];
  previewOverride = {};
  sendStatus = 202;
  sendBody = null;
  breakpoints.isMobile = false;
});

function renderComposer(audience: ComposeAudience) {
  const onSent = vi.fn();
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <MailComposer eventCode="BAT1" audience={audience} onSent={onSent} />
      </MemoryRouter>
    </QueryClientProvider>
  );
  return { onSent };
}

const lastPreview = () => previews[previews.length - 1];

async function waitForPreview() {
  return screen.findByTitle('eventPage.mail.composer.preview.frameTitle');
}

function chooseTemplate(name: string) {
  fireEvent.mouseDown(screen.getByRole('combobox', { name: /eventPage.mail.composer.template/ }));
  fireEvent.click(within(screen.getByRole('listbox')).getByRole('option', { name }));
}

// ── AC11: same steps everywhere ──────────────────────────────────────────────

describe('MailComposer: steps', () => {
  it.each<ComposeAudience>(['NEWSLETTER', 'REGISTRANTS', 'SPEAKERS', 'VENUE'])(
    '%s shows template, recipients, preview, reply note and both send buttons',
    async (audience) => {
      renderComposer(audience);
      const frame = await waitForPreview();
      expect(frame).toHaveAttribute('sandbox', '');
      expect(
        screen.getByRole('combobox', { name: /eventPage.mail.composer.template/ })
      ).toBeInTheDocument();
      expect(screen.getByTestId('composer-recipient-count')).toBeInTheDocument();
      expect(screen.getByTestId('composer-reply-note')).toHaveTextContent(
        'batbern60-moderator@example.com'
      );
      expect(screen.getByTestId('composer-test-button')).toBeEnabled();
      expect(screen.getByTestId('composer-send-button')).toBeEnabled();
      expect(lastPreview().audience).toBe(audience);
      expect(lastPreview().contentSource).toBe('TEMPLATE');
    }
  );

  it('offers Own text only as a disabled toggle until Story 16.5', async () => {
    renderComposer('NEWSLETTER');
    await waitForPreview();
    expect(screen.getByTestId('composer-source-own')).toBeDisabled();
    expect(screen.getByTestId('composer-source-template')).toHaveAttribute('aria-pressed', 'true');
  });

  it('newsletter preselects newsletter-event, lets the organizer pick the language, does not step', async () => {
    renderComposer('NEWSLETTER');
    await waitForPreview();
    expect(lastPreview().templateKey).toBe('newsletter-event');
    expect(lastPreview().locale).toBe('de');
    expect(screen.queryByTestId('composer-next')).not.toBeInTheDocument();
    expect(screen.getByTestId('composer-same-text')).toBeInTheDocument();

    fireEvent.click(screen.getByTestId('composer-locale-en'));
    await waitFor(() => expect(lastPreview().locale).toBe('en'));
  });

  it('shows the recipient count and up to eight name chips from the server', async () => {
    renderComposer('NEWSLETTER');
    await waitForPreview();
    expect(screen.getByTestId('composer-recipient-count')).toHaveTextContent('count=120');
    expect(screen.getAllByTestId('composer-sample-chip')).toHaveLength(8);
  });

  it('personalised audiences say each recipient gets their own language and hide the language switch', async () => {
    renderComposer('REGISTRANTS');
    await waitForPreview();
    expect(screen.queryByTestId('composer-locale-en')).not.toBeInTheDocument();
    expect(screen.getByText('eventPage.mail.composer.ownLanguage')).toBeInTheDocument();
  });

  it('‹ › steps through the recipients and shows (i/n)', async () => {
    renderComposer('REGISTRANTS');
    await waitForPreview();
    expect(screen.getByTestId('composer-position')).toHaveTextContent('index=1,count=3');
    expect(screen.getByTestId('composer-prev')).toBeDisabled();

    fireEvent.click(screen.getByTestId('composer-next'));
    await waitFor(() => expect(lastPreview().recipientIndex).toBe(1));
    await waitFor(() =>
      expect(screen.getByTestId('composer-position')).toHaveTextContent('index=2,count=3')
    );
    expect(screen.getByTestId('composer-recipient')).toHaveTextContent('p1@example.com');
    expect(screen.getByTestId('composer-next')).toHaveAccessibleName(
      'eventPage.mail.composer.preview.next'
    );
    expect(screen.getByTestId('composer-prev')).toHaveAccessibleName(
      'eventPage.mail.composer.preview.previous'
    );
  });

  it('stepping keeps focus on the button, so Esc still reaches the composer (AC16)', async () => {
    renderComposer('REGISTRANTS');
    await waitForPreview();
    const next = screen.getByTestId('composer-next');
    next.focus();
    fireEvent.click(next);
    // While the next preview loads the button stays enabled and focused.
    expect(next).toBeEnabled();
    expect(next).toHaveFocus();
    await waitFor(() => expect(lastPreview().recipientIndex).toBe(1));
    expect(next).toHaveFocus();
  });

  it('an unknown placeholder is shown and disables both send buttons', async () => {
    previewOverride = { unknownPlaceholders: ['{{speakerTitle}}'] };
    renderComposer('NEWSLETTER');
    await waitForPreview();
    expect(screen.getByTestId('composer-unknown-placeholders')).toHaveTextContent(
      '{{speakerTitle}}'
    );
    expect(screen.getByTestId('composer-test-button')).toBeDisabled();
    expect(screen.getByTestId('composer-send-button')).toBeDisabled();
  });

  it('lists who is left out, and why (AC14)', async () => {
    previewOverride = {
      skipped: [
        { displayName: 'Max Muted', reason: 'REMINDERS_DISABLED' },
        { displayName: 'Gil Ghost', reason: 'NO_EMAIL' },
      ],
    };
    renderComposer('SPEAKERS');
    await waitForPreview();
    const skipped = screen.getByTestId('composer-skipped');
    expect(skipped).toHaveTextContent('count=2');
    fireEvent.click(within(skipped).getByRole('button'));
    expect(await screen.findByText(/Max Muted/)).toHaveTextContent(
      'eventPage.mail.composer.skipped.reason.REMINDERS_DISABLED'
    );
    expect(screen.getByText(/Gil Ghost/)).toHaveTextContent(
      'eventPage.mail.composer.skipped.reason.NO_EMAIL'
    );
  });

  it('shows the preview error from the server', async () => {
    server.use(
      http.post('*/events/:code/mails/preview', () =>
        HttpResponse.json(
          { message: 'Nobody in this audience would get the mail' },
          { status: 400 }
        )
      )
    );
    renderComposer('REGISTRANTS');
    expect(await screen.findByTestId('composer-error')).toHaveTextContent(
      'Nobody in this audience would get the mail'
    );
    expect(screen.getByTestId('composer-send-button')).toBeDisabled();
  });
});

// ── AC13: sending ────────────────────────────────────────────────────────────

describe('MailComposer: sending', () => {
  it('test send goes to the organizers, toasts, and the composer stays open', async () => {
    const { onSent } = renderComposer('REGISTRANTS');
    await waitForPreview();
    fireEvent.click(screen.getByTestId('composer-next'));
    await waitFor(() => expect(lastPreview().recipientIndex).toBe(1));

    fireEvent.click(screen.getByTestId('composer-test-button'));

    expect(await screen.findByTestId('composer-test-toast')).toHaveTextContent(
      'eventPage.mail.composer.testSent(count=2)'
    );
    expect(sends).toHaveLength(1);
    expect(sends[0]).toMatchObject({
      audience: 'REGISTRANTS',
      target: 'ORGANIZERS',
      recipientIndex: 1,
      templateKey: 'slides-online',
    });
    expect(onSent).not.toHaveBeenCalled();
    expect(screen.getByTestId('composer-send-button')).toBeInTheDocument();
  });

  it('the test button keeps focus while its send runs', async () => {
    renderComposer('NEWSLETTER');
    await waitForPreview();
    const test = screen.getByTestId('composer-test-button');
    test.focus();
    fireEvent.click(test);
    expect(test).toBeEnabled();
    expect(test).toHaveFocus();
    await screen.findByTestId('composer-test-toast');
    expect(sends).toHaveLength(1);
  });

  it('send asks once inline, then sends to the recipients and reports the mail keys', async () => {
    const { onSent } = renderComposer('NEWSLETTER');
    await waitForPreview();

    fireEvent.click(screen.getByTestId('composer-send-button'));
    expect(screen.getByTestId('composer-confirm')).toHaveTextContent('count=120');
    expect(sends).toHaveLength(0);

    fireEvent.click(screen.getByTestId('composer-confirm-back'));
    expect(screen.queryByTestId('composer-confirm')).not.toBeInTheDocument();

    fireEvent.click(screen.getByTestId('composer-send-button'));
    fireEvent.click(screen.getByTestId('composer-confirm-yes'));

    await waitFor(() => expect(onSent).toHaveBeenCalledTimes(1));
    expect(onSent.mock.calls[0][0]).toMatchObject({ mailKeys: ['BAT1-5'], target: 'RECIPIENTS' });
    expect(sends[0]).toMatchObject({ audience: 'NEWSLETTER', target: 'RECIPIENTS' });
  });

  it('a 409 shows the reason inline, not a generic error (AC14)', async () => {
    sendStatus = 409;
    sendBody = { message: 'The slides-online notice was already sent for this event' };
    const { onSent } = renderComposer('REGISTRANTS');
    await waitForPreview();

    fireEvent.click(screen.getByTestId('composer-send-button'));
    fireEvent.click(screen.getByTestId('composer-confirm-yes'));

    const error = await screen.findByTestId('composer-error');
    expect(error).toHaveTextContent('The slides-online notice was already sent for this event');
    expect(onSent).not.toHaveBeenCalled();
  });
});

// ── audiences ────────────────────────────────────────────────────────────────

describe('MailComposer: audience fields', () => {
  it('speakers: Automatic plus exactly the six reminder templates', async () => {
    renderComposer('SPEAKERS');
    await waitForPreview();
    fireEvent.mouseDown(screen.getByRole('combobox', { name: /eventPage.mail.composer.template/ }));
    const options = within(screen.getByRole('listbox'))
      .getAllByRole('option')
      .map((o) => o.textContent);
    expect(options).toEqual([
      'eventPage.mail.composer.automatic',
      'speaker-reminder-response-tier1',
      'speaker-reminder-response-tier2',
      'speaker-reminder-response-tier3',
      'speaker-reminder-content-tier1',
      'speaker-reminder-content-tier2',
      'speaker-reminder-content-tier3',
    ]);
  });

  it('speakers automatic: sends the reminder type and shows the template detected for the speaker', async () => {
    renderComposer('SPEAKERS');
    await waitForPreview();
    expect(lastPreview()).toMatchObject({ templateKey: null, speakerReminderType: 'RESPONSE' });
    expect(screen.getByTestId('composer-detected-template')).toHaveTextContent(
      'speaker-reminder-response-tier2'
    );

    fireEvent.click(
      within(screen.getByTestId('composer-speakers-type-CONTENT')).getByRole('radio')
    );
    await waitFor(() => expect(lastPreview().speakerReminderType).toBe('CONTENT'));
  });

  it('speakers: choosing a content template switches the recipient hint', async () => {
    renderComposer('SPEAKERS');
    await waitForPreview();
    chooseTemplate('speaker-reminder-content-tier2');

    await waitFor(() =>
      expect(lastPreview()).toMatchObject({
        templateKey: 'speaker-reminder-content-tier2',
        speakerReminderType: null,
      })
    );
    expect(screen.getByTestId('composer-speakers-target')).toHaveTextContent(
      'eventPage.mail.composer.speakers.type.CONTENT'
    );
    expect(screen.queryByTestId('composer-speakers-type-RESPONSE')).not.toBeInTheDocument();
  });

  it('venue: role checkboxes go into the request; no role, no preview and no send', async () => {
    renderComposer('VENUE');
    await waitForPreview();
    expect(lastPreview()).toMatchObject({
      templateKey: 'venue-timetable',
      venueRecipients: ['VENUE', 'CATERING'],
    });

    fireEvent.click(screen.getByTestId('venue-coord-send-venue').querySelector('input')!);
    await waitFor(() => expect(lastPreview().venueRecipients).toEqual(['CATERING']));

    fireEvent.click(screen.getByTestId('venue-coord-send-catering').querySelector('input')!);
    await waitFor(() => expect(screen.getByTestId('composer-send-button')).toBeDisabled());
    expect(screen.getByTestId('composer-test-button')).toBeDisabled();
  });

  it('newsletter: the reminder switch goes into the request', async () => {
    renderComposer('NEWSLETTER');
    await waitForPreview();
    fireEvent.click(screen.getByTestId('composer-newsletter-reminder').querySelector('input')!);
    await waitFor(() => expect(lastPreview().newsletterReminder).toBe(true));
  });
});
