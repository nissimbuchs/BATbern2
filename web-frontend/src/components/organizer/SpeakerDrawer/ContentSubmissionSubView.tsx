/**
 * On-behalf content submission form (Story 11.D.4 — AC8;
 * Epic 11 bug fix 2026-05-19 stripped the bio + portrait overrides).
 *
 * Hosts inside the drawer's `drawerView === 'content-submission'` sub-view, or as the
 * `embedded` body of the Content TAB. The form collects:
 *   - Speaker (user-autocomplete, required) — picked to resolve `speaker_pool.username`.
 *   - Presentation title + abstract (required).
 *   - Optional presentation upload — DEFERRED: the speaker-portal materials upload uses
 *     a magic-link `?token=` path that isn't accessible from the organizer surface; per
 *     AC8 step 3 fallback this field is omitted until a dedicated organizer materials
 *     endpoint is added. The `SubmitContentRequest.presentationUploadId` field is wired
 *     and ready to accept the upload ID when that endpoint lands.
 *
 * Bio + portrait are NOT edited here — they are user-level attributes and are
 * managed via the user-edit modal (the "Edit speaker profile" button below the
 * autocomplete opens `UserCreateEditModal`). Carrying them on the per-event
 * content-submission form duplicates that surface with no added value.
 *
 * The request body NO LONGER includes `username` — the 11.C.2 backend reads the
 * resolved username from `speaker_pool` (set at provisioning time by Story 11.D.1's
 * promote flow). Sending `username` to the strict backend returns 400.
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
  TextField,
  Typography,
} from '@mui/material';
import { ArrowBack as ArrowBackIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { speakerContentService } from '@/services/speakerContentService';
import { sessionApiClient } from '@/services/api/sessionApiClient';
import { speakerPoolKeys } from '@/hooks/useSpeakerPool';
import { getUserByUsername, searchUsers, updateUserRoles } from '@/services/api/userManagementApi';
import { UserAutocomplete } from '@/components/shared/UserAutocomplete';
import { UserAvatar } from '@/components/shared/UserAvatar';
import UserCreateEditModal from '@/components/organizer/UserManagement/UserCreateEditModal';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';
import type { SubmitContentRequest } from '@/services/speakerContentService';
import type { UserSearchResponse, User, Role } from '@/types/user.types';

interface ContentSubmissionSubViewProps {
  speaker: SpeakerPoolEntry;
  eventCode: string;
  onBack: () => void;
  onClose: () => void;
  /**
   * When true the sub-view renders without its takeover chrome (back-arrow header bar +
   * onClose-on-success). Used by the drawer's Content TAB so the tab body looks like a
   * regular panel. Epic 11 bug fix 2026-05-18.
   */
  embedded?: boolean;
}

const MAX_ABSTRACT_LENGTH = 1000;

