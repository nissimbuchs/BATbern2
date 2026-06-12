/**
 * ThankOrganizersWidget Tests (Story 7.4 — "Thank the Organizers")
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThankOrganizersWidget } from '../ThankOrganizersWidget';

vi.mock('@/hooks/useThanks/useThanks', () => ({
  useThanksCount: vi.fn(),
  useSubmitThanks: vi.fn(),
}));

vi.mock('@/hooks/useTurnstile', () => ({
  useTurnstile: vi.fn(),
}));

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

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => {
      const map: Record<string, string> = {
        'thanks.widget.title': 'Thank the organizers',
        'thanks.widget.subtitle': 'BATbern is run by volunteers.',
        'thanks.widget.notePlaceholder': 'Add a note for the organizers (optional)',
        'thanks.widget.button': 'Thank the organizers',
        'thanks.widget.count_one': '{{count}} thank-you so far',
        'thanks.widget.count_other': '{{count}} thank-yous so far',
        'thanks.widget.success': 'Thank you for saying thanks!',
        'thanks.widget.error': 'Something went wrong. Please try again.',
      };
      // emulate i18next plural resolution for the count key
      const resolvedKey =
        key === 'thanks.widget.count' && opts
          ? (opts.count as number) === 1
            ? 'thanks.widget.count_one'
            : 'thanks.widget.count_other'
          : key;
      const val = map[resolvedKey] ?? key;
      return opts ? val.replace(/\{\{(\w+)\}\}/g, (_, k: string) => String(opts[k] ?? '')) : val;
    },
  }),
}));

import { useThanksCount, useSubmitThanks } from '@/hooks/useThanks/useThanks';
import { useTurnstile } from '@/hooks/useTurnstile';

const mockUseTurnstile = vi.mocked(useTurnstile);

function defaultTurnstileMock(token: string | null = null) {
  const mockGetToken = vi.fn().mockResolvedValue(token);
  const mockResetWidget = vi.fn();
  mockUseTurnstile.mockReturnValue({
    getToken: mockGetToken,
    resetWidget: mockResetWidget,
    widgetRef: { current: null },
  });
  return { mockGetToken, mockResetWidget };
}

function setCount(count: number) {
  vi.mocked(useThanksCount).mockReturnValue({
    data: { count },
  } as ReturnType<typeof useThanksCount>);
}

function renderWidget() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ThankOrganizersWidget eventCode="BATbern57" />
    </QueryClientProvider>
  );
}

describe('ThankOrganizersWidget', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders title, note input, button and the live count', () => {
    setCount(42);
    vi.mocked(useSubmitThanks).mockReturnValue({
      mutate: vi.fn(),
    } as unknown as ReturnType<typeof useSubmitThanks>);
    defaultTurnstileMock();

    renderWidget();

    expect(screen.getByTestId('thank-organizers-widget')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Thank the organizers' })).toBeInTheDocument();
    expect(screen.getByTestId('thanks-note-input')).toBeInTheDocument();
    expect(screen.getByTestId('thanks-count')).toHaveTextContent('42 thank-yous so far');
  });

  it('submits with the typed note and Turnstile token', async () => {
    setCount(0);
    defaultTurnstileMock('test-token');
    let capturedVars: unknown;
    const mockMutate = vi.fn((vars) => {
      capturedVars = vars;
    });
    vi.mocked(useSubmitThanks).mockReturnValue({
      mutate: mockMutate,
    } as unknown as ReturnType<typeof useSubmitThanks>);

    renderWidget();

    fireEvent.change(screen.getByTestId('thanks-note-input'), {
      target: { value: 'Merci!' },
    });
    fireEvent.click(screen.getByTestId('thanks-submit'));

    await waitFor(() => expect(mockMutate).toHaveBeenCalled());
    expect((capturedVars as { note: string }).note).toBe('Merci!');
    expect((capturedVars as { turnstileToken: string }).turnstileToken).toBe('test-token');
  });

  it('sends null note for a one-click thanks (empty input)', async () => {
    setCount(0);
    defaultTurnstileMock(null);
    let capturedVars: unknown;
    const mockMutate = vi.fn((vars) => {
      capturedVars = vars;
    });
    vi.mocked(useSubmitThanks).mockReturnValue({
      mutate: mockMutate,
    } as unknown as ReturnType<typeof useSubmitThanks>);

    renderWidget();

    fireEvent.click(screen.getByTestId('thanks-submit'));

    await waitFor(() => expect(mockMutate).toHaveBeenCalled());
    expect((capturedVars as { note: null }).note).toBeNull();
    expect((capturedVars as { turnstileToken: null }).turnstileToken).toBeNull();
  });

  it('shows success state after a successful thank-you', async () => {
    setCount(0);
    defaultTurnstileMock();
    let onSuccess: (() => void) | undefined;
    const mockMutate = vi.fn((_vars, opts?: { onSuccess?: () => void }) => {
      onSuccess = opts?.onSuccess;
    });
    vi.mocked(useSubmitThanks).mockReturnValue({
      mutate: mockMutate,
    } as unknown as ReturnType<typeof useSubmitThanks>);

    renderWidget();
    fireEvent.click(screen.getByTestId('thanks-submit'));
    await waitFor(() => expect(mockMutate).toHaveBeenCalled());
    onSuccess?.();

    await waitFor(() => {
      expect(screen.getByTestId('thanks-success')).toBeInTheDocument();
    });
  });

  it('resets the widget and shows error on 403 turnstile_failed', async () => {
    setCount(0);
    const { mockResetWidget } = defaultTurnstileMock();
    const turnstileError = {
      isAxiosError: true,
      response: { status: 403, data: { error: 'turnstile_failed' } },
    };
    let onError: ((e: unknown) => void) | undefined;
    const mockMutate = vi.fn((_vars, opts?: { onError?: (e: unknown) => void }) => {
      onError = opts?.onError;
    });
    vi.mocked(useSubmitThanks).mockReturnValue({
      mutate: mockMutate,
    } as unknown as ReturnType<typeof useSubmitThanks>);

    renderWidget();
    fireEvent.click(screen.getByTestId('thanks-submit'));
    await waitFor(() => expect(mockMutate).toHaveBeenCalled());
    onError?.(turnstileError);

    await waitFor(() => {
      expect(screen.getByTestId('thanks-error')).toBeInTheDocument();
    });
    expect(mockResetWidget).toHaveBeenCalled();
  });
});
