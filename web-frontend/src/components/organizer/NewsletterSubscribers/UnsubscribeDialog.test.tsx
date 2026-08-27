/**
 * #818 defect 3: "the confirmation dialog stays open after the action completes".
 *
 * There was no test coverage for this component at all, which is why the behaviour could not be
 * settled by reading the code — both the dialog and its parent LOOK correct.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import UnsubscribeDialog from './UnsubscribeDialog';
import '../../../i18n/config';

// The component imports from the '@/hooks/useNewsletterSubscribers' barrel, so the mock must
// target the barrel rather than the mutations module.
const mutate = vi.fn((_id: string, opts?: { onSuccess?: () => void }) => opts?.onSuccess?.());
vi.mock('@/hooks/useNewsletterSubscribers', () => ({
  useUnsubscribeSubscriber: () => ({
    mutate,
    reset: vi.fn(),
    isPending: false,
    isError: false,
  }),
}));

const subscriber = {
  id: 'sub-1',
  email: 'someone@example.com',
  status: 'ACTIVE',
  subscribedAt: '2026-01-01T00:00:00Z',
} as never;

describe('UnsubscribeDialog (#818)', () => {
  let queryClient: QueryClient;
  const onClose = vi.fn();
  const onSuccess = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  });

  const renderDialog = () =>
    render(
      <QueryClientProvider client={queryClient}>
        <UnsubscribeDialog open subscriber={subscriber} onClose={onClose} onSuccess={onSuccess} />
      </QueryClientProvider>
    );

  it('should_closeAndReportSuccess_when_unsubscribeCompletes', async () => {
    renderDialog();
    await userEvent.click(screen.getByRole('button', { name: /unsubscribe|abmelden/i }));

    await waitFor(() => {
      expect(onSuccess).toHaveBeenCalledTimes(1);
    });
    // The defect: the dialog was reported as staying open, requiring a manual Close.
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
