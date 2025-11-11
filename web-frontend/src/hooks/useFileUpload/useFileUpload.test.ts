/**
 * useFileUpload Hook Tests
 * Story 1.16.3: Generic File Upload Service
 * Tests three-phase upload flow: presigned URL → S3 upload → confirm
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useFileUpload } from './useFileUpload';
import apiClient from '@/services/api/apiClient';

// Mock apiClient
vi.mock('@/services/api/apiClient', () => ({
  default: {
    post: vi.fn(),
  },
}));

const mockApiClient = vi.mocked(apiClient);

describe('useFileUpload Hook', () => {
  let mockXHR: Partial<XMLHttpRequest>;
  let xhrInstance: Partial<XMLHttpRequest>;

  beforeEach(() => {
    vi.clearAllMocks();

    // Mock XMLHttpRequest
    mockXHR = {
      open: vi.fn(),
      send: vi.fn(),
      setRequestHeader: vi.fn(),
      upload: {
        addEventListener: vi.fn(),
      } as XMLHttpRequestUpload,
      addEventListener: vi.fn(),
      status: 200,
    };

    xhrInstance = mockXHR;
    global.XMLHttpRequest = vi.fn(() => xhrInstance) as unknown as typeof XMLHttpRequest;
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  // ============ AC1: Generate Presigned URL Tests ============

  it('should_initializeWithDefaultState_when_hookCreated', () => {
    // Arrange & Act
    const { result } = renderHook(() => useFileUpload());

    // Assert
    expect(result.current.isUploading).toBe(false);
    expect(result.current.uploadProgress).toBe(0);
    expect(result.current.error).toBeNull();
  });

  it('should_uploadFileSuccessfully_when_validFileProvided', async () => {
    // Arrange
    const file = new File(['test content'], 'test-logo.png', { type: 'image/png' });
    const mockPresignedResponse = {
      data: {
        uploadUrl: 'https://s3.amazonaws.com/bucket/logos/temp/upload-123/logo.png?signature=xyz',
        fileId: 'upload-123',
        s3Key: 'logos/temp/upload-123/logo.png',
        fileExtension: 'png',
        expiresInMinutes: 15,
        requiredHeaders: {
          'Content-Type': 'image/png',
        },
      },
    };

    mockApiClient.post
      .mockResolvedValueOnce(mockPresignedResponse) // Phase 1: presigned URL
      .mockResolvedValueOnce({ data: {} }); // Phase 3: confirm

    const onUploadSuccess = vi.fn();
    const { result } = renderHook(() =>
      useFileUpload({
        onUploadSuccess,
      })
    );

    // Act
    let uploadPromise: Promise<string | null>;
    act(() => {
      uploadPromise = result.current.uploadFile(file);
    });

    // Wait for presigned URL request
    await waitFor(() => {
      expect(mockApiClient.post).toHaveBeenCalledWith('/logos/presigned-url', {
        fileName: 'test-logo.png',
        fileSize: file.size,
        mimeType: 'image/png',
      });
    });

    // Simulate S3 upload completion
    await act(async () => {
      const mockFn = mockXHR.addEventListener as ReturnType<typeof vi.fn>;
      const loadListener = mockFn.mock.calls.find((call: unknown[]) => call[0] === 'load')?.[1] as
        | (() => void)
        | undefined;
      if (loadListener) {
        loadListener();
      }
    });

    // Wait for confirm request
    await waitFor(() => {
      expect(mockApiClient.post).toHaveBeenCalledWith(
        '/logos/upload-123/confirm',
        expect.objectContaining({
          fileId: 'upload-123',
          fileExtension: 'png',
          checksum: expect.any(String),
        })
      );
    });

    // Assert
    await waitFor(() => {
      expect(result.current.isUploading).toBe(false);
      expect(result.current.uploadProgress).toBe(100);
      expect(result.current.error).toBeNull();
      expect(onUploadSuccess).toHaveBeenCalledWith({
        uploadId: 'upload-123',
        tempFileUrl: 'http://localhost:8450/logos/temp/upload-123/logo.png',
      });
    });

    const uploadId = await uploadPromise!;
    expect(uploadId).toBe('upload-123');
  });

  it('should_trackUploadProgress_when_fileUploading', async () => {
    // Arrange
    const file = new File(['test content'], 'test-logo.png', { type: 'image/png' });
    const mockPresignedResponse = {
      data: {
        uploadUrl: 'https://s3.amazonaws.com/bucket/test.png',
        fileId: 'upload-456',
        s3Key: 'logos/test.png',
        fileExtension: 'png',
        expiresInMinutes: 15,
        requiredHeaders: {},
      },
    };

    mockApiClient.post
      .mockResolvedValueOnce(mockPresignedResponse)
      .mockResolvedValueOnce({ data: {} });

    const { result } = renderHook(() => useFileUpload());

    // Act
    act(() => {
      result.current.uploadFile(file);
    });

    // Wait for presigned URL
    await waitFor(() => {
      expect(result.current.isUploading).toBe(true);
    });

    // Simulate progress events
    await act(async () => {
      const mockFn = mockXHR.upload!.addEventListener as ReturnType<typeof vi.fn>;
      const progressListener = mockFn.mock.calls.find(
        (call: unknown[]) => call[0] === 'progress'
      )?.[1] as ((event: ProgressEvent) => void) | undefined;

      if (progressListener) {
        // 50% progress
        progressListener({ lengthComputable: true, loaded: 500, total: 1000 } as ProgressEvent);
      }
    });

    // Assert progress update
    await waitFor(() => {
      expect(result.current.uploadProgress).toBe(50);
    });

    // Simulate upload completion
    await act(async () => {
      const mockFn = mockXHR.addEventListener as ReturnType<typeof vi.fn>;
      const loadListener = mockFn.mock.calls.find((call: unknown[]) => call[0] === 'load')?.[1] as
        | (() => void)
        | undefined;
      if (loadListener) {
        loadListener();
      }
    });

    // Assert completion
    await waitFor(() => {
      expect(result.current.uploadProgress).toBe(100);
      expect(result.current.isUploading).toBe(false);
    });
  });

  // ============ Validation Tests ============

  it('should_rejectInvalidFileType_when_fileTypeNotAllowed', async () => {
    // Arrange
    const file = new File(['test content'], 'document.pdf', { type: 'application/pdf' });
    const onUploadError = vi.fn();

    const { result } = renderHook(() =>
      useFileUpload({
        allowedTypes: ['image/png', 'image/jpeg'],
        onUploadError,
      })
    );

    // Act
    let uploadPromise: Promise<string | null>;
    await act(async () => {
      uploadPromise = result.current.uploadFile(file);
    });

    // Assert
    await waitFor(() => {
      expect(result.current.error).toContain('Invalid file type');
      expect(onUploadError).toHaveBeenCalledWith({
        type: 'INVALID_FILE_TYPE',
        message: expect.stringContaining('Invalid file type'),
      });
    });

    const uploadId = await uploadPromise!;
    expect(uploadId).toBeNull();
    expect(mockApiClient.post).not.toHaveBeenCalled();
  });

  it('should_rejectLargeFile_when_fileSizeExceedsLimit', async () => {
    // Arrange
    const largeContent = new Array(6 * 1024 * 1024).fill('a').join(''); // 6 MB
    const file = new File([largeContent], 'large-logo.png', { type: 'image/png' });
    const onUploadError = vi.fn();

    const { result } = renderHook(() =>
      useFileUpload({
        maxFileSize: 5 * 1024 * 1024, // 5 MB limit
        onUploadError,
      })
    );

    // Act
    let uploadPromise: Promise<string | null>;
    await act(async () => {
      uploadPromise = result.current.uploadFile(file);
    });

    // Assert
    await waitFor(() => {
      expect(result.current.error).toContain('File size must be less than');
      expect(onUploadError).toHaveBeenCalledWith({
        type: 'FILE_TOO_LARGE',
        message: expect.stringContaining('File size must be less than'),
      });
    });

    const uploadId = await uploadPromise!;
    expect(uploadId).toBeNull();
    expect(mockApiClient.post).not.toHaveBeenCalled();
  });

  // ============ Error Handling Tests ============

  it('should_handlePresignedUrlError_when_backendFails', async () => {
    // Arrange
    const file = new File(['test content'], 'test-logo.png', { type: 'image/png' });
    mockApiClient.post.mockRejectedValueOnce(new Error('Backend error'));

    const onUploadError = vi.fn();
    const { result } = renderHook(() =>
      useFileUpload({
        onUploadError,
      })
    );

    // Act
    let uploadPromise: Promise<string | null>;
    await act(async () => {
      uploadPromise = result.current.uploadFile(file);
    });

    // Assert
    await waitFor(() => {
      expect(result.current.error).toContain('Backend error');
      expect(result.current.isUploading).toBe(false);
      expect(onUploadError).toHaveBeenCalledWith({
        type: 'UPLOAD_FAILED',
        message: 'Backend error',
      });
    });

    const uploadId = await uploadPromise!;
    expect(uploadId).toBeNull();
  });

  it('should_handleS3UploadError_when_uploadFails', async () => {
    // Arrange
    const file = new File(['test content'], 'test-logo.png', { type: 'image/png' });
    const mockPresignedResponse = {
      data: {
        uploadUrl: 'https://s3.amazonaws.com/bucket/test.png',
        fileId: 'upload-789',
        s3Key: 'logos/test.png',
        fileExtension: 'png',
        expiresInMinutes: 15,
        requiredHeaders: {},
      },
    };

    mockApiClient.post.mockResolvedValueOnce(mockPresignedResponse);

    const onUploadError = vi.fn();
    const { result } = renderHook(() =>
      useFileUpload({
        onUploadError,
      })
    );

    // Act
    act(() => {
      result.current.uploadFile(file);
    });

    // Wait for presigned URL
    await waitFor(() => {
      expect(result.current.isUploading).toBe(true);
    });

    // Simulate S3 error
    await act(async () => {
      const mockFn = mockXHR.addEventListener as ReturnType<typeof vi.fn>;
      const errorListener = mockFn.mock.calls.find(
        (call: unknown[]) => call[0] === 'error'
      )?.[1] as (() => void) | undefined;
      if (errorListener) {
        errorListener();
      }
    });

    // Assert
    await waitFor(() => {
      expect(result.current.error).toContain('S3 upload failed');
      expect(result.current.isUploading).toBe(false);
      expect(onUploadError).toHaveBeenCalledWith({
        type: 'UPLOAD_FAILED',
        message: 'S3 upload failed',
      });
    });
  });

  it('should_handleConfirmError_when_confirmationFails', async () => {
    // Arrange
    const file = new File(['test content'], 'test-logo.png', { type: 'image/png' });
    const mockPresignedResponse = {
      data: {
        uploadUrl: 'https://s3.amazonaws.com/bucket/test.png',
        fileId: 'upload-999',
        s3Key: 'logos/test.png',
        fileExtension: 'png',
        expiresInMinutes: 15,
        requiredHeaders: {},
      },
    };

    mockApiClient.post
      .mockResolvedValueOnce(mockPresignedResponse)
      .mockRejectedValueOnce(new Error('Confirm failed'));

    const onUploadError = vi.fn();
    const { result } = renderHook(() =>
      useFileUpload({
        onUploadError,
      })
    );

    // Act
    act(() => {
      result.current.uploadFile(file);
    });

    // Wait and simulate S3 success
    await waitFor(() => {
      expect(result.current.isUploading).toBe(true);
    });

    await act(async () => {
      const mockFn = mockXHR.addEventListener as ReturnType<typeof vi.fn>;
      const loadListener = mockFn.mock.calls.find((call: unknown[]) => call[0] === 'load')?.[1] as
        | (() => void)
        | undefined;
      if (loadListener) {
        loadListener();
      }
    });

    // Assert
    await waitFor(() => {
      expect(result.current.error).toContain('Confirm failed');
      expect(result.current.isUploading).toBe(false);
      expect(onUploadError).toHaveBeenCalledWith({
        type: 'UPLOAD_FAILED',
        message: 'Confirm failed',
      });
    });
  });

  // ============ Reset Tests ============

  it('should_resetState_when_resetCalled', async () => {
    // Arrange
    const file = new File(['test content'], 'invalid.pdf', { type: 'application/pdf' });
    const { result } = renderHook(() => useFileUpload());

    // Upload with invalid file to set error state
    await act(async () => {
      await result.current.uploadFile(file);
    });

    // Verify error state
    await waitFor(() => {
      expect(result.current.error).not.toBeNull();
    });

    // Act - Reset state
    act(() => {
      result.current.reset();
    });

    // Assert
    expect(result.current.isUploading).toBe(false);
    expect(result.current.uploadProgress).toBe(0);
    expect(result.current.error).toBeNull();
  });

  // ============ Options Tests ============

  it('should_useCustomOptions_when_optionsProvided', () => {
    // Arrange
    const customMaxSize = 10 * 1024 * 1024; // 10 MB
    const customAllowedTypes = ['image/png'];
    const onSuccess = vi.fn();
    const onError = vi.fn();

    // Act
    const { result } = renderHook(() =>
      useFileUpload({
        maxFileSize: customMaxSize,
        allowedTypes: customAllowedTypes,
        onUploadSuccess: onSuccess,
        onUploadError: onError,
      })
    );

    // Assert - hooks should be initialized (exact validation happens during upload)
    expect(result.current.uploadFile).toBeDefined();
    expect(result.current.isUploading).toBe(false);
  });

  it('should_sendRequiredHeaders_when_uploadingToS3', async () => {
    // Arrange
    const file = new File(['test content'], 'test-logo.png', { type: 'image/png' });
    const mockPresignedResponse = {
      data: {
        uploadUrl: 'https://s3.amazonaws.com/bucket/test.png',
        fileId: 'upload-101',
        s3Key: 'logos/test.png',
        fileExtension: 'png',
        expiresInMinutes: 15,
        requiredHeaders: {
          'Content-Type': 'image/png',
          'x-amz-acl': 'private',
        },
      },
    };

    mockApiClient.post
      .mockResolvedValueOnce(mockPresignedResponse)
      .mockResolvedValueOnce({ data: {} });

    const { result } = renderHook(() => useFileUpload());

    // Act
    act(() => {
      result.current.uploadFile(file);
    });

    // Wait for presigned URL
    await waitFor(() => {
      expect(mockXHR.open).toHaveBeenCalledWith('PUT', mockPresignedResponse.data.uploadUrl);
    });

    // Simulate upload completion
    await act(async () => {
      const mockFn = mockXHR.addEventListener as ReturnType<typeof vi.fn>;
      const loadListener = mockFn.mock.calls.find((call: unknown[]) => call[0] === 'load')?.[1] as
        | (() => void)
        | undefined;
      if (loadListener) {
        loadListener();
      }
    });

    // Assert - verify headers were set
    await waitFor(() => {
      expect(mockXHR.setRequestHeader).toHaveBeenCalledWith('Content-Type', 'image/png');
      expect(mockXHR.setRequestHeader).toHaveBeenCalledWith('x-amz-acl', 'private');
    });
  });
});
