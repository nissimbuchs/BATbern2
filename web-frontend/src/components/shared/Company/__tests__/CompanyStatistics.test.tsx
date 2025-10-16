import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { CompanyStatistics } from '@/components/shared/Company/CompanyStatistics';

// Updated to match backend CompanyStatistics schema
const mockStatistics = {
  totalEvents: 5,
  totalSpeakers: 12,
  totalPartners: 3,
};

describe('CompanyStatistics Component', () => {
  describe('Display statistics dashboard', () => {
    it('should_displayStatistics_when_statisticsProvided', () => {
      render(<CompanyStatistics statistics={mockStatistics} />);

      // Verify only 3 statistics are displayed (backend only provides these)
      expect(screen.getByText(/5/)).toBeInTheDocument(); // total events
      expect(screen.getByText(/12/)).toBeInTheDocument(); // total speakers
      expect(screen.getByText(/3/)).toBeInTheDocument(); // total partners
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

  describe('Visual statistics display', () => {
    it('should_renderStatisticsCards_when_metricsExist', () => {
      render(<CompanyStatistics statistics={mockStatistics} />);

      // Verify only 3 statistics cards are rendered (matching backend schema)
      expect(screen.getByTestId('events-card')).toBeInTheDocument();
      expect(screen.getByTestId('speakers-card')).toBeInTheDocument();
      expect(screen.getByTestId('partners-card')).toBeInTheDocument();
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
