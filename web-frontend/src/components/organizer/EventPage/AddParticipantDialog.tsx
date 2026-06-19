/**
 * AddParticipantDialog (Epic 14 follow-up — organizer "Add participant")
 *
 * Lets an organizer add an EXISTING BATbern user onto an event directly as a `confirmed`
 * participant — no self-registration / email-confirmation step. The user is chosen via the
 * shared `UserAutocomplete` (the same picker the speaker kanban uses to promote a READY
 * speaker); there is deliberately no "add by email / new person" path.
 *
 * Capacity: a full event returns 409 with `details.code = "capacity_exceeded"`; the dialog
 * then offers an explicit "add anyway" that re-submits with `force=true`.
 */

import React, { useState } from 'react';
import {
  Alert,
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  Stack,
} from '@mui/material';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { UserAutocomplete } from '@/components/shared/UserAutocomplete';
import type { UserSearchResponse } from '@/types/user.types';
import { addParticipant } from '@/services/api/eventRegistrationService';

interface AddParticipantDialogProps {
  open: boolean;
  onClose: () => void;
  eventCode: string;
  /** Notifies the parent so it can show a success snackbar. */
  onAdded?: (attendeeName: string) => void;
}

/** Narrow an axios-style error to the backend ErrorResponse `details.code`, if any. */
function errorDetailCode(err: unknown): string | undefined {
  const data = (err as { response?: { data?: { details?: { code?: string } } } })?.response?.data;
  return data?.details?.code;
}
function errorStatus(err: unknown): number | undefined {
  return (err as { response?: { status?: number } })?.response?.status;
}

export const AddParticipantDialog: React.FC<AddParticipantDialogProps> = ({
  open,
  onClose,
  eventCode,
  onAdded,
}) => {
  const { t } = useTranslation('events');
  const queryClient = useQueryClient();

  const [selectedUser, setSelectedUser] = useState<UserSearchResponse | null>(null);
  const [notify, setNotify] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Set when the event is full → the primary button becomes "Add anyway" (force).
  const [capacityFull, setCapacityFull] = useState(false);

  const reset = (): void => {
    setSelectedUser(null);
    setNotify(true);
    setSubmitting(false);
    setError(null);
    setCapacityFull(false);
  };

  const handleClose = (): void => {
    if (submitting) return;
    reset();
    onClose();
  };

  const submit = async (force: boolean): Promise<void> => {
    if (!selectedUser) return;
    setSubmitting(true);
    setError(null);
    try {
      await addParticipant(eventCode, { username: selectedUser.id, force, notify });
      // Mirror the promote-from-waitlist invalidations so the list + count badge refresh.
      queryClient.invalidateQueries({ queryKey: ['event-registrations', eventCode] });
      queryClient.invalidateQueries({ queryKey: ['events', eventCode] });
      queryClient.invalidateQueries({ queryKey: ['event', eventCode] });
      const name =
        `${selectedUser.firstName ?? ''} ${selectedUser.lastName ?? ''}`.trim() || selectedUser.id;
      reset();
      onClose();
      onAdded?.(name);
    } catch (err) {
      if (errorStatus(err) === 409 && errorDetailCode(err) === 'capacity_exceeded') {
        // Offer the explicit override (NFR1 — consequential action confirmed).
        setCapacityFull(true);
        setError(
          t(
            'eventPage.participantsTab.addParticipantFull',
            'This event is full. Add this participant over capacity anyway?'
          )
        );
      } else if (errorStatus(err) === 409) {
        setError(
          t(
            'eventPage.participantsTab.addParticipantDuplicate',
            'This user is already registered for this event.'
          )
        );
      } else {
        setError(
          t('eventPage.participantsTab.addParticipantError', 'Could not add this participant.')
        );
      }
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle>
        {t('eventPage.participantsTab.addParticipantTitle', 'Add participant')}
      </DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }} data-testid="add-participant-dialog">
          {error && (
            <Alert
              severity={capacityFull ? 'warning' : 'error'}
              data-testid="add-participant-error"
            >
              {error}
            </Alert>
          )}
          <UserAutocomplete
            value={selectedUser}
            onChange={(u) => {
              setSelectedUser(u);
              setCapacityFull(false);
              setError(null);
            }}
            label={t('eventPage.participantsTab.addParticipantUserLabel', 'User')}
            data-testid="add-participant-user"
          />
          <FormControlLabel
            control={
              <Checkbox
                checked={notify}
                onChange={(e) => setNotify(e.target.checked)}
                data-testid="add-participant-notify"
              />
            }
            label={t(
              'eventPage.participantsTab.addParticipantNotify',
              'Notify the participant by email'
            )}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={submitting}>
          {t('common:actions.cancel', 'Cancel')}
        </Button>
        <Button
          variant="contained"
          onClick={() => submit(capacityFull)}
          disabled={!selectedUser || submitting}
          data-testid="add-participant-confirm"
        >
          {capacityFull
            ? t('eventPage.participantsTab.addParticipantForce', 'Add anyway')
            : t('eventPage.participantsTab.addParticipant', 'Add participant')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default AddParticipantDialog;
