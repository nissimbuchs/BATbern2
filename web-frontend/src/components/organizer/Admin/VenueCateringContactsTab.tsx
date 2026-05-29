/**
 * VenueCateringContactsTab
 *
 * Singleton config edited in Administration: the one venue contact, the one
 * catering contact, and the organizer who acts as venue coordinator (reply-to
 * on every venue-coordination email sent from the Event Detail Venue tab).
 *
 * Persisted as a single JSON blob under app_settings key
 * `venue.coordination.config` via the existing key/value AdminSettings API —
 * no new backend endpoint required.
 */

import React, { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Divider,
  Snackbar,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { getAdminSetting, updateAdminSetting } from '@/services/adminSettingsService';
import { OrganizerSelect } from '@/components/shared/OrganizerSelect';

export const VENUE_COORDINATION_CONFIG_KEY = 'venue.coordination.config';

export interface VenueContact {
  salutation: string;
  name: string;
  email: string;
}

export interface VenueCoordinationConfig {
  venue: VenueContact;
  catering: VenueContact;
  coordinatorUsername: string;
}

const EMPTY_CONFIG: VenueCoordinationConfig = {
  venue: { salutation: '', name: '', email: '' },
  catering: { salutation: '', name: '', email: '' },
  coordinatorUsername: '',
};

function parseConfig(raw: string | null | undefined): VenueCoordinationConfig {
  if (!raw) return EMPTY_CONFIG;
  try {
    const parsed = JSON.parse(raw) as Partial<VenueCoordinationConfig>;
    return {
      venue: { ...EMPTY_CONFIG.venue, ...(parsed.venue ?? {}) },
      catering: { ...EMPTY_CONFIG.catering, ...(parsed.catering ?? {}) },
      coordinatorUsername: parsed.coordinatorUsername ?? '',
    };
  } catch {
    return EMPTY_CONFIG;
  }
}

export const VenueCateringContactsTab: React.FC = () => {
  const { t } = useTranslation('admin');
  const queryClient = useQueryClient();

  const { data: setting, isLoading } = useQuery({
    queryKey: ['admin-settings', VENUE_COORDINATION_CONFIG_KEY],
    queryFn: () => getAdminSetting(VENUE_COORDINATION_CONFIG_KEY),
  });

  const [config, setConfig] = useState<VenueCoordinationConfig>(EMPTY_CONFIG);
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [snackbarError, setSnackbarError] = useState(false);

  useEffect(() => {
    if (setting) {
      setConfig(parseConfig(setting.value));
    }
  }, [setting]);

  const mutation = useMutation({
    mutationFn: () => updateAdminSetting(VENUE_COORDINATION_CONFIG_KEY, JSON.stringify(config)),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: ['admin-settings', VENUE_COORDINATION_CONFIG_KEY],
      });
      setSnackbarError(false);
      setSnackbarOpen(true);
    },
    onError: () => {
      setSnackbarError(true);
      setSnackbarOpen(true);
    },
  });

  const updateContact = (role: 'venue' | 'catering', field: keyof VenueContact, value: string) => {
    setConfig((prev) => ({ ...prev, [role]: { ...prev[role], [field]: value } }));
  };

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', mt: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box sx={{ maxWidth: 760 }} data-testid="venue-catering-contacts-tab">
      <Typography variant="h6" gutterBottom>
        {t('settings.venueCoordination.title', 'Venue & Catering Contacts')}
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        {t(
          'settings.venueCoordination.subtitle',
          'These contacts receive the offerte and timetable emails sent from the Event Detail Venue tab. They are the same across every event — define them once here.'
        )}
      </Typography>

      <Typography variant="subtitle1" sx={{ mb: 1, fontWeight: 600 }}>
        {t('settings.venueCoordination.venueSection', 'Venue contact')}
      </Typography>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 3 }}>
        <TextField
          label={t('settings.venueCoordination.salutation', 'Salutation')}
          placeholder="Frau"
          value={config.venue.salutation}
          onChange={(e) => updateContact('venue', 'salutation', e.target.value)}
          sx={{ minWidth: 120 }}
        />
        <TextField
          label={t('settings.venueCoordination.name', 'Name')}
          placeholder="Gabriela Senn"
          value={config.venue.name}
          onChange={(e) => updateContact('venue', 'name', e.target.value)}
          fullWidth
        />
        <TextField
          label={t('settings.venueCoordination.email', 'Email')}
          placeholder="contact@venue.example"
          type="email"
          value={config.venue.email}
          onChange={(e) => updateContact('venue', 'email', e.target.value)}
          fullWidth
        />
      </Stack>

      <Divider sx={{ mb: 3 }} />

      <Typography variant="subtitle1" sx={{ mb: 1, fontWeight: 600 }}>
        {t('settings.venueCoordination.cateringSection', 'Catering contact')}
      </Typography>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 3 }}>
        <TextField
          label={t('settings.venueCoordination.salutation', 'Salutation')}
          placeholder="Herr"
          value={config.catering.salutation}
          onChange={(e) => updateContact('catering', 'salutation', e.target.value)}
          sx={{ minWidth: 120 }}
        />
        <TextField
          label={t('settings.venueCoordination.name', 'Name')}
          placeholder="Stefan Oppliger"
          value={config.catering.name}
          onChange={(e) => updateContact('catering', 'name', e.target.value)}
          fullWidth
        />
        <TextField
          label={t('settings.venueCoordination.email', 'Email')}
          placeholder="contact@catering.example"
          type="email"
          value={config.catering.email}
          onChange={(e) => updateContact('catering', 'email', e.target.value)}
          fullWidth
        />
      </Stack>

      <Divider sx={{ mb: 3 }} />

      <Typography variant="subtitle1" sx={{ mb: 1, fontWeight: 600 }}>
        {t('settings.venueCoordination.coordinatorSection', 'Venue coordinator')}
      </Typography>
      <Alert severity="info" sx={{ mb: 2 }}>
        {t(
          'settings.venueCoordination.coordinatorHelp',
          'The organizer username (e.g. nissim.buchs) whose email address will appear as Reply-To on every venue/catering message. Replies from the recipient go straight to them.'
        )}
      </Alert>
      <OrganizerSelect
        value={config.coordinatorUsername}
        onChange={(username) => setConfig((prev) => ({ ...prev, coordinatorUsername: username }))}
        label={t('settings.venueCoordination.coordinatorUsername', 'Coordinator')}
        includeUnassigned={false}
        size="medium"
        fullWidth
        sx={{ mb: 3 }}
        data-testid="venue-coord-coordinator-select"
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
        onClose={() => setSnackbarOpen(false)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert
          severity={snackbarError ? 'error' : 'success'}
          onClose={() => setSnackbarOpen(false)}
        >
          {snackbarError
            ? t('settings.saveFailed', 'Save failed. Please try again.')
            : t('settings.saved', 'Settings saved.')}
        </Alert>
      </Snackbar>
    </Box>
  );
};
