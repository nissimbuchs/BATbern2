/**
 * #1246: on /profile the participant sees the company logo and asks the organizers for a new one.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AxiosError, AxiosHeaders } from 'axios';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

const uploadFile = vi.fn();
vi.mock('@/hooks/useFileUpload/useFileUpload', () => ({
  useFileUpload: () => ({ uploadFile, isUploading: false, uploadProgress: 0, error: null }),
}));

vi.mock('@/services/api/companyApi', () => ({
  companyApiClient: { getCompany: vi.fn() },
}));

vi.mock('@/services/api/userAccountApi', () => ({
  createCompanyLogoRequest: vi.fn(),
  getMyOpenCompanyLogoRequest: vi.fn(),
}));

import { companyApiClient } from '@/services/api/companyApi';
import {
  createCompanyLogoRequest,
  getMyOpenCompanyLogoRequest,
} from '@/services/api/userAccountApi';
import { CompanyLogoSection } from '../CompanyLogoSection';

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

function conflict(code: string) {
  return new AxiosError('conflict', '409', undefined, undefined, {
    status: 409,
    statusText: 'Conflict',
    data: { errorCode: code },
    headers: {},
    config: { headers: new AxiosHeaders() },
  });
}

describe('CompanyLogoSection', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(companyApiClient.getCompany).mockResolvedValue(company as never);
    vi.mocked(getMyOpenCompanyLogoRequest).mockResolvedValue(null);
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
    vi.mocked(companyApiClient.getCompany).mockResolvedValue({ name: 'acme' } as never);
    renderSection();

    expect(await screen.findByTestId('company-logo-none')).toBeInTheDocument();
  });

  it('uploads the chosen file and sends the request with the note', async () => {
    uploadFile.mockResolvedValue('up-1');
    vi.mocked(createCompanyLogoRequest).mockResolvedValue({ status: 'OPEN' } as never);
    renderSection();

    await userEvent.click(await screen.findByTestId('company-logo-request-button'));
    await userEvent.upload(screen.getByTestId('company-logo-file'), png());
    await userEvent.type(screen.getByTestId('company-logo-note'), 'Rebranding');
    await userEvent.click(screen.getByTestId('company-logo-send'));

    expect(uploadFile).toHaveBeenCalled();
    expect(createCompanyLogoRequest).toHaveBeenCalledWith('up-1', 'Rebranding');
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

    expect(createCompanyLogoRequest).not.toHaveBeenCalled();
    expect(await screen.findByTestId('company-logo-error')).toHaveTextContent(
      'profile.logo.errorUpload'
    );
  });

  it('an open request replaces the button with a notice', async () => {
    vi.mocked(getMyOpenCompanyLogoRequest).mockResolvedValue({
      requestId: 'r-1',
      status: 'OPEN',
      newLogoUrl: 'https://cdn.example.com/new.png',
    } as never);
    renderSection();

    expect(await screen.findByTestId('company-logo-pending')).toBeInTheDocument();
    expect(screen.queryByTestId('company-logo-request-button')).not.toBeInTheDocument();
  });

  it('shows the reason when the server refuses the request', async () => {
    uploadFile.mockResolvedValue('up-1');
    vi.mocked(createCompanyLogoRequest).mockRejectedValue(conflict('LOGO_REQUEST_OPEN'));
    renderSection();

    await userEvent.click(await screen.findByTestId('company-logo-request-button'));
    await userEvent.upload(screen.getByTestId('company-logo-file'), png());
    await userEvent.click(screen.getByTestId('company-logo-send'));

    expect(await screen.findByTestId('company-logo-error')).toHaveTextContent(
      'profile.logo.errorOpen'
    );
  });
});
