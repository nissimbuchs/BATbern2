import React from 'react';
import {
  FormControl,
  FormControlLabel,
  FormLabel,
  Radio,
  RadioGroup,
  Typography,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import type { ExtraFieldsProps, SpeakerReminderType } from '../types';
import { REMINDER_TYPES, reminderTypeOf } from './speakerTemplates';

/** Speakers: in automatic mode which speakers; with a chosen template, who it goes to. */
export const SpeakerFields: React.FC<ExtraFieldsProps> = ({ state, onChange }) => {
  const { t } = useTranslation('events');
  const chosenType = reminderTypeOf(state.templateKey);

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
