import React from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Skeleton,
  Stack,
} from '@mui/material';
import Grid from '@mui/material/Grid';
import {
  Event as EventIcon,
  Slideshow as PresentationIcon,
  People as PeopleIcon,
} from '@mui/icons-material';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
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
        <Skeleton variant="rectangular" width="100%" height={300} sx={{ mt: 3 }} />
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

  // Prepare chart data
  const chartData = statistics.topicExpertise.map((topic) => ({
    name: topic.topic,
    count: topic.count,
  }));

  return (
    <Box data-testid="statistics-container" className={isMobile ? 'mobile-layout' : ''}>
      {/* Statistics Cards */}
      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid size={{ xs: 12, sm: 4 }}>
          <StatCard
            title="Total Events"
            value={statistics.totalEvents}
            icon={<EventIcon />}
            testId="events-card"
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 4 }}>
          <StatCard
            title="Total Presentations"
            value={statistics.totalPresentations}
            icon={<PresentationIcon />}
            testId="presentations-card"
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 4 }}>
          <StatCard
            title="Total Attendees"
            value={statistics.totalAttendees}
            icon={<PeopleIcon />}
            testId="attendees-card"
          />
        </Grid>
      </Grid>

      {/* Event Date Range */}
      {statistics.firstEvent && statistics.mostRecentEvent && (
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Event History
            </Typography>
            <Stack spacing={1}>
              <Box>
                <Typography variant="body2" color="text.secondary">
                  First Event
                </Typography>
                <Typography variant="body1">{statistics.firstEvent}</Typography>
              </Box>
              <Box>
                <Typography variant="body2" color="text.secondary">
                  Most Recent Event
                </Typography>
                <Typography variant="body1">{statistics.mostRecentEvent}</Typography>
              </Box>
            </Stack>
          </CardContent>
        </Card>
      )}

      {/* Topic Expertise Chart */}
      {statistics.topicExpertise.length > 0 && (
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Topic Expertise
            </Typography>
            <Box data-testid="topic-expertise-chart" sx={{ width: '100%', height: 300 }}>
              <ResponsiveContainer width="100%" height="100%">
                <BarChart
                  data={chartData}
                  margin={{ top: 20, right: 30, left: 20, bottom: 60 }}
                >
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis
                    dataKey="name"
                    angle={-45}
                    textAnchor="end"
                    height={80}
                    interval={0}
                  />
                  <YAxis />
                  <Tooltip />
                  <Bar dataKey="count" fill="#1976d2" />
                </BarChart>
              </ResponsiveContainer>
            </Box>
            <Box sx={{ mt: 2 }}>
              <Typography variant="body2" color="text.secondary" gutterBottom>
                Presentations by Topic:
              </Typography>
              {statistics.topicExpertise.map((topic) => (
                <Typography key={topic.topic} variant="body2">
                  • {topic.topic}: {topic.count} presentation{topic.count !== 1 ? 's' : ''}
                </Typography>
              ))}
            </Box>
          </CardContent>
        </Card>
      )}
    </Box>
  );
};
