import React from 'react';
import { Box, Chip, Stack, Typography } from '@mui/material';
import { useTranslation } from 'react-i18next';
import {
  isOwnText,
  REGISTRANT_STATUSES,
  type ExtraFieldsProps,
  type RegistrantStatus,
} from '../types';

/**
 * Registrants: own text goes to the registrations in the chosen statuses, at least one. A template
 * goes to the default audience (it is one-shot per event), so template mode offers no choice.
 */
export const RegistrantFields: React.FC<ExtraFieldsProps> = ({ state, onChange }) => {
  const { t } = useTranslation('events');
  if (!isOwnText(state)) return null;

  const toggle = (status: RegistrantStatus) => {
    const chosen = state.registrantStatuses.includes(status)
      ? state.registrantStatuses.filter((s) => s !== status)
      : REGISTRANT_STATUSES.filter((s) => s === status || state.registrantStatuses.includes(s));
    onChange({ registrantStatuses: chosen, recipientIndex: 0 });
  };

  return (
    <Box role="group" aria-labelledby="composer-registrants-status">
      <Typography variant="subtitle2" id="composer-registrants-status" sx={{ mb: 0.5 }}>
        {t('eventPage.mail.composer.registrants.which')}
      </Typography>
      <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
        {REGISTRANT_STATUSES.map((status) => {
          const selected = state.registrantStatuses.includes(status);
          return (
            <Chip
              key={status}
              label={t(`eventPage.mail.composer.registrants.status.${status}`)}
              color={selected ? 'primary' : 'default'}
              variant={selected ? 'filled' : 'outlined'}
              onClick={() => toggle(status)}
              aria-pressed={selected}
              data-testid={`composer-registrants-status-${status}`}
            />
          );
        })}
      </Stack>
      {state.registrantStatuses.length === 0 && (
        <Typography
          variant="caption"
          color="error"
          component="div"
          sx={{ mt: 0.5 }}
          data-testid="composer-registrants-status-required"
        >
          {t('eventPage.mail.composer.registrants.statusRequired')}
        </Typography>
      )}
    </Box>
  );
};
