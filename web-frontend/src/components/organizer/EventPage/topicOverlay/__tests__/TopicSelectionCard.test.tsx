/**
 * TopicSelectionCard tests (Epic 14, Story 14.F.3)
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import type { Topic } from '@/types/topic.types';
import { TopicSelectionCard } from '../TopicSelectionCard';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: string | Record<string, unknown>) =>
      typeof opts === 'string' ? opts : ((opts?.defaultValue as string) ?? key),
  }),
}));

const topic = (over: Partial<Topic> = {}): Topic =>
  ({
    topicCode: 'cloud-native',
    title: 'Cloud Native',
    category: 'technical',
    stalenessScore: 90,
    colorZone: 'green',
    usageCount: 0,
    lastUsedDate: '2024-01-01T00:00:00Z',
    similarityScores: [],
    ...over,
  }) as unknown as Topic;

beforeEach(() => vi.clearAllMocks());

describe('TopicSelectionCard', () => {
  const handlers = () => ({ onSelect: vi.fn(), onEdit: vi.fn(), onDelete: vi.fn() });

  it('renders title, staleness and category', () => {
    render(
      <TopicSelectionCard
        topic={topic()}
        hasHighSimilarity={false}
        isPending={false}
        {...handlers()}
      />
    );
    expect(screen.getByText('Cloud Native')).toBeInTheDocument();
    expect(screen.getByTestId('topic-card-staleness-cloud-native')).toHaveTextContent('90%');
  });

  it('fires onSelect / onEdit / onDelete', () => {
    const h = handlers();
    render(
      <TopicSelectionCard topic={topic()} hasHighSimilarity={false} isPending={false} {...h} />
    );
    fireEvent.click(screen.getByTestId('topic-card-select-cloud-native'));
    fireEvent.click(screen.getByTestId('topic-card-edit-cloud-native'));
    fireEvent.click(screen.getByTestId('topic-card-delete-cloud-native'));
    expect(h.onSelect).toHaveBeenCalledTimes(1);
    expect(h.onEdit).toHaveBeenCalledTimes(1);
    expect(h.onDelete).toHaveBeenCalledTimes(1);
  });

  it('disables Delete when usageCount > 0', () => {
    render(
      <TopicSelectionCard
        topic={topic({ usageCount: 3 })}
        hasHighSimilarity={false}
        isPending={false}
        {...handlers()}
      />
    );
    expect(screen.getByTestId('topic-card-delete-cloud-native')).toBeDisabled();
  });

  it('shows "Select anyway…" for a too-recent (red) topic', () => {
    render(
      <TopicSelectionCard
        topic={topic({ stalenessScore: 20, colorZone: 'red' })}
        hasHighSimilarity={false}
        isPending={false}
        {...handlers()}
      />
    );
    expect(screen.getByTestId('topic-card-select-cloud-native')).toHaveTextContent(
      'Select anyway…'
    );
  });

  it('shows the inline similarity warning when flagged', () => {
    render(
      <TopicSelectionCard topic={topic()} hasHighSimilarity isPending={false} {...handlers()} />
    );
    expect(screen.getByTestId('topic-card-similarity-cloud-native')).toBeInTheDocument();
  });
});
