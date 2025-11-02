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

describe('EventList Component', () => {
  const mockEvents: Event[] = [
    {
      eventCode: 'BATbern56',
      eventNumber: 56,
      title: 'Cloud Computing 2025',
      description: 'Annual cloud computing event',
      eventDate: '2025-03-15T18:00:00Z',
      eventType: 'full_day',
      status: 'active',
      workflowState: 'speaker_research',
      registrationDeadline: '2025-03-08T23:59:59Z',
      capacity: 200,
      currentAttendeeCount: 0,
      createdAt: '2025-01-01T10:00:00Z',
      updatedAt: '2025-01-10T15:00:00Z',
      createdBy: 'john.doe',
      version: 1,
    },
    {
      eventCode: 'BATbern57',
      eventNumber: 57,
      title: 'DevOps Mastery',
      description: 'DevOps best practices',
      eventDate: '2025-04-20T18:00:00Z',
      eventType: 'afternoon',
      status: 'published',
      workflowState: 'topic_selection',
      registrationDeadline: '2025-04-13T23:59:59Z',
      capacity: 150,
      currentAttendeeCount: 25,
      createdAt: '2025-01-15T10:00:00Z',
      updatedAt: '2025-01-20T15:00:00Z',
      createdBy: 'jane.smith',
      version: 1,
    },
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

    it('should_renderEventCount_when_eventsProvided', () => {
      render(<EventList events={mockEvents} />, { wrapper: createWrapper() });

      expect(screen.getByText(/2.*events/i)).toBeInTheDocument();
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

      const cards = screen.getAllByTestId(/event-card-/);
      expect(cards).toHaveLength(2);
    });

    it('should_haveResponsiveColumns_when_rendered', () => {
      render(<EventList events={mockEvents} />, { wrapper: createWrapper() });

      // Grid should have responsive columns (xs=12, sm=6, md=4)
      const items = screen.getAllByTestId(/event-card-/);
      items.forEach((item) => {
        expect(item.parentElement).toHaveClass('MuiGrid-item');
      });
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

      expect(screen.getByRole('progressbar')).toBeInTheDocument();
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

    it('should_announceEventCount_when_screenReaderActive', () => {
      render(<EventList events={mockEvents} />, { wrapper: createWrapper() });

      expect(screen.getByLabelText(/2 events/i)).toBeInTheDocument();
    });
  });

  describe('Internationalization', () => {
    it('should_useTranslationKeys_when_renderingText', () => {
      render(<EventList events={mockEvents} />, { wrapper: createWrapper() });

      // Verify translated text appears (from events namespace)
      expect(screen.getByText(/events/i)).toBeInTheDocument();
    });

    it('should_translateEmptyState_when_noEvents', () => {
      render(<EventList events={[]} />, { wrapper: createWrapper() });

      // Empty state should use translation key
      expect(screen.getByText(/no events found/i)).toBeInTheDocument();
    });
  });
});
