/**
 * EventParticipantTable Component Tests
 *
 * TDD Tests for event participant table component
 * RED Phase: Tests written first
 */

import { render, screen, within } from '@testing-library/react';
import { describe, it, expect, vi, afterEach } from 'vitest';
import userEvent from '@testing-library/user-event';
import EventParticipantTable from './EventParticipantTable';
import type { EventParticipant } from '../../../types/eventParticipant.types';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

// Mock translation
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

// Mock CompanyCell component
vi.mock('../UserManagement/CompanyCell', () => ({
  default: ({ companyId }: { companyId?: string }) => (
    <div data-testid="company-cell">{companyId || 'N/A'}</div>
  ),
}));

const mockParticipants: EventParticipant[] = [
  {
    registrationCode: 'REG-001',
    eventCode: 'BAT-2024-01',
    attendeeUsername: 'john.doe',
    firstName: 'John',
    lastName: 'Doe',
    email: 'john.doe@example.com',
    company: {
      id: 'company-1',
      name: 'Centris AG',
      logo: 'https://example.com/logo.png',
    },
    status: 'CONFIRMED',
    registrationDate: '2024-01-15T10:30:00Z',
  },
  {
    registrationCode: 'REG-002',
    eventCode: 'BAT-2024-01',
    attendeeUsername: 'jane.smith',
    firstName: 'Jane',
    lastName: 'Smith',
    email: 'jane.smith@example.com',
    company: {
      id: 'company-2',
      name: 'Puzzle ITC',
    },
    status: 'REGISTERED',
    registrationDate: '2024-01-16T14:20:00Z',
  },
  {
    registrationCode: 'REG-003',
    eventCode: 'BAT-2024-01',
    attendeeUsername: 'bob.wilson',
    firstName: 'Bob',
    lastName: 'Wilson',
    email: 'bob.wilson@example.com',
    status: 'WAITLISTED',
    registrationDate: '2024-01-17T09:00:00Z',
  },
];

const renderWithProviders = (ui: React.ReactElement) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
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
 * True when the element collapses to display:none at the xs base breakpoint.
 * MUI compiles `display: { xs: 'none', sm: 'table-cell' }` into per-breakpoint
 * `@media (min-width:…)` rules; the xs value lands behind `@media (min-width:0px)`,
 * which jsdom never applies — so we inspect the injected stylesheet directly.
 */
const isHiddenAtXs = (el: HTMLElement): boolean => {
  const css = cssForElement(el);
  return /@media\s*\(min-width:\s*0px\)\s*\{[^}]*display:\s*none[^}]*\}/.test(css);
};

