/**
 * MeetingDetailPanel
 * Story 8.3: Partner Meeting Coordination — AC2, 3, 4, 7
 *
 * Expanded panel shown below a meeting row.
 * Provides: agenda editing, notes editing, send-invite action (PartnerMeetingInviteAction).
 */

import React, { useEffect, useState } from 'react';
import { Box, Divider, TextField, Typography } from '@mui/material';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { updateMeeting, type PartnerMeetingDTO } from '@/services/api/partnerMeetingsApi';
import PartnerMeetingRsvpPanel from './PartnerMeetingRsvpPanel';
import PartnerMeetingInviteAction from './PartnerMeetingInviteAction';

interface MeetingDetailPanelProps {
  meeting: PartnerMeetingDTO;
}

const MeetingDetailPanel: React.FC<MeetingDetailPanelProps> = ({ meeting }) => {
  const { t } = useTranslation('partners');
  const queryClient = useQueryClient();

  const [agenda, setAgenda] = useState(meeting.agenda ?? '');
  const [notes, setNotes] = useState(meeting.notes ?? '');
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

  return (
    <Box
      sx={{ p: 2, bgcolor: 'action.hover', borderRadius: 1 }}
      data-testid={`meeting-detail-${meeting.id}`}
    >
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

      {/* Invite section (shared with the event's mail page, Story 16.4) */}
      <PartnerMeetingInviteAction
        meeting={meeting}
        onSent={() => setRsvpRefreshKey((k) => k + 1)}
      />

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
