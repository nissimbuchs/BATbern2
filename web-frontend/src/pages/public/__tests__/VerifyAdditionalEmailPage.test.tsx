/**
 * VerifyAdditionalEmailPage Tests (Additional-email verification v2)
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import VerifyAdditionalEmailPage from '../VerifyAdditionalEmailPage';

vi.mock('@/hooks/useAdditionalEmailVerification/useAdditionalEmailVerification', () => ({
  useCheckAdditionalEmailVerification: vi.fn(),
  useConfirmAdditionalEmailVerification: vi.fn(),
}));

vi.mock('@/components/public/PublicLayout', () => ({
  PublicLayout: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => {
      const map: Record<string, string> = {
        'verifyEmail.title': 'Confirm your email address',
        'verifyEmail.loading': 'Checking your verification link…',
        'verifyEmail.confirmMessage': 'Confirm that {{email}} is your email address.',
        'verifyEmail.confirmButton': 'Confirm email address',
        'verifyEmail.success': 'Your email address has been confirmed. You can close this page.',
        'verifyEmail.alreadyVerified': 'This email address is already confirmed.',
        'verifyEmail.homeLink': 'Go to batbern.ch',
        'verifyEmail.invalidToken':
          'This verification link is invalid or has expired. Please request a new one from your account settings.',
        'verifyEmail.notFound':
          'This email address is no longer on the profile it was added to. If you re-added it, use the link from the newest verification email.',
      };
      if (opts) {
        return map[key]?.replace(/\{\{(\w+)\}\}/g, (_, k: string) => String(opts[k] ?? '')) ?? key;
      }
      return map[key] ?? key;
    },
  }),
}));

import {
  useCheckAdditionalEmailVerification,
  useConfirmAdditionalEmailVerification,
} from '@/hooks/useAdditionalEmailVerification/useAdditionalEmailVerification';

function renderPage(token?: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const url = token ? `/verify-email?token=${token}` : '/verify-email';
  return render(
    <MemoryRouter initialEntries={[url]}>
      <QueryClientProvider client={queryClient}>
        <VerifyAdditionalEmailPage />
      </QueryClientProvider>
    </MemoryRouter>
  );
}

describe('VerifyAdditionalEmailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows the confirm button when the token check succeeds (unverified)', async () => {
    vi.mocked(useCheckAdditionalEmailVerification).mockReturnValue({
      isSuccess: true,
      isError: false,
      data: { email: 'in***@example.com', verified: false },
    } as ReturnType<typeof useCheckAdditionalEmailVerification>);
    vi.mocked(useConfirmAdditionalEmailVerification).mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as ReturnType<typeof useConfirmAdditionalEmailVerification>);

    renderPage('valid-token');

    await waitFor(() => {
      expect(screen.getByText('Confirm email address')).toBeInTheDocument();
    });
    expect(screen.getByText(/in\*\*\*@example.com/)).toBeInTheDocument();
  });

  it('shows success after confirming', async () => {
    let onSuccess: ((d: { alreadyVerified: boolean }) => void) | undefined;
    const mockMutate = vi.fn(
      (_token: string, opts?: { onSuccess?: (d: { alreadyVerified: boolean }) => void }) => {
        onSuccess = opts?.onSuccess;
      }
    );
    vi.mocked(useCheckAdditionalEmailVerification).mockReturnValue({
      isSuccess: true,
      isError: false,
      data: { email: 'in***@example.com', verified: false },
    } as ReturnType<typeof useCheckAdditionalEmailVerification>);
    vi.mocked(useConfirmAdditionalEmailVerification).mockReturnValue({
      mutate: mockMutate,
      isPending: false,
    } as ReturnType<typeof useConfirmAdditionalEmailVerification>);

    renderPage('valid-token');
    await waitFor(() => {
      expect(screen.getByText('Confirm email address')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('Confirm email address'));
    onSuccess?.({ alreadyVerified: false });

    await waitFor(() => {
      expect(
        screen.getByText('Your email address has been confirmed. You can close this page.')
      ).toBeInTheDocument();
    });
  });

  it('shows the not-found message when confirm returns 404 (row deleted)', async () => {
    let onError: ((e: unknown) => void) | undefined;
    const mockMutate = vi.fn((_token: string, opts?: { onError?: (e: unknown) => void }) => {
      onError = opts?.onError;
    });
    vi.mocked(useCheckAdditionalEmailVerification).mockReturnValue({
      isSuccess: true,
      isError: false,
      data: { email: 'in***@example.com', verified: false },
    } as ReturnType<typeof useCheckAdditionalEmailVerification>);
    vi.mocked(useConfirmAdditionalEmailVerification).mockReturnValue({
      mutate: mockMutate,
      isPending: false,
    } as ReturnType<typeof useConfirmAdditionalEmailVerification>);

    renderPage('valid-token');
    await waitFor(() => {
      expect(screen.getByText('Confirm email address')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('Confirm email address'));
    // Simulate the axios 404 the confirm endpoint returns once the row is gone.
    onError?.({ isAxiosError: true, response: { status: 404 } });

    await waitFor(() => {
      expect(screen.getByTestId('verify-email-not-found')).toBeInTheDocument();
    });
    expect(screen.queryByTestId('verify-email-invalid')).not.toBeInTheDocument();
  });

  it('shows the generic invalid message when confirm fails with a non-404 error', async () => {
    let onError: ((e: unknown) => void) | undefined;
    const mockMutate = vi.fn((_token: string, opts?: { onError?: (e: unknown) => void }) => {
      onError = opts?.onError;
    });
    vi.mocked(useCheckAdditionalEmailVerification).mockReturnValue({
      isSuccess: true,
      isError: false,
      data: { email: 'in***@example.com', verified: false },
    } as ReturnType<typeof useCheckAdditionalEmailVerification>);
    vi.mocked(useConfirmAdditionalEmailVerification).mockReturnValue({
      mutate: mockMutate,
      isPending: false,
    } as ReturnType<typeof useConfirmAdditionalEmailVerification>);

    renderPage('valid-token');
    await waitFor(() => {
      expect(screen.getByText('Confirm email address')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('Confirm email address'));
    onError?.({ isAxiosError: true, response: { status: 400 } });

    await waitFor(() => {
      expect(screen.getByTestId('verify-email-invalid')).toBeInTheDocument();
    });
  });

  it('shows already-verified state when the check reports verified', async () => {
    vi.mocked(useCheckAdditionalEmailVerification).mockReturnValue({
      isSuccess: true,
      isError: false,
      data: { email: 'in***@example.com', verified: true },
    } as ReturnType<typeof useCheckAdditionalEmailVerification>);
    vi.mocked(useConfirmAdditionalEmailVerification).mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as ReturnType<typeof useConfirmAdditionalEmailVerification>);

    renderPage('valid-token');

    await waitFor(() => {
      expect(screen.getByText('This email address is already confirmed.')).toBeInTheDocument();
    });
  });

  it('shows invalid state when the token check errors', async () => {
    vi.mocked(useCheckAdditionalEmailVerification).mockReturnValue({
      isSuccess: false,
      isError: true,
      data: undefined,
    } as ReturnType<typeof useCheckAdditionalEmailVerification>);
    vi.mocked(useConfirmAdditionalEmailVerification).mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as ReturnType<typeof useConfirmAdditionalEmailVerification>);

    renderPage('bad-token');

    await waitFor(() => {
      expect(
        screen.getByText(
          'This verification link is invalid or has expired. Please request a new one from your account settings.'
        )
      ).toBeInTheDocument();
    });
  });

  it('shows invalid state when no token is provided', async () => {
    vi.mocked(useCheckAdditionalEmailVerification).mockReturnValue({
      isSuccess: false,
      isError: false,
      data: undefined,
    } as ReturnType<typeof useCheckAdditionalEmailVerification>);
    vi.mocked(useConfirmAdditionalEmailVerification).mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as ReturnType<typeof useConfirmAdditionalEmailVerification>);

    renderPage();

    await waitFor(() => {
      expect(
        screen.getByText(
          'This verification link is invalid or has expired. Please request a new one from your account settings.'
        )
      ).toBeInTheDocument();
    });
  });
});
