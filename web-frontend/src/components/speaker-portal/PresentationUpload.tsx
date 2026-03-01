/**
 * PresentationUpload Component
 * Story 6.3: Speaker Content Self-Submission Portal - AC7 (File Upload)
 *
 * Features:
 * - Drag-and-drop support (AC7.1)
 * - Click to browse (AC7.2)
 * - File type validation - PPTX, PDF, KEY (AC7.3)
 * - File size validation - max 50MB (AC7.4)
 * - Upload uses presigned URL (AC7.5)
 * - Progress indicator during upload (AC7.6)
 */

import { useState, useCallback, useRef, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Upload, FileText, X, AlertCircle, ExternalLink } from 'lucide-react';
import { BATbernLoader } from '@components/shared/BATbernLoader';
import { speakerPortalService } from '@/services/speakerPortalService';

// Constants
const MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB (AC7.4)
const ALLOWED_TYPES = [
  'application/vnd.openxmlformats-officedocument.presentationml.presentation', // PPTX
  'application/vnd.ms-powerpoint', // PPT
  'application/pdf', // PDF
  'application/x-iwork-keynote-sffkey', // Keynote (KEY)
  'application/vnd.apple.keynote', // Keynote alternative MIME
]; // (AC7.3)
const ALLOWED_EXTENSIONS = ['pptx', 'ppt', 'pdf', 'key'];

interface PresentationUploadProps {
  /** Magic link token for authentication */
  token: string;
  /** Current material URL (null if none) */
  currentMaterialUrl: string | null;
  /** Current material file name (null if none) */
  currentMaterialName: string | null;
  /** Callback when material is successfully uploaded */
  onMaterialUploaded: (url: string, fileName: string) => void;
  /** Callback when an error occurs */
  onError: (error: { type: string; message: string }) => void;
}

/**
 * Presentation material upload component with drag-and-drop support.
 * Uses presigned URL pattern for direct S3 upload.
 */
