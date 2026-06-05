/**
 * TopicStatusPanel Tests
 * Story 8.2: Organizer topic status panel.
 *
 * Covers the responsive table behaviour added for organizer mobile support:
 * the table lives in a horizontally scrollable container, and the low-value
 * Date + Planned-Event columns are hidden at the xs breakpoint.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material/styles';
import { theme } from '@/theme/theme';
import TopicStatusPanel from './TopicStatusPanel';
import * as partnerTopicsApi from '@/services/api/partnerTopicsApi';
import type { TopicDTO } from '@/services/api/partnerTopicsApi';

// Mock i18n — return the key (namespace-stripped) so assertions stay locale-agnostic.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'en' },
  }),
}));

// Mock CompanyLogo (pulls company data via react-query) and the edit dialog —
// both are exercised by their own tests; here we only need a stable render.
vi.mock('@/components/shared/Company/CompanyLogo', () => ({
  default: ({ companyName }: { companyName: string }) => (
    <div data-testid="company-logo">{companyName}</div>
  ),
}));
vi.mock('@/components/partner/TopicSuggestionForm', () => ({
  TopicSuggestionForm: () => null,
}));

vi.mock('@/services/api/partnerTopicsApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/services/api/partnerTopicsApi')>();
  return { ...actual, getTopics: vi.fn() };
});

const mockTopics: TopicDTO[] = [
  {
    id: 'topic-1',
    title: 'Event-Driven Architecture',
    description: 'Patterns and pitfalls',
    suggestedByCompany: 'Centris AG',
    voteCount: 12,
    currentPartnerHasVoted: false,
    status: 'PROPOSED',
    plannedEvent: null,
    createdAt: '2026-01-15T10:00:00Z',
  },
  {
    id: 'topic-2',
    title: 'Zero-Trust Networking',
    description: null,
    suggestedByCompany: 'Puzzle ITC',
    voteCount: 7,
    currentPartnerHasVoted: false,
    status: 'SELECTED',
    plannedEvent: 'BATbern99',
    createdAt: '2026-02-01T09:00:00Z',
  },
];

const renderPanel = () => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme}>
        <TopicStatusPanel />
      </ThemeProvider>
    </QueryClientProvider>
  );
};

/** Concatenate every emotion <style> rule that targets the element's css-* class. */
const cssForElement = (el: HTMLElement): string => {
  const cssClass = Array.from(el.classList).find((c) => c.startsWith('css-'));
  if (!cssClass) return '';
  let combined = '';
  document.querySelectorAll('style').forEach((styleEl) => {
    const css = styleEl.textContent ?? '';
    if (css.includes(`.${cssClass}`)) combined += css + '\n';
  });
  return combined;
};

/**
 * Deterministic matchMedia mock simulating a viewport of the given width.
 * Parses the max-/min-width px value out of each MUI breakpoint query so a 375px
 * phone matches down('md') while a 1280px desktop does not.
 */
const installMatchMediaForWidth = (viewportWidth: number) => {
  window.matchMedia = vi.fn((query: string) => {
    const maxMatch = /max-width:\s*([\d.]+)px/.exec(query);
    const minMatch = /min-width:\s*([\d.]+)px/.exec(query);
    let matches = false;
    if (maxMatch) matches = viewportWidth <= parseFloat(maxMatch[1]);
    else if (minMatch) matches = viewportWidth >= parseFloat(minMatch[1]);
    return {
      matches,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    };
  }) as unknown as typeof window.matchMedia;
};

describe('TopicStatusPanel', () => {
  const originalMatchMedia = window.matchMedia;

  beforeEach(() => {
    vi.mocked(partnerTopicsApi.getTopics).mockResolvedValue(mockTopics);
    installMatchMediaForWidth(1280); // default to desktop unless a test overrides
  });

  afterEach(() => {
    vi.clearAllMocks();
    window.matchMedia = originalMatchMedia;
  });

  it('should_renderTopicsTableInScrollableContainer_when_topicsLoaded', async () => {
    installMatchMediaForWidth(1280);
    renderPanel();

    const container = await screen.findByTestId('organizer-topics-table');
    expect(container).toBeInTheDocument();
    // The TableContainer enables horizontal scrolling on narrow viewports.
    expect(cssForElement(container)).toMatch(/overflow-x:\s*auto/);

    expect(screen.getByText('Event-Driven Architecture')).toBeInTheDocument();
    expect(screen.getByText('Zero-Trust Networking')).toBeInTheDocument();
  });

  it('should_renderTable_when_desktopViewport', async () => {
    installMatchMediaForWidth(1280);
    renderPanel();

    await screen.findByTestId('organizer-topics-table');
    expect(screen.queryByTestId('organizer-topics-cards')).not.toBeInTheDocument();
  });

  it('should_renderCards_when_mobileViewport', async () => {
    installMatchMediaForWidth(375);
    renderPanel();

    const cards = await screen.findByTestId('organizer-topics-cards');
    expect(cards).toBeInTheDocument();
    expect(screen.queryByTestId('organizer-topics-table')).not.toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();

    // Each topic renders as a card with its title…
    expect(screen.getByTestId('organizer-topic-card-topic-1')).toBeInTheDocument();
    expect(screen.getByText('Event-Driven Architecture')).toBeInTheDocument();
    // …and the SAME status select + actions present in the card.
    expect(screen.getByTestId('status-select-topic-1')).toBeInTheDocument();
    expect(screen.getByTestId('save-status-topic-1')).toBeInTheDocument();
    expect(screen.getByTestId('edit-topic-topic-1')).toBeInTheDocument();
    expect(screen.getByTestId('delete-topic-topic-1')).toBeInTheDocument();
  });

  it('should_showPlannedEventField_when_mobileCardStatusSelected', async () => {
    installMatchMediaForWidth(375);
    renderPanel();

    await screen.findByTestId('organizer-topics-cards');
    // topic-2 is SELECTED with a plannedEvent — the field renders inside the card.
    expect(screen.getByTestId('planned-event-topic-2')).toBeInTheDocument();
    // topic-1 is PROPOSED — no planned-event field.
    expect(screen.queryByTestId('planned-event-topic-1')).not.toBeInTheDocument();
  });
});
