import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PublishingTimeline } from './PublishingTimeline';

// Mock date formatting utility to return predictable values
vi.mock('@/utils/date/dateFormat', () => ({
  formatDate: (date: Date, locale: string, fmt?: string) => {
    const month = date.toLocaleString('en-US', { month: 'short' });
    const day = date.getDate();
    const year = date.getFullYear();
    if (fmt) {
      return `${day}. ${month} ${year}`;
    }
    return locale === 'de' ? `${day}. ${month} ${year}` : `${month} ${day}, ${year}`;
  },
}));

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

      // Uses i18n key with formatted date
      expect(screen.getByTestId('published-date-topic')).toBeInTheDocument();
    });

    it('should show scheduled date for Speakers phase', () => {
      render(
        <PublishingTimeline
          eventCode="BATbern142"
          currentPhase="topic"
          publishedPhases={['topic']}
          eventDate="2025-05-15"
          scheduledDates={{ speakers: '2025-04-15T08:00:00Z' }}
        />
      );

      expect(screen.getByTestId('scheduled-date-speakers')).toBeInTheDocument();
    });

    it('should show scheduled date for Agenda phase', () => {
      render(
        <PublishingTimeline
          eventCode="BATbern142"
          currentPhase="speakers"
          publishedPhases={['topic', 'speakers']}
          eventDate="2025-05-15"
          scheduledDates={{ agenda: '2025-05-01T08:00:00Z' }}
        />
      );

      expect(screen.getByTestId('scheduled-date-agenda')).toBeInTheDocument();
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

    it('should show progress line at 33% when topic phase complete', () => {
      render(
        <PublishingTimeline
          eventCode="BATbern142"
          currentPhase="topic"
          publishedPhases={['topic']}
          eventDate="2025-05-15"
        />
      );

      const progressFill = screen.getByTestId('progress-fill');
      // 1/3 ≈ 33.33%
      expect(progressFill).toHaveStyle({ width: '33.33333333333333%' });
    });

    it('should show progress line at 67% when speakers phase complete', () => {
      render(
        <PublishingTimeline
          eventCode="BATbern142"
          currentPhase="speakers"
          publishedPhases={['topic', 'speakers']}
          eventDate="2025-05-15"
        />
      );

      const progressFill = screen.getByTestId('progress-fill');
      // 2/3 ≈ 66.67%
      expect(progressFill).toHaveStyle({ width: '66.66666666666666%' });
    });

    it('should show progress line at 100% when all phases complete', () => {
      render(
        <PublishingTimeline
          eventCode="BATbern142"
          currentPhase="agenda"
          publishedPhases={['topic', 'speakers', 'agenda']}
          eventDate="2025-05-15"
        />
      );

      const progressFill = screen.getByTestId('progress-fill');
      expect(progressFill).toHaveStyle({ width: '100%' });
    });

    it('should use i18n for progress text', () => {
      render(
        <PublishingTimeline
          eventCode="BATbern142"
          currentPhase="topic"
          publishedPhases={['topic']}
          eventDate="2025-05-15"
        />
      );

      expect(screen.getByText(/1 of 3 phases published/i)).toBeInTheDocument();
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
