/**
 * TopicList Tests - Focused component tests for topic list display
 */
import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TopicList } from './TopicList';
import type { Topic } from '@/types/topic.types';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string, def?: string) => def || key }),
}));

const mockTopics: Topic[] = [
  {
    id: '1',
    title: 'Cloud Native',
    category: 'technical',
    stalenessScore: 85,
    usageCount: 3,
    lastUsedDate: '2024-01-15',
    isActive: true,
    createdDate: '2023-01-01',
    description: 'Test',
  },
  {
    id: '2',
    title: 'Leadership',
    category: 'management',
    stalenessScore: 50,
    usageCount: 5,
    lastUsedDate: '2024-06-01',
    isActive: true,
    createdDate: '2023-02-01',
    description: 'Test',
  },
];

describe('TopicList', () => {
  it('should render all topics', () => {
    render(
      <TopicList
        topics={mockTopics}
        onTopicSelect={vi.fn()}
        pagination={{ page: 1, limit: 20, total: 2 }}
        onPageChange={vi.fn()}
      />
    );
    expect(screen.getByText('Cloud Native')).toBeInTheDocument();
    expect(screen.getByText('Leadership')).toBeInTheDocument();
  });

  it('should call onTopicSelect when topic is clicked', async () => {
    const user = userEvent.setup();
    const onTopicSelect = vi.fn();
    render(
      <TopicList
        topics={mockTopics}
        onTopicSelect={onTopicSelect}
        pagination={{ page: 1, limit: 20, total: 2 }}
        onPageChange={vi.fn()}
      />
    );

    await user.click(screen.getByText('Cloud Native'));
    expect(onTopicSelect).toHaveBeenCalledWith(mockTopics[0]);
  });

  it('should highlight selected topic', () => {
    render(
      <TopicList
        topics={mockTopics}
        selectedTopicId="1"
        onTopicSelect={vi.fn()}
        pagination={{ page: 1, limit: 20, total: 2 }}
        onPageChange={vi.fn()}
      />
    );
    const selectedButton = screen.getByText('Cloud Native').closest('[role="button"]');
    expect(selectedButton).toHaveClass('Mui-selected');
  });

  it('should display staleness scores with color coding', () => {
    render(
      <TopicList
        topics={mockTopics}
        onTopicSelect={vi.fn()}
        pagination={{ page: 1, limit: 20, total: 2 }}
        onPageChange={vi.fn()}
      />
    );
    expect(screen.getByText('85%')).toBeInTheDocument(); // High staleness = safe
    expect(screen.getByText('50%')).toBeInTheDocument(); // Medium staleness
  });

  it('should handle pagination', async () => {
    const user = userEvent.setup();
    const onPageChange = vi.fn();
    render(
      <TopicList
        topics={mockTopics}
        onTopicSelect={vi.fn()}
        pagination={{ page: 1, limit: 20, total: 50 }}
        onPageChange={onPageChange}
      />
    );

    const nextButton = screen.getByRole('button', { name: /next/i });
    await user.click(nextButton);
    expect(onPageChange).toHaveBeenCalledWith(2);
  });
});
