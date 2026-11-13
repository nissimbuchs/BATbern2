/**
 * useFileUpload Hook Tests
 * Story 1.16.3, the upload contract since Story 17.11: presign → S3 PUT → confirm.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { HttpResponse } from 'msw';
import { useFileUpload } from './useFileUpload';
import { uploadTarget } from '@/services/uploadService';
import { api } from '@/test/msw/api';

// The backend answers through the MSW harness (checked against uploads-api); the real upload
// targets and apiClient run. The S3 PUT goes through XMLHttpRequest, replaced per test below.
const logoUploadTarget = uploadTarget('company-logo');

/** The presign answer of the upload contract for a slot in the hook's shape. */
function answerPresign(slot: {
  uploadUrl: string;
  fileId: string;
  requiredHeaders: Record<string, string>;
}) {
  api.on(
    'createFileUpload',
    () => ({
      uploadId: slot.fileId,
      uploadUrl: slot.uploadUrl,
      expiresAt: '2026-11-12T10:15:00Z',
      requiredHeaders: slot.requiredHeaders,
    }),
    { status: 201 }
  );
}

/** A confirm without target: CONFIRMED, no file URL yet. */
function answerConfirm(fileUrl: string | null = null) {
  api.on('confirmFileUpload', ({ params }) => ({
    uploadId: params.uploadId,
    purpose: 'company-logo' as const,
    status: fileUrl ? ('ASSOCIATED' as const) : ('CONFIRMED' as const),
    fileUrl,
  }));
}

