import React from 'react';
import { Box, Card, CardContent, Skeleton, Typography } from '@mui/material';
import Grid from '@mui/material/Grid';
import { usePartnerStatistics } from '@/hooks/usePartners';

const tierEmojis: Record<string, string> = {
  STRATEGIC: '🏆',
  PLATINUM: '💎',
  GOLD: '🥇',
  SILVER: '🥈',
  BRONZE: '🥉',
};

const tierLabels: Record<string, string> = {
  STRATEGIC: 'Strategic',
  PLATINUM: 'Platinum',
  GOLD: 'Gold',
  SILVER: 'Silver',
  BRONZE: 'Bronze',
};

export const PartnerOverviewStats: React.FC = () => {
  const { data: statistics, isLoading, isError, error } = usePartnerStatistics();

  // Loading state
  if (isLoading) {
    return (
      <Box sx={{ mb: 3 }}>
        <Grid container spacing={2}>
          {[1, 2, 3, 4].map((i) => (
            <Grid size={{ xs: 12, sm: 6, md: 3 }} key={i}>
              <Card>
                <CardContent>
                  <Skeleton variant="text" width="60%" />
                  <Skeleton variant="text" width="40%" height={40} />
                </CardContent>
              </Card>
            </Grid>
          ))}
        </Grid>
      </Box>
    );
  }

  // Error state
  if (isError) {
    return (
      <Box sx={{ mb: 3 }}>
        <Card>
          <CardContent>
            <Typography color="error">
              Failed to load statistics: {error?.message || 'Unknown error'}
            </Typography>
          </CardContent>
        </Card>
      </Box>
    );
  }

  // Default values when no data
  const totalPartners = statistics?.totalPartners || 0;
  const activePartners = statistics?.activePartners || 0;
  const tierDistribution = statistics?.tierCounts || {
    STRATEGIC: 0,
    PLATINUM: 0,
    GOLD: 0,
    SILVER: 0,
    BRONZE: 0,
  };

  return (
    <Box sx={{ mb: 3 }}>
      <Grid container spacing={2}>
        {/* Total Partners */}
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Card data-testid="stats-total-partners">
            <CardContent>
              <Typography color="text.secondary" variant="body2" gutterBottom>
                Total Partners
              </Typography>
              <Typography variant="h4" component="div">
                {totalPartners}
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        {/* Active Partners */}
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Card data-testid="stats-active-partners">
            <CardContent>
              <Typography color="text.secondary" variant="body2" gutterBottom>
                Active Partners
              </Typography>
              <Typography variant="h4" component="div">
                {activePartners}
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        {/* Engaged Partners - Placeholder for Epic 8 */}
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Card data-testid="stats-engaged-partners">
            <CardContent>
              <Typography color="text.secondary" variant="body2" gutterBottom>
                Engaged Partners
              </Typography>
              <Typography variant="body2" component="div" color="text.disabled">
                Coming Soon - Epic 8
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        {/* Tier Distribution */}
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Card data-testid="stats-tier-distribution">
            <CardContent>
              <Typography color="text.secondary" variant="body2" gutterBottom>
                Tier Distribution
              </Typography>
              <Box>
                {Object.entries(tierDistribution).map(([tier, count]) => (
                  <Typography key={tier} variant="body2" component="div">
                    {tierEmojis[tier]} {tierLabels[tier]}: {count}
                  </Typography>
                ))}
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
};
