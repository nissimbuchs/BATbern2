/**
 * DragDropSlotAssignment Component Tests
 *
 * Story 5.7 — original drag-and-drop slot assignment behaviour.
 * Epic 14 Phase C (14.C.5) — reworked to a 2-column in-tab layout with a top
 * action bar (no viewport lock) plus an optional `focusSpeakerId` highlight.
 *
 * Coverage:
 * - AC5: Drag-and-drop UI to drag speaker cards to time slots
 * - AC6: Visual timeline showing all slots and assignments
 * - AC7: Display speaker time preferences
 * - AC11: Highlight when slot matches speaker preference
 * - AC12: Show unassigned speakers list with real-time updates
 * - 14.C.5: 2-column layout, top action bar, no 100vh lock, focusSpeakerId
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

// Mobile detection — desktop by default so existing tests are unaffected; the
// 14.G.3 block flips it to mobile.
let mockIsMobile = false;
vi.mock('@/hooks/useBreakpoints', () => ({
  useBreakpoints: () => ({ isMobile: mockIsMobile, isTablet: false, isDesktop: !mockIsMobile }),
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

describe('DragDropSlotAssignment Component', () => {
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

  describe('Component Rendering (14.C.5 two-column in-tab layout)', () => {
    it('should_renderTwoColumnLayoutWithTopActionBar_when_initialized', () => {
      // 14.C.5: 2 columns (tray + timeline) + a top action bar above them.
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      // Then: the unassigned tray, the timeline, and the new top action bar exist.
      expect(screen.getByTestId('speaker-pool-sidebar')).toBeInTheDocument();
      expect(screen.getByTestId('session-timeline-grid')).toBeInTheDocument();
      expect(screen.getByTestId('slot-action-bar')).toBeInTheDocument();
    });

    it('should_notRenderSeparateQuickActionsPanel_when_initialized', () => {
      // 14.C.5: the right-hand 3rd column ("quick-actions-panel") is retired;
      // its actions move into the top action bar.
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      expect(screen.queryByTestId('quick-actions-panel')).not.toBeInTheDocument();
    });

    it('should_notLockToViewportHeight_when_renderedInTab', () => {
      // 14.C.5: the `height: { md: '100vh' }` viewport lock is removed so the
      // component sizes to its content when mounted inside the Speakers tab.
      const { container } = renderWithProviders(
        <DragDropSlotAssignment eventCode={mockEventCode} />
      );

      const css = Array.from(container.ownerDocument.querySelectorAll('style'))
        .map((s) => s.textContent ?? '')
        .join('\n');

      // The viewport-height lock must not survive anywhere in the injected styles.
      expect(/height:\s*100vh/.test(css)).toBe(false);
    });

    it('should_displayProgressIndicator_when_sessionsLoaded', () => {
      // AC12: Progress tracking (e.g., "3 of 10 assigned (30%)")
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      // Then: Shows progress text (3 Assigned in the top action bar)
      expect(screen.getByText(/3 Assigned/i)).toBeInTheDocument();
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
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      // Then: Displays both unassigned speakers
      expect(screen.getByText('John Doe - Acme Corp')).toBeInTheDocument();
      expect(screen.getByText('Jane Smith - Tech Inc')).toBeInTheDocument();
    });

    it('should_showDraggableIndicator_when_hoveringOverSpeakerCard', async () => {
      // AC5: Draggable speaker cards with grab handle
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

  describe('Focus speaker (14.C.5 carry speaker context)', () => {
    it('should_highlightMatchingUnassignedSession_when_focusSpeakerIdMatches', async () => {
      renderWithProviders(
        <DragDropSlotAssignment eventCode={mockEventCode} focusSpeakerId="jane.smith" />
      );

      // The matching session card carries a focus marker that the tray scrolls to.
      await waitFor(() => {
        expect(screen.getByTestId('focused-session-session-2')).toBeInTheDocument();
      });
      // The non-matching session is NOT marked.
      expect(screen.queryByTestId('focused-session-session-1')).not.toBeInTheDocument();
    });

    it('should_renderNormally_when_focusSpeakerIdHasNoMatch', () => {
      renderWithProviders(
        <DragDropSlotAssignment eventCode={mockEventCode} focusSpeakerId="nobody.here" />
      );

      // No error, no focus marker, both sessions still render.
      expect(screen.queryByTestId('focused-session-session-1')).not.toBeInTheDocument();
      expect(screen.queryByTestId('focused-session-session-2')).not.toBeInTheDocument();
      expect(screen.getByText('John Doe - Acme Corp')).toBeInTheDocument();
      expect(screen.getByText('Jane Smith - Tech Inc')).toBeInTheDocument();
    });
  });

  describe('Session Timeline Grid', () => {
    it('should_displayTimelineGrid_when_initialized', () => {
      // AC6: Visual timeline showing all slots and assignments
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      // Then: Shows timeline with time slots
      expect(screen.getByTestId('timeline-grid')).toBeInTheDocument();
      expect(screen.getByText(/09:00/)).toBeInTheDocument(); // Morning slot
      expect(screen.getByText(/14:00/)).toBeInTheDocument(); // Afternoon slot
    });

    it('should_showDropZones_when_draggingSession', async () => {
      // AC5: Droppable zones for each slot
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

  describe('Top Action Bar (14.C.5)', () => {
    it('should_displaySessionSummary_when_rendered', () => {
      // Given: Event has 10 total sessions, 3 assigned, 2 unassigned/pending
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      // Then: Shows session summary in the top action bar
      expect(screen.getByText('10 Total Sessions')).toBeInTheDocument();
      expect(screen.getByText('3 Assigned')).toBeInTheDocument();
      expect(screen.getByText('2 Pending')).toBeInTheDocument();
    });

    it('should_renderAllThreeActionButtons_when_rendered', () => {
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      expect(screen.getByTestId('generate-structural-button')).toBeInTheDocument();
      expect(screen.getByTestId('auto-assign-button')).toBeInTheDocument();
      // Clear-all button (the action-bar variant).
      expect(screen.getByRole('button', { name: /clear all/i })).toBeInTheDocument();
    });

    it('should_openAutoAssignModal_when_autoAssignClicked', () => {
      // AC13: Bulk auto-assignment feature
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      const autoAssignButton = screen.getByRole('button', { name: /auto.*assign/i });
      fireEvent.click(autoAssignButton);

      expect(screen.getByRole('dialog')).toBeInTheDocument();
    });

    it('should_clearAllAssignments_when_clearButtonClicked', () => {
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      const clearButton = screen.getByRole('button', { name: /clear all/i });
      fireEvent.click(clearButton);

      expect(screen.getByRole('dialog')).toBeInTheDocument();
      expect(screen.getByText(/are you sure/i)).toBeInTheDocument();
    });

    it('should_labelActionBarForScreenReaders_when_rendered', () => {
      // NFR7: the top action bar is a labelled toolbar region.
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      const bar = screen.getByTestId('slot-action-bar');
      expect(bar).toHaveAttribute('aria-label');
    });
  });

  describe('Success States', () => {
    it('should_showSuccessBanner_when_allSessionsAssigned', async () => {
      // Given: All sessions are already assigned (totalSessions === assignedCount)
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

  describe('Responsive timeline (organizer mobile)', () => {
    /**
     * The timeline grid is now FLUID on every viewport — the fixed
     * minWidth ({ xs: 560, md: 800 }) was removed (round 3) so the grid
     * fills the available width with no horizontal scroll forced by a min
     * width. jsdom never evaluates media queries, so we inspect the injected
     * emotion stylesheet for the element's css-* class.
     */
    const cssForClass = (className: string): string => {
      let combined = '';
      document.querySelectorAll('style').forEach((styleEl) => {
        const css = styleEl.textContent ?? '';
        if (css.includes(`.${className}`)) combined += css + '\n';
      });
      return combined;
    };

    const emotionClass = (el: HTMLElement): string => {
      const cssClass = Array.from(el.classList).find((c) => c.startsWith('css-'));
      return cssClass ?? '';
    };

    it('should_notSetFixedMinWidthOnTimelineGrid_when_rendered', () => {
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      // The inner grid wrapper must no longer carry any fixed min-width rule.
      const scroller = screen.getByTestId('timeline-grid');
      const inner = scroller.firstElementChild as HTMLElement;
      expect(inner).toBeTruthy();

      // No emotion class at all is the cleanest signal of "no sx min-width".
      const cls = emotionClass(inner);
      const css = cls ? cssForClass(cls) : '';

      // Neither the old xs (560px) nor md (800px) min-width may survive.
      expect(/min-width:\s*560px/.test(css)).toBe(false);
      expect(/min-width:\s*800px/.test(css)).toBe(false);
    });

    it('should_wrapSessionTitle_whiteSpaceNormal_atXs', async () => {
      // Provide an event session assigned to the 09:00 Main Hall slot so the
      // session-title Typography actually renders in a timeline cell.
      const assignedSession: Session = {
        sessionSlug: 'assigned-session',
        eventCode: mockEventCode,
        title: 'A Very Long Session Title That Must Wrap On Phones',
        startTime: '2025-12-15T09:00:00',
        endTime: '2025-12-15T10:00:00',
        room: 'Main Hall',
        speakers: [{ username: 'john.doe', displayName: 'John Doe', companyName: 'Acme Corp' }],
      };
      const { useEvent } = await import('@/hooks/useEvents');
      vi.mocked(useEvent).mockReturnValue({
        data: {
          eventCode: mockEventCode,
          eventType: 'FULL_DAY',
          date: '2025-12-15',
          sessions: [...mockUnassignedSessions, assignedSession],
        },
        isLoading: false,
        isError: false,
        error: null,
        refetch: vi.fn(),
      } as any);

      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      const titleEl = screen.getByText(
        'A Very Long Session Title That Must Wrap On Phones'
      ) as HTMLElement;
      const css = cssForClass(emotionClass(titleEl));

      // xs base (min-width:0px) -> white-space:normal (wraps on phones)
      expect(
        new RegExp(
          `@media\\s*\\(min-width:\\s*0px\\)\\s*\\{[^}]*white-space:\\s*normal[^}]*\\}`
        ).test(css)
      ).toBe(true);
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
      const announcement = screen.getByRole('status', { hidden: true });
      expect(announcement).toHaveTextContent('3 sessions assigned successfully');
    });
  });

  describe('Mobile tap-to-assign (14.G.3)', () => {
    beforeEach(() => {
      mockIsMobile = true;
    });
    afterEach(() => {
      mockIsMobile = false;
    });

    it('arms empty slots after a tray session is tapped, then assigns on slot tap', async () => {
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);

      // No slot is armed before a session is picked up.
      expect(document.querySelector('[data-armed="true"]')).toBeNull();

      // Tap a tray session → empty slots become armed tap targets.
      fireEvent.click(screen.getByTestId('tray-session-session-1'));
      await waitFor(() => {
        expect(document.querySelector('[data-armed="true"]')).toBeTruthy();
      });

      // Tap an armed slot → assigns the selected session via the shared path.
      const armed = document.querySelector('[data-armed="true"]') as HTMLElement;
      fireEvent.click(armed);
      await waitFor(() => {
        expect(mockUseSlotAssignment.assignTiming).toHaveBeenCalledWith(
          'session-1',
          expect.objectContaining({ room: 'Main Hall' })
        );
      });
    });

    it('does not arm slots on desktop (drag-drop unchanged)', () => {
      mockIsMobile = false;
      renderWithProviders(<DragDropSlotAssignment eventCode={mockEventCode} />);
      fireEvent.click(screen.getByTestId('tray-session-session-1'));
      expect(document.querySelector('[data-armed="true"]')).toBeNull();
    });
  });
});
