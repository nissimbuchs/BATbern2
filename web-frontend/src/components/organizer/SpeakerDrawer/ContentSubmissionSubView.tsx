import React, { useState, useEffect, useRef } from 'react';
import {
  Box,
  Typography,
  IconButton,
  Divider,
  TextField,
  Button,
  Paper,
  Alert,
  CircularProgress,
} from '@mui/material';
import { ArrowBack as ArrowBackIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { speakerContentService } from '@/services/speakerContentService';
import { speakerPoolKeys } from '@/hooks/useSpeakerPool';
import { searchUsers, updateUserRoles } from '@/services/api/userManagementApi';
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
}

const MAX_ABSTRACT_LENGTH = 1000;

export const ContentSubmissionSubView: React.FC<ContentSubmissionSubViewProps> = ({
  speaker,
  eventCode,
  onBack,
  onClose,
}) => {
  const { t } = useTranslation('organizer');
  const queryClient = useQueryClient();

  const [selectedUser, setSelectedUser] = useState<UserSearchResponse | null>(null);
  const [presentationTitle, setPresentationTitle] = useState('');
  const [presentationAbstract, setPresentationAbstract] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});

  const [userModalOpen, setUserModalOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<UserSearchResponse | null>(null);

  const lastPrefilledSpeakerIdRef = useRef<string | null>(null);

  const submitContentMutation = useMutation({
    mutationFn: (request: SubmitContentRequest) =>
      speakerContentService.submitContent(eventCode, speaker.id, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['speakerStatusSummary', eventCode] });
      queryClient.invalidateQueries({ queryKey: speakerPoolKeys.list(eventCode) });
      queryClient.invalidateQueries({ queryKey: ['event', eventCode] });
      resetForm();
      onClose();
    },
  });

  useEffect(() => {
    const prefillSpeaker = async () => {
      if (speaker && lastPrefilledSpeakerIdRef.current !== speaker.id) {
        try {
          let users = await searchUsers(speaker.speakerName, 20);
          if (users.length === 0 && speaker.speakerName.includes(' ')) {
            const firstName = speaker.speakerName.split(' ')[0];
            users = await searchUsers(firstName, 20);
          }
          const speakers = users.filter((u) => u.roles?.includes('SPEAKER'));
          if (speakers.length > 0) {
            setSelectedUser(speakers[0]);
          }
          if (speaker.initialPresentationTitle) {
            setPresentationTitle(speaker.initialPresentationTitle);
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

  const validateForm = (): boolean => {
    const newErrors: Record<string, string> = {};
    if (!selectedUser) {
      newErrors.username = t('speakerContent.errors.usernameRequired');
    }
    if (!presentationTitle.trim()) {
      newErrors.presentationTitle = t('speakerContent.errors.titleRequired');
    }
    if (!presentationAbstract.trim()) {
      newErrors.presentationAbstract = t('speakerContent.errors.abstractRequired');
    } else if (presentationAbstract.length > MAX_ABSTRACT_LENGTH) {
      newErrors.presentationAbstract = t('speakerContent.errors.abstractTooLong', {
        max: MAX_ABSTRACT_LENGTH,
      });
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

    submitContentMutation.mutate({
      username: selectedUser.id,
      presentationTitle: presentationTitle.trim(),
      presentationAbstract: presentationAbstract.trim(),
    });
  };

  const handleEditSpeaker = () => {
    setEditingUser(selectedUser);
    setUserModalOpen(true);
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

  const remainingChars = MAX_ABSTRACT_LENGTH - presentationAbstract.length;
  const isAbstractTooLong = remainingChars < 0;

  return (
    <>
      {/* Back Button */}
      <Box sx={{ p: 1, display: 'flex', alignItems: 'center', gap: 1 }}>
        <IconButton onClick={onBack} size="small">
          <ArrowBackIcon />
        </IconButton>
        <Typography variant="h6">{t('speakerContent.submitContent')}</Typography>
      </Box>

      <Divider />

      {/* Form Content */}
      <Box sx={{ flex: 1, overflow: 'auto', p: 2 }}>
        <Typography variant="subtitle2" gutterBottom>
          {t('speakerContent.presentationDetails')}
        </Typography>

        {submitContentMutation.isError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {submitContentMutation.error instanceof Error
              ? submitContentMutation.error.message
              : t('speakerContent.errors.submitFailed')}
          </Alert>
        )}

        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 2 }}>
          {/* Speaker Information Section */}
          <Box>
            <Typography variant="subtitle2" gutterBottom>
              {t('speakerContent.speakerInformation')}
            </Typography>
            <Paper variant="outlined" sx={{ p: 2 }}>
              <UserAutocomplete
                value={selectedUser}
                onChange={setSelectedUser}
                error={errors.username}
                label={t('speakerContent.form.username')}
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
                      {t('speakerContent.editSpeakerProfile')}
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
                  {t('speakerContent.createNewSpeaker')}
                </Button>
              </Box>
            </Paper>
          </Box>

          <TextField
            label={t('speakerContent.form.presentationTitle')}
            value={presentationTitle}
            onChange={(e) => setPresentationTitle(e.target.value)}
            error={!!errors.presentationTitle}
            helperText={errors.presentationTitle}
            required
            fullWidth
            disabled={submitContentMutation.isPending}
            inputProps={{ 'data-testid': 'presentation-title-field' }}
          />

          <TextField
            label={t('speakerContent.form.presentationAbstract')}
            value={presentationAbstract}
            onChange={(e) => setPresentationAbstract(e.target.value)}
            error={!!errors.presentationAbstract || isAbstractTooLong}
            helperText={
              errors.presentationAbstract ||
              `${remainingChars} / ${MAX_ABSTRACT_LENGTH} ${t('speakerContent.form.charactersRemaining')}`
            }
            required
            multiline
            rows={6}
            fullWidth
            disabled={submitContentMutation.isPending}
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
        <Button variant="outlined" onClick={onBack} disabled={submitContentMutation.isPending}>
          {t('common:actions.cancel')}
        </Button>
        <Button
          variant="contained"
          onClick={handleSubmit}
          disabled={submitContentMutation.isPending || isAbstractTooLong}
          startIcon={submitContentMutation.isPending ? <CircularProgress size={20} /> : null}
          data-testid="submit-speaker-content-button"
        >
          {submitContentMutation.isPending
            ? t('speakerContent.submitting')
            : t('speakerContent.submitContent')}
        </Button>
      </Box>

      <UserCreateEditModal
        open={userModalOpen}
        onClose={handleUserModalClose}
        onSuccess={handleUserModalSuccess}
        user={(editingUser as User) || null}
      />
    </>
  );
};
