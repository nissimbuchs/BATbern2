import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { PartnerTopicsTab } from '../PartnerTopicsTab';
import * as partnerTopicsApi from '@/services/api/partnerTopicsApi';
import type { TopicDTO } from '@/services/api/partnerTopicsApi';

vi.mock('@/services/api/partnerTopicsApi');

const mockGetTopics = vi.mocked(partnerTopicsApi.getTopics);
const mockSuggestTopic = vi.mocked(partnerTopicsApi.suggestTopic);
const mockUpdateTopic = vi.mocked(partnerTopicsApi.updateTopic);
const mockDeleteTopic = vi.mocked(partnerTopicsApi.deleteTopic);

const ALPHA_COMPANY = 'AlphaCo';
const BETA_COMPANY = 'BetaCo';

const alphaTopics: TopicDTO[] = [
  {
    id: 'topic-1',
    title: 'Serverless Architecture',
    description: 'Deep dive into AWS Lambda',
    suggestedByCompany: ALPHA_COMPANY,
    voteCount: 3,
    currentPartnerHasVoted: false,
    status: 'PROPOSED',
    plannedEvent: null,
    createdAt: '2026-01-01T10:00:00Z',
  },
  {
    id: 'topic-2',
    title: 'Event-Driven Systems',
    description: null,
    suggestedByCompany: ALPHA_COMPANY,
    voteCount: 1,
    currentPartnerHasVoted: false,
    status: 'SELECTED',
    plannedEvent: 'BATbern58',
    createdAt: '2026-01-02T10:00:00Z',
  },
];

const betaTopic: TopicDTO = {
  id: 'topic-3',
  title: 'Platform Engineering',
  description: null,
  suggestedByCompany: BETA_COMPANY,
  voteCount: 0,
  currentPartnerHasVoted: false,
  status: 'PROPOSED',
  plannedEvent: null,
  createdAt: '2026-01-03T10:00:00Z',
};

function makeQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
}

function renderTab(companyName = ALPHA_COMPANY) {
  const queryClient = makeQueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <PartnerTopicsTab companyName={companyName} />
    </QueryClientProvider>
  );
}

