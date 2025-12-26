/**
 * DragDropSlotAssignment Component Tests (Story 5.7 - Task 4a RED Phase)
 *
 * Tests for drag-and-drop slot assignment interface
 * Following TDD: These tests MUST fail until implementation (Task 4b)
 *
 * Coverage:
 * - AC5: Drag-and-drop UI to drag speaker cards to time slots
 * - AC6: Visual timeline showing all slots and assignments
 * - AC7: Display speaker time preferences
 * - AC11: Highlight when slot matches speaker preference
 * - AC12: Show unassigned speakers list with real-time updates
 */

import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { BrowserRouter } from 'react-router-dom';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import { DragDropSlotAssignment } from '../DragDropSlotAssignment';
import { useSlotAssignment } from '@/hooks/useSlotAssignment/useSlotAssignment';
import type { Session } from '@/types/event.types';

// Mock useSlotAssignment hook
vi.mock('@/hooks/useSlotAssignment/useSlotAssignment', () => ({
  useSlotAssignment: vi.fn(),
}));

// Mock DnD library
vi.mock('@dnd-kit/core', () => ({
  DndContext: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  useDraggable: () => ({
    attributes: {},
    listeners: {},
    setNodeRef: () => {},
    transform: null,
  }),
  useDroppable: () => ({
    setNodeRef: () => {},
    isOver: false,
  }),
}));

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
];

const renderWithProviders = (ui: React.ReactElement) => {
  return render(
    <BrowserRouter>
      <I18nextProvider i18n={i18n}>{ui}</I18nextProvider>
    </BrowserRouter>
  );
};

