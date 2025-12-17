/**
 * TopicBacklogManager Component Tests (Story 5.2 - Frontend Tests)
 *
 * Tests main topic selection interface:
 * - Rendering with loading/error states
 * - Filter panel integration
 * - Topic list display and selection
 * - Topic details panel interaction
 * - Event topic selection callback
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { TopicBacklogManager } from './TopicBacklogManager';
import * as useTopicsHook from '@/hooks/useTopics';
import type { Topic, TopicListResponse } from '@/types/topic.types';

// Mock the useTopics hook
vi.mock('@/hooks/useTopics', () => ({
  useTopics: vi.fn(),
}));

// Mock react-i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, defaultValue?: string) => defaultValue || key,
  }),
}));

// Mock child components for isolated testing
vi.mock('./TopicFilterPanel', () => ({
  TopicFilterPanel: ({ onFilterChange }: { onFilterChange: (filters: unknown) => void }) => (
    <div data-testid="topic-filter-panel">
      <button onClick={() => onFilterChange({ category: 'technical' })}>Apply Filter</button>
    </div>
  ),
}));

vi.mock('./TopicList', () => ({
  TopicList: ({
    topics,
    onTopicSelect,
    selectedTopicId,
  }: {
    topics: Topic[];
    onTopicSelect: (topic: Topic) => void;
    selectedTopicId?: string;
  }) => (
    <div data-testid="topic-list">
      {topics.map((topic) => (
        <div
          key={topic.id}
          data-testid={`topic-${topic.id}`}
          data-selected={topic.id === selectedTopicId}
          onClick={() => onTopicSelect(topic)}
        >
          {topic.title}
        </div>
      ))}
    </div>
  ),
}));

vi.mock('./TopicDetailsPanel', () => ({
  TopicDetailsPanel: ({
    topic,
    eventCode,
    onTopicConfirm,
  }: {
    topic: Topic;
    eventCode?: string;
    onTopicConfirm: (id: string) => void;
  }) => (
    <div data-testid="topic-details-panel">
      <div>{topic.title}</div>
      {eventCode && <button onClick={() => onTopicConfirm(topic.id)}>Select for Event</button>}
    </div>
  ),
}));

describe('TopicBacklogManager', () => {
  let queryClient: QueryClient;
  const mockTopics: Topic[] = [
    {
      id: 'topic-123',
      title: 'Cloud Native Architecture',
      description: 'Modern cloud patterns',
      category: 'technical',
      stalenessScore: 85,
      usageCount: 3,
      lastUsedDate: '2024-01-15',
      isActive: true,
      createdDate: '2023-01-01',
    },
    {
      id: 'topic-456',
      title: 'Leadership Skills',
      description: 'Management best practices',
      category: 'management',
      stalenessScore: 50,
      usageCount: 5,
      lastUsedDate: '2024-06-01',
      isActive: true,
      createdDate: '2023-02-01',
    },
  ];

  const mockTopicListResponse: TopicListResponse = {
    data: mockTopics,
    pagination: { page: 1, limit: 20, total: 2 },
  };

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false, gcTime: 0 },
      },
    });
    vi.clearAllMocks();
  });

  const renderComponent = (props = {}) => {
    return render(
      <MemoryRouter>
        <QueryClientProvider client={queryClient}>
          <TopicBacklogManager {...props} />
        </QueryClientProvider>
      </MemoryRouter>
    );
  };

  it('should render with title and subtitle', () => {
    vi.mocked(useTopicsHook.useTopics).mockReturnValue({
      data: mockTopicListResponse,
      isLoading: false,
      isError: false,
      error: null,
    } as ReturnType<typeof useTopicsHook.useTopics>);

    renderComponent();

    expect(screen.getByText('Topic Backlog Manager')).toBeInTheDocument();
    expect(
      screen.getByText(/Select topics from the backlog with intelligent suggestions/)
    ).toBeInTheDocument();
  });

  it('should display loading state', () => {
    vi.mocked(useTopicsHook.useTopics).mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
      error: null,
    } as ReturnType<typeof useTopicsHook.useTopics>);

    renderComponent();

    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  it('should display error state', () => {
    const error = new Error('Failed to load topics');
    vi.mocked(useTopicsHook.useTopics).mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error,
    } as ReturnType<typeof useTopicsHook.useTopics>);

    renderComponent();

    expect(screen.getByText(/Failed to load topics/)).toBeInTheDocument();
    expect(screen.getByText(/Failed to load topics/)).toHaveAttribute(
      'class',
      expect.stringContaining('MuiAlert')
    );
  });

  it('should render filter panel, topic list, and details placeholder', () => {
    vi.mocked(useTopicsHook.useTopics).mockReturnValue({
      data: mockTopicListResponse,
      isLoading: false,
      isError: false,
      error: null,
    } as ReturnType<typeof useTopicsHook.useTopics>);

    renderComponent();

    expect(screen.getByTestId('topic-filter-panel')).toBeInTheDocument();
    expect(screen.getByTestId('topic-list')).toBeInTheDocument();
    expect(screen.getByText('Select a topic to view details')).toBeInTheDocument();
  });

  it('should display topics in the list', () => {
    vi.mocked(useTopicsHook.useTopics).mockReturnValue({
      data: mockTopicListResponse,
      isLoading: false,
      isError: false,
      error: null,
    } as ReturnType<typeof useTopicsHook.useTopics>);

    renderComponent();

    expect(screen.getByText('Cloud Native Architecture')).toBeInTheDocument();
    expect(screen.getByText('Leadership Skills')).toBeInTheDocument();
  });

  it('should show topic details when topic is selected', async () => {
    const user = userEvent.setup();
    vi.mocked(useTopicsHook.useTopics).mockReturnValue({
      data: mockTopicListResponse,
      isLoading: false,
      isError: false,
      error: null,
    } as ReturnType<typeof useTopicsHook.useTopics>);

    renderComponent();

    const topicElement = screen.getByTestId('topic-topic-123');
    await user.click(topicElement);

    await waitFor(() => {
      expect(screen.getByTestId('topic-details-panel')).toBeInTheDocument();
      expect(screen.queryByText('Select a topic to view details')).not.toBeInTheDocument();
    });
  });

  it('should call onTopicSelected callback when topic is confirmed', async () => {
    const user = userEvent.setup();
    const onTopicSelected = vi.fn();

    vi.mocked(useTopicsHook.useTopics).mockReturnValue({
      data: mockTopicListResponse,
      isLoading: false,
      isError: false,
      error: null,
    } as ReturnType<typeof useTopicsHook.useTopics>);

    renderComponent({ eventCode: 'BATbern56', onTopicSelected });

    // Select topic
    const topicElement = screen.getByTestId('topic-topic-123');
    await user.click(topicElement);

    // Confirm selection
    const confirmButton = await screen.findByText('Select for Event');
    await user.click(confirmButton);

    expect(onTopicSelected).toHaveBeenCalledWith('topic-123');
  });

  it('should update filters when filter panel changes', async () => {
    const user = userEvent.setup();
    const useTopicsSpy = vi.mocked(useTopicsHook.useTopics);

    useTopicsSpy.mockReturnValue({
      data: mockTopicListResponse,
      isLoading: false,
      isError: false,
      error: null,
    } as ReturnType<typeof useTopicsHook.useTopics>);

    renderComponent();

    const filterButton = screen.getByText('Apply Filter');
    await user.click(filterButton);

    // Verify useTopics was called with updated filters
    await waitFor(() => {
      expect(useTopicsSpy).toHaveBeenCalledWith(
        expect.objectContaining({
          category: 'technical',
          page: 1, // Reset to page 1 on filter change
        })
      );
    });
  });

  it('should initialize with default filters (sort by staleness descending)', () => {
    const useTopicsSpy = vi.mocked(useTopicsHook.useTopics);

    useTopicsSpy.mockReturnValue({
      data: mockTopicListResponse,
      isLoading: false,
      isError: false,
      error: null,
    } as ReturnType<typeof useTopicsHook.useTopics>);

    renderComponent();

    expect(useTopicsSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        page: 1,
        limit: 20,
        sort: '-stalenessScore',
      })
    );
  });

  it('should not show topic selection button when eventCode is not provided', async () => {
    const user = userEvent.setup();
    vi.mocked(useTopicsHook.useTopics).mockReturnValue({
      data: mockTopicListResponse,
      isLoading: false,
      isError: false,
      error: null,
    } as ReturnType<typeof useTopicsHook.useTopics>);

    renderComponent(); // No eventCode

    const topicElement = screen.getByTestId('topic-topic-123');
    await user.click(topicElement);

    await waitFor(() => {
      expect(screen.queryByText('Select for Event')).not.toBeInTheDocument();
    });
  });

  it('should handle empty topic list', () => {
    vi.mocked(useTopicsHook.useTopics).mockReturnValue({
      data: { data: [], pagination: { page: 1, limit: 20, total: 0 } },
      isLoading: false,
      isError: false,
      error: null,
    } as ReturnType<typeof useTopicsHook.useTopics>);

    renderComponent();

    expect(screen.getByTestId('topic-list')).toBeInTheDocument();
    expect(screen.queryByText(/Cloud Native/i)).not.toBeInTheDocument();
  });
});
