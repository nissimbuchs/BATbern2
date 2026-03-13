/**
 * NewsletterSubscriberList Tests
 * Story 10.28: Newsletter Subscriber Management Page
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { I18nextProvider } from 'react-i18next';
import i18n from '../../../../i18n/config';

// Mock hooks
const mockRefetch = vi.fn();
vi.mock('@/hooks/useNewsletterSubscribers', () => ({
  useNewsletterSubscriberList: vi.fn(),
  useUnsubscribeSubscriber: () => ({ mutate: vi.fn(), isPending: false, isError: false }),
  useResubscribeSubscriber: () => ({ mutate: vi.fn(), isPending: false, isError: false }),
  useDeleteSubscriber: () => ({ mutate: vi.fn(), isPending: false, isError: false }),
}));

vi.mock('@/stores/newsletterSubscriberStore', () => ({
  useNewsletterSubscriberStore: () => ({
    filters: { searchQuery: '', status: 'all', sortBy: 'subscribedAt', sortDir: 'desc' },
    pagination: { page: 1, limit: 20 },
    setSort: vi.fn(),
    setPage: vi.fn(),
    setLimit: vi.fn(),
    setSearchQuery: vi.fn(),
    setFilters: vi.fn(),
    resetFilters: vi.fn(),
  }),
}));

import { useNewsletterSubscriberList } from '@/hooks/useNewsletterSubscribers';

const mockUseNewsletterSubscriberList = vi.mocked(useNewsletterSubscriberList);

import NewsletterSubscriberList from '../NewsletterSubscriberList';

const createWrapper = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <I18nextProvider i18n={i18n}>{children}</I18nextProvider>
    </QueryClientProvider>
  );
};

const mockSubscribers = [
  {
    id: 'sub-1',
    email: 'alice@example.com',
    firstName: 'Alice',
    language: 'en',
    source: 'website',
    subscribedAt: '2026-01-15T10:00:00Z',
    unsubscribedAt: null,
  },
];

describe('NewsletterSubscriberList', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should_showLoader_when_loading', () => {
    mockUseNewsletterSubscriberList.mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
      refetch: mockRefetch,
    } as any);

    render(<NewsletterSubscriberList />, { wrapper: createWrapper() });

    // BATbernLoader renders an image or animation — check for its container
    expect(screen.queryByTestId('subscriber-table')).not.toBeInTheDocument();
  });

  it('should_showErrorAlert_when_errorOccurred', () => {
    mockUseNewsletterSubscriberList.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      refetch: mockRefetch,
    } as any);

    render(<NewsletterSubscriberList />, { wrapper: createWrapper() });

    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText(/failed to load subscribers/i)).toBeInTheDocument();
  });

  it('should_showRetryButton_when_errorOccurred', async () => {
    const user = userEvent.setup();
    mockUseNewsletterSubscriberList.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      refetch: mockRefetch,
    } as any);

    render(<NewsletterSubscriberList />, { wrapper: createWrapper() });

    const retryButton = screen.getByRole('button', { name: /retry/i });
    await user.click(retryButton);

    expect(mockRefetch).toHaveBeenCalledTimes(1);
  });

  it('should_renderTable_when_dataLoaded', () => {
    mockUseNewsletterSubscriberList.mockReturnValue({
      data: {
        data: mockSubscribers,
        pagination: {
          page: 1,
          limit: 20,
          totalItems: 1,
          totalPages: 1,
          hasNext: false,
          hasPrev: false,
        },
      },
      isLoading: false,
      isError: false,
      refetch: mockRefetch,
    } as any);

    render(<NewsletterSubscriberList />, { wrapper: createWrapper() });

    expect(screen.getByText('alice@example.com')).toBeInTheDocument();
  });
});
