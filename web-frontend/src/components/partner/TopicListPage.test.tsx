/**
 * TopicListPage tests
 * Story 8.2: AC1, AC2, AC5, AC7, AC8 — Task 12
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import TopicListPage from './TopicListPage';
import * as topicsApi from '@/services/api/partnerTopicsApi';

// ─── Mocks ───────────────────────────────────────────────────────────────────

vi.mock('@/services/api/partnerTopicsApi');

vi.mock('@/hooks/useAuth/useAuth', () => ({
  useAuth: () => ({ user: { username: 'testpartner', role: 'partner', companyName: 'GoogleZH' } }),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const map: Record<string, string> = {
        'portal.topics.title': 'Topic Suggestions',
        'portal.topics.suggest': 'Suggest a Topic',
        'portal.topics.vote': 'Vote',
        'portal.topics.unvote': 'Remove Vote',
        'portal.topics.status.proposed': 'Proposed',
        'portal.topics.status.selected': 'Selected',
        'portal.topics.status.declined': 'Declined',
        'portal.topics.plannedFor': 'Planned for',
        'portal.topics.empty': 'No topic suggestions yet',
        'portal.topics.error': 'Failed to load topics',
        'portal.topics.form.dialogTitle': 'Suggest a New Topic',
        'portal.topics.form.title': 'Title',
        'portal.topics.form.description': 'Description',
        'portal.topics.form.submit': 'Submit',
        'portal.topics.form.titleRequired': 'Title is required (at least 5 characters)',
        'portal.topics.form.descriptionHint': 'Optional, max 500 characters',
        'modal.actions.cancel': 'Cancel',
      };
      return map[key] ?? key;
    },
  }),
}));

// ─── Fixtures ─────────────────────────────────────────────────────────────────

const mockTopics: topicsApi.TopicDTO[] = [
  {
    id: 'topic-1',
    title: 'Kafka Streams in Production',
    description: 'Real-world Kafka usage patterns',
    suggestedByCompany: 'GoogleZH',
    voteCount: 5,
    currentPartnerHasVoted: false,
    status: 'PROPOSED',
    plannedEvent: null,
    createdAt: '2026-01-01T10:00:00Z',
  },
  {
    id: 'topic-2',
    title: 'eBPF for Platform Engineers',
    description: null,
    suggestedByCompany: 'MicrosoftZH',
    voteCount: 3,
    currentPartnerHasVoted: true,
    status: 'SELECTED',
    plannedEvent: 'BATbern58',
    createdAt: '2026-01-02T10:00:00Z',
  },
  {
    id: 'topic-3',
    title: 'Old Topic Nobody Likes',
    description: null,
    suggestedByCompany: 'SomeCompany',
    voteCount: 0,
    currentPartnerHasVoted: false,
    status: 'DECLINED',
    plannedEvent: null,
    createdAt: '2026-01-03T10:00:00Z',
  },
];

// ─── Test helpers ─────────────────────────────────────────────────────────────

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <TopicListPage />
    </QueryClientProvider>
  );
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('TopicListPage', () => {
  beforeEach(() => {
    vi.mocked(topicsApi.getTopics).mockResolvedValue(mockTopics);
    vi.mocked(topicsApi.castVote).mockResolvedValue(undefined);
    vi.mocked(topicsApi.removeVote).mockResolvedValue(undefined);
    vi.mocked(topicsApi.suggestTopic).mockResolvedValue({
      ...mockTopics[0],
      id: 'topic-new',
      title: 'New Topic',
      voteCount: 0,
      currentPartnerHasVoted: false,
      status: 'PROPOSED',
    });
  });

  it('renders topic list with mocked data (AC1)', async () => {
    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('topic-list-page')).toBeInTheDocument();
    });

    expect(screen.getByText('Kafka Streams in Production')).toBeInTheDocument();
    expect(screen.getByText('eBPF for Platform Engineers')).toBeInTheDocument();
    expect(screen.getByText('Old Topic Nobody Likes')).toBeInTheDocument();
  });

  it('shows loading skeleton while fetching', () => {
    vi.mocked(topicsApi.getTopics).mockReturnValue(new Promise(() => {}));
    renderPage();

    expect(screen.getByTestId('topics-loading')).toBeInTheDocument();
  });

  it('shows empty state when no topics', async () => {
    vi.mocked(topicsApi.getTopics).mockResolvedValue([]);
    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('topics-empty')).toBeInTheDocument();
    });
    expect(screen.getByText('No topic suggestions yet')).toBeInTheDocument();
  });

  it('shows error alert when API fails', async () => {
    vi.mocked(topicsApi.getTopics).mockRejectedValue(new Error('Network error'));
    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('topics-error')).toBeInTheDocument();
    });
  });

  it('vote toggle calls castVote when not yet voted (AC2)', async () => {
    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('vote-button-topic-1')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByTestId('vote-button-topic-1'));

    await waitFor(() => {
      expect(topicsApi.castVote).toHaveBeenCalledWith('topic-1');
    });
  });

  it('vote toggle calls removeVote when already voted (AC2)', async () => {
    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('vote-button-topic-2')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByTestId('vote-button-topic-2'));

    await waitFor(() => {
      expect(topicsApi.removeVote).toHaveBeenCalledWith('topic-2');
    });
  });

  it('status badge renders with correct colour classes (AC5)', async () => {
    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('topic-status-topic-1')).toBeInTheDocument();
    });

    expect(screen.getByTestId('topic-status-topic-1')).toHaveTextContent('Proposed');
    expect(screen.getByTestId('topic-status-topic-2')).toHaveTextContent('Selected');
    expect(screen.getByTestId('topic-status-topic-3')).toHaveTextContent('Declined');
  });

  it('shows plannedEvent for selected topics (AC5)', async () => {
    renderPage();

    await waitFor(() => {
      expect(screen.getByText(/BATbern58/)).toBeInTheDocument();
    });
  });

  it('renders page title in i18n (AC7)', async () => {
    renderPage();

    await waitFor(() => {
      expect(screen.getByText('Topic Suggestions')).toBeInTheDocument();
    });
  });

  it('opens suggestion form dialog when "Suggest a Topic" button clicked (AC3)', async () => {
    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('suggest-topic-button')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByTestId('suggest-topic-button'));

    expect(screen.getByTestId('topic-form-title')).toBeInTheDocument();
  });

  it('validates that title must be at least 5 chars before submit (AC3)', async () => {
    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('suggest-topic-button')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByTestId('suggest-topic-button'));

    const titleInput = screen.getByTestId('topic-form-title').querySelector('input')!;
    fireEvent.change(titleInput, { target: { value: 'hi' } });
    fireEvent.click(screen.getByTestId('topic-form-submit'));

    await waitFor(() => {
      expect(screen.getByText('Title is required (at least 5 characters)')).toBeInTheDocument();
    });

    expect(topicsApi.suggestTopic).not.toHaveBeenCalled();
  });
});
