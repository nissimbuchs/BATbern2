/**
 * Speaker Status Dashboard Component (Story 5.4)
 *
 * Dashboard displaying speaker status summary and metrics
 * Features:
 * - Status counts by workflow state
 * - Acceptance rate percentage
 * - Progress bar showing accepted/required speakers
 * - Threshold and overflow indicators
 * - Real-time updates via React Query (30s polling)
 * - i18n support (German/English)
 */

import React from 'react';
import {
  Paper,
  Card,
  CardContent,
  Typography,
  LinearProgress,
  Alert,
  Grid,
  Chip,
  Box,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { speakerStatusService } from '@/services/speakerStatusService';
import { speakerPoolService } from '@/services/speakerPoolService';
import { SpeakerStatusLanes } from './SpeakerStatusLanes';

export interface SpeakerStatusDashboardProps {
  eventCode: string;
}

// Status color mapping (Story 5.5 - Extended to 8 lanes)
const STATUS_COLORS: Record<string, string> = {
  IDENTIFIED: '#9e9e9e', // Gray
  CONTACTED: '#ffc107', // Amber/Yellow
  READY: '#ff9800', // Orange
  ACCEPTED: '#4caf50', // Green
  CONTENT_SUBMITTED: '#fbc02d', // Yellow (NEW - Story 5.5)
  QUALITY_REVIEWED: '#7cb342', // Light Green (NEW - Story 5.5)
  CONFIRMED: '#2e7d32', // Dark Green (NEW - Story 5.5)
  DECLINED: '#f44336', // Red
};

export const SpeakerStatusDashboard: React.FC<SpeakerStatusDashboardProps> = ({ eventCode }) => {
  const { t } = useTranslation(['organizer', 'common']);

  // Fetch status summary with 30s polling (AC8)
  const {
    data: summary,
    isLoading,
    error,
  } = useQuery({
    queryKey: ['speakerStatusSummary', eventCode],
    queryFn: () => speakerStatusService.getStatusSummary(eventCode),
    refetchInterval: 30000, // Poll every 30 seconds
    staleTime: 15000, // Consider data stale after 15 seconds
  });

  // Fetch speaker pool data for lanes
  const { data: speakers, isLoading: speakersLoading } = useQuery({
    queryKey: ['speakerPool', eventCode],
    queryFn: () => speakerPoolService.getSpeakerPool(eventCode),
    refetchInterval: 30000, // Poll every 30 seconds
    staleTime: 15000,
  });

  if (isLoading || speakersLoading) {
    return (
      <Box>
        <LinearProgress />
      </Box>
    );
  }

  if (error) {
    return (
      <Box>
        <Alert severity="error">{t('common:errors.loadFailed')}</Alert>
      </Box>
    );
  }

  if (!summary || !speakers) {
    return null;
  }

  const progressPercentage =
    summary.minSlotsRequired && summary.minSlotsRequired > 0
      ? (summary.acceptedCount ?? 0 / (summary.minSlotsRequired ?? 1)) * 100
      : 0;

  return (
    <Box sx={{ mt: 4, mb: 4 }}>
      <Typography variant="h4" gutterBottom>
        {t('organizer:speakerStatus.title')}
      </Typography>

      {/* Overflow Warning (AC13) */}
      {summary.overflowDetected && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          {t('organizer:speakerStatus.overflowDetected', {
            count: summary.acceptedCount,
            max: summary.maxSlotsAllowed,
          })}
        </Alert>
      )}

      {/* Status Summary Card (AC5-6) */}
      <Paper sx={{ p: 3, mb: 3 }}>
        <Typography variant="h6" gutterBottom>
          {t('organizer:speakerStatus.statusSummary')}
        </Typography>

        {/* Progress Bar (AC8) */}
        <Box sx={{ mb: 3 }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
            <Typography variant="body2">
              {t('organizer:speakerStatus.progress', {
                accepted: summary.acceptedCount,
                required: summary.minSlotsRequired,
              })}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {summary.acceptanceRate?.toFixed(1)}%
            </Typography>
          </Box>
          <LinearProgress
            variant="determinate"
            value={Math.min(progressPercentage, 100)}
            sx={{ height: 10, borderRadius: 5 }}
          />
        </Box>

        {/* Threshold Indicator */}
        {summary.thresholdMet ? (
          <Alert severity="success" sx={{ mb: 2 }}>
            {t('organizer:speakerStatus.thresholdMet')}
          </Alert>
        ) : (
          <Alert severity="warning" sx={{ mb: 2 }}>
            {t('organizer:speakerStatus.thresholdNotMet', {
              count: summary.acceptedCount,
              required: summary.minSlotsRequired,
            })}
          </Alert>
        )}

        {/* Status Counts (AC5) */}
        <Grid container spacing={2}>
          {Object.entries(summary.statusCounts || {}).map(([status, count]) => (
            <Grid size={{ xs: 6, sm: 4, md: 3 }} key={status}>
              <Card variant="outlined">
                <CardContent>
                  <Chip
                    label={t(`organizer:speakerStatus.${status}`)}
                    size="small"
                    sx={{
                      backgroundColor: STATUS_COLORS[status] || '#9e9e9e',
                      color: 'white',
                      mb: 1,
                    }}
                  />
                  <Typography variant="h4">{count}</Typography>
                </CardContent>
              </Card>
            </Grid>
          ))}
        </Grid>
      </Paper>

      {/* Speaker Status Lanes (Kanban Board) - AC4 */}
      <SpeakerStatusLanes eventCode={eventCode} speakers={speakers} />

      {/* Status History Timeline - AC15 - TODO: Implement in speaker detail view with speakerId */}
    </Box>
  );
};

export default SpeakerStatusDashboard;