describe('DragDropSlotAssignment Component (Story 5.7 - Task 4a RED Phase)', () => {
  const mockEventCode = 'BATbern142';

  // Default mock return value for useSlotAssignment
  const mockUseSlotAssignment = {
    unassignedSessions: mockUnassignedSessions,
    isLoading: false,
    error: null,
    conflict: null,
    conflictAnalysis: null,
    assignedCount: 3,
    totalSessions: 10,
    assignTiming: vi.fn().mockResolvedValue(undefined),
    bulkAssignTiming: vi.fn().mockResolvedValue(undefined),
    detectConflicts: vi.fn().mockResolvedValue(undefined),
    clearConflict: vi.fn(),
    refreshSessions: vi.fn().mockResolvedValue(undefined),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    // Set default mock implementation
    vi.mocked(useSlotAssignment).mockReturnValue(mockUseSlotAssignment);
  });

  describe('Component Rendering', () => {
    it('should_renderThreeColumnLayout_when_initialized', () => {
      // AC5-AC6: Three-column layout (wireframe story-5.7-slot-assignment-page.md)
      // Given: Component is initialized
      // When: Component renders
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      // Then: Shows three main sections
      expect(screen.getByTestId('speaker-pool-sidebar')).toBeInTheDocument();
      expect(screen.getByTestId('session-timeline-grid')).toBeInTheDocument();
      expect(screen.getByTestId('quick-actions-panel')).toBeInTheDocument();
    });

    it('should_displayProgressIndicator_when_sessionsLoaded', () => {
      // AC12: Progress tracking (e.g., "3 of 10 assigned (30%)")
      // Given: Event has 10 total sessions, 3 assigned
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      // Then: Shows progress indicator
      expect(screen.getByText(/assigned/i)).toBeInTheDocument();
      expect(screen.getByRole('progressbar')).toBeInTheDocument();
    });

    it('should_showLoadingState_when_fetchingData', () => {
      // Given: Data is loading
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      // Then: Shows loading skeleton
      expect(screen.getByTestId('loading-skeleton')).toBeInTheDocument();
    });
  });

  describe('Unassigned Speakers List', () => {
    it('should_displayUnassignedSessions_when_loaded', () => {
      // AC12: Show unassigned speakers list
      // Given: 2 unassigned sessions exist
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      // Then: Displays both unassigned speakers
      expect(screen.getByText('John Doe - Acme Corp')).toBeInTheDocument();
      expect(screen.getByText('Jane Smith - Tech Inc')).toBeInTheDocument();
    });

    it('should_showDraggableIndicator_when_hoveringOverSpeakerCard', async () => {
      // AC5: Draggable speaker cards with grab handle
      // Given: User hovers over speaker card
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      const speakerCard = screen.getByText('John Doe - Acme Corp').closest('[draggable]');

      // When: Mouse enters speaker card
      fireEvent.mouseEnter(speakerCard!);

      // Then: Shows grab handle cursor
      await waitFor(() => {
        expect(speakerCard).toHaveStyle({ cursor: 'grab' });
      });
    });

    it('should_filterUnassignedSessions_when_filterApplied', () => {
      // Given: Filter options [All] [Assigned] [Unassigned]
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      // When: User clicks [Unassigned] filter
      const unassignedFilter = screen.getByRole('button', { name: /unassigned/i });
      fireEvent.click(unassignedFilter);

      // Then: Shows only unassigned sessions
      expect(screen.getByText('John Doe - Acme Corp')).toBeInTheDocument();
      expect(screen.getByText('Jane Smith - Tech Inc')).toBeInTheDocument();
    });
  });

  describe('Session Timeline Grid', () => {
    it('should_displayTimelineGrid_when_initialized', () => {
      // AC6: Visual timeline showing all slots and assignments
      // Given: Component is initialized
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      // Then: Shows timeline with time slots
      expect(screen.getByTestId('timeline-grid')).toBeInTheDocument();
      expect(screen.getByText(/08:00/)).toBeInTheDocument(); // Morning slot
      expect(screen.getByText(/14:00/)).toBeInTheDocument(); // Afternoon slot
    });

    it('should_showDropZones_when_draggingSession', async () => {
      // AC5: Droppable zones for each slot
      // Given: User starts dragging a session
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      const speakerCard = screen.getByText('John Doe - Acme Corp').closest('[draggable]');

      // When: Drag starts
      fireEvent.dragStart(speakerCard!);

      // Then: Drop zones become visible and highlighted
      await waitFor(() => {
        const dropZones = screen.getAllByTestId('drop-zone');
        expect(dropZones.length).toBeGreaterThan(0);
        dropZones.forEach((zone) => {
          expect(zone).toHaveClass('drop-zone-active');
        });
      });
    });

    it('should_highlightPreferenceMatch_when_draggingOverMatchingSlot', async () => {
      // AC11: Highlight when slot matches speaker preference
      // Given: Speaker prefers morning slots (09:00-12:00)
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      const speakerCard = screen.getByText('John Doe - Acme Corp').closest('[draggable]');
      fireEvent.dragStart(speakerCard!);

      // When: Drag over morning slot (matches preference)
      const morningSlot = screen.getByTestId('slot-09:00-Main-Hall');
      fireEvent.dragOver(morningSlot);

      // Then: Slot highlights green (80-100% match)
      await waitFor(() => {
        expect(morningSlot).toHaveClass('preference-match-high');
        expect(screen.getByText(/90% match/)).toBeInTheDocument();
      });
    });

    it('should_showYellowHighlight_when_partialPreferenceMatch', async () => {
      // AC11: Color-coded highlights (yellow for 50-79% match)
      // Given: Speaker slightly prefers morning but slot is early afternoon
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      const speakerCard = screen.getByText('John Doe - Acme Corp').closest('[draggable]');
      fireEvent.dragStart(speakerCard!);

      // When: Drag over early afternoon slot (partial match)
      const afternoonSlot = screen.getByTestId('slot-13:00-Main-Hall');
      fireEvent.dragOver(afternoonSlot);

      // Then: Slot highlights yellow (50-79% match)
      await waitFor(() => {
        expect(afternoonSlot).toHaveClass('preference-match-medium');
        expect(screen.getByText(/65% match/)).toBeInTheDocument();
      });
    });

    it('should_showRedHighlight_when_poorPreferenceMatch', async () => {
      // AC11: Color-coded highlights (red for <50% match)
      // Given: Speaker prefers morning but slot is late evening
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      const speakerCard = screen.getByText('John Doe - Acme Corp').closest('[draggable]');
      fireEvent.dragStart(speakerCard!);

      // When: Drag over evening slot (poor match)
      const eveningSlot = screen.getByTestId('slot-19:00-Main-Hall');
      fireEvent.dragOver(eveningSlot);

      // Then: Slot highlights red (<50% match)
      await waitFor(() => {
        expect(eveningSlot).toHaveClass('preference-match-low');
        expect(screen.getByText(/20% match/)).toBeInTheDocument();
      });
    });
  });

  describe('Drag and Drop Interaction', () => {
    it('should_assignTiming_when_sessionDroppedOnSlot', async () => {
      // AC5: Complete drag-and-drop assignment workflow
      // Given: User drags session to empty slot
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      const speakerCard = screen.getByText('John Doe - Acme Corp').closest('[draggable]');
      const targetSlot = screen.getByTestId('slot-09:00-Main-Hall');

      // When: Session is dropped on slot
      fireEvent.dragStart(speakerCard!);
      fireEvent.dragOver(targetSlot);
      fireEvent.drop(targetSlot);

      // Then: Session is assigned to slot
      await waitFor(() => {
        expect(screen.getByTestId('assigned-session-session-1')).toBeInTheDocument();
        expect(screen.getByText('John Doe - Acme Corp')).toBeInTheDocument();
      });
    });

    it('should_updateUnassignedCount_when_sessionAssigned', async () => {
      // AC12: Real-time updates to unassigned count
      // Given: Initially 2 unassigned sessions
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      expect(screen.getByText(/2.*unassigned/i)).toBeInTheDocument();

      const speakerCard = screen.getByText('John Doe - Acme Corp').closest('[draggable]');
      const targetSlot = screen.getByTestId('slot-09:00-Main-Hall');

      // When: One session is assigned
      fireEvent.dragStart(speakerCard!);
      fireEvent.drop(targetSlot);

      // Then: Unassigned count decreases to 1
      await waitFor(() => {
        expect(screen.getByText(/1.*unassigned/i)).toBeInTheDocument();
      });
    });

    it('should_rollbackOnError_when_assignmentFails', async () => {
      // Given: Optimistic UI update is applied
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      const speakerCard = screen.getByText('John Doe - Acme Corp').closest('[draggable]');
      const targetSlot = screen.getByTestId('slot-09:00-Main-Hall');

      // When: Assignment fails (API error)
      fireEvent.dragStart(speakerCard!);
      fireEvent.drop(targetSlot);

      // Then: Session returns to unassigned list
      await waitFor(() => {
        expect(screen.getByText('John Doe - Acme Corp')).toBeInTheDocument();
        expect(screen.getByTestId('speaker-pool-sidebar')).toContainElement(
          screen.getByText('John Doe - Acme Corp')
        );
      });
    });
  });

  describe('Quick Actions Panel', () => {
    it('should_displaySessionSummary_when_rendered', () => {
      // Given: Event has 10 total sessions, 3 assigned, 7 pending
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      // Then: Shows session summary
      expect(screen.getByText(/10.*total/i)).toBeInTheDocument();
      expect(screen.getByText(/3.*assigned/i)).toBeInTheDocument();
      expect(screen.getByText(/7.*pending/i)).toBeInTheDocument();
    });

    it('should_openAutoAssignModal_when_autoAssignClicked', () => {
      // AC13: Bulk auto-assignment feature
      // Given: [Auto-Assign All] button exists
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      const autoAssignButton = screen.getByRole('button', { name: /auto-assign all/i });

      // When: Button is clicked
      fireEvent.click(autoAssignButton);

      // Then: Modal opens
      expect(screen.getByRole('dialog')).toBeInTheDocument();
      expect(screen.getByText(/select algorithm/i)).toBeInTheDocument();
    });

    it('should_clearAllAssignments_when_clearButtonClicked', () => {
      // Given: Some sessions are assigned
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      const clearButton = screen.getByRole('button', { name: /clear all/i });

      // When: [Clear All Assignments] is clicked
      fireEvent.click(clearButton);

      // Then: Confirmation modal appears
      expect(screen.getByRole('dialog')).toBeInTheDocument();
      expect(screen.getByText(/are you sure/i)).toBeInTheDocument();
    });
  });

  describe('Success States', () => {
    it('should_showSuccessBanner_when_allSessionsAssigned', async () => {
      // Given: All sessions have been assigned
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      // Simulate assigning all sessions
      const speakerCards = screen.getAllByRole('article');
      const slots = screen.getAllByTestId(/slot-/);

      for (let i = 0; i < speakerCards.length; i++) {
        fireEvent.dragStart(speakerCards[i]);
        fireEvent.drop(slots[i]);
      }

      // Then: Success banner appears with link to Publishing tab
      await waitFor(() => {
        expect(screen.getByText(/all timings assigned/i)).toBeInTheDocument();
        expect(screen.getByRole('link', { name: /go to publishing tab/i })).toBeInTheDocument();
      });
    });
  });

  describe('Accessibility', () => {
    it('should_supportKeyboardNavigation_when_tabPressed', () => {
      // Given: Component supports keyboard navigation
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      const speakerCard = screen.getByText('John Doe - Acme Corp').closest('[role="article"]');

      // When: User tabs to speaker card
      speakerCard?.focus();

      // Then: Card is focusable and has aria-label
      expect(speakerCard).toHaveAttribute('aria-label');
      expect(speakerCard).toHaveAttribute('tabindex', '0');
    });

    it('should_announceAssignment_when_screenReaderEnabled', async () => {
      // Given: Screen reader is active
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      const speakerCard = screen.getByText('John Doe - Acme Corp').closest('[draggable]');
      const targetSlot = screen.getByTestId('slot-09:00-Main-Hall');

      // When: Session is assigned
      fireEvent.dragStart(speakerCard!);
      fireEvent.drop(targetSlot);

      // Then: ARIA live region announces assignment
      await waitFor(() => {
        const announcement = screen.getByRole('status', { hidden: true });
        expect(announcement).toHaveTextContent(/assigned successfully/i);
      });
    });
  });
});
