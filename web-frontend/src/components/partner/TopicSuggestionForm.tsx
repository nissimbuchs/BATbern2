/**
 * TopicSuggestionForm
 * Story 8.2: AC3, AC7 — Task 8
 *
 * MUI Dialog for submitting a new topic suggestion.
 * Fields: Title (required, min 5 chars), Description (optional, max 500 chars).
 */

import React, { useState } from 'react';
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  TextField,
} from '@mui/material';
import { useTranslation } from 'react-i18next';

interface Props {
  open: boolean;
  onClose: () => void;
  onSubmit: (title: string, description: string) => Promise<void>;
  initialTitle?: string;
  initialDescription?: string;
  editMode?: boolean;
}

export const TopicSuggestionForm: React.FC<Props> = ({
  open,
  onClose,
  onSubmit,
  initialTitle = '',
  initialDescription = '',
  editMode = false,
}) => {
  const { t } = useTranslation('partners');
  const [title, setTitle] = useState(initialTitle);
  const [description, setDescription] = useState(initialDescription);
  const [submitting, setSubmitting] = useState(false);
  const [titleError, setTitleError] = useState('');

  React.useEffect(() => {
    if (open) {
      setTitle(initialTitle);
      setDescription(initialDescription);
      setTitleError('');
    }
  }, [open, initialTitle, initialDescription]);

  const handleClose = () => {
    setTitle('');
    setDescription('');
    setTitleError('');
    onClose();
  };

  const handleSubmit = async () => {
    if (title.trim().length < 5) {
      setTitleError(t('portal.topics.form.titleRequired'));
      return;
    }
    setTitleError('');
    setSubmitting(true);
    try {
      await onSubmit(title.trim(), description.trim());
      handleClose();
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="sm">
      <DialogTitle>
        {editMode ? t('portal.topics.form.editDialogTitle') : t('portal.topics.form.dialogTitle')}
      </DialogTitle>
      <DialogContent>
        <TextField
          autoFocus
          required
          fullWidth
          label={t('common:labels.title')}
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          error={!!titleError}
          helperText={titleError || ''}
          inputProps={{ maxLength: 255 }}
          sx={{ mt: 1, mb: 2 }}
          data-testid="topic-form-title"
        />
        <TextField
          fullWidth
          multiline
          rows={3}
          label={t('portal.topics.form.description')}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          helperText={t('portal.topics.form.descriptionHint')}
          inputProps={{ maxLength: 500 }}
          data-testid="topic-form-description"
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={submitting}>
          {t('common:actions.cancel')}
        </Button>
        <Button
          variant="contained"
          onClick={handleSubmit}
          disabled={submitting}
          data-testid="topic-form-submit"
        >
          {editMode ? t('portal.topics.form.update') : t('portal.topics.form.submit')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};
