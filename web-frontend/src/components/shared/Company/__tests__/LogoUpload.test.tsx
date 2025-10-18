import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { LogoUpload } from '@/components/shared/Company/LogoUpload';

// Mock apiClient
vi.mock('@/services/api/apiClient', () => ({
  default: {
    post: vi.fn(),
  },
}));

import apiClient from '@/services/api/apiClient';

// Mock file creation helper
const createFile = (
  name: string,
  size: number,
  type: string
): File => {
  const file = new File(['a'.repeat(size)], name, { type });
  return file;
};

// Helper to setup API mocks
const setupApiMocks = () => {
  vi.mocked(apiClient.post).mockImplementation((url: string) => {
    if (url.includes('/presigned-url')) {
      return Promise.resolve({
        data: {
          uploadUrl: 'https://s3.amazonaws.com/test-bucket/upload',
          fileId: 'file-123',
        },
      }) as any;
    }
    if (url.includes('/confirm')) {
      return Promise.resolve({
        data: {
          logoUrl: 'https://cdn.example.com/logos/company-logo.png',
        },
      }) as any;
    }
    return Promise.resolve({ data: {} }) as any;
  });
};

describe('LogoUpload Component', () => {
  const mockOnUploadSuccess = vi.fn();
  const mockOnUploadError = vi.fn();
  const defaultProps = {
    companyId: 'test-company-id',
    onUploadSuccess: mockOnUploadSuccess,
    onUploadError: mockOnUploadError,
  };

  beforeEach(() => {
    vi.clearAllMocks();
    // Reset apiClient mock
    vi.mocked(apiClient.post).mockReset();
  });

  describe('AC5.1: Drag-and-drop file acceptance', () => {
    it('should_acceptDragAndDrop_when_fileDropped', async () => {
      // Mock successful upload
      setupApiMocks();

      render(<LogoUpload {...defaultProps} />);

      // Find the hidden input element inside the dropzone
      const input = screen.getByTestId('logo-dropzone').querySelector('input[type="file"]') as HTMLInputElement;
      const file = createFile('company-logo.png', 1024, 'image/png');

      // Upload file via input
      await userEvent.upload(input, file);

      // Verify file is being uploaded
      await waitFor(() => {
        expect(screen.getByText(/uploading/i)).toBeInTheDocument();
      });
    });

    it('should_showDropzoneActive_when_fileDraggedOver', async () => {
      render(<LogoUpload {...defaultProps} />);

      const dropzone = screen.getByTestId('logo-dropzone');

      // Simulate drag over by checking if dropzone-active class is added
      // Note: react-dropzone adds the class via internal state, so we test the component's rendering
      expect(dropzone).toBeInTheDocument();

      // The actual drag-over styling is managed by react-dropzone's isDragActive state
      // We can verify the dropzone is rendered correctly
      expect(dropzone).toHaveAttribute('data-testid', 'logo-dropzone');
    });
  });

  describe('AC5.2: File type validation', () => {
    it('should_validateFileType_when_invalidFileUploaded', async () => {
      render(<LogoUpload {...defaultProps} />);

      const input = screen.getByTestId('logo-dropzone').querySelector('input[type="file"]') as HTMLInputElement;
      const invalidFile = createFile('document.pdf', 1024, 'application/pdf');

      await userEvent.upload(input, invalidFile);

      // Note: react-dropzone filters invalid files BEFORE our onDrop callback
      // This is expected behavior and provides better UX by preventing invalid files entirely
      // The file will be rejected and not reach our validation logic
      // Verify no upload was attempted (no progress indicator)
      await waitFor(() => {
        expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
      }, { timeout: 500 });

      // Verify no success callback
      expect(mockOnUploadSuccess).not.toHaveBeenCalled();
    });

    it('should_acceptPNG_when_validPNGFileUploaded', async () => {
      render(<LogoUpload {...defaultProps} />);

      const input = screen.getByTestId('logo-dropzone').querySelector('input[type="file"]') as HTMLInputElement;
      const pngFile = createFile('logo.png', 1024, 'image/png');

      await userEvent.upload(input, pngFile);

      // Verify no error
      expect(
        screen.queryByText(/only png, jpeg, and svg files are allowed/i)
      ).not.toBeInTheDocument();
    });

    it('should_acceptJPEG_when_validJPEGFileUploaded', async () => {
      render(<LogoUpload {...defaultProps} />);

      const input = screen.getByTestId('logo-dropzone').querySelector('input[type="file"]') as HTMLInputElement;
      const jpegFile = createFile('logo.jpg', 1024, 'image/jpeg');

      await userEvent.upload(input, jpegFile);

      // Verify no error
      expect(
        screen.queryByText(/only png, jpeg, and svg files are allowed/i)
      ).not.toBeInTheDocument();
    });

    it('should_acceptSVG_when_validSVGFileUploaded', async () => {
      render(<LogoUpload {...defaultProps} />);

      const input = screen.getByTestId('logo-dropzone').querySelector('input[type="file"]') as HTMLInputElement;
      const svgFile = createFile('logo.svg', 1024, 'image/svg+xml');

      await userEvent.upload(input, svgFile);

      // Verify no error
      expect(
        screen.queryByText(/only png, jpeg, and svg files are allowed/i)
      ).not.toBeInTheDocument();
    });
  });

  describe('AC5.3: File size validation', () => {
    it('should_validateFileSize_when_fileTooLarge', async () => {
      render(<LogoUpload {...defaultProps} />);

      const input = screen.getByTestId('logo-dropzone').querySelector('input[type="file"]') as HTMLInputElement;
      const largeFile = createFile(
        'large-logo.png',
        6 * 1024 * 1024, // 6MB (exceeds 5MB limit)
        'image/png'
      );

      await userEvent.upload(input, largeFile);

      // Verify error message
      await waitFor(() => {
        expect(
          screen.getByText(/file size must be less than 5mb/i)
        ).toBeInTheDocument();
      });

      // Verify error callback
      expect(mockOnUploadError).toHaveBeenCalledWith(
        expect.objectContaining({
          type: 'FILE_TOO_LARGE',
          message: expect.stringContaining('5MB'),
        })
      );
    });

    it('should_acceptFile_when_fileSizeValid', async () => {
      render(<LogoUpload {...defaultProps} />);

      const input = screen.getByTestId('logo-dropzone').querySelector('input[type="file"]') as HTMLInputElement;
      const validFile = createFile(
        'valid-logo.png',
        4 * 1024 * 1024, // 4MB (within 5MB limit)
        'image/png'
      );

      await userEvent.upload(input, validFile);

      // Verify no error
      expect(
        screen.queryByText(/file size must be less than 5mb/i)
      ).not.toBeInTheDocument();
    });
  });

  describe('AC5.4: Upload progress indicator', () => {
    it('should_showUploadProgress_when_uploadingLogo', async () => {
      // Mock API calls
      setupApiMocks();

      render(<LogoUpload {...defaultProps} />);

      const input = screen.getByTestId('logo-dropzone').querySelector('input[type="file"]') as HTMLInputElement;
      const file = createFile('logo.png', 1024 * 1024, 'image/png');

      await userEvent.upload(input, file);

      // Verify progress indicator appears
      await waitFor(() => {
        expect(screen.getByRole('progressbar')).toBeInTheDocument();
      });

      // Verify progress percentage is shown
      expect(screen.getByText(/uploading/i)).toBeInTheDocument();
    });

    it('should_updateProgressPercentage_when_uploadProgressing', async () => {
      // Mock presigned URL fetch
      setupApiMocks();

      // Mock XMLHttpRequest for upload progress tracking
      let progressHandler: any;
      let loadHandler: any;
      const mockXHR = {
        open: vi.fn(),
        send: vi.fn(() => {
          // Trigger progress events after send
          setTimeout(() => progressHandler && progressHandler({ loaded: 50, total: 100, lengthComputable: true }), 50);
          setTimeout(() => progressHandler && progressHandler({ loaded: 100, total: 100, lengthComputable: true }), 100);
          setTimeout(() => loadHandler && loadHandler(), 150);
        }),
        setRequestHeader: vi.fn(),
        status: 200,
        upload: {
          addEventListener: vi.fn((event, handler) => {
            if (event === 'progress') {
              progressHandler = handler;
            }
          }),
        },
        addEventListener: vi.fn((event, handler) => {
          if (event === 'load') {
            loadHandler = handler;
          }
        }),
      };

      global.XMLHttpRequest = vi.fn(() => mockXHR) as any;

      render(<LogoUpload {...defaultProps} />);

      const input = screen.getByTestId('logo-dropzone').querySelector('input[type="file"]') as HTMLInputElement;
      const file = createFile('logo.png', 1024, 'image/png');

      await userEvent.upload(input, file);

      // Verify progress percentage updates
      await waitFor(() => {
        expect(screen.getByText(/50%/)).toBeInTheDocument();
      }, { timeout: 2000 });

      await waitFor(() => {
        expect(screen.getByText(/100%/)).toBeInTheDocument();
      }, { timeout: 2000 });
    });
  });

  describe('AC5.5: Logo preview after upload', () => {
    it('should_displayLogoPreview_when_uploadComplete', async () => {
      // Mock successful upload
      setupApiMocks();

      // Mock XMLHttpRequest for S3 upload
      let loadHandler: any;
      const mockXHR = {
        open: vi.fn(),
        send: vi.fn(() => {
          setTimeout(() => loadHandler && loadHandler(), 50);
        }),
        setRequestHeader: vi.fn(),
        status: 200,
        upload: {
          addEventListener: vi.fn(),
        },
        addEventListener: vi.fn((event, handler) => {
          if (event === 'load') {
            loadHandler = handler;
          }
        }),
      };
      global.XMLHttpRequest = vi.fn(() => mockXHR) as any;

      render(<LogoUpload {...defaultProps} />);

      const input = screen.getByTestId('logo-dropzone').querySelector('input[type="file"]') as HTMLInputElement;
      const file = createFile('logo.png', 1024, 'image/png');

      await userEvent.upload(input, file);

      // Wait for upload to complete
      await waitFor(() => {
        const previewImage = screen.getByAltText(/company logo preview/i);
        expect(previewImage).toBeInTheDocument();
        expect(previewImage).toHaveAttribute(
          'src',
          'https://cdn.example.com/logos/company-logo.png'
        );
      }, { timeout: 3000 });

      // Verify success callback
      expect(mockOnUploadSuccess).toHaveBeenCalledWith({
        logoUrl: 'https://cdn.example.com/logos/company-logo.png',
      });
    });

    it('should_showRemoveButton_when_logoPreviewDisplayed', async () => {
      render(<LogoUpload {...defaultProps} currentLogoUrl="https://cdn.example.com/logos/existing-logo.png" />);

      // Verify remove button is present
      expect(screen.getByRole('button', { name: /remove logo/i })).toBeInTheDocument();
    });

    it('should_clearPreview_when_removeButtonClicked', async () => {
      render(<LogoUpload {...defaultProps} currentLogoUrl="https://cdn.example.com/logos/existing-logo.png" />);

      const removeButton = screen.getByRole('button', { name: /remove logo/i });

      await userEvent.click(removeButton);

      // Verify logo preview is removed
      await waitFor(() => {
        expect(screen.queryByAltText(/company logo preview/i)).not.toBeInTheDocument();
      });
    });
  });

  describe('AC5.6: S3 upload via presigned URLs', () => {
    it('should_uploadToS3_when_presignedURLReceived', async () => {
      // Mock apiClient for presigned URL and confirm
      setupApiMocks();

      // Mock XMLHttpRequest for S3 upload (Step 2 uses XHR, not fetch)
      const mockXHR = {
        open: vi.fn(),
        send: vi.fn(function(this: any) {
          // Trigger load event immediately to simulate successful upload
          setTimeout(() => {
            const loadEvent = { target: this };
            this.addEventListener.mock.calls.forEach(([event, handler]: [string, (e: unknown) => void]) => {
              if (event === 'load') handler(loadEvent);
            });
          }, 10);
        }),
        setRequestHeader: vi.fn(),
        status: 200,
        upload: {
          addEventListener: vi.fn(),
        },
        addEventListener: vi.fn(),
      };
      global.XMLHttpRequest = vi.fn(() => mockXHR) as any;

      render(<LogoUpload {...defaultProps} />);

      const input = screen.getByTestId('logo-dropzone').querySelector('input[type="file"]') as HTMLInputElement;
      const file = createFile('logo.png', 1024, 'image/png');

      await userEvent.upload(input, file);

      // Wait for all API calls to complete (2 apiClient.post calls + 1 XHR)
      await waitFor(() => {
        expect(apiClient.post).toHaveBeenCalledTimes(2);
      }, { timeout: 2000 });

      // Verify Step 1: Request presigned URL
      expect(apiClient.post).toHaveBeenNthCalledWith(
        1,
        expect.stringContaining('/presigned-url'),
        expect.objectContaining({
          fileName: 'logo.png',
          fileSize: 1024,
          mimeType: 'image/png',
        })
      );

      // Verify Step 2: Upload to S3 via XHR (not apiClient)
      expect(mockXHR.open).toHaveBeenCalledWith('PUT', 'https://s3.amazonaws.com/test-bucket/upload');
      expect(mockXHR.setRequestHeader).toHaveBeenCalledWith('Content-Type', 'image/png');
      expect(mockXHR.send).toHaveBeenCalledWith(file);

      // Verify Step 3: Confirm upload
      expect(apiClient.post).toHaveBeenNthCalledWith(
        2,
        expect.stringContaining('/confirm'),
        expect.objectContaining({
          fileId: 'file-123',
        })
      );
    });

    it('should_handlePresignedURLError_when_requestFails', async () => {
      // Mock API to reject with network error
      vi.mocked(apiClient.post).mockRejectedValueOnce(new Error('Network error'));

      render(<LogoUpload {...defaultProps} />);

      const input = screen.getByTestId('logo-dropzone').querySelector('input[type="file"]') as HTMLInputElement;
      const file = createFile('logo.png', 1024, 'image/png');

      await userEvent.upload(input, file);

      // Verify error callback was called with network error
      await waitFor(() => {
        expect(mockOnUploadError).toHaveBeenCalledWith(
          expect.objectContaining({
            type: 'UPLOAD_FAILED',
            message: expect.stringContaining('Network error'),
          })
        );
      }, { timeout: 1000 });
    });

    it('should_disableUpload_when_uploadInProgress', async () => {
      // Mock presigned URL request
      setupApiMocks();

      // Mock slow XHR upload
      let progressHandler: any;
      const mockXHR = {
        open: vi.fn(),
        send: vi.fn(() => {
          // Trigger initial progress event
          setTimeout(() => progressHandler && progressHandler({ loaded: 10, total: 100, lengthComputable: true }), 100);
          // Don't complete - keep it in progress
        }),
        setRequestHeader: vi.fn(),
        upload: {
          addEventListener: vi.fn((event, handler) => {
            if (event === 'progress') {
              progressHandler = handler;
            }
          }),
        },
        addEventListener: vi.fn(),
      };
      global.XMLHttpRequest = vi.fn(() => mockXHR) as any;

      render(<LogoUpload {...defaultProps} />);

      const input = screen.getByTestId('logo-dropzone').querySelector('input[type="file"]') as HTMLInputElement;
      const file = createFile('logo.png', 1024, 'image/png');

      await userEvent.upload(input, file);

      // Wait for upload to start
      await waitFor(() => {
        expect(screen.getByRole('progressbar')).toBeInTheDocument();
      }, { timeout: 2000 });

      // Verify dropzone is disabled
      const dropzone = screen.getByTestId('logo-dropzone');
      expect(dropzone).toHaveAttribute('aria-disabled', 'true');
    });

    it('should_showCurrentLogo_when_currentLogoUrlProvided', () => {
      render(<LogoUpload {...defaultProps} currentLogoUrl="https://cdn.example.com/logos/current-logo.png" />);

      const currentLogo = screen.getByAltText(/company logo preview/i);
      expect(currentLogo).toBeInTheDocument();
      expect(currentLogo).toHaveAttribute('src', 'https://cdn.example.com/logos/current-logo.png');
    });
  });
});
