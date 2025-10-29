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
import { Box, Typography, IconButton, LinearProgress, Alert } from '@mui/material';
import { CloudUpload as CloudUploadIcon, Delete as DeleteIcon } from '@mui/icons-material';
import { useFileUpload } from '@/hooks/useFileUpload/useFileUpload';

interface FileUploadProps {
  currentFileUrl?: string;
  onUploadSuccess?: (data: { uploadId: string; tempFileUrl?: string }) => void;
  onUploadError?: (error: { type: string; message: string }) => void;
  maxFileSize?: number; // in bytes, default 5MB
  allowedTypes?: string[]; // MIME types
  altText?: string; // Alt text for the uploaded image
  removeButtonLabel?: string; // Aria label for the remove button
}

export const FileUpload: React.FC<FileUploadProps> = ({
  currentFileUrl,
  onUploadSuccess,
  onUploadError,
  maxFileSize = 5 * 1024 * 1024, // 5MB
  allowedTypes = ['image/png', 'image/jpeg', 'image/svg+xml'],
  altText = 'Uploaded file preview',
  removeButtonLabel = 'Remove file',
}) => {
  const [fileUrl, setFileUrl] = useState<string | undefined>(currentFileUrl);
  const [, setUploadId] = useState<string | undefined>();

  const { uploadFile, isUploading, uploadProgress, error, reset } = useFileUpload({
    maxFileSize,
    allowedTypes,
    onUploadSuccess: (data) => {
      setFileUrl(data.tempFileUrl);
      setUploadId(data.uploadId);
      onUploadSuccess?.(data);
    },
    onUploadError,
  });

  // Sync fileUrl with currentFileUrl prop
  useEffect(() => {
    setFileUrl(currentFileUrl);
  }, [currentFileUrl]);

  const onDrop = useCallback(
    async (acceptedFiles: File[]) => {
      // Reset error state
      reset();

      // Check for multiple files - we handle this manually for better error messaging
      if (acceptedFiles.length > 1) {
        onUploadError?.({
          type: 'MULTIPLE_FILES',
          message: 'Only one file can be uploaded at a time',
        });
        return;
      }

      const file = acceptedFiles[0];
      if (file) {
        await uploadFile(file);
      }
    },
    [uploadFile, reset, onUploadError]
  );

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: {
      'image/png': ['.png'],
      'image/jpeg': ['.jpg', '.jpeg'],
      'image/svg+xml': ['.svg'],
    },
    // Note: We don't set maxFiles or multiple:false here to allow testing
    // and to provide a custom error message when multiple files are selected
    disabled: isUploading,
  });

  const handleRemoveFile = useCallback(() => {
    setFileUrl(undefined);
    setUploadId(undefined);
    reset();
  }, [reset]);

  return (
    <Box sx={{ width: '100%' }}>
      {fileUrl ? (
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
            alt={altText}
            crossOrigin="anonymous"
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
            onClick={handleRemoveFile}
            color="error"
            aria-label={removeButtonLabel}
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
            {isDragActive ? 'Drop the file here' : 'Drag and drop a file here, or click to select'}
          </Typography>
          <Typography variant="caption" color="textSecondary" sx={{ mt: 1 }}>
            Accepted formats: PNG, JPEG, SVG (max {(maxFileSize / (1024 * 1024)).toFixed(0)}MB)
          </Typography>
        </Box>
      )}

      {isUploading && (
        <Box sx={{ width: '100%', mt: 2 }}>
          <Typography variant="body2" color="textSecondary" sx={{ mb: 1 }}>
            Uploading... {uploadProgress}%
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
