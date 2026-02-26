/**
 * SessionSpeakersTab Component
 *
 * Tab content for managing speakers assigned to a session.
 * Displayed as tab index 2 in SessionEditModal.
 *
 * Features:
 * - List current session speakers with role and confirmation status
 * - Remove a speaker from the session
 * - Set a speaker as primary (DELETE + re-POST with PRIMARY_SPEAKER role)
 * - Add a speaker via UserAutocomplete (search any user by name) + role select
 * - Create a new user inline via UserCreateEditModal
 *
 * All mutations operate directly against the session_speaker table.
 * Speaker pool is NOT involved.
 */

import React, { useState, useEffect } from 'react';
import {
  Box,
  Typography,
  Button,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Chip,
  IconButton,
  Alert,
  Paper,
  Divider,
  CircularProgress,
  Stack,
} from '@mui/material';
import {
  StarOutline as SetPrimaryIcon,
  Star as PrimaryIcon,
  Delete as RemoveIcon,
  CheckCircle as ConfirmedIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { UserAutocomplete } from '@/components/shared/UserAutocomplete';
import { UserAvatar } from '@/components/shared/UserAvatar';
import UserCreateEditModal from '@/components/organizer/UserManagement/UserCreateEditModal';
import { useAssignSpeaker, useRemoveSpeaker } from '@/hooks/useSessionSpeakers';
import { updateUserRoles } from '@/services/api/userManagementApi';
import type { SessionUI, SessionSpeaker } from '@/types/event.types';
import type { UserSearchResponse, User, Role } from '@/types/user.types';

interface SessionSpeakersTabProps {
  session: SessionUI;
}

type SpeakerRole = 'PRIMARY_SPEAKER' | 'CO_SPEAKER' | 'MODERATOR' | 'PANELIST';

const SPEAKER_ROLES: SpeakerRole[] = ['PRIMARY_SPEAKER', 'CO_SPEAKER', 'MODERATOR', 'PANELIST'];

export const SessionSpeakersTab: React.FC<SessionSpeakersTabProps> = ({ session }) => {
  const { t } = useTranslation('events');
  const queryClient = useQueryClient();

  // Add form state
  const [selectedUser, setSelectedUser] = useState<UserSearchResponse | null>(null);
  const [addRole, setAddRole] = useState<SpeakerRole>('CO_SPEAKER');
  const [addError, setAddError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  // User create modal state
  const [userModalOpen, setUserModalOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<UserSearchResponse | null>(null);

  // Track which speaker is being mutated (for per-row loading states)
  const [mutatingUsername, setMutatingUsername] = useState<string | null>(null);

  const assignMutation = useAssignSpeaker();
  const removeMutation = useRemoveSpeaker();

  // Local speakers state — source of truth for the list while the modal is open.
  // Initialized from session.speakers and updated directly from API responses so
  // the list reflects changes immediately without depending on the parent re-render.
  const [localSpeakers, setLocalSpeakers] = useState<SessionSpeaker[]>(session.speakers ?? []);

  // Sync when a different session is opened (sessionSlug changes)
  useEffect(() => {
    setLocalSpeakers(session.speakers ?? []);
  }, [session.sessionSlug]);

  const hasPrimary = localSpeakers.some((s) => s.speakerRole === 'PRIMARY_SPEAKER');

  // Default new speaker role to PRIMARY_SPEAKER when no speakers exist yet
  useEffect(() => {
    setAddRole(hasPrimary ? 'CO_SPEAKER' : 'PRIMARY_SPEAKER');
  }, [hasPrimary]);

  // ─── Handlers ───────────────────────────────────────────────────────────────

  const handleRemove = async (speaker: SessionSpeaker) => {
    setMutatingUsername(speaker.username);
    setActionError(null);
    try {
      await removeMutation.mutateAsync({
        eventCode: session.eventCode,
        sessionSlug: session.sessionSlug,
        username: speaker.username,
      });
      setLocalSpeakers((prev) => prev.filter((s) => s.username !== speaker.username));
    } catch (err) {
      setActionError(
        err instanceof Error
          ? err.message
          : t('sessionEdit.speakers.removeError', 'Failed to remove speaker')
      );
    } finally {
      setMutatingUsername(null);
    }
  };

  const handleSetPrimary = async (speaker: SessionSpeaker) => {
    setMutatingUsername(speaker.username);
    setActionError(null);
    try {
      // Demote the current primary speaker to CO_SPEAKER (if one exists)
      const currentPrimary = localSpeakers.find(
        (s) => s.speakerRole === 'PRIMARY_SPEAKER' && s.username !== speaker.username
      );
      if (currentPrimary) {
        await removeMutation.mutateAsync({
          eventCode: session.eventCode,
          sessionSlug: session.sessionSlug,
          username: currentPrimary.username,
        });
        const demoted = await assignMutation.mutateAsync({
          eventCode: session.eventCode,
          sessionSlug: session.sessionSlug,
          request: { username: currentPrimary.username, speakerRole: 'CO_SPEAKER' },
        });
        setLocalSpeakers((prev) => [
          ...prev.filter((s) => s.username !== currentPrimary.username),
          demoted,
        ]);
      }

      // Promote the selected speaker to PRIMARY_SPEAKER
      await removeMutation.mutateAsync({
        eventCode: session.eventCode,
        sessionSlug: session.sessionSlug,
        username: speaker.username,
      });
      const updated = await assignMutation.mutateAsync({
        eventCode: session.eventCode,
        sessionSlug: session.sessionSlug,
        request: { username: speaker.username, speakerRole: 'PRIMARY_SPEAKER' },
      });
      setLocalSpeakers((prev) => [...prev.filter((s) => s.username !== speaker.username), updated]);
    } catch (err) {
      setActionError(
        err instanceof Error
          ? err.message
          : t('sessionEdit.speakers.setPrimaryError', 'Failed to set primary speaker')
      );
    } finally {
      setMutatingUsername(null);
    }
  };

  const handleAdd = async () => {
    if (!selectedUser) {
      setAddError(t('sessionEdit.speakers.userRequired', 'Please select a user'));
      return;
    }
    setAddError(null);
    try {
      const newSpeaker = await assignMutation.mutateAsync({
        eventCode: session.eventCode,
        sessionSlug: session.sessionSlug,
        request: { username: selectedUser.id, speakerRole: addRole },
      });
      setLocalSpeakers((prev) => [...prev, newSpeaker]);

      // Clear form immediately — role grant is non-fatal
      const userToGrant = selectedUser;
      setSelectedUser(null);

      // Grant SPEAKER role if not already present (fire-and-forget, non-fatal)
      const existingRoles = (userToGrant.roles ?? []) as Role[];
      if (!existingRoles.includes('SPEAKER')) {
        updateUserRoles(userToGrant.id, [...existingRoles, 'SPEAKER']).catch((err) => {
          console.error('Failed to grant SPEAKER role:', err);
        });
      }
    } catch (err) {
      setAddError(
        err instanceof Error
          ? err.message
          : t('sessionEdit.speakers.addError', 'Failed to add speaker')
      );
    }
  };

  // ─── User modal handlers (same pattern as ContentSubmissionDrawer) ───────────

  const handleCreateUser = () => {
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

  // ─── Render ──────────────────────────────────────────────────────────────────

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
      {/* Current Speakers */}
      <Typography variant="subtitle2">
        {t('sessionEdit.speakers.title', 'Session Speakers')}
      </Typography>

      {actionError && (
        <Alert severity="error" onClose={() => setActionError(null)}>
          {actionError}
        </Alert>
      )}

      {localSpeakers.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          {t('sessionEdit.speakers.noSpeakers', 'No speakers assigned yet')}
        </Typography>
      ) : (
        <Stack spacing={1}>
          {localSpeakers.map((speaker) => {
            const isMutating = mutatingUsername === speaker.username;
            const isPrimary = speaker.speakerRole === 'PRIMARY_SPEAKER';

            return (
              <Paper key={speaker.username} variant="outlined" sx={{ p: 1.5 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  {/* Avatar + name */}
                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    <UserAvatar
                      firstName={speaker.firstName}
                      lastName={speaker.lastName}
                      company={speaker.company}
                      profilePictureUrl={speaker.profilePictureUrl}
                      size={36}
                      showCompany={true}
                      horizontal={true}
                    />
                  </Box>

                  {/* Role + confirmed */}
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                    <Chip
                      label={t(
                        `sessionEdit.speakers.roles.${speaker.speakerRole}`,
                        speaker.speakerRole
                      )}
                      size="small"
                      color={isPrimary ? 'primary' : 'default'}
                      icon={isPrimary ? <PrimaryIcon fontSize="small" /> : undefined}
                    />
                    {speaker.isConfirmed && (
                      <ConfirmedIcon
                        fontSize="small"
                        color="success"
                        titleAccess={t('sessionEdit.speakers.confirmed', 'Confirmed')}
                      />
                    )}
                  </Box>

                  {/* Actions */}
                  {isMutating ? (
                    <CircularProgress size={20} />
                  ) : (
                    <>
                      {!isPrimary && (
                        <IconButton
                          size="small"
                          onClick={() => handleSetPrimary(speaker)}
                          title={t('sessionEdit.speakers.setPrimary', 'Set Primary')}
                          disabled={mutatingUsername !== null}
                        >
                          <SetPrimaryIcon fontSize="small" />
                        </IconButton>
                      )}
                      <IconButton
                        size="small"
                        color="error"
                        onClick={() => handleRemove(speaker)}
                        title={t('sessionEdit.speakers.remove', 'Remove')}
                        disabled={mutatingUsername !== null}
                      >
                        <RemoveIcon fontSize="small" />
                      </IconButton>
                    </>
                  )}
                </Box>
              </Paper>
            );
          })}
        </Stack>
      )}

      <Divider />

      {/* Add Speaker Form */}
      <Typography variant="subtitle2">
        {t('sessionEdit.speakers.addSpeaker', 'Add Speaker')}
      </Typography>

      {addError && (
        <Alert severity="error" onClose={() => setAddError(null)}>
          {addError}
        </Alert>
      )}

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {/* User search */}
          <UserAutocomplete
            value={selectedUser}
            onChange={(user) => {
              setSelectedUser(user);
              if (addError) setAddError(null);
            }}
            label={t('sessionEdit.speakers.searchUser', 'Search User')}
            disabled={assignMutation.isPending}
            data-testid="session-speaker-search"
          />

          {/* Selected user preview */}
          {selectedUser && (
            <Box sx={{ p: 1, bgcolor: 'action.hover', borderRadius: 1 }}>
              <UserAvatar
                firstName={selectedUser.firstName}
                lastName={selectedUser.lastName}
                company={selectedUser.companyId}
                profilePictureUrl={selectedUser.profilePictureUrl}
                size={32}
                showCompany={true}
                horizontal={true}
              />
            </Box>
          )}

          {/* Create new user shortcut */}
          <Button
            variant="outlined"
            size="small"
            onClick={handleCreateUser}
            disabled={assignMutation.isPending}
          >
            {t('sessionEdit.speakers.createNewUser', '+ Create New User')}
          </Button>

          {/* Role selector */}
          <FormControl size="small" fullWidth>
            <InputLabel>{t('sessionEdit.speakers.roleLabel', 'Speaker Role')}</InputLabel>
            <Select
              value={addRole}
              label={t('sessionEdit.speakers.roleLabel', 'Speaker Role')}
              onChange={(e) => setAddRole(e.target.value as SpeakerRole)}
              disabled={assignMutation.isPending}
            >
              {SPEAKER_ROLES.map((role) => (
                <MenuItem key={role} value={role}>
                  {t(`sessionEdit.speakers.roles.${role}`, role)}
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          {/* Add button */}
          <Button
            variant="contained"
            onClick={handleAdd}
            disabled={assignMutation.isPending || !selectedUser}
            startIcon={assignMutation.isPending ? <CircularProgress size={20} /> : undefined}
          >
            {assignMutation.isPending
              ? t('common.saving', 'Saving...')
              : t('sessionEdit.speakers.addSpeaker', 'Add Speaker')}
          </Button>
        </Box>
      </Paper>

      {/* User Create Modal */}
      <UserCreateEditModal
        open={userModalOpen}
        onClose={handleUserModalClose}
        onSuccess={handleUserModalSuccess}
        user={(editingUser as User) || null}
      />
    </Box>
  );
};
