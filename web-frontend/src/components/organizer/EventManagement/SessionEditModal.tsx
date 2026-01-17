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
  Tabs,
  Tab,
  List,
  ListItem,
  ListItemText,
  ListItemSecondaryAction,
  IconButton,
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import { useTranslation } from 'react-i18next';
import { AxiosError } from 'axios';
import type { SessionUI, SessionMaterial } from '@/types/event.types';
import { FileUpload, type UploadedFile } from '@/components/shared/FileUpload/FileUpload';

interface SessionEditModalProps {
  open: boolean;
  onClose: () => void;
  session: SessionUI | null;
  eventDate: string; // ISO 8601 date for time conversion
  onSave: (sessionSlug: string, updates: SessionUpdateData) => Promise<void>;
}

export interface SessionUpdateData {
  title: string;
  description?: string;
  durationMinutes?: number;
  startTime?: string; // ISO 8601 timestamp
  endTime?: string; // ISO 8601 timestamp
}

interface SpeakerConflict {
  speakerName: string;
  sessionTitle: string;
  startTime: string;
  endTime: string;
}

interface RoomConflict {
  roomName: string;
  sessionTitle: string;
  startTime: string;
  endTime: string;
}

const MAX_ABSTRACT_LENGTH = 1000;
const MIN_SESSION_DURATION = 15; // minutes
const MAX_SESSION_DURATION = 480; // 8 hours

// Extract HH:mm from ISO 8601 timestamp
const extractTimeFromISO = (isoString: string | null | undefined): string => {
  if (!isoString) return '';
  try {
    const date = new Date(isoString);
    if (isNaN(date.getTime())) return '';
    return date.toTimeString().substring(0, 5); // "14:30"
  } catch {
    return '';
  }
};

// Combine event date + HH:mm time → ISO 8601 timestamp
const combineEventDateAndTime = (eventDate: string, timeString: string): string => {
  const date = new Date(eventDate);
  const [hours, minutes] = timeString.split(':').map(Number);
  date.setHours(hours, minutes, 0, 0);
  return date.toISOString();
};

