import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { CommunityTopicSuggestPanel } from '../CommunityTopicSuggestPanel';
import * as partnerTopicsApi from '@/services/api/partnerTopicsApi';
import type { TopicDTO } from '@/services/api/partnerTopicsApi';

vi.mock('@/services/api/partnerTopicsApi');

const mockSuggest = vi.mocked(partnerTopicsApi.suggestTopicAsAttendee);

function makeQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
}

function renderPanel() {
  return render(
    <QueryClientProvider client={makeQueryClient()}>
      <CommunityTopicSuggestPanel />
    </QueryClientProvider>
  );
}

describe('CommunityTopicSuggestPanel (Story 7.1)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSuggest.mockResolvedValue({ id: 't1', source: 'COMMUNITY' } as TopicDTO);
  });

  it('disables submit until the title meets the minimum length', async () => {
    const user = userEvent.setup();
    renderPanel();

    const submit = screen.getByTestId('community-topic-submit');
    expect(submit).toBeDisabled();

    await user.type(screen.getByTestId('community-topic-title'), 'Hi'); // < 5 chars
    expect(submit).toBeDisabled();

    await user.type(screen.getByTestId('community-topic-title'), ' there'); // now >= 5
    expect(submit).toBeEnabled();
  });

  it('submits a community topic and shows a success message', async () => {
    const user = userEvent.setup();
    renderPanel();

    await user.type(
      screen.getByTestId('community-topic-title'),
      'Event-driven architecture in practice'
    );
    await user.type(screen.getByTestId('community-topic-description'), 'Lessons learned');
    await user.click(screen.getByTestId('community-topic-submit'));

    await waitFor(() => {
      expect(mockSuggest).toHaveBeenCalledWith({
        title: 'Event-driven architecture in practice',
        description: 'Lessons learned',
      });
    });
    expect(await screen.findByTestId('community-topic-success')).toBeInTheDocument();
  });

  it('shows an error message when the submission fails', async () => {
    const user = userEvent.setup();
    mockSuggest.mockRejectedValueOnce(new Error('network'));
    renderPanel();

    await user.type(screen.getByTestId('community-topic-title'), 'A valid topic title');
    await user.click(screen.getByTestId('community-topic-submit'));

    expect(await screen.findByTestId('community-topic-error')).toBeInTheDocument();
  });
});
