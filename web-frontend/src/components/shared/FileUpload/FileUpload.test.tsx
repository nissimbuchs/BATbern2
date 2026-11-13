/**
 * FileUpload Component Tests
 * Story 1.16.3, the upload contract since Story 17.11.
 *
 * The real useFileUpload and upload services run; the backend answers through the MSW harness
 * (checked against uploads-api), the S3 PUT through a handler of its own.
 */

import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import { server } from '@/test/msw/server';
import { FileUpload } from './FileUpload';
import { uploadTarget } from '@/services/uploadService';

/** The upload target is required since Story 17.11: there is no default purpose. */
const logoUploadTarget = uploadTarget('company-logo');
/** Session materials, confirmed without a target (the session editor, Story 17.11d). */
const materialUploadTarget = uploadTarget('session-material');

const S3_PUT = 'https://bucket.example/logos/temp/up-1/logo-up-1.png';
const MATERIAL_PUT = 'https://bucket.example/materials/temp/mat/file-mat.pdf';

/** Reserve and confirm of the upload contract succeed; the S3 PUT answers `s3`. */
function logoUploadAnswers(s3: () => Promise<Response> | Response = () => new HttpResponse(null)) {
  api.on(
    'createFileUpload',
    () => ({
      uploadId: 'up-1',
      uploadUrl: S3_PUT,
      expiresAt: '2026-11-12T10:15:00Z',
      requiredHeaders: { 'Content-Type': 'image/png' },
    }),
    { status: 201 }
  );
  server.use(http.put(S3_PUT, s3));
  api.on('confirmFileUpload', () => ({
    uploadId: 'up-1',
    purpose: 'company-logo' as const,
    status: 'CONFIRMED' as const,
    fileUrl: null,
  }));
}

/** Reserve and confirm of `session-material` answer; each file gets its own upload id. */
function materialUploadAnswers() {
  let n = 0;
  api.on(
    'createFileUpload',
    () => {
      n += 1;
      return {
        uploadId: `mat-${n}`,
        uploadUrl: MATERIAL_PUT,
        expiresAt: '2026-11-12T10:15:00Z',
        requiredHeaders: { 'Content-Type': 'application/pdf' },
      };
    },
    { status: 201 }
  );
  server.use(http.put(MATERIAL_PUT, () => new HttpResponse(null)));
  api.on('confirmFileUpload', () => ({
    uploadId: 'mat',
    purpose: 'session-material' as const,
    status: 'CONFIRMED' as const,
    fileUrl: null,
  }));
}

function fileInput(): HTMLInputElement {
  return screen
    .getByTestId('file-dropzone')
    .querySelector('input[type="file"]') as HTMLInputElement;
}

const png = (name = 'test-logo.png') => new File(['png'], name, { type: 'image/png' });
const pdf = (name: string) => new File(['pdf'], name, { type: 'application/pdf' });

