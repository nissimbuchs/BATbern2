/**
 * FileUpload Component
 * Story 1.16.3: Generic File Upload Service
 * ADR-002: Generic File Upload Service Architecture
 *
 * Reusable file upload component with drag-and-drop support
 * Uses generic /api/v1/logos endpoints - works for any entity type
 *
 * Features:
 * - Drag-and-drop file upload
 * - File type and size validation
 * - Upload progress indicator
 * - Preview uploaded file
 * - Remove uploaded file
 * - Error handling with user-friendly messages
 *
 * Usage:
 * <FileUpload
 *   currentFileUrl={logoUrl}
 *   onUploadSuccess={(data) => setLogoUploadId(data.uploadId)}
 *   onUploadError={(error) => console.error(error)}
 * />
 */

import React, { useState, useCallback, useEffect } from 'react';
import { useDropzone } from 'react-dropzone';
import {
  Box,
  Typography,
  IconButton,
  LinearProgress,
  Alert,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
} from '@mui/material';
import {
  CloudUpload as CloudUploadIcon,
  Delete as DeleteIcon,
  Description as DescriptionIcon,
  PictureAsPdf as PdfIcon,
  VideoLibrary as VideoIcon,
  Slideshow as PresentationIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useFileUpload } from '@/hooks/useFileUpload/useFileUpload';

export interface UploadedFile {
  uploadId: string;
  fileName: string;
  fileSize: number;
  fileType: string;
}

interface FileUploadProps {
  currentFileUrl?: string;
  onUploadSuccess?: (data: {
    uploadId: string;
    tempFileUrl?: string;
    fileName?: string;
    fileSize?: number;
    fileType?: string;
  }) => void;
  onUploadError?: (error: { type: string; message: string }) => void;
  onFileRemove?: (uploadId?: string) => void; // Callback when file is removed (Story 2.5.3a, 5.9)
  maxFileSize?: number; // in bytes, default 5MB
  allowedTypes?: string[]; // MIME types
  altText?: string; // Alt text for the uploaded image
  removeButtonLabel?: string; // Aria label for the remove button
  // Story 5.9: Multiple files support
  multiple?: boolean; // Enable multiple file uploads
  maxFiles?: number; // Maximum number of files (default 10)
  uploadedFiles?: UploadedFile[]; // List of uploaded files (for multiple mode)
  uploadEndpoint?: string; // API endpoint for presigned URL (default: '/logos/presigned-url')
}

