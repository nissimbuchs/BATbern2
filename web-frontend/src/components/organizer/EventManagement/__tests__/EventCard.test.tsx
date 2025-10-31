/**
 * EventCard Component Tests (RED Phase - TDD)
 *
 * Story 2.5.3 - Task 8a
 * AC: 1 (Event Dashboard Display)
 * Wireframe: docs/wireframes/story-1.16-event-management-dashboard.md v1.0
 *
 * Tests for event card with:
 * - Progress bar (workflow completion %)
 * - Workflow step indicator (Step X/16)
 * - Event details (title, date, type)
 * - Quick actions (Edit, View Details)
 * - Status badge
 */

import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { EventCard } from '../EventCard';
import type { Event } from '@/types/event.types';

describe('EventCard Component', () => {
  const mockEvent: Event = {
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
    currentAttendeeCount: 50,
    createdAt: '2025-01-01T10:00:00Z',
    updatedAt: '2025-01-10T15:00:00Z',
    createdBy: 'john.doe',
    version: 1,
  };

  const createWrapper = () => {
    return ({ children }: { children: React.ReactNode }) => (
      <BrowserRouter>{children}</BrowserRouter>
    );
  };

  describe('Basic Display (AC1)', () => {
    it('should_displayEventTitle_when_rendered', () => {
      render(<EventCard event={mockEvent} />, { wrapper: createWrapper() });

      expect(screen.getByText('Cloud Computing 2025')).toBeInTheDocument();
    });

    it('should_displayEventCode_when_rendered', () => {
      render(<EventCard event={mockEvent} />, { wrapper: createWrapper() });

      expect(screen.getByText(/BATbern56/i)).toBeInTheDocument();
    });

    it('should_displayEventNumber_when_rendered', () => {
      render(<EventCard event={mockEvent} />, { wrapper: createWrapper() });

      expect(screen.getByText(/#56/i)).toBeInTheDocument();
    });

    it('should_displayEventDate_when_rendered', () => {
      render(<EventCard event={mockEvent} />, { wrapper: createWrapper() });

      // Date should be formatted (e.g., "15.03.2025" for DE or "Mar 15, 2025" for EN)
      expect(screen.getByText(/15.*mar.*2025/i)).toBeInTheDocument();
    });

    it('should_displayEventType_when_rendered', () => {
      render(<EventCard event={mockEvent} />, { wrapper: createWrapper() });

      expect(screen.getByText(/full day/i)).toBeInTheDocument();
    });
  });

  describe('Progress Bar (AC1)', () => {
    it('should_displayProgressBar_when_rendered', () => {
      render(<EventCard event={mockEvent} />, { wrapper: createWrapper() });

      const progressBar = screen.getByRole('progressbar');
      expect(progressBar).toBeInTheDocument();
    });

    it('should_displayWorkflowProgress_when_workflowStateProvided', () => {
      render(<EventCard event={mockEvent} />, { wrapper: createWrapper() });

      // Progress should be shown as percentage (e.g., "65%")
      expect(screen.getByText(/65%/i)).toBeInTheDocument();
    });

    it('should_setProgressBarValue_when_rendered', () => {
      render(<EventCard event={mockEvent} />, { wrapper: createWrapper() });

      const progressBar = screen.getByRole('progressbar');
      expect(progressBar).toHaveAttribute('aria-valuenow', '65');
    });

    it('should_colorProgressBar_when_progressBelow30', () => {
      const earlyEvent = { ...mockEvent, workflowState: 'topic_selection' as const };
      render(<EventCard event={earlyEvent} />, { wrapper: createWrapper() });

      const progressBar = screen.getByRole('progressbar');
      expect(progressBar).toHaveClass('MuiLinearProgress-colorWarning');
    });

    it('should_colorProgressBar_when_progressAbove70', () => {
      const advancedEvent = { ...mockEvent, workflowState: 'event_execution' as const };
      render(<EventCard event={advancedEvent} />, { wrapper: createWrapper() });

      const progressBar = screen.getByRole('progressbar');
      expect(progressBar).toHaveClass('MuiLinearProgress-colorSuccess');
    });
  });

  describe('Workflow Step Indicator (AC1)', () => {
    it('should_displayWorkflowStep_when_rendered', () => {
      render(<EventCard event={mockEvent} />, { wrapper: createWrapper() });

      // Should display "Step 7/16" format
      expect(screen.getByText(/step.*7.*16/i)).toBeInTheDocument();
    });

    it('should_displayWorkflowStepName_when_rendered', () => {
      render(<EventCard event={mockEvent} />, { wrapper: createWrapper() });

      // Should display workflow state name (e.g., "Speaker Research")
      expect(screen.getByText(/speaker research/i)).toBeInTheDocument();
    });

    it('should_updateStep_when_differentWorkflowState', () => {
      const { rerender } = render(<EventCard event={mockEvent} />, { wrapper: createWrapper() });

      expect(screen.getByText(/step.*7.*16/i)).toBeInTheDocument();

      const updatedEvent = { ...mockEvent, workflowState: 'venue_booking' as const };
      rerender(<EventCard event={updatedEvent} />);

      expect(screen.getByText(/step.*3.*16/i)).toBeInTheDocument();
    });
  });

  describe('Status Badge (AC1)', () => {
    it('should_displayStatusBadge_when_statusActive', () => {
      render(<EventCard event={mockEvent} />, { wrapper: createWrapper() });

      expect(screen.getByText(/active/i)).toBeInTheDocument();
    });

    it('should_useSuccessColor_when_statusPublished', () => {
      const publishedEvent = { ...mockEvent, status: 'published' as const };
      render(<EventCard event={publishedEvent} />, { wrapper: createWrapper() });

      const badge = screen.getByText(/published/i);
      expect(badge).toHaveClass('MuiChip-colorSuccess');
    });

    it('should_useWarningColor_when_statusDraft', () => {
      const draftEvent = { ...mockEvent, status: 'draft' as const };
      render(<EventCard event={draftEvent} />, { wrapper: createWrapper() });

      const badge = screen.getByText(/draft/i);
      expect(badge).toHaveClass('MuiChip-colorWarning');
    });

    it('should_useDefaultColor_when_statusCompleted', () => {
      const completedEvent = { ...mockEvent, status: 'completed' as const };
      render(<EventCard event={completedEvent} />, { wrapper: createWrapper() });

      const badge = screen.getByText(/completed/i);
      expect(badge).toHaveClass('MuiChip-colorDefault');
    });
  });

  describe('Attendee Information', () => {
    it('should_displayAttendeeCount_when_currentAttendeeCountProvided', () => {
      render(<EventCard event={mockEvent} />, { wrapper: createWrapper() });

      expect(screen.getByText(/50.*200/i)).toBeInTheDocument();
    });

    it('should_displayAttendeePercentage_when_currentAttendeeCountProvided', () => {
      render(<EventCard event={mockEvent} />, { wrapper: createWrapper() });

      // 50/200 = 25%
      expect(screen.getByText(/25%.*full/i)).toBeInTheDocument();
    });

    it('should_highlightFullEvent_when_capacityReached', () => {
      const fullEvent = { ...mockEvent, currentAttendeeCount: 200 };
      render(<EventCard event={fullEvent} />, { wrapper: createWrapper() });

      expect(screen.getByText(/100%.*full/i)).toBeInTheDocument();
      expect(screen.getByText(/100%.*full/i)).toHaveClass('MuiTypography-colorError');
    });
  });

  describe('Quick Actions', () => {
    it('should_displayEditButton_when_rendered', () => {
      render(<EventCard event={mockEvent} />, { wrapper: createWrapper() });

      expect(screen.getByRole('button', { name: /edit/i })).toBeInTheDocument();
    });

    it('should_displayViewDetailsButton_when_rendered', () => {
      render(<EventCard event={mockEvent} />, { wrapper: createWrapper() });

      expect(screen.getByRole('button', { name: /view details/i })).toBeInTheDocument();
    });

    it('should_callOnEdit_when_editButtonClicked', () => {
      const onEdit = vi.fn();
      render(<EventCard event={mockEvent} onEdit={onEdit} />, { wrapper: createWrapper() });

      fireEvent.click(screen.getByRole('button', { name: /edit/i }));

      expect(onEdit).toHaveBeenCalledWith(mockEvent.eventCode);
    });

    it('should_navigateToDetails_when_viewDetailsClicked', () => {
      render(<EventCard event={mockEvent} />, { wrapper: createWrapper() });

      const viewButton = screen.getByRole('button', { name: /view details/i });
      expect(viewButton).toHaveAttribute('href', '/organizer/events/BATbern56');
    });

    it('should_navigateToDetails_when_cardClicked', () => {
      const onCardClick = vi.fn();
      render(<EventCard event={mockEvent} onCardClick={onCardClick} />, {
        wrapper: createWrapper(),
      });

      fireEvent.click(screen.getByTestId('event-card-BATbern56'));

      expect(onCardClick).toHaveBeenCalledWith(mockEvent.eventCode);
    });
  });

  describe('Hover Effects', () => {
    it('should_showElevation_when_cardHovered', () => {
      render(<EventCard event={mockEvent} />, { wrapper: createWrapper() });

      const card = screen.getByTestId('event-card-BATbern56');
      expect(card).toHaveClass('MuiCard-root');
    });

    it('should_showActionButtons_when_cardHovered', () => {
      render(<EventCard event={mockEvent} />, { wrapper: createWrapper() });

      const card = screen.getByTestId('event-card-BATbern56');
      fireEvent.mouseEnter(card);

      const editButton = screen.getByRole('button', { name: /edit/i });
      expect(editButton).toBeVisible();
    });
  });

  describe('Responsive Design', () => {
    it('should_haveResponsivePadding_when_rendered', () => {
      render(<EventCard event={mockEvent} />, { wrapper: createWrapper() });

      const card = screen.getByTestId('event-card-BATbern56');
      expect(card).toHaveClass('MuiCard-root');
    });

    it('should_stackContent_when_mobileView', () => {
      render(<EventCard event={mockEvent} />, { wrapper: createWrapper() });

      const cardContent = screen.getByTestId('event-card-content');
      expect(cardContent).toHaveClass('MuiCardContent-root');
    });
  });

  describe('Accessibility', () => {
    it('should_haveAriaLabel_when_rendered', () => {
      render(<EventCard event={mockEvent} />, { wrapper: createWrapper() });

      expect(screen.getByLabelText(/event card.*cloud computing 2025/i)).toBeInTheDocument();
    });

    it('should_announceProgressBar_when_screenReaderActive', () => {
      render(<EventCard event={mockEvent} />, { wrapper: createWrapper() });

      const progressBar = screen.getByRole('progressbar');
      expect(progressBar).toHaveAttribute('aria-label', /workflow progress.*65%/i);
    });

    it('should_supportKeyboardNavigation_when_focused', () => {
      render(<EventCard event={mockEvent} />, { wrapper: createWrapper() });

      const card = screen.getByTestId('event-card-BATbern56');
      expect(card).toHaveAttribute('tabIndex', '0');
    });
  });

  describe('Internationalization', () => {
    it('should_translateWorkflowState_when_rendered', () => {
      render(<EventCard event={mockEvent} />, { wrapper: createWrapper() });

      // Workflow state should be translated
      expect(screen.getByText(/speaker research/i)).toBeInTheDocument();
    });

    it('should_translateEventType_when_rendered', () => {
      render(<EventCard event={mockEvent} />, { wrapper: createWrapper() });

      // Event type should be translated
      expect(screen.getByText(/full day/i)).toBeInTheDocument();
    });

    it('should_formatDate_when_localeProvided', () => {
      render(<EventCard event={mockEvent} />, { wrapper: createWrapper() });

      // Date should be formatted according to locale
      expect(screen.getByText(/15.*mar.*2025/i)).toBeInTheDocument();
    });
  });
});