export const ContentSubmissionSubView: React.FC<ContentSubmissionSubViewProps> = ({
  speaker,
  eventCode,
  onBack,
  onClose,
  embedded = false,
}) => {
  const { t } = useTranslation(['organizer', 'common']);
  const queryClient = useQueryClient();

  const [selectedUser, setSelectedUser] = useState<UserSearchResponse | null>(null);
  const [presentationTitle, setPresentationTitle] = useState('');
  const [presentationAbstract, setPresentationAbstract] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});

  const [userModalOpen, setUserModalOpen] = useState(false);
  // `editingUser` holds the FULL `User` (UserResponse) shape — UserSearchResponse
  // omits `bio` / `isActive` / other user-level fields, and passing it to the modal
  // surfaces an empty bio even when the backend has one. Resolved on demand in
  // `handleEditSpeaker` (fetches the full projection via getUserByUsername).
  const [editingUser, setEditingUser] = useState<User | null>(null);

  const lastPrefilledSpeakerIdRef = useRef<string | null>(null);

  const submitContentMutation = useMutation({
    mutationFn: (request: SubmitContentRequest) =>
      speakerContentService.submitContent(eventCode, speaker.id, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['speakerStatusSummary', eventCode] });
      queryClient.invalidateQueries({ queryKey: speakerPoolKeys.list(eventCode) });
      queryClient.invalidateQueries({ queryKey: ['event', eventCode] });
      resetForm();
      // Takeover mode auto-closes the drawer on success; embedded mode leaves the
      // drawer open so the organizer sees the updated state in the now-CONTENT_SUBMITTED
      // panel (Epic 11 bug fix 2026-05-18 — Content as a tab).
      if (!embedded) {
        onClose();
      }
    },
  });

  // 2026-05-20 (Q#8) — READY-state draft save. Writes directly to sessions.title /
  // sessions.description (the canonical source per plan §2.9) without transitioning the
  // workflow state and without inserting a session_content_history row. The speaker can
  // still later accept normally; their accepted-state Save will be the first real
  // session_content_history audit row. PATCH /events/{code}/sessions/{slug} is the same
  // endpoint the organizer's session-edit modal uses.
  const saveDraftMutation = useMutation({
    mutationFn: async ({ title, description }: { title: string; description: string }) => {
      if (!speaker.sessionSlug) {
        throw new Error(
          'Session is not yet provisioned for this speaker — re-run promote-to-READY first.'
        );
      }
      return sessionApiClient.updateSession(eventCode, speaker.sessionSlug, {
        title,
        description,
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: speakerPoolKeys.list(eventCode) });
      queryClient.invalidateQueries({ queryKey: ['event', eventCode] });
      // Embedded mode: keep drawer open so the organizer sees the saved values reflected.
      if (!embedded) {
        onClose();
      }
    },
  });

  useEffect(() => {
    const prefillSpeaker = async () => {
      if (speaker && lastPrefilledSpeakerIdRef.current !== speaker.id) {
        try {
          // Epic 11 bug fix 2026-05-19 — once promoted, the pool entry carries
          // `username` (the linked User's meaningful ID). Direct lookup via
          // `getUserByUsername` is the only reliable resolver: `/users/search`
          // matches name/email substrings and does NOT match by username, so
          // `searchUsers("nissim.buchs.3")` returns `[]` even though the user
          // exists. Falls back to the legacy name-based search for unpromoted
          // speakers (where `speaker.username == null`).
          let resolved: UserSearchResponse | null = null;
          if (speaker.username) {
            const user = await getUserByUsername(speaker.username);
            if (user) {
              resolved = {
                id: user.id,
                email: user.email,
                firstName: user.firstName,
                lastName: user.lastName,
                roles: user.roles,
                profilePictureUrl: user.profilePictureUrl,
                companyId: user.companyId,
              };
            }
          }
          if (!resolved) {
            let users = await searchUsers(speaker.speakerName, 20);
            if (users.length === 0 && speaker.speakerName.includes(' ')) {
              const firstName = speaker.speakerName.split(' ')[0];
              users = await searchUsers(firstName, 20);
            }
            const candidates = users.filter((u) => u.roles?.includes('SPEAKER'));
            if (candidates.length > 0) {
              resolved = candidates[0];
            }
          }
          if (resolved) {
            setSelectedUser(resolved);
          }
          // 2026-05-20 (Q#9) — prefill both title and abstract from the canonical
          // sessions.title / sessions.description (mirrored as submittedTitle /
          // submittedAbstract on the pool response per plan §2.9). The old code only
          // read the now-dropped initialPresentationTitle column, so the abstract was
          // never prefilled and the title was empty in any state past READY. The
          // initialPresentationTitle fallback is kept for any legacy rows where
          // submittedTitle is still null.
          const prefilledTitle = speaker.submittedTitle || speaker.initialPresentationTitle || '';
          const prefilledAbstract = speaker.submittedAbstract || '';
          if (prefilledTitle) {
            setPresentationTitle(prefilledTitle);
          }
          if (prefilledAbstract) {
            setPresentationAbstract(prefilledAbstract);
          }
          lastPrefilledSpeakerIdRef.current = speaker.id;
        } catch (error) {
          console.error('Failed to prefill speaker:', error);
        }
      }
    };
    prefillSpeaker();
  }, [speaker]);

  const resetForm = () => {
    setSelectedUser(null);
    setPresentationTitle('');
    setPresentationAbstract('');
    setErrors({});
    lastPrefilledSpeakerIdRef.current = null;
  };

  // 2026-05-20 (Q#8) — in READY the workflow doesn't yet allow CONTENT_SUBMITTED, so the
  // form runs in "draft" mode: title + abstract are OPTIONAL, save writes directly to
  // sessions.title / sessions.description (no state transition, no audit row). In
  // ACCEPTED+ the form is a real submission; both title and abstract become required.
  const isReadyStateDraftOnly = speaker.status === 'READY';

  const validateForm = (): boolean => {
    const newErrors: Record<string, string> = {};
    if (!selectedUser) {
      newErrors.username = t('organizer:speakerContent.errors.usernameRequired');
    }
    if (!isReadyStateDraftOnly) {
      // Real submission: title + abstract required.
      if (!presentationTitle.trim()) {
        newErrors.presentationTitle = t('organizer:speakerContent.errors.titleRequired');
      }
      if (!presentationAbstract.trim()) {
        newErrors.presentationAbstract = t('organizer:speakerContent.errors.abstractRequired');
      } else if (presentationAbstract.length > MAX_ABSTRACT_LENGTH) {
        newErrors.presentationAbstract = t('organizer:speakerContent.errors.abstractTooLong', {
          max: MAX_ABSTRACT_LENGTH,
        });
      }
    } else {
      // Draft mode: only the abstract-length cap is enforced when an abstract is given.
      if (presentationAbstract.length > MAX_ABSTRACT_LENGTH) {
        newErrors.presentationAbstract = t('organizer:speakerContent.errors.abstractTooLong', {
          max: MAX_ABSTRACT_LENGTH,
        });
      }
    }
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async () => {
    if (!validateForm() || !selectedUser) return;

    const existingRoles = (selectedUser.roles ?? []) as Role[];
    if (!existingRoles.includes('SPEAKER')) {
      try {
        await updateUserRoles(selectedUser.id, [...existingRoles, 'SPEAKER']);
      } catch (error) {
        console.error('Failed to grant SPEAKER role:', error);
      }
    }

    if (isReadyStateDraftOnly) {
      // 2026-05-20 (Q#8) — draft path: PATCH sessions.title / .description directly.
      // No workflow transition, no session_content_history row.
      saveDraftMutation.mutate({
        title: presentationTitle.trim(),
        description: presentationAbstract.trim(),
      });
      return;
    }

    // Story 11.D.4 AC8 — request body shape matches the 11.C.2 backend DTO.
    // CRITICAL: NO `username` field (the strict backend rejects unknown properties).
    // Epic 11 bug fix 2026-05-19 — bio + profilePictureUrl removed; those are
    // user-level fields edited via the user-edit modal, not per-event submissions.
    const requestBody: SubmitContentRequest = {
      presentationTitle: presentationTitle.trim(),
      presentationAbstract: presentationAbstract.trim(),
    };
    // presentationUploadId is wired and ready for a future organizer-side materials
    // upload endpoint (per AC8 step 3 fallback — speaker-portal materials flow uses a
    // magic-link `?token=` that isn't reachable from the organizer drawer today).

    submitContentMutation.mutate(requestBody);
  };

  const handleEditSpeaker = async () => {
    if (!selectedUser) return;
    // Epic 11 bug fix 2026-05-19 — `selectedUser` is the narrow UserSearchResponse
    // returned by the autocomplete, which omits `bio` / `isActive` and other
    // user-level fields. Passing that to UserCreateEditModal renders an empty bio
    // even when the backend has one set (e.g. opening the same modal from the
    // user-list page shows the bio filled). Fetch the full `UserResponse` here so
    // every field round-trips correctly. Modal opens optimistically with the
    // narrow projection so the UI is responsive while the fetch resolves.
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

  const handleCreateSpeaker = () => {
    setEditingUser(null);
    setUserModalOpen(true);
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

  const remainingAbstractChars = MAX_ABSTRACT_LENGTH - presentationAbstract.length;
  const isAbstractTooLong = remainingAbstractChars < 0;

  // 2026-05-20 (Q#8) — `isReadyStateDraftOnly` is hoisted to the validate/submit closures
  // above (re-declaring it here would shadow). Used below to swap the submit button label
  // + banner + busy spinner between the draft and real-submit mutations.
  const isSavingDraft = saveDraftMutation.isPending;
  const isSubmitting = submitContentMutation.isPending;
  const isBusy = isSavingDraft || isSubmitting;

  return (
    <>
      {!embedded && (
        <>
          <Box sx={{ p: 1, display: 'flex', alignItems: 'center', gap: 1 }}>
            <IconButton
              onClick={onBack}
              size="small"
              aria-label={t('organizer:speakers.drawer.back')}
            >
              <ArrowBackIcon />
            </IconButton>
            <Typography variant="h6">{t('organizer:speakerContent.submitContent')}</Typography>
          </Box>
          <Divider />
        </>
      )}

      <Box sx={{ flex: 1, overflow: 'auto', p: 2 }}>
        <Typography variant="subtitle2" gutterBottom>
          {t('organizer:speakerContent.presentationDetails')}
        </Typography>

        {isReadyStateDraftOnly && (
          <Alert severity="info" sx={{ mb: 2 }} data-testid="content-tab-ready-banner">
            {t(
              'organizer:speakerContent.readyStateDraftBanner',
              'Speaker has not accepted yet. Saving here writes the title and abstract as a draft on the session — no submission is recorded, and the speaker can still update it after accepting.'
            )}
          </Alert>
        )}

        {submitContentMutation.isError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {submitContentMutation.error instanceof Error
              ? submitContentMutation.error.message
              : t('organizer:speakerContent.errors.submitFailed')}
          </Alert>
        )}

        {saveDraftMutation.isError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {saveDraftMutation.error instanceof Error
              ? saveDraftMutation.error.message
              : t('organizer:speakerContent.errors.saveDraftFailed', 'Could not save the draft.')}
          </Alert>
        )}

        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 2 }}>
          {/* Speaker selection */}
          <Box>
            <Typography variant="subtitle2" gutterBottom>
              {t('organizer:speakerContent.speakerInformation')}
            </Typography>
            <Paper variant="outlined" sx={{ p: 2 }}>
              <UserAutocomplete
                value={selectedUser}
                onChange={setSelectedUser}
                error={errors.username}
                label={t('organizer:speakerContent.form.username')}
                role="SPEAKER"
                disabled={submitContentMutation.isPending}
                data-testid="speaker-search-field"
              />

              {selectedUser && (
                <Box sx={{ mt: 2, p: 2, bgcolor: 'action.hover', borderRadius: 1 }}>
                  <Box sx={{ mb: 2 }}>
                    <UserAvatar
                      firstName={selectedUser.firstName}
                      lastName={selectedUser.lastName}
                      company={selectedUser.companyId}
                      profilePictureUrl={selectedUser.profilePictureUrl}
                      size={40}
                      showCompany={true}
                    />
                  </Box>
                  <Typography variant="body2" color="text.secondary" gutterBottom>
                    {selectedUser.email}
                  </Typography>
                  {selectedUser.roles && selectedUser.roles.length > 0 && (
                    <Typography variant="caption" color="text.secondary">
                      Role: {selectedUser.roles.join(', ')}
                    </Typography>
                  )}
                  <Box sx={{ mt: 2 }}>
                    <Button
                      variant="outlined"
                      size="small"
                      onClick={handleEditSpeaker}
                      disabled={submitContentMutation.isPending}
                      fullWidth
                    >
                      {t('organizer:speakerContent.editSpeakerProfile')}
                    </Button>
                  </Box>
                </Box>
              )}

              <Box sx={{ mt: 2 }}>
                <Button
                  variant="outlined"
                  size="small"
                  onClick={handleCreateSpeaker}
                  disabled={submitContentMutation.isPending}
                  fullWidth
                >
                  {t('organizer:speakerContent.createNewSpeaker')}
                </Button>
              </Box>
            </Paper>
          </Box>

          <TextField
            label={t('organizer:speakerContent.form.presentationTitle')}
            value={presentationTitle}
            onChange={(e) => setPresentationTitle(e.target.value)}
            error={!!errors.presentationTitle}
            helperText={errors.presentationTitle}
            required={!isReadyStateDraftOnly}
            fullWidth
            disabled={isBusy}
            inputProps={{ 'data-testid': 'presentation-title-field' }}
          />

          <TextField
            label={t('organizer:speakerContent.form.presentationAbstract')}
            value={presentationAbstract}
            onChange={(e) => setPresentationAbstract(e.target.value)}
            error={!!errors.presentationAbstract || isAbstractTooLong}
            helperText={
              errors.presentationAbstract ||
              `${remainingAbstractChars} / ${MAX_ABSTRACT_LENGTH} ${t('organizer:speakerContent.form.charactersRemaining')}`
            }
            required={!isReadyStateDraftOnly}
            multiline
            rows={6}
            fullWidth
            disabled={isBusy}
            inputProps={{ 'data-testid': 'presentation-abstract-field' }}
          />
        </Box>
      </Box>

      {/* Actions */}
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
        <Button variant="outlined" onClick={onBack} disabled={isBusy}>
          {t('common:actions.cancel')}
        </Button>
        <Button
          variant="contained"
          onClick={handleSubmit}
          // 2026-05-20 (Q#8) — `isReadyStateDraftOnly` no longer disables Save; in READY
          // we route through `saveDraftMutation` instead of `submitContentMutation`.
          disabled={isBusy || isAbstractTooLong}
          startIcon={isBusy ? <CircularProgress size={20} /> : null}
          data-testid="submit-speaker-content-button"
        >
          {isBusy
            ? t('organizer:speakerContent.submitting')
            : isReadyStateDraftOnly
              ? t('organizer:speakerContent.saveDraft', 'Save draft')
              : t('organizer:speakerContent.submitContent')}
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
