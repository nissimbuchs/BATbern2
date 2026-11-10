/**
 * MailComposer (Story 16.8b): one composer for newsletter, registrants, speakers, venue and (Story
 * 16.9) partners.
 *
 * The same steps for every audience (`docs/ux/mail-center-spec.md` → "Composer"): content source,
 * template, language, the audience's own fields, recipients, preview of one real recipient, where
 * replies go, and the footer with a test send to the organizers and the real send.
 *
 * Everything shown comes from the server: the preview is rendered by the code the send uses, so
 * what the organizer sees for recipient i is what recipient i gets (16.8a AC3). Audience specifics
 * live in `audiences/*`; this component has no audience `if`.
 *
 * Story 16.5: own text. The organizer writes subject and body (placeholder chips from the server),
 * may start from a template or save the text as one, and the preview shows the highlighted render.
 * Registrants: a second send of the same subject needs an explicit "Send again" (AC9).
 */
import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Skeleton,
  Snackbar,
  Stack,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material';
import axios from 'axios';
import { useTranslation } from 'react-i18next';
import { emailTemplateService } from '@/services/emailTemplateService';
import { useBreakpoints } from '@/hooks/useBreakpoints';
import { useDebounce } from '@/hooks/useDebounce';
import { useEmailTemplates } from '@/hooks/useEmailTemplates';
import { useMailPreview, useSendMail } from '@/hooks/useMailMessages/useMailMessages';
import type {
  ComposeAudience,
  MailComposeRequest,
  MailSendResponse,
  MailSendTarget,
} from '@/services/mailMessageService';
import { COMPOSER_AUDIENCES } from './audiences';
import { ComposerFooter } from './ComposerFooter';
import { ComposerPreview } from './ComposerPreview';
import { ComposerRecipients } from './ComposerRecipients';
import { OwnTextEditor, type BodySeed } from './OwnTextEditor';
import { SaveAsTemplateDialog } from './SaveAsTemplateDialog';
import {
  INITIAL_STATE,
  isOwnText,
  ownTextReady,
  type ComposerLocale,
  type ComposerState,
  type ContentSource,
} from './types';

const AUTOMATIC = '__automatic__';
const NOTES_DEBOUNCE_MS = 400;
const OWN_TEXT_DEBOUNCE_MS = 400;

interface MailComposerProps {
  eventCode: string;
  audience: ComposeAudience;
  /**
   * A real send was accepted; the page closes the composer and highlights the new row(s). Without
   * a result for a pseudo-template's own send (the partner meeting invite returns no mail key).
   */
  onSent: (result?: MailSendResponse) => void;
}

/** `ErrorResponse.details` as the mail endpoints fill it (Story 16.5). */
interface ErrorDetails {
  code?: string;
  placeholders?: string[];
  sentAt?: string;
}

function detailsOf(error: unknown): ErrorDetails | null {
  if (!axios.isAxiosError(error)) return null;
  const data = error.response?.data as { details?: ErrorDetails } | undefined;
  return data?.details ?? null;
}

/** 400 UNKNOWN_PLACEHOLDER: the placeholders the server named. */
function unknownPlaceholdersOf(error: unknown): string[] | null {
  const details = detailsOf(error);
  return details?.code === 'UNKNOWN_PLACEHOLDER' ? (details.placeholders ?? []) : null;
}

/** 409 ALREADY_SENT: when the same own-text subject went to the registrants. */
function alreadySentOf(error: unknown): string | null {
  const details = detailsOf(error);
  return details?.code === 'ALREADY_SENT' ? (details.sentAt ?? '') : null;
}

function formatSentAt(sentAt: string, language: string): string {
  const date = new Date(sentAt);
  if (!sentAt || Number.isNaN(date.getTime())) return sentAt;
  return date.toLocaleString(language, {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'Europe/Zurich',
  });
}

