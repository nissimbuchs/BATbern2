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
import { useQueryClient } from '@tanstack/react-query';
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
  ListItemIcon,
  Link,
} from '@mui/material';
import DownloadIcon from '@mui/icons-material/Download';
import {
  Description as DescriptionIcon,
  PictureAsPdf as PdfIcon,
  VideoLibrary as VideoIcon,
  Slideshow as PresentationIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { AxiosError } from 'axios';
import type { SessionUI, SessionMaterial } from '@/types/event.types';
import { FileUpload, type UploadedFile } from '@/components/shared/FileUpload/FileUpload';
import { sessionApiClient } from '@/services/api/sessionApiClient';
import { SessionSpeakersTab } from './SessionSpeakersTab';

// Helper function to get file type icon based on mime type
const getFileTypeIcon = (mimeType: string | undefined): React.ReactNode => {
  if (!mimeType) {
    return <DescriptionIcon />;
  }
  if (
    mimeType.startsWith('application/vnd.openxmlformats-officedocument.presentationml') ||
    mimeType.startsWith('application/vnd.ms-powerpoint')
  ) {
    return <PresentationIcon />;
  }
  if (mimeType === 'application/pdf') {
    return <PdfIcon />;
  }
  if (mimeType.startsWith('video/')) {
    return <VideoIcon />;
  }
  return <DescriptionIcon />;
};

interface SessionEditModalProps {
  open: boolean;
  onClose: () => void;
  session: SessionUI | null;
  eventDate: string; // ISO 8601 date for time conversion
  onSave: (sessionSlug: string, updates: SessionUpdateData) => Promise<void>;
  initialTab?: number; // 0 = Details (default), 1 = Materials, 2 = Speakers
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

// Helper: Map MIME type to material type enum
const getMaterialTypeFromMimeType = (mimeType: string): string => {
  if (mimeType.startsWith('video/')) {
    return 'VIDEO';
  } else if (
    mimeType.includes('powerpoint') ||
    mimeType.includes('presentation') ||
    mimeType.includes('keynote')
  ) {
    return 'PRESENTATION';
  } else if (
    mimeType === 'application/pdf' ||
    mimeType.includes('document') ||
    mimeType === 'text/plain'
  ) {
    return 'DOCUMENT';
  } else if (mimeType.includes('zip') || mimeType.includes('gzip') || mimeType.includes('tar')) {
    return 'ARCHIVE';
  } else {
    return 'OTHER';
  }
};

// Helper: Extract file extension from filename (without dot)
const getFileExtension = (fileName: string): string => {
  const lastDot = fileName.lastIndexOf('.');
  if (lastDot === -1) return '';
  return fileName.substring(lastDot + 1).toLowerCase();
};

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
  initialTab = 0,
}) => {
  const { t } = useTranslation('events');
  const queryClient = useQueryClient();

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
  const [activeTab, setActiveTab] = useState<number>(initialTab); // 0 = Details, 1 = Materials
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
      setActiveTab(initialTab); // Use initialTab prop (AC2: Materials button opens Materials tab)
    }
  }, [session, initialTab]);

  const validateForm = (): boolean => {
    const newErrors: typeof errors = {};

    const isStructural = ['moderation', 'break', 'lunch'].includes(session?.sessionType ?? '');
    const minDuration = isStructural ? 1 : MIN_SESSION_DURATION;

    if (!title.trim()) {
      newErrors.title = t('sessionEdit.errors.titleRequired', 'Title is required');
    }

    if (abstract.length > MAX_ABSTRACT_LENGTH) {
      newErrors.abstract = t('sessionEdit.errors.abstractTooLong', {
        max: MAX_ABSTRACT_LENGTH,
        defaultValue: `Abstract must be ${MAX_ABSTRACT_LENGTH} characters or less`,
      });
    }

    if (duration < minDuration || duration > MAX_SESSION_DURATION) {
      newErrors.duration = t('sessionEdit.errors.invalidDuration', {
        min: minDuration,
        max: MAX_SESSION_DURATION,
        defaultValue: `Duration must be between ${minDuration} and ${MAX_SESSION_DURATION} minutes`,
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
      if (calculatedDuration < minDuration) {
        newErrors.duration = t('sessionEdit.errors.durationTooShort', {
          min: minDuration,
          defaultValue: `Session duration must be at least ${minDuration} minutes`,
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

      // Story 5.9: Save uploaded materials if any
      if (uploadedMaterials.length > 0) {
        const materials = uploadedMaterials.map((file) => ({
          uploadId: file.uploadId,
          materialType: getMaterialTypeFromMimeType(file.fileType),
          fileName: file.fileName,
          fileExtension: getFileExtension(file.fileName),
          fileSize: file.fileSize,
          mimeType: file.fileType,
        }));

        await sessionApiClient.associateMaterials(session.eventCode, session.sessionSlug, {
          materials,
        });

        // Invalidate event cache to reload sessions with new materials
        queryClient.invalidateQueries({ queryKey: ['event', session.eventCode] });
      }

      // Clear uploaded materials after successful save
      setUploadedMaterials([]);

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
  const handleFileUploadSuccess = (
    data: UploadedFile | { uploadId: string; tempFileUrl?: string }
  ) => {
    // Handle both UploadedFile (multiple mode) and basic data (single mode)
    const uploadedFile: UploadedFile =
      'fileName' in data
        ? data
        : {
            uploadId: data.uploadId,
            fileName: 'Unknown', // Fallback for single mode (shouldn't happen in materials upload)
            fileSize: 0,
            fileType: 'application/octet-stream',
          };
    setUploadedMaterials((prev) => [...prev, uploadedFile]);
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
          <Tab label={t('sessionEdit.tabs.speakers', 'Speakers')} />
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

        {/* Speakers Tab */}
        {activeTab === 2 && <SessionSpeakersTab session={session} />}

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
                allowedTypes={[
                  // Presentations (AC6 - Story 5.9)
                  'application/vnd.ms-powerpoint', // .ppt
                  'application/vnd.openxmlformats-officedocument.presentationml.presentation', // .pptx
                  'application/vnd.apple.keynote', // .key
                  'application/vnd.oasis.opendocument.presentation', // .odp
                  // Documents (AC6 - Story 5.9)
                  'application/pdf', // .pdf
                  'application/msword', // .doc
                  'application/vnd.openxmlformats-officedocument.wordprocessingml.document', // .docx
                  'text/plain', // .txt
                  // Videos (AC6 - Story 5.9)
                  'video/mp4', // .mp4
                  'video/quicktime', // .mov
                  'video/x-msvideo', // .avi
                  'video/x-matroska', // .mkv
                  'video/webm', // .webm
                  // Archives (AC6 - Story 5.9)
                  'application/zip', // .zip
                  'application/gzip', // .tar.gz
                  'application/x-gzip', // .tar.gz
                  'application/x-tar', // .tar
                ]}
                maxFileSize={100 * 1024 * 1024} // 100MB
                multiple={true}
                maxFiles={10}
                uploadedFiles={uploadedMaterials}
                uploadEndpoint="/materials/presigned-url" // Story 5.9: Use materials endpoint
              />
            </Box>

            {/* Existing Materials List (Story 5.9: Only show after save & reopen) */}
            {existingMaterials.length > 0 && (
              <Box>
                <Typography variant="subtitle2" gutterBottom>
                  {t('sessionEdit.materials.existingTitle', 'Existing Materials')}
                </Typography>
                <List dense>
                  {existingMaterials.map((material) => (
                    <ListItem key={material.id}>
                      <ListItemIcon>{getFileTypeIcon(material.mimeType)}</ListItemIcon>
                      <ListItemText
                        primary={
                          <Link
                            component="button"
                            onClick={async (e) => {
                              e.preventDefault();
                              try {
                                // Fetch presigned download URL
                                const response = await sessionApiClient.getMaterialDownloadUrl(
                                  session!.eventCode,
                                  session!.sessionSlug,
                                  material.id
                                );
                                // Open in new tab
                                window.open(response.downloadUrl, '_blank', 'noopener,noreferrer');
                              } catch (error) {
                                console.error('Failed to get download URL:', error);
                                alert(
                                  t(
                                    'sessionEdit.materials.downloadError',
                                    'Failed to download material'
                                  )
                                );
                              }
                            }}
                            underline="hover"
                            sx={{
                              display: 'flex',
                              alignItems: 'center',
                              gap: 0.5,
                              cursor: 'pointer',
                            }}
                          >
                            {material.fileName}
                            <DownloadIcon fontSize="small" />
                          </Link>
                        }
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
