/**
 * QuickActions Component (Story 5.1 - Task 3b)
 *
 * Sidebar component for quick access to common organizer actions
 * Features:
 * - New Event button
 * - Event Types configuration button (navigates to admin page)
 * - Batch Import button
 * - i18n compliance (all text uses react-i18next)
 */

import React from 'react';
import { Paper, Typography, Button, Stack } from '@mui/material';
import {
  Add as AddIcon,
  Settings as SettingsIcon,
  UploadFile as UploadFileIcon,
  Topic as TopicIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';

interface QuickActionsProps {
  onNewEvent?: () => void;
  onBatchImport?: () => void;
  onBatchImportSessions?: () => void;
}

export const QuickActions: React.FC<QuickActionsProps> = ({
  onNewEvent,
  onBatchImport,
  onBatchImportSessions,
}) => {
  const { t } = useTranslation('events');
  const navigate = useNavigate();

  const handleEventTypesClick = () => {
    navigate('/organizer/event-types');
  };

  const handleManageTopicsClick = () => {
    navigate('/organizer/topics');
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
          onClick={onNewEvent}
          aria-label={t('dashboard.actions.newEvent')}
          data-testid="new-event-button"
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
          data-testid="event-types-button"
        >
          {t('dashboard.actions.eventTypes')}
        </Button>

        {/* Batch Import Button */}
        <Button
          variant="outlined"
          startIcon={<UploadFileIcon />}
          fullWidth
          onClick={onBatchImport}
          aria-label="Import historical events"
          data-testid="batch-import-button"
        >
          {t('common:event.batchImport.button')}
        </Button>

        {/* Session Batch Import Button */}
        <Button
          variant="outlined"
          startIcon={<UploadFileIcon />}
          fullWidth
          onClick={onBatchImportSessions}
          aria-label="Import historical sessions"
          data-testid="session-batch-import-button"
        >
          {t('common:session.batchImport.button')}
        </Button>

        {/* Manage Topics Button */}
        <Button
          variant="outlined"
          startIcon={<TopicIcon />}
          fullWidth
          onClick={handleManageTopicsClick}
          aria-label={t('dashboard.actions.manageTopics')}
          data-testid="manage-topics-button"
        >
          {t('dashboard.actions.manageTopics')}
        </Button>
      </Stack>
    </Paper>
  );
};
