/**
 * ProfilePhotoUpload Component
 * Story 6.2b: Speaker Profile Update Portal - AC7 (Profile Photo Upload)
 *
 * Features:
 * - Drag-and-drop support (AC7.1)
 * - Click to browse (AC7.2)
 * - File type validation - JPEG, PNG, WebP (AC7.3)
 * - File size validation - max 5MB (AC7.4)
 * - Image preview after selection (AC7.5)
 * - Upload uses presigned URL (AC7.6)
 * - Progress indicator during upload (AC7.7)
 * - Photo updates immediately after upload (AC7.8)
 */

import { useState, useCallback, useRef, useEffect } from 'react';
import { Upload, X, Loader2, AlertCircle } from 'lucide-react';
import { speakerPortalService } from '@/services/speakerPortalService';

// Constants
const MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB (AC7.4)
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp']; // (AC7.3)

interface ProfilePhotoUploadProps {
  /** Magic link token for authentication */
  token: string;
  /** Current profile photo URL (null if none) */
  currentPhotoUrl: string | null;
  /** Callback when photo is successfully uploaded */
  onPhotoUploaded: (url: string) => void;
  /** Callback when an error occurs */
  onError: (error: { type: string; message: string }) => void;
}

/**
 * Profile photo upload component with drag-and-drop support.
 * Uses presigned URL pattern for direct S3 upload.
 */
