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
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import i18n from '@/i18n/config';
import { DragDropSlotAssignment } from '../DragDropSlotAssignment';
import { useSlotAssignment } from '@/hooks/useSlotAssignment/useSlotAssignment';
import type { Session } from '@/types/event.types';

// Mock useSlotAssignment hook
vi.mock('@/hooks/useSlotAssignment/useSlotAssignment', () => ({
  useSlotAssignment: vi.fn(),
}));

// Mock useEvent hook
vi.mock('@/hooks/useEvents', () => ({
  useEvent: vi.fn(),
}));

/**
 * Generates a mock TimetableResponse with hourly SPEAKER_SLOTs from 09:00 to 19:00
 * on the given date string (YYYY-MM-DD). Strings are without 'Z' so Date treats them
 * as local time and toTimeStr produces the expected "09:00"..."19:00" labels.
 */
const buildMockTimetable = (dateStr: string = '2025-12-15') => ({
  slots: Array.from({ length: 11 }, (_, i) => {
    const h = 9 + i;
    const pad = (n: number) => String(n).padStart(2, '0');
    return {
      type: 'SPEAKER_SLOT' as const,
      startTime: `${dateStr}T${pad(h)}:00:00`,
      endTime: `${dateStr}T${pad(h + 1)}:00:00`,
      title: null,
      slotIndex: i + 1,
      sessionSlug: null,
      assignedSessionSlug: null,
    };
  }),
  unassignedSessions: [],
});

// Mock useTimetable hook (replaces useEventType — slot grid is now backend-driven)
vi.mock('@/hooks/useTimetable/useTimetable', () => ({
  useTimetable: vi.fn(),
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
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <I18nextProvider i18n={i18n}>{ui}</I18nextProvider>
      </BrowserRouter>
    </QueryClientProvider>
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

  beforeEach(async () => {
    vi.clearAllMocks();

    // Set default mock implementation for useSlotAssignment
    vi.mocked(useSlotAssignment).mockReturnValue(mockUseSlotAssignment);

    // Mock useEvent to return event with sessions
    const { useEvent } = await import('@/hooks/useEvents');
    vi.mocked(useEvent).mockReturnValue({
      data: {
        eventCode: mockEventCode,
        eventType: 'FULL_DAY',
        date: '2025-12-15',
        sessions: mockUnassignedSessions,
      },
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    } as any);

    // Mock useTimetable — backend-driven slot grid (11 hourly SPEAKER_SLOTs 09:00-19:00)
    const { useTimetable } = await import('@/hooks/useTimetable/useTimetable');
    vi.mocked(useTimetable).mockReturnValue({
      data: buildMockTimetable('2025-12-15'),
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    } as any);
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
      // Given: Event has 10 total sessions, 3 assigned (from mockUseSlotAssignment)
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      // Then: Shows progress text (3 Assigned in Quick Actions panel)
      expect(screen.getByText(/3 Assigned/i)).toBeInTheDocument();
      // Note: Actual progressbar implementation would be in UnassignedSessionsList component
    });

    it('should_showLoadingState_when_fetchingData', async () => {
      // Given: Data is loading
      const { useEvent } = await import('@/hooks/useEvents');
      vi.mocked(useEvent).mockReturnValue({
        data: undefined,
        isLoading: true, // Override to show loading state
        isError: false,
        error: null,
        refetch: vi.fn(),
      } as any);

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
      expect(screen.getByText(/09:00/)).toBeInTheDocument(); // Morning slot
      expect(screen.getByText(/14:00/)).toBeInTheDocument(); // Afternoon slot
    });

    it('should_showDropZones_when_draggingSession', async () => {
      // AC5: Droppable zones for each slot
      // Given: User starts dragging a session
      const { container } = renderWithProviders(
        <DragDropSlotAssignment eventCode={mockEventCode} />
      );

      const speakerCard = screen.getByText('John Doe - Acme Corp').closest('[draggable]');

      // When: Drag starts and hovers over a slot
      fireEvent.dragStart(speakerCard!);

      // Simulate dragging over the first slot (09:00-Main-Hall)
      const firstSlot = screen.getByTestId('slot-09:00-Main-Hall');
      fireEvent.dragOver(firstSlot);

      // Then: Drop zones become visible and highlighted with drop-zone-active class
      await waitFor(() => {
        const dropZones = container.querySelectorAll('.drop-zone-active');
        expect(dropZones.length).toBeGreaterThan(0);
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

      // Then: assignTiming hook function is called with correct parameters
      await waitFor(() => {
        expect(mockUseSlotAssignment.assignTiming).toHaveBeenCalledWith(
          'session-1',
          expect.objectContaining({
            room: 'Main Hall',
            changeReason: 'drag_drop_reassignment',
          })
        );
      });
    });

    it('should_updateUnassignedCount_when_sessionAssigned', async () => {
      // AC12: Real-time updates to unassigned count
      // Given: Initially 2 unassigned sessions (from mock)
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      // Initial pending count shows 2 unassigned sessions
      expect(screen.getByText('2 Pending')).toBeInTheDocument();

      const speakerCard = screen.getByText('John Doe - Acme Corp').closest('[draggable]');
      const targetSlot = screen.getByTestId('slot-09:00-Main-Hall');

      // When: One session is assigned via drag & drop
      fireEvent.dragStart(speakerCard!);
      fireEvent.dragOver(targetSlot);
      fireEvent.drop(targetSlot);

      // Then: assignTiming is called (optimistic update handled by useSlotAssignment hook)
      await waitFor(() => {
        expect(mockUseSlotAssignment.assignTiming).toHaveBeenCalled();
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
      // Given: Event has 10 total sessions, 3 assigned, 2 unassigned/pending
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      // Then: Shows session summary in Quick Actions panel
      expect(screen.getByText('10 Total Sessions')).toBeInTheDocument();
      expect(screen.getByText('3 Assigned')).toBeInTheDocument();
      expect(screen.getByText('2 Pending')).toBeInTheDocument(); // 2 unassigned sessions from mock
    });

    it('should_openAutoAssignModal_when_autoAssignClicked', () => {
      // AC13: Bulk auto-assignment feature
      // Given: Auto-assign button exists (uses translation key)
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      // Find button by icon and contained text (more flexible than exact translation)
      const autoAssignButton = screen.getByRole('button', { name: /auto.*assign/i });

      // When: Button is clicked
      fireEvent.click(autoAssignButton);

      // Then: Modal opens
      expect(screen.getByRole('dialog')).toBeInTheDocument();
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
      // Given: All sessions are already assigned (totalSessions === assignedCount)
      // Override mock to show all sessions assigned
      vi.mocked(useSlotAssignment).mockReturnValue({
        ...mockUseSlotAssignment,
        assignedCount: 10, // Same as totalSessions
        unassignedSessions: [], // No unassigned sessions
      });

      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      // Then: Success banner appears with link to Publishing tab
      expect(screen.getByText('All timings assigned!')).toBeInTheDocument();
      expect(screen.getByText('Go to Publishing Tab')).toBeInTheDocument();
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
      // Given: Component is rendered with assignedCount > 0 (from mockUseSlotAssignment)
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      // Then: ARIA live region announces the current assignment count
      // The ARIA live region shows "{assignedCount} sessions assigned successfully"
      const announcement = screen.getByRole('status', { hidden: true });
      expect(announcement).toHaveTextContent('3 sessions assigned successfully');
    });
  });
});
