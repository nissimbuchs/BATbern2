/**
 * Speaker Outreach Details Drawer Component
 *
 * Displays detailed outreach history for a selected speaker
 * Features:
 * - Slide-in drawer from right side
 * - Speaker information summary
 * - Chronological list of outreach attempts
 * - Contact method, date, and notes for each attempt
 * - Empty state when no outreach history exists
 */

import React from 'react';
import {
  Drawer,
  Box,
  Typography,
  IconButton,
  Divider,
  List,
  ListItem,
  ListItemText,
  Chip,
  CircularProgress,
  Alert,
  Paper,
} from '@mui/material';
import { Close as CloseIcon, Email, Phone, Person } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useSpeakerOutreachHistory } from '../../../hooks/useSpeakerOutreach';
import type { SpeakerPoolEntry } from '../../../types/speakerPool.types';

interface SpeakerOutreachDetailsDrawerProps {
  open: boolean;
  onClose: () => void;
  speaker: SpeakerPoolEntry | null;
  eventCode: string;
}

const SpeakerOutreachDetailsDrawer: React.FC<SpeakerOutreachDetailsDrawerProps> = ({
  open,
  onClose,
  speaker,
  eventCode,
}) => {
  const { t } = useTranslation('organizer');

  const {
    data: outreachHistory,
    isLoading,
    isError,
  } = useSpeakerOutreachHistory(eventCode, speaker?.id || '');

  const getContactMethodIcon = (method: string) => {
    switch (method) {
      case 'email':
        return <Email fontSize="small" />;
      case 'phone':
        return <Phone fontSize="small" />;
      case 'in_person':
        return <Person fontSize="small" />;
      default:
        return null;
    }
  };

  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    return new Intl.DateTimeFormat('de-CH', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(date);
  };

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      PaperProps={{
        sx: { width: { xs: '100%', sm: 500 } },
      }}
    >
      <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
        {/* Header */}
        <Box sx={{ p: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="h6">{t('speakerOutreach.outreachDetails')}</Typography>
          <IconButton onClick={onClose} size="small">
            <CloseIcon />
          </IconButton>
        </Box>

        <Divider />

        {/* Speaker Info */}
        {speaker && (
          <Paper sx={{ m: 2, p: 2 }} elevation={1}>
            <Typography variant="subtitle1" fontWeight="bold" gutterBottom>
              {speaker.speakerName}
            </Typography>
            {speaker.company && (
              <Typography variant="body2" color="text.secondary" gutterBottom>
                {speaker.company}
              </Typography>
            )}
            {speaker.expertise && (
              <Typography variant="body2" color="text.secondary" gutterBottom>
                {t('speakerBrainstorm.form.expertise')}: {speaker.expertise}
              </Typography>
            )}
            <Box mt={1}>
              <Chip
                label={speaker.status}
                size="small"
                color={speaker.status === 'CONTACTED' ? 'success' : 'default'}
              />
            </Box>
          </Paper>
        )}

        <Divider />

        {/* Outreach History */}
        <Box sx={{ flex: 1, overflow: 'auto', p: 2 }}>
          <Typography variant="subtitle2" gutterBottom>
            {t('speakerOutreach.contactHistory')}
          </Typography>

          {isLoading && (
            <Box display="flex" justifyContent="center" p={4}>
              <CircularProgress />
            </Box>
          )}

          {isError && (
            <Alert severity="error" sx={{ mt: 2 }}>
              {t('speakerOutreach.error.loadHistory')}
            </Alert>
          )}

          {!isLoading && !isError && (!outreachHistory || outreachHistory.length === 0) && (
            <Box sx={{ textAlign: 'center', py: 4 }}>
              <Typography variant="body2" color="text.secondary">
                {t('speakerOutreach.noContactHistory')}
              </Typography>
            </Box>
          )}

          {!isLoading && !isError && outreachHistory && outreachHistory.length > 0 && (
            <List>
              {outreachHistory.map((attempt, index) => (
                <ListItem
                  key={index}
                  sx={{
                    flexDirection: 'column',
                    alignItems: 'flex-start',
                    border: 1,
                    borderColor: 'divider',
                    borderRadius: 1,
                    mb: 2,
                  }}
                >
                  <Box display="flex" alignItems="center" gap={1} mb={1}>
                    {getContactMethodIcon(attempt.contactMethod)}
                    <Chip
                      label={t(
                        `speakerOutreach.markContactedModal.method.${attempt.contactMethod === 'in_person' ? 'inPerson' : attempt.contactMethod}`
                      )}
                      size="small"
                      variant="outlined"
                    />
                    <Typography variant="caption" color="text.secondary">
                      {formatDate(attempt.contactDate)}
                    </Typography>
                  </Box>

                  {attempt.notes && (
                    <ListItemText
                      secondary={attempt.notes}
                      secondaryTypographyProps={{
                        sx: {
                          whiteSpace: 'pre-wrap',
                          wordBreak: 'break-word',
                        },
                      }}
                    />
                  )}

                  {attempt.organizerUsername && (
                    <Typography variant="caption" color="text.secondary" mt={0.5}>
                      {t('speakerOutreach.contactedBy')}: {attempt.organizerUsername}
                    </Typography>
                  )}
                </ListItem>
              ))}
            </List>
          )}
        </Box>
      </Box>
    </Drawer>
  );
};

export default SpeakerOutreachDetailsDrawer;
