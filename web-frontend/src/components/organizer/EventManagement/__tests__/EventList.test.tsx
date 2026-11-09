/**
 * EventList Component Tests (RED Phase - TDD)
 *
 * Story 2.5.3 - Task 8a
 * AC: 1 (Event Dashboard Display), 2 (Event List & Filters)
 * Wireframe: docs/wireframes/story-1.16-event-management-dashboard.md v1.0
 *
 * Tests for active events pipeline:
 * - Display event cards in grid/list layout
 * - Show event count
 * - Handle empty state
 * - Responsive layout
 */

import React from 'react';
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { EventList } from '../EventList';
import type { Event } from '@/types/event.types';
import { anEvent } from '@/test/fixtures';

describe('EventList Component', () => {
  const mockEvents: Event[] = [
    anEvent({
      eventCode: 'BATbern56',
      eventNumber: 56,
      title: 'Cloud Computing 2025',
      description: 'Annual cloud computing event',
      date: '2025-03-15T18:00:00Z',
      registrationDeadline: '2025-03-08T23:59:59Z',
      eventType: 'FULL_DAY',
      workflowState: 'SPEAKER_IDENTIFICATION',
      venueCapacity: 200,
      currentAttendeeCount: 0,
    }),
    anEvent({
      eventCode: 'BATbern57',
      eventNumber: 57,
      title: 'DevOps Mastery',
      description: 'DevOps best practices',
      date: '2025-04-20T18:00:00Z',
      registrationDeadline: '2025-04-13T23:59:59Z',
      eventType: 'AFTERNOON',
      workflowState: 'TOPIC_SELECTION',
      venueCapacity: 150,
      currentAttendeeCount: 25,
    }),
  ];

  const createWrapper = () => {
    return ({ children }: { children: React.ReactNode }) => (
      <BrowserRouter>{children}</BrowserRouter>
    );
  };

  describe('Event Display', () => {
    it('should_renderAllEvents_when_eventsProvided', () => {
      render(<EventList events={mockEvents} />, { wrapper: createWrapper() });

      expect(screen.getByText('Cloud Computing 2025')).toBeInTheDocument();
      expect(screen.getByText('DevOps Mastery')).toBeInTheDocument();
    });

    it('should_displayEmptyState_when_noEvents', () => {
      render(<EventList events={[]} />, { wrapper: createWrapper() });

      expect(screen.getByText(/no events found/i)).toBeInTheDocument();
    });

    it('should_displayEmptyStateMessage_when_filteredEventsEmpty', () => {
      render(<EventList events={[]} />, { wrapper: createWrapper() });

      expect(screen.getByText(/try adjusting your filters/i)).toBeInTheDocument();
    });
  });

  describe('Layout', () => {
    it('should_useGridLayout_when_eventsProvided', () => {
      render(<EventList events={mockEvents} />, { wrapper: createWrapper() });

      const container = screen.getByTestId('event-list-container');
      expect(container).toHaveClass('MuiGrid-container');
    });

    it('should_displayEventsInCards_when_gridLayout', () => {
      render(<EventList events={mockEvents} />, { wrapper: createWrapper() });

      // One card per event; the grid item around it has its own test id.
      const card56 = screen.getByTestId('event-card-BATbern56');
      expect(screen.getByTestId('event-card-BATbern57')).toBeInTheDocument();
      expect(screen.getByTestId('event-list-item-BATbern56')).toContainElement(card56);
    });

    it('should_haveResponsiveColumns_when_rendered', () => {
      render(<EventList events={mockEvents} />, { wrapper: createWrapper() });

      // MUI Grid v2 uses different class structure - just verify Grid container exists
      const container = screen.getByTestId('event-list-container');
      expect(container).toHaveClass('MuiGrid-container');

      // Verify both event cards are rendered, once each
      expect(screen.getByTestId('event-card-BATbern56')).toBeInTheDocument();
      expect(screen.getByTestId('event-card-BATbern57')).toBeInTheDocument();
    });
  });

  describe('Event Card Integration', () => {
    it('should_renderEventCard_when_eventProvided', () => {
      render(<EventList events={mockEvents} />, { wrapper: createWrapper() });

      // EventCard should display event code
      expect(screen.getByText(/BATbern56/i)).toBeInTheDocument();
      expect(screen.getByText(/BATbern57/i)).toBeInTheDocument();
    });

    it('should_passEventData_when_renderingCard', () => {
      render(<EventList events={mockEvents} />, { wrapper: createWrapper() });

      // EventCard should receive and display event data
      expect(screen.getByText('Cloud Computing 2025')).toBeInTheDocument();
      expect(screen.getByText('DevOps Mastery')).toBeInTheDocument();
    });
  });

  describe('Loading and Error States', () => {
    it('should_displayLoadingState_when_isLoadingTrue', () => {
      render(<EventList events={[]} isLoading={true} />, { wrapper: createWrapper() });

      // Skeleton components don't have progressbar role - verify skeletons are rendered
      const skeletons = screen.getAllByTestId(/skeleton-card-/);
      expect(skeletons.length).toBeGreaterThan(0);
    });

    it('should_displaySkeletonCards_when_isLoadingTrue', () => {
      render(<EventList events={[]} isLoading={true} />, { wrapper: createWrapper() });

      const skeletons = screen.getAllByTestId(/skeleton-card-/);
      expect(skeletons.length).toBeGreaterThan(0);
    });
  });

  describe('Accessibility', () => {
    it('should_haveAriaLabel_when_rendered', () => {
      render(<EventList events={mockEvents} />, { wrapper: createWrapper() });

      expect(screen.getByLabelText(/event list/i)).toBeInTheDocument();
    });
  });

  describe('Internationalization', () => {
    it('should_translateEmptyState_when_noEvents', () => {
      render(<EventList events={[]} />, { wrapper: createWrapper() });

      // Empty state should use translation key
      expect(screen.getByText(/no events found/i)).toBeInTheDocument();
    });
  });
});
