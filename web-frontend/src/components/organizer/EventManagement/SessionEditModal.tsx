/**
 * SessionEditModal Component
 *
 * Modal for editing session details (title, abstract, duration)
 * Features:
 * - Edit session title (required)
 * - Edit session abstract (optional, max 1000 chars)
 * - Edit session duration in minutes
 * - Form validation
 * - i18n support (German/English)
 */

import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Button,
  Box,
  Typography,
  Alert,
  CircularProgress,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import type { SessionUI } from '@/types/event.types';

interface SessionEditModalProps {
  open: boolean;
  onClose: () => void;
  session: SessionUI | null;
  onSave: (sessionSlug: string, updates: SessionUpdateData) => Promise<void>;
}

export interface SessionUpdateData {
  title: string;
  description?: string;
  durationMinutes?: number;
}

const MAX_ABSTRACT_LENGTH = 1000;

export const SessionEditModal: React.FC<SessionEditModalProps> = ({
  open,
  onClose,
  session,
  onSave,
}) => {
  const { t } = useTranslation('events');

  const [title, setTitle] = useState('');
  const [abstract, setAbstract] = useState('');
  const [duration, setDuration] = useState<number>(60); // Default 60 minutes
  const [errors, setErrors] = useState<{
    title?: string;
    abstract?: string;
    duration?: string;
  }>({});
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  // Initialize form when session changes
  useEffect(() => {
    if (session) {
      setTitle(session.title || '');
      setAbstract(session.description || '');

      // Calculate duration from startTime and endTime if available
      if (session.startTime && session.endTime) {
        const start = new Date(session.startTime);
        const end = new Date(session.endTime);
        const durationMs = end.getTime() - start.getTime();
        const durationMin = Math.round(durationMs / (1000 * 60));
        setDuration(durationMin || 60);
      } else {
        setDuration(60);
      }

      setErrors({});
      setSaveError(null);
    }
  }, [session]);

  const validateForm = (): boolean => {
    const newErrors: typeof errors = {};

    if (!title.trim()) {
      newErrors.title = t('sessionEdit.errors.titleRequired', 'Title is required');
    }

    if (abstract.length > MAX_ABSTRACT_LENGTH) {
      newErrors.abstract = t('sessionEdit.errors.abstractTooLong', {
        max: MAX_ABSTRACT_LENGTH,
        defaultValue: `Abstract must be ${MAX_ABSTRACT_LENGTH} characters or less`,
      });
    }

    if (duration <= 0 || duration > 480) {
      // Max 8 hours
      newErrors.duration = t('sessionEdit.errors.invalidDuration', {
        defaultValue: 'Duration must be between 1 and 480 minutes',
      });
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSave = async () => {
    if (!session || !validateForm()) {
      return;
    }

    setSaving(true);
    setSaveError(null);

    try {
      await onSave(session.sessionSlug, {
        title: title.trim(),
        description: abstract.trim() || undefined,
        durationMinutes: duration,
      });
      onClose();
    } catch (error) {
      setSaveError(
        error instanceof Error
          ? error.message
          : t('sessionEdit.errors.saveFailed', 'Failed to save session')
      );
    } finally {
      setSaving(false);
    }
  };

  const handleClose = () => {
    if (!saving) {
      onClose();
    }
  };

  if (!session) {
    return null;
  }

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle>
        {t('sessionEdit.title', 'Edit Session')} -{' '}
        {t('speakers.slotLabel', { number: session.slotNumber || 0 })}
      </DialogTitle>

      <DialogContent>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
          {/* Session Title */}
          <TextField
            label={t('sessionEdit.titleLabel', 'Session Title')}
            value={title}
            onChange={(e) => {
              setTitle(e.target.value);
              if (errors.title) {
                setErrors({ ...errors, title: undefined });
              }
            }}
            error={!!errors.title}
            helperText={errors.title}
            required
            fullWidth
            autoFocus
            disabled={saving}
          />

          {/* Session Abstract */}
          <Box>
            <TextField
              label={t('sessionEdit.abstractLabel', 'Session Abstract')}
              value={abstract}
              onChange={(e) => {
                setAbstract(e.target.value);
                if (errors.abstract) {
                  setErrors({ ...errors, abstract: undefined });
                }
              }}
              error={!!errors.abstract}
              helperText={
                errors.abstract ||
                t('sessionEdit.charactersRemaining', {
                  count: MAX_ABSTRACT_LENGTH - abstract.length,
                  defaultValue: `{{count}} characters remaining`,
                })
              }
              multiline
              rows={6}
              fullWidth
              disabled={saving}
            />
            <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5 }}>
              {abstract.length} / {MAX_ABSTRACT_LENGTH}{' '}
              {t('qualityReview.characters', 'characters')}
            </Typography>
          </Box>

          {/* Session Duration */}
          <TextField
            label={t('sessionEdit.durationLabel', 'Duration (minutes)')}
            type="number"
            value={duration}
            onChange={(e) => {
              setDuration(Number(e.target.value));
              if (errors.duration) {
                setErrors({ ...errors, duration: undefined });
              }
            }}
            error={!!errors.duration}
            helperText={errors.duration}
            inputProps={{ min: 1, max: 480, step: 5 }}
            fullWidth
            disabled={saving}
          />

          {/* Save Error */}
          {saveError && (
            <Alert severity="error" onClose={() => setSaveError(null)}>
              {saveError}
            </Alert>
          )}
        </Box>
      </DialogContent>

      <DialogActions>
        <Button onClick={handleClose} disabled={saving}>
          {t('common.cancel', 'Cancel')}
        </Button>
        <Button
          onClick={handleSave}
          variant="contained"
          disabled={saving}
          startIcon={saving ? <CircularProgress size={20} /> : undefined}
        >
          {saving ? t('common.saving', 'Saving...') : t('common.save', 'Save')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};
