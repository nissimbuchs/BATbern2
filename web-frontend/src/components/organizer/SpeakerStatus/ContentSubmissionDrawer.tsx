/**
 * Content Submission Drawer Component (Story 5.5)
 *
 * Drawer for submitting speaker presentation content
 * Features:
 * - Slide-in drawer from right side (600px)
 * - Speaker information summary
 * - Presentation title and abstract form
 * - Character counter for abstract (max 1000 chars)
 * - Form validation
 * - Submit creates session + updates speaker status to CONTENT_SUBMITTED
 */

import React, { useState, useEffect, useRef } from 'react';
import {
  Drawer,
  Box,
  Typography,
  IconButton,
  Divider,
  TextField,
  Button,
  Paper,
  Chip,
  Alert,
  CircularProgress,
} from '@mui/material';
import { Close as CloseIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { speakerContentService } from '@/services/speakerContentService';
import { speakerPoolKeys } from '@/hooks/useSpeakerPool';
import { searchUsers } from '@/services/api/userManagementApi';
import { UserAutocomplete } from '@/components/shared/UserAutocomplete';
import { UserAvatar } from '@/components/shared/UserAvatar';
import UserCreateEditModal from '@/components/organizer/UserManagement/UserCreateEditModal';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';
import type { SubmitContentRequest } from '@/services/speakerContentService';
import type { UserSearchResponse, User } from '@/types/user.types';

interface ContentSubmissionDrawerProps {
  open: boolean;
  onClose: () => void;
  speaker: SpeakerPoolEntry | null;
  eventCode: string;
}

const MAX_ABSTRACT_LENGTH = 1000;

export const ContentSubmissionDrawer: React.FC<ContentSubmissionDrawerProps> = ({
  open,
  onClose,
  speaker,
  eventCode,
}) => {
  const { t } = useTranslation('organizer');
  const queryClient = useQueryClient();

  // Form state
  const [selectedUser, setSelectedUser] = useState<UserSearchResponse | null>(null);
  const [presentationTitle, setPresentationTitle] = useState('');
  const [presentationAbstract, setPresentationAbstract] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});

  // User modal state
  const [userModalOpen, setUserModalOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<UserSearchResponse | null>(null);

  // Track last prefilled speaker to avoid duplicate prefills
  const lastPrefilledSpeakerIdRef = useRef<string | null>(null);

  // Mutation for submitting content
  const submitContentMutation = useMutation({
    mutationFn: (request: SubmitContentRequest) =>
      speakerContentService.submitContent(eventCode, speaker?.id || '', request),
    onSuccess: () => {
      // Invalidate queries to refetch data
      queryClient.invalidateQueries({ queryKey: ['speakerStatusSummary', eventCode] });
      queryClient.invalidateQueries({ queryKey: speakerPoolKeys.list(eventCode) });
      // Invalidate event cache to update Sessions tab (matches ['event', eventCode, include])
      queryClient.invalidateQueries({ queryKey: ['event', eventCode] });

      // Reset form and close drawer
      resetForm();
      onClose();
    },
  });

  // Prefill speaker and presentation title from pool when drawer opens
  useEffect(() => {
    const prefillSpeaker = async () => {
      // Only prefill if drawer is open, speaker exists, and we haven't prefilled this speaker yet
      if (open && speaker && lastPrefilledSpeakerIdRef.current !== speaker.id) {
        try {
          // Try searching by full name first
          let users = await searchUsers(speaker.speakerName, 20);

          // If no results, try searching by first name only
          if (users.length === 0 && speaker.speakerName.includes(' ')) {
            const firstName = speaker.speakerName.split(' ')[0];
            users = await searchUsers(firstName, 20);
          }

          // Filter by SPEAKER role
          const speakers = users.filter((u) => u.roles?.includes('SPEAKER'));

          // Auto-select first match if found
          if (speakers.length > 0) {
            setSelectedUser(speakers[0]);
          }

          // Prefill presentation title from speaker's initial response (Story 6.2a)
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
  }, [open, speaker]);

  const resetForm = () => {
    setSelectedUser(null);
    setPresentationTitle('');
    setPresentationAbstract('');
    setErrors({});
    lastPrefilledSpeakerIdRef.current = null; // Reset prefill tracker
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

  const handleSubmit = () => {
    if (!validateForm() || !selectedUser) {
      return;
    }

    submitContentMutation.mutate({
      username: selectedUser.id, // id is the username
      presentationTitle: presentationTitle.trim(),
      presentationAbstract: presentationAbstract.trim(),
    });
  };

  const handleCancel = () => {
    resetForm();
    onClose();
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
    // Refresh user search results
    queryClient.invalidateQueries({ queryKey: ['users', 'search'] });
  };

  const handleUserModalSuccess = (createdOrUpdatedUser?: User) => {
    // Auto-select newly created/updated speaker
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
    <Drawer
      anchor="right"
      open={open}
      onClose={handleCancel}
      PaperProps={{
        sx: { width: { xs: '100%', sm: 600 } },
      }}
    >
      <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
        {/* Header */}
        <Box sx={{ p: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="h6">{t('speakerContent.submitContent')}</Typography>
          <IconButton
            onClick={handleCancel}
            size="small"
            disabled={submitContentMutation.isPending}
          >
            <CloseIcon />
          </IconButton>
        </Box>

        <Divider />

        {/* Speaker Info (Display Only) */}
        {speaker && (
          <Paper sx={{ m: 2, p: 2 }} elevation={1}>
            <Typography variant="subtitle1" fontWeight="bold" gutterBottom>
              {speaker.speakerName}
            </Typography>
            {speaker.company && (
              <Typography variant="body2" color="text.secondary" gutterBottom>
                {speaker.company}
              </Typography>
            )}
            {speaker.expertise && (
              <Typography variant="body2" color="text.secondary" gutterBottom>
                {t('speakerBrainstorm.form.expertise')}: {speaker.expertise}
              </Typography>
            )}
            <Box mt={1}>
              <Chip label={speaker.status} size="small" color="success" />
            </Box>
          </Paper>
        )}

        <Divider />

        {/* Content Submission Form */}
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
                {/* User Autocomplete */}
                <UserAutocomplete
                  value={selectedUser}
                  onChange={setSelectedUser}
                  error={errors.username}
                  label={t('speakerContent.form.username')}
                  role="SPEAKER"
                  disabled={submitContentMutation.isPending}
                  data-testid="speaker-search-field"
                />

                {/* Selected User Display */}
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

                    {/* Edit Button */}
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

                {/* Create New Speaker Button - Always Visible */}
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

            {/* Presentation Title */}
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

            {/* Presentation Abstract */}
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
          <Button
            variant="outlined"
            onClick={handleCancel}
            disabled={submitContentMutation.isPending}
          >
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
      </Box>

      {/* User Create/Edit Modal */}
      <UserCreateEditModal
        open={userModalOpen}
        onClose={handleUserModalClose}
        onSuccess={handleUserModalSuccess}
        user={(editingUser as User) || null}
      />
    </Drawer>
  );
};

export default ContentSubmissionDrawer;
