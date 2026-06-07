/**
 * UnsuppressDialog Tests
 * Story 10.29: SES Bounce Processing — Newsletter List Hygiene
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { I18nextProvider } from 'react-i18next';
import i18n from '../../../../i18n/config';
import UnsuppressDialog from '../UnsuppressDialog';

const mockMutate = vi.fn();

vi.mock('@/hooks/useNewsletterSubscribers', () => ({
  useUnsuppressSubscriber: () => ({
    mutate: mockMutate,
    isPending: false,
    isError: false,
    reset: vi.fn(),
  }),
}));

const mockSubscriber = {
  id: 'sub-1',
  email: 'bounced@example.com',
  firstName: 'Diana',
  language: 'en',
  source: 'website',
  subscribedAt: '2026-01-15T10:00:00Z',
  unsubscribedAt: null,
  suppressedAt: '2026-03-01T08:00:00Z',
  bounceType: 'Permanent',
  bounceCount: 3,
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

describe('UnsuppressDialog', () => {
  const mockOnClose = vi.fn();
  const mockOnSuccess = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should_renderDialog_when_openIsTrue', () => {
    renderWithProviders(
      <UnsuppressDialog
        open={true}
        subscriber={mockSubscriber}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
      />
    );

    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('should_notRenderDialog_when_openIsFalse', () => {
    renderWithProviders(
      <UnsuppressDialog
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
      <UnsuppressDialog
        open={true}
        subscriber={mockSubscriber}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
      />
    );

    expect(screen.getByText(/bounced@example.com/)).toBeInTheDocument();
  });

  it('should_displayBounceDetails_when_subscriberProvided', () => {
    renderWithProviders(
      <UnsuppressDialog
        open={true}
        subscriber={mockSubscriber}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
      />
    );

    expect(screen.getByText(/Permanent/)).toBeInTheDocument();
  });

  it('should_closeDialog_when_cancelClicked', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <UnsuppressDialog
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
      <UnsuppressDialog
        open={true}
        subscriber={mockSubscriber}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
      />
    );

    const confirmButton = screen.getByTestId('confirm-unsuppress');
    await user.click(confirmButton);

    expect(mockMutate).toHaveBeenCalledWith(
      'sub-1',
      expect.objectContaining({ onSuccess: expect.any(Function) })
    );
  });

  it('should_callOnSuccess_when_mutationSucceeds', async () => {
    mockMutate.mockImplementation((id: string, options: { onSuccess: () => void }) => {
      options.onSuccess();
    });

    const user = userEvent.setup();
    renderWithProviders(
      <UnsuppressDialog
        open={true}
        subscriber={mockSubscriber}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
      />
    );

    const confirmButton = screen.getByTestId('confirm-unsuppress');
    await user.click(confirmButton);

    expect(mockOnSuccess).toHaveBeenCalledTimes(1);
    expect(mockOnClose).toHaveBeenCalledTimes(1);
  });
});
