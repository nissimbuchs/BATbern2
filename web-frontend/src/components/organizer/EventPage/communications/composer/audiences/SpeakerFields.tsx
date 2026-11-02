import React from 'react';
import {
  Box,
  Chip,
  FormControl,
  FormControlLabel,
  FormLabel,
  Radio,
  RadioGroup,
  Stack,
  Typography,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import {
  isOwnText,
  SPEAKER_STATUSES,
  type ExtraFieldsProps,
  type SpeakerReminderType,
  type SpeakerStatus,
} from '../types';
import { REMINDER_TYPES, reminderTypeOf } from './speakerTemplates';

/** Story 16.5 AC11: own text goes to the speakers in the chosen statuses, at least one. */
const SpeakerStatusChips: React.FC<ExtraFieldsProps> = ({ state, onChange }) => {
  const { t } = useTranslation('events');
  const toggle = (status: SpeakerStatus) => {
    const chosen = state.speakerStatuses.includes(status)
      ? state.speakerStatuses.filter((s) => s !== status)
      : SPEAKER_STATUSES.filter((s) => s === status || state.speakerStatuses.includes(s));
    onChange({ speakerStatuses: chosen });
  };
  return (
    <Box role="group" aria-labelledby="composer-speakers-status">
      <Typography variant="subtitle2" id="composer-speakers-status" sx={{ mb: 0.5 }}>
        {t('eventPage.mail.composer.speakers.which')}
      </Typography>
      <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
        {SPEAKER_STATUSES.map((status) => {
          const selected = state.speakerStatuses.includes(status);
          return (
            <Chip
              key={status}
              label={t(`eventPage.mail.composer.speakers.status.${status}`)}
              color={selected ? 'primary' : 'default'}
              variant={selected ? 'filled' : 'outlined'}
              onClick={() => toggle(status)}
              aria-pressed={selected}
              data-testid={`composer-speakers-status-${status}`}
            />
          );
        })}
      </Stack>
      {state.speakerStatuses.length === 0 && (
        <Typography
          variant="caption"
          color="error"
          component="div"
          sx={{ mt: 0.5 }}
          data-testid="composer-speakers-status-required"
        >
          {t('eventPage.mail.composer.speakers.statusRequired')}
        </Typography>
      )}
    </Box>
  );
};

/**
 * Speakers: in automatic mode which speakers; with a chosen template, who it goes to; with own
 * text (Story 16.5) the statuses.
 */
export const SpeakerFields: React.FC<ExtraFieldsProps> = ({ state, onChange }) => {
  const { t } = useTranslation('events');
  const chosenType = reminderTypeOf(state.templateKey);

  if (isOwnText(state)) return <SpeakerStatusChips state={state} onChange={onChange} />;

  // A chosen template decides who gets it; say so instead of offering a choice.
  if (chosenType) {
    return (
      <Typography variant="body2" color="text.secondary" data-testid="composer-speakers-target">
        {t('eventPage.mail.composer.speakers.goesTo', {
          target: t(`eventPage.mail.composer.speakers.type.${chosenType}`),
        })}
      </Typography>
    );
  }

  return (
    <FormControl>
      <FormLabel id="composer-speakers-type">
        {t('eventPage.mail.composer.speakers.which')}
      </FormLabel>
      <RadioGroup
        aria-labelledby="composer-speakers-type"
        value={state.speakerReminderType ?? ''}
        onChange={(e) =>
          onChange({
            speakerReminderType: e.target.value as SpeakerReminderType,
            recipientIndex: 0,
          })
        }
      >
        {REMINDER_TYPES.map((type) => (
          <FormControlLabel
            key={type}
            value={type}
            control={<Radio />}
            label={t(`eventPage.mail.composer.speakers.type.${type}`)}
            data-testid={`composer-speakers-type-${type}`}
          />
        ))}
      </RadioGroup>
    </FormControl>
  );
};
