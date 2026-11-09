/**
 * PartnerMeetingRsvpPanel
 * Story 10.27: iCal RSVP Tracking — AC8
 *
 * Displays RSVP responses for a partner meeting invite.
 * Only rendered after an invite has been sent (inviteSentAt non-null).
 * Re-fetches when `refreshKey` changes (incremented by parent after send-invite).
 */

import React from 'react';
import {
  Alert,
  Box,
  Chip,
  CircularProgress,
  Divider,
  List,
  ListItem,
  ListItemText,
  Typography,
} from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { getRsvps, type RsvpDTO } from '@/services/api/partnerMeetingsApi';

interface PartnerMeetingRsvpPanelProps {
  meetingId: string;
  inviteSentAt: string | null;
  refreshKey?: number;
}

const statusColor = (status: NonNullable<RsvpDTO['status']>): 'success' | 'error' | 'warning' => {
  if (status === 'ACCEPTED') return 'success';
  if (status === 'DECLINED') return 'error';
  return 'warning';
};

const PartnerMeetingRsvpPanel: React.FC<PartnerMeetingRsvpPanelProps> = ({
  meetingId,
  inviteSentAt,
  refreshKey = 0,
}) => {
  const { t } = useTranslation('partners');

  const { data, isLoading, isError } = useQuery({
    queryKey: ['meetingRsvps', meetingId, refreshKey],
    queryFn: () => getRsvps(meetingId),
    enabled: inviteSentAt !== null,
  });

  if (inviteSentAt === null) {
    return null;
  }

  return (
    <Box sx={{ mt: 2 }} data-testid={`rsvp-panel-${meetingId}`}>
      <Divider sx={{ mb: 2 }} />

      <Typography variant="subtitle2" gutterBottom>
        {t('meetings.rsvp.title')}
      </Typography>

      {isLoading && (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }} data-testid="rsvp-loading">
          <CircularProgress size={16} />
          <Typography variant="body2" color="text.secondary">
            {t('meetings.rsvp.loading')}
          </Typography>
        </Box>
      )}

      {isError && (
        <Alert severity="error" data-testid="rsvp-error">
          {t('meetings.rsvp.error')}
        </Alert>
      )}

      {data && (
        <>
          <Typography
            variant="body2"
            color="text.secondary"
            sx={{ mb: 1 }}
            data-testid="rsvp-summary"
          >
            {t('meetings.rsvp.summary', {
              accepted: data.summary?.accepted ?? 0,
              declined: data.summary?.declined ?? 0,
              tentative: data.summary?.tentative ?? 0,
            })}
          </Typography>

          {!data.rsvps?.length ? (
            <Typography variant="body2" color="text.secondary">
              {t('meetings.rsvp.noResponses')}
            </Typography>
          ) : (
            <List dense disablePadding>
              {data.rsvps.map((rsvp) => (
                <ListItem key={rsvp.attendeeEmail} disableGutters>
                  {rsvp.status && (
                    <Chip
                      label={t(`meetings.rsvp.status.${rsvp.status.toLowerCase()}`)}
                      color={statusColor(rsvp.status)}
                      size="small"
                      sx={{ mr: 1, minWidth: 90 }}
                      data-testid={`rsvp-chip-${rsvp.attendeeEmail}`}
                    />
                  )}
                  <ListItemText
                    primary={rsvp.attendeeEmail}
                    secondary={
                      rsvp.respondedAt ? new Date(rsvp.respondedAt).toLocaleString() : undefined
                    }
                  />
                </ListItem>
              ))}
            </List>
          )}
        </>
      )}
    </Box>
  );
};

export default PartnerMeetingRsvpPanel;