const ProfilePhotoUpload = ({
  token,
  currentPhotoUrl,
  onPhotoUploaded,
  onError,
}: ProfilePhotoUploadProps) => {
  // State
  const [isUploading, setIsUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [showDropzone, setShowDropzone] = useState(!currentPhotoUrl);

  // Refs
  const fileInputRef = useRef<HTMLInputElement>(null);
  const dropzoneRef = useRef<HTMLDivElement>(null);

  // Update showDropzone when currentPhotoUrl changes
  useEffect(() => {
    if (currentPhotoUrl && !isUploading) {
      setShowDropzone(false);
    }
  }, [currentPhotoUrl, isUploading]);

  // Clean up preview URL on unmount
  useEffect(() => {
    return () => {
      if (previewUrl) {
        URL.revokeObjectURL(previewUrl);
      }
    };
  }, [previewUrl]);

  /**
   * Validate file type and size
   */
  const validateFile = useCallback(
    (file: File): boolean => {
      // Validate file type (AC7.3)
      if (!ALLOWED_TYPES.includes(file.type)) {
        const errorMsg = 'Invalid file type. Accepted formats: JPEG, PNG, WebP';
        setError(errorMsg);
        onError({ type: 'INVALID_FILE_TYPE', message: errorMsg });
        return false;
      }

      // Validate file size (AC7.4)
      if (file.size > MAX_FILE_SIZE) {
        const errorMsg = 'File size must be less than 5MB';
        setError(errorMsg);
        onError({ type: 'FILE_TOO_LARGE', message: errorMsg });
        return false;
      }

      return true;
    },
    [onError]
  );

  /**
   * Handle file upload
   */
  const handleUpload = useCallback(
    async (file: File) => {
      // Reset state
      setError(null);
      setUploadProgress(0);

      // Validate file
      if (!validateFile(file)) {
        return;
      }

      // Create preview (AC7.5)
      const objectUrl = URL.createObjectURL(file);
      setPreviewUrl(objectUrl);
      setIsUploading(true);

      try {
        // Upload using service (3-phase presigned URL flow)
        const uploadedUrl = await speakerPortalService.uploadProfilePhoto(token, file, (progress) =>
          setUploadProgress(progress)
        );

        // Clean up preview and notify success
        URL.revokeObjectURL(objectUrl);
        setPreviewUrl(null);
        setIsUploading(false);
        setUploadProgress(0);
        onPhotoUploaded(uploadedUrl);
      } catch (err) {
        // Clean up preview on error
        URL.revokeObjectURL(objectUrl);
        setPreviewUrl(null);
        setIsUploading(false);
        setUploadProgress(0);

        const errorMsg = err instanceof Error ? err.message : 'Upload failed';
        setError(errorMsg);
        onError({ type: 'UPLOAD_FAILED', message: errorMsg });
      }
    },
    [token, validateFile, onPhotoUploaded, onError]
  );

  /**
   * Handle file input change
   */
  const handleFileChange = useCallback(
    (event: React.ChangeEvent<HTMLInputElement>) => {
      const file = event.target.files?.[0];
      if (file) {
        handleUpload(file);
      }
      // Reset input to allow selecting same file again
      event.target.value = '';
    },
    [handleUpload]
  );

  /**
   * Handle drag events
   */
  const handleDragEnter = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(true);
  }, []);

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    // Only set to false if we're leaving the dropzone entirely
    if (e.currentTarget === e.target) {
      setIsDragging(false);
    }
  }, []);

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
  }, []);

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      e.stopPropagation();
      setIsDragging(false);

      const file = e.dataTransfer.files?.[0];
      if (file) {
        handleUpload(file);
      }
    },
    [handleUpload]
  );

  /**
   * Handle click on dropzone
   */
  const handleDropzoneClick = useCallback(() => {
    if (!isUploading) {
      fileInputRef.current?.click();
    }
  }, [isUploading]);

  /**
   * Handle keyboard activation of dropzone
   */
  const handleDropzoneKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        handleDropzoneClick();
      }
    },
    [handleDropzoneClick]
  );

  /**
   * Handle "Change Photo" button click
   */
  const handleChangePhoto = useCallback(() => {
    setShowDropzone(true);
    setError(null);
  }, []);

  /**
   * Handle "Cancel" button click
   */
  const handleCancel = useCallback(() => {
    setShowDropzone(false);
    setError(null);
    setPreviewUrl(null);
  }, []);

  // Render photo display (when photo exists and not changing)
  if (!showDropzone && currentPhotoUrl) {
    return (
      <div className="flex flex-col items-center gap-4">
        <div className="relative">
          <img
            src={currentPhotoUrl}
            alt="Profile photo"
            className="w-32 h-32 rounded-full object-cover border-4 border-zinc-700"
          />
        </div>
        <button
          type="button"
          onClick={handleChangePhoto}
          className="text-sm text-blue-400 hover:text-blue-300 transition-colors"
        >
          Change photo
        </button>
      </div>
    );
  }

  // Render dropzone
  return (
    <div className="flex flex-col items-center gap-4">
      {/* Preview during upload (AC7.5) */}
      {previewUrl && (
        <img
          data-testid="photo-preview"
          src={previewUrl}
          alt="Photo preview"
          className="w-32 h-32 rounded-full object-cover border-4 border-zinc-700"
        />
      )}

      {/* Dropzone (AC7.1, AC7.2) */}
      {!previewUrl && (
        <div
          ref={dropzoneRef}
          data-testid="photo-dropzone"
          role="button"
          tabIndex={0}
          aria-disabled={isUploading}
          onClick={handleDropzoneClick}
          onKeyDown={handleDropzoneKeyDown}
          onDragEnter={handleDragEnter}
          onDragLeave={handleDragLeave}
          onDragOver={handleDragOver}
          onDrop={handleDrop}
          className={`
            w-32 h-32 rounded-full flex flex-col items-center justify-center
            border-2 border-dashed transition-all cursor-pointer
            ${isDragging ? 'border-blue-500 bg-blue-900/20' : 'border-zinc-600 hover:border-zinc-500'}
            ${isUploading ? 'opacity-50 cursor-not-allowed' : ''}
          `}
        >
          {/* Hidden file input inside dropzone for test accessibility */}
          <input
            ref={fileInputRef}
            type="file"
            accept={ALLOWED_TYPES.join(',')}
            onChange={handleFileChange}
            className="hidden"
            aria-label="Upload profile photo"
          />
          {isUploading ? (
            <Loader2 className="h-8 w-8 text-blue-400 animate-spin" />
          ) : (
            <Upload className="h-8 w-8 text-zinc-400" />
          )}
        </div>
      )}

      {/* Upload progress (AC7.7) */}
      {isUploading && (
        <div className="w-full max-w-xs" role="status" aria-live="polite">
          <div className="flex justify-between text-sm text-zinc-400 mb-1">
            <span>Uploading...</span>
            <span>{uploadProgress}%</span>
          </div>
          <div className="h-2 bg-zinc-700 rounded-full overflow-hidden">
            <div
              role="progressbar"
              aria-valuenow={uploadProgress}
              aria-valuemin={0}
              aria-valuemax={100}
              className="h-full bg-blue-500 transition-all duration-300"
              style={{ width: `${uploadProgress}%` }}
            />
          </div>
        </div>
      )}

      {/* Instructions */}
      {!isUploading && !previewUrl && (
        <div className="text-center">
          <p className="text-sm text-zinc-400">
            Drag and drop a photo here
            <br />
            or click to browse
          </p>
          <p className="text-xs text-zinc-500 mt-1">JPEG, PNG, WebP (max 5MB)</p>
        </div>
      )}

      {/* Error message */}
      {error && (
        <div
          role="alert"
          className="flex items-center gap-2 text-sm text-red-400 bg-red-900/20 px-3 py-2 rounded-lg"
        >
          <AlertCircle className="h-4 w-4 flex-shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Cancel button (when changing existing photo) */}
      {currentPhotoUrl && !isUploading && (
        <button
          type="button"
          onClick={handleCancel}
          className="flex items-center gap-1 text-sm text-zinc-400 hover:text-zinc-300 transition-colors"
          aria-label="Cancel"
        >
          <X className="h-4 w-4" />
          Cancel
        </button>
      )}
    </div>
  );
};

export default ProfilePhotoUpload;
