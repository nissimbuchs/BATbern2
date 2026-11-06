/**
 * #1183 D — earlier profiles under the person's name: hidden when none, "This is me" sends the link.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { I18nextProvider } from 'react-i18next';
import i18n from '../../../i18n/config';
import { EarlierProfilesSection } from './EarlierProfilesSection';
import * as api from '@/services/api/userAccountApi';

vi.mock('@/services/api/userAccountApi', () => ({
  listProfileClaimCandidates: vi.fn(),
  requestProfileClaim: vi.fn(),
  requestProfileMerge: vi.fn(),
}));

const renderSection = () =>
  render(
    <QueryClientProvider
      client={
        new QueryClient({
          defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
        })
      }
    >
      <I18nextProvider i18n={i18n}>
        <EarlierProfilesSection />
      </I18nextProvider>
    </QueryClientProvider>
  );

describe('EarlierProfilesSection (#1183 D)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should_renderNothing_when_noCandidate', async () => {
    vi.mocked(api.listProfileClaimCandidates).mockResolvedValue([]);
    const { container } = renderSection();
    await vi.waitFor(() => expect(api.listProfileClaimCandidates).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });

  it('should_sendLink_when_thisIsMeClicked', async () => {
    vi.mocked(api.listProfileClaimCandidates).mockResolvedValue([
      { candidateId: 'c-1', maskedEmail: 'a***@example.com', companyName: 'acme' },
    ]);
    vi.mocked(api.requestProfileClaim).mockResolvedValue();
    renderSection();

    expect(await screen.findByText('a***@example.com')).toBeInTheDocument();
    await userEvent.click(screen.getByTestId('claim-button-c-1'));

    expect(api.requestProfileClaim).toHaveBeenCalledWith('c-1');
    expect(await screen.findByTestId('claim-sent-c-1')).toBeInTheDocument();
  });

  // #1237: no access to the old address — ask the organizers
  it('should_sendRequestWithNote_when_noAccess', async () => {
    vi.mocked(api.listProfileClaimCandidates)
      .mockResolvedValueOnce([{ candidateId: 'c-1', maskedEmail: 'a***@oldco.example.com' }])
      .mockResolvedValue([
        { candidateId: 'c-1', maskedEmail: 'a***@oldco.example.com', requestPending: true },
      ]);
    vi.mocked(api.requestProfileMerge).mockResolvedValue('r-1');
    renderSection();

    await userEvent.click(await screen.findByTestId('claim-no-access-c-1'));
    await userEvent.type(screen.getByTestId('claim-note-c-1'), 'Firma X bis 2024');
    await userEvent.click(screen.getByTestId('claim-send-request-c-1'));

    expect(api.requestProfileMerge).toHaveBeenCalledWith('c-1', 'Firma X bis 2024');
    expect(await screen.findByTestId('claim-pending-c-1')).toBeInTheDocument();
    expect(screen.queryByTestId('claim-button-c-1')).not.toBeInTheDocument();
    expect(screen.queryByTestId('claim-no-access-c-1')).not.toBeInTheDocument();
  });

  it('should_offerNeitherPath_when_requestPending', async () => {
    vi.mocked(api.listProfileClaimCandidates).mockResolvedValue([
      { candidateId: 'c-1', maskedEmail: 'a***@oldco.example.com', requestPending: true },
    ]);
    renderSection();

    expect(await screen.findByTestId('claim-pending-c-1')).toBeInTheDocument();
    expect(screen.queryByTestId('claim-button-c-1')).not.toBeInTheDocument();
    expect(screen.queryByTestId('claim-no-access-c-1')).not.toBeInTheDocument();
  });

  it('should_explainCooldown_when_recentlyDeclined', async () => {
    vi.mocked(api.listProfileClaimCandidates).mockResolvedValue([
      { candidateId: 'c-1', maskedEmail: 'a***@oldco.example.com' },
    ]);
    vi.mocked(api.requestProfileMerge).mockRejectedValue({
      response: { status: 409, data: { errorCode: 'MERGE_REQUEST_DECLINED' } },
    });
    renderSection();

    await userEvent.click(await screen.findByTestId('claim-no-access-c-1'));
    await userEvent.click(screen.getByTestId('claim-send-request-c-1'));

    expect(await screen.findByTestId('claim-request-error')).toHaveTextContent(/30/);
  });
});
