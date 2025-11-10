import React from 'react';
import { Card, CardContent, Typography, Box } from '@mui/material';
import Grid from '@mui/material/Grid';
import { CalendarToday, EventNote, HowToVote, MeetingRoom } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';

/**
 * Partner data interface for Quick Stats display
 * TODO: statistics will be added to PartnerResponse in future backend implementation
 */
interface PartnerQuickStatsProps {
  partner: {
    partnershipStartDate: string;
    statistics?: {
      eventsAttended: number;
      lastEventName: string | null;
      activeVotes: number;
      totalMeetings: number;
    };
  };
}

/**
 * Quick Stats Card Component
 * Displays individual stat card with icon, label, and value
 */
interface StatCardProps {
  icon: React.ReactNode;
  label: string;
  value: string;
  subtitle?: string;
}

const StatCard: React.FC<StatCardProps> = ({ icon, label, value, subtitle }) => (
  <Card elevation={1} data-testid="stat-card">
    <CardContent>
      <Box display="flex" alignItems="center" gap={1} mb={1}>
        {icon}
        <Typography variant="h6" component="h3" color="text.secondary">
          {label}
        </Typography>
      </Box>
      <Typography variant="h4" component="div" fontWeight="bold">
        {value}
      </Typography>
      {subtitle && (
        <Typography variant="body2" color="text.secondary" mt={0.5}>
          {subtitle}
        </Typography>
      )}
    </CardContent>
  </Card>
);

/**
 * Calculate duration from partnership start date to now
 * Returns formatted string like "3 years" or "6 months"
 */
const calculateDuration = (startDate: string): string => {
  const start = new Date(startDate);
  const now = new Date();
  const diffTime = Math.abs(now.getTime() - start.getTime());
  const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

  const years = Math.floor(diffDays / 365);
  const months = Math.floor((diffDays % 365) / 30);

  if (years > 0) {
    return `${years} year${years !== 1 ? 's' : ''}`;
  }
  return `${months} month${months !== 1 ? 's' : ''}`;
};

/**
 * Format date to "Month Year" format (e.g., "January 2022")
 */
const formatDate = (dateString: string): string => {
  const date = new Date(dateString);
  return date.toLocaleDateString('en-US', { month: 'long', year: 'numeric' });
};

/**
 * PartnerQuickStats Component
 *
 * Displays key partner metrics in card format:
 * - Partner Since: date and duration
 * - Events Attended: count and last event
 * - Active Votes: count
 * - Meetings: count
 *
 * @param partner - Partner data with start date and statistics
 */
export const PartnerQuickStats: React.FC<PartnerQuickStatsProps> = ({ partner }) => {
  const { t } = useTranslation('partners');
  const { partnershipStartDate, statistics } = partner;

  // TODO: Remove defaults when backend implements statistics
  const eventsAttended = statistics?.eventsAttended ?? 0;
  const lastEventName = statistics?.lastEventName ?? null;
  const activeVotes = statistics?.activeVotes ?? 0;
  const totalMeetings = statistics?.totalMeetings ?? 0;

  // Calculate duration
  const duration = calculateDuration(partnershipStartDate);
  const formattedDate = formatDate(partnershipStartDate);

  // Format events subtitle
  const eventsSubtitle =
    eventsAttended > 0
      ? t('detail.quickStats.lastEvent', { event: lastEventName || 'N/A' })
      : t('detail.quickStats.noEventsYet');

  return (
    <Grid container spacing={2} sx={{ mb: 3 }}>
      {/* Partner Since Card */}
      <Grid size={{ xs: 12, sm: 6, md: 3 }}>
        <StatCard
          icon={<CalendarToday color="primary" />}
          label={t('detail.quickStats.partnerSince')}
          value={formattedDate}
          subtitle={`(${duration})`}
        />
      </Grid>

      {/* Events Attended Card */}
      <Grid size={{ xs: 12, sm: 6, md: 3 }}>
        <StatCard
          icon={<EventNote color="primary" />}
          label={t('detail.quickStats.eventsAttended')}
          value={eventsAttended.toString()}
          subtitle={eventsSubtitle}
        />
      </Grid>

      {/* Active Votes Card */}
      <Grid size={{ xs: 12, sm: 6, md: 3 }}>
        <StatCard
          icon={<HowToVote color="primary" />}
          label={t('detail.quickStats.activeVotes')}
          value={activeVotes.toString()}
          subtitle={t('detail.quickStats.votesSubtitle')}
        />
      </Grid>

      {/* Meetings Card */}
      <Grid size={{ xs: 12, sm: 6, md: 3 }}>
        <StatCard
          icon={<MeetingRoom color="primary" />}
          label={t('detail.quickStats.meetings')}
          value={totalMeetings.toString()}
          subtitle={t('detail.quickStats.meetingsSubtitle')}
        />
      </Grid>
    </Grid>
  );
};
