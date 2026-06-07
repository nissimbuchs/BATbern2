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
import { formatErrorForDisplay } from '@/utils/errorHandling/errorMessages';

interface UseFileUploadOptions {
  maxFileSize?: number; // in bytes, default 5MB
  allowedTypes?: string[]; // MIME types, default: PNG, JPEG, SVG
  onUploadSuccess?: (data: UploadSuccessData) => void;
  onUploadError?: (error: UploadError) => void;
  uploadEndpoint?: string; // endpoint for presigned URL, default: '/logos/presigned-url'
}

interface UploadSuccessData {
  uploadId: string;
  tempFileUrl?: string;
}

/**
 * Granular error types so the UI and support team can tell which step of the
 * three-phase upload broke. The legacy 'UPLOAD_FAILED' and 'NETWORK_ERROR'
 * values are kept for backwards compatibility with existing callers.
 */
type UploadErrorType =
  | 'INVALID_FILE_TYPE'
  | 'FILE_TOO_LARGE'
  | 'BACKEND_PRESIGN_FAILED' // Phase 1: backend rejected the presigned-URL request
  | 'S3_NETWORK_ERROR' // Phase 2: PUT never reached S3 (CORS / network / blocker)
  | 'S3_TIMEOUT' // Phase 2: PUT timed out
  | 'S3_REJECTED' // Phase 2: S3 returned non-2xx
  | 'S3_ABORTED' // Phase 2: user or browser aborted
  | 'BACKEND_CONFIRM_FAILED' // Phase 3: backend confirm failed
  | 'UPLOAD_FAILED' // generic fallback (kept for callers)
  | 'NETWORK_ERROR'; // legacy alias (kept for callers)

interface UploadError {
  type: UploadErrorType;
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
    // Local development - files served from MinIO
    // Include bucket name in URL for MinIO path-style access
    return 'http://localhost:8450/batbern-development-company-logos';
  }

  // Production (batbern.ch or www.batbern.ch)
  return 'https://cdn.batbern.ch';
}

