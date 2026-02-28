/**
 * CreateTopicModal Tests (Story 5.2)
 *
 * Tests:
 * - Create mode: renders with empty form, create button label
 * - Edit mode: renders with pre-populated fields, save button label
 * - Validation: title and category required
 * - Submit: calls createTopic / updateTopic with correct data
 * - Cancel: calls onClose
 * - Error state: shows error alert when mutation fails
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { CreateTopicModal } from './CreateTopicModal';
import type { Topic } from '@/types/topic.types';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, defaultValue?: string) => defaultValue || key,
  }),
}));

vi.mock('@/services/topicService', () => ({
  topicService: {
    createTopic: vi.fn().mockResolvedValue({ topicCode: 'new-topic', title: 'New Topic' }),
    updateTopic: vi.fn().mockResolvedValue({ topicCode: 'existing-topic', title: 'Updated' }),
  },
}));

const mockTopic: Topic = {
  topicCode: 'existing-topic',
  title: 'Existing Topic',
  description: 'An existing topic description',
  category: 'technical',
  stalenessScore: 85,
  usageCount: 2,
  lastUsedDate: '2024-01-15',
  active: true,
  createdDate: '2023-01-01',
  colorZone: 'green',
  status: 'AVAILABLE',
};

describe('CreateTopicModal', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
    });
    vi.clearAllMocks();
  });

  const renderModal = (props: Partial<React.ComponentProps<typeof CreateTopicModal>> = {}) =>
    render(
      <QueryClientProvider client={queryClient}>
        <CreateTopicModal open={true} onClose={vi.fn()} {...props} />
      </QueryClientProvider>
    );

  it('renders in create mode with empty fields and create button', () => {
    renderModal();
    expect(screen.getByTestId('create-topic-modal')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Create New Topic/i })).toBeInTheDocument();
    expect(screen.getByTestId('submit-topic-button')).toHaveTextContent('Create Topic');
    expect(screen.getByTestId('topic-title-input')).toHaveValue('');
  });

  it('renders in edit mode with pre-populated title and save button', () => {
    renderModal({ topic: mockTopic });
    expect(screen.getByRole('heading', { name: /Edit Topic/i })).toBeInTheDocument();
    expect(screen.getByTestId('submit-topic-button')).toHaveTextContent('Save Changes');
    expect(screen.getByTestId('topic-title-input')).toHaveValue('Existing Topic');
  });

  it('does not render content when closed', () => {
    render(
      <QueryClientProvider client={queryClient}>
        <CreateTopicModal open={false} onClose={vi.fn()} />
      </QueryClientProvider>
    );
    expect(screen.queryByTestId('create-topic-modal')).not.toBeInTheDocument();
  });

  it('shows title required error when submitted with empty title', async () => {
    const user = userEvent.setup();
    renderModal();
    await user.click(screen.getByTestId('submit-topic-button'));
    expect(screen.getByText('Title is required')).toBeInTheDocument();
  });

  it('shows category required error when submitted without category', async () => {
    const user = userEvent.setup();
    renderModal();
    await user.type(screen.getByTestId('topic-title-input'), 'A Topic Title');
    await user.click(screen.getByTestId('submit-topic-button'));
    expect(screen.getByText('Category is required')).toBeInTheDocument();
  });

  it('calls onClose when cancel button is clicked', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    renderModal({ onClose });
    await user.click(screen.getByTestId('cancel-topic-button'));
    expect(onClose).toHaveBeenCalled();
  });

  it('calls createTopic on valid form submit in create mode', async () => {
    const { topicService } = await import('@/services/topicService');
    const user = userEvent.setup();
    const onClose = vi.fn();
    renderModal({ onClose });

    await user.type(screen.getByTestId('topic-title-input'), 'New Topic Title');

    // Open the category select and choose Technical
    const combobox = screen.getByRole('combobox');
    await user.click(combobox);
    const option = await screen.findByRole('option', { name: /technical/i });
    await user.click(option);

    await user.click(screen.getByTestId('submit-topic-button'));

    await waitFor(() => {
      expect(topicService.createTopic).toHaveBeenCalledWith(
        expect.objectContaining({
          title: 'New Topic Title',
          category: 'technical',
        })
      );
    });
  });

  it('calls updateTopic on valid submit in edit mode', async () => {
    const { topicService } = await import('@/services/topicService');
    const user = userEvent.setup();
    const onClose = vi.fn();
    renderModal({ topic: mockTopic, onClose });

    // Update the title
    const titleInput = screen.getByTestId('topic-title-input');
    await user.clear(titleInput);
    await user.type(titleInput, 'Updated Title');

    await user.click(screen.getByTestId('submit-topic-button'));

    await waitFor(() => {
      expect(topicService.updateTopic).toHaveBeenCalledWith(
        'existing-topic',
        expect.objectContaining({ title: 'Updated Title' })
      );
    });
  });

  it('calls onSuccess callback after successful creation', async () => {
    const user = userEvent.setup();
    const onSuccess = vi.fn();
    renderModal({ onSuccess });

    await user.type(screen.getByTestId('topic-title-input'), 'My Topic');
    const combobox = screen.getByRole('combobox');
    await user.click(combobox);
    const option = await screen.findByRole('option', { name: /management/i });
    await user.click(option);

    await user.click(screen.getByTestId('submit-topic-button'));

    await waitFor(() => {
      expect(onSuccess).toHaveBeenCalled();
    });
  });

  it('shows error alert when creation fails', async () => {
    const { topicService } = await import('@/services/topicService');
    vi.mocked(topicService.createTopic).mockRejectedValueOnce(new Error('Server error'));
    const user = userEvent.setup();
    renderModal();

    await user.type(screen.getByTestId('topic-title-input'), 'Failing Topic');
    const combobox = screen.getByRole('combobox');
    await user.click(combobox);
    const option = await screen.findByRole('option', { name: /technical/i });
    await user.click(option);

    await user.click(screen.getByTestId('submit-topic-button'));

    await waitFor(() => {
      expect(screen.getByTestId('topic-form-error')).toBeInTheDocument();
    });
  });

  it('fills description field when provided', async () => {
    const user = userEvent.setup();
    renderModal();
    const descriptionInput = screen.getByTestId('topic-description-input');
    await user.type(descriptionInput, 'A detailed description');
    expect(descriptionInput).toHaveValue('A detailed description');
  });
});
