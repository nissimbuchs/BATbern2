import React from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Skeleton,
} from '@mui/material';
import Grid from '@mui/material/Grid';
import {
  Event as EventIcon,
  RecordVoiceOver as SpeakerIcon,
  Handshake as PartnerIcon,
} from '@mui/icons-material';
import { CompanyStatistics as CompanyStatisticsType } from '@/types/company.types';

interface CompanyStatisticsProps {
  statistics: CompanyStatisticsType | null | undefined;
  isLoading?: boolean;
}

interface StatCardProps {
  title: string;
  value: number;
  icon: React.ReactNode;
  testId: string;
}

const StatCard: React.FC<StatCardProps> = ({ title, value, icon, testId }) => (
  <Card data-testid={testId}>
    <CardContent>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: 48,
            height: 48,
            borderRadius: 2,
            bgcolor: 'primary.light',
            color: 'primary.contrastText',
          }}
        >
          {icon}
        </Box>
        <Box>
          <Typography variant="body2" color="text.secondary">
            {title}
          </Typography>
          <Typography variant="h4">{value}</Typography>
        </Box>
      </Box>
    </CardContent>
  </Card>
);

export const CompanyStatistics: React.FC<CompanyStatisticsProps> = ({
  statistics,
  isLoading = false,
}) => {
  const isMobile = window.innerWidth < 600;

  // Loading state
  if (isLoading) {
    return (
      <Box data-testid="statistics-skeleton">
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, sm: 4 }}>
            <Skeleton variant="rectangular" width="100%" height={100} />
          </Grid>
          <Grid size={{ xs: 12, sm: 4 }}>
            <Skeleton variant="rectangular" width="100%" height={100} />
          </Grid>
          <Grid size={{ xs: 12, sm: 4 }}>
            <Skeleton variant="rectangular" width="100%" height={100} />
          </Grid>
        </Grid>
      </Box>
    );
  }

  // Empty state
  if (!statistics) {
    return (
      <Card>
        <CardContent>
          <Box sx={{ textAlign: 'center', py: 4 }}>
            <Typography variant="body1" color="text.secondary">
              No statistics available for this company
            </Typography>
          </Box>
        </CardContent>
      </Card>
    );
  }

  return (
    <Box data-testid="statistics-container" className={isMobile ? 'mobile-layout' : ''}>
      {/* Statistics Cards - Only totalEvents, totalSpeakers, totalPartners */}
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, sm: 4 }}>
          <StatCard
            title="Total Events"
            value={statistics.totalEvents ?? 0}
            icon={<EventIcon />}
            testId="events-card"
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 4 }}>
          <StatCard
            title="Total Speakers"
            value={statistics.totalSpeakers ?? 0}
            icon={<SpeakerIcon />}
            testId="speakers-card"
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 4 }}>
          <StatCard
            title="Total Partners"
            value={statistics.totalPartners ?? 0}
            icon={<PartnerIcon />}
            testId="partners-card"
          />
        </Grid>
      </Grid>
    </Box>
  );
};