describe('PartnerTopicsTab', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSuggestTopic.mockResolvedValue({} as TopicDTO);
    mockUpdateTopic.mockResolvedValue({} as TopicDTO);
    mockDeleteTopic.mockResolvedValue();
  });

  it('renders loading skeleton while fetching', () => {
    mockGetTopics.mockImplementation(() => new Promise(() => {}));
    renderTab();
    expect(screen.getByTestId('partner-topics-tab-loading')).toBeInTheDocument();
  });

  it('shows error alert when fetch fails', async () => {
    mockGetTopics.mockRejectedValue(new Error('network error'));
    renderTab();
    await waitFor(() => {
      expect(screen.queryByTestId('partner-topics-tab-loading')).not.toBeInTheDocument();
    });
  });

  it('shows empty-state message when company has no topics', async () => {
    mockGetTopics.mockResolvedValue([betaTopic]); // only BetaCo topic
    renderTab(ALPHA_COMPANY);
    await waitFor(() => {
      expect(screen.getByTestId('no-topics-message')).toBeInTheDocument();
    });
  });

  it('filters topics to show only the specified company', async () => {
    mockGetTopics.mockResolvedValue([...alphaTopics, betaTopic]);
    renderTab(ALPHA_COMPANY);
    await waitFor(() => {
      expect(screen.getByText('Serverless Architecture')).toBeInTheDocument();
      expect(screen.getByText('Event-Driven Systems')).toBeInTheDocument();
      expect(screen.queryByText('Platform Engineering')).not.toBeInTheDocument();
    });
  });

  it('shows the planned event for SELECTED topics', async () => {
    mockGetTopics.mockResolvedValue(alphaTopics);
    renderTab(ALPHA_COMPANY);
    await waitFor(() => {
      expect(screen.getByText(/BATbern58/)).toBeInTheDocument();
    });
  });

  describe('Add Topic', () => {
    it('opens the add dialog when "Suggest a Topic" button is clicked', async () => {
      const user = userEvent.setup();
      mockGetTopics.mockResolvedValue(alphaTopics);
      renderTab(ALPHA_COMPANY);
      await waitFor(() => screen.getByTestId('add-topic-button'));

      await user.click(screen.getByTestId('add-topic-button'));
      expect(screen.getByRole('dialog')).toBeInTheDocument();
    });

    it('calls suggestTopic with title, description, AND companyName on submit', async () => {
      const user = userEvent.setup();
      mockGetTopics.mockResolvedValue([]);
      renderTab(ALPHA_COMPANY);
      await waitFor(() => screen.getByTestId('add-topic-button'));

      await user.click(screen.getByTestId('add-topic-button'));

      const titleInput = await screen.findByLabelText(/title/i);
      await user.type(titleInput, 'Microservices Patterns');

      await user.click(screen.getByRole('button', { name: /submit/i }));

      await waitFor(() => {
        expect(mockSuggestTopic).toHaveBeenCalledWith({
          title: 'Microservices Patterns',
          description: '',
          companyName: ALPHA_COMPANY,
        });
      });
    });
  });

  describe('Edit Topic', () => {
    it('opens edit dialog with pre-filled title when edit button is clicked', async () => {
      const user = userEvent.setup();
      mockGetTopics.mockResolvedValue(alphaTopics);
      renderTab(ALPHA_COMPANY);
      await waitFor(() => screen.getByTestId(`edit-topic-${alphaTopics[0].id}`));

      await user.click(screen.getByTestId(`edit-topic-${alphaTopics[0].id}`));

      const titleInput = await screen.findByDisplayValue('Serverless Architecture');
      expect(titleInput).toBeInTheDocument();
    });

    it('calls updateTopic with the edited values on save', async () => {
      const user = userEvent.setup();
      mockGetTopics.mockResolvedValue(alphaTopics);
      renderTab(ALPHA_COMPANY);
      await waitFor(() => screen.getByTestId(`edit-topic-${alphaTopics[0].id}`));

      await user.click(screen.getByTestId(`edit-topic-${alphaTopics[0].id}`));

      const titleInput = await screen.findByDisplayValue('Serverless Architecture');
      await user.clear(titleInput);
      await user.type(titleInput, 'Serverless Deep Dive');

      await user.click(screen.getByRole('button', { name: /update/i }));

      await waitFor(() => {
        expect(mockUpdateTopic).toHaveBeenCalledWith(
          alphaTopics[0].id,
          expect.objectContaining({ title: 'Serverless Deep Dive' })
        );
      });
    });
  });

  describe('Delete Topic', () => {
    it('opens delete confirmation dialog when delete button is clicked', async () => {
      const user = userEvent.setup();
      mockGetTopics.mockResolvedValue(alphaTopics);
      renderTab(ALPHA_COMPANY);
      await waitFor(() => screen.getByTestId(`delete-topic-${alphaTopics[0].id}`));

      await user.click(screen.getByTestId(`delete-topic-${alphaTopics[0].id}`));

      expect(screen.getByTestId('confirm-delete-button')).toBeInTheDocument();
    });

    it('calls deleteTopic when confirmed', async () => {
      const user = userEvent.setup();
      mockGetTopics.mockResolvedValue(alphaTopics);
      renderTab(ALPHA_COMPANY);
      await waitFor(() => screen.getByTestId(`delete-topic-${alphaTopics[0].id}`));

      await user.click(screen.getByTestId(`delete-topic-${alphaTopics[0].id}`));
      await user.click(screen.getByTestId('confirm-delete-button'));

      await waitFor(() => {
        expect(mockDeleteTopic).toHaveBeenCalledWith(alphaTopics[0].id);
      });
    });
  });
});
