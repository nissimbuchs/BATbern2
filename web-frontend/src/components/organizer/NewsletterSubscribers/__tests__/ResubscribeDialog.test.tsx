/**
 * ResubscribeDialog Tests
 * Story 10.28: Newsletter Subscriber Management Page
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, delay } from 'msw';
import { api } from '@/test/msw/api';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { I18nextProvider } from 'react-i18next';
import i18n from '../../../../i18n/config';
import ResubscribeDialog from '../ResubscribeDialog';
import { aSubscriber } from '@/test/fixtures';

const mockSubscriber = aSubscriber({
  id: 'sub-2',
  email: 'inactive@example.com',
  firstName: 'Bob',
  language: 'de',
  source: 'import',
  subscribedAt: '2025-12-01T08:00:00Z',
  unsubscribedAt: '2026-02-01T12:00:00Z',
});

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

describe('ResubscribeDialog', () => {
  const mockOnClose = vi.fn();
  const mockOnSuccess = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should_renderDialog_when_openIsTrue', () => {
    renderWithProviders(
      <ResubscribeDialog
        open={true}
        subscriber={mockSubscriber}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
      />
    );

    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByText(/re-subscribe subscriber/i)).toBeInTheDocument();
  });

  it('should_notRenderDialog_when_openIsFalse', () => {
    renderWithProviders(
      <ResubscribeDialog
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
      <ResubscribeDialog
        open={true}
        subscriber={mockSubscriber}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
      />
    );

    expect(screen.getByText(/inactive@example.com/)).toBeInTheDocument();
  });

  it('should_closeDialog_when_cancelClicked', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <ResubscribeDialog
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

  it('should_callTheApiAndClose_when_confirmClicked', async () => {
    // Real hook, real request (MSW) that takes a moment, as in production: the dialog reports
    // success and closes once the call returns. A hook mock that called onSuccess synchronously
    // hid that the dialog stayed open (#818 defect 3, reported again 2026-11-21).
    api.on('resubscribeSubscriberById', async () => {
      await delay(50);
      return HttpResponse.json(mockSubscriber);
    });
    const user = userEvent.setup();
    renderWithProviders(
      <ResubscribeDialog
        open={true}
        subscriber={mockSubscriber}
        onClose={mockOnClose}
        onSuccess={mockOnSuccess}
      />
    );

    const confirmButton = screen.getByRole('button', { name: /re-subscribe/i });
    await user.click(confirmButton);

    await waitFor(() => expect(mockOnSuccess).toHaveBeenCalledTimes(1));
    expect(api.calls('resubscribeSubscriberById')[0].params.id).toBe('sub-2');
    expect(mockOnClose).toHaveBeenCalledTimes(1);
  });
});
