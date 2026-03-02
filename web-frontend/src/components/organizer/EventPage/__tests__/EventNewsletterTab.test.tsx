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

vi.mock('@/hooks/useEmailTemplates', () => ({
  useEmailTemplates: vi.fn(),
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
        'eventPage.newsletter.confirmSendBody': `Send ${opts?.type ?? ''} using '${opts?.templateKey ?? ''}' to ${opts?.count ?? 0} subscribers for event ${opts?.eventTitle ?? ''}?`,
        'eventPage.newsletter.sendSuccess': `Newsletter sent to ${opts?.count ?? 0} recipients.`,
        'common.cancel': 'Cancel',
        'common.confirm': 'Confirm',
        'organizer:newsletter.templateSelect.label': 'Template',
        'organizer:newsletter.templateSelect.createNew': 'Create new template',
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
import { useEmailTemplates } from '@/hooks/useEmailTemplates';

function renderTab(eventCode = 'BATbern58', eventTitle = 'AI in der Software Entwicklung') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <EventNewsletterTab eventCode={eventCode} eventTitle={eventTitle} />
    </QueryClientProvider>
  );
}

describe('EventNewsletterTab', () => {
  const deTemplate = {
    templateKey: 'newsletter-event',
    locale: 'de',
    category: 'NEWSLETTER',
    htmlBody: '<p>DE template</p>',
    isLayout: false,
    isSystemTemplate: false,
    updatedAt: '2026-01-01T00:00:00Z',
  };
  const enTemplate = {
    templateKey: 'newsletter-event',
    locale: 'en',
    category: 'NEWSLETTER',
    htmlBody: '<p>EN template</p>',
    isLayout: false,
    isSystemTemplate: false,
    updatedAt: '2026-01-01T00:00:00Z',
  };

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

    vi.mocked(useEmailTemplates).mockReturnValue({
      isLoading: false,
      data: [deTemplate, enTemplate],
    } as ReturnType<typeof useEmailTemplates>);
  });

  it('shows subscriber count when loaded', () => {
    vi.mocked(useSubscriberCount).mockReturnValue({
      isLoading: false,
      data: { totalActive: 234 },
    } as ReturnType<typeof useSubscriberCount>);

    renderTab();

    expect(screen.getByText('234 active subscribers')).toBeInTheDocument();
  });

  it('shows empty history message when no newsletters sent', () => {
    vi.mocked(useSubscriberCount).mockReturnValue({
      isLoading: false,
      data: { totalActive: 0 },
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
      data: { totalActive: 10 },
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
      data: { totalActive: 100 },
    } as ReturnType<typeof useSubscriberCount>);

    renderTab();

    fireEvent.click(screen.getByRole('button', { name: /Send Newsletter/i }));

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
      data: { totalActive: 50 },
    } as ReturnType<typeof useSubscriberCount>);

    renderTab();

    fireEvent.click(screen.getByRole('button', { name: /Send Newsletter/i }));

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /Confirm Send/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /Confirm/i }));

    expect(mockMutate).toHaveBeenCalledWith(
      expect.objectContaining({ isReminder: false, templateKey: 'newsletter-event' }),
      expect.anything()
    );
  });

  // ── AC3: Template dropdown renders ──────────────────────────────────────────

  it('AC3: renders template dropdown with data-testid', () => {
    vi.mocked(useSubscriberCount).mockReturnValue({
      isLoading: false,
      data: { totalActive: 0 },
    } as ReturnType<typeof useSubscriberCount>);

    renderTab();

    expect(screen.getByTestId('newsletter-template-select')).toBeInTheDocument();
  });

  it('AC3: template dropdown is disabled while templates are loading', () => {
    vi.mocked(useSubscriberCount).mockReturnValue({
      isLoading: false,
      data: { totalActive: 0 },
    } as ReturnType<typeof useSubscriberCount>);
    vi.mocked(useEmailTemplates).mockReturnValue({
      isLoading: true,
      data: undefined,
    } as ReturnType<typeof useEmailTemplates>);

    renderTab();

    // The combobox (SelectDisplay div) carries aria-disabled when Select is disabled
    const combobox = screen.getByTestId('newsletter-template-select');
    expect(combobox).toHaveAttribute('aria-disabled', 'true');
  });

  // ── AC5: Preview passes templateKey ─────────────────────────────────────────

  it('AC5: preview request includes selected templateKey', () => {
    const mockPreviewMutate = vi.fn();
    vi.mocked(useNewsletterPreview).mockReturnValue({
      mutate: mockPreviewMutate,
      isPending: false,
    } as ReturnType<typeof useNewsletterPreview>);

    vi.mocked(useSubscriberCount).mockReturnValue({
      isLoading: false,
      data: { totalActive: 10 },
    } as ReturnType<typeof useSubscriberCount>);

    renderTab();

    fireEvent.click(screen.getByRole('button', { name: /Preview/i }));

    expect(mockPreviewMutate).toHaveBeenCalledWith(
      expect.objectContaining({
        request: expect.objectContaining({ templateKey: 'newsletter-event' }),
      }),
      expect.anything()
    );
  });

  // ── AC6: Confirm dialog shows templateKey ────────────────────────────────────

  it('AC6: confirmation dialog body includes selected templateKey', async () => {
    vi.mocked(useSubscriberCount).mockReturnValue({
      isLoading: false,
      data: { totalActive: 100 },
    } as ReturnType<typeof useSubscriberCount>);

    renderTab();

    fireEvent.click(screen.getByRole('button', { name: /Send Newsletter/i }));

    await waitFor(() => {
      expect(screen.getByText(/using 'newsletter-event'/)).toBeInTheDocument();
    });
  });

  // ── AC7: Create new template link ────────────────────────────────────────────

  it('AC7: shows create new template link pointing to email-templates admin tab', () => {
    vi.mocked(useSubscriberCount).mockReturnValue({
      isLoading: false,
      data: { totalActive: 0 },
    } as ReturnType<typeof useSubscriberCount>);

    renderTab();

    const link = screen.getByRole('link', { name: /create new template/i });
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', '/organizer/admin?tab=email-templates');
  });
});
