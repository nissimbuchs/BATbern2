import React, { useState } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Avatar,
  Stack,
  Chip,
  CircularProgress,
  Alert,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Pagination,
} from '@mui/material';
import {
  HowToVote,
  EventNote,
  PersonAdd,
  TrendingUp,
  Note,
  Timeline as TimelineIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { usePartnerActivity } from '@/hooks/usePartnerActivity';

interface PartnerActivityTabProps {
  companyName: string;
}

// TODO: Remove when backend implements ActivityResponse with all fields
// interface Activity {
//   id: string;
//   type: string;
//   timestamp: string;
//   username: string;
//   description: string;
//   details?: Record<string, any>;
// }

// Map activity types to icons
const activityTypeIcons: Record<string, React.ReactElement> = {
  VOTE_CAST: <HowToVote data-testid="icon-VOTE_CAST" />,
  MEETING_ATTENDED: <EventNote data-testid="icon-MEETING_ATTENDED" />,
  CONTACT_ADDED: <PersonAdd data-testid="icon-CONTACT_ADDED" />,
  TIER_CHANGED: <TrendingUp data-testid="icon-TIER_CHANGED" />,
  NOTE_ADDED: <Note data-testid="icon-NOTE_ADDED" />,
};

// Format timestamp: relative for recent, absolute for old
const formatTimestamp = (timestamp: string): string => {
  const date = new Date(timestamp);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffHours = diffMs / (1000 * 60 * 60);
  const diffDays = diffMs / (1000 * 60 * 60 * 24);

  // Show relative time for recent activities (within 7 days)
  if (diffDays < 7) {
    if (diffHours < 1) {
      const diffMinutes = Math.floor(diffMs / (1000 * 60));
      return `${diffMinutes} ${diffMinutes === 1 ? 'minute' : 'minutes'} ago`;
    }
    if (diffHours < 24) {
      const hours = Math.floor(diffHours);
      return `${hours} ${hours === 1 ? 'hour' : 'hours'} ago`;
    }
    const days = Math.floor(diffDays);
    return `${days} ${days === 1 ? 'day' : 'days'} ago`;
  }

  // Show absolute date for older activities
  return date.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });
};

const PartnerActivityTab: React.FC<PartnerActivityTabProps> = ({ companyName }) => {
  const { t } = useTranslation('partners');
  const [activityTypeFilter, setActivityTypeFilter] = useState<string>('ALL');
  const [currentPage, setCurrentPage] = useState<number>(1);
  const itemsPerPage = 20;

  const {
    data: activities,
    isLoading,
    error,
  } = usePartnerActivity(companyName, {
    type: activityTypeFilter !== 'ALL' ? activityTypeFilter : undefined,
  });

  if (isLoading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="300px">
        <CircularProgress />
      </Box>
    );
  }

  if (error) {
    return (
      <Alert severity="error">
        <Typography variant="h6">Failed to load activity</Typography>
        <Typography variant="body2">{(error as Error).message}</Typography>
      </Alert>
    );
  }

  if (!activities || activities.length === 0) {
    return (
      <Box textAlign="center" py={6}>
        <TimelineIcon sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
        <Typography variant="h6" color="text.secondary">
          {t('detail.activityTab.noActivity')}
        </Typography>
      </Box>
    );
  }

  // Sort activities by timestamp (most recent first)
  const sortedActivities = [...activities].sort(
    (a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
  );

  // Paginate activities
  const totalPages = Math.ceil(sortedActivities.length / itemsPerPage);
  const startIndex = (currentPage - 1) * itemsPerPage;
  const endIndex = startIndex + itemsPerPage;
  const paginatedActivities = sortedActivities.slice(startIndex, endIndex);

  const handleFilterChange = (event: any) => {
    setActivityTypeFilter(event.target.value);
    setCurrentPage(1); // Reset to first page when filter changes
  };

  const handlePageChange = (_event: React.ChangeEvent<unknown>, page: number) => {
    setCurrentPage(page);
  };

  return (
    <Box>
      {/* Header with filter */}
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h6">{t('detail.activityTab.title')}</Typography>
        <FormControl size="small" sx={{ minWidth: 200 }}>
          <InputLabel id="activity-type-filter-label">
            {t('detail.activityTab.filterLabel')}
          </InputLabel>
          <Select
            labelId="activity-type-filter-label"
            id="activity-type-filter"
            value={activityTypeFilter}
            label={t('detail.activityTab.filterLabel')}
            onChange={handleFilterChange}
          >
            <MenuItem value="ALL">All Activities</MenuItem>
            <MenuItem value="VOTE_CAST">Vote Cast</MenuItem>
            <MenuItem value="MEETING_ATTENDED">Meeting Attended</MenuItem>
            <MenuItem value="CONTACT_ADDED">Contact Added</MenuItem>
            <MenuItem value="TIER_CHANGED">Tier Changed</MenuItem>
            <MenuItem value="NOTE_ADDED">Note Added</MenuItem>
          </Select>
        </FormControl>
      </Stack>

      {/* Activity timeline */}
      <Stack spacing={2}>
        {paginatedActivities.map((activity) => (
          <Card key={activity.id} data-testid={`activity-item-${activity.id}`}>
            <CardContent>
              <Stack direction="row" spacing={2} alignItems="flex-start">
                {/* Activity type icon */}
                <Avatar sx={{ bgcolor: 'primary.light' }}>
                  {activityTypeIcons[activity.type] || <TimelineIcon />}
                </Avatar>

                {/* Activity details */}
                <Box flex={1}>
                  <Typography variant="body1" fontWeight="medium">
                    {activity.description}
                  </Typography>
                  <Stack direction="row" spacing={2} alignItems="center" mt={1}>
                    <Chip
                      label={(activity as any).username || 'System'}
                      size="small"
                      variant="outlined"
                    />
                    <Typography variant="body2" color="text.secondary">
                      {formatTimestamp(activity.timestamp)}
                    </Typography>
                  </Stack>
                </Box>
              </Stack>
            </CardContent>
          </Card>
        ))}
      </Stack>

      {/* Pagination */}
      {totalPages > 1 && (
        <Box display="flex" justifyContent="center" mt={4}>
          <Pagination
            count={totalPages}
            page={currentPage}
            onChange={handlePageChange}
            color="primary"
          />
        </Box>
      )}
    </Box>
  );
};

export default PartnerActivityTab;
