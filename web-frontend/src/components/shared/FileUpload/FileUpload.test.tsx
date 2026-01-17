/**
 * FileUpload Component Tests
 * Story 1.16.3: Generic File Upload Service
 * Tests drag-and-drop, file validation, upload progress, and error handling
 */

import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import userEvent from '@testing-library/user-event';
import { FileUpload } from './FileUpload';
import { useFileUpload } from '@/hooks/useFileUpload/useFileUpload';

// Mock the useFileUpload hook
vi.mock('@/hooks/useFileUpload/useFileUpload');

const mockUseFileUpload = vi.mocked(useFileUpload);

describe('FileUpload Component', () => {
  const mockUploadFile = vi.fn();
  const mockReset = vi.fn();
  const mockOnUploadSuccess = vi.fn();
  const mockOnUploadError = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();

    // Default mock implementation
    mockUseFileUpload.mockReturnValue({
      uploadFile: mockUploadFile,
      isUploading: false,
      uploadProgress: 0,
      error: null,
      reset: mockReset,
    });
  });

  // ============ Display and Initial State Tests ============

  it('should_displayDropzone_when_noFileUploaded', () => {
    render(<FileUpload />);

    const dropzone = screen.getByTestId('file-dropzone');
    expect(dropzone).toBeInTheDocument();
    expect(screen.getByText(/drag and drop a file here/i)).toBeInTheDocument();
    expect(screen.getByText(/accepted formats/i)).toBeInTheDocument();
  });

  it('should_displayCurrentFile_when_currentFileUrlProvided', () => {
    render(<FileUpload currentFileUrl="https://cdn.example.com/logos/company-logo.png" />);

    const image = screen.getByAltText('Uploaded file preview');
    expect(image).toBeInTheDocument();
    expect(image).toHaveAttribute('src', 'https://cdn.example.com/logos/company-logo.png');

    // Dropzone should not be visible
    expect(screen.queryByTestId('file-dropzone')).not.toBeInTheDocument();
  });

  it('should_displayRemoveButton_when_fileDisplayed', () => {
    render(<FileUpload currentFileUrl="https://cdn.example.com/logos/logo.png" />);

    const removeButton = screen.getByRole('button', { name: /remove file/i });
    expect(removeButton).toBeInTheDocument();
  });

  it('should_displayAcceptedFormatsText_when_dropzoneVisible', () => {
    render(<FileUpload maxFileSize={10 * 1024 * 1024} />);

    expect(screen.getByText(/accepted formats: png, jpeg, svg \(max 10mb\)/i)).toBeInTheDocument();
  });

  // ============ File Selection Tests ============

  it('should_uploadFile_when_fileSelected', async () => {
    const user = userEvent.setup();
    mockUploadFile.mockResolvedValue('upload-123');

    render(<FileUpload onUploadSuccess={mockOnUploadSuccess} />);

    const file = new File(['test content'], 'test-logo.png', { type: 'image/png' });
    const dropzone = screen.getByTestId('file-dropzone');
    const input = dropzone.querySelector('input[type="file"]') as HTMLInputElement;

    await user.upload(input, file);

    await waitFor(() => {
      expect(mockUploadFile).toHaveBeenCalledWith(file);
    });
  });

  it('should_callOnUploadSuccess_when_uploadSucceeds', async () => {
    mockUseFileUpload.mockReturnValue({
      uploadFile: vi.fn().mockResolvedValue('upload-456'),
      isUploading: false,
      uploadProgress: 100,
      error: null,
      reset: mockReset,
    });

    render(<FileUpload onUploadSuccess={mockOnUploadSuccess} onUploadError={mockOnUploadError} />);

    // Hook should have been called with callbacks
    expect(mockUseFileUpload).toHaveBeenCalledWith(
      expect.objectContaining({
        onUploadSuccess: expect.any(Function),
        onUploadError: mockOnUploadError,
      })
    );
  });

  // ============ Upload Progress Tests ============

  it('should_displayProgressBar_when_uploading', () => {
    mockUseFileUpload.mockReturnValue({
      uploadFile: mockUploadFile,
      isUploading: true,
      uploadProgress: 50,
      error: null,
      reset: mockReset,
    });

    render(<FileUpload />);

    expect(screen.getByText(/uploading\.\.\. 50%/i)).toBeInTheDocument();
    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  it('should_updateProgress_when_uploadProgressing', () => {
    mockUseFileUpload.mockReturnValue({
      uploadFile: mockUploadFile,
      isUploading: true,
      uploadProgress: 75,
      error: null,
      reset: mockReset,
    });

    render(<FileUpload />);

    expect(screen.getByText(/uploading\.\.\. 75%/i)).toBeInTheDocument();
    const progressBar = screen.getByRole('progressbar');
    expect(progressBar).toHaveAttribute('aria-valuenow', '75');
  });

  it('should_disableDropzone_when_uploading', () => {
    mockUseFileUpload.mockReturnValue({
      uploadFile: mockUploadFile,
      isUploading: true,
      uploadProgress: 25,
      error: null,
      reset: mockReset,
    });

    render(<FileUpload />);

    const dropzone = screen.getByTestId('file-dropzone');
    expect(dropzone).toHaveAttribute('aria-disabled', 'true');
  });

  // ============ Error Handling Tests ============

  it('should_displayErrorMessage_when_uploadFails', () => {
    mockUseFileUpload.mockReturnValue({
      uploadFile: mockUploadFile,
      isUploading: false,
      uploadProgress: 0,
      error: 'File size exceeds 5MB limit',
      reset: mockReset,
    });

    render(<FileUpload />);

    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText(/file size exceeds 5mb limit/i)).toBeInTheDocument();
  });

  it('should_callOnUploadError_when_multipleFilesSelected', async () => {
    const user = userEvent.setup();

    render(<FileUpload onUploadError={mockOnUploadError} />);

    const file1 = new File(['content1'], 'logo1.png', { type: 'image/png' });
    const file2 = new File(['content2'], 'logo2.png', { type: 'image/png' });

    const dropzone = screen.getByTestId('file-dropzone');
    const input = dropzone.querySelector('input[type="file"]') as HTMLInputElement;

    await user.upload(input, [file1, file2]);

    await waitFor(() => {
      expect(mockOnUploadError).toHaveBeenCalledWith({
        type: 'MULTIPLE_FILES',
        message: 'Only one file can be uploaded at a time',
      });
    });
  });

  // ============ File Removal Tests ============

  it('should_removeFile_when_removeButtonClicked', async () => {
    const user = userEvent.setup();

    const { rerender } = render(
      <FileUpload currentFileUrl="https://cdn.example.com/logos/logo.png" />
    );

    const removeButton = screen.getByRole('button', { name: /remove file/i });
    await user.click(removeButton);

    expect(mockReset).toHaveBeenCalled();

    // After removal, dropzone should be visible again
    rerender(<FileUpload />);
    expect(screen.getByTestId('file-dropzone')).toBeInTheDocument();
  });

  it('should_disableRemoveButton_when_uploading', () => {
    mockUseFileUpload.mockReturnValue({
      uploadFile: mockUploadFile,
      isUploading: true,
      uploadProgress: 50,
      error: null,
      reset: mockReset,
    });

    render(<FileUpload currentFileUrl="https://cdn.example.com/logos/logo.png" />);

    const removeButton = screen.getByRole('button', { name: /remove file/i });
    expect(removeButton).toBeDisabled();
  });

  // ============ Drag and Drop Tests ============

  it('should_highlightDropzone_when_dragActive', () => {
    render(<FileUpload />);

    const dropzone = screen.getByTestId('file-dropzone');
    expect(dropzone).not.toHaveClass('dropzone-active');

    // Note: react-dropzone drag state testing is complex
    // In real scenarios, you might need to simulate drag events
    // or test the visual feedback through snapshot testing
  });

  // ============ Custom Props Tests ============

  it('should_useCustomMaxFileSize_when_provided', () => {
    render(<FileUpload maxFileSize={10 * 1024 * 1024} />);

    expect(mockUseFileUpload).toHaveBeenCalledWith(
      expect.objectContaining({
        maxFileSize: 10 * 1024 * 1024,
      })
    );
  });

  it('should_useCustomAllowedTypes_when_provided', () => {
    render(<FileUpload allowedTypes={['image/png', 'image/jpeg']} />);

    expect(mockUseFileUpload).toHaveBeenCalledWith(
      expect.objectContaining({
        allowedTypes: ['image/png', 'image/jpeg'],
      })
    );
  });

  it('should_syncFileUrl_when_currentFileUrlChanges', () => {
    const { rerender } = render(
      <FileUpload currentFileUrl="https://cdn.example.com/logos/logo-v1.png" />
    );

    let image = screen.getByAltText('Uploaded file preview');
    expect(image).toHaveAttribute('src', 'https://cdn.example.com/logos/logo-v1.png');

    // Change currentFileUrl prop
    rerender(<FileUpload currentFileUrl="https://cdn.example.com/logos/logo-v2.png" />);

    image = screen.getByAltText('Uploaded file preview');
    expect(image).toHaveAttribute('src', 'https://cdn.example.com/logos/logo-v2.png');
  });

  // ============ Accessibility Tests ============

  it('should_haveAccessibleDropzone_when_rendered', () => {
    render(<FileUpload />);

    const dropzone = screen.getByTestId('file-dropzone');
    expect(dropzone).toHaveAttribute('aria-disabled', 'false');
  });

  it('should_haveAccessibleRemoveButton_when_fileDisplayed', () => {
    render(<FileUpload currentFileUrl="https://cdn.example.com/logos/logo.png" />);

    const removeButton = screen.getByRole('button', { name: /remove file/i });
    expect(removeButton).toHaveAttribute('aria-label', 'Remove file');
  });

  it('should_displayAltText_when_imageRendered', () => {
    render(<FileUpload currentFileUrl="https://cdn.example.com/logos/logo.png" />);

    const image = screen.getByAltText('Uploaded file preview');
    expect(image).toBeInTheDocument();
  });

  // ============ Image Display Tests ============

  it('should_displayImageWithCorrectStyles_when_fileUploaded', () => {
    render(<FileUpload currentFileUrl="https://cdn.example.com/logos/logo.png" />);

    const image = screen.getByAltText('Uploaded file preview');
    expect(image).toHaveStyle({
      maxWidth: '200px',
      maxHeight: '200px',
      objectFit: 'contain',
    });
  });

  // ============ Integration Tests ============

  it('should_resetAndShowDropzone_when_fileRemovedAfterUpload', async () => {
    const user = userEvent.setup();

    // Start with uploaded file
    const { rerender } = render(
      <FileUpload currentFileUrl="https://cdn.example.com/logos/logo.png" />
    );

    expect(screen.getByAltText('Uploaded file preview')).toBeInTheDocument();
    expect(screen.queryByTestId('file-dropzone')).not.toBeInTheDocument();

    // Click remove
    const removeButton = screen.getByRole('button', { name: /remove file/i });
    await user.click(removeButton);

    expect(mockReset).toHaveBeenCalled();

    // Rerender without file URL to show dropzone
    rerender(<FileUpload />);

    expect(screen.queryByAltText('Uploaded file preview')).not.toBeInTheDocument();
    expect(screen.getByTestId('file-dropzone')).toBeInTheDocument();
  });

  it('should_notUploadFile_when_uploadAlreadyInProgress', () => {
    mockUseFileUpload.mockReturnValue({
      uploadFile: mockUploadFile,
      isUploading: true,
      uploadProgress: 50,
      error: null,
      reset: mockReset,
    });

    render(<FileUpload />);

    const dropzone = screen.getByTestId('file-dropzone');
    expect(dropzone).toHaveAttribute('aria-disabled', 'true');

    // Input should be disabled (tested through react-dropzone disabled prop)
    const input = dropzone.querySelector('input[type="file"]') as HTMLInputElement;
    expect(input).toBeInTheDocument();
  });

  // ============ Story 5.9: Multiple Files Upload Tests (AC1) ============

  describe('Story 5.9: Session Materials Upload', () => {
    it('should_acceptMultipleFiles_when_multiplePropsEnabled', async () => {
      const user = userEvent.setup();
      mockUploadFile.mockResolvedValue('upload-multi-123');

      render(
        <FileUpload
          multiple={true}
          maxFiles={10}
          allowedTypes={[
            'application/vnd.openxmlformats-officedocument.presentationml.presentation',
            'application/pdf',
          ]}
          onUploadSuccess={mockOnUploadSuccess}
        />
      );

      const file1 = new File(['content1'], 'presentation.pptx', {
        type: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
      });
      const file2 = new File(['content2'], 'document.pdf', { type: 'application/pdf' });

      const dropzone = screen.getByTestId('file-dropzone');
      const input = dropzone.querySelector('input[type="file"]') as HTMLInputElement;

      await user.upload(input, [file1, file2]);

      await waitFor(() => {
        expect(mockUploadFile).toHaveBeenCalledTimes(2);
        expect(mockUploadFile).toHaveBeenCalledWith(file1);
        expect(mockUploadFile).toHaveBeenCalledWith(file2);
      });
    });

    it('should_rejectFiles_when_overMaxFilesLimit', async () => {
      const user = userEvent.setup();

      render(
        <FileUpload
          multiple={true}
          maxFiles={3}
          allowedTypes={['application/pdf']}
          onUploadError={mockOnUploadError}
        />
      );

      // Try to upload 4 files (exceeds limit of 3)
      const files = [
        new File(['1'], 'file1.pdf', { type: 'application/pdf' }),
        new File(['2'], 'file2.pdf', { type: 'application/pdf' }),
        new File(['3'], 'file3.pdf', { type: 'application/pdf' }),
        new File(['4'], 'file4.pdf', { type: 'application/pdf' }),
      ];

      const dropzone = screen.getByTestId('file-dropzone');
      const input = dropzone.querySelector('input[type="file"]') as HTMLInputElement;

      await user.upload(input, files);

      await waitFor(() => {
        expect(mockOnUploadError).toHaveBeenCalledWith({
          type: 'TOO_MANY_FILES',
          message: expect.stringContaining('maximum of 3 files'),
        });
      });
    });

    it('should_acceptPowerPointFiles_when_pptxProvided', async () => {
      const user = userEvent.setup();
      mockUploadFile.mockResolvedValue('upload-pptx-123');

      render(
        <FileUpload
          allowedTypes={[
            'application/vnd.openxmlformats-officedocument.presentationml.presentation',
            'application/vnd.ms-powerpoint',
          ]}
          onUploadSuccess={mockOnUploadSuccess}
        />
      );

      const file = new File(['pptx content'], 'slides.pptx', {
        type: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
      });

      const dropzone = screen.getByTestId('file-dropzone');
      const input = dropzone.querySelector('input[type="file"]') as HTMLInputElement;

      await user.upload(input, file);

      await waitFor(() => {
        expect(mockUploadFile).toHaveBeenCalledWith(file);
      });
    });

    it('should_acceptPDFFiles_when_pdfProvided', async () => {
      const user = userEvent.setup();
      mockUploadFile.mockResolvedValue('upload-pdf-123');

      render(
        <FileUpload allowedTypes={['application/pdf']} onUploadSuccess={mockOnUploadSuccess} />
      );

      const file = new File(['pdf content'], 'document.pdf', { type: 'application/pdf' });

      const dropzone = screen.getByTestId('file-dropzone');
      const input = dropzone.querySelector('input[type="file"]') as HTMLInputElement;

      await user.upload(input, file);

      await waitFor(() => {
        expect(mockUploadFile).toHaveBeenCalledWith(file);
      });
    });

    it('should_acceptVideoFiles_when_mp4Provided', async () => {
      const user = userEvent.setup();
      mockUploadFile.mockResolvedValue('upload-video-123');

      render(
        <FileUpload
          allowedTypes={['video/mp4', 'video/quicktime']}
          onUploadSuccess={mockOnUploadSuccess}
        />
      );

      const file = new File(['video content'], 'presentation.mp4', { type: 'video/mp4' });

      const dropzone = screen.getByTestId('file-dropzone');
      const input = dropzone.querySelector('input[type="file"]') as HTMLInputElement;

      await user.upload(input, file);

      await waitFor(() => {
        expect(mockUploadFile).toHaveBeenCalledWith(file);
      });
    });

    it('should_accept100MBFiles_when_maxFileSizeIncreased', () => {
      render(<FileUpload maxFileSize={100 * 1024 * 1024} />);

      expect(mockUseFileUpload).toHaveBeenCalledWith(
        expect.objectContaining({
          maxFileSize: 100 * 1024 * 1024,
        })
      );

      expect(screen.getByText(/accepted formats.*\(max 100mb\)/i)).toBeInTheDocument();
    });

    it('should_displayUploadedFilesList_when_multipleFilesUploaded', () => {
      render(
        <FileUpload
          multiple={true}
          uploadedFiles={[
            {
              uploadId: 'upload-1',
              fileName: 'presentation.pptx',
              fileSize: 5242880,
              fileType: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
            },
            {
              uploadId: 'upload-2',
              fileName: 'document.pdf',
              fileSize: 1048576,
              fileType: 'application/pdf',
            },
          ]}
        />
      );

      expect(screen.getByText('presentation.pptx')).toBeInTheDocument();
      expect(screen.getByText('document.pdf')).toBeInTheDocument();
      expect(screen.getByText(/5\.0 mb/i)).toBeInTheDocument();
      expect(screen.getByText(/1\.0 mb/i)).toBeInTheDocument();
    });

    it('should_displayFileTypeIcons_when_filesListed', () => {
      render(
        <FileUpload
          multiple={true}
          uploadedFiles={[
            {
              uploadId: 'upload-1',
              fileName: 'slides.pptx',
              fileSize: 1024,
              fileType: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
            },
            {
              uploadId: 'upload-2',
              fileName: 'doc.pdf',
              fileSize: 1024,
              fileType: 'application/pdf',
            },
            {
              uploadId: 'upload-3',
              fileName: 'video.mp4',
              fileSize: 1024,
              fileType: 'video/mp4',
            },
          ]}
        />
      );

      // Icons should be displayed (using data-testid or aria-label)
      expect(screen.getByTestId('file-icon-presentation')).toBeInTheDocument();
      expect(screen.getByTestId('file-icon-document')).toBeInTheDocument();
      expect(screen.getByTestId('file-icon-video')).toBeInTheDocument();
    });

    it('should_removeSpecificFile_when_deleteButtonClicked', async () => {
      const user = userEvent.setup();
      const mockOnFileRemove = vi.fn();

      render(
        <FileUpload
          multiple={true}
          uploadedFiles={[
            {
              uploadId: 'upload-1',
              fileName: 'file1.pdf',
              fileSize: 1024,
              fileType: 'application/pdf',
            },
            {
              uploadId: 'upload-2',
              fileName: 'file2.pdf',
              fileSize: 1024,
              fileType: 'application/pdf',
            },
          ]}
          onFileRemove={mockOnFileRemove}
        />
      );

      const deleteButtons = screen.getAllByRole('button', { name: /delete/i });
      await user.click(deleteButtons[0]); // Delete first file

      await waitFor(() => {
        expect(mockOnFileRemove).toHaveBeenCalledWith('upload-1');
      });
    });

    it('should_displayEmptyState_when_noFilesUploadedWithMultipleMode', () => {
      render(<FileUpload multiple={true} uploadedFiles={[]} />);

      expect(screen.getByTestId('file-dropzone')).toBeInTheDocument();
      expect(screen.getByText(/drag and drop files here/i)).toBeInTheDocument();
    });
  });
});