export const SessionEditModal: React.FC<SessionEditModalProps> = ({
  open,
  onClose,
  session,
  eventDate,
  onSave,
}) => {
  const { t } = useTranslation('events');

  const [title, setTitle] = useState('');
  const [abstract, setAbstract] = useState('');
  const [duration, setDuration] = useState<number>(60); // Default 60 minutes
  const [startTime, setStartTime] = useState<string>(''); // HH:mm format
  const [endTime, setEndTime] = useState<string>(''); // HH:mm format
  const [errors, setErrors] = useState<{
    title?: string;
    abstract?: string;
    duration?: string;
    startTime?: string;
    endTime?: string;
  }>({});
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  // Story 5.9: Materials tab state
  const [activeTab, setActiveTab] = useState<number>(0); // 0 = Details, 1 = Materials
  const [uploadedMaterials, setUploadedMaterials] = useState<UploadedFile[]>([]);
  const [existingMaterials, setExistingMaterials] = useState<SessionMaterial[]>([]);

  // Initialize form when session changes
  useEffect(() => {
    if (session) {
      setTitle(session.title || '');
      setAbstract(session.description || '');

      // Extract times from session
      const extractedStartTime = extractTimeFromISO(session.startTime);
      const extractedEndTime = extractTimeFromISO(session.endTime);
      setStartTime(extractedStartTime);
      setEndTime(extractedEndTime);

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

      // Story 5.9: Initialize materials from session
      setExistingMaterials(session.materials || []);
      setUploadedMaterials([]);
      setActiveTab(0); // Reset to Details tab
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

    if (duration < MIN_SESSION_DURATION || duration > MAX_SESSION_DURATION) {
      newErrors.duration = t('sessionEdit.errors.invalidDuration', {
        min: MIN_SESSION_DURATION,
        max: MAX_SESSION_DURATION,
        defaultValue: `Duration must be between ${MIN_SESSION_DURATION} and ${MAX_SESSION_DURATION} minutes`,
      });
    }

    // Time validation (if times are provided)
    if (startTime && endTime) {
      const [startHours, startMinutes] = startTime.split(':').map(Number);
      const [endHours, endMinutes] = endTime.split(':').map(Number);
      const startTotalMinutes = startHours * 60 + startMinutes;
      const endTotalMinutes = endHours * 60 + endMinutes;

      if (endTotalMinutes <= startTotalMinutes) {
        newErrors.endTime = t('sessionEdit.errors.endTimeBeforeStart', {
          defaultValue: 'End time must be after start time',
        });
      }

      const calculatedDuration = endTotalMinutes - startTotalMinutes;
      if (calculatedDuration < MIN_SESSION_DURATION) {
        newErrors.duration = t('sessionEdit.errors.durationTooShort', {
          min: MIN_SESSION_DURATION,
          defaultValue: `Session duration must be at least ${MIN_SESSION_DURATION} minutes`,
        });
      }

      if (calculatedDuration > MAX_SESSION_DURATION) {
        newErrors.duration = t('sessionEdit.errors.durationTooLong', {
          max: MAX_SESSION_DURATION,
          defaultValue: `Session duration must be at most ${MAX_SESSION_DURATION} minutes`,
        });
      }
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
      const updates: SessionUpdateData = {
        title: title.trim(),
        description: abstract.trim() || undefined,
        durationMinutes: duration,
      };

      // Include time updates if times are set
      if (startTime && endTime) {
        updates.startTime = combineEventDateAndTime(eventDate, startTime);
        updates.endTime = combineEventDateAndTime(eventDate, endTime);
      }

      await onSave(session.sessionSlug, updates);
      onClose();
    } catch (error) {
      // Handle 409 conflict errors (speaker/room conflicts)
      if (error instanceof AxiosError && error.response?.status === 409) {
        const conflictData = error.response.data;
        const conflictMessages: string[] = [];

        if (conflictData.speakerConflicts && conflictData.speakerConflicts.length > 0) {
          conflictData.speakerConflicts.forEach((conflict: SpeakerConflict) => {
            conflictMessages.push(
              t('sessionEdit.errors.speakerConflict', {
                speaker: conflict.speakerName,
                session: conflict.sessionTitle,
                time: `${conflict.startTime} - ${conflict.endTime}`,
                defaultValue: `Speaker ${conflict.speakerName} is already scheduled for "${conflict.sessionTitle}" at ${conflict.startTime} - ${conflict.endTime}`,
              })
            );
          });
        }

        if (conflictData.roomConflicts && conflictData.roomConflicts.length > 0) {
          conflictData.roomConflicts.forEach((conflict: RoomConflict) => {
            conflictMessages.push(
              t('sessionEdit.errors.roomConflict', {
                room: conflict.roomName,
                session: conflict.sessionTitle,
                time: `${conflict.startTime} - ${conflict.endTime}`,
                defaultValue: `Room ${conflict.roomName} is already booked for "${conflict.sessionTitle}" at ${conflict.startTime} - ${conflict.endTime}`,
              })
            );
          });
        }

        setSaveError(
          conflictMessages.length > 0
            ? conflictMessages.join('\n')
            : t('sessionEdit.errors.timingConflict', 'Timing conflict detected')
        );
      } else {
        setSaveError(
          error instanceof Error
            ? error.message
            : t('sessionEdit.errors.saveFailed', 'Failed to save session')
        );
      }
    } finally {
      setSaving(false);
    }
  };

  const handleClose = () => {
    if (!saving) {
      onClose();
    }
  };

  // Story 5.9: Materials tab handlers
  const handleFileUploadSuccess = (uploadedFile: UploadedFile) => {
    setUploadedMaterials((prev) => [...prev, uploadedFile]);
  };

  const handleRemoveMaterial = (uploadId: string) => {
    setUploadedMaterials((prev) => prev.filter((file) => file.uploadId !== uploadId));
  };

  const handleTabChange = (_event: React.SyntheticEvent, newValue: number) => {
    setActiveTab(newValue);
  };

  // Auto-calculation handlers per AC2
  const handleStartTimeChange = (newStartTime: string) => {
    setStartTime(newStartTime);
    if (errors.startTime) {
      setErrors({ ...errors, startTime: undefined });
    }

    // Auto-recalculate endTime based on current duration
    if (newStartTime && duration > 0) {
      const [hours, minutes] = newStartTime.split(':').map(Number);
      const startTotalMinutes = hours * 60 + minutes;
      const endTotalMinutes = startTotalMinutes + duration;
      const endHours = Math.floor(endTotalMinutes / 60) % 24;
      const endMinutes = endTotalMinutes % 60;
      const calculatedEndTime = `${String(endHours).padStart(2, '0')}:${String(endMinutes).padStart(2, '0')}`;
      setEndTime(calculatedEndTime);
    }
  };

  const handleDurationChange = (newDuration: number) => {
    setDuration(newDuration);
    if (errors.duration) {
      setErrors({ ...errors, duration: undefined });
    }

    // Auto-recalculate endTime based on current startTime
    if (startTime && newDuration > 0) {
      const [hours, minutes] = startTime.split(':').map(Number);
      const startTotalMinutes = hours * 60 + minutes;
      const endTotalMinutes = startTotalMinutes + newDuration;
      const endHours = Math.floor(endTotalMinutes / 60) % 24;
      const endMinutes = endTotalMinutes % 60;
      const calculatedEndTime = `${String(endHours).padStart(2, '0')}:${String(endMinutes).padStart(2, '0')}`;
      setEndTime(calculatedEndTime);
    }
  };

  const handleEndTimeChange = (newEndTime: string) => {
    setEndTime(newEndTime);
    if (errors.endTime) {
      setErrors({ ...errors, endTime: undefined });
    }
    // Note: Duration does NOT auto-update when endTime is manually changed (per AC2)
  };

  if (!session) {
    return null;
  }

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth>
      <DialogTitle>{t('sessionEdit.title', 'Edit Session')}</DialogTitle>

      {/* Story 5.9: Tabs for Details and Materials */}
      <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Tabs value={activeTab} onChange={handleTabChange} aria-label="Session edit tabs">
          <Tab label={t('sessionEdit.tabs.details', 'Details')} />
          <Tab label={t('sessionEdit.tabs.materials', 'Materials')} />
        </Tabs>
      </Box>

      <DialogContent>
        {/* Details Tab */}
        {activeTab === 0 && (
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
              onChange={(e) => handleDurationChange(Number(e.target.value))}
              error={!!errors.duration}
              helperText={errors.duration}
              inputProps={{ step: 5 }}
              fullWidth
              disabled={saving}
            />

            {/* Start Time */}
            <TextField
              label={t('sessionEdit.startTimeLabel', 'Start Time')}
              type="time"
              value={startTime}
              onChange={(e) => handleStartTimeChange(e.target.value)}
              error={!!errors.startTime}
              helperText={errors.startTime}
              fullWidth
              disabled={saving}
              InputLabelProps={{
                shrink: true,
              }}
            />

            {/* End Time */}
            <TextField
              label={t('sessionEdit.endTimeLabel', 'End Time')}
              type="time"
              value={endTime}
              onChange={(e) => handleEndTimeChange(e.target.value)}
              error={!!errors.endTime}
              helperText={errors.endTime}
              fullWidth
              disabled={saving}
              InputLabelProps={{
                shrink: true,
              }}
            />

            {/* Save Error */}
            {saveError && (
              <Alert severity="error" onClose={() => setSaveError(null)}>
                {saveError}
              </Alert>
            )}
          </Box>
        )}

        {/* Materials Tab (Story 5.9) */}
        {activeTab === 1 && (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
            {/* File Upload Component */}
            <Box>
              <Typography variant="subtitle1" gutterBottom>
                {t('sessionEdit.materials.uploadTitle', 'Upload Session Materials')}
              </Typography>
              <Typography variant="body2" color="text.secondary" gutterBottom>
                {t(
                  'sessionEdit.materials.uploadDescription',
                  'Upload presentations, documents, or videos (max 100MB per file, up to 10 files)'
                )}
              </Typography>
              <FileUpload
                onUploadSuccess={handleFileUploadSuccess}
                allowedTypes={['presentation', 'document', 'video', 'archive']}
                maxFileSize={100 * 1024 * 1024} // 100MB
                multiple={true}
                maxFiles={10}
                uploadedFiles={uploadedMaterials}
              />
            </Box>

            {/* Uploaded Materials List */}
            {uploadedMaterials.length > 0 && (
              <Box>
                <Typography variant="subtitle2" gutterBottom>
                  {t('sessionEdit.materials.uploadedTitle', 'Newly Uploaded Materials')}
                </Typography>
                <List dense>
                  {uploadedMaterials.map((file) => (
                    <ListItem key={file.uploadId}>
                      <ListItemText
                        primary={file.fileName}
                        secondary={`${(file.fileSize / 1024 / 1024).toFixed(2)} MB • ${file.fileType}`}
                      />
                      <ListItemSecondaryAction>
                        <IconButton
                          edge="end"
                          aria-label="delete"
                          onClick={() => handleRemoveMaterial(file.uploadId)}
                          size="small"
                        >
                          <DeleteIcon />
                        </IconButton>
                      </ListItemSecondaryAction>
                    </ListItem>
                  ))}
                </List>
              </Box>
            )}

            {/* Existing Materials List */}
            {existingMaterials.length > 0 && (
              <Box>
                <Typography variant="subtitle2" gutterBottom>
                  {t('sessionEdit.materials.existingTitle', 'Existing Materials')}
                </Typography>
                <List dense>
                  {existingMaterials.map((material) => (
                    <ListItem key={material.id}>
                      <ListItemText
                        primary={material.fileName}
                        secondary={`${(material.fileSize / 1024 / 1024).toFixed(2)} MB • ${material.materialType}`}
                      />
                    </ListItem>
                  ))}
                </List>
              </Box>
            )}
          </Box>
        )}
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
