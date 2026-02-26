/**
 * UnsubscribePage Tests (Story 10.7 — AC5, AC12)
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import UnsubscribePage from '../UnsubscribePage';

// Mock hooks
vi.mock('@/hooks/useNewsletter/useNewsletter', () => ({
  useVerifyUnsubscribeToken: vi.fn(),
  useUnsubscribeByToken: vi.fn(),
}));

// Mock PublicLayout to render children directly
vi.mock('@/components/public/PublicLayout', () => ({
  PublicLayout: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

// Mock react-i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => {
      const map: Record<string, string> = {
        'newsletter.unsubscribe.title': 'Unsubscribe from BATbern Newsletter',
        'newsletter.unsubscribe.loading': 'Loading…',
        'newsletter.unsubscribe.confirmMessage': `You are about to unsubscribe ${opts?.email ?? ''} from BATbern newsletters.`,
        'newsletter.unsubscribe.confirmButton': 'Confirm Unsubscribe',
        'newsletter.unsubscribe.success': 'You have been successfully unsubscribed.',
        'newsletter.unsubscribe.resubscribeLink': 'Subscribe again',
        'newsletter.unsubscribe.invalidToken':
          'This unsubscribe link is invalid or has already been used.',
      };
      if (opts) {
        return map[key]?.replace(/\{\{(\w+)\}\}/g, (_, k: string) => String(opts[k] ?? '')) ?? key;
      }
      return map[key] ?? key;
    },
  }),
}));

import {
  useVerifyUnsubscribeToken,
  useUnsubscribeByToken,
} from '@/hooks/useNewsletter/useNewsletter';

function renderPage(token?: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const url = token ? `/unsubscribe?token=${token}` : '/unsubscribe';
  return render(
    <MemoryRouter initialEntries={[url]}>
      <QueryClientProvider client={queryClient}>
        <UnsubscribePage />
      </QueryClientProvider>
    </MemoryRouter>
  );
}

describe('UnsubscribePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows confirm button when token is valid', async () => {
    vi.mocked(useVerifyUnsubscribeToken).mockReturnValue({
      isSuccess: true,
      isError: false,
      data: { email: 'alice@example.com' },
    } as ReturnType<typeof useVerifyUnsubscribeToken>);

    vi.mocked(useUnsubscribeByToken).mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
      isSuccess: false,
      isError: false,
    } as ReturnType<typeof useUnsubscribeByToken>);

    renderPage('valid-token');

    await waitFor(() => {
      expect(screen.getByText('Confirm Unsubscribe')).toBeInTheDocument();
    });
    expect(screen.getByText(/alice@example.com/)).toBeInTheDocument();
  });

  it('shows success message after confirming unsubscribe', async () => {
    let onSuccessCallback: (() => void) | undefined;
    const mockMutate = vi.fn((_token: string, opts?: { onSuccess?: () => void }) => {
      onSuccessCallback = opts?.onSuccess;
    });

    vi.mocked(useVerifyUnsubscribeToken).mockReturnValue({
      isSuccess: true,
      isError: false,
      data: { email: 'bob@example.com' },
    } as ReturnType<typeof useVerifyUnsubscribeToken>);

    vi.mocked(useUnsubscribeByToken).mockReturnValue({
      mutate: mockMutate,
      isPending: false,
    } as ReturnType<typeof useUnsubscribeByToken>);

    renderPage('valid-token');

    await waitFor(() => {
      expect(screen.getByText('Confirm Unsubscribe')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('Confirm Unsubscribe'));
    onSuccessCallback?.();

    await waitFor(() => {
      expect(screen.getByText('You have been successfully unsubscribed.')).toBeInTheDocument();
    });
    expect(screen.getByText('Subscribe again')).toBeInTheDocument();
  });

  it('shows invalid token message when token verification fails', async () => {
    vi.mocked(useVerifyUnsubscribeToken).mockReturnValue({
      isSuccess: false,
      isError: true,
      data: undefined,
    } as ReturnType<typeof useVerifyUnsubscribeToken>);

    vi.mocked(useUnsubscribeByToken).mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as ReturnType<typeof useUnsubscribeByToken>);

    renderPage('invalid-token');

    await waitFor(() => {
      expect(
        screen.getByText('This unsubscribe link is invalid or has already been used.')
      ).toBeInTheDocument();
    });
  });

  it('shows invalid token message when no token is provided', async () => {
    vi.mocked(useVerifyUnsubscribeToken).mockReturnValue({
      isSuccess: false,
      isError: false,
      data: undefined,
    } as ReturnType<typeof useVerifyUnsubscribeToken>);

    vi.mocked(useUnsubscribeByToken).mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as ReturnType<typeof useUnsubscribeByToken>);

    renderPage(); // no token

    await waitFor(() => {
      expect(
        screen.getByText('This unsubscribe link is invalid or has already been used.')
      ).toBeInTheDocument();
    });
  });
});
