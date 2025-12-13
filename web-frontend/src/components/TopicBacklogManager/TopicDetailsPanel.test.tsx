/**
 * TopicDetailsPanel Tests - Focused component tests for topic details display
 */
import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TopicDetailsPanel } from './TopicDetailsPanel';
import type { Topic } from '@/types/topic.types';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string, def?: string) => def || key }),
}));

vi.mock('@/hooks/useTopics', () => ({
  useSimilarTopics: () => ({ data: [], isLoading: false }),
  useSelectTopicForEvent: () => ({ mutate: vi.fn(), isLoading: false }),
}));

const mockTopic: Topic = {
  id: 'topic-123',
  title: 'Cloud Native Architecture',
  description: 'Modern cloud patterns',
  category: 'technical',
  stalenessScore: 85,
  usageCount: 3,
  lastUsedDate: '2024-01-15',
  isActive: true,
  createdDate: '2023-01-01',
};

describe('TopicDetailsPanel', () => {
  it('should render topic title and description', () => {
    render(<TopicDetailsPanel topic={mockTopic} />);
    expect(screen.getByText('Cloud Native Architecture')).toBeInTheDocument();
    expect(screen.getByText('Modern cloud patterns')).toBeInTheDocument();
  });

  it('should display staleness score with label', () => {
    render(<TopicDetailsPanel topic={mockTopic} />);
    expect(screen.getByText(/Staleness Score/i)).toBeInTheDocument();
    expect(screen.getByText('85')).toBeInTheDocument();
  });

  it('should show category chip', () => {
    render(<TopicDetailsPanel topic={mockTopic} />);
    expect(screen.getByText('technical')).toBeInTheDocument();
  });

  it('should display usage statistics', () => {
    render(<TopicDetailsPanel topic={mockTopic} />);
    expect(screen.getByText(/Used 3 times/i)).toBeInTheDocument();
    expect(screen.getByText(/Last used.*2024-01-15/i)).toBeInTheDocument();
  });

  it('should show topic selection button when eventCode is provided', () => {
    render(<TopicDetailsPanel topic={mockTopic} eventCode="BATbern56" onTopicConfirm={vi.fn()} />);
    expect(screen.getByRole('button', { name: /Select for Event/i })).toBeInTheDocument();
  });

  it('should call onTopicConfirm when selection button is clicked', async () => {
    const user = userEvent.setup();
    const onTopicConfirm = vi.fn();
    render(
      <TopicDetailsPanel topic={mockTopic} eventCode="BATbern56" onTopicConfirm={onTopicConfirm} />
    );

    await user.click(screen.getByRole('button', { name: /Select for Event/i }));
    expect(onTopicConfirm).toHaveBeenCalledWith('topic-123');
  });

  it('should not show selection button when eventCode is not provided', () => {
    render(<TopicDetailsPanel topic={mockTopic} />);
    expect(screen.queryByRole('button', { name: /Select for Event/i })).not.toBeInTheDocument();
  });

  it('should display similar topics warning when similarity is high', () => {
    const topicWithSimilarity = {
      ...mockTopic,
      similarityScores: [{ topicId: 'topic-456', score: 0.75 }],
    };
    render(<TopicDetailsPanel topic={topicWithSimilarity} />);
    expect(screen.getByText(/Similar topics detected/i)).toBeInTheDocument();
  });
});
