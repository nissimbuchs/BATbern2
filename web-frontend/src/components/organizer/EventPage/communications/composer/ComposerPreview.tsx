/**
 * Composer steps 5 and 6 (Story 16.8b): the mail as one real recipient gets it, rendered by the
 * same code the send uses (16.8a AC3), and where replies go.
 *
 * The body is shown in a sandboxed iframe without scripts, as in MailDetail. Personalised audiences
 * step through the recipients with ‹ › and "(i/n)". An unknown placeholder is shown in red; the
 * composer then disables both send buttons.
 *
 * Story 16.5: for own text the server's highlighted rendering is shown (filled values marked, an
 * unknown placeholder red). It is display only; what is sent is `html`/`subject`.
 */
import React from 'react';
import { Alert, Box, IconButton, LinearProgress, Stack, Typography } from '@mui/material';
import { ChevronLeft as PrevIcon, ChevronRight as NextIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import type { MailPreviewResponse } from '@/services/mailMessageService';
import { parseHighlightedSubject } from './highlightedSubject';

interface ComposerPreviewProps {
  preview: MailPreviewResponse;
  personalized: boolean;
  /** A newer preview is on its way; the one shown is the previous request's. */
  updating: boolean;
  onStep: (recipientIndex: number) => void;
  /** Story 16.5: own text shows the highlighted rendering and the redaction hint. */
  ownText?: boolean;
}

/** The highlighted subject as text with styled spans; parsed, never rendered as HTML. */
function renderHighlightedSubject(highlighted: string): React.ReactNode[] {
  return parseHighlightedSubject(highlighted).map((segment, i) =>
    segment.mark ? (
      <Box
        component="span"
        key={i}
        data-var={segment.mark.kind === 'var' ? segment.mark.name : undefined}
        data-unknown={segment.mark.kind === 'unknown' || undefined}
        sx={{
          bgcolor: segment.mark.kind === 'unknown' ? '#ffcdd2' : '#fff59d',
          color: segment.mark.kind === 'unknown' ? 'error.dark' : 'common.black',
          px: 0.25,
          borderRadius: 0.5,
        }}
      >
        {segment.text}
      </Box>
    ) : (
      <React.Fragment key={i}>{segment.text}</React.Fragment>
    )
  );
}

export const ComposerPreview: React.FC<ComposerPreviewProps> = ({
  preview,
  personalized,
  updating,
  onStep,
  ownText = false,
}) => {
  const { t } = useTranslation('events');
  const { recipient } = preview;
  const index = preview.recipientIndex;
  const stepping = personalized && preview.recipientCount > 1;
  const html = (ownText && preview.highlightedHtml) || preview.html;
  const subject =
    ownText && preview.highlightedSubject
      ? renderHighlightedSubject(preview.highlightedSubject)
      : preview.subject;

  return (
    <Stack spacing={1}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, minWidth: 0 }}>
        <Typography
          variant="caption"
          color="text.secondary"
          sx={{ flex: 1, minWidth: 0, overflowWrap: 'anywhere' }}
          data-testid="composer-recipient"
        >
          {recipient &&
            `${t('eventPage.mail.composer.recipients.to')}: ${recipient.displayName} <${recipient.address}>`}
          {recipient?.cc && recipient.cc.length > 0 && (
            <>
              <br />
              {`${t('eventPage.mail.composer.recipients.cc')}: ${recipient.cc.join(', ')}`}
            </>
          )}
        </Typography>
        {stepping && (
          <Box sx={{ display: 'flex', alignItems: 'center', flex: '0 0 auto' }}>
            <IconButton
              size="small"
              // Not disabled while loading: a disabled button drops focus, and Esc with it.
              onClick={() => !updating && onStep(index - 1)}
              aria-busy={updating}
              disabled={index <= 0}
              aria-label={t('eventPage.mail.composer.preview.previous')}
              data-testid="composer-prev"
            >
              <PrevIcon />
            </IconButton>
            <Typography variant="caption" aria-live="polite" data-testid="composer-position">
              {t('eventPage.mail.composer.preview.position', {
                index: index + 1,
                count: preview.recipientCount,
              })}
            </Typography>
            <IconButton
              size="small"
              onClick={() => !updating && onStep(index + 1)}
              aria-busy={updating}
              disabled={index >= preview.recipientCount - 1}
              aria-label={t('eventPage.mail.composer.preview.next')}
              data-testid="composer-next"
            >
              <NextIcon />
            </IconButton>
          </Box>
        )}
      </Box>

      <Typography
        variant="subtitle2"
        sx={{ overflowWrap: 'anywhere' }}
        data-testid="composer-subject"
      >
        {t('eventPage.mail.composer.preview.subject')}: {subject}
      </Typography>

      {preview.unknownPlaceholders.length > 0 && (
        <Alert severity="error" data-testid="composer-unknown-placeholders">
          {t(
            ownText
              ? 'eventPage.mail.composer.ownText.unknownPlaceholders'
              : 'eventPage.mail.composer.preview.unknownPlaceholders',
            { list: preview.unknownPlaceholders.join(', ') }
          )}
        </Alert>
      )}

      <Box sx={{ position: 'relative' }}>
        {updating && <LinearProgress sx={{ position: 'absolute', top: 0, left: 0, right: 0 }} />}
        <Box
          component="iframe"
          title={t('eventPage.mail.composer.preview.frameTitle')}
          srcDoc={html}
          sandbox=""
          sx={{
            width: '100%',
            height: { xs: '60vh', md: 520 },
            border: 1,
            borderColor: 'divider',
            borderRadius: 1,
            bgcolor: 'common.white',
            opacity: updating ? 0.6 : 1,
          }}
        />
      </Box>

      {ownText && (
        <Typography variant="caption" color="text.secondary" data-testid="composer-token-hint">
          {t('eventPage.mail.composer.ownText.tokenHint')}
        </Typography>
      )}

      <Typography variant="caption" color="text.secondary" data-testid="composer-reply-note">
        {preview.replyTo
          ? t('eventPage.mail.composer.replyTo', { address: preview.replyTo })
          : t('eventPage.mail.composer.replyDefault')}
      </Typography>
    </Stack>
  );
};
