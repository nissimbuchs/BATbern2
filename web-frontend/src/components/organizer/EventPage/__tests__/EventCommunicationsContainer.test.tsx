/**
 * EventCommunicationsContainer tests (Epic 16, Stories 16.3, 16.4, 16.8b)
 *
 * The mail page: "What went out" and "Handled automatically", plus the "+ New mail" picker. Since
 * 16.8b four audiences open the shared MailComposer and Partners keeps PartnerMeetingsCompose
 * (AC15), inline at the top (desktop) or full screen (mobile). Composers and the list have their
 * own tests, so they are stubbed here; the stubs expose `onSent` to drive the after-send path.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { EventCommunicationsContainer } from '../EventCommunicationsContainer';
import type { Event } from '@/types/event.types';

const { getMailCounts, stub, breakpoints, sentListProps } = vi.hoisted(() => {
  type StubProps = { eventCode?: string; onSent?: () => void };
  return {
    getMailCounts: vi.fn(),
    breakpoints: { isMobile: false, isTablet: false, isDesktop: true },
    sentListProps: { highlightKeys: undefined as string[] | undefined },
    stub:
      (id: string) =>
      ({ onSent }: StubProps) => (
        <div data-testid={id}>
          <button onClick={() => onSent?.()}>send-{id}</button>
        </div>
      ),
  };
});

vi.mock('@/services/mailMessageService', () => ({
  getMailCounts: (...args: unknown[]) => getMailCounts(...args),
  listMailMessages: vi.fn(),
  getMailMessage: vi.fn(),
}));

vi.mock('../communications/composer/MailComposer', () => ({
  MailComposer: ({
    audience,
    onSent,
  }: {
    audience: string;
    onSent: (r: {
      mailKeys: string[];
      target: string;
      recipientCount: number;
      skipped: [];
    }) => void;
  }) => (
    <div data-testid={`stub-composer-${audience}`}>
      <button
        onClick={() =>
          onSent({ mailKeys: ['BAT54-7'], target: 'RECIPIENTS', recipientCount: 3, skipped: [] })
        }
      >
        send-{audience}
      </button>
    </div>
  ),
}));
vi.mock('../communications/PartnerMeetingsCompose', () => ({
  PartnerMeetingsCompose: stub('stub-partners'),
}));
vi.mock('../communications/MailSentList', () => ({
  MailSentList: ({ highlightKeys }: { highlightKeys?: string[] }) => {
    sentListProps.highlightKeys = highlightKeys;
    return <div data-testid="stub-sent-list">{(highlightKeys ?? []).join(',')}</div>;
  },
}));
vi.mock('@/hooks/useBreakpoints', () => ({ useBreakpoints: () => breakpoints }));
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: { count?: number } | string) =>
      typeof opts === 'object' && opts.count !== undefined ? `${key}:${opts.count}` : key,
  }),
}));

const mockEvent = { eventCode: 'BAT54', title: 'Spring Conf' } as Event;

function renderContainer() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <EventCommunicationsContainer event={mockEvent} eventCode="BAT54" />
      </MemoryRouter>
    </QueryClientProvider>
  );
  return { invalidate };
}

describe('EventCommunicationsContainer', () => {
  beforeEach(() => {
    getMailCounts.mockReset().mockResolvedValue({ failedDeliveries: 3 });
    breakpoints.isMobile = false;
    sentListProps.highlightKeys = undefined;
  });

  it('shows "What went out" and a collapsed "Handled automatically" with the failed count', async () => {
    renderContainer();
    expect(screen.getByText('eventPage.mail.whatWentOut')).toBeInTheDocument();
    expect(screen.getByTestId('stub-sent-list')).toBeInTheDocument();

    const automatic = screen.getByTestId('mail-automatic');
    const summary = within(automatic).getByRole('button', {
      name: 'eventPage.mail.handledAutomatically',
    });
    expect(summary).toHaveAttribute('aria-expanded', 'false');

    fireEvent.click(summary);
    await waitFor(() =>
      expect(screen.getByTestId('mail-automatic-failed')).toHaveTextContent(
        'eventPage.mail.failedDeliveries:3'
      )
    );
    expect(getMailCounts).toHaveBeenCalledWith('BAT54');
  });

  it('offers the five audiences in the "+ New mail" menu', () => {
    renderContainer();
    fireEvent.click(screen.getByTestId('new-mail-button'));
    for (const audience of ['newsletter', 'registrants', 'speakers', 'venue', 'partners']) {
      expect(screen.getByTestId(`new-mail-${audience}`)).toBeInTheDocument();
    }
  });

  it.each([
    ['newsletter', 'stub-composer-NEWSLETTER'],
    ['registrants', 'stub-composer-REGISTRANTS'],
    ['speakers', 'stub-composer-SPEAKERS'],
    ['venue', 'stub-composer-VENUE'],
    ['partners', 'stub-partners'],
  ])('opens the %s composer inline above the list', async (audience, stubId) => {
    renderContainer();
    fireEvent.click(screen.getByTestId('new-mail-button'));
    fireEvent.click(screen.getByTestId(`new-mail-${audience}`));

    const panel = await screen.findByTestId(`compose-panel-${audience}`);
    expect(within(panel).getByTestId(stubId)).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(
      panel.compareDocumentPosition(screen.getByTestId('stub-sent-list')) &
        Node.DOCUMENT_POSITION_FOLLOWING
    ).toBeTruthy();
    await waitFor(() =>
      expect(
        within(panel).getByRole('heading', { name: `eventPage.mail.compose.${audience}` })
      ).toHaveFocus()
    );
  });

  it('mobile: opens the composer as a full-screen dialog', async () => {
    breakpoints.isMobile = true;
    renderContainer();
    fireEvent.click(screen.getByTestId('new-mail-button'));
    fireEvent.click(screen.getByTestId('new-mail-speakers'));

    const dialog = await screen.findByRole('dialog', { name: 'eventPage.mail.compose.speakers' });
    expect(within(dialog).getByTestId('stub-composer-SPEAKERS')).toBeInTheDocument();

    fireEvent.click(within(dialog).getByRole('button', { name: 'common:actions.close' }));
    await waitFor(() =>
      expect(screen.queryByTestId('stub-composer-SPEAKERS')).not.toBeInTheDocument()
    );
    await waitFor(() => expect(screen.getByTestId('new-mail-button')).toHaveFocus());
  });

  it('closes the composer on Esc and returns focus to the "+ New mail" button', async () => {
    renderContainer();
    const button = screen.getByTestId('new-mail-button');
    fireEvent.click(button);
    fireEvent.click(screen.getByTestId('new-mail-speakers'));
    const panel = await screen.findByTestId('compose-panel-speakers');

    fireEvent.keyDown(panel, { key: 'Escape' });

    await waitFor(() =>
      expect(screen.queryByTestId('stub-composer-SPEAKERS')).not.toBeInTheDocument()
    );
    await waitFor(() => expect(button).toHaveFocus());
  });

  it('after a send closes the composer, refreshes the list, confirms and highlights the new row (AC13)', async () => {
    const { invalidate } = renderContainer();
    fireEvent.click(screen.getByTestId('new-mail-button'));
    fireEvent.click(screen.getByTestId('new-mail-registrants'));
    fireEvent.click(await screen.findByText('send-REGISTRANTS'));

    await waitFor(() =>
      expect(screen.queryByTestId('stub-composer-REGISTRANTS')).not.toBeInTheDocument()
    );
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['mails', 'BAT54'] });
    expect(screen.getByTestId('mail-sent-toast')).toHaveTextContent('eventPage.mail.sentToast');
    expect(sentListProps.highlightKeys).toEqual(['BAT54-7']);
  });

  it('partners still opens PartnerMeetingsCompose; its send refreshes without a highlight (AC15)', async () => {
    const { invalidate } = renderContainer();
    fireEvent.click(screen.getByTestId('new-mail-button'));
    fireEvent.click(screen.getByTestId('new-mail-partners'));
    fireEvent.click(await screen.findByText('send-stub-partners'));

    await waitFor(() => expect(screen.queryByTestId('stub-partners')).not.toBeInTheDocument());
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['mails', 'BAT54'] });
    expect(sentListProps.highlightKeys ?? []).toEqual([]);
  });
});
