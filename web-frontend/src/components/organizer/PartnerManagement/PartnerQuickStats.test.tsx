import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { PartnerQuickStats } from './PartnerQuickStats';

/**
 * Test suite for PartnerQuickStats component
 * Tests all AC2 scenarios (Quick Stats Cards)
 *
 * Following TDD Red-Green-Refactor:
 * - Phase: RED - All tests written to FAIL first
 * - Next: GREEN - Implement component to pass tests
 */
describe('PartnerQuickStats', () => {
  const mockPartnerData = {
    partnershipStartDate: '2022-01-15',
    statistics: {
      eventsAttended: 24,
      lastEventName: 'Spring 25',
      activeVotes: 5,
      totalMeetings: 12,
    },
  };

  /**
   * AC2 Test 2.1: should_displayPartnerSince_when_partnerLoaded
   */
  it('should_displayPartnerSince_when_partnerLoaded', () => {
    render(<PartnerQuickStats partner={mockPartnerData} />);

    expect(screen.getByText(/partner since/i)).toBeInTheDocument();
    expect(screen.getByText(/january 2022/i)).toBeInTheDocument();
  });

  /**
   * AC2 Test 2.2: should_calculateDuration_when_partnerSinceSet
   */
  it('should_calculateDuration_when_partnerSinceSet', () => {
    render(<PartnerQuickStats partner={mockPartnerData} />);

    // Calculate expected years since 2022-01-15
    const startDate = new Date('2022-01-15');
    const now = new Date();
    const expectedYears = Math.floor(
      (now.getTime() - startDate.getTime()) / (365.25 * 24 * 60 * 60 * 1000)
    );

    // Should show duration (e.g., "4 years")
    expect(screen.getByText(new RegExp(`${expectedYears} years`, 'i'))).toBeInTheDocument();
  });

  /**
   * AC2 Test 2.3: should_displayEventsAttended_when_statisticsLoaded
   */
  it('should_displayEventsAttended_when_statisticsLoaded', () => {
    render(<PartnerQuickStats partner={mockPartnerData} />);

    expect(screen.getByText(/events attended/i)).toBeInTheDocument();
    expect(screen.getByText('24')).toBeInTheDocument();
    expect(screen.getByText(/last: spring 25/i)).toBeInTheDocument();
  });

  /**
   * AC2 Test 2.4: should_displayActiveVotes_when_votesLoaded
   */
  it('should_displayActiveVotes_when_votesLoaded', () => {
    render(<PartnerQuickStats partner={mockPartnerData} />);

    expect(screen.getByText(/active votes/i)).toBeInTheDocument();
    expect(screen.getByText('5')).toBeInTheDocument();
    expect(screen.getByText(/topics/i)).toBeInTheDocument();
  });

  /**
   * AC2 Test 2.5: should_displayMeetingsCount_when_meetingsLoaded
   */
  it('should_displayMeetingsCount_when_meetingsLoaded', () => {
    render(<PartnerQuickStats partner={mockPartnerData} />);

    expect(screen.getByText(/meetings/i)).toBeInTheDocument();
    expect(screen.getByText('12')).toBeInTheDocument();
    expect(screen.getByText(/seasonal/i)).toBeInTheDocument();
  });

  /**
   * Edge Case: should_handleMissingStatistics_when_dataIncomplete
   */
  it('should_handleMissingStatistics_when_dataIncomplete', () => {
    const incompleteData = {
      partnershipStartDate: '2022-01-15',
      statistics: {
        eventsAttended: 0,
        lastEventName: null,
        activeVotes: 0,
        totalMeetings: 0,
      },
    };

    render(<PartnerQuickStats partner={incompleteData} />);

    expect(screen.getByText(/events attended/i)).toBeInTheDocument();
    expect(screen.getByText(/no events yet/i)).toBeInTheDocument();
  });

  /**
   * Edge Case: should_formatDuration_when_lessThanOneYear
   */
  it('should_formatDuration_when_lessThanOneYear', () => {
    const recentPartner = {
      partnershipStartDate: new Date(Date.now() - 6 * 30 * 24 * 60 * 60 * 1000)
        .toISOString()
        .split('T')[0], // 6 months ago
      statistics: {
        eventsAttended: 2,
        lastEventName: 'Fall 24',
        activeVotes: 1,
        totalMeetings: 1,
      },
    };

    render(<PartnerQuickStats partner={recentPartner} />);

    expect(screen.getByText(/6 months/i)).toBeInTheDocument();
  });

  /**
   * Responsive Design: should_stackCards_when_mobileViewport
   */
  it('should_stackCards_when_mobileViewport', () => {
    const { container } = render(<PartnerQuickStats partner={mockPartnerData} />);

    // Check that Grid component has responsive props
    const gridElement = container.querySelector('[class*="MuiGrid"]');
    expect(gridElement).toBeInTheDocument();
  });

  /**
   * Accessibility: should_haveSemanticStructure_when_rendered
   */
  it('should_haveSemanticStructure_when_rendered', () => {
    render(<PartnerQuickStats partner={mockPartnerData} />);

    // Each stat card should have proper heading structure
    const headings = screen.getAllByRole('heading', { level: 3 });
    expect(headings.length).toBe(4); // 4 stat cards
  });
});
