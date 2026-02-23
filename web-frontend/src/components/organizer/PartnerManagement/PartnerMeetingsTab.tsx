import React, { useState } from 'react';
import { Box, Card, CardContent, Typography, Stack, Chip, Button, Alert } from '@mui/material';
import { Add as AddIcon, EventNote as EventNoteIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { BATbernLoader } from '@components/shared/BATbernLoader';
import { usePartnerMeetings } from '@/hooks/usePartnerMeetings';
import CreateMeetingDialog from '@/components/organizer/CreateMeetingDialog';
import type { PartnerMeetingDTO } from '@/services/api/partnerMeetingsApi';

type UserRole = 'ORGANIZER' | 'PARTNER' | 'SPEAKER' | 'ATTENDEE';

interface PartnerMeetingsTabProps {
  companyName: string;
  role?: UserRole; // Story 8.0: hide Add Meeting for PARTNER
}

const formatMeetingDate = (dateString: string): string => {
  const date = new Date(dateString);
  return date.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
};

export const PartnerMeetingsTab: React.FC<PartnerMeetingsTabProps> = ({ companyName, role }) => {
  const { t } = useTranslation('partners');
  const { data: meetings, isLoading, error } = usePartnerMeetings(companyName);
  const isPartner = role === 'PARTNER';
  const [createOpen, setCreateOpen] = useState(false);

  if (isLoading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="200px">
        <BATbernLoader size={96} />
      </Box>
    );
  }

  if (error) {
    return <Alert severity="error">Failed to load meetings. Please try again later.</Alert>;
  }

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h6">{t('detail.meetingsTab.title')}</Typography>
        {!isPartner && (
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => setCreateOpen(true)}
            data-testid="add-meeting-button"
          >
            {t('detail.meetingsTab.addMeeting')}
          </Button>
        )}
      </Stack>

      <CreateMeetingDialog open={createOpen} onClose={() => setCreateOpen(false)} />

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
          {meetings.map((meeting: PartnerMeetingDTO) => (
            <Card key={meeting.id}>
              <CardContent>
                <Stack spacing={2}>
                  {/* Meeting Header */}
                  <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
                    <Box>
                      <Typography variant="h6" gutterBottom>
                        {meeting.agenda ?? `${meeting.meetingType} Meeting`}
                      </Typography>
                      <Stack direction="row" spacing={1} alignItems="center">
                        <Chip label={meeting.meetingType} size="small" variant="outlined" />
                        {meeting.inviteSentAt && (
                          <Chip label="Invite Sent" size="small" color="success" />
                        )}
                      </Stack>
                    </Box>
                  </Stack>

                  {/* Meeting Details */}
                  <Stack spacing={1}>
                    <Typography variant="body2" color="text.secondary">
                      <strong>Date:</strong> {formatMeetingDate(meeting.meetingDate)}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      <strong>Location:</strong> {meeting.location ?? 'TBD'}
                    </Typography>
                  </Stack>
                </Stack>
              </CardContent>
            </Card>
          ))}
        </Stack>
      )}
    </Box>
  );
};
