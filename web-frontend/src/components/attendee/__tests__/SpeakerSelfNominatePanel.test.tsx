import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SpeakerSelfNominatePanel } from '../SpeakerSelfNominatePanel';
import * as speakerNominationApi from '@/services/api/speakerNominationApi';
import { useAuth } from '@/hooks/useAuth/useAuth';

vi.mock('@/services/api/speakerNominationApi');
vi.mock('@/hooks/useAuth/useAuth');

const mockNominate = vi.mocked(speakerNominationApi.selfNominateSpeaker);
const mockUseAuth = vi.mocked(useAuth);

function setAuthenticated(isAuthenticated: boolean) {
  // The panel only reads isAuthenticated; cast the partial context for the test.
  mockUseAuth.mockReturnValue({ isAuthenticated } as ReturnType<typeof useAuth>);
}

function makeQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
}

function renderPanel(eventCode = 'BATbern57') {
  return render(
    <QueryClientProvider client={makeQueryClient()}>
      <SpeakerSelfNominatePanel eventCode={eventCode} topicName="Cloud Native" />
    </QueryClientProvider>
  );
}

describe('SpeakerSelfNominatePanel (Story 7.2)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setAuthenticated(true);
    mockNominate.mockResolvedValue({ id: 's1', source: 'self_nomination' } as never);
  });

  it('renders nothing when the user is not logged in', () => {
    setAuthenticated(false);
    renderPanel();
    expect(screen.queryByTestId('self-nominate-button')).not.toBeInTheDocument();
  });

  it('shows the button to a logged-in attendee and opens the form on click', async () => {
    const user = userEvent.setup();
    renderPanel();

    await user.click(screen.getByTestId('self-nominate-button'));
    expect(await screen.findByTestId('self-nominate-dialog')).toBeInTheDocument();
    expect(screen.getByTestId('self-nominate-title')).toBeInTheDocument();
    expect(screen.getByTestId('self-nominate-abstract')).toBeInTheDocument();
  });

  it('disables submit until both title and abstract are valid', async () => {
    const user = userEvent.setup();
    renderPanel();

    await user.click(screen.getByTestId('self-nominate-button'));
    const submit = await screen.findByTestId('self-nominate-submit');
    expect(submit).toBeDisabled();

    await user.type(screen.getByTestId('self-nominate-title'), 'A solid talk title');
    expect(submit).toBeDisabled(); // abstract still empty

    await user.type(screen.getByTestId('self-nominate-abstract'), 'A few sentences of pitch.');
    expect(submit).toBeEnabled();
  });

  it('submits a nomination for the card event and shows success', async () => {
    const user = userEvent.setup();
    renderPanel('BATbern57');

    await user.click(screen.getByTestId('self-nominate-button'));
    await user.type(await screen.findByTestId('self-nominate-title'), 'Event-driven in practice');
    await user.type(
      screen.getByTestId('self-nominate-abstract'),
      'A field report from the trenches.'
    );
    await user.click(screen.getByTestId('self-nominate-submit'));

    await waitFor(() => {
      expect(mockNominate).toHaveBeenCalledWith('BATbern57', {
        sessionTitle: 'Event-driven in practice',
        abstract: 'A field report from the trenches.',
      });
    });
    expect(await screen.findByTestId('self-nominate-success')).toBeInTheDocument();
  });

  it('treats a 409 (already nominated) as already-done', async () => {
    const user = userEvent.setup();
    mockNominate.mockRejectedValueOnce({ isAxiosError: true, response: { status: 409 } });
    renderPanel();

    await user.click(screen.getByTestId('self-nominate-button'));
    await user.type(await screen.findByTestId('self-nominate-title'), 'Another talk title');
    await user.type(screen.getByTestId('self-nominate-abstract'), 'Pitch text goes here.');
    await user.click(screen.getByTestId('self-nominate-submit'));

    // After the duplicate error the dialog flips to the success/already-done view.
    expect(await screen.findByTestId('self-nominate-success')).toBeInTheDocument();
  });

  it('shows a generic error when the submission fails for another reason', async () => {
    const user = userEvent.setup();
    mockNominate.mockRejectedValueOnce(new Error('network'));
    renderPanel();

    await user.click(screen.getByTestId('self-nominate-button'));
    await user.type(await screen.findByTestId('self-nominate-title'), 'Yet another title');
    await user.type(screen.getByTestId('self-nominate-abstract'), 'Some abstract content.');
    await user.click(screen.getByTestId('self-nominate-submit'));

    expect(await screen.findByTestId('self-nominate-error')).toBeInTheDocument();
  });
});
