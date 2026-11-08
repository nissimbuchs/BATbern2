/**
 * #1246: on /profile the participant sees the company logo and asks the organizers for a new one.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import MockAdapter from 'axios-mock-adapter';
import apiClient from '@/services/api/apiClient';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

const uploadFile = vi.fn();
const useFileUploadOptions = vi.fn();
vi.mock('@/hooks/useFileUpload/useFileUpload', () => ({
  useFileUpload: (options: unknown) => {
    useFileUploadOptions(options);
    return { uploadFile, isUploading: false, uploadProgress: 0, error: null };
  },
}));

import { CompanyLogoSection } from '../CompanyLogoSection';
import { logoUploadTarget } from '@/services/api/companyApi';

// Company and logo-request calls are stubbed at the HTTP boundary.
const COMPANY_URL = '/companies/acme';
const OPEN_REQUEST_URL = '/users/me/company-logo-requests/open';
const REQUESTS_URL = '/users/me/company-logo-requests';
let http: MockAdapter;

const company = {
  name: 'acme',
  displayName: 'ACME AG',
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

describe('CompanyLogoSection', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    http = new MockAdapter(apiClient);
    http.onGet(COMPANY_URL).reply(200, company);
    http.onGet(OPEN_REQUEST_URL).reply(204);
  });

  afterEach(() => {
    http.restore();
  });

  it('shows the current logo and the request button', async () => {
    renderSection();

    expect(await screen.findByTestId('company-logo-current')).toHaveAttribute(
      'src',
      'https://cdn.example.com/old.png'
    );
    expect(screen.getByTestId('company-logo-request-button')).toBeEnabled();
  });

  it('uploads the logo to the logo upload target', async () => {
    renderSection();

    await screen.findByTestId('company-logo-current');
    expect(useFileUploadOptions).toHaveBeenCalledWith(
      expect.objectContaining({ target: logoUploadTarget })
    );
  });

  it('says so when the company has no logo yet', async () => {
    http.onGet(COMPANY_URL).reply(200, { name: 'acme' });
    renderSection();

    expect(await screen.findByTestId('company-logo-none')).toBeInTheDocument();
  });

  it('uploads the chosen file and sends the request with the note', async () => {
    uploadFile.mockResolvedValue('up-1');
    http.onPost(REQUESTS_URL).reply(200, { status: 'OPEN' });
    renderSection();

    await userEvent.click(await screen.findByTestId('company-logo-request-button'));
    await userEvent.upload(screen.getByTestId('company-logo-file'), png());
    await userEvent.type(screen.getByTestId('company-logo-note'), 'Rebranding');
    await userEvent.click(screen.getByTestId('company-logo-send'));

    expect(uploadFile).toHaveBeenCalled();
    expect(http.history.post).toHaveLength(1);
    expect(JSON.parse(http.history.post[0].data)).toEqual({
      logoUploadId: 'up-1',
      note: 'Rebranding',
    });
    expect(await screen.findByTestId('company-logo-sent')).toBeInTheDocument();
  });

  it('cannot send without a file', async () => {
    renderSection();

    await userEvent.click(await screen.findByTestId('company-logo-request-button'));

    expect(screen.getByTestId('company-logo-send')).toBeDisabled();
  });

  it('a failed upload sends no request', async () => {
    uploadFile.mockResolvedValue(null);
    renderSection();

    await userEvent.click(await screen.findByTestId('company-logo-request-button'));
    await userEvent.upload(screen.getByTestId('company-logo-file'), png());
    await userEvent.click(screen.getByTestId('company-logo-send'));

    expect(http.history.post).toHaveLength(0);
    expect(await screen.findByTestId('company-logo-error')).toHaveTextContent(
      'profile.logo.errorUpload'
    );
  });

  it('an open request replaces the button with a notice', async () => {
    http.onGet(OPEN_REQUEST_URL).reply(200, {
      requestId: 'r-1',
      status: 'OPEN',
      newLogoUrl: 'https://cdn.example.com/new.png',
    });
    renderSection();

    expect(await screen.findByTestId('company-logo-pending')).toBeInTheDocument();
    expect(screen.queryByTestId('company-logo-request-button')).not.toBeInTheDocument();
  });

  it('shows the reason when the server refuses the request', async () => {
    uploadFile.mockResolvedValue('up-1');
    http.onPost(REQUESTS_URL).reply(409, { errorCode: 'LOGO_REQUEST_OPEN' });
    renderSection();

    await userEvent.click(await screen.findByTestId('company-logo-request-button'));
    await userEvent.upload(screen.getByTestId('company-logo-file'), png());
    await userEvent.click(screen.getByTestId('company-logo-send'));

    expect(await screen.findByTestId('company-logo-error')).toHaveTextContent(
      'profile.logo.errorOpen'
    );
  });
});
