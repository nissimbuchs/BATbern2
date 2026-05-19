import React from 'react';
import { Box, Typography, IconButton, Chip, Divider } from '@mui/material';
import { Close as CloseIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { usePublicUser } from '@/hooks/useUserPortrait';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';

interface SpeakerDrawerHeaderProps {
  speaker: SpeakerPoolEntry | null;
  onClose: () => void;
}

export const SpeakerDrawerHeader: React.FC<SpeakerDrawerHeaderProps> = ({ speaker, onClose }) => {
  const { t } = useTranslation('organizer');

  // Epic 11 bug fix 2026-05-19 — once CONTACTED→READY promotes the speaker, the pool
  // entry carries `username` (the linked User's meaningful ID). Display the real user's
  // first+last name in the header instead of the brainstorm `speakerName`, so the
  // organizer sees the actual identified speaker. Falls back to `speakerName` while
  // the user fetch is in flight or when no username is set yet (pre-promote).
  const { data: linkedUser } = usePublicUser(speaker?.username ?? undefined);
  const displayName = (() => {
    if (linkedUser?.firstName && linkedUser?.lastName) {
      return `${linkedUser.firstName} ${linkedUser.lastName}`;
    }
    return speaker?.speakerName ?? '';
  })();
  // Surface the brainstorm name as a sub-caption ONLY when it differs from the real
  // name (e.g. brainstorm placeholder like "Testreferent2" vs. real "Markus Gerber").
  const showBrainstormCaption =
    !!linkedUser?.firstName &&
    !!linkedUser?.lastName &&
    !!speaker?.speakerName &&
    speaker.speakerName !== displayName;

  return (
    <>
      <Box
        sx={{ p: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}
      >
        <Box sx={{ flex: 1, mr: 1 }}>
          {speaker && (
            <>
              <Typography variant="h6" gutterBottom>
                {displayName}
              </Typography>
              {showBrainstormCaption && (
                <Typography
                  variant="caption"
                  color="text.secondary"
                  sx={{ display: 'block', mt: -0.5, mb: 0.5 }}
                  data-testid="drawer-header-brainstorm-name"
                >
                  {t('speakerDrawer.brainstormNameLabel', 'brainstormed as')} {speaker.speakerName}
                </Typography>
              )}
              {speaker.company && (
                <Typography variant="body2" color="text.secondary" gutterBottom>
                  {speaker.company}
                </Typography>
              )}
              <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                <Chip
                  label={t(`speakerStatus.${speaker.status}`, speaker.status)}
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
