import React from 'react';
import { FormControlLabel, Switch } from '@mui/material';
import { useTranslation } from 'react-i18next';
import type { ExtraFieldsProps } from '../types';

/** Newsletter: the reminder switch. */
export const NewsletterFields: React.FC<ExtraFieldsProps> = ({ state, onChange }) => {
  const { t } = useTranslation('events');
  return (
    <FormControlLabel
      control={
        <Switch
          checked={state.newsletterReminder}
          onChange={(e) => onChange({ newsletterReminder: e.target.checked })}
          data-testid="composer-newsletter-reminder"
        />
      }
      label={t('eventPage.mail.composer.newsletter.reminder')}
    />
  );
};
