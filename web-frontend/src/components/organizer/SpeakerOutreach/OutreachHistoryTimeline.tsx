/**
 * Outreach History Timeline Component (Story 5.3 - Task 6b)
 *
 * Displays chronological contact history for a speaker
 * Features:
 * - Material-UI Timeline visualization
 * - Contact method icons
 * - Chronological ordering (most recent first)
 * - Contact notes display
 * - Organizer information
 */

import React from 'react';
import {
  Timeline,
  TimelineItem,
  TimelineSeparator,
  TimelineConnector,
  TimelineContent,
  TimelineDot,
  TimelineOppositeContent,
} from '@mui/lab';
import { Box, Typography, Paper, Chip, Alert } from '@mui/material';
import { Email as EmailIcon, Phone as PhoneIcon, Person as PersonIcon } from '@mui/icons-material';
import { BATbernLoader } from '@components/shared/BATbernLoader';
import { useTranslation } from 'react-i18next';
import { format } from 'date-fns';
import { useSpeakerOutreachHistory } from '../../../hooks/useSpeakerOutreach';
import type { ContactMethod } from '../../../types/speakerOutreach.types';

interface OutreachHistoryTimelineProps {
  eventCode: string;
  speakerId: string;
}

const OutreachHistoryTimeline: React.FC<OutreachHistoryTimelineProps> = ({
  eventCode,
  speakerId,
}) => {
  const { t } = useTranslation('organizer');
  const { data: history, isLoading, isError } = useSpeakerOutreachHistory(eventCode, speakerId);

  // Get icon for contact method
  const getContactMethodIcon = (method: ContactMethod) => {
    switch (method) {
      case 'email':
        return <EmailIcon />;
      case 'phone':
        return <PhoneIcon />;
      case 'in_person':
        return <PersonIcon />;
      default:
        return <EmailIcon />;
    }
  };

  // Get color for TimelineDot (supports 'grey' but not 'default')
  const getTimelineDotColor = (
    method: ContactMethod
  ): 'primary' | 'secondary' | 'success' | 'grey' => {
    switch (method) {
      case 'email':
        return 'primary';
      case 'phone':
        return 'secondary';
      case 'in_person':
        return 'success';
      default:
        return 'grey';
    }
  };

  // Get color for Chip (supports 'default' but not 'grey')
  const getChipColor = (method: ContactMethod): 'primary' | 'secondary' | 'success' | 'default' => {
    switch (method) {
      case 'email':
        return 'primary';
      case 'phone':
        return 'secondary';
      case 'in_person':
        return 'success';
      default:
        return 'default';
    }
  };

  if (isLoading) {
    return (
      <Box display="flex" justifyContent="center" p={3}>
        <BATbernLoader size={32} />
      </Box>
    );
  }

  if (isError) {
    return <Alert severity="error">Failed to load contact history</Alert>;
  }

  if (!history || history.length === 0) {
    return (
      <Paper sx={{ p: 3 }}>
        <Typography variant="body2" color="text.secondary" textAlign="center">
          No contact history yet
        </Typography>
      </Paper>
    );
  }

  return (
    <Box>
      <Typography variant="h6" gutterBottom>
        {t('speakerOutreach.contactHistory')}
      </Typography>

      <Timeline position="right" data-testid="outreach-timeline">
        {history.map((item, index) => (
          <TimelineItem key={item.id}>
            <TimelineOppositeContent color="text.secondary" sx={{ flex: 0.3 }}>
              <Typography variant="caption" display="block">
                {format(new Date(item.contactDate), 'MMM dd, yyyy')}
              </Typography>
              <Typography variant="caption" display="block">
                {format(new Date(item.contactDate), 'h:mm a')}
              </Typography>
            </TimelineOppositeContent>

            <TimelineSeparator>
              <TimelineDot color={getTimelineDotColor(item.contactMethod)}>
                {getContactMethodIcon(item.contactMethod)}
              </TimelineDot>
              {index < history.length - 1 && <TimelineConnector />}
            </TimelineSeparator>

            <TimelineContent>
              <Paper elevation={1} sx={{ p: 2, mb: 2 }}>
                <Box display="flex" gap={1} mb={1} alignItems="center">
                  <Chip
                    label={t(
                      `speakerOutreach.markContactedModal.method.${item.contactMethod === 'in_person' ? 'inPerson' : item.contactMethod}`
                    )}
                    size="small"
                    color={getChipColor(item.contactMethod)}
                  />
                  <Typography variant="caption" color="text.secondary">
                    by {item.organizerUsername}
                  </Typography>
                </Box>

                {item.notes && (
                  <Typography variant="body2" color="text.secondary">
                    {item.notes}
                  </Typography>
                )}
              </Paper>
            </TimelineContent>
          </TimelineItem>
        ))}
      </Timeline>
    </Box>
  );
};

export default OutreachHistoryTimeline;
