import React from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Stack,
  Chip,
  Button,
  CircularProgress,
  Alert,
  Link,
  Divider,
} from '@mui/material';
import { Add as AddIcon, EventNote as EventNoteIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { usePartnerMeetings } from '@/hooks/usePartnerMeetings';

interface PartnerMeetingsTabProps {
  companyName: string;
}

// TODO: Remove when backend implements MeetingResponse with all fields
// interface Meeting {
//   id: string;
//   meetingType: string;
//   scheduledDate: string;
//   location: string;
//   agenda: string;
//   rsvpStatus?: string;
//   materials?: Array<{
//     name: string;
//     url: string;
//   }>;
// }

const formatMeetingDate = (dateString: string): string => {
  const date = new Date(dateString);
  return date.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
};

const getRsvpColor = (status: string): 'success' | 'warning' | 'default' => {
  if (status === 'CONFIRMED') return 'success';
  if (status === 'PENDING') return 'warning';
  return 'default';
};

export const PartnerMeetingsTab: React.FC<PartnerMeetingsTabProps> = ({ companyName }) => {
  const { t } = useTranslation('partners');
  const { data: meetings, isLoading, error } = usePartnerMeetings(companyName);

  if (isLoading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="200px">
        <CircularProgress />
      </Box>
    );
  }

  if (error) {
    return <Alert severity="error">Failed to load meetings. Please try again later.</Alert>;
  }

  return (
    <Box>
      {/* Header with Epic 8 message */}
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={3}>
        <Box>
          <Typography variant="h6" gutterBottom>
            {t('detail.meetingsTab.title')}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {t('detail.header.comingSoon')}
          </Typography>
        </Box>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          disabled
          title={t('detail.header.comingSoon')}
        >
          {t('detail.meetingsTab.addMeeting')}
        </Button>
      </Stack>

      {/* Meetings List */}
      {!meetings || meetings.length === 0 ? (
        <Card>
          <CardContent>
            <Stack alignItems="center" spacing={2} py={4}>
              <EventNoteIcon sx={{ fontSize: 64, color: 'text.secondary' }} />
              <Typography variant="h6" color="text.secondary">
                No meetings scheduled
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Schedule meetings to track partnership coordination
              </Typography>
            </Stack>
          </CardContent>
        </Card>
      ) : (
        <Stack spacing={2}>
          {meetings.map((meeting) => (
            <Card key={meeting.id}>
              <CardContent>
                <Stack spacing={2}>
                  {/* Meeting Header */}
                  <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
                    <Box>
                      <Typography variant="h6" gutterBottom>
                        {(meeting as any).agenda || meeting.title}
                      </Typography>
                      <Stack direction="row" spacing={1} alignItems="center">
                        <Chip
                          label={(meeting as any).meetingType || 'Meeting'}
                          size="small"
                          variant="outlined"
                        />
                        {(meeting as any).rsvpStatus && (
                          <Chip
                            label={(meeting as any).rsvpStatus}
                            size="small"
                            color={getRsvpColor((meeting as any).rsvpStatus)}
                          />
                        )}
                      </Stack>
                    </Box>
                  </Stack>

                  {/* Meeting Details */}
                  <Stack spacing={1}>
                    <Typography variant="body2" color="text.secondary">
                      <strong>Date:</strong>{' '}
                      {formatMeetingDate((meeting as any).scheduledDate || meeting.date)}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      <strong>Location:</strong> {(meeting as any).location || 'TBD'}
                    </Typography>
                  </Stack>

                  {/* Meeting Materials */}
                  {(meeting as any).materials && (meeting as any).materials.length > 0 && (
                    <>
                      <Divider />
                      <Box>
                        <Typography variant="subtitle2" gutterBottom>
                          Meeting Materials
                        </Typography>
                        <Stack spacing={1}>
                          {(meeting as any).materials.map((material: any, index: number) => (
                            <Link
                              key={index}
                              href={material.url}
                              target="_blank"
                              rel="noopener noreferrer"
                              underline="hover"
                            >
                              {material.name}
                            </Link>
                          ))}
                        </Stack>
                      </Box>
                    </>
                  )}
                </Stack>
              </CardContent>
            </Card>
          ))}
        </Stack>
      )}
    </Box>
  );
};
