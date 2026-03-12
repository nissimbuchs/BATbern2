/**
 * MeetingDetailPanel
 * Story 8.3: Partner Meeting Coordination — AC2, 3, 4, 7
 *
 * Expanded panel shown below a meeting row.
 * Provides: agenda editing, notes editing, send-invite action.
 */

import React, { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  TextField,
  Typography,
} from '@mui/material';
import { CalendarMonth as CalendarMonthIcon } from '@mui/icons-material';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import {
  updateMeeting,
  sendInvite,
  type PartnerMeetingDTO,
} from '@/services/api/partnerMeetingsApi';
import PartnerMeetingRsvpPanel from './PartnerMeetingRsvpPanel';

interface MeetingDetailPanelProps {
  meeting: PartnerMeetingDTO;
}

const MeetingDetailPanel: React.FC<MeetingDetailPanelProps> = ({ meeting }) => {
  const { t } = useTranslation('partners');
  const queryClient = useQueryClient();

  const [agenda, setAgenda] = useState(meeting.agenda ?? '');
  const [notes, setNotes] = useState(meeting.notes ?? '');
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [inviteSuccess, setInviteSuccess] = useState(false);
  const [rsvpRefreshKey, setRsvpRefreshKey] = useState(0);

  // Keep local state in sync if meeting data changes
  useEffect(() => {
    setAgenda(meeting.agenda ?? '');
    setNotes(meeting.notes ?? '');
  }, [meeting.agenda, meeting.notes]);

  const updateMutation = useMutation({
    mutationFn: (req: { agenda?: string | null; notes?: string | null }) =>
      updateMeeting(meeting.id, req),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['partnerMeetings'] });
    },
  });

  const inviteMutation = useMutation({
    mutationFn: () => sendInvite(meeting.id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['partnerMeetings'] });
      setInviteSuccess(true);
      setConfirmOpen(false);
      setRsvpRefreshKey((k) => k + 1);
    },
  });

  const handleAgendaBlur = () => {
    const trimmed = agenda.trim();
    if (trimmed !== (meeting.agenda ?? '').trim()) {
      updateMutation.mutate({ agenda: trimmed || null });
    }
  };

  const handleNotesBlur = () => {
    const trimmed = notes.trim();
    if (trimmed !== (meeting.notes ?? '').trim()) {
      updateMutation.mutate({ notes: trimmed || null });
    }
  };

  const inviteSentDate = meeting.inviteSentAt
    ? new Date(meeting.inviteSentAt).toLocaleDateString()
    : null;

  return (
    <Box
      sx={{ p: 2, bgcolor: 'action.hover', borderRadius: 1 }}
      data-testid={`meeting-detail-${meeting.id}`}
    >
      {inviteSuccess && (
        <Alert severity="success" sx={{ mb: 2 }} onClose={() => setInviteSuccess(false)}>
          {t('meetings.inviteSuccess')}
        </Alert>
      )}

      <Box sx={{ display: 'flex', gap: 2, flexDirection: { xs: 'column', md: 'row' } }}>
        {/* Agenda */}
        <Box sx={{ flex: 1 }}>
          <Typography variant="subtitle2" gutterBottom>
            {t('meetings.fields.agenda')}
          </Typography>
          <TextField
            multiline
            rows={4}
            fullWidth
            value={agenda}
            onChange={(e) => setAgenda(e.target.value)}
            onBlur={handleAgendaBlur}
            placeholder={t('meetings.fields.agenda')}
            inputProps={{ 'data-testid': `meeting-agenda-${meeting.id}` }}
          />
        </Box>

        {/* Notes */}
        <Box sx={{ flex: 1 }}>
          <Typography variant="subtitle2" gutterBottom>
            {t('meetings.fields.notes')}
          </Typography>
          <TextField
            multiline
            rows={4}
            fullWidth
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            onBlur={handleNotesBlur}
            placeholder={t('meetings.fields.notes')}
            inputProps={{ 'data-testid': `meeting-notes-${meeting.id}` }}
          />
        </Box>
      </Box>

      <Divider sx={{ my: 2 }} />

      {/* Invite section */}
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

      {/* Confirmation dialog */}
      <Dialog open={confirmOpen} onClose={() => setConfirmOpen(false)} maxWidth="xs">
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

      {/* RSVP responses — Story 10.27 */}
      <PartnerMeetingRsvpPanel
        meetingId={meeting.id}
        inviteSentAt={meeting.inviteSentAt ?? null}
        refreshKey={rsvpRefreshKey}
      />
    </Box>
  );
};

export default MeetingDetailPanel;
