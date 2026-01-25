/**
 * Create/Edit Topic Modal Component
 * Story 5.2: Topic Selection & Speaker Brainstorming
 * Story 5.2a: Edit Topic Feature
 *
 * Modal dialog for creating new or editing existing topics in the backlog.
 * Features:
 * - Form with title, description, category fields
 * - Validation (required fields, max lengths)
 * - Duplicate detection warning (similarity >70%)
 * - Success/error feedback
 * - i18n support (German/English)
 * - Edit mode when topic prop is provided
 */

import React, { useState, useEffect } from 'react';
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
import type { CreateTopicRequest, Topic } from '@/types/topic.types';

export interface CreateTopicModalProps {
  open: boolean;
  topic?: Topic | null; // If provided, modal is in edit mode
  onClose: () => void;
  onSuccess?: () => void;
}

export const CreateTopicModal: React.FC<CreateTopicModalProps> = ({
  open,
  topic,
  onClose,
  onSuccess,
}) => {
  const { t } = useTranslation(['organizer', 'common']);
  const queryClient = useQueryClient();
  const isEditMode = !!topic;

  const [formData, setFormData] = useState<CreateTopicRequest>({
    title: '',
    description: '',
    category: '',
  });

  const [errors, setErrors] = useState<Record<string, string>>({});

  // Populate form when topic changes (edit mode)
  useEffect(() => {
    if (topic) {
      setFormData({
        title: topic.title,
        description: topic.description || '',
        category: topic.category,
      });
      setErrors({});
    } else {
      setFormData({ title: '', description: '', category: '' });
      setErrors({});
    }
  }, [topic, open]);

  // Mutation for creating/updating topic
  const createTopicMutation = useMutation({
    mutationFn: (request: CreateTopicRequest) => {
      if (isEditMode && topic) {
        return topicService.updateTopic(topic.topicCode, request);
      }
      return topicService.createTopic(request);
    },
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
    const modalPrefix = isEditMode ? 'editModal' : 'createModal';

    if (!formData.title.trim()) {
      newErrors.title = t(`topicBacklog.${modalPrefix}.errors.titleRequired`, 'Title is required');
    } else if (formData.title.length > 255) {
      newErrors.title = t(
        `topicBacklog.${modalPrefix}.errors.titleTooLong`,
        'Title must be 255 characters or less'
      );
    }

    if (!formData.category.trim()) {
      newErrors.category = t(
        `topicBacklog.${modalPrefix}.errors.categoryRequired`,
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

  const modalPrefix = isEditMode ? 'editModal' : 'createModal';

  return (
    <Dialog
      open={open}
      onClose={handleClose}
      maxWidth="sm"
      fullWidth
      data-testid="create-topic-modal"
    >
      <DialogTitle>
        {t(`topicBacklog.${modalPrefix}.title`, isEditMode ? 'Edit Topic' : 'Create New Topic')}
      </DialogTitle>

      <DialogContent>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 2 }}>
          {createTopicMutation.isError && (
            <Alert severity="error" data-testid="topic-form-error">
              {t(
                `topicBacklog.${modalPrefix}.errors.${isEditMode ? 'updateFailed' : 'createFailed'}`,
                `Failed to ${isEditMode ? 'update' : 'create'} topic. Please try again.`
              )}
            </Alert>
          )}

          <TextField
            label={t(`topicBacklog.${modalPrefix}.fields.title`, 'Title')}
            value={formData.title}
            onChange={(e) => setFormData({ ...formData, title: e.target.value })}
            error={!!errors.title}
            helperText={errors.title}
            required
            fullWidth
            disabled={createTopicMutation.isPending}
            inputProps={{ maxLength: 255, 'data-testid': 'topic-title-input' }}
          />

          <TextField
            label={t(`topicBacklog.${modalPrefix}.fields.description`, 'Description')}
            value={formData.description || ''}
            onChange={(e) => setFormData({ ...formData, description: e.target.value })}
            error={!!errors.description}
            helperText={errors.description}
            fullWidth
            multiline
            rows={4}
            disabled={createTopicMutation.isPending}
            inputProps={{ 'data-testid': 'topic-description-input' }}
          />

          <FormControl
            fullWidth
            required
            error={!!errors.category}
            disabled={createTopicMutation.isPending}
            data-testid="topic-category-select"
          >
            <InputLabel>{t(`topicBacklog.${modalPrefix}.fields.category`, 'Category')}</InputLabel>
            <Select
              value={formData.category}
              onChange={(e) => setFormData({ ...formData, category: e.target.value })}
              label={t(`topicBacklog.${modalPrefix}.fields.category`, 'Category')}
              data-testid="topic-category-select-input"
            >
              <MenuItem value="technical" data-testid="category-option-technical">
                {t('topicBacklog.filters.categories.technical', 'Technical')}
              </MenuItem>
              <MenuItem value="management" data-testid="category-option-management">
                {t('topicBacklog.filters.categories.management', 'Management')}
              </MenuItem>
              <MenuItem value="soft_skills" data-testid="category-option-soft-skills">
                {t('topicBacklog.filters.categories.softSkills', 'Soft Skills')}
              </MenuItem>
              <MenuItem value="industry_trends" data-testid="category-option-industry-trends">
                {t('topicBacklog.filters.categories.industryTrends', 'Industry Trends')}
              </MenuItem>
              <MenuItem value="tools_platforms" data-testid="category-option-tools-platforms">
                {t('topicBacklog.filters.categories.toolsPlatforms', 'Tools & Platforms')}
              </MenuItem>
            </Select>
            {errors.category && <FormHelperText>{errors.category}</FormHelperText>}
          </FormControl>
        </Box>
      </DialogContent>

      <DialogActions>
        <Button
          onClick={handleClose}
          disabled={createTopicMutation.isPending}
          data-testid="cancel-topic-button"
        >
          {t('common.cancel', 'Cancel')}
        </Button>
        <Button
          variant="contained"
          onClick={handleSubmit}
          disabled={createTopicMutation.isPending}
          startIcon={createTopicMutation.isPending ? <CircularProgress size={20} /> : undefined}
          data-testid="submit-topic-button"
        >
          {createTopicMutation.isPending
            ? t(
                `topicBacklog.${modalPrefix}.${isEditMode ? 'saving' : 'creating'}`,
                isEditMode ? 'Saving...' : 'Creating...'
              )
            : t(
                `topicBacklog.${modalPrefix}.${isEditMode ? 'save' : 'create'}`,
                isEditMode ? 'Save Changes' : 'Create Topic'
              )}
        </Button>
      </DialogActions>
    </Dialog>
  );
};
