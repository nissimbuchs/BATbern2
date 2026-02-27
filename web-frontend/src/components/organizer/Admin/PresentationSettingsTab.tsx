/**
 * PresentationSettingsTab
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * Tab component for admin page: configure about text and partner count
 * displayed on the moderator presentation page.
 *
 * AC #8 (admin UI)
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
import {
  getPresentationSettings,
  updatePresentationSettings,
} from '@/services/presentationService';

export const PresentationSettingsTab: React.FC = () => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();

  const { data: settings, isLoading } = useQuery({
    queryKey: ['presentation-settings'],
    queryFn: getPresentationSettings,
  });

  const [aboutText, setAboutText] = useState('');
  const [partnerCount, setPartnerCount] = useState<number>(0);
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [snackbarError, setSnackbarError] = useState(false);

  // Sync local state when settings load
  useEffect(() => {
    if (settings) {
      setAboutText(settings.aboutText ?? '');
      setPartnerCount(settings.partnerCount ?? 0);
    }
  }, [settings]);

  const mutation = useMutation({
    mutationFn: () => updatePresentationSettings({ aboutText, partnerCount }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['presentation-settings'] });
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
        {t('admin.presentationSettings.title', 'Presentation Settings')}
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        {t(
          'admin.presentationSettings.subtitle',
          'These values appear on the moderator presentation page (/present/:eventCode).'
        )}
      </Typography>

      <TextField
        label={t('admin.presentationSettings.aboutText', 'About Text')}
        value={aboutText}
        onChange={(e) => {
          setAboutText(e.target.value);
        }}
        multiline
        minRows={4}
        fullWidth
        sx={{ mb: 3 }}
        inputProps={{ 'aria-label': 'about text' }}
      />

      <TextField
        label={t('admin.presentationSettings.partnerCount', 'Partner Count')}
        type="number"
        value={partnerCount}
        onChange={(e) => {
          setPartnerCount(Number(e.target.value));
        }}
        inputProps={{ min: 0, 'aria-label': 'partner count' }}
        sx={{ mb: 3, width: 200 }}
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
          {t('admin.presentationSettings.save', 'Save')}
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
            ? t('admin.presentationSettings.saveFailed', 'Save failed. Please try again.')
            : t('admin.presentationSettings.saved', 'Settings saved.')}
        </Alert>
      </Snackbar>
    </Box>
  );
};
