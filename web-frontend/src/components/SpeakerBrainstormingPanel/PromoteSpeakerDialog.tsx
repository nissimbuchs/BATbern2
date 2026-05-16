/**
 * PromoteSpeakerDialog (Story 11.D.1)
 *
 * Modal that drives the `CONTACTED → READY` workflow transition for a speaker pool entry.
 * Submits to `POST /events/{eventCode}/speakers/{speakerId}/promote` via
 * `usePromoteSpeakerToReady`, then closes on success. On 409
 * `INVALID_PROMOTION_STATE` the modal stays open and surfaces the backend message in an
 * inline `<Alert>`; the kanban/pool query is invalidated by the hook on success so the
 * speaker visually moves to the READY lane.
 */
import React, { useMemo } from 'react';
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Stack,
  TextField,
} from '@mui/material';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import type { AxiosError } from 'axios';
import { usePromoteSpeakerToReady } from '@/hooks/useSpeakerPool';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';

export interface PromoteSpeakerDialogProps {
  open: boolean;
  eventCode: string;
  speaker: SpeakerPoolEntry | null;
  onClose: () => void;
}

interface PromoteFormValues {
  email: string;
  firstName?: string;
  lastName?: string;
}

// Splits "First Last" into { firstName, lastName }. Falls back gracefully when the
// pool entry's speakerName is a single word, empty, or "Title First Last".
function splitFullName(name: string | undefined): { firstName: string; lastName: string } {
  if (!name || !name.trim()) {
    return { firstName: '', lastName: '' };
  }
  const parts = name.trim().split(/\s+/);
  if (parts.length === 1) {
    return { firstName: parts[0], lastName: '' };
  }
  return { firstName: parts[0], lastName: parts.slice(1).join(' ') };
}

interface PromotionConflictDetails {
  code?: string;
  currentState?: string;
}

interface PromotionErrorBody {
  message?: string;
  details?: PromotionConflictDetails;
}

export const PromoteSpeakerDialog: React.FC<PromoteSpeakerDialogProps> = ({
  open,
  eventCode,
  speaker,
  onClose,
}) => {
  const { t } = useTranslation('organizer');
  const promoteMutation = usePromoteSpeakerToReady();

  const schema = useMemo(
    () =>
      z.object({
        email: z
          .string()
          .min(1, t('speakerBrainstorm.promoteDialog.errorEmailRequired', 'Email is required'))
          .email(t('speakerBrainstorm.promoteDialog.errorEmailInvalid', 'Email must be valid'))
          .max(320),
        firstName: z.string().max(100).optional().or(z.literal('')),
        lastName: z.string().max(100).optional().or(z.literal('')),
      }),
    [t]
  );

  const defaults = useMemo<PromoteFormValues>(() => {
    const { firstName, lastName } = splitFullName(speaker?.speakerName);
    return { email: speaker?.email ?? '', firstName, lastName };
  }, [speaker]);

  const {
    control,
    handleSubmit,
    formState: { errors, isValid },
  } = useForm<PromoteFormValues>({
    resolver: zodResolver(schema),
    mode: 'onChange',
    defaultValues: defaults,
  });

  const onSubmit = handleSubmit((values) => {
    if (!speaker) {
      return;
    }
    promoteMutation.mutate(
      {
        eventCode,
        speakerId: speaker.id,
        request: {
          email: values.email.trim(),
          firstName: values.firstName?.trim() || undefined,
          lastName: values.lastName?.trim() || undefined,
        },
      },
      {
        onSuccess: () => {
          onClose();
        },
      }
    );
  });

  const errorBody =
    (promoteMutation.error as AxiosError<PromotionErrorBody> | null)?.response?.data ?? null;
  const errorAlert = (() => {
    if (!promoteMutation.isError) {
      return null;
    }
    const currentState = errorBody?.details?.currentState;
    if (errorBody?.details?.code === 'INVALID_PROMOTION_STATE' && currentState) {
      // Distinct copy per current state — generic "already in X" wording is wrong for
      // IDENTIFIED (X is *before* CONTACTED, not after) and for DECLINED (terminal).
      const stateMessageKey = (() => {
        switch (currentState) {
          case 'IDENTIFIED':
            return 'speakerBrainstorm.promoteDialog.errorStillIdentified';
          case 'DECLINED':
            return 'speakerBrainstorm.promoteDialog.errorDeclined';
          case 'READY':
            return 'speakerBrainstorm.promoteDialog.errorAlreadyReady';
          default:
            return 'speakerBrainstorm.promoteDialog.errorAlreadyPromoted';
        }
      })();
      const localizedState = t(`speakerBrainstorm.workflowState.${currentState}`, currentState);
      return (
        <Alert severity="warning" data-testid="promote-error-state">
          {t(stateMessageKey, { currentState: localizedState })}
        </Alert>
      );
    }
    return (
      <Alert severity="error" data-testid="promote-error-generic">
        {errorBody?.message ||
          t('speakerBrainstorm.promoteDialog.errorTitle', 'Could not promote speaker')}
      </Alert>
    );
  })();

  return (
    <Dialog
      open={open}
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      aria-describedby="promote-speaker-description"
    >
      <DialogTitle>{t('speakerBrainstorm.promoteDialog.title', 'Promote to speaker')}</DialogTitle>
      <form onSubmit={onSubmit} noValidate>
        <DialogContent>
          <Stack spacing={2}>
            <DialogContentText id="promote-speaker-description">
              {t(
                'speakerBrainstorm.promoteDialog.description',
                'The speaker moves to the READY lane and a user account is provisioned. No invitation email is sent yet — send the invitation from the READY lane when you are ready.'
              )}
            </DialogContentText>

            <Controller
              name="email"
              control={control}
              render={({ field }) => (
                <TextField
                  {...field}
                  type="email"
                  required
                  fullWidth
                  size="small"
                  label={t('speakerBrainstorm.promoteDialog.emailLabel', 'Email')}
                  error={!!errors.email}
                  helperText={errors.email?.message}
                  inputProps={{ 'data-testid': 'promote-email-field', maxLength: 320 }}
                />
              )}
            />
            <Controller
              name="firstName"
              control={control}
              render={({ field }) => (
                <TextField
                  {...field}
                  fullWidth
                  size="small"
                  label={t('speakerBrainstorm.promoteDialog.firstNameLabel', 'First name')}
                  error={!!errors.firstName}
                  helperText={errors.firstName?.message}
                  inputProps={{ 'data-testid': 'promote-first-name-field', maxLength: 100 }}
                />
              )}
            />
            <Controller
              name="lastName"
              control={control}
              render={({ field }) => (
                <TextField
                  {...field}
                  fullWidth
                  size="small"
                  label={t('speakerBrainstorm.promoteDialog.lastNameLabel', 'Last name')}
                  error={!!errors.lastName}
                  helperText={errors.lastName?.message}
                  inputProps={{ 'data-testid': 'promote-last-name-field', maxLength: 100 }}
                />
              )}
            />

            {errorAlert}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} disabled={promoteMutation.isPending}>
            {t('speakerBrainstorm.promoteDialog.cancelButton', 'Cancel')}
          </Button>
          <Button
            type="submit"
            variant="contained"
            color="primary"
            disabled={!isValid || promoteMutation.isPending}
            data-testid="promote-submit-button"
          >
            {promoteMutation.isPending
              ? t('speakerBrainstorm.promoteDialog.submitting', 'Promoting…')
              : t('speakerBrainstorm.promoteDialog.submitButton', 'Promote')}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
};

export default PromoteSpeakerDialog;
