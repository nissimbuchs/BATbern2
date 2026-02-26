/**
 * EventNewsletterTab Tests (Story 10.7 — AC9, AC12)
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { EventNewsletterTab } from '../EventNewsletterTab';

// Mock hooks
vi.mock('@/hooks/useNewsletter/useNewsletter', () => ({
  useSubscriberCount: vi.fn(),
  useNewsletterHistory: vi.fn(),
  useNewsletterPreview: vi.fn(),
  useSendNewsletter: vi.fn(),
}));

// Mock react-i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => {
      const map: Record<string, string> = {
        'eventPage.newsletter.title': 'Newsletter',
        'eventPage.newsletter.subscriberCount': `${opts?.count ?? 0} active subscribers`,
        'eventPage.newsletter.sendHistory': 'Send History',
        'eventPage.newsletter.noHistory': 'No newsletters sent for this event yet.',
        'eventPage.newsletter.composeTitle': 'Compose & Send',
        'eventPage.newsletter.locale': 'Language',
        'eventPage.newsletter.preview': 'Preview',
        'eventPage.newsletter.sendNewsletter': 'Send Newsletter',
        'eventPage.newsletter.sendReminder': 'Send Reminder',
        'eventPage.newsletter.confirmSendTitle': 'Confirm Send',
        'eventPage.newsletter.confirmSendBody': `Send ${opts?.type ?? ''} to ${opts?.count ?? 0} subscribers for event ${opts?.eventTitle ?? ''}?`,
        'eventPage.newsletter.sendSuccess': `Newsletter sent to ${opts?.count ?? 0} recipients.`,
        'common.cancel': 'Cancel',
        'common.confirm': 'Confirm',
      };
      if (opts) {
        return map[key]?.replace(/\{\{(\w+)\}\}/g, (_, k: string) => String(opts[k] ?? '')) ?? key;
      }
      return map[key] ?? key;
    },
  }),
}));

import {
  useSubscriberCount,
  useNewsletterHistory,
  useNewsletterPreview,
  useSendNewsletter,
} from '@/hooks/useNewsletter/useNewsletter';

function renderTab(eventCode = 'BATbern58', eventTitle = 'AI in der Software Entwicklung') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <EventNewsletterTab eventCode={eventCode} eventTitle={eventTitle} />
    </QueryClientProvider>
  );
}

describe('EventNewsletterTab', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    vi.mocked(useNewsletterHistory).mockReturnValue({
      isLoading: false,
      data: [],
    } as ReturnType<typeof useNewsletterHistory>);

    vi.mocked(useNewsletterPreview).mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as ReturnType<typeof useNewsletterPreview>);

    vi.mocked(useSendNewsletter).mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
      isSuccess: false,
      isError: false,
    } as ReturnType<typeof useSendNewsletter>);
  });

  it('shows subscriber count when loaded', () => {
    vi.mocked(useSubscriberCount).mockReturnValue({
      isLoading: false,
      data: { totalCount: 234 },
    } as ReturnType<typeof useSubscriberCount>);

    renderTab();

    expect(screen.getByText('234 active subscribers')).toBeInTheDocument();
  });

  it('shows empty history message when no newsletters sent', () => {
    vi.mocked(useSubscriberCount).mockReturnValue({
      isLoading: false,
      data: { totalCount: 0 },
    } as ReturnType<typeof useSubscriberCount>);

    vi.mocked(useNewsletterHistory).mockReturnValue({
      isLoading: false,
      data: [],
    } as ReturnType<typeof useNewsletterHistory>);

    renderTab();

    expect(screen.getByText('No newsletters sent for this event yet.')).toBeInTheDocument();
  });

  it('shows send history table when history exists', () => {
    vi.mocked(useSubscriberCount).mockReturnValue({
      isLoading: false,
      data: { totalCount: 10 },
    } as ReturnType<typeof useSubscriberCount>);

    vi.mocked(useNewsletterHistory).mockReturnValue({
      isLoading: false,
      data: [
        {
          id: 'send-1',
          isReminder: false,
          sentAt: '2026-02-25T10:00:00Z',
          sentByUsername: 'organizer',
          recipientCount: 150,
          templateKey: 'newsletter-event',
        },
      ],
    } as ReturnType<typeof useNewsletterHistory>);

    renderTab();

    expect(screen.getByText('150')).toBeInTheDocument();
    expect(screen.getByText('Newsletter')).toBeInTheDocument();
  });

  it('opens confirmation dialog when Send Newsletter is clicked', async () => {
    vi.mocked(useSubscriberCount).mockReturnValue({
      isLoading: false,
      data: { totalCount: 100 },
    } as ReturnType<typeof useSubscriberCount>);

    renderTab();

    fireEvent.click(screen.getByText('Send Newsletter'));

    await waitFor(() => {
      expect(screen.getByText('Confirm Send')).toBeInTheDocument();
    });
  });

  it('calls sendMutation on confirm in dialog', async () => {
    const mockMutate = vi.fn();
    vi.mocked(useSendNewsletter).mockReturnValue({
      mutate: mockMutate,
      isPending: false,
      isSuccess: false,
      isError: false,
    } as ReturnType<typeof useSendNewsletter>);

    vi.mocked(useSubscriberCount).mockReturnValue({
      isLoading: false,
      data: { totalCount: 50 },
    } as ReturnType<typeof useSubscriberCount>);

    renderTab();

    fireEvent.click(screen.getByText('Send Newsletter'));

    await waitFor(() => {
      expect(screen.getByText('Confirm Send')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('Confirm'));

    expect(mockMutate).toHaveBeenCalledWith(
      expect.objectContaining({ isReminder: false }),
      expect.anything()
    );
  });
});