/** The server's message for a failed request, and whether it is a 409 the organizer must read. */
function describeError(error: unknown): { message: string; conflict: boolean } | null {
  if (!error) return null;
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string } | undefined;
    return {
      message: data?.message ?? error.message,
      conflict: error.response?.status === 409,
    };
  }
  return { message: error instanceof Error ? error.message : String(error), conflict: false };
}

export const MailComposer: React.FC<MailComposerProps> = ({ eventCode, audience, onSent }) => {
  const { t, i18n } = useTranslation('events');
  const { isMobile } = useBreakpoints();
  const adapter = COMPOSER_AUDIENCES[audience];
  const [state, setState] = useState<ComposerState>(() => ({
    ...INITIAL_STATE,
    ...adapter.initialState,
  }));
  const [confirming, setConfirming] = useState(false);
  const [testSentTo, setTestSentTo] = useState<number | null>(null);
  const send = useSendMail(eventCode);
  const [sendingTarget, setSendingTarget] = useState<MailSendTarget | null>(null);
  // Own text (Story 16.5).
  const [bodySeed, setBodySeed] = useState<BodySeed>({ html: '', version: 0 });
  const [resendSentAt, setResendSentAt] = useState<string | null>(null);
  const [startError, setStartError] = useState(false);
  const [saveOpen, setSaveOpen] = useState(false);
  const [savedKey, setSavedKey] = useState<string | null>(null);
  const [offeredVariables, setOfferedVariables] = useState<string[]>([]);
  const ownText = isOwnText(state);

  // Any change but stepping shows recipient 1 again, and drops a pending question or error.
  const update = (patch: Partial<ComposerState>) => {
    setState((s) => ({ ...s, recipientIndex: 0, ...patch }));
    setConfirming(false);
    setResendSentAt(null);
    send.reset();
  };

  const switchSource = (contentSource: ContentSource) => {
    // The editor is mounted again; it starts from the text written so far.
    if (contentSource === 'OWN_TEXT') {
      setBodySeed((seed) => ({ html: state.ownBodyHtml, version: seed.version + 1 }));
    }
    setStartError(false);
    update({ contentSource });
  };

  // ── templates ──────────────────────────────────────────────────────────────
  const templates = useEmailTemplates({ category: adapter.templateCategory, isLayout: false });
  const templateKeys = useMemo(() => {
    const keys = (templates.data ?? [])
      .map((tpl) => tpl.templateKey)
      .filter((key) => !adapter.templateFilter || adapter.templateFilter(key));
    return [...new Set(keys)];
  }, [templates.data, adapter]);
  // The preselected template is derived, not stored: the request is right from the first render.
  const pseudoTemplates = adapter.pseudoTemplates ?? [];
  const choices = [...pseudoTemplates.map((p) => p.key), ...templateKeys];
  const defaultKey =
    adapter.defaultTemplateKey && choices.includes(adapter.defaultTemplateKey)
      ? adapter.defaultTemplateKey
      : (choices[0] ?? null);
  const templateKey = state.templateKey ?? (adapter.allowsAutomatic ? null : defaultKey);
  // Own text never uses a pseudo-template (the partner meeting invite has no stored text).
  const pseudo = ownText ? undefined : pseudoTemplates.find((p) => p.key === templateKey);

  // ── preview ────────────────────────────────────────────────────────────────
  const venueNotes = useDebounce(state.venueNotes, NOTES_DEBOUNCE_MS);
  const ownSubject = useDebounce(state.ownSubject, OWN_TEXT_DEBOUNCE_MS);
  const ownBodyHtml = useDebounce(state.ownBodyHtml, OWN_TEXT_DEBOUNCE_MS);
  // Typed text the preview has not caught up with: no send until it has.
  const typing = ownText && (ownSubject !== state.ownSubject || ownBodyHtml !== state.ownBodyHtml);
  const requestJson =
    (templates.isLoading && !ownText) || pseudo
      ? null
      : JSON.stringify(
          adapter.toRequest({ ...state, templateKey, venueNotes, ownSubject, ownBodyHtml })
        );
  const request = useMemo<MailComposeRequest | null>(
    () => (requestJson ? (JSON.parse(requestJson) as MailComposeRequest | null) : null),
    [requestJson]
  );
  const preview = useMailPreview(eventCode, request);
  // A disabled query keeps its placeholder; without a request there is nothing to show.
  const shown = request ? preview.data : undefined;
  const updating = Boolean(shown) && (preview.isPlaceholderData || preview.isFetching);

  // The chips come from the server (AC6). Kept from the last answer: empty own text has no preview.
  const latestOffered = preview.data?.offeredVariables;
  useEffect(() => {
    if (latestOffered && latestOffered.length > 0) setOfferedVariables(latestOffered);
  }, [latestOffered]);

  // A 400 UNKNOWN_PLACEHOLDER from the send (or the preview) reads like the preview's red message.
  const unknownFromServer =
    unknownPlaceholdersOf(send.error) ?? (request ? unknownPlaceholdersOf(preview.error) : null);

  const canSend =
    Boolean(shown) &&
    !updating &&
    !typing &&
    !preview.isError &&
    !unknownFromServer &&
    shown!.unknownPlaceholders.length === 0;

  // AC9: the same own-text subject already went to the registrants (preview, or a 409 on send).
  const alreadySentAt = resendSentAt ?? (ownText ? (shown?.alreadySent?.sentAt ?? null) : null);

  const sendTo = (target: MailSendTarget) => {
    // The buttons stay enabled while a send runs (focus); a second send waits for the first.
    if (!request || send.isPending) return;
    const confirmResend = target === 'RECIPIENTS' && alreadySentAt !== null;
    setSendingTarget(target);
    send.mutate(
      { ...request, target, confirmResend },
      {
        onSuccess: (result) => {
          if (target === 'ORGANIZERS') setTestSentTo(result.recipientCount);
          else onSent(result);
        },
        onSettled: (_result, err) => {
          setSendingTarget(null);
          const sentAt = target === 'RECIPIENTS' && !confirmResend ? alreadySentOf(err) : null;
          if (sentAt !== null) {
            // Ask instead of showing an error; only "Send again" sends.
            setResendSentAt(sentAt);
            setConfirming(true);
            send.reset();
          } else {
            setConfirming(false);
          }
        },
      }
    );
  };

  // AC7: copy the template's subject and body (current language) into own text; it stays as is.
  const startingTemplateKey = templateKey ?? shown?.templateKey ?? null;
  const startFromTemplate = async () => {
    if (!startingTemplateKey) return;
    setStartError(false);
    try {
      const template = await emailTemplateService.getTemplate(startingTemplateKey, state.locale);
      setBodySeed((seed) => ({ html: template.htmlBody, version: seed.version + 1 }));
      update({
        contentSource: 'OWN_TEXT',
        ownSubject: (template.subject ?? '').replace(/[\r\n]+/g, ' '),
        ownBodyHtml: template.htmlBody,
      });
    } catch {
      setStartError(true);
    }
  };

  const error = unknownFromServer
    ? null
    : (describeError(send.error) ?? (request ? describeError(preview.error) : null));
  const ExtraFields = adapter.ExtraFields;
  const showLanguage = adapter.organizerPicksLanguage || ownText;

  // ── layout ─────────────────────────────────────────────────────────────────
  const form = (
    <Stack spacing={2}>
      <Box>
        <ToggleButtonGroup
          value={state.contentSource}
          exclusive
          size="small"
          onChange={(_e, value: ContentSource | null) => value && switchSource(value)}
          aria-label={t('eventPage.mail.composer.contentSource')}
        >
          <ToggleButton value="TEMPLATE" data-testid="composer-source-template">
            {t('eventPage.mail.composer.source.template')}
          </ToggleButton>
          <ToggleButton value="OWN_TEXT" data-testid="composer-source-own">
            {t('eventPage.mail.composer.source.ownText')}
          </ToggleButton>
        </ToggleButtonGroup>
      </Box>

      {ownText ? (
        <OwnTextEditor
          subject={state.ownSubject}
          onSubjectChange={(ownSubject) => update({ ownSubject })}
          bodySeed={bodySeed}
          onBodyChange={(ownBodyHtml) => update({ ownBodyHtml })}
          offeredVariables={offeredVariables}
          personalized={shown?.personalized ?? adapter.personalized}
        />
      ) : (
        <>
          <FormControl fullWidth size="small" disabled={templates.isLoading}>
            <InputLabel id={`composer-template-${audience}`}>
              {t('eventPage.mail.composer.template')}
            </InputLabel>
            <Select
              labelId={`composer-template-${audience}`}
              label={t('eventPage.mail.composer.template')}
              value={templateKey ?? (adapter.allowsAutomatic ? AUTOMATIC : '')}
              onChange={(e) =>
                update({ templateKey: e.target.value === AUTOMATIC ? null : e.target.value })
              }
              data-testid="composer-template-select"
            >
              {adapter.allowsAutomatic && (
                <MenuItem value={AUTOMATIC}>{t('eventPage.mail.composer.automatic')}</MenuItem>
              )}
              {pseudoTemplates.map((p) => (
                <MenuItem key={p.key} value={p.key}>
                  {t(p.labelKey)}
                </MenuItem>
              ))}
              {templateKeys.map((key) => (
                <MenuItem key={key} value={key}>
                  {key}
                </MenuItem>
              ))}
              {!adapter.allowsAutomatic && choices.length === 0 && (
                <MenuItem value="" disabled>
                  {t('eventPage.mail.composer.noTemplates')}
                </MenuItem>
              )}
            </Select>
          </FormControl>
          {adapter.allowsAutomatic && !templateKey && shown?.templateKey && (
            <Typography
              variant="caption"
              color="text.secondary"
              data-testid="composer-detected-template"
            >
              {t('eventPage.mail.composer.speakers.detected', {
                templateKey: shown.templateKey,
                name: shown.recipient.displayName,
              })}
            </Typography>
          )}
          {!pseudo && startingTemplateKey && (
            <Box>
              <Button
                size="small"
                onClick={() => void startFromTemplate()}
                sx={{ px: 0, textTransform: 'none' }}
                data-testid="composer-starting-point"
              >
                {t('eventPage.mail.composer.ownText.startingPoint')}
              </Button>
              {startError && (
                <Typography
                  variant="caption"
                  color="error"
                  component="div"
                  data-testid="composer-starting-point-error"
                >
                  {t('eventPage.mail.composer.ownText.startingPointFailed')}
                </Typography>
              )}
            </Box>
          )}
        </>
      )}

      {pseudo ? (
        <Typography variant="body2" color="text.secondary" data-testid="composer-fixed-text-hint">
          {t(pseudo.hintKey)}
        </Typography>
      ) : showLanguage ? (
        <Box>
          <Typography variant="subtitle2" id={`composer-language-${audience}`} sx={{ mb: 0.5 }}>
            {t('eventPage.mail.composer.language')}
          </Typography>
          <ToggleButtonGroup
            value={state.locale}
            exclusive
            size="small"
            onChange={(_e, value: ComposerLocale | null) => value && update({ locale: value })}
            aria-labelledby={`composer-language-${audience}`}
          >
            <ToggleButton value="de" data-testid="composer-locale-de">
              DE
            </ToggleButton>
            <ToggleButton value="en" data-testid="composer-locale-en">
              EN
            </ToggleButton>
          </ToggleButtonGroup>
          {ownText && !adapter.organizerPicksLanguage && (
            <Typography
              variant="caption"
              color="text.secondary"
              component="div"
              sx={{ mt: 0.5 }}
              data-testid="composer-as-written"
            >
              {t('eventPage.mail.composer.ownText.asWritten')}
            </Typography>
          )}
          {ownText && adapter.ownTextHintKey && (
            <Typography
              variant="caption"
              color="text.secondary"
              component="div"
              sx={{ mt: 0.5 }}
              data-testid="composer-own-text-hint"
            >
              {t(adapter.ownTextHintKey)}
            </Typography>
          )}
        </Box>
      ) : (
        <Typography variant="body2" color="text.secondary">
          {t('eventPage.mail.composer.ownLanguage')}
        </Typography>
      )}

      {ExtraFields && !pseudo && <ExtraFields state={state} onChange={update} />}

      {shown && !pseudo && (
        <ComposerRecipients preview={shown} personalized={adapter.personalized} />
      )}
    </Stack>
  );

  const previewPane = pseudo ? (
    <pseudo.Body eventCode={eventCode} onSent={() => onSent()} />
  ) : shown ? (
    <ComposerPreview
      preview={shown}
      personalized={adapter.personalized}
      updating={updating}
      onStep={(recipientIndex) => setState((s) => ({ ...s, recipientIndex }))}
      ownText={ownText}
    />
  ) : request && preview.isLoading ? (
    <Skeleton variant="rectangular" height={isMobile ? 240 : 520} />
  ) : (
    <Typography variant="body2" color="text.secondary" data-testid="composer-preview-incomplete">
      {t(
        ownText && !ownTextReady(state)
          ? 'eventPage.mail.composer.ownText.empty'
          : 'eventPage.mail.composer.preview.incomplete'
      )}
    </Typography>
  );

  return (
    <Box data-testid={`mail-composer-${audience}`}>
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: { xs: 'minmax(0, 1fr)', md: 'minmax(280px, 36%) minmax(0, 1fr)' },
          gap: 3,
          alignItems: 'start',
        }}
      >
        {form}
        <Box sx={{ minWidth: 0 }}>{previewPane}</Box>
      </Box>

      {unknownFromServer && (
        <Alert severity="error" sx={{ mt: 2 }} data-testid="composer-unknown-placeholders">
          {t('eventPage.mail.composer.ownText.unknownPlaceholders', {
            list: unknownFromServer.join(', '),
          })}
        </Alert>
      )}

      {error && (
        <Alert
          severity={error.conflict ? 'info' : 'error'}
          sx={{ mt: 2 }}
          data-testid="composer-error"
        >
          {error.message}
        </Alert>
      )}

      {!pseudo && (
        <ComposerFooter
          recipientCount={shown?.recipientCount ?? 0}
          disabled={!canSend}
          testPending={sendingTarget === 'ORGANIZERS'}
          sendPending={sendingTarget === 'RECIPIENTS'}
          sticky={isMobile}
          confirming={confirming}
          onConfirmingChange={setConfirming}
          onTest={() => sendTo('ORGANIZERS')}
          onSend={() => sendTo('RECIPIENTS')}
          onSaveAsTemplate={
            ownText && adapter.canSaveAsTemplate !== false ? () => setSaveOpen(true) : undefined
          }
          saveAsTemplateDisabled={!ownTextReady(state)}
          alreadySentOn={alreadySentAt !== null ? formatSentAt(alreadySentAt, i18n.language) : null}
        />
      )}

      {saveOpen && (
        <SaveAsTemplateDialog
          open
          category={adapter.templateCategory}
          subject={state.ownSubject}
          htmlBody={state.ownBodyHtml}
          initialLocale={state.locale}
          onClose={() => setSaveOpen(false)}
          onSaved={(key) => {
            setSaveOpen(false);
            setSavedKey(key);
          }}
        />
      )}
      <Snackbar
        open={savedKey !== null}
        autoHideDuration={5000}
        onClose={() => setSavedKey(null)}
        message={t('eventPage.mail.composer.saveTemplate.saved', { key: savedKey ?? '' })}
        data-testid="composer-saved-toast"
      />

      <Snackbar
        open={testSentTo !== null}
        autoHideDuration={5000}
        onClose={() => setTestSentTo(null)}
        message={t('eventPage.mail.composer.testSent', { count: testSentTo ?? 0 })}
        data-testid="composer-test-toast"
      />
    </Box>
  );
};

export default MailComposer;
