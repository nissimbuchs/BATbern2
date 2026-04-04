import React from 'react';
import { Box, Typography, IconButton, Chip, Divider } from '@mui/material';
import { Close as CloseIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';

interface SpeakerDrawerHeaderProps {
  speaker: SpeakerPoolEntry | null;
  onClose: () => void;
}

export const SpeakerDrawerHeader: React.FC<SpeakerDrawerHeaderProps> = ({ speaker, onClose }) => {
  const { t } = useTranslation('organizer');

  return (
    <>
      <Box
        sx={{ p: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}
      >
        <Box sx={{ flex: 1, mr: 1 }}>
          {speaker && (
            <>
              <Typography variant="h6" gutterBottom>
                {speaker.speakerName}
              </Typography>
              {speaker.company && (
                <Typography variant="body2" color="text.secondary" gutterBottom>
                  {speaker.company}
                </Typography>
              )}
              <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                <Chip
                  label={speaker.status}
                  size="small"
                  color={
                    speaker.status === 'ACCEPTED'
                      ? 'success'
                      : speaker.status === 'DECLINED'
                        ? 'error'
                        : speaker.status === 'INVITED'
                          ? 'info'
                          : speaker.status === 'CONTACTED'
                            ? 'warning'
                            : 'default'
                  }
                />
                {speaker.isTentative && (
                  <Chip label={t('speakers.tentative')} size="small" color="warning" />
                )}
              </Box>
            </>
          )}
        </Box>
        <IconButton onClick={onClose} size="small">
          <CloseIcon />
        </IconButton>
      </Box>
      <Divider />
    </>
  );
};
