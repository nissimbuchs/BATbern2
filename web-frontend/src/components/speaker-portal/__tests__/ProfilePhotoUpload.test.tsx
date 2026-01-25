/**
 * ProfilePhotoUpload Component Tests
 * Story 6.2b: Speaker Profile Update Portal - AC7 (Profile Photo Upload)
 *
 * Tests for:
 * - Drag-and-drop support (AC7.1)
 * - Click to browse (AC7.2)
 * - File type validation - JPEG, PNG, WebP (AC7.3)
 * - File size validation - max 5MB (AC7.4)
 * - Image preview after selection (AC7.5)
 * - Upload progress indicator (AC7.7)
 * - Photo updates after upload (AC7.8)
 */

import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import ProfilePhotoUpload from '../ProfilePhotoUpload';

// Mock the speakerPortalService
vi.mock('@/services/speakerPortalService', () => ({
  speakerPortalService: {
    uploadProfilePhoto: vi.fn(),
  },
}));

import { speakerPortalService } from '@/services/speakerPortalService';

const mockUploadProfilePhoto = vi.mocked(speakerPortalService.uploadProfilePhoto);

describe('ProfilePhotoUpload Component', () => {
  const defaultProps = {
    token: 'test-token-123',
    currentPhotoUrl: null as string | null,
    onPhotoUploaded: vi.fn(),
    onError: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  // ============ Display Tests ============

  describe('Display and Initial State', () => {
    it('should_displayDropzone_when_noPhotoExists', () => {
      render(<ProfilePhotoUpload {...defaultProps} />);

      expect(screen.getByTestId('photo-dropzone')).toBeInTheDocument();
      expect(screen.getByText(/drag and drop/i)).toBeInTheDocument();
      expect(screen.getByText(/or click to browse/i)).toBeInTheDocument();
    });

    it('should_displayCurrentPhoto_when_currentPhotoUrlProvided', () => {
      render(
        <ProfilePhotoUpload
          {...defaultProps}
          currentPhotoUrl="https://cdn.batbern.ch/speakers/photo.jpg"
        />
      );

      const image = screen.getByAltText(/profile photo/i);
      expect(image).toBeInTheDocument();
      expect(image).toHaveAttribute('src', 'https://cdn.batbern.ch/speakers/photo.jpg');
    });

    it('should_displayChangePhotoButton_when_photoExists', () => {
      render(
        <ProfilePhotoUpload
          {...defaultProps}
          currentPhotoUrl="https://cdn.batbern.ch/speakers/photo.jpg"
        />
      );

      expect(screen.getByRole('button', { name: /change photo/i })).toBeInTheDocument();
    });

    it('should_displayAcceptedFormatsHint_when_dropzoneVisible', () => {
      render(<ProfilePhotoUpload {...defaultProps} />);

      expect(screen.getByText(/jpeg, png, webp/i)).toBeInTheDocument();
      expect(screen.getByText(/max 5mb/i)).toBeInTheDocument();
    });
  });

  // ============ File Type Validation Tests (AC7.3) ============

  describe('File Type Validation (AC7.3)', () => {
    it('should_acceptJPEGFiles_when_jpegProvided', async () => {
      const user = userEvent.setup();
      mockUploadProfilePhoto.mockResolvedValue('https://cdn.batbern.ch/new-photo.jpg');

      render(<ProfilePhotoUpload {...defaultProps} />);

      const file = new File(['image content'], 'photo.jpg', { type: 'image/jpeg' });
      const dropzone = screen.getByTestId('photo-dropzone');
      const input = dropzone.querySelector('input[type="file"]') as HTMLInputElement;

      await user.upload(input, file);

      await waitFor(() => {
        expect(mockUploadProfilePhoto).toHaveBeenCalledWith(
          'test-token-123',
          file,
          expect.any(Function)
        );
      });
    });

    it('should_acceptPNGFiles_when_pngProvided', async () => {
      const user = userEvent.setup();
      mockUploadProfilePhoto.mockResolvedValue('https://cdn.batbern.ch/new-photo.png');

      render(<ProfilePhotoUpload {...defaultProps} />);

      const file = new File(['image content'], 'photo.png', { type: 'image/png' });
      const dropzone = screen.getByTestId('photo-dropzone');
      const input = dropzone.querySelector('input[type="file"]') as HTMLInputElement;

      await user.upload(input, file);

      await waitFor(() => {
        expect(mockUploadProfilePhoto).toHaveBeenCalled();
      });
    });

    it('should_acceptWebPFiles_when_webpProvided', async () => {
      const user = userEvent.setup();
      mockUploadProfilePhoto.mockResolvedValue('https://cdn.batbern.ch/new-photo.webp');

      render(<ProfilePhotoUpload {...defaultProps} />);

      const file = new File(['image content'], 'photo.webp', { type: 'image/webp' });
      const dropzone = screen.getByTestId('photo-dropzone');
      const input = dropzone.querySelector('input[type="file"]') as HTMLInputElement;

      await user.upload(input, file);

      await waitFor(() => {
        expect(mockUploadProfilePhoto).toHaveBeenCalled();
      });
    });

    it('should_rejectGIFFiles_when_gifProvided', async () => {
      render(<ProfilePhotoUpload {...defaultProps} />);

      const file = new File(['image content'], 'photo.gif', { type: 'image/gif' });
      const dropzone = screen.getByTestId('photo-dropzone');

      // Use drag-and-drop which bypasses the accept attribute
      fireEvent.drop(dropzone, {
        dataTransfer: {
          files: [file],
        },
      });

      await waitFor(() => {
        expect(defaultProps.onError).toHaveBeenCalledWith(
          expect.objectContaining({
            message: expect.stringContaining('Invalid file type'),
          })
        );
      });

      expect(mockUploadProfilePhoto).not.toHaveBeenCalled();
    });

    it('should_rejectPDFFiles_when_pdfProvided', async () => {
      render(<ProfilePhotoUpload {...defaultProps} />);

      const file = new File(['pdf content'], 'document.pdf', { type: 'application/pdf' });
      const dropzone = screen.getByTestId('photo-dropzone');

      // Use drag-and-drop which bypasses the accept attribute
      fireEvent.drop(dropzone, {
        dataTransfer: {
          files: [file],
        },
      });

      await waitFor(() => {
        expect(defaultProps.onError).toHaveBeenCalled();
      });

      expect(mockUploadProfilePhoto).not.toHaveBeenCalled();
    });
  });

  // ============ File Size Validation Tests (AC7.4) ============

  describe('File Size Validation (AC7.4)', () => {
    it('should_acceptFile_when_under5MB', async () => {
      const user = userEvent.setup();
      mockUploadProfilePhoto.mockResolvedValue('https://cdn.batbern.ch/new-photo.jpg');

      render(<ProfilePhotoUpload {...defaultProps} />);

      // Create a 4MB file (under limit)
      const fileContent = new ArrayBuffer(4 * 1024 * 1024);
      const file = new File([fileContent], 'photo.jpg', { type: 'image/jpeg' });
      const dropzone = screen.getByTestId('photo-dropzone');
      const input = dropzone.querySelector('input[type="file"]') as HTMLInputElement;

      await user.upload(input, file);

      await waitFor(() => {
        expect(mockUploadProfilePhoto).toHaveBeenCalled();
      });
    });

    it('should_rejectFile_when_over5MB', async () => {
      const user = userEvent.setup();

      render(<ProfilePhotoUpload {...defaultProps} />);

      // Create a 6MB file (over limit)
      const fileContent = new ArrayBuffer(6 * 1024 * 1024);
      const file = new File([fileContent], 'large-photo.jpg', { type: 'image/jpeg' });
      const dropzone = screen.getByTestId('photo-dropzone');
      const input = dropzone.querySelector('input[type="file"]') as HTMLInputElement;

      await user.upload(input, file);

      await waitFor(() => {
        expect(defaultProps.onError).toHaveBeenCalledWith(
          expect.objectContaining({
            message: expect.stringContaining('5MB'),
          })
        );
      });

      expect(mockUploadProfilePhoto).not.toHaveBeenCalled();
    });
  });

  // ============ Preview Tests (AC7.5) ============

  describe('Image Preview (AC7.5)', () => {
    it('should_displayPreview_when_fileSelected', async () => {
      const user = userEvent.setup();
      mockUploadProfilePhoto.mockImplementation(() => new Promise(() => {})); // Never resolves

      render(<ProfilePhotoUpload {...defaultProps} />);

      const file = new File(['image content'], 'photo.jpg', { type: 'image/jpeg' });
      const dropzone = screen.getByTestId('photo-dropzone');
      const input = dropzone.querySelector('input[type="file"]') as HTMLInputElement;

      // Mock URL.createObjectURL
      const mockObjectUrl = 'blob:http://localhost/preview-123';
      global.URL.createObjectURL = vi.fn().mockReturnValue(mockObjectUrl);

      await user.upload(input, file);

      await waitFor(() => {
        const preview = screen.getByTestId('photo-preview');
        expect(preview).toBeInTheDocument();
        expect(preview).toHaveAttribute('src', mockObjectUrl);
      });
    });
  });

  // ============ Upload Progress Tests (AC7.7) ============

  describe('Upload Progress (AC7.7)', () => {
    it('should_displayProgressBar_when_uploading', async () => {
      const user = userEvent.setup();

      // Mock upload that stays pending
      mockUploadProfilePhoto.mockImplementation(() => new Promise(() => {}));

      render(<ProfilePhotoUpload {...defaultProps} />);

      const file = new File(['image content'], 'photo.jpg', { type: 'image/jpeg' });
      const dropzone = screen.getByTestId('photo-dropzone');
      const input = dropzone.querySelector('input[type="file"]') as HTMLInputElement;

      await user.upload(input, file);

      await waitFor(() => {
        expect(screen.getByRole('progressbar')).toBeInTheDocument();
      });
    });

    it('should_showPreviewInsteadOfDropzone_when_uploading', async () => {
      const user = userEvent.setup();

      // Mock upload that stays pending
      mockUploadProfilePhoto.mockImplementation(() => new Promise(() => {}));

      // Mock URL.createObjectURL
      const mockObjectUrl = 'blob:http://localhost/preview-123';
      global.URL.createObjectURL = vi.fn().mockReturnValue(mockObjectUrl);

      render(<ProfilePhotoUpload {...defaultProps} />);

      const file = new File(['image content'], 'photo.jpg', { type: 'image/jpeg' });
      const dropzone = screen.getByTestId('photo-dropzone');
      const input = dropzone.querySelector('input[type="file"]') as HTMLInputElement;

      await user.upload(input, file);

      await waitFor(() => {
        // During upload, preview is shown instead of dropzone
        expect(screen.getByTestId('photo-preview')).toBeInTheDocument();
        expect(screen.queryByTestId('photo-dropzone')).not.toBeInTheDocument();
      });
    });

    it('should_displayUploadingText_when_uploading', async () => {
      const user = userEvent.setup();
      mockUploadProfilePhoto.mockImplementation(() => new Promise(() => {}));

      render(<ProfilePhotoUpload {...defaultProps} />);

      const file = new File(['image content'], 'photo.jpg', { type: 'image/jpeg' });
      const dropzone = screen.getByTestId('photo-dropzone');
      const input = dropzone.querySelector('input[type="file"]') as HTMLInputElement;

      await user.upload(input, file);

      await waitFor(() => {
        expect(screen.getByText(/uploading/i)).toBeInTheDocument();
      });
    });
  });

  // ============ Upload Success Tests (AC7.8) ============

  describe('Upload Success (AC7.8)', () => {
    it('should_callOnPhotoUploaded_when_uploadSucceeds', async () => {
      const user = userEvent.setup();
      const newPhotoUrl = 'https://cdn.batbern.ch/speakers/new-photo.jpg';
      mockUploadProfilePhoto.mockResolvedValue(newPhotoUrl);

      render(<ProfilePhotoUpload {...defaultProps} />);

      const file = new File(['image content'], 'photo.jpg', { type: 'image/jpeg' });
      const dropzone = screen.getByTestId('photo-dropzone');
      const input = dropzone.querySelector('input[type="file"]') as HTMLInputElement;

      await user.upload(input, file);

      await waitFor(() => {
        expect(defaultProps.onPhotoUploaded).toHaveBeenCalledWith(newPhotoUrl);
      });
    });

    it('should_displayNewPhoto_when_uploadSucceeds', async () => {
      const user = userEvent.setup();
      const newPhotoUrl = 'https://cdn.batbern.ch/speakers/new-photo.jpg';
      mockUploadProfilePhoto.mockResolvedValue(newPhotoUrl);

      const { rerender } = render(<ProfilePhotoUpload {...defaultProps} />);

      const file = new File(['image content'], 'photo.jpg', { type: 'image/jpeg' });
      const dropzone = screen.getByTestId('photo-dropzone');
      const input = dropzone.querySelector('input[type="file"]') as HTMLInputElement;

      await user.upload(input, file);

      await waitFor(() => {
        expect(defaultProps.onPhotoUploaded).toHaveBeenCalledWith(newPhotoUrl);
      });

      // Simulate parent updating the currentPhotoUrl prop
      rerender(<ProfilePhotoUpload {...defaultProps} currentPhotoUrl={newPhotoUrl} />);

      const image = screen.getByAltText(/profile photo/i);
      expect(image).toHaveAttribute('src', newPhotoUrl);
    });

    it('should_hideProgressBar_when_uploadSucceeds', async () => {
      const user = userEvent.setup();
      mockUploadProfilePhoto.mockResolvedValue('https://cdn.batbern.ch/new-photo.jpg');

      render(<ProfilePhotoUpload {...defaultProps} />);

      const file = new File(['image content'], 'photo.jpg', { type: 'image/jpeg' });
      const dropzone = screen.getByTestId('photo-dropzone');
      const input = dropzone.querySelector('input[type="file"]') as HTMLInputElement;

      await user.upload(input, file);

      await waitFor(() => {
        expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
      });
    });
  });

  // ============ Error Handling Tests ============

  describe('Error Handling', () => {
    it('should_callOnError_when_uploadFails', async () => {
      const user = userEvent.setup();
      mockUploadProfilePhoto.mockRejectedValue(new Error('Upload failed'));

      render(<ProfilePhotoUpload {...defaultProps} />);

      const file = new File(['image content'], 'photo.jpg', { type: 'image/jpeg' });
      const dropzone = screen.getByTestId('photo-dropzone');
      const input = dropzone.querySelector('input[type="file"]') as HTMLInputElement;

      await user.upload(input, file);

      await waitFor(() => {
        expect(defaultProps.onError).toHaveBeenCalledWith(
          expect.objectContaining({
            message: expect.stringContaining('Upload failed'),
          })
        );
      });
    });

    it('should_displayErrorMessage_when_uploadFails', async () => {
      const user = userEvent.setup();
      mockUploadProfilePhoto.mockRejectedValue(new Error('Network error'));

      render(<ProfilePhotoUpload {...defaultProps} />);

      const file = new File(['image content'], 'photo.jpg', { type: 'image/jpeg' });
      const dropzone = screen.getByTestId('photo-dropzone');
      const input = dropzone.querySelector('input[type="file"]') as HTMLInputElement;

      await user.upload(input, file);

      await waitFor(() => {
        expect(screen.getByRole('alert')).toBeInTheDocument();
      });
    });

    it('should_resetState_when_retryAfterError', async () => {
      const user = userEvent.setup();

      // First upload fails
      mockUploadProfilePhoto.mockRejectedValueOnce(new Error('First upload failed'));
      // Second upload succeeds
      mockUploadProfilePhoto.mockResolvedValueOnce('https://cdn.batbern.ch/new-photo.jpg');

      render(<ProfilePhotoUpload {...defaultProps} />);

      const file1 = new File(['content1'], 'photo1.jpg', { type: 'image/jpeg' });
      const dropzone = screen.getByTestId('photo-dropzone');
      const input = dropzone.querySelector('input[type="file"]') as HTMLInputElement;

      // First upload - fails
      await user.upload(input, file1);

      await waitFor(() => {
        expect(screen.getByRole('alert')).toBeInTheDocument();
      });

      // Get the input again since DOM may have changed
      const dropzoneAfterError = screen.getByTestId('photo-dropzone');
      const inputAfterError = dropzoneAfterError.querySelector(
        'input[type="file"]'
      ) as HTMLInputElement;

      // Second upload - succeeds
      const file2 = new File(['content2'], 'photo2.jpg', { type: 'image/jpeg' });
      await user.upload(inputAfterError, file2);

      await waitFor(() => {
        expect(defaultProps.onPhotoUploaded).toHaveBeenCalledWith(
          'https://cdn.batbern.ch/new-photo.jpg'
        );
      });

      // After success, error should be cleared (check that callback was called successfully)
      expect(defaultProps.onPhotoUploaded).toHaveBeenCalledTimes(1);
    });
  });

  // ============ Drag and Drop Tests (AC7.1) ============

  describe('Drag and Drop (AC7.1)', () => {
    it('should_highlightDropzone_when_draggingOver', () => {
      render(<ProfilePhotoUpload {...defaultProps} />);

      const dropzone = screen.getByTestId('photo-dropzone');

      fireEvent.dragEnter(dropzone);

      expect(dropzone).toHaveClass('border-blue-500');
    });

    it('should_removeHighlight_when_dragLeaves', () => {
      render(<ProfilePhotoUpload {...defaultProps} />);

      const dropzone = screen.getByTestId('photo-dropzone');

      fireEvent.dragEnter(dropzone);
      fireEvent.dragLeave(dropzone);

      expect(dropzone).not.toHaveClass('border-blue-500');
    });
  });

  // ============ Accessibility Tests ============

  describe('Accessibility', () => {
    it('should_haveAccessibleDropzone_when_rendered', () => {
      render(<ProfilePhotoUpload {...defaultProps} />);

      const dropzone = screen.getByTestId('photo-dropzone');
      expect(dropzone).toHaveAttribute('role', 'button');
      expect(dropzone).toHaveAttribute('tabIndex', '0');
    });

    it('should_haveAccessibleImagePreview_when_photoExists', () => {
      render(
        <ProfilePhotoUpload {...defaultProps} currentPhotoUrl="https://cdn.batbern.ch/photo.jpg" />
      );

      const image = screen.getByAltText(/profile photo/i);
      expect(image).toBeInTheDocument();
    });

    it('should_announceUploadStatus_when_uploading', async () => {
      const user = userEvent.setup();
      mockUploadProfilePhoto.mockImplementation(() => new Promise(() => {}));

      // Mock URL.createObjectURL
      const mockObjectUrl = 'blob:http://localhost/preview-123';
      global.URL.createObjectURL = vi.fn().mockReturnValue(mockObjectUrl);

      render(<ProfilePhotoUpload {...defaultProps} />);

      const file = new File(['content'], 'photo.jpg', { type: 'image/jpeg' });
      const dropzone = screen.getByTestId('photo-dropzone');
      const input = dropzone.querySelector('input[type="file"]') as HTMLInputElement;

      await user.upload(input, file);

      await waitFor(() => {
        // The progress section has role="status" and aria-live="polite"
        const status = screen.getByRole('status');
        expect(status).toBeInTheDocument();
        expect(status).toHaveAttribute('aria-live', 'polite');
      });
    });
  });

  // ============ Change Photo Flow ============

  describe('Change Photo Flow', () => {
    it('should_showDropzone_when_changePhotoClicked', async () => {
      const user = userEvent.setup();

      render(
        <ProfilePhotoUpload
          {...defaultProps}
          currentPhotoUrl="https://cdn.batbern.ch/current-photo.jpg"
        />
      );

      const changeButton = screen.getByRole('button', { name: /change photo/i });
      await user.click(changeButton);

      expect(screen.getByTestId('photo-dropzone')).toBeInTheDocument();
    });

    it('should_allowCancellingPhotoChange_when_dropzoneShown', async () => {
      const user = userEvent.setup();

      render(
        <ProfilePhotoUpload
          {...defaultProps}
          currentPhotoUrl="https://cdn.batbern.ch/current-photo.jpg"
        />
      );

      // Click change photo
      const changeButton = screen.getByRole('button', { name: /change photo/i });
      await user.click(changeButton);

      // Click cancel
      const cancelButton = screen.getByRole('button', { name: /cancel/i });
      await user.click(cancelButton);

      // Should show original photo again
      const image = screen.getByAltText(/profile photo/i);
      expect(image).toHaveAttribute('src', 'https://cdn.batbern.ch/current-photo.jpg');
    });
  });
});