// Helper function to format file size
const formatFileSize = (bytes: number): string => {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

// Helper function to get file type icon
const getFileTypeIcon = (mimeType: string): React.ReactNode => {
  if (
    mimeType.startsWith('application/vnd.openxmlformats-officedocument.presentationml') ||
    mimeType.startsWith('application/vnd.ms-powerpoint')
  ) {
    return <PresentationIcon data-testid="file-icon-presentation" />;
  }
  if (mimeType === 'application/pdf') {
    return <PdfIcon data-testid="file-icon-document" />;
  }
  if (mimeType.startsWith('video/')) {
    return <VideoIcon data-testid="file-icon-video" />;
  }
  return <DescriptionIcon data-testid="file-icon-document" />;
};

export const FileUpload: React.FC<FileUploadProps> = ({
  currentFileUrl,
  onUploadSuccess,
  onUploadError,
  onFileRemove,
  maxFileSize = 5 * 1024 * 1024, // 5MB
  allowedTypes = ['image/png', 'image/jpeg', 'image/svg+xml'],
  altText,
  removeButtonLabel,
  multiple = false,
  maxFiles = 10,
  uploadedFiles = [],
  uploadEndpoint = '/logos/presigned-url', // Default to logos for backward compatibility
}) => {
  const { t } = useTranslation('common');
  const [fileUrl, setFileUrl] = useState<string | undefined>(currentFileUrl);
  const [, setUploadId] = useState<string | undefined>();

  // Use i18n translations with fallback to props or default values
  const translatedAltText = altText || t('fileUpload.altText');
  const translatedRemoveButtonLabel = removeButtonLabel || t('fileUpload.removeFile');

  const { uploadFile, isUploading, uploadProgress, error, reset } = useFileUpload({
    maxFileSize,
    allowedTypes,
    onUploadSuccess: (data) => {
      setFileUrl(data.tempFileUrl);
      setUploadId(data.uploadId);
      // Only call parent's onUploadSuccess for single file mode
      // Multiple file mode handles this in onDrop with full metadata
      if (!multiple) {
        onUploadSuccess?.(data);
      }
    },
    onUploadError,
    uploadEndpoint, // Pass through the endpoint
  });

  // Sync fileUrl with currentFileUrl prop
  useEffect(() => {
    setFileUrl(currentFileUrl);
  }, [currentFileUrl]);

  const onDrop = useCallback(
    async (acceptedFiles: File[]) => {
      // Reset error state
      reset();

      // Story 5.9: Handle multiple files mode
      if (multiple) {
        // Check if exceeding maxFiles limit
        const totalFiles = uploadedFiles.length + acceptedFiles.length;
        if (totalFiles > maxFiles) {
          onUploadError?.({
            type: 'TOO_MANY_FILES',
            message: `You can only upload a maximum of ${maxFiles} files. Current: ${uploadedFiles.length}, Attempting to add: ${acceptedFiles.length}`,
          });
          return;
        }

        // Upload all accepted files sequentially to capture metadata
        for (const file of acceptedFiles) {
          const uploadId = await uploadFile(file);
          if (uploadId && onUploadSuccess) {
            onUploadSuccess({
              uploadId,
              tempFileUrl: undefined,
              fileName: file.name,
              fileSize: file.size,
              fileType: file.type,
            });
          }
        }
      } else {
        // Original behavior: single file only
        if (acceptedFiles.length > 1) {
          onUploadError?.({
            type: 'MULTIPLE_FILES',
            message: t('fileUpload.errors.multipleFiles'),
          });
          return;
        }

        const file = acceptedFiles[0];
        if (file) {
          await uploadFile(file);
        }
      }
    },
    [uploadFile, reset, onUploadError, multiple, maxFiles, uploadedFiles.length, t]
  );

  // Build accept object dynamically from allowed types
  const buildAcceptObject = (types: string[]) => {
    const acceptObj: Record<string, string[]> = {};
    types.forEach((type) => {
      // Map MIME types to extensions
      if (type === 'image/png') acceptObj[type] = ['.png'];
      else if (type === 'image/jpeg') acceptObj[type] = ['.jpg', '.jpeg'];
      else if (type === 'image/svg+xml') acceptObj[type] = ['.svg'];
      else if (type === 'application/pdf') acceptObj[type] = ['.pdf'];
      else if (type === 'application/vnd.openxmlformats-officedocument.presentationml.presentation')
        acceptObj[type] = ['.pptx'];
      else if (type === 'application/vnd.ms-powerpoint') acceptObj[type] = ['.ppt'];
      else if (type === 'video/mp4') acceptObj[type] = ['.mp4'];
      else if (type === 'video/quicktime') acceptObj[type] = ['.mov'];
      else acceptObj[type] = []; // Accept without specific extension
    });
    return acceptObj;
  };

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: buildAcceptObject(allowedTypes),
    multiple: true, // Always allow multiple file selection, handle limits in onDrop
    disabled: isUploading,
  });

  const handleRemoveFile = useCallback(
    (uploadId?: string) => {
      setFileUrl(undefined);
      setUploadId(undefined);
      reset();
      // Notify parent component that file was removed (Story 2.5.3a, 5.9)
      onFileRemove?.(uploadId);
    },
    [reset, onFileRemove]
  );

  return (
    <Box sx={{ width: '100%' }}>
      {/* Story 5.9: Multiple files mode */}
      {multiple ? (
        <>
          {/* Display uploaded files list */}
          {uploadedFiles.length > 0 && (
            <List sx={{ mb: 2 }}>
              {uploadedFiles.map((file) => (
                <ListItem
                  key={file.uploadId}
                  secondaryAction={
                    <IconButton
                      edge="end"
                      aria-label={t('actions.delete')}
                      onClick={() => handleRemoveFile(file.uploadId)}
                      disabled={isUploading}
                    >
                      <DeleteIcon />
                    </IconButton>
                  }
                >
                  <ListItemIcon>{getFileTypeIcon(file.fileType)}</ListItemIcon>
                  <ListItemText primary={file.fileName} secondary={formatFileSize(file.fileSize)} />
                </ListItem>
              ))}
            </List>
          )}

          {/* Always show dropzone in multiple mode */}
          <Box
            {...getRootProps()}
            data-testid="file-dropzone"
            aria-disabled={isUploading}
            className={isDragActive ? 'dropzone-active' : ''}
            sx={{
              border: '2px dashed',
              borderColor: isDragActive ? 'primary.main' : 'grey.400',
              borderRadius: 2,
              padding: 4,
              textAlign: 'center',
              cursor: isUploading ? 'not-allowed' : 'pointer',
              backgroundColor: isDragActive ? 'action.hover' : 'background.paper',
              transition: 'all 0.2s ease-in-out',
              '&:hover': {
                borderColor: isUploading ? 'grey.400' : 'primary.main',
                backgroundColor: isUploading ? 'background.paper' : 'action.hover',
              },
            }}
          >
            <input {...getInputProps()} />
            <CloudUploadIcon sx={{ fontSize: 48, color: 'grey.500', mb: 2 }} />
            <Typography variant="body1" color="textSecondary">
              {isDragActive ? t('fileUpload.dragActive') : t('fileUpload.dragDropMultiple')}
            </Typography>
            <Typography variant="caption" color="textSecondary" sx={{ mt: 1 }}>
              {t('fileUpload.acceptedFormatsMultiple', {
                maxSize: (maxFileSize / (1024 * 1024)).toFixed(0),
              })}
            </Typography>
          </Box>
        </>
      ) : /* Original single file mode */
      fileUrl ? (
        <Box
          sx={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 2,
          }}
        >
          <Box
            component="img"
            src={fileUrl}
            alt={translatedAltText}
            sx={{
              maxWidth: '200px',
              maxHeight: '200px',
              objectFit: 'contain',
              border: '1px solid #e0e0e0',
              borderRadius: 1,
              padding: 2,
            }}
          />
          <IconButton
            onClick={() => handleRemoveFile()}
            color="error"
            aria-label={translatedRemoveButtonLabel}
            disabled={isUploading}
          >
            <DeleteIcon />
          </IconButton>
        </Box>
      ) : (
        <Box
          {...getRootProps()}
          data-testid="file-dropzone"
          aria-disabled={isUploading}
          className={isDragActive ? 'dropzone-active' : ''}
          sx={{
            border: '2px dashed',
            borderColor: isDragActive ? 'primary.main' : 'grey.400',
            borderRadius: 2,
            padding: 4,
            textAlign: 'center',
            cursor: isUploading ? 'not-allowed' : 'pointer',
            backgroundColor: isDragActive ? 'action.hover' : 'background.paper',
            transition: 'all 0.2s ease-in-out',
            '&:hover': {
              borderColor: isUploading ? 'grey.400' : 'primary.main',
              backgroundColor: isUploading ? 'background.paper' : 'action.hover',
            },
          }}
        >
          <input {...getInputProps()} />
          <CloudUploadIcon sx={{ fontSize: 48, color: 'grey.500', mb: 2 }} />
          <Typography variant="body1" color="textSecondary">
            {isDragActive ? t('fileUpload.dragActive') : t('fileUpload.dragDrop')}
          </Typography>
          <Typography variant="caption" color="textSecondary" sx={{ mt: 1 }}>
            {t('fileUpload.acceptedFormats', {
              maxSize: (maxFileSize / (1024 * 1024)).toFixed(0),
            })}
          </Typography>
        </Box>
      )}

      {isUploading && (
        <Box sx={{ width: '100%', mt: 2 }}>
          <Typography variant="body2" color="textSecondary" sx={{ mb: 1 }}>
            {t('fileUpload.uploading', { progress: uploadProgress })}
          </Typography>
          <LinearProgress variant="determinate" value={uploadProgress} role="progressbar" />
        </Box>
      )}

      {error && (
        <Alert severity="error" sx={{ mt: 2 }}>
          {error}
        </Alert>
      )}
    </Box>
  );
};