describe('useFileUpload Hook', () => {
  let mockXHR: Partial<XMLHttpRequest>;
  let xhrInstance: Partial<XMLHttpRequest>;

  // The XMLHttpRequest of the harness (MSW, patched in at server start), for the backend calls
  let HarnessXHR: typeof XMLHttpRequest;

  beforeEach(() => {
    vi.clearAllMocks();
    HarnessXHR = global.XMLHttpRequest;
    // jsdom has no object URLs (a browser API): the preview of a chosen file
    URL.createObjectURL = vi.fn(() => 'blob:preview');

    // Mock XMLHttpRequest
    mockXHR = {
      open: vi.fn(),
      send: vi.fn(),
      setRequestHeader: vi.fn(),
      upload: {
        addEventListener: vi.fn(),
      } as unknown as XMLHttpRequestUpload,
      addEventListener: vi.fn(),
      status: 200,
    };

    xhrInstance = mockXHR;
    // Vitest v4 requires constructor mocks to use 'function' or 'class'
    // Only the S3 PUT of the hook is replaced: a request axios opens (the backend calls) gets the
    // harness's XMLHttpRequest, so MSW answers it and checks it against the spec.
    global.XMLHttpRequest = vi.fn(function MockXMLHttpRequest(this: XMLHttpRequest) {
      if ((new Error().stack ?? '').includes('/axios/')) {
        return new HarnessXHR();
      }
      return Object.assign(this, xhrInstance);
    }) as unknown as typeof XMLHttpRequest;
  });

  afterEach(() => {
    global.XMLHttpRequest = HarnessXHR;
    vi.restoreAllMocks();
  });

  // ============ AC1: Generate Presigned URL Tests ============

  it('should_initializeWithDefaultState_when_hookCreated', () => {
    // Arrange & Act
    const { result } = renderHook(() => useFileUpload({ target: logoUploadTarget }));

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

    answerPresign(mockPresignedResponse.data); // Phase 1: presigned URL
    answerConfirm(); // Phase 3: confirm

    const onUploadSuccess = vi.fn();
    const { result } = renderHook(() =>
      useFileUpload({
        target: logoUploadTarget,
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
      expect(api.calls('createFileUpload')).toHaveLength(1);
    });
    expect(api.calls('createFileUpload')[0].body).toEqual({
      fileName: 'test-logo.png',
      contentType: 'image/png',
      size: file.size,
    });

    // Simulate S3 upload completion
    await act(async () => {
      const mockFn = mockXHR.addEventListener as ReturnType<typeof vi.fn>;
      const loadListener = mockFn.mock.calls.find((call: unknown[]) => call[0] === 'load')?.[1] as
        (() => void) | undefined;
      if (loadListener) {
        loadListener();
      }
    });

    // Wait for confirm request
    await waitFor(() => {
      expect(api.calls('confirmFileUpload')).toHaveLength(1);
    });
    expect(api.calls('confirmFileUpload')[0].params).toEqual({
      purpose: 'company-logo',
      uploadId: 'upload-123',
    });
    expect(api.calls('confirmFileUpload')[0].body).toEqual({});

    // Assert
    await waitFor(() => {
      expect(result.current.isUploading).toBe(false);
      expect(result.current.uploadProgress).toBe(100);
      expect(result.current.error).toBeNull();
      // No hard-coded bucket any more: a local preview of the chosen file
      expect(onUploadSuccess).toHaveBeenCalledWith({
        uploadId: 'upload-123',
        tempFileUrl: 'blob:preview',
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

    answerPresign(mockPresignedResponse.data);
    answerConfirm();

    const { result } = renderHook(() => useFileUpload({ target: logoUploadTarget }));

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
        (() => void) | undefined;
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
        target: logoUploadTarget,
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
    expect(api.allCalls()).toHaveLength(0);
  });

  it('should_rejectLargeFile_when_fileSizeExceedsLimit', async () => {
    // Arrange
    const largeContent = new Array(6 * 1024 * 1024).fill('a').join(''); // 6 MB
    const file = new File([largeContent], 'large-logo.png', { type: 'image/png' });
    const onUploadError = vi.fn();

    const { result } = renderHook(() =>
      useFileUpload({
        target: logoUploadTarget,
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
    expect(api.allCalls()).toHaveLength(0);
  });

  // ============ Error Handling Tests ============

  it('should_handlePresignedUrlError_when_backendFails', async () => {
    // Arrange
    const file = new File(['test content'], 'test-logo.png', { type: 'image/png' });
    api.on('createFileUpload', () =>
      HttpResponse.json({ message: 'Backend error' }, { status: 500 })
    );

    const onUploadError = vi.fn();
    const { result } = renderHook(() =>
      useFileUpload({
        target: logoUploadTarget,
        onUploadError,
      })
    );

    // Act
    let uploadPromise: Promise<string | null>;
    await act(async () => {
      uploadPromise = result.current.uploadFile(file);
    });

    // Assert — Phase 1 failure is now tagged BACKEND_PRESIGN_FAILED and the
    // message prefixes with "Could not request upload URL:" so the user knows
    // it's the backend (not S3) that failed.
    await waitFor(() => {
      expect(result.current.error).toContain('Could not request upload URL');
      expect(result.current.error).toContain('Backend error');
      expect(result.current.isUploading).toBe(false);
      expect(onUploadError).toHaveBeenCalledWith({
        type: 'BACKEND_PRESIGN_FAILED',
        message: expect.stringContaining('Backend error'),
      });
    });

    const uploadId = await uploadPromise!;
    expect(uploadId).toBeNull();
  });

  it('should_handleS3NetworkError_when_putNeverReachesS3', async () => {
    // Arrange — this models Vanessa's failure case: the XHR PUT fires the
    // 'error' event before any bytes reach S3 (CORS / network / blocker /
    // VPN / offline). The new hook surfaces the upload ID and actionable
    // hints (DevTools Network tab, common causes).
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

    answerPresign(mockPresignedResponse.data);

    const onUploadError = vi.fn();
    const { result } = renderHook(() =>
      useFileUpload({
        target: logoUploadTarget,
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

    // Simulate the XHR 'error' event (no response from S3)
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
      expect(result.current.error).toContain('could not reach S3');
      expect(result.current.error).toContain('upload-789'); // upload ID surfaced for support
      expect(result.current.isUploading).toBe(false);
      expect(onUploadError).toHaveBeenCalledWith({
        type: 'S3_NETWORK_ERROR',
        message: expect.stringContaining('could not reach S3'),
      });
    });
  });

  it('should_handleS3Rejected_when_putReturnsNon2xx', async () => {
    // Arrange — S3 itself responds with a non-2xx status (e.g., 403 AccessDenied,
    // 400 EntityTooLarge, signature mismatch). New hook now extracts the response
    // body so the actual S3 reason makes it to the user.
    const file = new File(['test content'], 'test-logo.png', { type: 'image/png' });
    const mockPresignedResponse = {
      data: {
        uploadUrl: 'https://s3.amazonaws.com/bucket/test.png',
        fileId: 'upload-403',
        s3Key: 'logos/test.png',
        fileExtension: 'png',
        expiresInMinutes: 15,
        requiredHeaders: {},
      },
    };

    answerPresign(mockPresignedResponse.data);

    // The XHR constructor in the test harness copies mockXHR's props onto the
    // new instance once, at construction time — so we need the 403 state set
    // BEFORE the hook calls new XMLHttpRequest(), not after.
    Object.assign(mockXHR, {
      status: 403,
      statusText: 'Forbidden',
      responseText:
        '<?xml version="1.0"?><Error><Code>AccessDenied</Code><Message>Request has expired</Message></Error>',
    });

    const onUploadError = vi.fn();
    const { result } = renderHook(() =>
      useFileUpload({
        target: logoUploadTarget,
        onUploadError,
      })
    );

    act(() => {
      result.current.uploadFile(file);
    });
    await waitFor(() => {
      expect(result.current.isUploading).toBe(true);
    });

    // Fire 'load' — handler will see status=403 and pull responseText.
    await act(async () => {
      const mockFn = mockXHR.addEventListener as ReturnType<typeof vi.fn>;
      const loadListener = mockFn.mock.calls.find((call: unknown[]) => call[0] === 'load')?.[1] as
        (() => void) | undefined;
      if (loadListener) {
        loadListener();
      }
    });

    await waitFor(() => {
      expect(result.current.error).toContain('HTTP 403 Forbidden');
      expect(result.current.error).toContain('AccessDenied');
      expect(result.current.error).toContain('upload-403');
      expect(onUploadError).toHaveBeenCalledWith({
        type: 'S3_REJECTED',
        message: expect.stringContaining('HTTP 403'),
      });
    });
  });

  it('should_handleS3Timeout_when_putTimesOut', async () => {
    // Arrange — XHR fires its 'timeout' event after xhr.timeout ms.
    const file = new File(['test content'], 'test-logo.png', { type: 'image/png' });
    answerPresign({
      uploadUrl: 'https://s3.amazonaws.com/bucket/test.png',
      fileId: 'upload-timeout',
      requiredHeaders: {},
    });

    const onUploadError = vi.fn();
    const { result } = renderHook(() => useFileUpload({ target: logoUploadTarget, onUploadError }));

    act(() => {
      result.current.uploadFile(file);
    });
    await waitFor(() => {
      expect(result.current.isUploading).toBe(true);
    });

    await act(async () => {
      const mockFn = mockXHR.addEventListener as ReturnType<typeof vi.fn>;
      const timeoutListener = mockFn.mock.calls.find(
        (call: unknown[]) => call[0] === 'timeout'
      )?.[1] as (() => void) | undefined;
      if (timeoutListener) {
        timeoutListener();
      }
    });

    await waitFor(() => {
      expect(result.current.error).toContain('timed out');
      expect(result.current.error).toContain('upload-timeout');
      expect(onUploadError).toHaveBeenCalledWith({
        type: 'S3_TIMEOUT',
        message: expect.stringContaining('timed out'),
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

    answerPresign(mockPresignedResponse.data);
    api.on('confirmFileUpload', () =>
      HttpResponse.json({ message: 'Confirm failed' }, { status: 500 })
    );

    const onUploadError = vi.fn();
    const { result } = renderHook(() =>
      useFileUpload({
        target: logoUploadTarget,
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
        (() => void) | undefined;
      if (loadListener) {
        loadListener();
      }
    });

    // Assert — Phase 3 (confirm) now tagged BACKEND_CONFIRM_FAILED and the
    // message explicitly tells the user the file IS in S3 (only the confirm
    // step failed) — important so they don't re-upload duplicates.
    await waitFor(() => {
      expect(result.current.error).toContain('reached S3 but');
      expect(result.current.error).toContain('Confirm failed');
      expect(result.current.error).toContain('upload-999'); // upload ID
      expect(result.current.isUploading).toBe(false);
      expect(onUploadError).toHaveBeenCalledWith({
        type: 'BACKEND_CONFIRM_FAILED',
        message: expect.stringContaining('Confirm failed'),
      });
    });
  });

  // ============ Reset Tests ============

  it('should_resetState_when_resetCalled', async () => {
    // Arrange
    const file = new File(['test content'], 'invalid.pdf', { type: 'application/pdf' });
    const { result } = renderHook(() => useFileUpload({ target: logoUploadTarget }));

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
        target: logoUploadTarget,
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

    answerPresign(mockPresignedResponse.data);
    answerConfirm();

    const { result } = renderHook(() => useFileUpload({ target: logoUploadTarget }));

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
        (() => void) | undefined;
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

  it('should_reportAttachedUrl_when_confirmAttachesToTarget', async () => {
    const file = new File(['logo'], 'logo.svg', { type: 'image/svg+xml' });
    answerPresign({ uploadUrl: 'https://bucket.example/put', fileId: 'up-t', requiredHeaders: {} });
    answerConfirm('https://cdn.batbern.ch/logos/2026/companies/acme/logo-up-t.svg');
    const onUploadSuccess = vi.fn();
    const { result } = renderHook(() =>
      useFileUpload({
        target: uploadTarget('company-logo', { companyName: 'acme' }),
        onUploadSuccess,
      })
    );

    act(() => {
      void result.current.uploadFile(file);
    });
    await waitFor(() => {
      expect(mockXHR.open).toHaveBeenCalledWith('PUT', 'https://bucket.example/put');
    });
    await act(async () => {
      const mockFn = mockXHR.addEventListener as ReturnType<typeof vi.fn>;
      const loadListener = mockFn.mock.calls.find((call: unknown[]) => call[0] === 'load')?.[1] as
        (() => void) | undefined;
      loadListener?.();
    });

    await waitFor(() => {
      expect(onUploadSuccess).toHaveBeenCalledWith({
        uploadId: 'up-t',
        tempFileUrl: 'https://cdn.batbern.ch/logos/2026/companies/acme/logo-up-t.svg',
      });
    });
    expect(api.calls('confirmFileUpload')[0].body).toEqual({ target: { companyName: 'acme' } });
  });

  it('should_sendTheTwoKeyTarget_when_sessionMaterialTargetGiven', async () => {
    // Story 17.11d: session materials are the upload contract's purpose session-material, with a
    // target of two keys.
    const file = new File(['slides'], 'talk.pdf', { type: 'application/pdf' });
    answerPresign({
      uploadUrl: 'https://s3.amazonaws.com/bucket/talk.pdf',
      fileId: 'mat-1',
      requiredHeaders: { 'Content-Type': 'application/pdf' },
    });
    answerConfirm(
      'https://cdn.batbern.ch/materials/events/BATbern58/sessions/keynote/file-mat-1.pdf'
    );
    const target = uploadTarget('session-material', {
      eventCode: 'BATbern58',
      sessionSlug: 'keynote',
    });

    const { result } = renderHook(() =>
      useFileUpload({ target, allowedTypes: ['application/pdf'] })
    );

    let uploadPromise: Promise<string | null>;
    act(() => {
      uploadPromise = result.current.uploadFile(file);
    });
    await waitFor(() => {
      expect(mockXHR.open).toHaveBeenCalledWith('PUT', 'https://s3.amazonaws.com/bucket/talk.pdf');
    });
    await act(async () => {
      const mockFn = mockXHR.addEventListener as ReturnType<typeof vi.fn>;
      const loadListener = mockFn.mock.calls.find((call: unknown[]) => call[0] === 'load')?.[1] as
        (() => void) | undefined;
      loadListener?.();
    });

    expect(await uploadPromise!).toBe('mat-1');
    expect(api.allCalls().map((c) => c.key)).toEqual(['createFileUpload', 'confirmFileUpload']);
    expect(api.calls('createFileUpload')[0].params).toEqual({ purpose: 'session-material' });
    expect(api.calls('createFileUpload')[0].body).toEqual({
      fileName: 'talk.pdf',
      contentType: 'application/pdf',
      size: file.size,
      target: { eventCode: 'BATbern58', sessionSlug: 'keynote' },
    });
    expect(api.calls('confirmFileUpload')[0].body).toEqual({
      target: { eventCode: 'BATbern58', sessionSlug: 'keynote' },
    });
  });
});
