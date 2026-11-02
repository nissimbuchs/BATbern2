/**
 * Placeholder chips (Story 16.5 AC6): the audience's offered variables with plain labels. The list
 * comes from the server (`offeredVariables` of the preview), never from the frontend. A click
 * inserts `{{name}}` where the organizer last was (subject or body); that is the parent's job.
 */
import React from 'react';
import { Box, Chip, Stack, Typography } from '@mui/material';
import { useTranslation } from 'react-i18next';

interface PlaceholderChipsProps {
  variables: string[];
  /** False when every recipient gets the same values (newsletter, venue, partners). */
  personalized: boolean;
  onInsert: (name: string) => void;
}

export const PlaceholderChips: React.FC<PlaceholderChipsProps> = ({
  variables,
  personalized,
  onInsert,
}) => {
  const { t } = useTranslation('events');
  return (
    <Box data-testid="composer-placeholder-chips">
      <Typography variant="caption" color="text.secondary" component="div" sx={{ mb: 0.5 }}>
        {t('eventPage.mail.composer.ownText.chips')}
      </Typography>
      <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
        {variables.map((name) => (
          <Chip
            key={name}
            size="small"
            variant="outlined"
            label={t(`eventPage.mail.composer.variables.${name}`, { defaultValue: name })}
            title={`{{${name}}}`}
            // Keep the focus (and the cursor) in the field the organizer was typing in.
            onMouseDown={(e) => e.preventDefault()}
            onClick={() => onInsert(name)}
            data-testid={`composer-chip-${name}`}
          />
        ))}
      </Stack>
      {!personalized && (
        <Typography
          variant="caption"
          color="text.secondary"
          component="div"
          sx={{ mt: 0.5 }}
          data-testid="composer-no-personal-fields"
        >
          {t('eventPage.mail.composer.ownText.noPersonalFields')}
        </Typography>
      )}
    </Box>
  );
};
