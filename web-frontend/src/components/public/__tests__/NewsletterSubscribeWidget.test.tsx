/**
 * NewsletterSubscribeWidget Tests (Story 10.7 — AC4, AC12; Story 10.31 — AC8, AC10, AC12)
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { NewsletterSubscribeWidget } from '../NewsletterSubscribeWidget';

// Mock the newsletter hook
vi.mock('@/hooks/useNewsletter/useNewsletter', () => ({
  useNewsletterSubscribe: vi.fn(),
}));

// Mock useTurnstile
vi.mock('@/hooks/useTurnstile', () => ({
  useTurnstile: vi.fn(),
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
import { useTurnstile } from '@/hooks/useTurnstile';

const mockUseTurnstile = vi.mocked(useTurnstile);

function defaultTurnstileMock() {
  const mockGetToken = vi.fn().mockResolvedValue(null);
  const mockResetWidget = vi.fn();
  mockUseTurnstile.mockReturnValue({
    getToken: mockGetToken,
    resetWidget: mockResetWidget,
    widgetRef: { current: null },
  });
  return { mockGetToken, mockResetWidget };
}

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
    defaultTurnstileMock();

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
    defaultTurnstileMock();

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

  it('sends X-Turnstile-Token header when token returned (AC8)', async () => {
    const mockGetToken = vi.fn().mockResolvedValue('test-turnstile-token');
    mockUseTurnstile.mockReturnValue({
      getToken: mockGetToken,
      resetWidget: vi.fn(),
      widgetRef: { current: null },
    });

    let capturedVars: unknown;
    const mockMutate = vi.fn((vars) => {
      capturedVars = vars;
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

    await waitFor(() => {
      expect(mockMutate).toHaveBeenCalled();
    });

    expect((capturedVars as { turnstileToken: string }).turnstileToken).toBe(
      'test-turnstile-token'
    );
    expect((capturedVars as { request: { email: string } }).request.email).toBe('test@example.com');
  });

  it('sends null turnstileToken when disabled (AC5)', async () => {
    const { mockGetToken } = defaultTurnstileMock(); // returns null

    let capturedVars: unknown;
    const mockMutate = vi.fn((vars) => {
      capturedVars = vars;
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

    await waitFor(() => {
      expect(mockMutate).toHaveBeenCalled();
    });

    expect(mockGetToken).toHaveBeenCalled();
    expect((capturedVars as { turnstileToken: null }).turnstileToken).toBeNull();
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
    defaultTurnstileMock();

    renderWidget();

    fireEvent.change(screen.getByPlaceholderText('your@email.com'), {
      target: { value: 'test@example.com' },
    });
    fireEvent.click(screen.getByText('Subscribe'));

    await waitFor(() => expect(mockMutate).toHaveBeenCalled());
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
    defaultTurnstileMock();

    renderWidget();

    fireEvent.change(screen.getByPlaceholderText('your@email.com'), {
      target: { value: 'existing@example.com' },
    });
    fireEvent.click(screen.getByText('Subscribe'));
    await waitFor(() => expect(mockMutate).toHaveBeenCalled());
    onErrorCallback?.(axiosError);

    await waitFor(() => {
      expect(screen.getByText('You are already subscribed.')).toBeInTheDocument();
    });
  });

  it('calls resetWidget and shows error on 403 turnstile_failed (AC10)', async () => {
    const turnstileError = {
      isAxiosError: true,
      response: { status: 403, data: { error: 'turnstile_failed' } },
    };
    let onErrorCallback: ((e: unknown) => void) | undefined;
    const mockMutate = vi.fn((_data, options?: { onError?: (e: unknown) => void }) => {
      onErrorCallback = options?.onError;
    });
    vi.mocked(useNewsletterSubscribe).mockReturnValue({
      mutate: mockMutate,
      isPending: false,
    } as ReturnType<typeof useNewsletterSubscribe>);
    const { mockResetWidget } = defaultTurnstileMock();

    renderWidget();

    fireEvent.change(screen.getByPlaceholderText('your@email.com'), {
      target: { value: 'test@example.com' },
    });
    fireEvent.click(screen.getByText('Subscribe'));
    await waitFor(() => expect(mockMutate).toHaveBeenCalled());
    onErrorCallback?.(turnstileError);

    await waitFor(() => {
      expect(screen.getByText('Something went wrong. Please try again.')).toBeInTheDocument();
    });
    expect(mockResetWidget).toHaveBeenCalled();
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
    defaultTurnstileMock();

    renderWidget();

    fireEvent.change(screen.getByPlaceholderText('your@email.com'), {
      target: { value: 'test@example.com' },
    });
    fireEvent.click(screen.getByText('Subscribe'));
    await waitFor(() => expect(mockMutate).toHaveBeenCalled());
    onErrorCallback?.(genericError);

    await waitFor(() => {
      expect(screen.getByText('Something went wrong. Please try again.')).toBeInTheDocument();
    });
  });
});
