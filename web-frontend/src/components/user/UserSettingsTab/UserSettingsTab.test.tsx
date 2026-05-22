/**
 * Story 10.32 — UserSettingsTab additional-emails section tests.
 *
 * Focus: the new AdditionalEmailsSection embedded under the Account sub-tab.
 * Mocks `useAddAdditionalEmail` / `useDeleteAdditionalEmail` via `vi.mock`.
 * Assertions use namespace-stripped i18n keys (per CLAUDE.md testing rules).
 */
import { describe, expect, test, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import UserSettingsTab from './UserSettingsTab';
import type { AdditionalEmail } from '@/types/userAccount.types';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => {
      // Namespace-stripped, parametrised — matches CLAUDE.md test pattern.
      if (opts && 'email' in opts) {
        return `${key}::${opts.email as string}`;
      }
      return key;
    },
    i18n: { language: 'en' },
  }),
}));

const mockAddMutate = vi.fn();
const mockDeleteMutate = vi.fn();

vi.mock('@/hooks/useUserAccount/useUserAccount', () => ({
  useUpdateUserPreferences: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useUpdateUserSettings: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useAddAdditionalEmail: () => ({
    mutateAsync: mockAddMutate,
    isPending: false,
  }),
  useDeleteAdditionalEmail: () => ({
    mutateAsync: mockDeleteMutate,
    isPending: false,
  }),
}));

vi.mock('@/hooks/useNewsletter/useNewsletter', () => ({
  useMySubscription: () => ({ data: { subscribed: false }, isLoading: false }),
  usePatchMySubscription: () => ({ mutate: vi.fn(), isPending: false }),
}));

function renderWithProviders(additionalEmails: AdditionalEmail[] = []) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <UserSettingsTab email="nissim.buchs@elca.ch" additionalEmails={additionalEmails} />
    </QueryClientProvider>
  );
}

describe('UserSettingsTab — Story 10.32 additional emails section', () => {
  beforeEach(() => {
    mockAddMutate.mockReset();
    mockDeleteMutate.mockReset();
  });

  test('should render the additional-emails section heading', () => {
    renderWithProviders([]);
    expect(screen.getByTestId('additional-emails-section')).toBeInTheDocument();
    expect(screen.getByText('settings.account.additionalEmailsTitle')).toBeInTheDocument();
  });

  test('should render existing additional emails with delete buttons and unverified pill', () => {
    renderWithProviders([
      {
        email: 'info@berner-architekten-treffen.ch',
        label: 'Hostpoint shared',
        createdAt: '2026-05-22T10:00:00Z',
        verifiedAt: null,
      },
    ]);
    expect(
      screen.getByTestId('additional-email-row-info@berner-architekten-treffen.ch')
    ).toBeInTheDocument();
    expect(
      screen.getByTestId('additional-email-delete-info@berner-architekten-treffen.ch')
    ).toBeInTheDocument();
    expect(
      screen.getByTestId('additional-email-unverified-info@berner-architekten-treffen.ch')
    ).toBeInTheDocument();
  });

  test('should call addAdditionalEmail on form submit with valid email', async () => {
    mockAddMutate.mockResolvedValueOnce({
      email: 'work@example.com',
      label: null,
      createdAt: '2026-05-22T10:00:00Z',
      verifiedAt: null,
    });

    renderWithProviders([]);

    fireEvent.change(screen.getByTestId('additional-email-input').querySelector('input')!, {
      target: { value: 'work@example.com' },
    });
    fireEvent.click(screen.getByTestId('additional-email-add-button'));

    await waitFor(() => {
      expect(mockAddMutate).toHaveBeenCalledWith({
        email: 'work@example.com',
        label: undefined,
      });
    });
  });

  test('should short-circuit on empty input without calling the mutation', async () => {
    // Post-review (2026-05-22, P3-3): client-side EMAIL_REGEX was dropped in
    // favour of HTML5 type="email" + authoritative backend validation. The
    // remaining client-side gate is just the empty-string check.
    renderWithProviders([]);

    // Empty input — Add button is disabled in this state, but assert the
    // gate even if the button is clicked programmatically (defence-in-depth).
    fireEvent.change(screen.getByTestId('additional-email-input').querySelector('input')!, {
      target: { value: '   ' },
    });
    fireEvent.click(screen.getByTestId('additional-email-add-button'));

    expect(mockAddMutate).not.toHaveBeenCalled();
  });

  test('should surface ADDITIONAL_EMAIL_DUPLICATE as inline error', async () => {
    const axiosErr = Object.assign(new Error('409'), {
      isAxiosError: true,
      response: { status: 409, data: { errorCode: 'ADDITIONAL_EMAIL_DUPLICATE' } },
    });
    mockAddMutate.mockRejectedValueOnce(axiosErr);

    renderWithProviders([]);

    fireEvent.change(screen.getByTestId('additional-email-input').querySelector('input')!, {
      target: { value: 'work@example.com' },
    });
    fireEvent.click(screen.getByTestId('additional-email-add-button'));

    await waitFor(() => {
      expect(
        screen.getByText('settings.account.additionalEmailErrorDuplicate')
      ).toBeInTheDocument();
    });
  });

  test('should call deleteAdditionalEmail when delete icon clicked and Remove confirmed in dialog', async () => {
    mockDeleteMutate.mockResolvedValueOnce(undefined);
    renderWithProviders([
      {
        email: 'box@example.com',
        label: null,
        createdAt: '2026-05-22T10:00:00Z',
        verifiedAt: null,
      },
    ]);

    fireEvent.click(screen.getByTestId('additional-email-delete-box@example.com'));
    // P3-1 (post-2026-05-22 review): confirmation is now a MUI Dialog rather
    // than window.confirm. Click the dialog's primary action to proceed.
    fireEvent.click(screen.getByTestId('additional-email-delete-confirm'));

    await waitFor(() => {
      expect(mockDeleteMutate).toHaveBeenCalledWith('box@example.com');
    });
  });

  test('should NOT delete when Cancel is clicked in the confirmation dialog', () => {
    renderWithProviders([
      {
        email: 'box@example.com',
        label: null,
        createdAt: '2026-05-22T10:00:00Z',
        verifiedAt: null,
      },
    ]);

    fireEvent.click(screen.getByTestId('additional-email-delete-box@example.com'));
    fireEvent.click(screen.getByTestId('additional-email-delete-cancel'));
    expect(mockDeleteMutate).not.toHaveBeenCalled();
  });

  test('should hide the add form and show limit alert when at cap', () => {
    const five: AdditionalEmail[] = Array.from({ length: 5 }, (_, i) => ({
      email: `box${i}@example.com`,
      label: null,
      createdAt: '2026-05-22T10:00:00Z',
      verifiedAt: null,
    }));
    renderWithProviders(five);

    expect(screen.getByTestId('additional-emails-at-limit')).toBeInTheDocument();
    expect(screen.queryByTestId('additional-email-add-button')).not.toBeInTheDocument();
  });
});
