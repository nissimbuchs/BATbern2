/**
 * Composer step 4 (Story 16.8b): who gets the mail. Count and up to eight names, both from the
 * server, plus who is left out and why (AC14).
 */
import React, { useState } from 'react';
import { Box, Button, Chip, Collapse, Stack, Typography } from '@mui/material';
import { ExpandMore as ExpandMoreIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import type { MailPreviewResponse } from '@/services/mailMessageService';

interface ComposerRecipientsProps {
  preview: MailPreviewResponse;
  personalized: boolean;
}

export const ComposerRecipients: React.FC<ComposerRecipientsProps> = ({
  preview,
  personalized,
}) => {
  const { t } = useTranslation('events');
  const [showSkipped, setShowSkipped] = useState(false);
  const more = preview.recipientCount - preview.sampleRecipients.length;

  return (
    <Box>
      <Typography variant="subtitle2" data-testid="composer-recipient-count">
        {t('eventPage.mail.composer.recipients.count', { count: preview.recipientCount })}
      </Typography>
      <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap" sx={{ mt: 1 }}>
        {preview.sampleRecipients.map((r, i) => (
          <Chip
            key={`${r.displayName}-${i}`}
            label={r.displayName}
            size="small"
            data-testid="composer-sample-chip"
          />
        ))}
        {more > 0 && (
          <Chip
            label={t('eventPage.mail.composer.recipients.more', { count: more })}
            size="small"
            variant="outlined"
          />
        )}
      </Stack>
      {!personalized && (
        <Typography
          variant="caption"
          color="text.secondary"
          component="div"
          sx={{ mt: 1 }}
          data-testid="composer-same-text"
        >
          {t('eventPage.mail.composer.recipients.sameText')}
        </Typography>
      )}
      {preview.skipped.length > 0 && (
        <Box sx={{ mt: 1 }} data-testid="composer-skipped">
          <Button
            size="small"
            onClick={() => setShowSkipped((s) => !s)}
            aria-expanded={showSkipped}
            endIcon={
              <ExpandMoreIcon
                sx={{
                  transform: showSkipped ? 'rotate(180deg)' : 'none',
                  transition: 'transform 0.2s',
                }}
              />
            }
            sx={{ px: 0 }}
          >
            {t('eventPage.mail.composer.skipped.title', { count: preview.skipped.length })}
          </Button>
          <Collapse in={showSkipped} unmountOnExit>
            <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
              {preview.skipped.map((s, i) => (
                <Typography key={`${s.displayName}-${i}`} component="li" variant="body2">
                  {`${s.displayName}: ${t(`eventPage.mail.composer.skipped.reason.${s.reason}`)}`}
                </Typography>
              ))}
            </Box>
          </Collapse>
        </Box>
      )}
    </Box>
  );
};
