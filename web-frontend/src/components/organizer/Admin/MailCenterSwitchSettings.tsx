/**
 * MailCenterSwitchSettings (Story 16.6 AC7): the switch `mailcenter.notify-instead-of-forward` in
 * Admin → Settings. Per alias group, the recipients get a short notice with a link to the Mail Center
 * instead of the forwarded copy. Nothing checked = forwarding as before. The event service reads the
 * setting for every mail, so a change applies to the next mail. Parsing matches `MailCenterSwitch`:
 * comma or space separated, case-insensitive, unknown names ignored.
 */

import React, { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Checkbox,
  CircularProgress,
  FormControlLabel,
  FormGroup,
  Snackbar,
  Typography,
} from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { getAdminSetting, updateAdminSetting } from '@/services/adminSettingsService';

export const MAIL_CENTER_SWITCH_KEY = 'mailcenter.notify-instead-of-forward';

/** In the order the runbook recommends switching them on. */
const GROUPS = ['moderator', 'replies', 'contact'] as const;
type Group = (typeof GROUPS)[number];

function parse(value: string | null | undefined): Set<Group> {
  const parts = (value ?? '')
    .split(/[,\s]+/)
    .map((part) => part.trim().toLowerCase())
    .filter(Boolean);
  return new Set(GROUPS.filter((group) => parts.includes(group)));
}

function serialize(groups: Set<Group>): string {
  return GROUPS.filter((group) => groups.has(group)).join(',');
}

export const MailCenterSwitchSettings: React.FC = () => {
  const { t } = useTranslation('admin');
  const queryClient = useQueryClient();
  const queryKey = ['admin-settings', MAIL_CENTER_SWITCH_KEY];

  const { data: setting, isLoading } = useQuery({
    queryKey,
    queryFn: () => getAdminSetting(MAIL_CENTER_SWITCH_KEY),
  });

  const [checked, setChecked] = useState<Set<Group>>(new Set());
  const [snackbar, setSnackbar] = useState<'saved' | 'failed' | null>(null);

  useEffect(() => {
    if (setting) setChecked(parse(setting.value));
  }, [setting]);

  const mutation = useMutation({
    mutationFn: (value: string) => updateAdminSetting(MAIL_CENTER_SWITCH_KEY, value),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey });
      setSnackbar('saved');
    },
    onError: () => setSnackbar('failed'),
  });

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', mt: 2 }}>
        <CircularProgress />
      </Box>
    );
  }

  const changed = serialize(checked) !== serialize(parse(setting?.value));
  const toggle = (group: Group) =>
    setChecked((current) => {
      const next = new Set(current);
      if (next.has(group)) next.delete(group);
      else next.add(group);
      return next;
    });

  return (
    <Box data-testid="mail-center-switch-settings">
      <Typography variant="h6" gutterBottom>
        {t('settings.mailCenterSwitch.title')}
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        {t('settings.mailCenterSwitch.subtitle')}
      </Typography>

      <FormGroup sx={{ mb: 2 }}>
        {GROUPS.map((group) => (
          <FormControlLabel
            key={group}
            sx={{ alignItems: 'flex-start', mb: 1 }}
            control={
              <Checkbox
                checked={checked.has(group)}
                onChange={() => toggle(group)}
                inputProps={{ 'data-testid': `mail-switch-${group}` } as Record<string, string>}
                sx={{ pt: 0.5 }}
              />
            }
            label={
              <Box>
                <Typography variant="body1">
                  {t(`settings.mailCenterSwitch.groups.${group}.label`)}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {t(`settings.mailCenterSwitch.groups.${group}.help`)}
                </Typography>
              </Box>
            }
          />
        ))}
      </FormGroup>

      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        {t('settings.mailCenterSwitch.alwaysForwarded')}
      </Typography>

      <Button
        variant="contained"
        onClick={() => mutation.mutate(serialize(checked))}
        disabled={!changed || mutation.isPending}
        startIcon={mutation.isPending ? <CircularProgress size={16} color="inherit" /> : undefined}
        data-testid="mail-switch-save"
      >
        {t('settings.save')}
      </Button>

      <Snackbar
        open={snackbar !== null}
        autoHideDuration={4000}
        onClose={() => setSnackbar(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert
          severity={snackbar === 'failed' ? 'error' : 'success'}
          onClose={() => setSnackbar(null)}
        >
          {snackbar === 'failed' ? t('settings.saveFailed') : t('settings.saved')}
        </Alert>
      </Snackbar>
    </Box>
  );
};
