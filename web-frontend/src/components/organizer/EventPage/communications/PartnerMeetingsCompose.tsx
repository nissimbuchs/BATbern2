/**
 * PartnerMeetingsCompose (Story 16.4 AC6): the Partners entry of "+ New mail".
 *
 * Lists this event's partner meetings (the list endpoint has no event filter; it holds a handful of
 * meetings, so it is filtered here) with the existing calendar invite action. Without a meeting it
 * says so and links to the partner meetings page.
 */

import React from 'react';
import { Box, Link, Paper, Skeleton, Stack, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink } from 'react-router-dom';
import { getMeetings } from '@/services/api/partnerMeetingsApi';
import PartnerMeetingInviteAction from '@/components/organizer/PartnerMeetingInviteAction';

interface PartnerMeetingsComposeProps {
  eventCode: string;
  onSent?: () => void;
}

export const PartnerMeetingsCompose: React.FC<PartnerMeetingsComposeProps> = ({
  eventCode,
  onSent,
}) => {
  const { t, i18n } = useTranslation('events');
  const meetingsQuery = useQuery({ queryKey: ['partnerMeetings'], queryFn: getMeetings });
  const meetings = (meetingsQuery.data ?? []).filter((m) => m.eventCode === eventCode);

  if (meetingsQuery.isLoading) {
    return <Skeleton variant="rectangular" height={80} />;
  }

  if (meetings.length === 0) {
    return (
      <Typography variant="body2" data-testid="partner-compose-empty">
        {t('eventPage.mail.partners.noMeeting')}{' '}
        <Link component={RouterLink} to="/organizer/partner-meetings">
          {t('eventPage.mail.partners.meetingsLink')}
        </Link>
      </Typography>
    );
  }

  return (
    <Stack spacing={2} data-testid="partner-compose">
      <Typography variant="body2" color="text.secondary">
        {t('eventPage.mail.partners.hint')}
      </Typography>
      {meetings.map((meeting) => (
        <Paper key={meeting.id} variant="outlined" sx={{ p: 2 }}>
          <Box sx={{ mb: 1 }}>
            <Typography variant="subtitle2">
              {t(`eventPage.mail.partners.type.${meeting.meetingType}`)} ·{' '}
              {new Date(meeting.meetingDate).toLocaleDateString(i18n.language)}, {meeting.startTime}
              –{meeting.endTime}
            </Typography>
            {meeting.location && (
              <Typography variant="caption" color="text.secondary">
                {meeting.location}
              </Typography>
            )}
          </Box>
          <PartnerMeetingInviteAction meeting={meeting} onSent={onSent} />
        </Paper>
      ))}
    </Stack>
  );
};

export default PartnerMeetingsCompose;
