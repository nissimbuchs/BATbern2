import React, { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Collapse,
  IconButton,
  Stack,
  Typography,
} from '@mui/material';
import {
  Add as AddIcon,
  EventNote as EventNoteIcon,
  KeyboardArrowDown as ArrowDownIcon,
  KeyboardArrowUp as ArrowUpIcon,
} from '@mui/icons-material';
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

const formatTime = (timeString: string | null | undefined): string | null => {
  if (!timeString) return null;
  return timeString.slice(0, 5);
};

interface MeetingCardProps {
  meeting: PartnerMeetingDTO;
  isOrganizer: boolean;
  t: (key: string, opts?: Record<string, unknown>) => string;
}

const MeetingCard: React.FC<MeetingCardProps> = ({ meeting, isOrganizer, t }) => {
  const [expanded, setExpanded] = useState(false);

  const startTime = formatTime(meeting.startTime);
  const endTime = formatTime(meeting.endTime);
  const timeDisplay =
    startTime && endTime ? `${startTime} – ${endTime}` : startTime ? startTime : null;

  return (
    <Card>
      <CardContent>
        <Stack spacing={1}>
          {/* Meeting Header */}
          <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
            <Box sx={{ flex: 1 }}>
              <Typography variant="h6" gutterBottom>
                {meeting.agenda ?? `${meeting.meetingType} Meeting`}
              </Typography>
              <Stack direction="row" spacing={1} alignItems="center">
                <Chip
                  label={t(
                    meeting.meetingType === 'SPRING'
                      ? 'meetings.fields.type.spring'
                      : 'meetings.fields.type.autumn'
                  )}
                  size="small"
                  variant="outlined"
                />
                {meeting.inviteSentAt && (
                  <Chip label={t('meetings.inviteSent')} size="small" color="success" />
                )}
              </Stack>
            </Box>
            <IconButton
              size="small"
              onClick={() => setExpanded((prev) => !prev)}
              aria-label={expanded ? 'Collapse' : 'Expand'}
            >
              {expanded ? <ArrowUpIcon /> : <ArrowDownIcon />}
            </IconButton>
          </Stack>

          {/* Summary row: date, time, location */}
          <Stack direction="row" spacing={2} flexWrap="wrap">
            <Typography variant="body2" color="text.secondary">
              <strong>{t('meetings.columns.date')}:</strong>{' '}
              {formatMeetingDate(meeting.meetingDate)}
            </Typography>
            {timeDisplay && (
              <Typography variant="body2" color="text.secondary">
                <strong>{t('meetings.columns.time')}:</strong> {timeDisplay}
              </Typography>
            )}
            <Typography variant="body2" color="text.secondary">
              <strong>{t('meetings.columns.location')}:</strong> {meeting.location ?? 'TBD'}
            </Typography>
          </Stack>

          {/* Collapsible agenda (read-only for partners, and organizers) */}
          <Collapse in={expanded} timeout="auto" unmountOnExit>
            <Box sx={{ pt: 1 }}>
              <Typography variant="subtitle2" gutterBottom>
                {t('meetings.fields.agenda')}
              </Typography>
              <Typography
                variant="body2"
                color={meeting.agenda ? 'text.primary' : 'text.secondary'}
                sx={{ whiteSpace: 'pre-wrap' }}
              >
                {meeting.agenda ?? '—'}
              </Typography>

              {/* Notes section — organizers only */}
              {isOrganizer && (
                <Box sx={{ mt: 2 }}>
                  <Typography variant="subtitle2" gutterBottom>
                    {t('meetings.fields.notes')}
                  </Typography>
                  <Typography
                    variant="body2"
                    color={meeting.notes ? 'text.primary' : 'text.secondary'}
                    sx={{ whiteSpace: 'pre-wrap' }}
                  >
                    {meeting.notes ?? '—'}
                  </Typography>
                </Box>
              )}
            </Box>
          </Collapse>
        </Stack>
      </CardContent>
    </Card>
  );
};

export const PartnerMeetingsTab: React.FC<PartnerMeetingsTabProps> = ({ companyName, role }) => {
  const { t } = useTranslation('partners');
  const { data: meetings, isLoading, error } = usePartnerMeetings(companyName);
  const isOrganizer = role === 'ORGANIZER';
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
        {isOrganizer && (
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
            <MeetingCard key={meeting.id} meeting={meeting} isOrganizer={isOrganizer} t={t} />
          ))}
        </Stack>
      )}
    </Box>
  );
};
