import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PartnerOverviewStats } from '../PartnerOverviewStats';
import { usePartnerStatistics } from '@/hooks/usePartners';

// Mock the usePartnerStatistics hook
vi.mock('@/hooks/usePartners', () => ({
  usePartnerStatistics: vi.fn(),
}));

describe('PartnerOverviewStats', () => {
  describe('AC2 Tests: Partner Overview Statistics', () => {
    it('should_renderStatistics_when_statisticsLoaded', () => {
      // Arrange
      vi.mocked(usePartnerStatistics).mockReturnValue({
        data: {
          totalPartners: 24,
          activePartners: 18,
          engagementRate: 75.5,
          tierDistribution: {
            STRATEGIC: 3,
            PLATINUM: 5,
            GOLD: 8,
            SILVER: 6,
            BRONZE: 2,
          },
        },
        isLoading: false,
        isError: false,
      } as any);

      // Act
      render(<PartnerOverviewStats />);

      // Assert - Component should render without crashing
      expect(screen.getByText('Total Partners')).toBeInTheDocument();
    });

    it('should_displayTotalCount_when_statisticsProvided', () => {
      // Arrange
      vi.mocked(usePartnerStatistics).mockReturnValue({
        data: {
          totalPartners: 24,
          activePartners: 18,
          engagementRate: 75.5,
          tierDistribution: {
            STRATEGIC: 3,
            PLATINUM: 5,
            GOLD: 8,
            SILVER: 6,
            BRONZE: 2,
          },
        },
        isLoading: false,
        isError: false,
      } as any);

      // Act
      render(<PartnerOverviewStats />);

      // Assert - Should display total partners count
      expect(screen.getByText('24')).toBeInTheDocument();
      expect(screen.getByText(/Total Partners/i)).toBeInTheDocument();
    });

    it('should_displayActiveCount_when_statisticsProvided', () => {
      // Arrange
      vi.mocked(usePartnerStatistics).mockReturnValue({
        data: {
          totalPartners: 24,
          activePartners: 18,
          engagementRate: 75.5,
          tierDistribution: {
            STRATEGIC: 3,
            PLATINUM: 5,
            GOLD: 8,
            SILVER: 6,
            BRONZE: 2,
          },
        },
        isLoading: false,
        isError: false,
      } as any);

      // Act
      render(<PartnerOverviewStats />);

      // Assert - Should display active partners count
      expect(screen.getByText('18')).toBeInTheDocument();
      expect(screen.getByText(/Active Partners/i)).toBeInTheDocument();
    });

    it('should_displayTierDistribution_when_statisticsProvided', () => {
      // Arrange
      vi.mocked(usePartnerStatistics).mockReturnValue({
        data: {
          totalPartners: 24,
          activePartners: 18,
          engagementRate: 75.5,
          tierDistribution: {
            STRATEGIC: 3,
            PLATINUM: 5,
            GOLD: 8,
            SILVER: 6,
            BRONZE: 2,
          },
        },
        isLoading: false,
        isError: false,
      } as any);

      // Act
      render(<PartnerOverviewStats />);

      // Assert - Should display tier distribution with emoji indicators and labels
      expect(screen.getByText(/🏆 Strategic: 3/)).toBeInTheDocument();
      expect(screen.getByText(/💎 Platinum: 5/)).toBeInTheDocument();
      expect(screen.getByText(/🥇 Gold: 8/)).toBeInTheDocument();
      expect(screen.getByText(/🥈 Silver: 6/)).toBeInTheDocument();
      expect(screen.getByText(/🥉 Bronze: 2/)).toBeInTheDocument();
    });

    it('should_showLoadingState_when_fetching', () => {
      // Arrange
      vi.mocked(usePartnerStatistics).mockReturnValue({
        data: undefined,
        isLoading: true,
        isError: false,
      } as any);

      // Act
      render(<PartnerOverviewStats />);

      // Assert - Should show loading skeleton
      const skeletons = document.querySelectorAll('.MuiSkeleton-root');
      expect(skeletons.length).toBeGreaterThan(0);
    });

    it('should_showPlaceholder_when_engagementMetricsDeferred', () => {
      // Arrange
      vi.mocked(usePartnerStatistics).mockReturnValue({
        data: {
          totalPartners: 24,
          activePartners: 18,
          engagementRate: 75.5,
          tierDistribution: {
            STRATEGIC: 3,
            PLATINUM: 5,
            GOLD: 8,
            SILVER: 6,
            BRONZE: 2,
          },
        },
        isLoading: false,
        isError: false,
      } as any);

      // Act
      render(<PartnerOverviewStats />);

      // Assert - Should show "Coming Soon" for engagement metrics (Epic 8)
      expect(screen.getByText(/Coming Soon/i)).toBeInTheDocument();
    });

    it('should_handleEmptyStatistics_when_noData', () => {
      // Arrange
      vi.mocked(usePartnerStatistics).mockReturnValue({
        data: undefined,
        isLoading: false,
        isError: false,
      } as any);

      // Act
      render(<PartnerOverviewStats />);

      // Assert - Should handle missing data gracefully with zero counts
      expect(screen.getByText('Total Partners')).toBeInTheDocument();
      expect(screen.getByText('Active Partners')).toBeInTheDocument();
      const zeros = screen.getAllByText('0');
      expect(zeros.length).toBeGreaterThan(0);
    });

    it('should_handleErrorState_when_fetchFails', () => {
      // Arrange
      vi.mocked(usePartnerStatistics).mockReturnValue({
        data: undefined,
        isLoading: false,
        isError: true,
        error: new Error('Failed to load statistics'),
      } as any);

      // Act
      render(<PartnerOverviewStats />);

      // Assert - Should display error state
      expect(screen.getByText(/Failed to load statistics/i)).toBeInTheDocument();
    });

    it('should_displayZeroCounts_when_noPartners', () => {
      // Arrange
      vi.mocked(usePartnerStatistics).mockReturnValue({
        data: {
          totalPartners: 0,
          activePartners: 0,
          engagementRate: 0,
          tierDistribution: {
            STRATEGIC: 0,
            PLATINUM: 0,
            GOLD: 0,
            SILVER: 0,
            BRONZE: 0,
          },
        },
        isLoading: false,
        isError: false,
      } as any);

      // Act
      render(<PartnerOverviewStats />);

      // Assert - Should display zero counts
      expect(screen.getByText(/Total Partners/i)).toBeInTheDocument();
      const zeros = screen.getAllByText('0');
      expect(zeros.length).toBeGreaterThan(0);
    });

    it('should_formatNumbers_when_largeValues', () => {
      // Arrange
      vi.mocked(usePartnerStatistics).mockReturnValue({
        data: {
          totalPartners: 1000,
          activePartners: 850,
          engagementRate: 85.0,
          tierDistribution: {
            STRATEGIC: 100,
            PLATINUM: 200,
            GOLD: 300,
            SILVER: 250,
            BRONZE: 150,
          },
        },
        isLoading: false,
        isError: false,
      } as any);

      // Act
      render(<PartnerOverviewStats />);

      // Assert - Should format large numbers correctly
      expect(screen.getByText('1000')).toBeInTheDocument();
      expect(screen.getByText('850')).toBeInTheDocument();
    });

    it('should_displayStatisticsInCards_when_rendered', () => {
      // Arrange
      vi.mocked(usePartnerStatistics).mockReturnValue({
        data: {
          totalPartners: 24,
          activePartners: 18,
          engagementRate: 75.5,
          tierDistribution: {
            STRATEGIC: 3,
            PLATINUM: 5,
            GOLD: 8,
            SILVER: 6,
            BRONZE: 2,
          },
        },
        isLoading: false,
        isError: false,
      } as any);

      // Act
      render(<PartnerOverviewStats />);

      // Assert - Should use Material-UI Card components
      const cards = document.querySelectorAll('.MuiCard-root');
      expect(cards.length).toBeGreaterThan(0);
    });

    it('should_displayTierLabels_when_distributionShown', () => {
      // Arrange
      vi.mocked(usePartnerStatistics).mockReturnValue({
        data: {
          totalPartners: 24,
          activePartners: 18,
          engagementRate: 75.5,
          tierDistribution: {
            STRATEGIC: 3,
            PLATINUM: 5,
            GOLD: 8,
            SILVER: 6,
            BRONZE: 2,
          },
        },
        isLoading: false,
        isError: false,
      } as any);

      // Act
      render(<PartnerOverviewStats />);

      // Assert - Should display tier labels
      expect(screen.getByText(/Strategic/i)).toBeInTheDocument();
      expect(screen.getByText(/Platinum/i)).toBeInTheDocument();
      expect(screen.getByText(/Gold/i)).toBeInTheDocument();
      expect(screen.getByText(/Silver/i)).toBeInTheDocument();
      expect(screen.getByText(/Bronze/i)).toBeInTheDocument();
    });
  });
});