describe('EventParticipantTable Component', () => {
  describe('Rendering', () => {
    it('should render table headers', () => {
      renderWithProviders(
        <EventParticipantTable participants={mockParticipants} isLoading={false} />
      );

      expect(screen.getByText('common:labels.name')).toBeInTheDocument();
      expect(screen.getByText('common:labels.email')).toBeInTheDocument();
      expect(screen.getByText('common:labels.company')).toBeInTheDocument();
      expect(screen.getByText('common:labels.status')).toBeInTheDocument();
      expect(
        screen.getByText('eventPage.participantTable.headers.registrationDate')
      ).toBeInTheDocument();
    });

    it('should render participant rows', () => {
      renderWithProviders(
        <EventParticipantTable participants={mockParticipants} isLoading={false} />
      );

      expect(screen.getByText('John Doe')).toBeInTheDocument();
      expect(screen.getByText('john.doe@example.com')).toBeInTheDocument();
      expect(screen.getByText('Jane Smith')).toBeInTheDocument();
      expect(screen.getByText('jane.smith@example.com')).toBeInTheDocument();
    });

    it('should show empty state when no participants', () => {
      renderWithProviders(<EventParticipantTable participants={[]} isLoading={false} />);

      expect(screen.getByText('eventPage.participantTable.empty')).toBeInTheDocument();
    });

    it('should render participant avatars with initials', () => {
      renderWithProviders(
        <EventParticipantTable participants={mockParticipants} isLoading={false} />
      );

      const firstRow = screen.getByText('John Doe').closest('tr');
      expect(firstRow).toBeInTheDocument();

      // Avatar should contain initials
      const avatar = within(firstRow!).getByText('JD');
      expect(avatar).toBeInTheDocument();
    });

    it('should render company cell for participants with company', () => {
      renderWithProviders(
        <EventParticipantTable participants={mockParticipants} isLoading={false} />
      );

      const companyCells = screen.getAllByTestId('company-cell');
      expect(companyCells).toHaveLength(3);
      // Table sorts by name ascending by default: Bob, Jane, John
      expect(companyCells[0]).toHaveTextContent('N/A'); // Bob has no company
      expect(companyCells[1]).toHaveTextContent('company-2'); // Jane
      expect(companyCells[2]).toHaveTextContent('company-1'); // John
    });

    it('should_hideCompanyAndRegistrationDateColumns_when_xsViewport', () => {
      renderWithProviders(
        <EventParticipantTable participants={mockParticipants} isLoading={false} />
      );

      const companyHeader = screen.getByText('common:labels.company').closest('th') as HTMLElement;
      const dateHeader = screen
        .getByText('eventPage.participantTable.headers.registrationDate')
        .closest('th') as HTMLElement;
      const nameHeader = screen.getByText('common:labels.name').closest('th') as HTMLElement;

      // Low-value columns collapse to display:none at the xs base breakpoint…
      expect(isHiddenAtXs(companyHeader)).toBe(true);
      expect(isHiddenAtXs(dateHeader)).toBe(true);
      // …while the Name column stays visible.
      expect(isHiddenAtXs(nameHeader)).toBe(false);
    });

    it('should render status chips with correct labels', () => {
      renderWithProviders(
        <EventParticipantTable participants={mockParticipants} isLoading={false} />
      );

      expect(screen.getByText('eventPage.participantFilters.status.confirmed')).toBeInTheDocument();
      expect(
        screen.getByText('eventPage.participantFilters.status.registered')
      ).toBeInTheDocument();
      expect(
        screen.getByText('eventPage.participantFilters.status.waitlisted')
      ).toBeInTheDocument();
    });
  });

  describe('Sorting', () => {
    it('should sort by name when name header clicked', async () => {
      const user = userEvent.setup();

      renderWithProviders(
        <EventParticipantTable participants={mockParticipants} isLoading={false} />
      );

      const nameHeader = screen.getByText('common:labels.name');

      // Table defaults to name ascending (Bob is already first)
      let rows = screen.getAllByRole('row');
      expect(within(rows[1]).getByText(/Bob/)).toBeInTheDocument();

      // Click to toggle to descending (John should be first)
      await user.click(nameHeader);
      rows = screen.getAllByRole('row');
      expect(within(rows[1]).getByText(/John/)).toBeInTheDocument();
    });

    it('should toggle sort direction when clicking same header twice', async () => {
      const user = userEvent.setup();

      renderWithProviders(
        <EventParticipantTable participants={mockParticipants} isLoading={false} />
      );

      const nameHeader = screen.getByText('common:labels.name');

      // Default is ascending (Bob first)
      let rows = screen.getAllByRole('row');
      expect(within(rows[1]).getByText(/Bob/)).toBeInTheDocument();

      // First click - toggle to descending (John first)
      await user.click(nameHeader);
      rows = screen.getAllByRole('row');
      expect(within(rows[1]).getByText(/John/)).toBeInTheDocument();

      // Second click - toggle back to ascending (Bob first)
      await user.click(nameHeader);
      rows = screen.getAllByRole('row');
      expect(within(rows[1]).getByText(/Bob/)).toBeInTheDocument();
    });

    it('should sort by email when email header clicked', async () => {
      const user = userEvent.setup();

      renderWithProviders(
        <EventParticipantTable participants={mockParticipants} isLoading={false} />
      );

      const emailHeader = screen.getByText('common:labels.email');
      await user.click(emailHeader);

      const rows = screen.getAllByRole('row');
      // Bob's email (bob.wilson@...) should be first alphabetically
      expect(within(rows[1]).getByText('bob.wilson@example.com')).toBeInTheDocument();
    });

    it('should sort by registration date when date header clicked', async () => {
      const user = userEvent.setup();

      renderWithProviders(
        <EventParticipantTable participants={mockParticipants} isLoading={false} />
      );

      const dateHeader = screen.getByText('eventPage.participantTable.headers.registrationDate');
      await user.click(dateHeader);

      const rows = screen.getAllByRole('row');
      // John registered first (2024-01-15)
      expect(within(rows[1]).getByText('John Doe')).toBeInTheDocument();
    });
  });

  describe('Interactions', () => {
    it('should call onRowClick when row is clicked', async () => {
      const user = userEvent.setup();
      const mockRowClick = vi.fn();

      renderWithProviders(
        <EventParticipantTable
          participants={mockParticipants}
          isLoading={false}
          onRowClick={mockRowClick}
        />
      );

      const firstRow = screen.getByText('John Doe').closest('tr');
      await user.click(firstRow!);

      expect(mockRowClick).toHaveBeenCalledWith(mockParticipants[0]);
    });

    it('should apply hover style on row hover', () => {
      const mockRowClick = vi.fn();

      renderWithProviders(
        <EventParticipantTable
          participants={mockParticipants}
          isLoading={false}
          onRowClick={mockRowClick}
        />
      );

      // When onRowClick is provided, cursor should be pointer
      const firstRow = screen.getByText(/John/).closest('tr');
      expect(firstRow).toHaveStyle({ cursor: 'pointer' });
    });
  });

  describe('Loading State', () => {
    it('should show loading skeleton when isLoading is true', () => {
      renderWithProviders(<EventParticipantTable participants={[]} isLoading={true} />);

      // Should show loading skeleton rows
      const skeletons = screen.getAllByTestId('participant-skeleton');
      expect(skeletons.length).toBeGreaterThan(0);
    });
  });

  describe('Date Formatting', () => {
    it('should format registration date correctly', () => {
      renderWithProviders(
        <EventParticipantTable participants={mockParticipants} isLoading={false} />
      );

      // Should display formatted date (exact format depends on implementation)
      // Looking for any date-like format in the first row
      const firstRow = screen.getByText('John Doe').closest('tr');
      expect(firstRow).toBeInTheDocument();
      // The formatted date should be visible somewhere in the row
      expect(within(firstRow!).getByText(/2024|Jan|15/)).toBeInTheDocument();
    });
  });

  describe('Mobile card view', () => {
    const originalMatchMedia = window.matchMedia;

    // Simulate a 375px phone so useBreakpoints().isMobile (down('md')) resolves true.
    const installMobileMatchMedia = (viewportWidth: number) => {
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

    afterEach(() => {
      window.matchMedia = originalMatchMedia;
    });

    it('should_renderCards_when_mobileViewport', () => {
      installMobileMatchMedia(375);
      renderWithProviders(
        <EventParticipantTable participants={mockParticipants} isLoading={false} />
      );

      // Cards container replaces the table.
      expect(screen.getByTestId('participant-cards')).toBeInTheDocument();
      expect(screen.queryByRole('table')).not.toBeInTheDocument();
      expect(screen.getByTestId('participant-card-REG-001')).toBeInTheDocument();
      expect(screen.getByText('John Doe')).toBeInTheDocument();
    });

    it('should_renderTable_when_desktopViewport', () => {
      installMobileMatchMedia(1280);
      renderWithProviders(
        <EventParticipantTable participants={mockParticipants} isLoading={false} />
      );

      expect(screen.getByRole('table')).toBeInTheDocument();
      expect(screen.queryByTestId('participant-cards')).not.toBeInTheDocument();
    });
  });

  describe('Waitlist mode (Epic 14 FR28)', () => {
    const waitlisted: EventParticipant[] = [
      {
        registrationCode: 'WL-2',
        eventCode: 'BAT-2024-01',
        attendeeUsername: 'second',
        firstName: 'Second',
        lastName: 'Person',
        email: 'second@example.com',
        status: 'WAITLIST',
        registrationDate: '2024-02-02T10:00:00Z',
        waitlistPosition: 2,
      },
      {
        registrationCode: 'WL-1',
        eventCode: 'BAT-2024-01',
        attendeeUsername: 'first',
        firstName: 'First',
        lastName: 'Person',
        email: 'first@example.com',
        status: 'WAITLIST',
        registrationDate: '2024-02-01T10:00:00Z',
        waitlistPosition: 1,
      },
    ];

    it('orders rows by waitlist position and shows the position cell', () => {
      renderWithProviders(
        <EventParticipantTable participants={waitlisted} isLoading={false} waitlistMode />
      );

      expect(screen.getByTestId('waitlist-position-WL-1')).toHaveTextContent('#1');
      expect(screen.getByTestId('waitlist-position-WL-2')).toHaveTextContent('#2');

      // Position #1 (WL-1) must render before #2 (WL-2) despite array order.
      const rows = screen.getAllByRole('row').slice(1); // drop header
      expect(within(rows[0]).getByText('First Person')).toBeInTheDocument();
    });

    it('falls back to page-offset numbering when waitlistPosition is absent', () => {
      const noPos = waitlisted.map(({ waitlistPosition, ...p }) => p);
      renderWithProviders(
        <EventParticipantTable
          participants={noPos}
          isLoading={false}
          waitlistMode
          pageOffset={25}
        />
      );
      // No positions → queue order falls back to registration date asc, then
      // page-offset numbering: WL-1 (earlier date) = #26, WL-2 = #27.
      expect(screen.getByTestId('waitlist-position-WL-1')).toHaveTextContent('#26');
      expect(screen.getByTestId('waitlist-position-WL-2')).toHaveTextContent('#27');
    });

    it('invokes onPromote (not an auto-commit) when the inline Promote button is clicked', async () => {
      const onPromote = vi.fn();
      const user = userEvent.setup();
      renderWithProviders(
        <EventParticipantTable
          participants={waitlisted}
          isLoading={false}
          waitlistMode
          onPromote={onPromote}
        />
      );

      await user.click(screen.getByTestId('waitlist-promote-WL-1'));
      expect(onPromote).toHaveBeenCalledWith(expect.objectContaining({ registrationCode: 'WL-1' }));
    });

    it('does not show position cells or Promote buttons outside waitlist mode', () => {
      renderWithProviders(
        <EventParticipantTable participants={mockParticipants} isLoading={false} />
      );
      expect(screen.queryByTestId('waitlist-position-REG-001')).not.toBeInTheDocument();
      expect(screen.queryByTestId('waitlist-promote-REG-001')).not.toBeInTheDocument();
    });
  });
});
