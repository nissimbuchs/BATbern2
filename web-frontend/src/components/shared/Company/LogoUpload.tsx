import React, { useState, useCallback } from 'react';
import { useDropzone } from 'react-dropzone';
import { Box, Typography, IconButton, LinearProgress, Alert } from '@mui/material';
import { CloudUpload as CloudUploadIcon, Delete as DeleteIcon } from '@mui/icons-material';
import apiClient from '@/services/api/apiClient';

interface LogoUploadProps {
  companyId: string;
  currentLogoUrl?: string;
  onUploadSuccess?: (data: { logoUrl: string }) => void;
  onUploadError?: (error: { type: string; message: string }) => void;
}

const MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
const ACCEPTED_FILE_TYPES = ['image/png', 'image/jpeg', 'image/svg+xml'];

export const LogoUpload: React.FC<LogoUploadProps> = ({
  companyId,
  currentLogoUrl,
  onUploadSuccess,
  onUploadError,
}) => {
  const [logoUrl, setLogoUrl] = useState<string | undefined>(currentLogoUrl);
  const [uploadProgress, setUploadProgress] = useState<number>(0);
  const [isUploading, setIsUploading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const handleError = useCallback(
    (errorType: string, errorMessage: string) => {
      setError(errorMessage);
      if (onUploadError) {
        onUploadError({ type: errorType, message: errorMessage });
      }
    },
    [onUploadError]
  );

  const validateFile = useCallback(
    (file: File): boolean => {
      // Validate file type
      if (!ACCEPTED_FILE_TYPES.includes(file.type)) {
        handleError('INVALID_FILE_TYPE', 'Only PNG, JPEG, and SVG files are allowed');
        return false;
      }

      // Validate file size
      if (file.size > MAX_FILE_SIZE) {
        handleError('FILE_TOO_LARGE', 'File size must be less than 5MB');
        return false;
      }

      return true;
    },
    [handleError]
  );

  const uploadToS3 = useCallback(
    async (file: File) => {
      setIsUploading(true);
      setError(null);
      setUploadProgress(0);

      try {
        // Step 1: Request presigned URL from backend
        const presignedResponse = await apiClient.post(
          `/companies/${companyId}/logo/presigned-url`,
          {
            fileName: file.name,
            fileSize: file.size,
            mimeType: file.type,
          }
        );

        const { uploadUrl: presignedUrl, fileId } = presignedResponse.data;

        // Step 2: Upload to S3 with progress tracking
        await new Promise<void>((resolve, reject) => {
          const xhr = new XMLHttpRequest();

          xhr.upload.addEventListener('progress', (event) => {
            if (event.lengthComputable) {
              const percentComplete = Math.round((event.loaded / event.total) * 100);
              setUploadProgress(percentComplete);
            }
          });

          xhr.addEventListener('load', () => {
            if (xhr.status === 200) {
              resolve();
            } else {
              reject(new Error('S3 upload failed'));
            }
          });

          xhr.addEventListener('error', () => {
            reject(new Error('S3 upload failed'));
          });

          xhr.open('PUT', presignedUrl);
          xhr.setRequestHeader('Content-Type', file.type);
          xhr.send(file);
        });

        // Step 3: Confirm upload with backend
        const confirmResponse = await apiClient.post(`/companies/${companyId}/logo/confirm`, {
          fileId,
        });

        const { logoUrl: newLogoUrl } = confirmResponse.data;

        setLogoUrl(newLogoUrl);
        setUploadProgress(100);

        if (onUploadSuccess) {
          onUploadSuccess({ logoUrl: newLogoUrl });
        }
      } catch (err) {
        const errorMessage = err instanceof Error ? err.message : 'Failed to upload logo';
        handleError('UPLOAD_FAILED', errorMessage);
      } finally {
        setIsUploading(false);
      }
    },
    [companyId, handleError, onUploadSuccess]
  );

  const onDrop = useCallback(
    <T extends File>(
      acceptedFiles: T[],
      fileRejections: import('react-dropzone').FileRejection[]
    ) => {
      // Reset error state
      setError(null);

      // Check for multiple files
      if (acceptedFiles.length > 1) {
        handleError('MULTIPLE_FILES', 'Only one file can be uploaded at a time');
        return;
      }

      // Check for rejected files
      if (fileRejections.length > 0) {
        handleError('INVALID_FILE', 'File was rejected. Please check file type and size.');
        return;
      }

      const file = acceptedFiles[0];
      if (file && validateFile(file)) {
        uploadToS3(file);
      }
    },
    [handleError, validateFile, uploadToS3]
  );

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: {
      'image/png': ['.png'],
      'image/jpeg': ['.jpg', '.jpeg'],
      'image/svg+xml': ['.svg'],
    },
    maxFiles: 1,
    disabled: isUploading,
  });

  const handleRemoveLogo = useCallback(() => {
    setLogoUrl(undefined);
    setError(null);
    setUploadProgress(0);
  }, []);

  return (
    <Box sx={{ width: '100%' }}>
      {logoUrl ? (
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
            src={logoUrl}
            alt="Company logo preview"
            sx={{
              maxWidth: '200px',
              maxHeight: '200px',
              objectFit: 'contain',
              border: '1px solid #e0e0e0',
              borderRadius: 1,
              padding: 2,
            }}
          />
          <IconButton onClick={handleRemoveLogo} color="error" aria-label="Remove logo">
            <DeleteIcon />
          </IconButton>
        </Box>
      ) : (
        <Box
          {...getRootProps()}
          data-testid="logo-dropzone"
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
            {isDragActive ? 'Drop the file here' : 'Drag and drop a logo here, or click to select'}
          </Typography>
          <Typography variant="caption" color="textSecondary" sx={{ mt: 1 }}>
            Accepted formats: PNG, JPEG, SVG (max 5MB)
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
