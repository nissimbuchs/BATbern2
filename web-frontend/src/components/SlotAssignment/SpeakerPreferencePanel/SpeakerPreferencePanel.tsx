/**
 * SpeakerPreferencePanel Component (Story 5.7 - Task 4b GREEN Phase)
 *
 * Displays speaker preferences in a right drawer
 * AC7: Display speaker time preferences (morning/afternoon, conflicts)
 * AC8: Track A/V needs and room setup requirements per speaker
 * AC11: Highlight when slot matches speaker preference
 */

import React from 'react';
import {
  Drawer,
  Box,
  Typography,
  IconButton,
  Divider,
  Stack,
  Chip,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Paper,
} from '@mui/material';
import {
  Close,
  WbSunny,
  Brightness3,
  CheckCircle,
  Cancel,
  CalendarToday,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';

interface SpeakerPreference {
  preferredTimeOfDay?: 'morning' | 'afternoon' | 'evening';
  neutralTimeOfDay?: string[];
  avoidTimeOfDay?: string[];
  avoidTimes?: Array<{
    start: string;
    end: string;
  }>;
  avRequirements?: {
    microphone?: boolean;
    projector?: boolean;
    recording?: boolean;
    whiteboard?: boolean;
  };
  roomSetupNotes?: string;
}

interface Speaker {
  username: string;
  displayName: string;
  companyName?: string;
  preferences?: SpeakerPreference;
}

export interface SpeakerPreferencePanelProps {
  speaker: Speaker | null;
  isOpen: boolean;
  onClose: () => void;
  hoveredSlot?: {
    time: string;
    room: string;
  };
  matchScore?: number;
}

export const SpeakerPreferencePanel: React.FC<SpeakerPreferencePanelProps> = ({
  speaker,
  isOpen,
  onClose,
  hoveredSlot,
  matchScore,
}) => {
  const { t } = useTranslation('events');

  if (!speaker) {
    return null;
  }

  const preferences = speaker.preferences;

  const getMatchClass = (score: number) => {
    if (score >= 80) return 'match-high';
    if (score >= 50) return 'match-medium';
    return 'match-low';
  };

  const getMatchColor = (score: number) => {
    if (score >= 80) return 'green';
    if (score >= 50) return 'orange';
    return 'red';
  };

  const getTimeIcon = (timeOfDay: string) => {
    if (timeOfDay === 'morning') return <WbSunny />;
    if (timeOfDay === 'afternoon' || timeOfDay === 'evening') return <Brightness3 />;
    return <CalendarToday />;
  };

  return (
    <Drawer
      anchor="right"
      open={isOpen}
      onClose={onClose}
      role="dialog"
      PaperProps={{
        sx: { width: 400 },
      }}
    >
      <Box sx={{ p: 3 }}>
        {/* Header */}
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start', mb: 3 }}>
          <Box>
            <Typography variant="h6">
              {speaker.displayName} - {t('slotAssignment.preferences.title')}
            </Typography>
            {speaker.companyName && (
              <Typography variant="body2" color="text.secondary">
                {speaker.companyName}
              </Typography>
            )}
          </Box>
          <IconButton onClick={onClose} size="small">
            <Close />
          </IconButton>
        </Box>

        <Divider sx={{ mb: 3 }} />

        <Stack spacing={3}>
          {/* Time Preferences */}
          <Box>
            <Typography variant="subtitle2" gutterBottom>
              {t('slotAssignment.preferences.timePreferences')}
            </Typography>

            {/* Preferred Time */}
            {preferences?.preferredTimeOfDay && (
              <Chip
                icon={getTimeIcon(preferences.preferredTimeOfDay)}
                label={t(`slotAssignment.preferences.times.${preferences.preferredTimeOfDay}`)}
                color="success"
                data-testid={`preferred-icon-${preferences.preferredTimeOfDay}`}
                className="icon-preferred"
                sx={{ mr: 1, mb: 1 }}
              />
            )}

            {/* Neutral Times */}
            {preferences?.neutralTimeOfDay?.map((time) => (
              <Chip
                key={time}
                icon={getTimeIcon(time)}
                label={t(`slotAssignment.preferences.times.${time}`)}
                variant="outlined"
                data-testid={`neutral-icon-${time}`}
                className="icon-neutral"
                sx={{ mr: 1, mb: 1 }}
              />
            ))}

            {/* Avoid Times */}
            {preferences?.avoidTimeOfDay?.map((time) => (
              <Chip
                key={time}
                icon={getTimeIcon(time)}
                label={t(`slotAssignment.preferences.times.${time}`)}
                color="error"
                variant="outlined"
                data-testid={`avoid-icon-${time}`}
                className="icon-avoid"
                sx={{ mr: 1, mb: 1 }}
              />
            ))}

            {/* Specific Avoid Times */}
            {preferences?.avoidTimes && preferences.avoidTimes.length > 0 && (
              <Box sx={{ mt: 2 }}>
                <Typography variant="caption" color="text.secondary" gutterBottom>
                  {t('slotAssignment.preferences.unavailableTimes')}
                </Typography>
                {preferences.avoidTimes.map((timeRange, index) => (
                  <Paper
                    key={index}
                    data-testid={`avoid-time-${index}`}
                    className="avoid-time-range"
                    sx={{ p: 1, mt: 1, bgcolor: 'error.lighter' }}
                  >
                    <Typography variant="body2">
                      {new Date(timeRange.start).toLocaleTimeString('en-US', {
                        hour: '2-digit',
                        minute: '2-digit',
                      })}{' '}
                      -{' '}
                      {new Date(timeRange.end).toLocaleTimeString('en-US', {
                        hour: '2-digit',
                        minute: '2-digit',
                      })}
                    </Typography>
                  </Paper>
                ))}
              </Box>
            )}
          </Box>

          <Divider />

          {/* A/V Requirements */}
          <Box>
            <Typography variant="subtitle2" gutterBottom>
              {t('slotAssignment.preferences.avRequirements')}
            </Typography>
            <List dense>
              <ListItem>
                <ListItemIcon>
                  {preferences?.avRequirements?.microphone ? (
                    <CheckCircle color="success" />
                  ) : (
                    <Cancel color="disabled" />
                  )}
                </ListItemIcon>
                <ListItemText
                  primary={t('slotAssignment.preferences.equipment.microphone')}
                  data-testid="av-requirement-microphone"
                  className={
                    preferences?.avRequirements?.microphone
                      ? 'requirement-needed'
                      : 'requirement-not-needed'
                  }
                />
              </ListItem>
              <ListItem>
                <ListItemIcon>
                  {preferences?.avRequirements?.projector ? (
                    <CheckCircle color="success" />
                  ) : (
                    <Cancel color="disabled" />
                  )}
                </ListItemIcon>
                <ListItemText
                  primary={t('slotAssignment.preferences.equipment.projector')}
                  data-testid="av-requirement-projector"
                  className={
                    preferences?.avRequirements?.projector
                      ? 'requirement-needed'
                      : 'requirement-not-needed'
                  }
                />
              </ListItem>
              <ListItem>
                <ListItemIcon>
                  {preferences?.avRequirements?.recording ? (
                    <CheckCircle color="success" />
                  ) : (
                    <Cancel color="disabled" />
                  )}
                </ListItemIcon>
                <ListItemText
                  primary={t('slotAssignment.preferences.equipment.recording')}
                  data-testid="av-requirement-recording"
                  className={
                    preferences?.avRequirements?.recording
                      ? 'requirement-needed'
                      : 'requirement-not-needed'
                  }
                />
              </ListItem>
            </List>
          </Box>

          <Divider />

          {/* Room Setup Notes */}
          {preferences?.roomSetupNotes && (
            <Box>
              <Typography variant="subtitle2" gutterBottom>
                {t('slotAssignment.preferences.setupNotes')}
              </Typography>
              <Paper sx={{ p: 2, bgcolor: 'background.default' }}>
                <Typography variant="body2">{preferences.roomSetupNotes}</Typography>
              </Paper>
            </Box>
          )}

          {/* Match Score Indicator */}
          {hoveredSlot && matchScore !== undefined && (
            <>
              <Divider />
              <Box>
                <Typography variant="subtitle2" gutterBottom>
                  {t('slotAssignment.preferences.slotMatchScore')}
                </Typography>
                <Paper
                  data-testid="match-score-indicator"
                  className={getMatchClass(matchScore)}
                  sx={{
                    p: 2,
                    bgcolor: getMatchColor(matchScore),
                    backgroundColor: getMatchColor(matchScore),
                    color: 'white',
                    textAlign: 'center',
                  }}
                >
                  <Typography variant="h4" fontWeight="bold">
                    {t('slotAssignment.preferences.matchPercent', { percent: matchScore })}
                  </Typography>
                  <Typography variant="body2">
                    {hoveredSlot.time} - {hoveredSlot.room}
                  </Typography>
                </Paper>
              </Box>
            </>
          )}
        </Stack>
      </Box>
    </Drawer>
  );
};
