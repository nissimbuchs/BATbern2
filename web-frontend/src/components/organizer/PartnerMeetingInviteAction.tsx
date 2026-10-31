/**
 * PartnerMeetingInviteAction: the calendar invite for one partner meeting, with its confirmation.
 *
 * Extracted from MeetingDetailPanel (Story 16.4 AC6) so the Mail page offers the same action; the
 * partner meetings page renders it unchanged. The partner service sends the invite (ICS, RSVP).
 */

import React, { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Typography,
} from '@mui/material';
import { CalendarMonth as CalendarMonthIcon } from '@mui/icons-material';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { sendInvite, type PartnerMeetingDTO } from '@/services/api/partnerMeetingsApi';
import { useBreakpoints } from '@/hooks/useBreakpoints';

interface PartnerMeetingInviteActionProps {
  meeting: PartnerMeetingDTO;
  /** Called after the invite was queued. */
  onSent?: () => void;
}

const PartnerMeetingInviteAction: React.FC<PartnerMeetingInviteActionProps> = ({
  meeting,
  onSent,
}) => {
  const { t } = useTranslation('partners');
  const { isMobile } = useBreakpoints();
  const queryClient = useQueryClient();
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [inviteSuccess, setInviteSuccess] = useState(false);

  const inviteMutation = useMutation({
    mutationFn: () => sendInvite(meeting.id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['partnerMeetings'] });
      setInviteSuccess(true);
      setConfirmOpen(false);
      onSent?.();
    },
  });

  const inviteSentDate = meeting.inviteSentAt
    ? new Date(meeting.inviteSentAt).toLocaleDateString()
    : null;

  return (
    <>
      {inviteSuccess && (
        <Alert
          severity="success"
          sx={{ mb: 2 }}
          onClose={() => setInviteSuccess(false)}
          data-testid={`invite-success-alert-${meeting.id}`}
        >
          {t('meetings.inviteSuccess')}
        </Alert>
      )}

      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
        {inviteSentDate ? (
          <Chip
            icon={<CalendarMonthIcon />}
            label={t('meetings.inviteSentOn', { date: inviteSentDate })}
            color="success"
            size="small"
            data-testid={`invite-sent-chip-${meeting.id}`}
          />
        ) : (
          <Chip
            label={t('meetings.inviteNotSent')}
            variant="outlined"
            size="small"
            data-testid={`invite-not-sent-chip-${meeting.id}`}
          />
        )}

        <Button
          variant="outlined"
          startIcon={<CalendarMonthIcon />}
          onClick={() => setConfirmOpen(true)}
          disabled={inviteMutation.isPending}
          data-testid={`send-invite-btn-${meeting.id}`}
        >
          {inviteMutation.isPending ? t('meetings.inviteQueued') : t('meetings.sendInvite')}
        </Button>
      </Box>

      <Dialog
        open={confirmOpen}
        onClose={() => setConfirmOpen(false)}
        maxWidth="xs"
        fullScreen={isMobile}
      >
        <DialogTitle>{t('meetings.sendInvite')}</DialogTitle>
        <DialogContent>
          <Typography>{t('meetings.confirmSendInvite')}</Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmOpen(false)}>{t('common:actions.cancel')}</Button>
          <Button
            variant="contained"
            onClick={() => inviteMutation.mutate()}
            disabled={inviteMutation.isPending}
            data-testid="confirm-send-invite-btn"
          >
            {inviteMutation.isPending ? t('meetings.inviteQueued') : t('meetings.sendInvite')}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default PartnerMeetingInviteAction;
