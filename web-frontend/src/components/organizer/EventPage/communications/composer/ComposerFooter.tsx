/**
 * Composer step 7 (Story 16.8b): [Send to organizers (test)] and [Send to N recipients], the
 * latter followed by one inline confirm ("Really send to …?" · Back · Yes, send). Sticky on mobile
 * so the buttons stay visible in the full-screen dialog (AC12).
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
          <Typography variant="body2" sx={{ fontWeight: 500 }}>
            {t('eventPage.mail.composer.footer.confirm', { count: recipientCount })}
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
            {t('eventPage.mail.composer.footer.yes')}
          </Button>
        </Box>
      ) : (
        <>
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
