/**
 * TopicDetailsPanel Tests - Focused component tests for topic details display
 */
import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { TopicDetailsPanel } from './TopicDetailsPanel';
import type { Topic } from '@/types/topic.types';
import * as useTopicsHook from '@/hooks/useTopics';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, def?: string, options?: { count?: number }) => {
      if (def && options?.count !== undefined) {
        return def.replace('{{count}}', String(options.count));
      }
      return def || key;
    },
  }),
}));

vi.mock('@/hooks/useTopics', () => ({
  useSimilarTopics: vi.fn(() => ({ data: [], isLoading: false })),
  useSelectTopicForEvent: vi.fn(),
  useTopicUsageHistory: () => ({ data: [], isLoading: false }),
}));

const mockTopic: Topic = {
  topicCode: 'topic-123',
  title: 'Cloud Native Architecture',
  description: 'Modern cloud patterns',
  category: 'technical',
  stalenessScore: 85,
  usageCount: 3,
  lastUsedDate: '2024-01-15',
  active: true,
  createdDate: '2023-01-01',
  similarityScores: [],
  colorZone: 'green',
  status: 'available',
};

describe('TopicDetailsPanel', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false, gcTime: 0 },
      },
    });
    vi.clearAllMocks();

    // Setup default mock for useSelectTopicForEvent that calls onSuccess immediately
    vi.mocked(useTopicsHook.useSelectTopicForEvent).mockReturnValue({
      mutate: vi.fn((variables, options) => {
        // Call onSuccess callback immediately to simulate successful mutation
        if (options?.onSuccess) {
          options.onSuccess(undefined as any, variables, undefined);
        }
      }),
      isPending: false,
    } as any);
  });

  const renderWithProviders = (ui: React.ReactElement) => {
    return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
  };

  it('should render topic title and description', () => {
    renderWithProviders(<TopicDetailsPanel topic={mockTopic} />);
    expect(screen.getByText('Cloud Native Architecture')).toBeInTheDocument();
    expect(screen.getByText('Modern cloud patterns')).toBeInTheDocument();
  });

  it('should display staleness score with label', () => {
    renderWithProviders(<TopicDetailsPanel topic={mockTopic} />);
    expect(screen.getByText('85%')).toBeInTheDocument();
  });

  it('should show category chip', () => {
    renderWithProviders(<TopicDetailsPanel topic={mockTopic} />);
    expect(screen.getByText('technical')).toBeInTheDocument();
  });

  it('should display usage statistics', () => {
    renderWithProviders(<TopicDetailsPanel topic={mockTopic} />);
    expect(screen.getByText(/Usage Count/i)).toBeInTheDocument();
    // "Last Used" appears in both alert message and metrics section
    expect(screen.getAllByText(/Last Used/i).length).toBeGreaterThan(0);
  });

  it('should show topic selection button when eventCode is provided', () => {
    renderWithProviders(
      <TopicDetailsPanel topic={mockTopic} eventCode="BATbern56" onTopicConfirm={vi.fn()} />
    );
    expect(screen.getByRole('button', { name: /Select for Event/i })).toBeInTheDocument();
  });

  it('should call onTopicConfirm when selection button is clicked', async () => {
    const user = userEvent.setup();
    const onTopicConfirm = vi.fn();
    renderWithProviders(
      <TopicDetailsPanel topic={mockTopic} eventCode="BATbern56" onTopicConfirm={onTopicConfirm} />
    );

    await user.click(screen.getByRole('button', { name: /Select for Event/i }));
    expect(onTopicConfirm).toHaveBeenCalledWith('topic-123');
  });

  it('should not show selection button when eventCode is not provided', () => {
    renderWithProviders(<TopicDetailsPanel topic={mockTopic} />);
    expect(screen.queryByRole('button', { name: /Select for Event/i })).not.toBeInTheDocument();
  });

  it('should display similar topics warning when similarity is high', () => {
    const topicWithSimilarity: Topic = {
      ...mockTopic,
      similarityScores: [{ topicCode: 'cloud-security', score: 0.75 }],
    };

    // Mock the similar topics hook to return a similar topic
    vi.mocked(useTopicsHook.useSimilarTopics).mockReturnValue({
      data: [
        {
          topicCode: 'cloud-security',
          title: 'Similar Topic',
          category: 'technical',
          stalenessScore: 80,
          usageCount: 2,
          lastUsedDate: '2024-02-01',
          active: true,
          createdDate: '2023-01-01',
          description: 'Similar',
          colorZone: 'green',
          status: 'available',
        },
      ],
      isLoading: false,
    } as ReturnType<typeof useTopicsHook.useSimilarTopics>);

    renderWithProviders(<TopicDetailsPanel topic={topicWithSimilarity} />);
    expect(screen.getByText(/similar topics detected/i)).toBeInTheDocument();
  });
});