describe('FileUpload Component', () => {
  const onUploadSuccess = vi.fn();
  const onUploadError = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    // jsdom has no object URLs (a browser API): the preview of a chosen file
    URL.createObjectURL = vi.fn(() => 'blob:preview');
  });

  // ============ Display and Initial State Tests ============

  it('should_displayDropzone_when_noFileUploaded', () => {
    render(<FileUpload uploadTarget={logoUploadTarget} />);

    expect(screen.getByTestId('file-dropzone')).toBeInTheDocument();
    expect(screen.getByText(/drag and drop a file here/i)).toBeInTheDocument();
    expect(screen.getByText(/accepted formats/i)).toBeInTheDocument();
  });

  it('should_displayCurrentFile_when_currentFileUrlProvided', () => {
    render(
      <FileUpload
        uploadTarget={logoUploadTarget}
        currentFileUrl="https://cdn.example.com/logos/company-logo.png"
      />
    );

    const image = screen.getByAltText('Uploaded file preview');
    expect(image).toHaveAttribute('src', 'https://cdn.example.com/logos/company-logo.png');
    expect(screen.queryByTestId('file-dropzone')).not.toBeInTheDocument();
  });

  it('should_displayRemoveButton_when_fileDisplayed', () => {
    render(
      <FileUpload
        uploadTarget={logoUploadTarget}
        currentFileUrl="https://cdn.example.com/logos/logo.png"
      />
    );

    expect(screen.getByRole('button', { name: /remove file/i })).toHaveAttribute(
      'aria-label',
      'Remove file'
    );
  });

  it('should_displayAcceptedFormatsText_when_dropzoneVisible', () => {
    render(<FileUpload uploadTarget={logoUploadTarget} maxFileSize={10 * 1024 * 1024} />);

    expect(screen.getByText(/accepted formats: png, jpeg, svg \(max 10mb\)/i)).toBeInTheDocument();
  });

  // ============ Upload through the upload contract ============

  it('should_uploadAndShowPreview_when_fileSelected', async () => {
    const user = userEvent.setup();
    logoUploadAnswers();
    render(<FileUpload uploadTarget={logoUploadTarget} onUploadSuccess={onUploadSuccess} />);

    await user.upload(fileInput(), png());

    await waitFor(() => {
      expect(onUploadSuccess).toHaveBeenCalledWith({
        uploadId: 'up-1',
        tempFileUrl: 'blob:preview',
      });
    });
    expect(screen.getByAltText('Uploaded file preview')).toHaveAttribute('src', 'blob:preview');
    expect(api.calls('createFileUpload')[0].params).toEqual({ purpose: 'company-logo' });
    expect(api.calls('createFileUpload')[0].body).toEqual({
      fileName: 'test-logo.png',
      contentType: 'image/png',
      size: 3,
    });
    expect(api.calls('confirmFileUpload')).toHaveLength(1);
  });

  it('should_showProgressAndDisableDropzone_when_uploading', async () => {
    const user = userEvent.setup();
    let releaseS3: () => void = () => {};
    const s3Held = new Promise<void>((resolve) => {
      releaseS3 = resolve;
    });
    logoUploadAnswers(async () => {
      await s3Held;
      return new HttpResponse(null);
    });
    render(<FileUpload uploadTarget={logoUploadTarget} onUploadSuccess={onUploadSuccess} />);

    await user.upload(fileInput(), png());

    expect(await screen.findByRole('progressbar')).toBeInTheDocument();
    expect(screen.getByText(/uploading\.\.\. \d+%/i)).toBeInTheDocument();
    expect(screen.getByTestId('file-dropzone')).toHaveAttribute('aria-disabled', 'true');

    releaseS3();
    await waitFor(() => expect(onUploadSuccess).toHaveBeenCalled());
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
  });

  it('should_displayErrorMessage_when_uploadRefused', async () => {
    const user = userEvent.setup();
    api.on('createFileUpload', () =>
      HttpResponse.json({ status: 400, message: 'The file exceeds 5 MB' }, { status: 400 })
    );
    render(<FileUpload uploadTarget={logoUploadTarget} onUploadError={onUploadError} />);

    await user.upload(fileInput(), png());

    expect(await screen.findByRole('alert')).toHaveTextContent(/could not request upload url/i);
    expect(onUploadError).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'BACKEND_PRESIGN_FAILED' })
    );
    expect(onUploadSuccess).not.toHaveBeenCalled();
  });

  it('should_refuseLargeFile_when_overMaxFileSize', async () => {
    const user = userEvent.setup();
    render(
      <FileUpload uploadTarget={logoUploadTarget} maxFileSize={2} onUploadError={onUploadError} />
    );

    await user.upload(fileInput(), png());

    expect(await screen.findByRole('alert')).toHaveTextContent(/file size must be less than/i);
    expect(onUploadError).toHaveBeenCalledWith(expect.objectContaining({ type: 'FILE_TOO_LARGE' }));
    expect(api.allCalls()).toHaveLength(0);
  });

  it('should_callOnUploadError_when_multipleFilesSelected', async () => {
    const user = userEvent.setup();
    render(<FileUpload uploadTarget={logoUploadTarget} onUploadError={onUploadError} />);

    await user.upload(fileInput(), [png('logo1.png'), png('logo2.png')]);

    await waitFor(() => {
      expect(onUploadError).toHaveBeenCalledWith({
        type: 'MULTIPLE_FILES',
        message: 'Only one file can be uploaded at a time',
      });
    });
    expect(api.allCalls()).toHaveLength(0);
  });

  // ============ File Removal Tests ============

  it('should_showDropzoneAndNotify_when_removeButtonClicked', async () => {
    const user = userEvent.setup();
    const onFileRemove = vi.fn();
    render(
      <FileUpload
        uploadTarget={logoUploadTarget}
        currentFileUrl="https://cdn.example.com/logos/logo.png"
        onFileRemove={onFileRemove}
      />
    );

    await user.click(screen.getByRole('button', { name: /remove file/i }));

    expect(onFileRemove).toHaveBeenCalled();
    expect(screen.queryByAltText('Uploaded file preview')).not.toBeInTheDocument();
    expect(screen.getByTestId('file-dropzone')).toHaveAttribute('aria-disabled', 'false');
  });

  it('should_syncFileUrl_when_currentFileUrlChanges', () => {
    const { rerender } = render(
      <FileUpload
        uploadTarget={logoUploadTarget}
        currentFileUrl="https://cdn.example.com/logos/logo-v1.png"
      />
    );
    expect(screen.getByAltText('Uploaded file preview')).toHaveAttribute(
      'src',
      'https://cdn.example.com/logos/logo-v1.png'
    );

    rerender(
      <FileUpload
        uploadTarget={logoUploadTarget}
        currentFileUrl="https://cdn.example.com/logos/logo-v2.png"
      />
    );

    expect(screen.getByAltText('Uploaded file preview')).toHaveAttribute(
      'src',
      'https://cdn.example.com/logos/logo-v2.png'
    );
  });

  it('should_displayImageWithCorrectStyles_when_fileUploaded', () => {
    render(
      <FileUpload
        uploadTarget={logoUploadTarget}
        currentFileUrl="https://cdn.example.com/logos/logo.png"
      />
    );

    expect(screen.getByAltText('Uploaded file preview')).toHaveStyle({
      maxWidth: '200px',
      maxHeight: '200px',
      objectFit: 'contain',
    });
  });

  // ============ Story 5.9: Multiple Files Upload (session materials) ============

  describe('Story 5.9: Session Materials Upload', () => {
    it('should_uploadEachFileAsSessionMaterial_when_multipleFilesSelected', async () => {
      const user = userEvent.setup();
      materialUploadAnswers();
      render(
        <FileUpload
          uploadTarget={materialUploadTarget}
          multiple={true}
          maxFiles={10}
          allowedTypes={['application/pdf']}
          onUploadSuccess={onUploadSuccess}
        />
      );

      await user.upload(fileInput(), [pdf('slides.pdf'), pdf('handout.pdf')]);

      await waitFor(() => expect(onUploadSuccess).toHaveBeenCalledTimes(2));
      expect(onUploadSuccess).toHaveBeenCalledWith(
        expect.objectContaining({
          uploadId: 'mat-1',
          fileName: 'slides.pdf',
          fileType: 'application/pdf',
        })
      );
      expect(onUploadSuccess).toHaveBeenCalledWith(
        expect.objectContaining({ uploadId: 'mat-2', fileName: 'handout.pdf' })
      );
      expect(api.calls('createFileUpload')).toHaveLength(2);
      expect(api.calls('createFileUpload')[0].params).toEqual({ purpose: 'session-material' });
      expect(api.calls('confirmFileUpload')).toHaveLength(2);
    });

    it('should_rejectFiles_when_overMaxFilesLimit', async () => {
      const user = userEvent.setup();
      render(
        <FileUpload
          uploadTarget={materialUploadTarget}
          multiple={true}
          maxFiles={3}
          allowedTypes={['application/pdf']}
          onUploadError={onUploadError}
        />
      );

      await user.upload(fileInput(), [pdf('1.pdf'), pdf('2.pdf'), pdf('3.pdf'), pdf('4.pdf')]);

      await waitFor(() => {
        expect(onUploadError).toHaveBeenCalledWith({
          type: 'TOO_MANY_FILES',
          message: expect.stringContaining('maximum of 3 files'),
        });
      });
      expect(api.allCalls()).toHaveLength(0);
    });

    it('should_show100MbLimit_when_maxFileSizeIncreased', () => {
      render(<FileUpload uploadTarget={materialUploadTarget} maxFileSize={100 * 1024 * 1024} />);

      expect(screen.getByText(/accepted formats.*\(max 100mb\)/i)).toBeInTheDocument();
    });

    it('should_displayUploadedFilesWithIcons_when_filesListed', () => {
      render(
        <FileUpload
          uploadTarget={materialUploadTarget}
          multiple={true}
          uploadedFiles={[
            {
              uploadId: 'upload-1',
              fileName: 'slides.pptx',
              fileSize: 5242880,
              fileType: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
            },
            {
              uploadId: 'upload-2',
              fileName: 'doc.pdf',
              fileSize: 1048576,
              fileType: 'application/pdf',
            },
            { uploadId: 'upload-3', fileName: 'video.mp4', fileSize: 1024, fileType: 'video/mp4' },
          ]}
        />
      );

      expect(screen.getByText('slides.pptx')).toBeInTheDocument();
      expect(screen.getByText(/5\.0 mb/i)).toBeInTheDocument();
      expect(screen.getByText(/1\.0 mb/i)).toBeInTheDocument();
      expect(screen.getByTestId('file-icon-presentation')).toBeInTheDocument();
      expect(screen.getByTestId('file-icon-document')).toBeInTheDocument();
      expect(screen.getByTestId('file-icon-video')).toBeInTheDocument();
    });

    it('should_removeSpecificFile_when_deleteButtonClicked', async () => {
      const user = userEvent.setup();
      const onFileRemove = vi.fn();
      render(
        <FileUpload
          uploadTarget={materialUploadTarget}
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
          onFileRemove={onFileRemove}
        />
      );

      await user.click(screen.getAllByRole('button', { name: /delete/i })[0]);

      expect(onFileRemove).toHaveBeenCalledWith('upload-1');
    });

    it('should_displayEmptyState_when_noFilesUploadedWithMultipleMode', () => {
      render(<FileUpload uploadTarget={materialUploadTarget} multiple={true} uploadedFiles={[]} />);

      expect(screen.getByTestId('file-dropzone')).toBeInTheDocument();
      expect(screen.getByText(/drag and drop files here/i)).toBeInTheDocument();
    });
  });
});
