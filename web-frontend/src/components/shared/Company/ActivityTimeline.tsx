import React from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Button,
  Alert,
  Skeleton,
  Avatar,
  Stack,
} from '@mui/material';
import {
  Slideshow as PresentationIcon,
  Handshake as HandshakeIcon,
  PersonAdd as PersonAddIcon,
  Event as EventIcon,
  Edit as EditIcon,
  VerifiedUser as VerifiedUserIcon,
} from '@mui/icons-material';

interface Activity {
  id: string;
  type: string;
  title: string;
  description: string;
  timestamp: string;
  icon: string;
}

interface ActivityTimelineProps {
  companyId: string;
  activities?: Activity[];
  isLoading?: boolean;
  hasMore?: boolean;
  error?: string;
  onLoadMore?: () => void;
}

const getActivityIcon = (iconType: string) => {
  const iconMap: Record<string, React.ReactElement> = {
    presentation: <PresentationIcon />,
    handshake: <HandshakeIcon />,
    person_add: <PersonAddIcon />,
    event: <EventIcon />,
    edit: <EditIcon />,
    verified: <VerifiedUserIcon />,
  };
  return iconMap[iconType] || <EventIcon />;
};

const formatDate = (timestamp: string): string => {
  const date = new Date(timestamp);
  return date.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
};

const formatTime = (timestamp: string): string => {
  const date = new Date(timestamp);
  return date.toLocaleTimeString('en-US', {
    hour: '2-digit',
    minute: '2-digit',
  });
};

const groupActivitiesByDate = (activities: Activity[]): Map<string, Activity[]> => {
  const grouped = new Map<string, Activity[]>();

  activities.forEach((activity) => {
    const dateKey = formatDate(activity.timestamp);
    if (!grouped.has(dateKey)) {
      grouped.set(dateKey, []);
    }
    grouped.get(dateKey)!.push(activity);
  });

  return grouped;
};

export const ActivityTimeline: React.FC<ActivityTimelineProps> = ({
  activities = [],
  isLoading = false,
  hasMore = false,
  error,
  onLoadMore,
}) => {
  // Loading state
  if (isLoading) {
    return (
      <Box data-testid="timeline-skeleton">
        <Skeleton variant="rectangular" width="100%" height={100} sx={{ mb: 2 }} />
        <Skeleton variant="rectangular" width="100%" height={100} sx={{ mb: 2 }} />
        <Skeleton variant="rectangular" width="100%" height={100} />
      </Box>
    );
  }

  // Error state
  if (error) {
    return (
      <Alert severity="error" sx={{ mb: 2 }}>
        {error}
      </Alert>
    );
  }

  // Empty state
  if (activities.length === 0) {
    return (
      <Card>
        <CardContent>
          <Box sx={{ textAlign: 'center', py: 4 }}>
            <EventIcon sx={{ fontSize: 48, color: 'text.secondary', mb: 2 }} />
            <Typography variant="body1" color="text.secondary">
              No recent activity for this company
            </Typography>
          </Box>
        </CardContent>
      </Card>
    );
  }

  // Sort activities by timestamp (newest first)
  const sortedActivities = [...activities].sort(
    (a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
  );

  // Group activities by date
  const groupedActivities = groupActivitiesByDate(sortedActivities);

  return (
    <Box>
      {Array.from(groupedActivities.entries()).map(([date, dateActivities], groupIndex) => (
        <Box key={date} sx={{ mb: 3 }}>
          {/* Date Header */}
          <Typography variant="h6" gutterBottom>
            {date}
          </Typography>

          {/* Custom Timeline for this date */}
          <Stack spacing={2}>
            {dateActivities.map((activity, index) => (
              <Box
                key={activity.id}
                sx={{
                  display: 'flex',
                  gap: 2,
                  position: 'relative',
                  pl: 2,
                }}
              >
                {/* Timeline connector line */}
                {(index < dateActivities.length - 1 ||
                  groupIndex < groupedActivities.size - 1) && (
                  <Box
                    data-testid="timeline-connector"
                    sx={{
                      position: 'absolute',
                      left: 20,
                      top: 48,
                      bottom: -16,
                      width: 2,
                      bgcolor: 'grey.300',
                    }}
                  />
                )}

                {/* Time */}
                <Box sx={{ minWidth: 80, pt: 1 }}>
                  <Typography variant="body2" color="text.secondary">
                    {formatTime(activity.timestamp)}
                  </Typography>
                </Box>

                {/* Icon */}
                <Avatar
                  data-testid={`activity-icon-${activity.icon}`}
                  sx={{
                    bgcolor: 'primary.main',
                    width: 40,
                    height: 40,
                  }}
                >
                  {getActivityIcon(activity.icon)}
                </Avatar>

                {/* Content */}
                <Box sx={{ flex: 1 }}>
                  <Card variant="outlined">
                    <CardContent>
                      <Typography
                        variant="subtitle1"
                        component="h3"
                        gutterBottom
                        data-testid={`activity-title-${activity.id}`}
                      >
                        {activity.title}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {activity.description}
                      </Typography>
                    </CardContent>
                  </Card>
                </Box>
              </Box>
            ))}
          </Stack>
        </Box>
      ))}

      {/* Load More Button */}
      {hasMore && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 3 }}>
          <Button variant="outlined" onClick={onLoadMore}>
            Load More
          </Button>
        </Box>
      )}
    </Box>
  );
};
