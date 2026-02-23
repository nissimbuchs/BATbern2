import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PublishingTimeline } from './PublishingTimeline';

describe('PublishingTimeline', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Phase Visualization', () => {
    it('should render all publishing phases', () => {
      render(
        <PublishingTimeline
          eventCode="BATbern142"
          currentPhase="topic"
          publishedPhases={['topic']}
          eventDate="2025-05-15"
        />
      );

      expect(screen.getByText(/topic/i)).toBeInTheDocument();
      expect(screen.getByText(/speakers/i)).toBeInTheDocument();
      expect(screen.getByText(/agenda/i)).toBeInTheDocument();
      expect(screen.getByText(/updates/i)).toBeInTheDocument();
    });

    it('should highlight current phase', () => {
      render(
        <PublishingTimeline
          eventCode="BATbern142"
          currentPhase="speakers"
          publishedPhases={['topic']}
          eventDate="2025-05-15"
        />
      );

      const speakersPhase = screen.getByTestId('timeline-phase-speakers');
      expect(speakersPhase).toHaveClass(/current/i);
    });

    it('should mark published phases as complete', () => {
      render(
        <PublishingTimeline
          eventCode="BATbern142"
          currentPhase="agenda"
          publishedPhases={['topic', 'speakers']}
          eventDate="2025-05-15"
        />
      );

      const topicPhase = screen.getByTestId('timeline-phase-topic');
      const speakersPhase = screen.getByTestId('timeline-phase-speakers');

      expect(topicPhase).toHaveClass(/complete/i);
      expect(speakersPhase).toHaveClass(/complete/i);
    });

    it('should mark unpublished phases as pending', () => {
      render(
        <PublishingTimeline
          eventCode="BATbern142"
          currentPhase="topic"
          publishedPhases={['topic']}
          eventDate="2025-05-15"
        />
      );

      const speakersPhase = screen.getByTestId('timeline-phase-speakers');
      const agendaPhase = screen.getByTestId('timeline-phase-agenda');

      expect(speakersPhase).toHaveClass(/pending/i);
      expect(agendaPhase).toHaveClass(/pending/i);
    });
  });

  describe('Milestone Markers', () => {
    it('should show milestone date for Topic phase', () => {
      render(
        <PublishingTimeline
          eventCode="BATbern142"
          currentPhase="speakers"
          publishedPhases={['topic']}
          eventDate="2025-05-15"
          publishedDates={{ topic: '2025-01-15T10:00:00Z' }}
        />
      );

      expect(screen.getByText(/published on/i)).toBeInTheDocument();
      expect(screen.getByText(/jan 15, 2025/i)).toBeInTheDocument();
    });

    it('should show scheduled date for Speakers phase (1 month before event)', () => {
      render(
        <PublishingTimeline
          eventCode="BATbern142"
          currentPhase="topic"
          publishedPhases={['topic']}
          eventDate="2025-05-15"
          scheduledDates={{ speakers: '2025-04-15T08:00:00Z' }}
        />
      );

      expect(screen.getByText(/scheduled:/i)).toBeInTheDocument();
      expect(screen.getByText(/apr 15, 2025/i)).toBeInTheDocument();
    });

    it('should show scheduled date for Agenda phase (2 weeks before event)', () => {
      render(
        <PublishingTimeline
          eventCode="BATbern142"
          currentPhase="speakers"
          publishedPhases={['topic', 'speakers']}
          eventDate="2025-05-15"
          scheduledDates={{ agenda: '2025-05-01T08:00:00Z' }}
        />
      );

      expect(screen.getByText(/scheduled:/i)).toBeInTheDocument();
      expect(screen.getByText(/may 1, 2025/i)).toBeInTheDocument();
    });

    it('should show event date for final milestone', () => {
      render(
        <PublishingTimeline
          eventCode="BATbern142"
          currentPhase="agenda"
          publishedPhases={['topic', 'speakers', 'agenda']}
          eventDate="2025-05-15"
        />
      );

      // Component shows the event date directly in the Updates phase milestone
      expect(screen.getByText(/5\/15\/2025/i)).toBeInTheDocument();
    });
  });

  describe('Progress Line', () => {
    it('should render progress line connecting phases', () => {
      render(
        <PublishingTimeline
          eventCode="BATbern142"
          currentPhase="speakers"
          publishedPhases={['topic', 'speakers']}
          eventDate="2025-05-15"
        />
      );

      expect(screen.getByTestId('progress-line')).toBeInTheDocument();
    });

    it('should show progress line at 25% when topic phase complete', () => {
      render(
        <PublishingTimeline
          eventCode="BATbern142"
          currentPhase="topic"
          publishedPhases={['topic']}
          eventDate="2025-05-15"
        />
      );

      const progressFill = screen.getByTestId('progress-fill');
      expect(progressFill).toHaveStyle({ width: '25%' });
    });

    it('should show progress line at 50% when speakers phase complete', () => {
      render(
        <PublishingTimeline
          eventCode="BATbern142"
          currentPhase="speakers"
          publishedPhases={['topic', 'speakers']}
          eventDate="2025-05-15"
        />
      );

      const progressFill = screen.getByTestId('progress-fill');
      expect(progressFill).toHaveStyle({ width: '50%' });
    });

    it('should show progress line at 75% when agenda phase complete', () => {
      render(
        <PublishingTimeline
          eventCode="BATbern142"
          currentPhase="agenda"
          publishedPhases={['topic', 'speakers', 'agenda']}
          eventDate="2025-05-15"
        />
      );

      const progressFill = screen.getByTestId('progress-fill');
      expect(progressFill).toHaveStyle({ width: '75%' });
    });

    it('should show progress line at 100% when all phases complete', () => {
      render(
        <PublishingTimeline
          eventCode="BATbern142"
          currentPhase="updates"
          publishedPhases={['topic', 'speakers', 'agenda', 'updates']}
          eventDate="2025-05-15"
        />
      );

      const progressFill = screen.getByTestId('progress-fill');
      expect(progressFill).toHaveStyle({ width: '100%' });
    });
  });

  describe('Auto-publish Schedule Display', () => {
    it('should show auto-publish indicator when scheduled', () => {
      render(
        <PublishingTimeline
          eventCode="BATbern142"
          currentPhase="topic"
          publishedPhases={['topic']}
          eventDate="2025-05-15"
          autoPublishSchedule={[{ phase: 'speakers', scheduledDate: '2025-04-15T08:00:00Z' }]}
        />
      );

      expect(screen.getByTestId('auto-publish-speakers')).toBeInTheDocument();
    });

    it('should show countdown for upcoming auto-publish', () => {
      const tomorrow = new Date();
      tomorrow.setDate(tomorrow.getDate() + 1);

      render(
        <PublishingTimeline
          eventCode="BATbern142"
          currentPhase="topic"
          publishedPhases={['topic']}
          eventDate="2025-05-15"
          autoPublishSchedule={[{ phase: 'speakers', scheduledDate: tomorrow.toISOString() }]}
        />
      );

      expect(screen.getByText(/auto-publish in 1 days?/i)).toBeInTheDocument();
    });

    it('should not show auto-publish indicator when not scheduled', () => {
      render(
        <PublishingTimeline
          eventCode="BATbern142"
          currentPhase="topic"
          publishedPhases={['topic']}
          eventDate="2025-05-15"
        />
      );

      expect(screen.queryByTestId('auto-publish-speakers')).not.toBeInTheDocument();
    });
  });

  describe('Phase Icons', () => {
    it('should render checkmark icon for completed phases', () => {
      render(
        <PublishingTimeline
          eventCode="BATbern142"
          currentPhase="speakers"
          publishedPhases={['topic', 'speakers']}
          eventDate="2025-05-15"
        />
      );

      expect(screen.getByTestId('icon-complete-topic')).toBeInTheDocument();
    });

    it('should render in-progress icon for current phase', () => {
      render(
        <PublishingTimeline
          eventCode="BATbern142"
          currentPhase="speakers"
          publishedPhases={['topic']}
          eventDate="2025-05-15"
        />
      );

      expect(screen.getByTestId('icon-current-speakers')).toBeInTheDocument();
    });

    it('should render pending icon for upcoming phases', () => {
      render(
        <PublishingTimeline
          eventCode="BATbern142"
          currentPhase="topic"
          publishedPhases={['topic']}
          eventDate="2025-05-15"
        />
      );

      expect(screen.getByTestId('icon-pending-agenda')).toBeInTheDocument();
    });
  });
});
