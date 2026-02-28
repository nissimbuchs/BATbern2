/**
 * CreateMeetingDialog
 * Story 8.3: Partner Meeting Coordination — AC1, 2, 7
 *
 * Dialog for creating a new partner meeting.
 * Event code links to BATbern event (date auto-filled via tooltip).
 */

import React, { useState } from 'react';
import {
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  TextField,
} from '@mui/material';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { createMeeting, type CreateMeetingRequest } from '@/services/api/partnerMeetingsApi';

interface CreateMeetingDialogProps {
  open: boolean;
  onClose: () => void;
}

const EMPTY_FORM: CreateMeetingRequest = {
  eventCode: '',
  meetingType: 'SPRING',
  startTime: '12:00',
  endTime: '14:00',
  location: '',
  agenda: '',
};

const CreateMeetingDialog: React.FC<CreateMeetingDialogProps> = ({ open, onClose }) => {
  const { t } = useTranslation('partners');
  const queryClient = useQueryClient();
  const [form, setForm] = useState<CreateMeetingRequest>(EMPTY_FORM);
  const [errors, setErrors] = useState<Partial<Record<keyof CreateMeetingRequest, string>>>({});

  const createMutation = useMutation({
    mutationFn: createMeeting,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['partnerMeetings'] });
      handleClose();
    },
  });

  const validate = (): boolean => {
    const next: typeof errors = {};
    if (!form.eventCode.trim()) {
      next.eventCode = t('meetings.fields.eventCode') + ' is required';
    }
    if (!form.startTime) next.startTime = t('meetings.fields.startTime') + ' is required';
    if (!form.endTime) next.endTime = t('meetings.fields.endTime') + ' is required';
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSubmit = () => {
    if (!validate()) return;
    createMutation.mutate({
      ...form,
      location: form.location?.trim() || undefined,
      agenda: form.agenda?.trim() || undefined,
    });
  };

  const handleClose = () => {
    setForm(EMPTY_FORM);
    setErrors({});
    createMutation.reset();
    onClose();
  };

  const setField = <K extends keyof CreateMeetingRequest>(
    key: K,
    value: CreateMeetingRequest[K]
  ) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    setErrors((prev) => ({ ...prev, [key]: undefined }));
  };

  return (
    <Dialog
      open={open}
      onClose={handleClose}
      maxWidth="sm"
      fullWidth
      data-testid="create-meeting-dialog"
    >
      <DialogTitle>{t('meetings.create')}</DialogTitle>
      <DialogContent>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
          <TextField
            label={t('meetings.fields.eventCode')}
            value={form.eventCode}
            onChange={(e) => setField('eventCode', e.target.value)}
            error={!!errors.eventCode}
            helperText={errors.eventCode || t('meetings.fields.eventCodeHint')}
            placeholder="BATbern57"
            required
            inputProps={{ 'data-testid': 'meeting-event-code' }}
          />

          <FormControl fullWidth>
            <InputLabel>{t('meetings.fields.type.spring').replace('Spring ', '')}</InputLabel>
            <Select
              value={form.meetingType}
              label={t('meetings.fields.type.spring').replace('Spring ', '')}
              onChange={(e) => setField('meetingType', e.target.value as 'SPRING' | 'AUTUMN')}
              inputProps={{ 'data-testid': 'meeting-type-select' }}
            >
              <MenuItem value="SPRING">{t('meetings.fields.type.spring')}</MenuItem>
              <MenuItem value="AUTUMN">{t('meetings.fields.type.autumn')}</MenuItem>
            </Select>
          </FormControl>

          <Box sx={{ display: 'flex', gap: 2 }}>
            <TextField
              label={t('meetings.fields.startTime')}
              type="time"
              value={form.startTime}
              onChange={(e) => setField('startTime', e.target.value)}
              error={!!errors.startTime}
              helperText={errors.startTime}
              InputLabelProps={{ shrink: true }}
              inputProps={{ step: 300, 'data-testid': 'meeting-start-time' }}
              sx={{ flex: 1 }}
            />
            <TextField
              label={t('meetings.fields.endTime')}
              type="time"
              value={form.endTime}
              onChange={(e) => setField('endTime', e.target.value)}
              error={!!errors.endTime}
              helperText={errors.endTime}
              InputLabelProps={{ shrink: true }}
              inputProps={{ step: 300, 'data-testid': 'meeting-end-time' }}
              sx={{ flex: 1 }}
            />
          </Box>

          <TextField
            label={t('meetings.fields.location')}
            value={form.location ?? ''}
            onChange={(e) => setField('location', e.target.value)}
            inputProps={{ 'data-testid': 'meeting-location' }}
          />

          <TextField
            label={t('meetings.fields.agenda')}
            value={form.agenda ?? ''}
            onChange={(e) => setField('agenda', e.target.value)}
            multiline
            rows={4}
            inputProps={{ 'data-testid': 'meeting-agenda' }}
          />
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} data-testid="create-meeting-cancel">
          {t('common:actions.cancel')}
        </Button>
        <Button
          variant="contained"
          onClick={handleSubmit}
          disabled={createMutation.isPending}
          data-testid="create-meeting-submit"
        >
          {createMutation.isPending ? t('common:actions.saving') : t('common:actions.save')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default CreateMeetingDialog;
