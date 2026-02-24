/**
 * UnassignedSpeakersList Component Tests (Story 5.7 - Task 4a RED Phase)
 *
 * Tests for unassigned speakers list sidebar
 * Following TDD: These tests MUST fail until implementation (Task 4b)
 *
 * Coverage:
 * - AC12: Show unassigned speakers list with real-time updates
 * - AC5: Draggable speaker cards with grab handle
 * - AC7: View preferences button per speaker
 */

import { render, screen, fireEvent, within } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { BrowserRouter } from 'react-router-dom';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import { UnassignedSpeakersList } from '../UnassignedSpeakersList';
import type { Session } from '@/types/event.types';

const renderWithProviders = (ui: React.ReactElement) => {
  return render(
    <BrowserRouter>
      <I18nextProvider i18n={i18n}>{ui}</I18nextProvider>
    </BrowserRouter>
  );
};

describe('UnassignedSpeakersList Component (Story 5.7 - Task 4a RED Phase)', () => {
  const mockUnassignedSessions: Session[] = [
    {
      sessionSlug: 'session-1',
      eventCode: 'BATbern142',
      title: 'John Doe - Acme Corp',
      startTime: null,
      endTime: null,
      room: null,
      speakers: [{ username: 'john.doe', displayName: 'John Doe', companyName: 'Acme Corp' }],
    },
    {
      sessionSlug: 'session-2',
      eventCode: 'BATbern142',
      title: 'Jane Smith - Tech Inc',
      startTime: null,
      endTime: null,
      room: null,
      speakers: [{ username: 'jane.smith', displayName: 'Jane Smith', companyName: 'Tech Inc' }],
    },
    {
      sessionSlug: 'session-3',
      eventCode: 'BATbern142',
      title: 'Bob Johnson - Startup Labs',
      startTime: null,
      endTime: null,
      room: null,
      speakers: [
        { username: 'bob.johnson', displayName: 'Bob Johnson', companyName: 'Startup Labs' },
      ],
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('List Rendering', () => {
    it('should_displayAllUnassignedSessions_when_loaded', () => {
      // AC12: Show unassigned speakers list
      // Given: 3 unassigned sessions exist
      renderWithProviders(
        <UnassignedSpeakersList
          sessions={mockUnassignedSessions}
          totalSessions={10}
          onViewPreferences={() => {}}
        />
      );

      // Then: Displays all 3 unassigned speakers
      expect(screen.getByText('John Doe - Acme Corp')).toBeInTheDocument();
      expect(screen.getByText('Jane Smith - Tech Inc')).toBeInTheDocument();
      expect(screen.getByText('Bob Johnson - Startup Labs')).toBeInTheDocument();
    });

    it('should_displayProgressIndicator_when_rendered', () => {
      // AC12: Progress indicator - "X of Y assigned (Z%)"
      // Given: 3 unassigned out of 10 total (7 assigned)
      renderWithProviders(
        <UnassignedSpeakersList
          sessions={mockUnassignedSessions}
          totalSessions={10}
          onViewPreferences={() => {}}
        />
      );

      // Then: Shows progress "7 of 10 assigned (70%)"
      expect(screen.getByText(/7.*of.*10.*assigned/i)).toBeInTheDocument();
      expect(screen.getByText(/70%/)).toBeInTheDocument();
    });

    it('should_displayUnassignedBadge_when_sessionsRemain', () => {
      // Unassigned badge: "🔶 N Remaining"
      // Given: 3 unassigned sessions
      renderWithProviders(
        <UnassignedSpeakersList
          sessions={mockUnassignedSessions}
          totalSessions={10}
          onViewPreferences={() => {}}
        />
      );

      // Then: Shows unassigned badge
      expect(screen.getByText(/3.*remaining/i)).toBeInTheDocument();
      expect(screen.getByTestId('unassigned-badge')).toBeInTheDocument();
    });

    it('should_showEmptyState_when_noUnassignedSessions', () => {
      // Given: All sessions are assigned (empty list)
      renderWithProviders(
        <UnassignedSpeakersList sessions={[]} totalSessions={10} onViewPreferences={() => {}} />
      );

      // Then: Shows success message
      expect(screen.getByText(/all sessions assigned/i)).toBeInTheDocument();
      expect(screen.getByTestId('empty-state')).toBeInTheDocument();
    });
  });

  describe('Filter Controls', () => {
    it('should_displayFilterButtons_when_rendered', () => {
      // Filters: [All] [Assigned] [Unassigned]
      // Given: Component is rendered
      renderWithProviders(
        <UnassignedSpeakersList
          sessions={mockUnassignedSessions}
          totalSessions={10}
          onViewPreferences={() => {}}
        />
      );

      // Then: Shows filter buttons (use testids for multiple buttons with same text)
      expect(screen.getByTestId('filter-all')).toBeInTheDocument();
      expect(screen.getByTestId('filter-assigned')).toBeInTheDocument();
      expect(screen.getByTestId('filter-unassigned')).toBeInTheDocument();
    });

    it('should_highlightActiveFilter_when_selected', () => {
      // Given: [Unassigned] filter is active
      renderWithProviders(
        <UnassignedSpeakersList
          sessions={mockUnassignedSessions}
          totalSessions={10}
          onViewPreferences={() => {}}
          activeFilter="unassigned"
        />
      );

      // Then: Unassigned button is highlighted
      const unassignedButton = screen.getByRole('button', { name: /unassigned/i });
      expect(unassignedButton).toHaveClass('filter-active');
    });

    it('should_updateList_when_filterChanged', () => {
      // Given: Initially showing unassigned sessions
      const onFilterChange = vi.fn();

      renderWithProviders(
        <UnassignedSpeakersList
          sessions={mockUnassignedSessions}
          totalSessions={10}
          onViewPreferences={() => {}}
          onFilterChange={onFilterChange}
        />
      );

      const allButton = screen.getByRole('button', { name: /all/i });

      // When: User clicks [All] filter
      fireEvent.click(allButton);

      // Then: Filter change callback is triggered
      expect(onFilterChange).toHaveBeenCalledWith('all');
    });
  });

  describe('Draggable Speaker Cards', () => {
    it('should_renderDraggableCards_when_displayed', () => {
      // AC5: Draggable speaker cards with grab handle
      // Given: Unassigned sessions exist
      renderWithProviders(
        <UnassignedSpeakersList
          sessions={mockUnassignedSessions}
          totalSessions={10}
          onViewPreferences={() => {}}
        />
      );

      // Then: Cards are draggable
      const speakerCards = screen.getAllByRole('article');
      speakerCards.forEach((card) => {
        expect(card).toHaveAttribute('draggable', 'true');
      });
    });

    it('should_showGrabHandle_when_hovering', () => {
      // Given: User hovers over speaker card
      renderWithProviders(
        <UnassignedSpeakersList
          sessions={mockUnassignedSessions}
          totalSessions={10}
          onViewPreferences={() => {}}
        />
      );

      const firstCard = screen.getAllByRole('article')[0];

      // When: Mouse enters card
      fireEvent.mouseEnter(firstCard);

      // Then: Grab handle is visible and cursor changes
      expect(firstCard).toHaveStyle({ cursor: 'grab' });
      // Actual testid is 'drag-handle' not 'grab-handle'
      expect(within(firstCard).getByTestId('drag-handle')).toBeVisible();
    });
  });

  describe('View Preferences Button', () => {
    it('should_displayViewPreferencesButton_when_rendered', () => {
      // AC7: [Preferences] button per speaker
      // Given: Speaker cards are displayed
      renderWithProviders(
        <UnassignedSpeakersList
          sessions={mockUnassignedSessions}
          totalSessions={10}
          onViewPreferences={() => {}}
        />
      );

      // Then: Each card has [Preferences] button (actual button text)
      const preferenceButtons = screen.getAllByRole('button', { name: /preferences/i });
      expect(preferenceButtons).toHaveLength(3);
    });

    it('should_openPreferencesPanel_when_buttonClicked', () => {
      // Given: [Preferences] button exists
      const onViewPreferences = vi.fn();

      renderWithProviders(
        <UnassignedSpeakersList
          sessions={mockUnassignedSessions}
          totalSessions={10}
          onViewPreferences={onViewPreferences}
        />
      );

      const firstCard = screen.getAllByRole('article')[0];
      const preferenceButton = within(firstCard).getByRole('button', { name: /preferences/i });

      // When: Button is clicked
      fireEvent.click(preferenceButton);

      // Then: Callback is triggered with speaker username
      expect(onViewPreferences).toHaveBeenCalledWith('john.doe');
    });
  });

  describe('Real-time Updates', () => {
    it('should_updateCount_when_sessionAssigned', () => {
      // AC12: Real-time updates as sessions are assigned
      // Given: Initially 3 unassigned sessions
      const { rerender } = renderWithProviders(
        <UnassignedSpeakersList
          sessions={mockUnassignedSessions}
          totalSessions={10}
          onViewPreferences={() => {}}
        />
      );

      expect(screen.getByText(/3.*remaining/i)).toBeInTheDocument();

      // When: One session is assigned (re-render with 2 sessions)
      const updatedSessions = mockUnassignedSessions.slice(1);

      rerender(
        <BrowserRouter>
          <I18nextProvider i18n={i18n}>
            <UnassignedSpeakersList
              sessions={updatedSessions}
              totalSessions={10}
              onViewPreferences={() => {}}
            />
          </I18nextProvider>
        </BrowserRouter>
      );

      // Then: Count updates to 2 remaining
      expect(screen.getByText(/2.*remaining/i)).toBeInTheDocument();
      expect(screen.getByText(/8.*of.*10.*assigned/i)).toBeInTheDocument();
    });

    it('should_removeSessionFromList_when_assigned', () => {
      // Given: Session is assigned
      const { rerender } = renderWithProviders(
        <UnassignedSpeakersList
          sessions={mockUnassignedSessions}
          totalSessions={10}
          onViewPreferences={() => {}}
        />
      );

      expect(screen.getByText('John Doe - Acme Corp')).toBeInTheDocument();

      // When: Session is removed from list
      const updatedSessions = mockUnassignedSessions.filter((s) => s.sessionSlug !== 'session-1');

      rerender(
        <BrowserRouter>
          <I18nextProvider i18n={i18n}>
            <UnassignedSpeakersList
              sessions={updatedSessions}
              totalSessions={10}
              onViewPreferences={() => {}}
            />
          </I18nextProvider>
        </BrowserRouter>
      );

      // Then: Session no longer appears in list
      expect(screen.queryByText('John Doe - Acme Corp')).not.toBeInTheDocument();
      expect(screen.getByText('Jane Smith - Tech Inc')).toBeInTheDocument();
    });
  });

  describe('Loading States', () => {
    it('should_showSkeletonLoader_when_loading', () => {
      // Given: Sessions are loading
      renderWithProviders(
        <UnassignedSpeakersList
          sessions={[]}
          totalSessions={0}
          onViewPreferences={() => {}}
          isLoading={true}
        />
      );

      // Then: Shows skeleton loader
      expect(screen.getByTestId('skeleton-loader')).toBeInTheDocument();
      expect(screen.getAllByTestId('skeleton-card')).toHaveLength(3); // Shows 3 skeleton cards
    });
  });
});