export const useFileUpload = (options: UseFileUploadOptions = {}) => {
  const {
    maxFileSize = DEFAULT_MAX_FILE_SIZE,
    allowedTypes = DEFAULT_ALLOWED_TYPES,
    onUploadSuccess,
    onUploadError,
    uploadEndpoint = '/logos/presigned-url', // Default to logos for backward compatibility
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

      // ── Phase 1: Request presigned URL from backend ─────────────────────────
      let presigned: PresignedUrlResponse;
      try {
        const presignedResponse = await apiClient.post<PresignedUrlResponse>(uploadEndpoint, {
          fileName: file.name,
          fileSize: file.size,
          mimeType: file.type,
        });
        presigned = presignedResponse.data;
      } catch (err) {
        const formatted = formatErrorForDisplay(err);
        console.error('[FileUpload] Phase 1 (presigned URL request) failed', {
          fileName: file.name,
          fileSize: file.size,
          mimeType: file.type,
          endpoint: uploadEndpoint,
          statusCode: formatted.statusCode,
          serverMessage: formatted.message,
          correlationId: formatted.correlationId,
        });
        const correlationSuffix = formatted.correlationId
          ? ` (id: ${formatted.correlationId})`
          : '';
        handleError(
          'BACKEND_PRESIGN_FAILED',
          `Could not request upload URL: ${formatted.message}${correlationSuffix}`
        );
        return null;
      }

      const { uploadUrl, fileId, s3Key, fileExtension, requiredHeaders } = presigned;

      // ── Phase 2: Upload file directly to S3 using the presigned URL ─────────
      // The PUT bypasses our backend entirely. Errors here are S3-side or
      // browser-side (CORS, ad-blocker, firewall, VPN, DNS, offline).
      const S3_UPLOAD_TIMEOUT_MS = 120_000; // 2 min — generous for slow connections
      try {
        await new Promise<void>((resolve, reject) => {
          const xhr = new XMLHttpRequest();
          xhr.timeout = S3_UPLOAD_TIMEOUT_MS;

          xhr.upload.addEventListener('progress', (event) => {
            if (event.lengthComputable) {
              const percentComplete = Math.round((event.loaded / event.total) * 100);
              setUploadProgress(percentComplete);
            }
          });

          xhr.addEventListener('load', () => {
            if (xhr.status >= 200 && xhr.status < 300) {
              resolve();
              return;
            }
            // S3 returned a non-2xx — pull the XML error body so we know WHY
            // (AccessDenied, EntityTooLarge, signature mismatch, expired, …).
            const bodyPreview = (xhr.responseText ?? '').slice(0, 500);
            const error = new Error('S3_REJECTED');
            // Stash diagnostics on the error for the outer catch.
            Object.assign(error, {
              s3Status: xhr.status,
              s3StatusText: xhr.statusText,
              s3Body: bodyPreview,
            });
            reject(error);
          });

          xhr.addEventListener('error', () => {
            reject(new Error('S3_NETWORK_ERROR'));
          });

          xhr.addEventListener('timeout', () => {
            reject(new Error('S3_TIMEOUT'));
          });

          xhr.addEventListener('abort', () => {
            reject(new Error('S3_ABORTED'));
          });

          xhr.open('PUT', uploadUrl);
          Object.entries(requiredHeaders).forEach(([key, value]) => {
            xhr.setRequestHeader(key, value);
          });
          xhr.send(file);
        });
      } catch (err) {
        // Strip the query string from the upload URL before logging — it
        // contains the signed credential and we don't want it in browser
        // consoles or shipped log aggregators.
        const safeUploadUrl = uploadUrl.split('?')[0];
        const errCode = err instanceof Error ? err.message : 'S3_UPLOAD_UNKNOWN';
        const diag = err as Error & { s3Status?: number; s3StatusText?: string; s3Body?: string };

        if (errCode === 'S3_REJECTED') {
          console.error('[FileUpload] Phase 2 (S3 PUT) rejected by S3', {
            fileName: file.name,
            fileSize: file.size,
            mimeType: file.type,
            uploadUrl: safeUploadUrl,
            uploadId: fileId,
            s3Status: diag.s3Status,
            s3StatusText: diag.s3StatusText,
            s3Body: diag.s3Body,
          });
          const status = diag.s3Status ?? 0;
          const statusText = diag.s3StatusText ? ` ${diag.s3StatusText}` : '';
          const bodyHint = diag.s3Body ? ` — ${diag.s3Body}` : '';
          handleError(
            'S3_REJECTED',
            `S3 rejected the upload (HTTP ${status}${statusText})${bodyHint}. ` +
              `Upload ID: ${fileId}.`
          );
          return null;
        }

        if (errCode === 'S3_NETWORK_ERROR') {
          console.error('[FileUpload] Phase 2 (S3 PUT) network error — no response received', {
            fileName: file.name,
            fileSize: file.size,
            mimeType: file.type,
            uploadUrl: safeUploadUrl,
            uploadId: fileId,
            online: typeof navigator !== 'undefined' ? navigator.onLine : 'unknown',
          });
          handleError(
            'S3_NETWORK_ERROR',
            'The upload could not reach S3. This is usually a network problem ' +
              '(offline / VPN / DNS), a CORS rejection, or a browser extension / ' +
              'firewall blocking the request. Open DevTools → Network for the failing ' +
              `PUT to see the exact cause, then retry. Upload ID: ${fileId}.`
          );
          return null;
        }

        if (errCode === 'S3_TIMEOUT') {
          console.error('[FileUpload] Phase 2 (S3 PUT) timed out', {
            fileName: file.name,
            fileSize: file.size,
            mimeType: file.type,
            uploadUrl: safeUploadUrl,
            uploadId: fileId,
            timeoutMs: S3_UPLOAD_TIMEOUT_MS,
          });
          handleError(
            'S3_TIMEOUT',
            `Upload timed out after ${S3_UPLOAD_TIMEOUT_MS / 1000}s. ` +
              `Check your connection and retry. Upload ID: ${fileId}.`
          );
          return null;
        }

        if (errCode === 'S3_ABORTED') {
          console.warn('[FileUpload] Phase 2 (S3 PUT) aborted', {
            fileName: file.name,
            uploadId: fileId,
          });
          handleError('S3_ABORTED', `Upload aborted. Upload ID: ${fileId}.`);
          return null;
        }

        // Unknown — preserve original message for forensics
        console.error('[FileUpload] Phase 2 (S3 PUT) failed with unknown error', err);
        handleError('UPLOAD_FAILED', `Upload failed: ${errCode}. Upload ID: ${fileId}.`);
        return null;
      }

      // ── Phase 3: Confirm upload with backend ────────────────────────────────
      try {
        const checksum = await calculateChecksum(file);
        // Extract base path from uploadEndpoint (/logos/presigned-url -> /logos)
        const basePath = uploadEndpoint.split('/').slice(0, -1).join('/');
        await apiClient.post(`${basePath}/${fileId}/confirm`, {
          fileId,
          fileExtension,
          checksum,
        });
      } catch (err) {
        const formatted = formatErrorForDisplay(err);
        console.error('[FileUpload] Phase 3 (confirm) failed — file IS in S3 but unconfirmed', {
          fileName: file.name,
          uploadId: fileId,
          statusCode: formatted.statusCode,
          serverMessage: formatted.message,
          correlationId: formatted.correlationId,
        });
        const correlationSuffix = formatted.correlationId
          ? ` (id: ${formatted.correlationId})`
          : '';
        handleError(
          'BACKEND_CONFIRM_FAILED',
          `Upload reached S3 but the server could not confirm it: ${formatted.message}${correlationSuffix}. ` +
            `Upload ID: ${fileId}.`
        );
        return null;
      }

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
    },
    [validateFile, handleError, onUploadSuccess, uploadEndpoint]
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
