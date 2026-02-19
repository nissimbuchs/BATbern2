/**
 * PresentationUpload Tests (Story 6.3 AC7)
 *
 * Tests for the presentation file upload component.
 */

import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import PresentationUpload from '../PresentationUpload';
import type { MaterialConfirmResponse } from '@/services/speakerPortalService';

// Mock the speaker portal service
const mockUploadMaterial = vi.fn();

vi.mock('@/services/speakerPortalService', () => ({
  speakerPortalService: {
    uploadMaterial: (...args: unknown[]) => mockUploadMaterial(...args),
  },
}));

describe('PresentationUpload', () => {
  const defaultProps = {
    token: 'valid-token',
    currentMaterialUrl: null,
    currentMaterialName: null,
    onMaterialUploaded: vi.fn(),
    onError: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('AC7.1: Drag-and-drop upload area', () => {
    it('should render dropzone when no material exists', () => {
      render(<PresentationUpload {...defaultProps} />);

      expect(screen.getByTestId('material-dropzone')).toBeInTheDocument();
      expect(screen.getByText(/drag and drop.*presentation/i)).toBeInTheDocument();
    });

    it('should show drag active state on drag enter', () => {
      render(<PresentationUpload {...defaultProps} />);

      const dropzone = screen.getByTestId('material-dropzone');
      fireEvent.dragEnter(dropzone, { dataTransfer: { files: [] } });

      expect(dropzone).toHaveClass('border-blue-500');
    });
  });

  describe('AC7.2: Click to browse', () => {
    it('should open file dialog on click', () => {
      render(<PresentationUpload {...defaultProps} />);

      const dropzone = screen.getByTestId('material-dropzone');
      const fileInput = screen.getByLabelText(/upload presentation/i);
      const clickSpy = vi.spyOn(fileInput, 'click');

      fireEvent.click(dropzone);

      expect(clickSpy).toHaveBeenCalled();
    });
  });

  describe('AC7.3: Accepted formats - PPTX, PDF, KEY', () => {
    it('should accept PPTX files', async () => {
      const mockResponse: MaterialConfirmResponse = {
        materialId: 'mat-123',
        uploadId: 'upload-123',
        fileName: 'presentation.pptx',
        cloudFrontUrl: 'https://cdn.example.com/materials/presentation.pptx',
        materialType: 'PRESENTATION',
        uploadedAt: new Date().toISOString(),
      };
      mockUploadMaterial.mockResolvedValue(mockResponse);

      render(<PresentationUpload {...defaultProps} />);

      const file = new File(['content'], 'presentation.pptx', {
        type: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
      });
      const fileInput = screen.getByLabelText(/upload presentation/i);

      await act(async () => {
        fireEvent.change(fileInput, { target: { files: [file] } });
      });

      await waitFor(() => {
        expect(mockUploadMaterial).toHaveBeenCalled();
      });
    });

    it('should accept PDF files', async () => {
      const mockResponse: MaterialConfirmResponse = {
        materialId: 'mat-123',
        uploadId: 'upload-123',
        fileName: 'presentation.pdf',
        cloudFrontUrl: 'https://cdn.example.com/materials/presentation.pdf',
        materialType: 'PRESENTATION',
        uploadedAt: new Date().toISOString(),
      };
      mockUploadMaterial.mockResolvedValue(mockResponse);

      render(<PresentationUpload {...defaultProps} />);

      const file = new File(['content'], 'presentation.pdf', { type: 'application/pdf' });
      const fileInput = screen.getByLabelText(/upload presentation/i);

      await act(async () => {
        fireEvent.change(fileInput, { target: { files: [file] } });
      });

      await waitFor(() => {
        expect(mockUploadMaterial).toHaveBeenCalled();
      });
    });

    it('should accept KEY files', async () => {
      const mockResponse: MaterialConfirmResponse = {
        materialId: 'mat-123',
        uploadId: 'upload-123',
        fileName: 'presentation.key',
        cloudFrontUrl: 'https://cdn.example.com/materials/presentation.key',
        materialType: 'PRESENTATION',
        uploadedAt: new Date().toISOString(),
      };
      mockUploadMaterial.mockResolvedValue(mockResponse);

      render(<PresentationUpload {...defaultProps} />);

      const file = new File(['content'], 'presentation.key', {
        type: 'application/x-iwork-keynote-sffkey',
      });
      const fileInput = screen.getByLabelText(/upload presentation/i);

      await act(async () => {
        fireEvent.change(fileInput, { target: { files: [file] } });
      });

      await waitFor(() => {
        expect(mockUploadMaterial).toHaveBeenCalled();
      });
    });

    it('should reject unsupported file types', async () => {
      const onError = vi.fn();
      render(<PresentationUpload {...defaultProps} onError={onError} />);

      const file = new File(['content'], 'document.doc', { type: 'application/msword' });
      const fileInput = screen.getByLabelText(/upload presentation/i);

      await act(async () => {
        fireEvent.change(fileInput, { target: { files: [file] } });
      });

      await waitFor(() => {
        expect(screen.getByText(/invalid file type/i)).toBeInTheDocument();
        expect(onError).toHaveBeenCalledWith(
          expect.objectContaining({ type: 'INVALID_FILE_TYPE' })
        );
      });
    });
  });

  describe('AC7.4: 50MB file size limit', () => {
    it('should accept files under 50MB', async () => {
      const mockResponse: MaterialConfirmResponse = {
        materialId: 'mat-123',
        uploadId: 'upload-123',
        fileName: 'large-presentation.pptx',
        cloudFrontUrl: 'https://cdn.example.com/materials/presentation.pptx',
        materialType: 'PRESENTATION',
        uploadedAt: new Date().toISOString(),
      };
      mockUploadMaterial.mockResolvedValue(mockResponse);

      render(<PresentationUpload {...defaultProps} />);

      // Create a mock file of 40MB
      const file = new File(['x'.repeat(40 * 1024 * 1024)], 'large-presentation.pptx', {
        type: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
      });
      // Mock file.size since File constructor doesn't accurately set size for repeated strings
      Object.defineProperty(file, 'size', { value: 40 * 1024 * 1024 });

      const fileInput = screen.getByLabelText(/upload presentation/i);

      await act(async () => {
        fireEvent.change(fileInput, { target: { files: [file] } });
      });

      await waitFor(() => {
        expect(mockUploadMaterial).toHaveBeenCalled();
      });
    });

    it('should reject files over 50MB', async () => {
      const onError = vi.fn();
      render(<PresentationUpload {...defaultProps} onError={onError} />);

      // Create a mock file of 60MB
      const file = new File(['x'], 'huge-presentation.pptx', {
        type: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
      });
      Object.defineProperty(file, 'size', { value: 60 * 1024 * 1024 });

      const fileInput = screen.getByLabelText(/upload presentation/i);

      await act(async () => {
        fireEvent.change(fileInput, { target: { files: [file] } });
      });

      await waitFor(() => {
        expect(screen.getByText(/file size must be less than 50MB/i)).toBeInTheDocument();
        expect(onError).toHaveBeenCalledWith(expect.objectContaining({ type: 'FILE_TOO_LARGE' }));
      });
    });
  });

  describe('AC7.5: Upload progress indicator', () => {
    it('should show progress during upload', async () => {
      let progressCallback: ((progress: number) => void) | undefined;
      mockUploadMaterial.mockImplementation(
        (_token: string, _file: File, onProgress?: (progress: number) => void) => {
          progressCallback = onProgress;
          return new Promise(() => {}); // Never resolves
        }
      );

      render(<PresentationUpload {...defaultProps} />);

      const file = new File(['content'], 'presentation.pptx', {
        type: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
      });
      const fileInput = screen.getByLabelText(/upload presentation/i);

      await act(async () => {
        fireEvent.change(fileInput, { target: { files: [file] } });
      });

      // Simulate progress update
      await act(async () => {
        progressCallback?.(50);
      });

      expect(screen.getByText(/50%/)).toBeInTheDocument();
      // Multiple progressbars may exist (BATbernLoader + upload bar); find the upload one by aria-valuenow
      const uploadBar = screen
        .getAllByRole('progressbar')
        .find((el) => el.hasAttribute('aria-valuenow'));
      expect(uploadBar).toHaveAttribute('aria-valuenow', '50');
    });

    it('should show uploading state', async () => {
      mockUploadMaterial.mockImplementation(() => new Promise(() => {})); // Never resolves

      render(<PresentationUpload {...defaultProps} />);

      const file = new File(['content'], 'presentation.pptx', {
        type: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
      });
      const fileInput = screen.getByLabelText(/upload presentation/i);

      await act(async () => {
        fireEvent.change(fileInput, { target: { files: [file] } });
      });

      expect(screen.getByText(/uploading/i)).toBeInTheDocument();
    });
  });

  describe('File name display', () => {
    it('should show current material when exists', () => {
      render(
        <PresentationUpload
          {...defaultProps}
          currentMaterialUrl="https://cdn.example.com/materials/existing.pptx"
          currentMaterialName="existing.pptx"
        />
      );

      expect(screen.getByText('existing.pptx')).toBeInTheDocument();
      expect(screen.getByRole('link', { name: /existing\.pptx/i })).toHaveAttribute(
        'href',
        'https://cdn.example.com/materials/existing.pptx'
      );
    });

    it('should show change button when material exists', () => {
      render(
        <PresentationUpload
          {...defaultProps}
          currentMaterialUrl="https://cdn.example.com/materials/existing.pptx"
          currentMaterialName="existing.pptx"
        />
      );

      expect(screen.getByText(/change/i)).toBeInTheDocument();
    });
  });

  describe('Upload success', () => {
    it('should call onMaterialUploaded after successful upload', async () => {
      const mockResponse: MaterialConfirmResponse = {
        materialId: 'mat-123',
        uploadId: 'upload-123',
        fileName: 'presentation.pptx',
        cloudFrontUrl: 'https://cdn.example.com/materials/presentation.pptx',
        materialType: 'PRESENTATION',
        uploadedAt: new Date().toISOString(),
      };
      mockUploadMaterial.mockResolvedValue(mockResponse);
      const onMaterialUploaded = vi.fn();

      render(<PresentationUpload {...defaultProps} onMaterialUploaded={onMaterialUploaded} />);

      const file = new File(['content'], 'presentation.pptx', {
        type: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
      });
      const fileInput = screen.getByLabelText(/upload presentation/i);

      await act(async () => {
        fireEvent.change(fileInput, { target: { files: [file] } });
      });

      await waitFor(() => {
        expect(onMaterialUploaded).toHaveBeenCalledWith(
          'https://cdn.example.com/materials/presentation.pptx',
          'presentation.pptx'
        );
      });
    });
  });

  describe('Error handling', () => {
    it('should display error message on upload failure', async () => {
      const onError = vi.fn();
      mockUploadMaterial.mockRejectedValue(new Error('Upload failed'));

      render(<PresentationUpload {...defaultProps} onError={onError} />);

      const file = new File(['content'], 'presentation.pptx', {
        type: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
      });
      const fileInput = screen.getByLabelText(/upload presentation/i);

      await act(async () => {
        fireEvent.change(fileInput, { target: { files: [file] } });
      });

      await waitFor(() => {
        expect(screen.getByText(/upload failed/i)).toBeInTheDocument();
        expect(onError).toHaveBeenCalledWith(expect.objectContaining({ type: 'UPLOAD_FAILED' }));
      });
    });
  });
});
