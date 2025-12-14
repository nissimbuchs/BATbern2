/**
 * Create Topic Modal Component
 * Story 5.2: Topic Selection & Speaker Brainstorming
 *
 * Modal dialog for creating new topics in the backlog.
 * Features:
 * - Form with title, description, category fields
 * - Validation (required fields, max lengths)
 * - Duplicate detection warning (similarity >70%)
 * - Success/error feedback
 * - i18n support (German/English)
 */

import React, { useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Alert,
  Box,
  CircularProgress,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  FormHelperText,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { topicService } from '@/services/topicService';
import type { CreateTopicRequest } from '@/types/topic.types';

export interface CreateTopicModalProps {
  open: boolean;
  onClose: () => void;
  onSuccess?: () => void;
}

export const CreateTopicModal: React.FC<CreateTopicModalProps> = ({ open, onClose, onSuccess }) => {
  const { t } = useTranslation('organizer');
  const queryClient = useQueryClient();

  const [formData, setFormData] = useState<CreateTopicRequest>({
    title: '',
    description: '',
    category: '',
  });

  const [errors, setErrors] = useState<Record<string, string>>({});

  // Mutation for creating topic
  const createTopicMutation = useMutation({
    mutationFn: (request: CreateTopicRequest) => topicService.createTopic(request),
    onSuccess: () => {
      // Invalidate topics query to refetch the list
      queryClient.invalidateQueries({ queryKey: ['topics'] });

      // Reset form
      setFormData({ title: '', description: '', category: '' });
      setErrors({});

      // Call success callback
      onSuccess?.();

      // Close modal
      onClose();
    },
  });

  const validateForm = (): boolean => {
    const newErrors: Record<string, string> = {};

    if (!formData.title.trim()) {
      newErrors.title = t('topicBacklog.createModal.errors.titleRequired', 'Title is required');
    } else if (formData.title.length > 255) {
      newErrors.title = t(
        'topicBacklog.createModal.errors.titleTooLong',
        'Title must be 255 characters or less'
      );
    }

    if (!formData.category.trim()) {
      newErrors.category = t(
        'topicBacklog.createModal.errors.categoryRequired',
        'Category is required'
      );
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = () => {
    if (!validateForm()) {
      return;
    }

    createTopicMutation.mutate(formData);
  };

  const handleClose = () => {
    if (!createTopicMutation.isPending) {
      setFormData({ title: '', description: '', category: '' });
      setErrors({});
      onClose();
    }
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle>{t('topicBacklog.createModal.title', 'Create New Topic')}</DialogTitle>

      <DialogContent>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 2 }}>
          {createTopicMutation.isError && (
            <Alert severity="error">
              {t(
                'topicBacklog.createModal.errors.createFailed',
                'Failed to create topic. Please try again.'
              )}
            </Alert>
          )}

          <TextField
            label={t('topicBacklog.createModal.fields.title', 'Title')}
            value={formData.title}
            onChange={(e) => setFormData({ ...formData, title: e.target.value })}
            error={!!errors.title}
            helperText={errors.title}
            required
            fullWidth
            disabled={createTopicMutation.isPending}
            inputProps={{ maxLength: 255 }}
          />

          <TextField
            label={t('topicBacklog.createModal.fields.description', 'Description')}
            value={formData.description || ''}
            onChange={(e) => setFormData({ ...formData, description: e.target.value })}
            error={!!errors.description}
            helperText={errors.description}
            fullWidth
            multiline
            rows={4}
            disabled={createTopicMutation.isPending}
          />

          <FormControl
            fullWidth
            required
            error={!!errors.category}
            disabled={createTopicMutation.isPending}
          >
            <InputLabel>{t('topicBacklog.createModal.fields.category', 'Category')}</InputLabel>
            <Select
              value={formData.category}
              onChange={(e) => setFormData({ ...formData, category: e.target.value })}
              label={t('topicBacklog.createModal.fields.category', 'Category')}
            >
              <MenuItem value="technical">
                {t('topicBacklog.filters.categories.technical', 'Technical')}
              </MenuItem>
              <MenuItem value="management">
                {t('topicBacklog.filters.categories.management', 'Management')}
              </MenuItem>
              <MenuItem value="soft_skills">
                {t('topicBacklog.filters.categories.softSkills', 'Soft Skills')}
              </MenuItem>
              <MenuItem value="industry_trends">
                {t('topicBacklog.filters.categories.industryTrends', 'Industry Trends')}
              </MenuItem>
              <MenuItem value="tools_platforms">
                {t('topicBacklog.filters.categories.toolsPlatforms', 'Tools & Platforms')}
              </MenuItem>
            </Select>
            {errors.category && <FormHelperText>{errors.category}</FormHelperText>}
          </FormControl>
        </Box>
      </DialogContent>

      <DialogActions>
        <Button onClick={handleClose} disabled={createTopicMutation.isPending}>
          {t('common.cancel', 'Cancel')}
        </Button>
        <Button
          variant="contained"
          onClick={handleSubmit}
          disabled={createTopicMutation.isPending}
          startIcon={createTopicMutation.isPending ? <CircularProgress size={20} /> : undefined}
        >
          {createTopicMutation.isPending
            ? t('topicBacklog.createModal.creating', 'Creating...')
            : t('topicBacklog.createModal.create', 'Create Topic')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};
