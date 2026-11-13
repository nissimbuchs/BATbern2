/**
 * PresentationUpload Tests (Story 6.3 AC7; the upload contract since Story 17.11d)
 *
 * The real speakerPortalService and upload service run; the backend answers through the MSW harness
 * (checked against uploads-api), the S3 PUT through a handler of its own. The speaker's slides are
 * purpose `session-material` with the target `{eventCode, sessionSlug}`.
 */

import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { http, HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import { server } from '@/test/msw/server';
import PresentationUpload from '../PresentationUpload';

const S3_PUT = 'https://bucket.example/materials/temp/up-1/file-up-1.pptx';
const FILE_URL =
  'https://cdn.batbern.ch/materials/events/BATbern99/sessions/keynote/file-up-1.pptx';
const PPTX = 'application/vnd.openxmlformats-officedocument.presentationml.presentation';

/** Reserve, S3 PUT and confirm succeed; the confirm attaches with `fileUrl`. */
function uploadAnswers(fileUrl: string | null = FILE_URL) {
  api.on(
    'createFileUpload',
    () => ({
      uploadId: 'up-1',
      uploadUrl: S3_PUT,
      expiresAt: '2026-11-13T10:15:00Z',
      requiredHeaders: { 'Content-Type': PPTX },
    }),
    { status: 201 }
  );
  server.use(http.put(S3_PUT, () => new HttpResponse(null)));
  api.on('confirmFileUpload', () => ({
    uploadId: 'up-1',
    purpose: 'session-material' as const,
    status: fileUrl ? ('ASSOCIATED' as const) : ('CONFIRMED' as const),
    fileUrl,
  }));
}

describe('PresentationUpload', () => {
  const defaultProps = {
    eventCode: 'BATbern99',
    sessionSlug: 'keynote',
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
      uploadAnswers();

      render(<PresentationUpload {...defaultProps} />);

      const file = new File(['content'], 'presentation.pptx', {
        type: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
      });
      const fileInput = screen.getByLabelText(/upload presentation/i);

      await act(async () => {
        fireEvent.change(fileInput, { target: { files: [file] } });
      });

      await waitFor(() => {
        expect(api.calls('confirmFileUpload')).toHaveLength(1);
      });
    });

    it('should accept PDF files', async () => {
      uploadAnswers();

      render(<PresentationUpload {...defaultProps} />);

      const file = new File(['content'], 'presentation.pdf', { type: 'application/pdf' });
      const fileInput = screen.getByLabelText(/upload presentation/i);

      await act(async () => {
        fireEvent.change(fileInput, { target: { files: [file] } });
      });

      await waitFor(() => {
        expect(api.calls('confirmFileUpload')).toHaveLength(1);
      });
    });

    it('should accept KEY files', async () => {
      uploadAnswers();

      render(<PresentationUpload {...defaultProps} />);

      const file = new File(['content'], 'presentation.key', {
        type: 'application/x-iwork-keynote-sffkey',
      });
      const fileInput = screen.getByLabelText(/upload presentation/i);

      await act(async () => {
        fireEvent.change(fileInput, { target: { files: [file] } });
      });

      await waitFor(() => {
        expect(api.calls('confirmFileUpload')).toHaveLength(1);
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
      uploadAnswers();

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
        expect(api.calls('confirmFileUpload')).toHaveLength(1);
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
    it('should show the progress bar while the file goes to S3', async () => {
      uploadAnswers();
      server.use(http.put(S3_PUT, () => new Promise<Response>(() => {}))); // S3 never answers

      render(<PresentationUpload {...defaultProps} />);

      const file = new File(['content'], 'presentation.pptx', { type: PPTX });
      const fileInput = screen.getByLabelText(/upload presentation/i);

      await act(async () => {
        fireEvent.change(fileInput, { target: { files: [file] } });
      });

      await waitFor(() => expect(screen.getByText(/uploading/i)).toBeInTheDocument());
      // Multiple progressbars may exist (BATbernLoader + upload bar); the upload one has a value
      const uploadBar = screen
        .getAllByRole('progressbar')
        .find((el) => el.hasAttribute('aria-valuenow'));
      expect(uploadBar).toBeDefined();
      expect(api.calls('confirmFileUpload')).toHaveLength(0);
    });

    it('should reserve for the speaker session with name, type and size', async () => {
      uploadAnswers();

      render(<PresentationUpload {...defaultProps} />);

      const file = new File(['content'], 'Vortrag Müller.pptx', { type: PPTX });
      await act(async () => {
        fireEvent.change(screen.getByLabelText(/upload presentation/i), {
          target: { files: [file] },
        });
      });

      await waitFor(() => expect(api.calls('confirmFileUpload')).toHaveLength(1));
      expect(api.calls('createFileUpload')[0].params).toEqual({ purpose: 'session-material' });
      expect(api.calls('createFileUpload')[0].body).toEqual({
        fileName: 'Vortrag Müller.pptx',
        contentType: PPTX,
        size: file.size,
        target: { eventCode: 'BATbern99', sessionSlug: 'keynote' },
      });
      expect(api.calls('confirmFileUpload')[0].params).toEqual({
        purpose: 'session-material',
        uploadId: 'up-1',
      });
      expect(api.calls('confirmFileUpload')[0].body).toEqual({
        target: { eventCode: 'BATbern99', sessionSlug: 'keynote' },
      });
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
      uploadAnswers();
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
        expect(onMaterialUploaded).toHaveBeenCalledWith(FILE_URL, 'presentation.pptx');
      });
    });
  });

  describe('Error handling', () => {
    it('should report a failed upload when the confirmation has no file URL', async () => {
      // A confirm that did not attach has no fileUrl; without it there is no material to show, so
      // the upload counts as failed instead of passing an empty URL on.
      const onError = vi.fn();
      const onMaterialUploaded = vi.fn();
      uploadAnswers(null);

      render(
        <PresentationUpload
          {...defaultProps}
          onError={onError}
          onMaterialUploaded={onMaterialUploaded}
        />
      );

      const file = new File(['content'], 'presentation.pdf', { type: 'application/pdf' });
      await act(async () => {
        fireEvent.change(screen.getByLabelText(/upload presentation/i), {
          target: { files: [file] },
        });
      });

      await waitFor(() => {
        expect(onError).toHaveBeenCalledWith(expect.objectContaining({ type: 'UPLOAD_FAILED' }));
      });
      expect(onMaterialUploaded).not.toHaveBeenCalled();
    });

    it('should display error message on upload failure', async () => {
      const onError = vi.fn();
      uploadAnswers();
      api.on('createFileUpload', () =>
        HttpResponse.json(
          { status: 400, message: 'Upload failed: files of type .pptx are too large' },
          { status: 400 }
        )
      );

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
