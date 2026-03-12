/**
 * AdminSettingsTab (Story 10.26)
 *
 * Settings tab for the Admin Page with Email Forwarding configuration.
 * Allows organizers to configure support@batbern.ch forwarding recipients.
 *
 * AC #8: Support contacts in Admin Settings tab
 */

import React, { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Snackbar,
  TextField,
  Typography,
} from '@mui/material';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { getAdminSetting, updateAdminSetting } from '@/services/adminSettingsService';

const SUPPORT_CONTACTS_KEY = 'email-forwarding.support-contacts';

export const AdminSettingsTab: React.FC = () => {
  const { t } = useTranslation('admin');
  const queryClient = useQueryClient();

  const { data: setting, isLoading } = useQuery({
    queryKey: ['admin-settings', SUPPORT_CONTACTS_KEY],
    queryFn: () => getAdminSetting(SUPPORT_CONTACTS_KEY),
  });

  const [supportContacts, setSupportContacts] = useState('');
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [snackbarError, setSnackbarError] = useState(false);

  useEffect(() => {
    if (setting) {
      setSupportContacts(setting.value ?? '');
    }
  }, [setting]);

  const mutation = useMutation({
    mutationFn: () => updateAdminSetting(SUPPORT_CONTACTS_KEY, supportContacts),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin-settings', SUPPORT_CONTACTS_KEY] });
      setSnackbarError(false);
      setSnackbarOpen(true);
    },
    onError: () => {
      setSnackbarError(true);
      setSnackbarOpen(true);
    },
  });

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', mt: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box sx={{ maxWidth: 700 }}>
      <Typography variant="h6" gutterBottom>
        {t('settings.emailForwarding.title', 'Email Forwarding')}
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        {t(
          'settings.emailForwarding.subtitle',
          'Configure email forwarding recipients for batbern.ch addresses.'
        )}
      </Typography>

      <TextField
        label={t('settings.emailForwarding.supportContacts', 'support@batbern.ch recipients')}
        value={supportContacts}
        onChange={(e) => {
          setSupportContacts(e.target.value);
        }}
        fullWidth
        placeholder="alice@example.ch, bob@example.ch"
        helperText={t(
          'settings.emailForwarding.supportContactsHelp',
          'Comma-separated email addresses. If empty, emails to support@batbern.ch are forwarded to all organizers.'
        )}
        sx={{ mb: 3 }}
        inputProps={{ 'aria-label': 'support contacts' }}
      />

      <Box>
        <Button
          variant="contained"
          onClick={() => {
            mutation.mutate();
          }}
          disabled={mutation.isPending}
          startIcon={
            mutation.isPending ? <CircularProgress size={16} color="inherit" /> : undefined
          }
        >
          {t('settings.save', 'Save')}
        </Button>
      </Box>

      <Snackbar
        open={snackbarOpen}
        autoHideDuration={4000}
        onClose={() => {
          setSnackbarOpen(false);
        }}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert
          severity={snackbarError ? 'error' : 'success'}
          onClose={() => {
            setSnackbarOpen(false);
          }}
        >
          {snackbarError
            ? t('settings.saveFailed', 'Save failed. Please try again.')
            : t('settings.saved', 'Settings saved.')}
        </Alert>
      </Snackbar>
    </Box>
  );
};
