/**
 * TopicStatusPanel Tests
 * Story 8.2: Organizer topic status panel.
 *
 * Covers the responsive table behaviour added for organizer mobile support:
 * the table lives in a horizontally scrollable container, and the low-value
 * Date + Planned-Event columns are hidden at the xs breakpoint.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
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

/** True when the element is hidden (display:none) at the xs base breakpoint. */
const isHiddenAtXs = (el: HTMLElement): boolean => {
  const css = cssForElement(el);
  return /@media\s*\(min-width:\s*0px\)\s*\{[^}]*display:\s*none[^}]*\}/.test(css);
};

describe('TopicStatusPanel', () => {
  beforeEach(() => {
    vi.mocked(partnerTopicsApi.getTopics).mockResolvedValue(mockTopics);
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('should_renderTopicsTableInScrollableContainer_when_topicsLoaded', async () => {
    renderPanel();

    const container = await screen.findByTestId('organizer-topics-table');
    expect(container).toBeInTheDocument();
    // The TableContainer enables horizontal scrolling on narrow viewports.
    expect(cssForElement(container)).toMatch(/overflow-x:\s*auto/);

    expect(screen.getByText('Event-Driven Architecture')).toBeInTheDocument();
    expect(screen.getByText('Zero-Trust Networking')).toBeInTheDocument();
  });

  it('should_hideDateAndPlannedEventColumns_when_xsViewport', async () => {
    renderPanel();
    await screen.findByTestId('organizer-topics-table');

    const headerRow = screen.getAllByRole('row')[0];
    const headerCells = within(headerRow).getAllByRole('columnheader');

    // Locate Date + Planned-Event header cells by their i18n key text.
    const dateHeader = screen.getByText('common:labels.date').closest('th') as HTMLElement;
    const plannedHeader = screen
      .getByText('portal.topics.organizer.plannedEvent')
      .closest('th') as HTMLElement;
    expect(dateHeader).toBeTruthy();
    expect(plannedHeader).toBeTruthy();

    // Both low-value columns collapse to display:none at the xs base breakpoint.
    expect(isHiddenAtXs(dateHeader)).toBe(true);
    expect(isHiddenAtXs(plannedHeader)).toBe(true);

    // A body-level Date cell is likewise hidden at xs.
    const firstBodyRow = screen.getByTestId('organizer-topic-row-topic-1');
    const dateBodyCell = within(firstBodyRow).getByText('15 Jan 2026').closest('td') as HTMLElement;
    expect(isHiddenAtXs(dateBodyCell)).toBe(true);

    // Always-visible columns (Title, Status) must NOT be hidden at xs.
    const titleHeader = screen.getByText('common:labels.title').closest('th') as HTMLElement;
    const statusHeader = screen.getByText('common:labels.status').closest('th') as HTMLElement;
    expect(isHiddenAtXs(titleHeader)).toBe(false);
    expect(isHiddenAtXs(statusHeader)).toBe(false);

    // Sanity: the header still has more columns than just the hidden two.
    expect(headerCells.length).toBeGreaterThan(2);
  });
});
