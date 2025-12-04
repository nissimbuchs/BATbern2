/**
 * QuickActions Component (Story 5.1 - Task 3b)
 *
 * Sidebar component for quick access to common organizer actions
 * Features:
 * - New Event button
 * - Event Types configuration button (navigates to admin page)
 * - Helper text for Event Types (admin-only feature)
 * - Future placeholder buttons (disabled)
 * - i18n compliance (all text uses react-i18next)
 */

import React from 'react';
import { Paper, Typography, Button, Stack } from '@mui/material';
import { Add as AddIcon, Settings as SettingsIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';

export const QuickActions: React.FC = () => {
  const { t } = useTranslation('events');
  const navigate = useNavigate();

  const handleEventTypesClick = () => {
    navigate('/organizer/event-types');
  };

  return (
    <Paper sx={{ p: 3 }} data-testid="quick-actions">
      <Typography variant="h6" gutterBottom>
        {t('dashboard.quickActions')}
      </Typography>

      <Stack spacing={2} sx={{ mt: 2 }}>
        {/* New Event Button */}
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          fullWidth
          aria-label={t('dashboard.actions.newEvent')}
        >
          {t('dashboard.actions.newEvent')}
        </Button>

        {/* Event Types Configuration Button */}
        <Button
          variant="outlined"
          startIcon={<SettingsIcon />}
          fullWidth
          onClick={handleEventTypesClick}
          aria-label={t('dashboard.actions.eventTypes')}
        >
          {t('dashboard.actions.eventTypes')}
        </Button>

        {/* Helper text for Event Types */}
        <Typography variant="caption" color="text.secondary" sx={{ pl: 1 }}>
          {t('dashboard.actions.eventTypesHelp')}
        </Typography>
      </Stack>
    </Paper>
  );
};
