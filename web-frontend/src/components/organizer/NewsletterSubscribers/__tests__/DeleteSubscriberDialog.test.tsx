/**
 * DeleteSubscriberDialog Tests
 * Story 10.28: Newsletter Subscriber Management Page
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { I18nextProvider } from 'react-i18next';
import i18n from '../../../../i18n/config';
import DeleteSubscriberDialog from '../DeleteSubscriberDialog';

const mockMutate = vi.fn();

vi.mock('@/hooks/useNewsletterSubscribers', () => ({
  useDeleteSubscriber: () => ({
    mutate: mockMutate,
    isPending: false,
    isError: false,
    reset: vi.fn(),
  }),
}));

const mockSubscriber = {
  id: 'sub-1',
  email: 'delete-me@example.com',
  firstName: 'Charlie',
  language: 'en',
  source: 'website',
  subscribedAt: '2026-01-15T10:00:00Z',
  unsubscribedAt: null,
};

const renderWithProviders = (ui: React.ReactElement) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <I18nextProvider i18n={i18n}>{ui}</I18nextProvider>
    </QueryClientProvider>
  );
};

describe('DeleteSubscriberDialog', () => {
  const mockOnClose = vi.fn();
  const mockOnSuccess = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should_renderDialog_when_openIsTrue', () => {
    renderWithProviders(
      <DeleteSubscriberDialog
        open={true}
        subscriber={mockSubscriber}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
      />
    );

    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByText(/delete subscriber/i)).toBeInTheDocument();
  });

  it('should_notRenderDialog_when_openIsFalse', () => {
    renderWithProviders(
      <DeleteSubscriberDialog
        open={false}
        subscriber={mockSubscriber}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
      />
    );

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('should_displayEmail_when_subscriberProvided', () => {
    renderWithProviders(
      <DeleteSubscriberDialog
        open={true}
        subscriber={mockSubscriber}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
      />
    );

    expect(screen.getByText(/delete-me@example.com/)).toBeInTheDocument();
  });

  it('should_displayPermanentWarning_when_rendered', () => {
    renderWithProviders(
      <DeleteSubscriberDialog
        open={true}
        subscriber={mockSubscriber}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
      />
    );

    expect(screen.getByText(/cannot be undone/i)).toBeInTheDocument();
  });

  it('should_closeDialog_when_cancelClicked', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <DeleteSubscriberDialog
        open={true}
        subscriber={mockSubscriber}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
      />
    );

    const cancelButton = screen.getByRole('button', { name: /cancel/i });
    await user.click(cancelButton);

    expect(mockOnClose).toHaveBeenCalledTimes(1);
  });

  it('should_callMutate_when_confirmClicked', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <DeleteSubscriberDialog
        open={true}
        subscriber={mockSubscriber}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
      />
    );

    const deleteButton = screen.getByRole('button', { name: /delete/i });
    await user.click(deleteButton);

    expect(mockMutate).toHaveBeenCalledWith(
      'sub-1',
      expect.objectContaining({ onSuccess: expect.any(Function) })
    );
  });
});
