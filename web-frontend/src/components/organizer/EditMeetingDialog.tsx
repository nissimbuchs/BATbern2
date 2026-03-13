/**
 * EditMeetingDialog
 * Allows organizers to edit location, start/end time, agenda and notes
 * of an existing partner meeting.
 */

import React, { useEffect, useState } from 'react';
import {
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  TextField,
} from '@mui/material';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import {
  updateMeeting,
  type PartnerMeetingDTO,
  type UpdateMeetingRequest,
} from '@/services/api/partnerMeetingsApi';

interface EditMeetingDialogProps {
  open: boolean;
  meeting: PartnerMeetingDTO | null;
  onClose: () => void;
}

const EditMeetingDialog: React.FC<EditMeetingDialogProps> = ({ open, meeting, onClose }) => {
  const { t } = useTranslation('partners');
  const queryClient = useQueryClient();

  const [form, setForm] = useState<UpdateMeetingRequest>({});

  useEffect(() => {
    if (meeting) {
      setForm({
        location: meeting.location ?? '',
        startTime: meeting.startTime ?? '',
        endTime: meeting.endTime ?? '',
        agenda: meeting.agenda ?? '',
        notes: meeting.notes ?? '',
      });
    }
  }, [meeting]);

  const editMutation = useMutation({
    mutationFn: (req: UpdateMeetingRequest) => updateMeeting(meeting!.id, req),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['partnerMeetings'] });
      handleClose();
    },
  });

  const handleSubmit = () => {
    if (!meeting) return;
    editMutation.mutate({
      location: form.location?.trim() || undefined,
      startTime: form.startTime || undefined,
      endTime: form.endTime || undefined,
      agenda: form.agenda?.trim() || undefined,
      notes: form.notes?.trim() || undefined,
    });
  };

  const handleClose = () => {
    editMutation.reset();
    onClose();
  };

  const setField = <K extends keyof UpdateMeetingRequest>(
    key: K,
    value: UpdateMeetingRequest[K]
  ) => {
    setForm((prev) => ({ ...prev, [key]: value }));
  };

  return (
    <Dialog
      open={open}
      onClose={handleClose}
      maxWidth="sm"
      fullWidth
      data-testid="edit-meeting-dialog"
    >
      <DialogTitle>{t('meetings.edit')}</DialogTitle>
      <DialogContent>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
          <Box sx={{ display: 'flex', gap: 2 }}>
            <TextField
              label={t('meetings.fields.startTime')}
              type="time"
              value={form.startTime ?? ''}
              onChange={(e) => setField('startTime', e.target.value)}
              InputLabelProps={{ shrink: true }}
              inputProps={{ step: 300, 'data-testid': 'edit-start-time' }}
              sx={{ flex: 1 }}
            />
            <TextField
              label={t('meetings.fields.endTime')}
              type="time"
              value={form.endTime ?? ''}
              onChange={(e) => setField('endTime', e.target.value)}
              InputLabelProps={{ shrink: true }}
              inputProps={{ step: 300, 'data-testid': 'edit-end-time' }}
              sx={{ flex: 1 }}
            />
          </Box>

          <TextField
            label={t('meetings.fields.location')}
            value={form.location ?? ''}
            onChange={(e) => setField('location', e.target.value)}
            inputProps={{ 'data-testid': 'edit-location' }}
          />

          <TextField
            label={t('meetings.fields.agenda')}
            value={form.agenda ?? ''}
            onChange={(e) => setField('agenda', e.target.value)}
            multiline
            rows={4}
            inputProps={{ 'data-testid': 'edit-agenda' }}
          />

          <TextField
            label={t('meetings.fields.notes')}
            value={form.notes ?? ''}
            onChange={(e) => setField('notes', e.target.value)}
            multiline
            rows={3}
            inputProps={{ 'data-testid': 'edit-notes' }}
          />
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} data-testid="edit-meeting-cancel">
          {t('common:actions.cancel')}
        </Button>
        <Button
          variant="contained"
          onClick={handleSubmit}
          disabled={editMutation.isPending}
          data-testid="edit-meeting-submit"
        >
          {editMutation.isPending ? t('common:actions.saving') : t('common:actions.save')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default EditMeetingDialog;
