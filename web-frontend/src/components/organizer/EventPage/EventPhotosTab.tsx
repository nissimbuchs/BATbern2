/**
 * EventPhotosTab Component
 * Story 10.21: Event Photos Gallery — AC8
 *
 * Photo grid for organizers: upload, view, and delete event photos.
 * Uses 3-phase presigned PUT upload pattern.
 * Drag-to-reorder is explicitly OUT OF SCOPE (AC12).
 */

import React, { useRef, useState } from 'react';
import {
  Box,
  Button,
  Card,
  CardContent,
  CardMedia,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  Typography,
  Alert,
} from '@mui/material';
import Grid from '@mui/material/Grid';
import {
  Delete as DeleteIcon,
  PhotoLibrary as PhotoLibraryIcon,
  CloudUpload as CloudUploadIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useDeleteEventPhoto, useEventPhotos, useUploadEventPhoto } from '@/hooks/useEventPhotos';

interface EventPhotosTabProps {
  eventCode: string;
}

export const EventPhotosTab: React.FC<EventPhotosTabProps> = ({ eventCode }) => {
  const { t } = useTranslation('events');
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [deletePhotoId, setDeletePhotoId] = useState<string | null>(null);
  const [uploadError, setUploadError] = useState<string | null>(null);

  const { data: photos, isLoading } = useEventPhotos(eventCode);
  const uploadMutation = useUploadEventPhoto(eventCode);
  const deleteMutation = useDeleteEventPhoto(eventCode);

  const handleUploadClick = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setUploadError(null);
    try {
      await uploadMutation.mutateAsync({
        file,
        request: {
          filename: file.name,
          contentType: file.type as 'image/jpeg' | 'image/png' | 'image/webp',
          fileSize: file.size,
        },
      });
    } catch {
      setUploadError(t('photos.uploadError', 'Upload failed. Please try again.'));
    } finally {
      // Reset file input so same file can be re-uploaded if needed
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const handleDeleteConfirm = async () => {
    if (!deletePhotoId) return;
    try {
      await deleteMutation.mutateAsync(deletePhotoId);
    } finally {
      setDeletePhotoId(null);
    }
  };

  return (
    <Box>
      {/* Header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <PhotoLibraryIcon color="primary" />
          <Typography variant="h6">{t('photos.title', 'Event Photos')}</Typography>
        </Box>
        <Button
          variant="contained"
          startIcon={<CloudUploadIcon />}
          onClick={handleUploadClick}
          disabled={uploadMutation.isPending}
        >
          {uploadMutation.isPending
            ? t('photos.uploading', 'Uploading...')
            : t('photos.uploadButton', 'Upload Photo')}
        </Button>
        <input
          ref={fileInputRef}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          style={{ display: 'none' }}
          onChange={handleFileChange}
        />
      </Box>

      {/* Upload error */}
      {uploadError && (
        <Alert severity="error" onClose={() => setUploadError(null)} sx={{ mb: 2 }}>
          {uploadError}
        </Alert>
      )}

      {/* Loading */}
      {isLoading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress />
        </Box>
      )}

      {/* Empty state */}
      {!isLoading && (!photos || photos.length === 0) && (
        <Box sx={{ textAlign: 'center', py: 6, color: 'text.secondary' }}>
          <PhotoLibraryIcon sx={{ fontSize: 64, mb: 2, opacity: 0.3 }} />
          <Typography>{t('photos.emptyState', 'No photos yet. Upload the first one!')}</Typography>
        </Box>
      )}

      {/* Photo grid */}
      {!isLoading && photos && photos.length > 0 && (
        <Grid container spacing={2}>
          {photos.map((photo) => (
            <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }} key={photo.id}>
              <Card sx={{ position: 'relative' }}>
                <CardMedia
                  component="img"
                  height="180"
                  image={photo.displayUrl}
                  alt={photo.filename || 'BATbern event photo'}
                  sx={{ objectFit: 'cover' }}
                />
                <CardContent sx={{ p: 1, '&:last-child': { pb: 1 } }}>
                  <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
                    <IconButton
                      size="small"
                      color="error"
                      onClick={() => setDeletePhotoId(photo.id ?? null)}
                      aria-label={t('photos.deleteConfirmTitle', 'Delete Photo')}
                    >
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Box>
                </CardContent>
              </Card>
            </Grid>
          ))}
        </Grid>
      )}

      {/* Delete confirmation dialog */}
      <Dialog open={!!deletePhotoId} onClose={() => setDeletePhotoId(null)}>
        <DialogTitle>{t('photos.deleteConfirmTitle', 'Delete Photo')}</DialogTitle>
        <DialogContent>
          {t(
            'photos.deleteConfirmMessage',
            'Are you sure you want to delete this photo? This cannot be undone.'
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeletePhotoId(null)}>
            {t('photos.deleteCancel', 'Cancel')}
          </Button>
          <Button
            color="error"
            variant="contained"
            onClick={handleDeleteConfirm}
            disabled={deleteMutation.isPending}
          >
            {t('photos.deleteConfirm', 'Delete')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};
