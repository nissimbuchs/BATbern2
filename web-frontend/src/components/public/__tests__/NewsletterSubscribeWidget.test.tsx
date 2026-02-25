/**
 * NewsletterSubscribeWidget Tests (Story 10.7 — AC4, AC12)
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { NewsletterSubscribeWidget } from '../NewsletterSubscribeWidget';

// Mock the hook
vi.mock('@/hooks/useNewsletter/useNewsletter', () => ({
  useNewsletterSubscribe: vi.fn(),
}));

// Mock axios so axios.isAxiosError works in tests
vi.mock('axios', () => ({
  default: {
    isAxiosError: (e: unknown): boolean =>
      typeof e === 'object' &&
      e !== null &&
      (e as { isAxiosError?: boolean }).isAxiosError === true,
  },
  isAxiosError: (e: unknown): boolean =>
    typeof e === 'object' && e !== null && (e as { isAxiosError?: boolean }).isAxiosError === true,
}));

// Mock react-i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => {
      const map: Record<string, string> = {
        'newsletter.widget.title': 'Stay updated with BATbern',
        'newsletter.widget.placeholder': 'your@email.com',
        'newsletter.widget.button': 'Subscribe',
        'newsletter.widget.success': 'Thank you! You are now subscribed.',
        'newsletter.widget.alreadySubscribed': 'You are already subscribed.',
        'newsletter.widget.error': 'Something went wrong. Please try again.',
        'newsletter.widget.emailInvalid': 'Please enter a valid email address.',
      };
      if (opts) {
        return map[key]?.replace(/\{\{(\w+)\}\}/g, (_, k: string) => String(opts[k] ?? '')) ?? key;
      }
      return map[key] ?? key;
    },
  }),
}));

import { useNewsletterSubscribe } from '@/hooks/useNewsletter/useNewsletter';

function renderWidget() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <NewsletterSubscribeWidget />
    </QueryClientProvider>
  );
}

describe('NewsletterSubscribeWidget', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders title, email input, and subscribe button', () => {
    const mockMutate = vi.fn();
    vi.mocked(useNewsletterSubscribe).mockReturnValue({
      mutate: mockMutate,
      isPending: false,
    } as ReturnType<typeof useNewsletterSubscribe>);

    renderWidget();

    expect(screen.getByText('Stay updated with BATbern')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('your@email.com')).toBeInTheDocument();
    expect(screen.getByText('Subscribe')).toBeInTheDocument();
  });

  it('shows email validation error for invalid email', async () => {
    const mockMutate = vi.fn();
    vi.mocked(useNewsletterSubscribe).mockReturnValue({
      mutate: mockMutate,
      isPending: false,
    } as ReturnType<typeof useNewsletterSubscribe>);

    renderWidget();

    fireEvent.change(screen.getByPlaceholderText('your@email.com'), {
      target: { value: 'invalid-email' },
    });
    fireEvent.click(screen.getByText('Subscribe'));

    await waitFor(() => {
      expect(screen.getByText('Please enter a valid email address.')).toBeInTheDocument();
    });
    expect(mockMutate).not.toHaveBeenCalled();
  });

  it('shows success state after successful subscription', async () => {
    let onSuccessCallback: (() => void) | undefined;
    const mockMutate = vi.fn((_data, options?: { onSuccess?: () => void }) => {
      onSuccessCallback = options?.onSuccess;
    });
    vi.mocked(useNewsletterSubscribe).mockReturnValue({
      mutate: mockMutate,
      isPending: false,
    } as ReturnType<typeof useNewsletterSubscribe>);

    renderWidget();

    fireEvent.change(screen.getByPlaceholderText('your@email.com'), {
      target: { value: 'test@example.com' },
    });
    fireEvent.click(screen.getByText('Subscribe'));
    onSuccessCallback?.();

    await waitFor(() => {
      expect(screen.getByText('Thank you! You are now subscribed.')).toBeInTheDocument();
    });
  });

  it('shows already-subscribed message on 409 response', async () => {
    const axiosError = { isAxiosError: true, response: { status: 409 } };
    let onErrorCallback: ((e: unknown) => void) | undefined;
    const mockMutate = vi.fn((_data, options?: { onError?: (e: unknown) => void }) => {
      onErrorCallback = options?.onError;
    });
    vi.mocked(useNewsletterSubscribe).mockReturnValue({
      mutate: mockMutate,
      isPending: false,
    } as ReturnType<typeof useNewsletterSubscribe>);

    renderWidget();

    fireEvent.change(screen.getByPlaceholderText('your@email.com'), {
      target: { value: 'existing@example.com' },
    });
    fireEvent.click(screen.getByText('Subscribe'));
    onErrorCallback?.(axiosError);

    await waitFor(() => {
      expect(screen.getByText('You are already subscribed.')).toBeInTheDocument();
    });
  });

  it('shows generic error message on non-409 error', async () => {
    const genericError = new Error('Network error');
    let onErrorCallback: ((e: unknown) => void) | undefined;
    const mockMutate = vi.fn((_data, options?: { onError?: (e: unknown) => void }) => {
      onErrorCallback = options?.onError;
    });
    vi.mocked(useNewsletterSubscribe).mockReturnValue({
      mutate: mockMutate,
      isPending: false,
    } as ReturnType<typeof useNewsletterSubscribe>);

    renderWidget();

    fireEvent.change(screen.getByPlaceholderText('your@email.com'), {
      target: { value: 'test@example.com' },
    });
    fireEvent.click(screen.getByText('Subscribe'));
    onErrorCallback?.(genericError);

    await waitFor(() => {
      expect(screen.getByText('Something went wrong. Please try again.')).toBeInTheDocument();
    });
  });
});
