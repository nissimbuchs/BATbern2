/**
 * Composer step 7 (Story 16.8b): [Send to organizers (test)] and [Send to N recipients], the
 * latter followed by one inline confirm ("Really send to …?" · Back · Yes, send). Sticky on mobile
 * so the buttons stay visible in the full-screen dialog (AC12).
 *
 * Story 16.5: "Save as template" for own text, and the registrant one-shot guard (AC9): when the
 * same own-text subject already went out, the confirm asks "Already sent on {date}. Send again?"
 * and only "Send again" sends.
 */
import React, { useEffect, useRef } from 'react';
import { Box, Button, CircularProgress, Typography } from '@mui/material';
import { Send as SendIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';

interface ComposerFooterProps {
  recipientCount: number;
  /** Nothing can be sent (no preview yet, a preview loading, an unknown placeholder). */
  disabled: boolean;
  testPending: boolean;
  sendPending: boolean;
  sticky: boolean;
  /** The inline confirm is showing (controlled, so a failed send can close it). */
  confirming: boolean;
  onConfirmingChange: (confirming: boolean) => void;
  onTest: () => void;
  onSend: () => void;
  /** Own text: offer "Save as template". */
  onSaveAsTemplate?: () => void;
  /** Own text without subject or body cannot be saved. */
  saveAsTemplateDisabled?: boolean;
  /** Formatted date of an earlier send with the same subject; the confirm asks to send again. */
  alreadySentOn?: string | null;
}

export const ComposerFooter: React.FC<ComposerFooterProps> = ({
  recipientCount,
  disabled,
  testPending,
  sendPending,
  sticky,
  confirming,
  onConfirmingChange: setConfirming,
  onTest,
  onSend,
  onSaveAsTemplate,
  saveAsTemplateDisabled = false,
  alreadySentOn,
}) => {
  const { t } = useTranslation('events');
  const yesButton = useRef<HTMLButtonElement>(null);
  const sendButton = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (confirming) yesButton.current?.focus();
  }, [confirming]);

  const back = () => {
    setConfirming(false);
    // The send button is rendered again after this update; focus it then.
    setTimeout(() => sendButton.current?.focus(), 0);
  };

  return (
    <Box
      sx={{
        display: 'flex',
        flexWrap: 'wrap',
        alignItems: 'center',
        justifyContent: 'flex-end',
        gap: 1,
        pt: 2,
        mt: 2,
        borderTop: 1,
        borderColor: 'divider',
        ...(sticky && {
          position: 'sticky',
          bottom: 0,
          bgcolor: 'background.paper',
          pb: 2,
          zIndex: 1,
        }),
      }}
    >
      {confirming ? (
        <Box
          role="group"
          aria-live="polite"
          sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 1 }}
          data-testid="composer-confirm"
        >
          <Typography
            variant="body2"
            sx={{ fontWeight: 500 }}
            data-testid={alreadySentOn ? 'composer-already-sent' : undefined}
          >
            {alreadySentOn
              ? t('eventPage.mail.composer.ownText.alreadySent', { date: alreadySentOn })
              : t('eventPage.mail.composer.footer.confirm', { count: recipientCount })}
          </Typography>
          <Button onClick={back} disabled={sendPending} data-testid="composer-confirm-back">
            {t('eventPage.mail.composer.footer.back')}
          </Button>
          <Button
            ref={yesButton}
            variant="contained"
            color="primary"
            onClick={onSend}
            disabled={sendPending}
            startIcon={sendPending ? <CircularProgress size={16} color="inherit" /> : <SendIcon />}
            data-testid="composer-confirm-yes"
          >
            {alreadySentOn
              ? t('eventPage.mail.composer.ownText.sendAgain')
              : t('eventPage.mail.composer.footer.yes')}
          </Button>
        </Box>
      ) : (
        <>
          {onSaveAsTemplate && (
            <Button
              onClick={onSaveAsTemplate}
              disabled={saveAsTemplateDisabled}
              sx={{ mr: 'auto' }}
              data-testid="composer-save-template-button"
            >
              {t('eventPage.mail.composer.saveTemplate.button')}
            </Button>
          )}
          <Button
            variant="outlined"
            // Not disabled while its own send runs: a disabled button drops focus, and Esc with it.
            onClick={() => !testPending && !sendPending && onTest()}
            disabled={disabled}
            aria-busy={testPending}
            startIcon={testPending ? <CircularProgress size={16} color="inherit" /> : undefined}
            data-testid="composer-test-button"
          >
            {t('eventPage.mail.composer.footer.test')}
          </Button>
          <Button
            ref={sendButton}
            variant="contained"
            color="primary"
            onClick={() => !testPending && setConfirming(true)}
            disabled={disabled}
            aria-busy={testPending}
            startIcon={<SendIcon />}
            data-testid="composer-send-button"
          >
            {t('eventPage.mail.composer.footer.send', { count: recipientCount })}
          </Button>
        </>
      )}
    </Box>
  );
};
