/**
 * #1183 D — the claim link page: POST only on the button; 403 means another login.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { I18nextProvider } from 'react-i18next';
import { AxiosError, type AxiosResponse } from 'axios';
import i18n from '../../i18n/config';
import ClaimProfilePage from './ClaimProfilePage';
import * as api from '@/services/api/userAccountApi';

vi.mock('@/services/api/userAccountApi', () => ({ confirmProfileClaim: vi.fn() }));
vi.mock('@/components/public/PublicLayout', () => ({
  PublicLayout: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

const renderPage = (search = '?token=abc') =>
  render(
    <QueryClientProvider
      client={
        new QueryClient({
          defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
        })
      }
    >
      <I18nextProvider i18n={i18n}>
        <MemoryRouter initialEntries={[`/claim-profile${search}`]}>
          <ClaimProfilePage />
        </MemoryRouter>
      </I18nextProvider>
    </QueryClientProvider>
  );

describe('ClaimProfilePage (#1183 D)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should_notCallApi_until_buttonClicked_then_showMerged', async () => {
    vi.mocked(api.confirmProfileClaim).mockResolvedValue({ merged: true, alreadyMerged: false });
    renderPage();

    expect(api.confirmProfileClaim).not.toHaveBeenCalled();
    await userEvent.click(screen.getByTestId('claim-confirm-button'));

    expect(api.confirmProfileClaim).toHaveBeenCalledWith('abc');
    expect(await screen.findByTestId('claim-merged')).toBeInTheDocument();
  });

  it('should_showOtherAccount_when_403', async () => {
    vi.mocked(api.confirmProfileClaim).mockRejectedValue(
      new AxiosError('forbidden', '403', undefined, undefined, { status: 403 } as AxiosResponse)
    );
    renderPage();

    await userEvent.click(screen.getByTestId('claim-confirm-button'));

    expect(await screen.findByTestId('claim-otherAccount')).toBeInTheDocument();
  });

  it('should_showInvalid_when_noToken', () => {
    renderPage('');
    expect(screen.getByTestId('claim-invalid')).toBeInTheDocument();
  });
});
