/**
 * MailComposer own text (Story 16.5, AC1, AC2, AC6–AC9, AC11, AC13).
 *
 * Same setup as MailComposer.test.tsx: the real service layer, the MSW harness (src/test/msw)
 * answering preview, send and the template store and checking each call against the spec, so the asserted request bodies are what the backend would get. TinyMCE does not
 * run in jsdom; it is replaced by a textarea whose editor handle inserts at the textarea's cursor,
 * the way `editor.insertContent` does. Nothing leaves the test process.
 */
import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import type { ResponseBody } from '@/test/msw/api';
import { MailComposer } from '../MailComposer';
import type {
  ComposeAudience,
  MailComposeRequest,
  MailPreviewResponse,
  MailSendRequest,
} from '@/services/mailMessageService';

vi.mock('@/hooks/useBreakpoints', () => ({
  useBreakpoints: () => ({ isMobile: false, isTablet: false, isDesktop: true }),
}));
vi.mock('../../PartnerMeetingsCompose', () => ({
  PartnerMeetingsCompose: () => <div data-testid="partner-meetings-compose-stub" />,
}));
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

// TinyMCE stand-in: a textarea; `insertContent` writes at its cursor and reports the change.
vi.mock('@tinymce/tinymce-react', () => ({
  Editor: ({
    initialValue,
    onEditorChange,
    onInit,
    init,
  }: {
    initialValue?: string;
    onEditorChange?: (html: string) => void;
    onInit?: (evt: unknown, editor: unknown) => void;
    init?: { setup?: (editor: unknown) => void };
  }) => {
    const ref = React.useRef<HTMLTextAreaElement>(null);
    const handlers = React.useRef<Record<string, () => void>>({});
    React.useEffect(() => {
      const el = ref.current!;
      const editor = {
        on: (name: string, fn: () => void) => {
          handlers.current[name] = fn;
        },
        insertContent: (text: string) => {
          const start = el.selectionStart;
          const end = el.selectionEnd;
          el.value = el.value.slice(0, start) + text + el.value.slice(end);
          onEditorChange?.(el.value);
        },
      };
      init?.setup?.(editor);
      onInit?.({}, editor);
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);
    return (
      <textarea
        ref={ref}
        data-testid="tinymce-editor"
        defaultValue={initialValue}
        onFocus={() => handlers.current.focus?.()}
        onChange={(e) => onEditorChange?.(e.target.value)}
      />
    );
  },
}));

// ── fake backend ──────────────────────────────────────────────────────────────

const TEMPLATES: Record<string, string[]> = {
  NEWSLETTER: ['newsletter-event'],
  REGISTRANT_NOTICE: ['slides-online'],
  SPEAKER: ['speaker-reminder-response-tier1'],
  VENUE_COORDINATION: ['venue-timetable'],
  PARTNER: ['partner-info'],
};

const STORED_TEMPLATE = {
  templateKey: 'slides-online',
  locale: 'de',
  category: 'REGISTRANT_NOTICE',
  subject: 'Folien online: {{eventTitle}}',
  htmlBody: '<p>Hallo {{recipientName}}</p>',
  isLayout: false,
  layoutKey: 'batbern-default',
  isSystemTemplate: true,
};

let previews: MailComposeRequest[] = [];
let sends: MailSendRequest[] = [];
let templateWrites: { method: string; url: string; body: unknown }[] = [];
let templateGets: string[] = [];
let previewOverride: Partial<MailPreviewResponse> = {};
let sendResponses: { status: number; body: Record<string, unknown> }[] = [];
let createStatus = 201;

function previewFor(req: MailComposeRequest): MailPreviewResponse {
  const personalized = req.audience === 'REGISTRANTS' || req.audience === 'SPEAKERS';
  const own = req.contentSource === 'OWN_TEXT';
  return {
    subject: own ? `${req.ownSubject}` : `Subject ${req.audience}`,
    html: own ? `<p>plain ${req.ownBodyHtml}</p>` : '<p>Template</p>',
    recipientIndex: req.recipientIndex,
    recipientCount: personalized ? 3 : 1,
    recipient: { displayName: 'Person 0', address: 'p0@example.com', cc: [] },
    sampleRecipients: [{ displayName: 'Person 0' }],
    personalized,
    templateKey: own ? null : (req.templateKey ?? 'speaker-reminder-response-tier1'),
    locale: req.locale,
    replyTo: null,
    organizerCount: 2,
    skipped: [],
    unknownPlaceholders: [],
    offeredVariables: personalized ? ['recipientName', 'eventTitle'] : ['eventTitle', 'eventDate'],
    allowedVariables: ['recipientName', 'eventTitle', 'eventDate'],
    highlightedSubject: own
      ? `&lt;b&gt;<mark data-var="eventTitle">A&amp;B</mark>&lt;/b&gt; <mark class="unknown">{{x}}</mark>`
      : null,
    highlightedHtml: own ? `<p>highlighted ${req.ownBodyHtml}</p>` : null,
    alreadySent: null,
    ...previewOverride,
  };
}

type TemplateCategory = ResponseBody<'listEmailTemplates', 200>[number]['category'];

function answerBackend() {
  api.on('listEmailTemplates', ({ query }) => {
    const category = (query.get('category') ?? '') as TemplateCategory;
    return (TEMPLATES[category] ?? []).flatMap((key) =>
      ['de', 'en'].map((locale) => ({
        templateKey: key,
        locale,
        category,
        isLayout: false,
        isSystemTemplate: true,
        htmlBody: '<p>Template</p>',
        updatedAt: '2026-05-14T16:00:00Z',
      }))
    );
  });
  api.on('getEmailTemplate', ({ params }) => {
    templateGets.push(`${params.templateKey}/${params.locale}`);
    return HttpResponse.json({ ...STORED_TEMPLATE, locale: params.locale });
  });
  api.on('createEmailTemplate', ({ body, url }) => {
    templateWrites.push({ method: 'POST', url: url.href, body });
    if (createStatus !== 201) {
      return HttpResponse.json({ message: 'exists' }, { status: createStatus });
    }
    return HttpResponse.json(body as Record<string, unknown>, { status: 201 });
  });
  api.on('updateEmailTemplate', ({ body, url }) => {
    templateWrites.push({ method: 'PUT', url: url.href, body });
    return HttpResponse.json({});
  });
  api.on('getAdminSetting', () => ({
    key: 'venue.coordination.config',
    value: JSON.stringify({
      venue: { salutation: 'Frau', name: 'Vera Venue', email: 'venue@example.com' },
      catering: { salutation: 'Herr', name: 'Carl Catering', email: 'catering@example.com' },
      coordinatorUsername: 'organizer1',
    }),
  }));
  api.on('previewEventMail', ({ body }) => {
    const req = body as MailComposeRequest;
    previews.push(req);
    return HttpResponse.json(previewFor(req));
  });
  api.on('sendEventMail', ({ body }) => {
    const req = body as MailSendRequest;
    sends.push(req);
    const next = sendResponses.shift();
    if (next) return HttpResponse.json(next.body, { status: next.status });
    return HttpResponse.json(
      { mailKeys: ['BAT1-9'], target: req.target, recipientCount: 3, skipped: [] },
      { status: 202 }
    );
  });
}

beforeEach(() => {
  answerBackend();
  previews = [];
  sends = [];
  templateWrites = [];
  templateGets = [];
  previewOverride = {};
  sendResponses = [];
  createStatus = 201;
});

function renderComposer(audience: ComposeAudience) {
  const onSent = vi.fn();
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
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
const ownPreviews = () => previews.filter((p) => p.contentSource === 'OWN_TEXT');

async function waitForPreview() {
  return screen.findByTitle('eventPage.mail.composer.preview.frameTitle');
}

const subjectInput = () => screen.getByTestId('composer-own-subject') as HTMLInputElement;
const bodyEditor = async () => (await screen.findByTestId('tinymce-editor')) as HTMLTextAreaElement;

/** Switch to own text, write subject and body, and wait for their preview. */
async function writeOwnText(subject: string, body: string) {
  fireEvent.click(screen.getByTestId('composer-source-own'));
  fireEvent.change(subjectInput(), { target: { value: subject } });
  fireEvent.change(await bodyEditor(), { target: { value: body } });
  await waitFor(() =>
    expect(lastPreview()).toMatchObject({
      contentSource: 'OWN_TEXT',
      ownSubject: subject,
      ownBodyHtml: body,
    })
  );
  await waitFor(() => expect(screen.getByTestId('composer-send-button')).toBeEnabled());
}

// ── toggle and request ───────────────────────────────────────────────────────

describe('MailComposer own text: toggle and request', () => {
  it('switches to own text: subject and body, no preview while empty (AC13)', async () => {
    renderComposer('NEWSLETTER');
    await waitForPreview();
    fireEvent.click(screen.getByTestId('composer-source-own'));

    expect(screen.getByTestId('composer-source-own')).toHaveAttribute('aria-pressed', 'true');
    expect(subjectInput()).toHaveAttribute('maxlength', '500');
    expect(await bodyEditor()).toBeInTheDocument();
    expect(
      screen.queryByRole('combobox', { name: /eventPage.mail.composer.template/ })
    ).not.toBeInTheDocument();
    expect(screen.getByTestId('composer-preview-incomplete')).toHaveTextContent(
      'eventPage.mail.composer.ownText.empty'
    );

    // Subject alone is not enough.
    fireEvent.change(subjectInput(), { target: { value: 'Room change' } });
    await new Promise((r) => setTimeout(r, 600));
    expect(ownPreviews()).toHaveLength(0);
    expect(screen.getByTestId('composer-send-button')).toBeDisabled();
  });

  it('sends contentSource OWN_TEXT with subject and body and no template key', async () => {
    const { onSent } = renderComposer('NEWSLETTER');
    await waitForPreview();
    await writeOwnText('Room change', '<p>We moved to room 2</p>');

    expect(lastPreview()).toMatchObject({
      audience: 'NEWSLETTER',
      contentSource: 'OWN_TEXT',
      templateKey: null,
      ownSubject: 'Room change',
      ownBodyHtml: '<p>We moved to room 2</p>',
    });

    fireEvent.click(screen.getByTestId('composer-send-button'));
    fireEvent.click(screen.getByTestId('composer-confirm-yes'));
    await waitFor(() => expect(onSent).toHaveBeenCalledTimes(1));
    expect(sends[0]).toMatchObject({
      contentSource: 'OWN_TEXT',
      templateKey: null,
      ownSubject: 'Room change',
      target: 'RECIPIENTS',
      confirmResend: false,
    });
  });

  it('shows the highlighted render, the highlighted subject and the token hint (AC1)', async () => {
    renderComposer('REGISTRANTS');
    await waitForPreview();
    await writeOwnText('Hi {{eventTitle}}', '<p>Dear {{recipientName}}</p>');

    const frame = screen.getByTitle('eventPage.mail.composer.preview.frameTitle');
    expect(frame).toHaveAttribute('sandbox', '');
    expect(frame.getAttribute('srcdoc')).toBe('<p>highlighted <p>Dear {{recipientName}}</p></p>');
    const subject = screen.getByTestId('composer-subject');
    // Parsed, not rendered as HTML: the escaped tags are text, the values styled spans.
    expect(subject).toHaveTextContent('<b>A&B</b> {{x}}');
    expect(subject.querySelector('b')).toBeNull();
    expect(subject.querySelector('mark')).toBeNull();
    expect(within(subject).getByText('A&B')).toHaveAttribute('data-var', 'eventTitle');
    expect(within(subject).getByText('{{x}}')).toHaveAttribute('data-unknown', 'true');
    expect(screen.getByTestId('composer-token-hint')).toBeInTheDocument();
  });

  it('registrants and speakers: the organizer picks the language and is told everyone gets the text as written', async () => {
    renderComposer('REGISTRANTS');
    await waitForPreview();
    expect(screen.queryByTestId('composer-locale-en')).not.toBeInTheDocument();
    fireEvent.click(screen.getByTestId('composer-source-own'));
    expect(screen.getByTestId('composer-as-written')).toHaveTextContent(
      'eventPage.mail.composer.ownText.asWritten'
    );
    fireEvent.click(screen.getByTestId('composer-locale-en'));
    fireEvent.change(subjectInput(), { target: { value: 'S' } });
    fireEvent.change(await bodyEditor(), { target: { value: '<p>B</p>' } });
    await waitFor(() =>
      expect(lastPreview()).toMatchObject({ contentSource: 'OWN_TEXT', locale: 'en' })
    );
  });

  it('newsletter: says the unsubscribe link is added', async () => {
    renderComposer('NEWSLETTER');
    await waitForPreview();
    fireEvent.click(screen.getByTestId('composer-source-own'));
    expect(screen.getByTestId('composer-own-text-hint')).toHaveTextContent(
      'eventPage.mail.composer.ownText.unsubscribeAdded'
    );
  });
});

// ── chips ────────────────────────────────────────────────────────────────────

describe('MailComposer own text: placeholder chips (AC6)', () => {
  it('shows the offered variables of the server, with the no-personal-fields hint where it applies', async () => {
    renderComposer('NEWSLETTER');
    await waitForPreview();
    fireEvent.click(screen.getByTestId('composer-source-own'));
    const chips = screen.getByTestId('composer-placeholder-chips');
    expect(within(chips).getByTestId('composer-chip-eventTitle')).toBeInTheDocument();
    expect(within(chips).getByTestId('composer-chip-eventDate')).toBeInTheDocument();
    expect(screen.getByTestId('composer-no-personal-fields')).toBeInTheDocument();
  });

  it('personalised audiences: no hint', async () => {
    renderComposer('REGISTRANTS');
    await waitForPreview();
    fireEvent.click(screen.getByTestId('composer-source-own'));
    expect(screen.getByTestId('composer-chip-recipientName')).toBeInTheDocument();
    expect(screen.queryByTestId('composer-no-personal-fields')).not.toBeInTheDocument();
  });

  it('inserts into the subject at the cursor when the subject had focus', async () => {
    renderComposer('REGISTRANTS');
    await waitForPreview();
    fireEvent.click(screen.getByTestId('composer-source-own'));
    await bodyEditor();
    const input = subjectInput();
    fireEvent.change(input, { target: { value: 'Hello , welcome' } });
    fireEvent.focus(input);
    input.setSelectionRange(6, 6);

    fireEvent.click(screen.getByTestId('composer-chip-recipientName'));

    expect(input.value).toBe('Hello {{recipientName}}, welcome');
    expect((await bodyEditor()).value).toBe('');
  });

  it('inserts into the body at the cursor when the body had focus', async () => {
    renderComposer('REGISTRANTS');
    await waitForPreview();
    fireEvent.click(screen.getByTestId('composer-source-own'));
    fireEvent.change(subjectInput(), { target: { value: 'Subject' } });
    fireEvent.focus(subjectInput());
    const editor = await bodyEditor();
    fireEvent.change(editor, { target: { value: 'Dear , see you' } });
    fireEvent.focus(editor);
    editor.setSelectionRange(5, 5);

    fireEvent.click(screen.getByTestId('composer-chip-recipientName'));

    expect(editor.value).toBe('Dear {{recipientName}}, see you');
    expect(subjectInput().value).toBe('Subject');
    await waitFor(() =>
      expect(lastPreview()).toMatchObject({ ownBodyHtml: 'Dear {{recipientName}}, see you' })
    );
  });
});

// ── unknown placeholders ─────────────────────────────────────────────────────

describe('MailComposer own text: unknown placeholders (AC2)', () => {
  it('the red message names them and both send buttons are disabled', async () => {
    renderComposer('SPEAKERS');
    await waitForPreview();
    previewOverride = { unknownPlaceholders: ['{{deadlin}}'] };
    fireEvent.click(screen.getByTestId('composer-source-own'));
    fireEvent.change(subjectInput(), { target: { value: 'Reminder' } });
    fireEvent.change(await bodyEditor(), { target: { value: '<p>By {{deadlin}}</p>' } });

    const message = await screen.findByTestId('composer-unknown-placeholders');
    expect(message).toHaveTextContent('eventPage.mail.composer.ownText.unknownPlaceholders');
    expect(message).toHaveTextContent('{{deadlin}}');
    expect(screen.getByTestId('composer-test-button')).toBeDisabled();
    expect(screen.getByTestId('composer-send-button')).toBeDisabled();
  });

  it('a 400 UNKNOWN_PLACEHOLDER from the send shows the same message and disables sending', async () => {
    sendResponses = [
      {
        status: 400,
        body: {
          message: 'Unknown placeholder',
          details: { code: 'UNKNOWN_PLACEHOLDER', placeholders: ['{{deadlin}}'] },
        },
      },
    ];
    renderComposer('NEWSLETTER');
    await waitForPreview();
    await writeOwnText('Subject', '<p>Body</p>');

    fireEvent.click(screen.getByTestId('composer-test-button'));

    const message = await screen.findByTestId('composer-unknown-placeholders');
    expect(message).toHaveTextContent('{{deadlin}}');
    expect(screen.queryByTestId('composer-error')).not.toBeInTheDocument();
    expect(screen.getByTestId('composer-test-button')).toBeDisabled();
    expect(screen.getByTestId('composer-send-button')).toBeDisabled();
  });
});

// ── starting point and save as template ──────────────────────────────────────

describe('MailComposer own text: starting point (AC7) and save as template (AC8)', () => {
  it('copies the template subject and body into own text and leaves the template unchanged', async () => {
    renderComposer('REGISTRANTS');
    await waitForPreview();

    fireEvent.click(screen.getByTestId('composer-starting-point'));

    await waitFor(() => expect(subjectInput().value).toBe('Folien online: {{eventTitle}}'));
    expect((await bodyEditor()).value).toBe('<p>Hallo {{recipientName}}</p>');
    expect(templateGets).toEqual(['slides-online/de']);
    await waitFor(() =>
      expect(lastPreview()).toMatchObject({
        contentSource: 'OWN_TEXT',
        templateKey: null,
        ownSubject: 'Folien online: {{eventTitle}}',
        ownBodyHtml: '<p>Hallo {{recipientName}}</p>',
      })
    );
    expect(templateWrites).toEqual([]);
  });

  it('is not offered for the partner meeting invite', async () => {
    renderComposer('PARTNERS');
    await screen.findByTestId('partner-meetings-compose-stub');
    await waitFor(() =>
      expect(
        screen.getByRole('combobox', { name: /eventPage.mail.composer.template/ })
      ).not.toHaveAttribute('aria-disabled', 'true')
    );
    expect(screen.queryByTestId('composer-starting-point')).not.toBeInTheDocument();
  });

  it('partners: own text replaces the meeting invite and previews one mail', async () => {
    renderComposer('PARTNERS');
    await screen.findByTestId('partner-meetings-compose-stub');
    fireEvent.click(screen.getByTestId('composer-source-own'));
    expect(screen.queryByTestId('partner-meetings-compose-stub')).not.toBeInTheDocument();
    fireEvent.change(subjectInput(), { target: { value: 'Partner news' } });
    fireEvent.change(await bodyEditor(), { target: { value: '<p>Hi</p>' } });
    await waitFor(() =>
      expect(lastPreview()).toMatchObject({
        audience: 'PARTNERS',
        contentSource: 'OWN_TEXT',
        templateKey: null,
      })
    );
  });

  it.each<[ComposeAudience, string]>([
    ['REGISTRANTS', 'REGISTRANT_NOTICE'],
    ['NEWSLETTER', 'NEWSLETTER'],
    ['VENUE', 'VENUE_COORDINATION'],
    ['PARTNERS', 'PARTNER'],
  ])(
    '%s: saves own text as a new %s template with the BATbern layout',
    async (audience, category) => {
      renderComposer(audience);
      // partners open on the meeting invite, which has no preview frame
      await (audience === 'PARTNERS'
        ? screen.findByTestId('partner-meetings-compose-stub')
        : waitForPreview());
      fireEvent.click(screen.getByTestId('composer-source-own'));
      expect(screen.getByTestId('composer-save-template-button')).toBeDisabled();
      await writeOwnText('Room change', '<p>Room 2</p>');

      fireEvent.click(screen.getByTestId('composer-save-template-button'));
      const dialog = await screen.findByTestId('composer-save-dialog');
      const key = within(dialog).getByTestId('composer-save-key');
      fireEvent.change(key, { target: { value: '!!!' } });
      expect(within(dialog).getByTestId('composer-save-confirm')).toBeDisabled();
      // a name as people type it; the key is derived and shown (owner 2026-11-04: "Room Change"
      // used to leave Save disabled, and the template was silently never stored)
      fireEvent.change(key, { target: { value: 'Room Change' } });
      expect(within(dialog).getByTestId('composer-save-key-preview')).toHaveTextContent(
        'room-change'
      );
      fireEvent.click(within(dialog).getByTestId('composer-save-locale-en'));
      fireEvent.click(within(dialog).getByTestId('composer-save-confirm'));

      await waitFor(() =>
        expect(screen.queryByTestId('composer-save-dialog')).not.toBeInTheDocument()
      );
      expect(templateWrites).toHaveLength(1);
      expect(templateWrites[0]).toMatchObject({
        method: 'POST',
        body: {
          templateKey: 'room-change',
          locale: 'en',
          category,
          isLayout: false,
          subject: 'Room change',
          htmlBody: '<p>Room 2</p>',
          layoutKey: 'batbern-default',
        },
      });
      expect(screen.getByTestId('composer-saved-toast')).toHaveTextContent('key=room-change');
    }
  );

  it('a key clash (409) is shown in the dialog', async () => {
    createStatus = 409;
    renderComposer('REGISTRANTS');
    await waitForPreview();
    await writeOwnText('Room change', '<p>Room 2</p>');

    fireEvent.click(screen.getByTestId('composer-save-template-button'));
    const dialog = await screen.findByTestId('composer-save-dialog');
    fireEvent.change(within(dialog).getByTestId('composer-save-key'), {
      target: { value: 'slides-online' },
    });
    fireEvent.click(within(dialog).getByTestId('composer-save-confirm'));

    expect(await within(dialog).findByTestId('composer-save-error')).toHaveTextContent(
      'eventPage.mail.composer.saveTemplate.duplicate'
    );
    expect(screen.getByTestId('composer-save-dialog')).toBeInTheDocument();
  });

  it('speakers: not offered (the speaker template list holds the six reminders only)', async () => {
    renderComposer('SPEAKERS');
    await waitForPreview();
    await writeOwnText('A note', '<p>Hi</p>');
    expect(screen.queryByTestId('composer-save-template-button')).not.toBeInTheDocument();
  });

  it('is not offered in template mode', async () => {
    renderComposer('REGISTRANTS');
    await waitForPreview();
    expect(screen.queryByTestId('composer-save-template-button')).not.toBeInTheDocument();
  });
});

// ── registrant guard ─────────────────────────────────────────────────────────

describe('MailComposer own text: registrant one-shot guard (AC9)', () => {
  it('asks "already sent, send again?" from the preview; only Send again sends, with confirmResend', async () => {
    previewOverride = { alreadySent: { sentAt: '2026-10-30T09:15:00Z', mailKey: 'BAT1-3' } };
    const { onSent } = renderComposer('REGISTRANTS');
    await waitForPreview();
    await writeOwnText('Slides online', '<p>Here</p>');

    fireEvent.click(screen.getByTestId('composer-send-button'));
    const question = screen.getByTestId('composer-already-sent');
    expect(question).toHaveTextContent('eventPage.mail.composer.ownText.alreadySent');
    expect(question).toHaveTextContent('2026');
    expect(screen.getByTestId('composer-confirm-yes')).toHaveTextContent(
      'eventPage.mail.composer.ownText.sendAgain'
    );
    expect(sends).toHaveLength(0);

    fireEvent.click(screen.getByTestId('composer-confirm-yes'));
    await waitFor(() => expect(onSent).toHaveBeenCalledTimes(1));
    expect(sends).toHaveLength(1);
    expect(sends[0]).toMatchObject({ target: 'RECIPIENTS', confirmResend: true });
  });

  it('a 409 ALREADY_SENT on send opens the same question; Send again resends with confirmResend', async () => {
    sendResponses = [
      {
        status: 409,
        body: {
          message: 'Already sent',
          details: { code: 'ALREADY_SENT', sentAt: '2026-10-30T09:15:00Z' },
        },
      },
    ];
    const { onSent } = renderComposer('REGISTRANTS');
    await waitForPreview();
    await writeOwnText('Slides online', '<p>Here</p>');

    fireEvent.click(screen.getByTestId('composer-send-button'));
    fireEvent.click(screen.getByTestId('composer-confirm-yes'));

    expect(await screen.findByTestId('composer-already-sent')).toBeInTheDocument();
    expect(screen.queryByTestId('composer-error')).not.toBeInTheDocument();
    expect(sends[0]).toMatchObject({ confirmResend: false });

    fireEvent.click(screen.getByTestId('composer-confirm-yes'));
    await waitFor(() => expect(onSent).toHaveBeenCalledTimes(1));
    expect(sends).toHaveLength(2);
    expect(sends[1]).toMatchObject({ target: 'RECIPIENTS', confirmResend: true });
  });

  it('the test send never carries confirmResend', async () => {
    previewOverride = { alreadySent: { sentAt: '2026-10-30T09:15:00Z', mailKey: 'BAT1-3' } };
    renderComposer('REGISTRANTS');
    await waitForPreview();
    await writeOwnText('Slides online', '<p>Here</p>');
    fireEvent.click(screen.getByTestId('composer-test-button'));
    await screen.findByTestId('composer-test-toast');
    expect(sends[0]).toMatchObject({ target: 'ORGANIZERS', confirmResend: false });
  });
});

// ── speakers ─────────────────────────────────────────────────────────────────

describe('MailComposer own text: speaker statuses (AC11)', () => {
  it('status chips go into the request; none chosen means no preview and no send', async () => {
    renderComposer('SPEAKERS');
    await waitForPreview();
    await writeOwnText('A note', '<p>Dear {{recipientName}}</p>');
    expect(lastPreview()).toMatchObject({
      audience: 'SPEAKERS',
      speakerReminderType: null,
      speakerStatuses: ['INVITED', 'ACCEPTED', 'CONTENT_SUBMITTED', 'QUALITY_REVIEWED'],
    });
    expect(screen.queryByTestId('composer-speakers-type-RESPONSE')).not.toBeInTheDocument();

    fireEvent.click(screen.getByTestId('composer-speakers-status-INVITED'));
    fireEvent.click(screen.getByTestId('composer-speakers-status-QUALITY_REVIEWED'));
    await waitFor(() =>
      expect(lastPreview().speakerStatuses).toEqual(['ACCEPTED', 'CONTENT_SUBMITTED'])
    );
    expect(screen.getByTestId('composer-speakers-status-ACCEPTED')).toHaveAttribute(
      'aria-pressed',
      'true'
    );

    fireEvent.click(screen.getByTestId('composer-speakers-status-ACCEPTED'));
    fireEvent.click(screen.getByTestId('composer-speakers-status-CONTENT_SUBMITTED'));
    expect(screen.getByTestId('composer-speakers-status-required')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByTestId('composer-send-button')).toBeDisabled());
    expect(screen.getByTestId('composer-test-button')).toBeDisabled();
  });

  it('template mode keeps the reminder fields', async () => {
    renderComposer('SPEAKERS');
    await waitForPreview();
    expect(screen.getByTestId('composer-speakers-type-RESPONSE')).toBeInTheDocument();
    expect(screen.queryByTestId('composer-speakers-status-INVITED')).not.toBeInTheDocument();
  });
});

// ── registrant statuses ──────────────────────────────────────────────────────

describe('MailComposer own text: registrant statuses', () => {
  it('defaults to registered, confirmed, attended; can go to the no-shows only; none chosen means no send', async () => {
    renderComposer('REGISTRANTS');
    await waitForPreview();
    await writeOwnText('Sorry we missed you', '<p>Dear {{recipientName}}</p>');
    expect(lastPreview()).toMatchObject({
      audience: 'REGISTRANTS',
      registrantStatuses: ['REGISTERED', 'CONFIRMED', 'ATTENDED'],
    });

    fireEvent.click(screen.getByTestId('composer-registrants-status-NO_SHOW'));
    fireEvent.click(screen.getByTestId('composer-registrants-status-REGISTERED'));
    fireEvent.click(screen.getByTestId('composer-registrants-status-CONFIRMED'));
    fireEvent.click(screen.getByTestId('composer-registrants-status-ATTENDED'));
    await waitFor(() => expect(lastPreview().registrantStatuses).toEqual(['NO_SHOW']));
    expect(screen.getByTestId('composer-registrants-status-NO_SHOW')).toHaveAttribute(
      'aria-pressed',
      'true'
    );

    fireEvent.click(screen.getByTestId('composer-registrants-status-NO_SHOW'));
    expect(screen.getByTestId('composer-registrants-status-required')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByTestId('composer-send-button')).toBeDisabled());
  });

  it('template mode offers no status choice and sends none', async () => {
    renderComposer('REGISTRANTS');
    await waitForPreview();
    expect(screen.queryByTestId('composer-registrants-status-NO_SHOW')).not.toBeInTheDocument();
    expect(lastPreview().registrantStatuses).toBeUndefined();
  });
});
