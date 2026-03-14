/**
 * GlobalImagesTab Component
 *
 * Admin tab for managing global teaser images (event_code IS NULL).
 * Reuses the same hooks as EventSettingsTab with eventCode=null.
 */

import React, { useRef, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  FormControl,
  IconButton,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  Typography,
} from '@mui/material';
import {
  Image as ImageIcon,
  AddPhotoAlternate as AddPhotoAlternateIcon,
  Delete as DeleteIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import {
  useTeaserImages,
  useUploadTeaserImage,
  useDeleteTeaserImage,
  useUpdateTeaserImagePosition,
} from '@/hooks/useEventTeaserImages';
import type { components } from '@/types/generated/events-api.types';

type TeaserImageItem = components['schemas']['TeaserImageItem'];

const MAX_GLOBAL_IMAGES = 10;

export const GlobalImagesTab: React.FC = () => {
  const { t } = useTranslation('admin');

  // null eventCode = global images
  const { data: images = [], isLoading } = useTeaserImages(null);
  const uploadMutation = useUploadTeaserImage(null);
  const deleteMutation = useDeleteTeaserImage(null);
  const updatePositionMutation = useUpdateTeaserImagePosition(null);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [removeError, setRemoveError] = useState<string | null>(null);
  const [positionError, setPositionError] = useState<string | null>(null);

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    e.target.value = '';
    setUploadError(null);
    try {
      await uploadMutation.mutateAsync({
        file,
        request: { contentType: file.type, fileName: file.name },
      });
    } catch (err) {
      setUploadError(
        err instanceof Error
          ? err.message
          : t('globalImages.uploadError', 'Upload failed. Please try again.')
      );
    }
  };

  const handleDelete = async (imageId: string) => {
    setRemoveError(null);
    try {
      await deleteMutation.mutateAsync(imageId);
    } catch (err) {
      setRemoveError(
        err instanceof Error
          ? err.message
          : t('globalImages.removeError', 'Remove failed. Please try again.')
      );
    }
  };

  const handlePositionChange = async (imageId: string, position: string) => {
    setPositionError(null);
    try {
      await updatePositionMutation.mutateAsync({
        imageId,
        request: { presentationPosition: position as TeaserImageItem['presentationPosition'] },
      });
    } catch (err) {
      setPositionError(
        err instanceof Error
          ? err.message
          : t('globalImages.positionError', 'Failed to update slide position. Please try again.')
      );
    }
  };

  if (isLoading) {
    return (
      <Box display="flex" justifyContent="center" py={4}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Paper sx={{ p: 3 }}>
      <Stack direction="row" spacing={1} alignItems="center" mb={2}>
        <ImageIcon color="action" />
        <Typography variant="h6">{t('globalImages.title', 'Global Teaser Images')}</Typography>
        <Chip
          label={`${images.length} / ${MAX_GLOBAL_IMAGES}`}
          size="small"
          color={images.length >= MAX_GLOBAL_IMAGES ? 'error' : 'default'}
          variant="outlined"
        />
      </Stack>
      <Divider sx={{ mb: 2 }} />

      <Typography variant="body2" color="text.secondary" mb={2}>
        {t(
          'globalImages.description',
          'Images shown as full-screen slides on ALL event presentations. Event-specific images can be added per event in the event Settings tab.'
        )}
      </Typography>

      {uploadError && (
        <Alert severity="error" onClose={() => setUploadError(null)} sx={{ mb: 2 }}>
          {uploadError}
        </Alert>
      )}
      {removeError && (
        <Alert severity="error" onClose={() => setRemoveError(null)} sx={{ mb: 2 }}>
          {removeError}
        </Alert>
      )}
      {positionError && (
        <Alert severity="error" onClose={() => setPositionError(null)} sx={{ mb: 2 }}>
          {positionError}
        </Alert>
      )}

      {/* Gallery */}
      {images.length > 0 && (
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))',
            gap: 1.5,
            mb: 2,
          }}
        >
          {[...images]
            .sort((a, b) => a.displayOrder - b.displayOrder)
            .map((img) => (
              <Stack key={img.id} spacing={0.75}>
                <Box
                  sx={{
                    position: 'relative',
                    borderRadius: 1,
                    overflow: 'hidden',
                    aspectRatio: '16/9',
                    bgcolor: 'grey.100',
                  }}
                >
                  <img
                    src={img.imageUrl}
                    alt=""
                    style={{
                      width: '100%',
                      height: '100%',
                      objectFit: 'cover',
                      display: 'block',
                    }}
                  />
                  <IconButton
                    size="small"
                    onClick={() => void handleDelete(img.id)}
                    disabled={deleteMutation.isPending}
                    sx={{
                      position: 'absolute',
                      top: 4,
                      right: 4,
                      bgcolor: 'rgba(0,0,0,0.55)',
                      color: '#fff',
                      '&:hover': { bgcolor: 'rgba(200,0,0,0.75)' },
                    }}
                  >
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                </Box>
                <FormControl size="small" fullWidth>
                  <InputLabel>{t('events:teaserImage.position.label', 'Show after')}</InputLabel>
                  <Select
                    value={img.presentationPosition}
                    label={t('events:teaserImage.position.label', 'Show after')}
                    onChange={(e) => void handlePositionChange(img.id, e.target.value)}
                  >
                    <MenuItem value="AFTER_WELCOME">
                      {t('events:teaserImage.position.afterWelcome', 'Welcome slide')}
                    </MenuItem>
                    <MenuItem value="AFTER_COMMITTEE">
                      {t('events:teaserImage.position.afterCommittee', 'Committee slide')}
                    </MenuItem>
                    <MenuItem value="AFTER_TOPIC_REVEAL">
                      {t('events:teaserImage.position.afterTopicReveal', 'Topic Reveal slide')}
                    </MenuItem>
                    <MenuItem value="AFTER_UPCOMING_EVENTS">
                      {t(
                        'events:teaserImage.position.afterUpcomingEvents',
                        'Upcoming Events slide'
                      )}
                    </MenuItem>
                  </Select>
                </FormControl>
              </Stack>
            ))}
        </Box>
      )}

      {/* Upload button */}
      <input
        ref={fileInputRef}
        type="file"
        accept="image/jpeg,image/png,image/webp,image/svg+xml"
        style={{ display: 'none' }}
        onChange={handleFileChange}
      />
      <Button
        variant="outlined"
        startIcon={
          uploadMutation.isPending ? <CircularProgress size={16} /> : <AddPhotoAlternateIcon />
        }
        disabled={images.length >= MAX_GLOBAL_IMAGES || uploadMutation.isPending}
        onClick={() => fileInputRef.current?.click()}
        data-testid="global-image-upload-btn"
      >
        {uploadMutation.isPending
          ? t('globalImages.uploading', 'Uploading...')
          : t('globalImages.uploadButton', 'Add Global Image')}
      </Button>

      {images.length >= MAX_GLOBAL_IMAGES && (
        <Typography variant="caption" color="error" sx={{ display: 'block', mt: 1 }}>
          {t('globalImages.limitReached', 'Maximum of {{max}} global teaser images reached.', {
            max: MAX_GLOBAL_IMAGES,
          })}
        </Typography>
      )}
    </Paper>
  );
};

export default GlobalImagesTab;
