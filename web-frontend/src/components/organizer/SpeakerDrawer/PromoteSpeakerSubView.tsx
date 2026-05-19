/**
 * Promote-to-READY sub-view (Epic 11 bug fix 2026-05-18 — replaces PromoteSpeakerDialog).
 *
 * Slides in over the SpeakerDetailDrawer body when an organizer presses "Promote to
 * speaker" on a CONTACTED-state card. Mirrors `ContentSubmissionSubView`'s structure:
 *
 *   - UserAutocomplete pre-filled with `speaker.speakerName` so existing SPEAKER users
 *     surface immediately.
 *   - "Create new speaker" button → UserCreateEditModal for the case where the real
 *     speaker doesn't exist yet (autoselects them on success).
 *   - "Promote" submits the resolved email/firstName/lastName via
 *     `usePromoteSpeakerToReady`. On success the drawer flips to the normal READY view
 *     because the cache invalidation pulls in the new state.
 *
 * Replaces the prior plain-text-fields PromoteSpeakerDialog modal — the dialog and the
 * Content sub-view used to ship two different speaker-resolution UXes; this consolidates
 * onto one.
 */
import React, { useEffect, useRef, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Divider,
  IconButton,
  Paper,
  Stack,
  Typography,
} from '@mui/material';
import { ArrowBack as ArrowBackIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import type { AxiosError } from 'axios';
import { UserAutocomplete } from '@/components/shared/UserAutocomplete';
import { UserAvatar } from '@/components/shared/UserAvatar';
import UserCreateEditModal from '@/components/organizer/UserManagement/UserCreateEditModal';
import { getUserByUsername, searchUsers } from '@/services/api/userManagementApi';
import { usePromoteSpeakerToReady } from '@/hooks/useSpeakerPool';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';
import type { UserSearchResponse, User } from '@/types/user.types';

interface PromoteSpeakerSubViewProps {
  speaker: SpeakerPoolEntry;
  eventCode: string;
  onBack: () => void;
}

interface PromotionConflictDetails {
  code?: string;
  currentState?: string;
}

interface PromotionErrorBody {
  message?: string;
  details?: PromotionConflictDetails;
}

export const PromoteSpeakerSubView: React.FC<PromoteSpeakerSubViewProps> = ({
  speaker,
  eventCode,
  onBack,
}) => {
  const { t } = useTranslation(['organizer', 'common']);
  const queryClient = useQueryClient();
  const promoteMutation = usePromoteSpeakerToReady();

  const [selectedUser, setSelectedUser] = useState<UserSearchResponse | null>(null);
  const [userPickerError, setUserPickerError] = useState<string | undefined>(undefined);
  const [userModalOpen, setUserModalOpen] = useState(false);
  // Holds the FULL `User` (UserResponse) shape — UserSearchResponse omits `bio` and
  // other user-level fields, which would surface as an empty bio in the modal.
  const [editingUser, setEditingUser] = useState<User | null>(null);

  const lastPrefilledSpeakerIdRef = useRef<string | null>(null);

  // Prefill the autocomplete with the existing user that best matches the brainstorm
  // name. Same heuristic as ContentSubmissionSubView so both sub-views resolve identically.
  useEffect(() => {
    const prefill = async () => {
      if (lastPrefilledSpeakerIdRef.current === speaker.id) return;
      lastPrefilledSpeakerIdRef.current = speaker.id;
      try {
        let users = await searchUsers(speaker.speakerName, 20);
        if (users.length === 0 && speaker.speakerName.includes(' ')) {
          const firstName = speaker.speakerName.split(' ')[0];
          users = await searchUsers(firstName, 20);
        }
        const candidates = users.filter((u) => u.roles?.includes('SPEAKER'));
        if (candidates.length > 0) {
          setSelectedUser(candidates[0]);
        }
      } catch (error) {
        console.error('PromoteSpeakerSubView: failed to prefill speaker', error);
      }
    };
    prefill();
  }, [speaker.id, speaker.speakerName]);

  const handleCreateSpeaker = () => {
    setEditingUser(null);
    setUserModalOpen(true);
  };

  const handleEditSpeaker = async () => {
    if (!selectedUser) return;
    // Epic 11 bug fix 2026-05-19 — fetch the full UserResponse before opening the
    // modal so the bio + other user-level fields are populated. The narrow
    // UserSearchResponse omits them; passing it to the modal surfaces an empty bio
    // (mirrors the bug fixed in ContentSubmissionSubView).
    setEditingUser(selectedUser as unknown as User);
    setUserModalOpen(true);
    try {
      const fullUser = await getUserByUsername(selectedUser.id);
      if (fullUser) {
        setEditingUser(fullUser);
      }
    } catch (error) {
      console.error('Failed to fetch full user for edit:', error);
    }
  };

  const handleUserModalClose = () => {
    setUserModalOpen(false);
    setEditingUser(null);
    queryClient.invalidateQueries({ queryKey: ['users', 'search'] });
  };

  const handleUserModalSuccess = (createdOrUpdatedUser?: User) => {
    if (createdOrUpdatedUser) {
      setSelectedUser({
        id: createdOrUpdatedUser.id,
        email: createdOrUpdatedUser.email,
        firstName: createdOrUpdatedUser.firstName,
        lastName: createdOrUpdatedUser.lastName,
        roles: createdOrUpdatedUser.roles,
        profilePictureUrl: createdOrUpdatedUser.profilePictureUrl,
        companyId: createdOrUpdatedUser.companyId,
      });
    }
    handleUserModalClose();
  };

  const handlePromote = () => {
    if (!selectedUser) {
      setUserPickerError(
        t(
          'organizer:speakerBrainstorm.promoteDialog.errorEmailRequired',
          'Select a speaker to promote'
        )
      );
      return;
    }
    setUserPickerError(undefined);
    promoteMutation.mutate(
      {
        eventCode,
        speakerId: speaker.id,
        request: {
          email: selectedUser.email,
          firstName: selectedUser.firstName ?? '',
          lastName: selectedUser.lastName ?? '',
        },
      },
      {
        onSuccess: () => {
          // The cache invalidation in usePromoteSpeakerToReady flips the drawer's
          // speaker.status to READY; the parent's selectedSpeaker also refreshes via
          // useSpeakerPool. onBack returns to the now-READY drawer body.
          onBack();
        },
      }
    );
  };

  const errorBody =
    (promoteMutation.error as AxiosError<PromotionErrorBody> | null)?.response?.data ?? null;
  const errorAlert = (() => {
    if (!promoteMutation.isError) return null;
    const currentState = errorBody?.details?.currentState;
    if (errorBody?.details?.code === 'INVALID_PROMOTION_STATE' && currentState) {
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
      const localizedState = t(
        `organizer:speakerBrainstorm.workflowState.${currentState}`,
        currentState
      );
      return (
        <Alert severity="warning" data-testid="promote-error-state">
          {t(`organizer:${stateMessageKey}`, { currentState: localizedState })}
        </Alert>
      );
    }
    return (
      <Alert severity="error" data-testid="promote-error-generic">
        {errorBody?.message ||
          t('organizer:speakerBrainstorm.promoteDialog.errorTitle', 'Could not promote speaker')}
      </Alert>
    );
  })();

  const canPromote =
    !!selectedUser && !!selectedUser.email && !!selectedUser.firstName && !!selectedUser.lastName;
  const incompleteUserBanner = selectedUser && !canPromote && (
    <Alert severity="info" data-testid="promote-incomplete-user">
      {t(
        'organizer:speakerBrainstorm.promoteSubView.incompleteUser',
        'Selected user is missing required fields (email / first name / last name). Edit the profile to complete it before promoting.'
      )}
    </Alert>
  );

  return (
    <>
      <Box sx={{ p: 1, display: 'flex', alignItems: 'center', gap: 1 }}>
        <IconButton
          onClick={onBack}
          size="small"
          aria-label={t('organizer:speakers.drawer.back')}
          data-testid="promote-subview-back"
        >
          <ArrowBackIcon />
        </IconButton>
        <Typography variant="h6">
          {t('organizer:speakerBrainstorm.promoteDialog.title', 'Promote to speaker')}
        </Typography>
      </Box>
      <Divider />

      <Box sx={{ flex: 1, overflow: 'auto', p: 2 }}>
        <Stack spacing={2}>
          <Typography variant="body2" color="text.secondary">
            {t(
              'organizer:speakerBrainstorm.promoteDialog.description',
              'The speaker moves to the READY lane and a user account is provisioned. No invitation email is sent yet — send the invitation from the READY lane when you are ready.'
            )}
          </Typography>

          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="subtitle2" gutterBottom>
              {t('organizer:speakerContent.speakerInformation', 'Speaker')}
            </Typography>
            <UserAutocomplete
              value={selectedUser}
              onChange={(u) => {
                setSelectedUser(u);
                setUserPickerError(undefined);
              }}
              error={userPickerError}
              label={t('organizer:speakerContent.form.username', 'Find existing speaker')}
              role="SPEAKER"
              disabled={promoteMutation.isPending}
              data-testid="promote-speaker-search-field"
            />

            {selectedUser && (
              <Box sx={{ mt: 2, p: 2, bgcolor: 'action.hover', borderRadius: 1 }}>
                <UserAvatar
                  firstName={selectedUser.firstName}
                  lastName={selectedUser.lastName}
                  company={selectedUser.companyId}
                  profilePictureUrl={selectedUser.profilePictureUrl}
                  size={40}
                  showCompany={true}
                />
                <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                  {selectedUser.email}
                </Typography>
                {selectedUser.roles && selectedUser.roles.length > 0 && (
                  <Typography variant="caption" color="text.secondary">
                    {t('organizer:speakerContent.form.roleLabel', 'Role')}:{' '}
                    {selectedUser.roles.join(', ')}
                  </Typography>
                )}
                <Box sx={{ mt: 2 }}>
                  <Button
                    variant="outlined"
                    size="small"
                    onClick={handleEditSpeaker}
                    disabled={promoteMutation.isPending}
                    fullWidth
                    data-testid="promote-edit-speaker-button"
                  >
                    {t('organizer:speakerContent.editSpeakerProfile', 'Edit speaker profile')}
                  </Button>
                </Box>
              </Box>
            )}

            <Box sx={{ mt: 2 }}>
              <Button
                variant="outlined"
                size="small"
                onClick={handleCreateSpeaker}
                disabled={promoteMutation.isPending}
                fullWidth
                data-testid="promote-create-new-speaker-button"
              >
                {t('organizer:speakerContent.createNewSpeaker', 'Create new speaker')}
              </Button>
            </Box>
          </Paper>

          {incompleteUserBanner}
          {errorAlert}
        </Stack>
      </Box>

      <Box
        sx={{
          p: 2,
          display: 'flex',
          justifyContent: 'flex-end',
          gap: 2,
          borderTop: 1,
          borderColor: 'divider',
        }}
      >
        <Button variant="outlined" onClick={onBack} disabled={promoteMutation.isPending}>
          {t('common:actions.cancel', 'Cancel')}
        </Button>
        <Button
          variant="contained"
          onClick={handlePromote}
          disabled={!canPromote || promoteMutation.isPending}
          startIcon={promoteMutation.isPending ? <CircularProgress size={16} /> : null}
          data-testid="promote-submit-button"
        >
          {promoteMutation.isPending
            ? t('organizer:speakerBrainstorm.promoteDialog.submitting', 'Promoting…')
            : t('organizer:speakerBrainstorm.promoteDialog.submitButton', 'Promote')}
        </Button>
      </Box>

      <UserCreateEditModal
        open={userModalOpen}
        onClose={handleUserModalClose}
        onSuccess={handleUserModalSuccess}
        user={editingUser}
      />
    </>
  );
};