const PresentationUpload = ({
  token,
  currentMaterialUrl,
  currentMaterialName,
  onMaterialUploaded,
  onError,
}: PresentationUploadProps) => {
  const { t } = useTranslation();
  // State
  const [isUploading, setIsUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [selectedFileName, setSelectedFileName] = useState<string | null>(null);
  const [showDropzone, setShowDropzone] = useState(!currentMaterialUrl);

  // Refs
  const fileInputRef = useRef<HTMLInputElement>(null);
  const dropzoneRef = useRef<HTMLDivElement>(null);

  // Update showDropzone when currentMaterialUrl changes
  useEffect(() => {
    if (currentMaterialUrl && !isUploading) {
      setShowDropzone(false);
    }
  }, [currentMaterialUrl, isUploading]);

  /**
   * Get file extension from filename
   */
  const getFileExtension = (fileName: string): string => {
    return fileName.split('.').pop()?.toLowerCase() || '';
  };

  /**
   * Validate file type and size
   */
  const validateFile = useCallback(
    (file: File): boolean => {
      // Validate file type (AC7.3)
      const extension = getFileExtension(file.name);
      const isValidType =
        ALLOWED_TYPES.includes(file.type) || ALLOWED_EXTENSIONS.includes(extension);

      if (!isValidType) {
        const errorMsg = t('speakerPortal.upload.invalidFileType');
        setError(errorMsg);
        onError({ type: 'INVALID_FILE_TYPE', message: errorMsg });
        return false;
      }

      // Validate file size (AC7.4)
      if (file.size > MAX_FILE_SIZE) {
        const errorMsg = t('speakerPortal.upload.fileTooLarge');
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

      // Set file name and start upload
      setSelectedFileName(file.name);
      setIsUploading(true);

      try {
        // Upload using service (3-phase presigned URL flow)
        const response = await speakerPortalService.uploadMaterial(token, file, (progress) =>
          setUploadProgress(progress)
        );

        // Notify success
        setIsUploading(false);
        setUploadProgress(0);
        setSelectedFileName(null);
        setShowDropzone(false);
        onMaterialUploaded(response.cloudFrontUrl, response.fileName);
      } catch (err) {
        // Handle error
        setIsUploading(false);
        setUploadProgress(0);
        setSelectedFileName(null);

        const errorMsg = err instanceof Error ? err.message : 'Upload failed';
        setError(errorMsg);
        onError({ type: 'UPLOAD_FAILED', message: errorMsg });
      }
    },
    [token, validateFile, onMaterialUploaded, onError]
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
   * Handle "Change" button click
   */
  const handleChangeFile = useCallback(() => {
    setShowDropzone(true);
    setError(null);
  }, []);

  /**
   * Handle "Cancel" button click
   */
  const handleCancel = useCallback(() => {
    setShowDropzone(false);
    setError(null);
    setSelectedFileName(null);
  }, []);

  // Render material display (when material exists and not changing)
  if (!showDropzone && currentMaterialUrl && currentMaterialName) {
    return (
      <div className="flex flex-col gap-4 p-4 bg-gray-700 rounded-lg">
        <div className="flex items-center gap-3">
          <FileText className="h-8 w-8 text-blue-400 flex-shrink-0" />
          <div className="flex-1 min-w-0">
            <a
              href={currentMaterialUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="text-blue-400 hover:text-blue-300 transition-colors truncate block"
            >
              {currentMaterialName}
              <ExternalLink className="h-3 w-3 inline-block ml-1" />
            </a>
            <p className="text-sm text-gray-500">{t('speakerPortal.upload.presentationFile')}</p>
          </div>
        </div>
        <button
          type="button"
          onClick={handleChangeFile}
          className="text-sm text-blue-400 hover:text-blue-300 transition-colors self-start"
        >
          {t('speakerPortal.upload.changeFile')}
        </button>
      </div>
    );
  }

  // Render dropzone
  return (
    <div className="flex flex-col gap-4">
      {/* Dropzone (AC7.1, AC7.2) */}
      <div
        ref={dropzoneRef}
        data-testid="material-dropzone"
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
          p-8 flex flex-col items-center justify-center
          border-2 border-dashed rounded-lg transition-all cursor-pointer
          ${isDragging ? 'border-blue-500 bg-blue-900/20' : 'border-gray-600 hover:border-gray-500'}
          ${isUploading ? 'opacity-50 cursor-not-allowed' : ''}
        `}
      >
        {/* Hidden file input inside dropzone for test accessibility */}
        <input
          ref={fileInputRef}
          type="file"
          accept={[...ALLOWED_TYPES, ...ALLOWED_EXTENSIONS.map((ext) => `.${ext}`)].join(',')}
          onChange={handleFileChange}
          className="hidden"
          aria-label={t('speakerPortal.upload.uploadAria')}
        />

        {isUploading ? (
          <>
            <BATbernLoader size={96} />
            <p className="text-white font-medium">{selectedFileName}</p>
          </>
        ) : (
          <>
            <Upload className="h-12 w-12 text-gray-400 mb-4" />
            <p className="text-white font-medium mb-1">
              {t('speakerPortal.upload.dragDropPresentation')}
            </p>
            <p className="text-gray-400 text-sm">{t('speakerPortal.upload.orClickBrowse')}</p>
          </>
        )}
      </div>

      {/* Upload progress (AC7.6) */}
      {isUploading && (
        <div role="status" aria-live="polite">
          <div className="flex justify-between text-sm text-gray-400 mb-1">
            <span>{t('speakerPortal.upload.uploading')}</span>
            <span>{uploadProgress}%</span>
          </div>
          <div className="h-2 bg-gray-700 rounded-full overflow-hidden">
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
      {!isUploading && (
        <p className="text-xs text-gray-500 text-center">
          {t('speakerPortal.upload.acceptedFormats')}
        </p>
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

      {/* Cancel button (when changing existing material) */}
      {currentMaterialUrl && !isUploading && (
        <button
          type="button"
          onClick={handleCancel}
          className="flex items-center gap-1 text-sm text-gray-400 hover:text-gray-300 transition-colors self-center"
          aria-label={t('speakerPortal.photo.cancel')}
        >
          <X className="h-4 w-4" />
          {t('speakerPortal.photo.cancel')}
        </button>
      )}
    </div>
  );
};

export default PresentationUpload;
