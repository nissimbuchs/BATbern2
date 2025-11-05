/**
 * useFileUpload Hook
 * Story 1.16.3: Generic File Upload Service
 * ADR-002: Generic File Upload Service Architecture
 *
 * Reusable hook for file uploads using the generic logo upload service
 * Supports three-phase upload flow:
 * 1. Generate presigned URL from backend
 * 2. Upload file directly to S3
 * 3. Confirm upload with backend
 *
 * Can be used for any file upload: company logos, user profile pictures, event banners, etc.
 */

import { useState, useCallback } from 'react';
import apiClient from '@/services/api/apiClient';

interface UseFileUploadOptions {
  maxFileSize?: number; // in bytes, default 5MB
  allowedTypes?: string[]; // MIME types, default: PNG, JPEG, SVG
  onUploadSuccess?: (data: UploadSuccessData) => void;
  onUploadError?: (error: UploadError) => void;
}

interface UploadSuccessData {
  uploadId: string;
  tempFileUrl?: string;
}

interface UploadError {
  type: 'INVALID_FILE_TYPE' | 'FILE_TOO_LARGE' | 'UPLOAD_FAILED' | 'NETWORK_ERROR';
  message: string;
}

interface PresignedUrlResponse {
  uploadUrl: string;
  fileId: string;
  s3Key: string; // S3 key for constructing CloudFront URL
  fileExtension: string;
  expiresInMinutes: number;
  requiredHeaders: Record<string, string>;
}

const DEFAULT_MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
const DEFAULT_ALLOWED_TYPES = ['image/png', 'image/jpeg', 'image/svg+xml'];

/**
 * Get CloudFront CDN URL based on environment
 * Returns the CloudFront domain for accessing uploaded files
 */
function getCloudFrontUrl(): string {
  const hostname = window.location.hostname;

  if (hostname === 'localhost' || hostname === '127.0.0.1') {
    // Local development - files served from local S3
    return 'http://localhost:9000'; // LocalStack S3 or MinIO
  }

  if (hostname === 'staging.batbern.ch' || hostname.includes('staging')) {
    return 'https://cdn.staging.batbern.ch';
  }

  // Production
  return 'https://cdn.batbern.ch';
}

export const useFileUpload = (options: UseFileUploadOptions = {}) => {
  const {
    maxFileSize = DEFAULT_MAX_FILE_SIZE,
    allowedTypes = DEFAULT_ALLOWED_TYPES,
    onUploadSuccess,
    onUploadError,
  } = options;

  const [isUploading, setIsUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const handleError = useCallback(
    (errorType: UploadError['type'], errorMessage: string) => {
      setError(errorMessage);
      setIsUploading(false);
      setUploadProgress(0);

      if (onUploadError) {
        onUploadError({ type: errorType, message: errorMessage });
      }
    },
    [onUploadError]
  );

  const validateFile = useCallback(
    (file: File): boolean => {
      // Validate file type
      if (!allowedTypes.includes(file.type)) {
        handleError(
          'INVALID_FILE_TYPE',
          `Invalid file type. Allowed types: ${allowedTypes.join(', ')}`
        );
        return false;
      }

      // Validate file size
      if (file.size > maxFileSize) {
        const maxSizeMB = (maxFileSize / (1024 * 1024)).toFixed(2);
        handleError('FILE_TOO_LARGE', `File size must be less than ${maxSizeMB}MB`);
        return false;
      }

      return true;
    },
    [allowedTypes, maxFileSize, handleError]
  );

  const calculateChecksum = async (file: File): Promise<string> => {
    // Simple checksum calculation using file metadata
    // In production, you might want to calculate actual SHA-256
    return `${file.name}-${file.size}-${file.lastModified}`;
  };

  const uploadFile = useCallback(
    async (file: File): Promise<string | null> => {
      // Reset state
      setError(null);
      setUploadProgress(0);
      setIsUploading(true);

      // Validate file
      if (!validateFile(file)) {
        return null;
      }

      try {
        // Phase 1: Request presigned URL from backend (generic endpoint)
        const presignedResponse = await apiClient.post<PresignedUrlResponse>(
          '/logos/presigned-url',
          {
            fileName: file.name,
            fileSize: file.size,
            mimeType: file.type,
          }
        );

        const { uploadUrl, fileId, s3Key, fileExtension, requiredHeaders } = presignedResponse.data;

        // Phase 2: Upload file directly to S3 using presigned URL
        await new Promise<void>((resolve, reject) => {
          const xhr = new XMLHttpRequest();

          // Track upload progress
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
              reject(new Error(`S3 upload failed with status ${xhr.status}`));
            }
          });

          xhr.addEventListener('error', () => {
            reject(new Error('S3 upload failed'));
          });

          xhr.addEventListener('abort', () => {
            reject(new Error('S3 upload aborted'));
          });

          // Open connection and set headers
          xhr.open('PUT', uploadUrl);
          Object.entries(requiredHeaders).forEach(([key, value]) => {
            xhr.setRequestHeader(key, value);
          });

          // Send file
          xhr.send(file);
        });

        // Phase 3: Confirm upload with backend
        const checksum = await calculateChecksum(file);
        await apiClient.post(`/logos/${fileId}/confirm`, {
          fileId,
          fileExtension,
          checksum,
        });

        setUploadProgress(100);
        setIsUploading(false);

        // Notify success with CloudFront URL for display
        if (onUploadSuccess) {
          const cloudFrontUrl = getCloudFrontUrl();
          onUploadSuccess({
            uploadId: fileId,
            tempFileUrl: `${cloudFrontUrl}/${s3Key}`, // Use CloudFront URL instead of S3 direct URL
          });
        }

        return fileId;
      } catch (err) {
        const errorMessage = err instanceof Error ? err.message : 'Failed to upload file';
        handleError('UPLOAD_FAILED', errorMessage);
        return null;
      }
    },
    [validateFile, handleError, onUploadSuccess]
  );

  const reset = useCallback(() => {
    setIsUploading(false);
    setUploadProgress(0);
    setError(null);
  }, []);

  return {
    uploadFile,
    isUploading,
    uploadProgress,
    error,
    reset,
  };
};
