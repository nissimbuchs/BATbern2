import React, { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { AttachFile as AttachFileIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { formatDateTime } from '@/utils/date';
import { usePatchSpeakerPool } from '@/hooks/useSpeakerPool';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';

interface DetailsTabPanelProps {
  speaker: SpeakerPoolEntry;
  eventCode: string;
  /** When true, the panel renders editable form fields with Save/Cancel actions. */
  isEditing: boolean;
  /** Invoked when the user successfully saves edits, or cancels them. */
  onExitEditMode: () => void;
}

interface EditFormState {
  speakerName: string;
  company: string;
  expertise: string;
  notes: string;
}

export const DetailsTabPanel: React.FC<DetailsTabPanelProps> = ({
  speaker,
  eventCode,
  isEditing,
  onExitEditMode,
}) => {
  const { t, i18n } = useTranslation('organizer');
  const fmt = (dateString: string) => formatDateTime(new Date(dateString), i18n.language);
  const patchMutation = usePatchSpeakerPool();

  const [form, setForm] = useState<EditFormState>({
    speakerName: speaker.speakerName ?? '',
    company: speaker.company ?? '',
    expertise: speaker.expertise ?? '',
    notes: speaker.notes ?? '',
  });
  const [submitError, setSubmitError] = useState<string | null>(null);

  // Reset form whenever we enter edit mode or the underlying speaker changes (e.g.
  // navigating between speakers while the drawer stays mounted).
  useEffect(() => {
    if (isEditing) {
      setForm({
        speakerName: speaker.speakerName ?? '',
        company: speaker.company ?? '',
        expertise: speaker.expertise ?? '',
        notes: speaker.notes ?? '',
      });
      setSubmitError(null);
    }
  }, [
    isEditing,
    speaker.id,
    speaker.speakerName,
    speaker.company,
    speaker.expertise,
    speaker.notes,
  ]);

  const trimmedName = form.speakerName.trim();
  const nameError = trimmedName.length === 0;

  const handleSave = async () => {
    if (nameError) return;
    setSubmitError(null);
    try {
      await patchMutation.mutateAsync({
        eventCode,
        speakerId: speaker.id,
        request: {
          speakerName: trimmedName,
          company: form.company,
          expertise: form.expertise,
          notes: form.notes,
        },
      });
      onExitEditMode();
    } catch (err) {
      setSubmitError(
        err instanceof Error
          ? err.message
          : t('speakerDrawer.editDetails.errors.saveFailed', 'Could not save changes.')
      );
    }
  };

  if (isEditing) {
    return (
      <Box sx={{ p: 2, overflow: 'auto' }} data-testid="details-tab-edit-mode">
        <Stack spacing={2}>
          <TextField
            label={t('speakers.speakerName', 'Speaker name')}
            value={form.speakerName}
            onChange={(e) => setForm((s) => ({ ...s, speakerName: e.target.value }))}
            required
            fullWidth
            size="small"
            error={nameError}
            helperText={
              nameError
                ? t('speakerDrawer.editDetails.errors.nameRequired', 'Speaker name is required')
                : undefined
            }
            inputProps={{
              'data-testid': 'details-edit-speakerName',
              maxLength: 255,
            }}
          />
          <TextField
            label={t('speakers.company', 'Company')}
            value={form.company}
            onChange={(e) => setForm((s) => ({ ...s, company: e.target.value }))}
            fullWidth
            size="small"
            inputProps={{ 'data-testid': 'details-edit-company', maxLength: 255 }}
          />
          <TextField
            label={t('speakers.expertise', 'Expertise')}
            value={form.expertise}
            onChange={(e) => setForm((s) => ({ ...s, expertise: e.target.value }))}
            fullWidth
            size="small"
            inputProps={{ 'data-testid': 'details-edit-expertise', maxLength: 1000 }}
          />
          <TextField
            label={t('speakers.internalNotes', 'Internal notes')}
            value={form.notes}
            onChange={(e) => setForm((s) => ({ ...s, notes: e.target.value }))}
            fullWidth
            multiline
            rows={3}
            size="small"
            inputProps={{ 'data-testid': 'details-edit-notes' }}
          />

          {submitError && <Alert severity="error">{submitError}</Alert>}

          <Stack direction="row" spacing={1} justifyContent="flex-end">
            <Button
              variant="outlined"
              onClick={() => {
                setSubmitError(null);
                onExitEditMode();
              }}
              disabled={patchMutation.isPending}
              data-testid="details-edit-cancel"
            >
              {t('common.cancel', 'Cancel')}
            </Button>
            <Button
              variant="contained"
              onClick={handleSave}
              disabled={nameError || patchMutation.isPending}
              startIcon={patchMutation.isPending ? <CircularProgress size={16} /> : null}
              data-testid="details-edit-save"
            >
              {t('common.save', 'Save')}
            </Button>
          </Stack>
        </Stack>
      </Box>
    );
  }

  return (
    <Box sx={{ p: 2, overflow: 'auto' }}>
      {/* Brainstorm-level metadata — always shown so the read-only view matches the edit
          form contents (otherwise hitting "Edit Details" produces a visually empty form). */}
      <Box sx={{ mb: 2 }} data-testid="details-tab-readonly-metadata">
        <Typography variant="body2" sx={{ color: 'text.secondary' }}>
          {t('speakers.speakerName', 'Speaker name')}
        </Typography>
        <Typography variant="body1" sx={{ mb: 1 }}>
          {speaker.speakerName || '—'}
        </Typography>
        {(speaker.company || speaker.expertise) && (
          <>
            {speaker.company && (
              <>
                <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                  {t('speakers.company', 'Company')}
                </Typography>
                <Typography variant="body1" sx={{ mb: 1 }}>
                  {speaker.company}
                </Typography>
              </>
            )}
            {speaker.expertise && (
              <>
                <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                  {t('speakers.expertise', 'Expertise')}
                </Typography>
                <Typography variant="body1" sx={{ mb: 1 }}>
                  {speaker.expertise}
                </Typography>
              </>
            )}
          </>
        )}
        {speaker.notes && (
          <>
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
              {t('speakers.internalNotes', 'Internal notes')}
            </Typography>
            <Typography variant="body1" sx={{ whiteSpace: 'pre-wrap' }}>
              {speaker.notes}
            </Typography>
          </>
        )}
      </Box>

      {/* Response Details */}
      {speaker.acceptedAt && (
        <Box sx={{ bgcolor: '#e8f5e9', p: 1.5, borderRadius: 1, mb: 2 }}>
          <Typography variant="subtitle2" sx={{ color: '#1b5e20' }} gutterBottom>
            {t('speakers.responseDetails')}
          </Typography>
          <Typography variant="body2" sx={{ color: '#212121' }}>
            {t('speakers.acceptedAt')}: {fmt(speaker.acceptedAt)}
          </Typography>
          {speaker.preferredTimeSlot && (
            <Typography variant="body2" sx={{ color: '#212121' }}>
              {t('speakers.preferredTimeSlot')}: {speaker.preferredTimeSlot}
            </Typography>
          )}
          {speaker.travelRequirements && (
            <Typography variant="body2" sx={{ color: '#212121' }}>
              {t('speakers.travelRequirements')}: {speaker.travelRequirements}
            </Typography>
          )}
          {speaker.technicalRequirements && (
            <Typography variant="body2" sx={{ color: '#212121' }}>
              {t('speakers.technicalRequirements')}: {speaker.technicalRequirements}
            </Typography>
          )}
          {speaker.initialPresentationTitle && (
            <Typography variant="body2" sx={{ color: '#212121' }}>
              {t('speakers.initialTitle')}: {speaker.initialPresentationTitle}
            </Typography>
          )}
          {speaker.preferenceComments && (
            <Typography variant="body2" sx={{ color: '#212121', mt: 1, fontStyle: 'italic' }}>
              {t('speakers.comments')}: {speaker.preferenceComments}
            </Typography>
          )}
        </Box>
      )}

      {/* Decline Details */}
      {speaker.status === 'DECLINED' && speaker.declineReason && (
        <Box sx={{ bgcolor: '#ffebee', p: 1.5, borderRadius: 1, mb: 2 }}>
          <Typography variant="subtitle2" sx={{ color: '#b71c1c' }} gutterBottom>
            {t('speakers.declineDetails')}
          </Typography>
          {speaker.declinedAt && (
            <Typography variant="body2" sx={{ color: '#212121' }}>
              {t('speakers.declinedAt')}: {fmt(speaker.declinedAt)}
            </Typography>
          )}
          <Typography variant="body2" sx={{ color: '#212121' }}>
            {t('speakers.declineReason')}: {speaker.declineReason}
          </Typography>
        </Box>
      )}

      {/* Revision Feedback */}
      {speaker.contentStatus === 'REVISION_NEEDED' && speaker.notes && (
        <Box sx={{ bgcolor: '#ffebee', p: 1.5, borderRadius: 1, mb: 2 }}>
          <Typography variant="subtitle2" sx={{ color: '#b71c1c' }} gutterBottom>
            {t('speakers.revisionRequested', 'Revision Requested')}
          </Typography>
          <Typography variant="body2" sx={{ color: '#212121', whiteSpace: 'pre-wrap' }}>
            {speaker.notes}
          </Typography>
        </Box>
      )}

      {/* Submitted Content */}
      {speaker.submittedTitle && (
        <Box sx={{ bgcolor: '#e8f5e9', p: 1.5, borderRadius: 1, mb: 2 }}>
          <Typography variant="subtitle2" sx={{ color: '#1b5e20' }} gutterBottom>
            {t('speakers.submittedContent', 'Submitted Content')}
            {speaker.contentStatus && (
              <Chip
                label={speaker.contentStatus}
                size="small"
                color={
                  speaker.contentStatus === 'APPROVED'
                    ? 'success'
                    : speaker.contentStatus === 'SUBMITTED'
                      ? 'info'
                      : 'warning'
                }
                sx={{ ml: 1 }}
              />
            )}
          </Typography>
          <Typography variant="body2" sx={{ color: '#212121' }} fontWeight="medium" gutterBottom>
            {speaker.submittedTitle}
          </Typography>
          {speaker.submittedAbstract && (
            <Typography
              variant="body2"
              sx={{
                color: '#212121',
                mt: 1,
                whiteSpace: 'pre-wrap',
              }}
            >
              {speaker.submittedAbstract}
            </Typography>
          )}
          {speaker.materialFileName && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mt: 1 }}>
              <AttachFileIcon sx={{ fontSize: 16, color: '#1b5e20' }} />
              {speaker.materialCloudFrontUrl ? (
                <Button
                  variant="text"
                  href={speaker.materialCloudFrontUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  size="small"
                  sx={{ textTransform: 'none', color: '#1b5e20', p: 0, minWidth: 0 }}
                >
                  {speaker.materialFileName}
                </Button>
              ) : (
                <Typography variant="body2" sx={{ color: '#212121' }}>
                  {speaker.materialFileName}
                </Typography>
              )}
            </Box>
          )}
          {speaker.contentSubmittedAt && (
            <Typography variant="caption" sx={{ color: '#424242', mt: 1, display: 'block' }}>
              {t('speakers.submittedAt', 'Submitted')}: {fmt(speaker.contentSubmittedAt)}
            </Typography>
          )}
        </Box>
      )}
    </Box>
  );
};
