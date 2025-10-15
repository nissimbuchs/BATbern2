import { render, screen } from '@testing-library/react';
import { vi, describe, it, expect } from 'vitest';
import { CompanyStatistics } from '@/components/shared/Company/CompanyStatistics';

const mockStatistics = {
  totalEvents: 5,
  totalPresentations: 12,
  totalAttendees: 250,
  firstEvent: '2024-02-01',
  mostRecentEvent: '2024-10-01',
  topicExpertise: [
    { topic: 'Cloud Security', count: 8 },
    { topic: 'DevOps', count: 4 },
    { topic: 'Container Orchestration', count: 3 },
  ],
};

describe('CompanyStatistics Component', () => {
  describe('Display statistics dashboard', () => {
    it('should_displayStatistics_when_statisticsProvided', () => {
      render(<CompanyStatistics statistics={mockStatistics} />);

      // Verify key statistics are displayed
      expect(screen.getByText(/5/)).toBeInTheDocument(); // total events
      expect(screen.getByText(/12/)).toBeInTheDocument(); // total presentations
      expect(screen.getByText(/250/)).toBeInTheDocument(); // total attendees
    });

    it('should_displayTopicExpertise_when_topicsAvailable', () => {
      render(<CompanyStatistics statistics={mockStatistics} />);

      // Verify topic expertise is displayed
      expect(screen.getByText('Cloud Security')).toBeInTheDocument();
      expect(screen.getByText('DevOps')).toBeInTheDocument();
      expect(screen.getByText('Container Orchestration')).toBeInTheDocument();
    });

    it('should_displayEventDateRange_when_eventsExist', () => {
      render(<CompanyStatistics statistics={mockStatistics} />);

      // Verify first and most recent event dates
      expect(screen.getByText(/2024-02-01/)).toBeInTheDocument();
      expect(screen.getByText(/2024-10-01/)).toBeInTheDocument();
    });

    it('should_displayEmptyState_when_noStatistics', () => {
      render(<CompanyStatistics statistics={null} />);

      // Verify empty state message
      expect(screen.getByText(/no statistics available/i)).toBeInTheDocument();
    });

    it('should_displayLoadingSkeleton_when_loading', () => {
      render(<CompanyStatistics statistics={null} isLoading={true} />);

      // Verify skeleton loader
      expect(screen.getByTestId('statistics-skeleton')).toBeInTheDocument();
    });
  });

  describe('Visual charts and graphs', () => {
    it('should_renderBarChart_when_topicExpertiseExists', () => {
      render(<CompanyStatistics statistics={mockStatistics} />);

      // Verify chart is rendered
      expect(screen.getByTestId('topic-expertise-chart')).toBeInTheDocument();
    });

    it('should_renderStatisticsCards_when_metricsExist', () => {
      render(<CompanyStatistics statistics={mockStatistics} />);

      // Verify statistics cards are rendered
      expect(screen.getByTestId('events-card')).toBeInTheDocument();
      expect(screen.getByTestId('presentations-card')).toBeInTheDocument();
      expect(screen.getByTestId('attendees-card')).toBeInTheDocument();
    });
  });

  describe('Responsive layout', () => {
    it('should_stackCards_when_mobileViewport', () => {
      global.innerWidth = 375;
      global.dispatchEvent(new Event('resize'));

      render(<CompanyStatistics statistics={mockStatistics} />);

      const container = screen.getByTestId('statistics-container');
      expect(container).toHaveClass('mobile-layout');
    });
  });
});
