/**
 * "Save as template" (Story 16.5 AC8, own text only): stores the own subject and body as a new
 * non-system template through the existing `POST /email-templates`, with the audience's category,
 * the chosen language and the BATbern layout. A key clash (409) is shown in the dialog.
 * The organizer types a name; the kebab-case key is derived from it and shown below the field.
 */
import React, { useState } from 'react';
import {
  Alert,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material';
import axios from 'axios';
import { useTranslation } from 'react-i18next';
import { useCreateEmailTemplate } from '@/hooks/useEmailTemplates';
import type { CreateEmailTemplateRequest } from '@/services/emailTemplateService';
import type { ComposerLocale } from './types';

import { toTemplateKey } from '@/utils/templateKey';

type TemplateCategory = CreateEmailTemplateRequest['category'];

interface SaveAsTemplateDialogProps {
  open: boolean;
  category: string;
  subject: string;
  htmlBody: string;
  initialLocale: ComposerLocale;
  onClose: () => void;
  onSaved: (templateKey: string) => void;
}

export const SaveAsTemplateDialog: React.FC<SaveAsTemplateDialogProps> = ({
  open,
  category,
  subject,
  htmlBody,
  initialLocale,
  onClose,
  onSaved,
}) => {
  const { t } = useTranslation('events');
  const create = useCreateEmailTemplate();
  const [templateKey, setTemplateKey] = useState('');
  const [locale, setLocale] = useState<ComposerLocale>(initialLocale);
  const [error, setError] = useState<string | null>(null);

  const key = toTemplateKey(templateKey);
  const keyInvalid = templateKey.trim().length > 0 && !key;

  const save = () => {
    setError(null);
    create.mutate(
      {
        templateKey: key,
        locale,
        category: category as TemplateCategory,
        isLayout: false,
        subject,
        htmlBody,
        layoutKey: 'batbern-default',
      },
      {
        onSuccess: () => onSaved(key),
        onError: (err) => {
          const conflict = axios.isAxiosError(err) && err.response?.status === 409;
          setError(
            t(
              conflict
                ? 'eventPage.mail.composer.saveTemplate.duplicate'
                : 'eventPage.mail.composer.saveTemplate.failed'
            )
          );
        },
      }
    );
  };

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="xs"
      fullWidth
      data-testid="composer-save-dialog"
    >
      <DialogTitle>{t('eventPage.mail.composer.saveTemplate.title')}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {error && (
            <Alert severity="error" data-testid="composer-save-error">
              {error}
            </Alert>
          )}
          <TextField
            label={t('eventPage.mail.composer.saveTemplate.key')}
            value={templateKey}
            onChange={(e) => {
              setTemplateKey(e.target.value);
              setError(null);
            }}
            error={keyInvalid}
            helperText={
              key ? (
                <span data-testid="composer-save-key-preview">
                  {t('eventPage.mail.composer.saveTemplate.keyPreview', { key })}
                </span>
              ) : (
                t(
                  keyInvalid
                    ? 'eventPage.mail.composer.saveTemplate.keyInvalid'
                    : 'eventPage.mail.composer.saveTemplate.keyHelp'
                )
              )
            }
            size="small"
            fullWidth
            required
            autoFocus
            inputProps={{ 'data-testid': 'composer-save-key' }}
          />
          <Stack spacing={0.5}>
            <Typography variant="subtitle2" id="composer-save-language">
              {t('eventPage.mail.composer.language')}
            </Typography>
            <ToggleButtonGroup
              value={locale}
              exclusive
              size="small"
              onChange={(_e, value: ComposerLocale | null) => value && setLocale(value)}
              aria-labelledby="composer-save-language"
            >
              <ToggleButton value="de" data-testid="composer-save-locale-de">
                DE
              </ToggleButton>
              <ToggleButton value="en" data-testid="composer-save-locale-en">
                EN
              </ToggleButton>
            </ToggleButtonGroup>
          </Stack>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={create.isPending}>
          {t('eventPage.mail.composer.footer.back')}
        </Button>
        <Button
          variant="contained"
          onClick={save}
          disabled={!key || keyInvalid || create.isPending}
          startIcon={create.isPending ? <CircularProgress size={16} color="inherit" /> : undefined}
          data-testid="composer-save-confirm"
        >
          {t('eventPage.mail.composer.saveTemplate.save')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};
