/**
 * #1246: on /profile the participant sees the company logo and asks the organizers for a new one.
 *
 * Story 17.11: the real useFileUpload and upload service run; the backend answers through the MSW
 * harness (uploads-api, companies-api, users-api), the S3 PUT through a handler of its own.
 */
import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import { server } from '@/test/msw/server';
import { CompanyLogoSection } from '../CompanyLogoSection';

const S3_PUT = 'https://bucket.example/logos/temp/up-1/logo-up-1.png';

const company = {
  name: 'acme',
  displayName: 'ACME AG',
  isVerified: true,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
  createdBy: 'orga.nizer',
  logo: { url: 'https://cdn.example.com/old.png' },
};

function renderSection() {
  return render(
    <QueryClientProvider
      client={
        new QueryClient({
          defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
        })
      }
    >
      <CompanyLogoSection companyName="acme" />
    </QueryClientProvider>
  );
}

const png = () => new File(['x'], 'logo.png', { type: 'image/png' });

/** The upload of the chosen file goes through: reserve, S3 PUT, confirm (no target). */
function uploadSucceeds() {
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
  server.use(http.put(S3_PUT, () => new HttpResponse(null, { status: 200 })));
  api.on('confirmFileUpload', () => ({
    uploadId: 'up-1',
    purpose: 'company-logo' as const,
    status: 'CONFIRMED' as const,
    fileUrl: null,
  }));
}

async function chooseAndSend(note?: string) {
  await userEvent.click(await screen.findByTestId('company-logo-request-button'));
  await userEvent.upload(screen.getByTestId('company-logo-file'), png());
  if (note) {
    await userEvent.type(screen.getByTestId('company-logo-note'), note);
  }
  await userEvent.click(screen.getByTestId('company-logo-send'));
}

describe('CompanyLogoSection', () => {
  beforeEach(() => {
    api.on('getCompany', () => company);
    api.on('getMyOpenCompanyLogoRequest', () => new HttpResponse(null, { status: 204 }));
  });

  it('shows the current logo and the request button', async () => {
    renderSection();

    expect(await screen.findByTestId('company-logo-current')).toHaveAttribute(
      'src',
      'https://cdn.example.com/old.png'
    );
    expect(screen.getByTestId('company-logo-request-button')).toBeEnabled();
  });

  it('says so when the company has no logo yet', async () => {
    api.on('getCompany', () => ({ ...company, logo: undefined }));
    renderSection();

    expect(await screen.findByTestId('company-logo-none')).toHaveTextContent('No logo yet');
  });

  it('uploads the chosen file as a company-logo without target and sends the request', async () => {
    uploadSucceeds();
    renderSection();

    await chooseAndSend('Rebranding');

    expect(await screen.findByTestId('company-logo-sent')).toBeInTheDocument();
    expect(api.calls('createFileUpload')[0].params).toEqual({ purpose: 'company-logo' });
    expect(api.calls('createFileUpload')[0].body).toEqual({
      fileName: 'logo.png',
      contentType: 'image/png',
      size: 1,
    });
    expect(api.calls('confirmFileUpload')[0].body).toEqual({});
    expect(api.calls('createCompanyLogoRequest')[0].body).toEqual({
      logoUploadId: 'up-1',
      note: 'Rebranding',
    });
  });

  it('cannot send without a file', async () => {
    renderSection();

    await userEvent.click(await screen.findByTestId('company-logo-request-button'));

    expect(screen.getByTestId('company-logo-send')).toBeDisabled();
  });

  it('a failed upload sends no request', async () => {
    api.on('createFileUpload', () =>
      HttpResponse.json({ status: 400, message: 'Type image/gif is not allowed' }, { status: 400 })
    );
    renderSection();

    await chooseAndSend();

    expect(await screen.findByTestId('company-logo-error')).toHaveTextContent(
      'The file could not be uploaded.'
    );
    expect(api.calls('createFileUpload')).toHaveLength(1);
    expect(api.calls('createCompanyLogoRequest')).toHaveLength(0);
  });

  it('an open request replaces the button with a notice', async () => {
    api.on('getMyOpenCompanyLogoRequest', () =>
      HttpResponse.json({
        requestId: '3f2b8c1e-5d4a-4e6b-9a7c-1b2d3e4f5a6b',
        status: 'OPEN',
        requesterUsername: 'anna.muster',
        requesterName: 'Anna Muster',
        companyName: 'acme',
        companyDisplayName: 'ACME AG',
        oldLogoUrl: 'https://cdn.example.com/old.png',
        newLogoUrl: 'https://cdn.example.com/new.png',
        createdAt: '2026-11-12T10:00:00Z',
      })
    );
    renderSection();

    expect(await screen.findByTestId('company-logo-pending')).toBeInTheDocument();
    expect(screen.queryByTestId('company-logo-request-button')).not.toBeInTheDocument();
  });

  it('shows the reason when the server refuses the request', async () => {
    uploadSucceeds();
    api.on('createCompanyLogoRequest', () =>
      HttpResponse.json({ status: 409, errorCode: 'LOGO_REQUEST_OPEN' }, { status: 409 })
    );
    renderSection();

    await chooseAndSend();

    await waitFor(() => {
      expect(screen.getByTestId('company-logo-error')).toHaveTextContent(
        'A request for this company is already with the organizers.'
      );
    });
  });
});
