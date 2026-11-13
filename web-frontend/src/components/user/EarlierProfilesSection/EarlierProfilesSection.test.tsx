/**
 * #1183 D — earlier profiles under the person's name: hidden when none, "This is me" sends the link.
 *
 * On the MSW harness: the real services and queries run; only the network is answered, checked
 * against the specs.
 */
import { describe, it, expect, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import { renderWithProviders } from '@/test/test-utils';
import { aClaimCandidate } from '@/test/fixtures';
import { EarlierProfilesSection } from './EarlierProfilesSection';

const renderSection = () => renderWithProviders(<EarlierProfilesSection />);

describe('EarlierProfilesSection (#1183 D)', () => {
  it('should_renderNothing_when_noCandidate', async () => {
    api.on('listProfileClaimCandidates', () => []);
    const { container, queryClient } = renderSection();
    await vi.waitFor(() =>
      expect(queryClient.getQueryState(['users', 'me', 'profile-claims'])?.status).toBe('success')
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('should_sendLink_when_thisIsMeClicked', async () => {
    api.on('listProfileClaimCandidates', () => [
      aClaimCandidate({ candidateId: 'c-1', maskedEmail: 'a***@example.com', companyName: 'acme' }),
    ]);
    renderSection();

    expect(await screen.findByText('a***@example.com')).toBeInTheDocument();
    // The "What's new" merge entry links here.
    expect(screen.getByTestId('earlier-profiles-section')).toHaveAttribute(
      'id',
      'earlier-profiles'
    );
    await userEvent.click(screen.getByTestId('claim-button-c-1'));

    expect(await screen.findByTestId('claim-sent-c-1')).toBeInTheDocument();
    expect(api.calls('requestProfileClaim').map((c) => c.body)).toEqual([{ candidateId: 'c-1' }]);
  });

  // #1237: no access to the old address — ask the organizers
  it('should_sendRequestWithNote_when_noAccess', async () => {
    let pending = false;
    api.on('listProfileClaimCandidates', () => [
      aClaimCandidate({
        candidateId: 'c-1',
        maskedEmail: 'a***@oldco.example.com',
        requestPending: pending,
      }),
    ]);
    api.on('requestProfileMerge', () => {
      pending = true;
      return { requestId: '5d0c3a52-7a43-4b8e-9a51-2f6a4c1d9e01' };
    });
    renderSection();

    await userEvent.click(await screen.findByTestId('claim-no-access-c-1'));
    await userEvent.type(screen.getByTestId('claim-note-c-1'), 'Firma X bis 2024');
    await userEvent.click(screen.getByTestId('claim-send-request-c-1'));

    expect(await screen.findByTestId('claim-pending-c-1')).toBeInTheDocument();
    expect(api.calls('requestProfileMerge').map((c) => c.body)).toEqual([
      { candidateId: 'c-1', note: 'Firma X bis 2024' },
    ]);
    expect(screen.queryByTestId('claim-button-c-1')).not.toBeInTheDocument();
    expect(screen.queryByTestId('claim-no-access-c-1')).not.toBeInTheDocument();
  });

  it('should_offerNeitherPath_when_requestPending', async () => {
    api.on('listProfileClaimCandidates', () => [
      aClaimCandidate({
        candidateId: 'c-1',
        maskedEmail: 'a***@oldco.example.com',
        requestPending: true,
      }),
    ]);
    renderSection();

    expect(await screen.findByTestId('claim-pending-c-1')).toBeInTheDocument();
    expect(screen.queryByTestId('claim-button-c-1')).not.toBeInTheDocument();
    expect(screen.queryByTestId('claim-no-access-c-1')).not.toBeInTheDocument();
  });

  it('should_offerOnlyTheRequest_when_placeholderAddress', async () => {
    // #1244: an imported profile with vorname.nachname@batbern.ch: a link would reach nobody
    api.on('listProfileClaimCandidates', () => [
      aClaimCandidate({
        candidateId: 'c-1',
        maskedEmail: 'a***@batbern.ch',
        placeholderAddress: true,
      }),
    ]);
    api.on('requestProfileMerge', () => ({ requestId: '5d0c3a52-7a43-4b8e-9a51-2f6a4c1d9e01' }));
    const user = userEvent.setup();
    renderSection();

    expect(await screen.findByTestId('claim-placeholder-c-1')).toBeInTheDocument();
    expect(screen.queryByTestId('claim-button-c-1')).not.toBeInTheDocument();

    await user.click(screen.getByTestId('claim-no-access-c-1'));
    await user.click(screen.getByTestId('claim-send-request-c-1'));
    await vi.waitFor(() => expect(api.calls('requestProfileMerge')).toHaveLength(1));
    // An empty note is not sent.
    expect(api.calls('requestProfileMerge')[0].body).toEqual({ candidateId: 'c-1' });
    expect(api.calls('requestProfileClaim')).toHaveLength(0);
  });

  it('should_explainCooldown_when_recentlyDeclined', async () => {
    api.on('listProfileClaimCandidates', () => [
      aClaimCandidate({ candidateId: 'c-1', maskedEmail: 'a***@oldco.example.com' }),
    ]);
    api.on('requestProfileMerge', () =>
      HttpResponse.json(
        { errorCode: 'MERGE_REQUEST_DECLINED', message: 'declined recently' },
        { status: 409 }
      )
    );
    renderSection();

    await userEvent.click(await screen.findByTestId('claim-no-access-c-1'));
    await userEvent.click(screen.getByTestId('claim-send-request-c-1'));

    expect(await screen.findByTestId('claim-request-error')).toHaveTextContent(/30/);
  });
});
