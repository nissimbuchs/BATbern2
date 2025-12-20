/**
 * Status History Timeline Component (Story 5.4)
 *
 * Timeline displaying speaker status change history
 * Features:
 * - Chronological timeline of all status transitions
 * - Shows timestamp, organizer, status change, and reason
 * - Material-UI Timeline components
 * - i18n support (German/English)
 * - Color-coded status indicators
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
import { Paper, Typography, Box, Chip, Alert } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { speakerStatusService } from '@/services/speakerStatusService';
import { format } from 'date-fns';

export interface StatusHistoryTimelineProps {
  speakerId: string;
  eventCode: string;
}

// Status color mapping
const STATUS_COLORS: Record<string, string> = {
  IDENTIFIED: '#9e9e9e',
  CONTACTED: '#ffc107',
  READY: '#ff9800',
  ACCEPTED: '#4caf50',
  DECLINED: '#f44336',
  SLOT_ASSIGNED: '#2196f3',
  QUALITY_REVIEWED: '#00bcd4',
  FINAL_AGENDA: '#9c27b0',
};

export const StatusHistoryTimeline: React.FC<StatusHistoryTimelineProps> = ({
  speakerId,
  eventCode,
}) => {
  const { t } = useTranslation(['organizer', 'common']);

  const {
    data: history,
    isLoading,
    error,
  } = useQuery({
    queryKey: ['speakerStatusHistory', eventCode, speakerId],
    queryFn: () => speakerStatusService.getStatusHistory(eventCode, speakerId),
  });

  if (isLoading) {
    return (
      <Box sx={{ p: 2 }}>
        <Typography>{t('common:loading')}</Typography>
      </Box>
    );
  }

  if (error) {
    return (
      <Alert severity="error" sx={{ m: 2 }}>
        {t('common:errors.loadFailed')}
      </Alert>
    );
  }

  if (!history || history.length === 0) {
    return (
      <Box sx={{ p: 2 }}>
        <Typography color="text.secondary">{t('organizer:speakerStatus.noHistory')}</Typography>
      </Box>
    );
  }

  return (
    <Box>
      <Typography variant="h6" sx={{ mb: 2 }}>
        {t('organizer:speakerStatus.historyTitle')}
      </Typography>

      <Timeline position="alternate">
        {history.map((item, index) => {
          const isLast = index === history.length - 1;
          const statusColor = STATUS_COLORS[item.newStatus || 'IDENTIFIED'];

          return (
            <TimelineItem key={item.id}>
              <TimelineOppositeContent sx={{ m: 'auto 0' }} variant="body2" color="text.secondary">
                {item.changedAt ? format(new Date(item.changedAt), 'PPp') : ''}
              </TimelineOppositeContent>

              <TimelineSeparator>
                <TimelineDot sx={{ bgcolor: statusColor }} />
                {!isLast && <TimelineConnector />}
              </TimelineSeparator>

              <TimelineContent sx={{ py: '12px', px: 2 }}>
                <Paper elevation={2} sx={{ p: 2 }}>
                  <Box sx={{ display: 'flex', gap: 1, mb: 1, flexWrap: 'wrap' }}>
                    <Chip
                      label={t(`organizer:speakerStatus.${item.previousStatus}`)}
                      size="small"
                      sx={{
                        backgroundColor: STATUS_COLORS[item.previousStatus || 'IDENTIFIED'],
                        color: 'white',
                      }}
                    />
                    <Typography variant="body2" sx={{ alignSelf: 'center' }}>
                      →
                    </Typography>
                    <Chip
                      label={t(`organizer:speakerStatus.${item.newStatus}`)}
                      size="small"
                      sx={{
                        backgroundColor: statusColor,
                        color: 'white',
                      }}
                    />
                  </Box>

                  <Typography variant="caption" color="text.secondary" display="block">
                    {t('organizer:speakerStatus.changedBy')}: {item.changedByUsername}
                  </Typography>

                  {item.changeReason && (
                    <Typography variant="body2" sx={{ mt: 1, fontStyle: 'italic' }}>
                      "{item.changeReason}"
                    </Typography>
                  )}
                </Paper>
              </TimelineContent>
            </TimelineItem>
          );
        })}
      </Timeline>
    </Box>
  );
};

export default StatusHistoryTimeline;
