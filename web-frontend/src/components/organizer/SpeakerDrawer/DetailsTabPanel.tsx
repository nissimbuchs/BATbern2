import React, { useEffect, useState } from 'react';
import { Alert, Box, Button, CircularProgress, Stack, TextField, Typography } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { usePatchSpeakerPool } from '@/hooks/useSpeakerPool';
import { usePublicUser } from '@/hooks/useUserPortrait';
import { OrganizerSelect } from '@/components/shared/OrganizerSelect';
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
  assignedOrganizerId: string;
  notes: string;
}

export const DetailsTabPanel: React.FC<DetailsTabPanelProps> = ({
  speaker,
  eventCode,
  isEditing,
  onExitEditMode,
}) => {
  const { t } = useTranslation('organizer');
  const patchMutation = usePatchSpeakerPool();

  // 2026-05-20 (Q#4) — once a real speaker is bound (CONTACTED→READY promote provisioned
  // the User), the speaker_pool.speakerName field becomes a brainstorm-era audit string,
  // not the editable display name. The canonical name is the linked User's first+last
  // (same source the drawer header + kanban card already use). Edit-mode locks the
  // speakerName field; read-only displays the User's name.
  const { data: linkedUser } = usePublicUser(speaker.username ?? undefined);
  const hasLinkedUser = !!(linkedUser?.firstName && linkedUser?.lastName);
  const linkedUserDisplayName = hasLinkedUser
    ? `${linkedUser!.firstName} ${linkedUser!.lastName}`
    : null;

  const [form, setForm] = useState<EditFormState>({
    speakerName: speaker.speakerName ?? '',
    company: speaker.company ?? '',
    expertise: speaker.expertise ?? '',
    assignedOrganizerId: speaker.assignedOrganizerId ?? '',
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
        assignedOrganizerId: speaker.assignedOrganizerId ?? '',
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
    speaker.assignedOrganizerId,
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
          // Empty string means "unassigned" — pass it through so the backend can clear
          // the column (PatchSpeakerPoolRequest treats empty as a sentinel).
          assignedOrganizerId: form.assignedOrganizerId,
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
          {hasLinkedUser ? (
            // 2026-05-20 (Q#4) — once the speaker is bound to a User, the name is owned
            // by the User entity. Render as a read-only field with a hint telling the
            // organizer to use the user-edit modal (accessible from the Content tab's
            // "Edit speaker profile" button) to change it.
            <TextField
              label={t('speakers.speakerName', 'Speaker name')}
              value={linkedUserDisplayName ?? ''}
              fullWidth
              size="small"
              disabled
              helperText={t(
                'speakerDrawer.editDetails.speakerNameLockedHint',
                'Name comes from the linked user profile. Use the Content tab’s "Edit speaker profile" to change it.'
              )}
              inputProps={{ 'data-testid': 'details-edit-speakerName-locked' }}
            />
          ) : (
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
          )}
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
          {/* Epic 11 bug fix 2026-05-19 — assignedOrganizer is part of the edit-details
              form (replaces the legacy standalone "Reassign organizer" secondary action). */}
          <OrganizerSelect
            value={form.assignedOrganizerId}
            onChange={(id) => setForm((s) => ({ ...s, assignedOrganizerId: id }))}
            includeUnassigned={true}
            disabled={patchMutation.isPending}
            size="small"
            fullWidth
            label={t('speakerBrainstorm.form.assignOrganizer', 'Assigned organizer')}
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

  // 2026-05-20 (Q#5) — Response Details / Decline Details / Submitted Content / Revision
  // Feedback boxes were a chronological side-channel that duplicated UnifiedHistoryPanel.
  // Dropped from the Details tab. The History tab is now the single timeline; the Details
  // tab carries brainstorm/identity metadata only.
  const displaySpeakerName = linkedUserDisplayName ?? speaker.speakerName ?? '—';

  return (
    <Box sx={{ p: 2, overflow: 'auto' }}>
      {/* Brainstorm-level metadata — always shown so the read-only view matches the edit
          form contents (otherwise hitting "Edit Details" produces a visually empty form). */}
      <Box sx={{ mb: 2 }} data-testid="details-tab-readonly-metadata">
        <Typography variant="body2" sx={{ color: 'text.secondary' }}>
          {t('speakers.speakerName', 'Speaker name')}
        </Typography>
        <Typography variant="body1" sx={{ mb: 1 }}>
          {displaySpeakerName}
        </Typography>
        {hasLinkedUser && speaker.speakerName && speaker.speakerName !== linkedUserDisplayName && (
          <Typography
            variant="caption"
            color="text.secondary"
            sx={{ display: 'block', mt: -1, mb: 1, fontStyle: 'italic' }}
            data-testid="details-tab-brainstorm-name"
          >
            {t('speakerDrawer.brainstormNameLabel', 'brainstormed as')} {speaker.speakerName}
          </Typography>
        )}
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
        {/* Epic 11 bug fix 2026-05-19 — assigned organizer shown in the Details read-only
            view (was previously surfaced only via the now-removed "Reassign organizer"
            secondary action). */}
        <Typography variant="body2" sx={{ color: 'text.secondary' }}>
          {t('speakerBrainstorm.form.assignOrganizer', 'Assigned organizer')}
        </Typography>
        <Typography variant="body1" sx={{ mb: 1 }}>
          {speaker.assignedOrganizerId || t('common.unassigned', 'Unassigned')}
        </Typography>
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
    </Box>
  );
};
