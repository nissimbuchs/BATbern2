/**
 * MailComposer (Story 16.8b): one composer for newsletter, registrants, speakers and venue.
 *
 * The same steps for every audience (`docs/ux/mail-center-spec.md` → "Composer"): content source,
 * template, language, the audience's own fields, recipients, preview of one real recipient, where
 * replies go, and the footer with a test send to the organizers and the real send.
 *
 * Everything shown comes from the server: the preview is rendered by the code the send uses, so
 * what the organizer sees for recipient i is what recipient i gets (16.8a AC3). Audience specifics
 * live in `audiences/*`; this component has no audience `if`.
 */
import React, { useMemo, useState } from 'react';
import {
  Alert,
  Box,
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
import { INITIAL_STATE, type ComposerLocale, type ComposerState } from './types';

const AUTOMATIC = '__automatic__';
const NOTES_DEBOUNCE_MS = 400;

interface MailComposerProps {
  eventCode: string;
  audience: ComposeAudience;
  /** A real send was accepted; the page closes the composer and highlights the new row(s). */
  onSent: (result: MailSendResponse) => void;
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
  const { t } = useTranslation('events');
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

  // Any change but stepping shows recipient 1 again, and drops a pending question or error.
  const update = (patch: Partial<ComposerState>) => {
    setState((s) => ({ ...s, recipientIndex: 0, ...patch }));
    setConfirming(false);
    send.reset();
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
  const defaultKey =
    adapter.defaultTemplateKey && templateKeys.includes(adapter.defaultTemplateKey)
      ? adapter.defaultTemplateKey
      : (templateKeys[0] ?? null);
  const templateKey = state.templateKey ?? (adapter.allowsAutomatic ? null : defaultKey);

  // ── preview ────────────────────────────────────────────────────────────────
  const venueNotes = useDebounce(state.venueNotes, NOTES_DEBOUNCE_MS);
  const requestJson = templates.isLoading
    ? null
    : JSON.stringify(adapter.toRequest({ ...state, templateKey, venueNotes }));
  const request = useMemo<MailComposeRequest | null>(
    () => (requestJson ? (JSON.parse(requestJson) as MailComposeRequest | null) : null),
    [requestJson]
  );
  const preview = useMailPreview(eventCode, request);
  // A disabled query keeps its placeholder; without a request there is nothing to show.
  const shown = request ? preview.data : undefined;
  const updating = Boolean(shown) && (preview.isPlaceholderData || preview.isFetching);

  const canSend =
    Boolean(shown) && !updating && !preview.isError && shown!.unknownPlaceholders.length === 0;

  const sendTo = (target: MailSendTarget) => {
    // The buttons stay enabled while a send runs (focus); a second send waits for the first.
    if (!request || send.isPending) return;
    setSendingTarget(target);
    send.mutate(
      { ...request, target },
      {
        onSuccess: (result) => {
          if (target === 'ORGANIZERS') setTestSentTo(result.recipientCount);
          else onSent(result);
        },
        onSettled: () => {
          setSendingTarget(null);
          setConfirming(false);
        },
      }
    );
  };

  const error = describeError(send.error) ?? (request ? describeError(preview.error) : null);
  const ExtraFields = adapter.ExtraFields;

  // ── layout ─────────────────────────────────────────────────────────────────
  const form = (
    <Stack spacing={2}>
      <Box>
        <ToggleButtonGroup
          value="TEMPLATE"
          exclusive
          size="small"
          aria-label={t('eventPage.mail.composer.contentSource')}
        >
          <ToggleButton value="TEMPLATE" data-testid="composer-source-template">
            {t('eventPage.mail.composer.source.template')}
          </ToggleButton>
          <ToggleButton value="OWN_TEXT" disabled data-testid="composer-source-own">
            {t('eventPage.mail.composer.source.ownText')}
          </ToggleButton>
        </ToggleButtonGroup>
        <Typography variant="caption" color="text.secondary" component="div" sx={{ mt: 0.5 }}>
          {t('eventPage.mail.composer.source.ownTextSoon')}
        </Typography>
      </Box>

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
          {templateKeys.map((key) => (
            <MenuItem key={key} value={key}>
              {key}
            </MenuItem>
          ))}
          {!adapter.allowsAutomatic && templateKeys.length === 0 && (
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
            name: shown.recipient?.displayName ?? '',
          })}
        </Typography>
      )}

      {adapter.organizerPicksLanguage ? (
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
        </Box>
      ) : (
        <Typography variant="body2" color="text.secondary">
          {t('eventPage.mail.composer.ownLanguage')}
        </Typography>
      )}

      {ExtraFields && <ExtraFields state={state} onChange={update} />}

      {shown && <ComposerRecipients preview={shown} personalized={adapter.personalized} />}
    </Stack>
  );

  const previewPane = shown ? (
    <ComposerPreview
      preview={shown}
      personalized={adapter.personalized}
      updating={updating}
      onStep={(recipientIndex) => setState((s) => ({ ...s, recipientIndex }))}
    />
  ) : request && preview.isLoading ? (
    <Skeleton variant="rectangular" height={isMobile ? 240 : 520} />
  ) : (
    <Typography variant="body2" color="text.secondary">
      {t('eventPage.mail.composer.preview.incomplete')}
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

      {error && (
        <Alert
          severity={error.conflict ? 'info' : 'error'}
          sx={{ mt: 2 }}
          data-testid="composer-error"
        >
          {error.message}
        </Alert>
      )}

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
