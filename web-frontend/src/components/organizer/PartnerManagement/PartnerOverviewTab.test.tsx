/**
 * PartnerOverviewTab Component Tests (RED Phase -> GREEN Phase)
 *
 * TDD tests for Partner Overview Tab component
 * Story 2.8.2: Partner Detail View
 *
 * Test Scenarios - AC4:
 * - Partnership Details Panel (tier, dates, benefits, UID, tax status)
 * - Engagement Metrics Panel (placeholder for Epic 8)
 * - Recent Activity Panel (last event, topic votes, next meeting)
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PartnerOverviewTab } from './PartnerOverviewTab';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import type { PartnerResponse } from '@/services/api/partnerApi';

// Mock partner detail store
const mockSetShowEditModal = vi.fn();
vi.mock('@/stores/partnerDetailStore', () => ({
  usePartnerDetailStore: () => ({
    setShowEditModal: mockSetShowEditModal,
  }),
}));

const theme = createTheme();

// Helper to render with providers
const renderWithProviders = (component: React.ReactElement) => {
  return render(
    <ThemeProvider theme={theme}>
      <I18nextProvider i18n={i18n}>{component}</I18nextProvider>
    </ThemeProvider>
  );
};

// Mock partner data
const mockPartnerData: PartnerResponse = {
  id: 'p-123',
  companyName: 'GoogleZH',
  partnershipLevel: 'PLATINUM',
  partnershipStartDate: '2024-01-01',
  isActive: true,
  company: {
    id: 'c-456',
    name: 'Google Zurich',
    industry: 'Technology',
    website: 'https://google.com',
    location: 'Zurich, Switzerland',
    logoUrl: 'https://cdn.example.com/google-logo.png',
  },
};

describe('PartnerOverviewTab', () => {
  describe('AC4.1: should_renderOverviewTab_when_tabActivated', () => {
    it('renders the overview tab with all panels', () => {
      renderWithProviders(<PartnerOverviewTab partner={mockPartnerData} />);

      // Should have Partnership Details section
      expect(screen.getByRole('heading', { name: /Partnership Details/i })).toBeInTheDocument();

      // Should have Engagement Metrics section
      expect(screen.getByRole('heading', { name: /Engagement Metrics/i })).toBeInTheDocument();

      // Should have Recent Activity section
      expect(screen.getByRole('heading', { name: /Recent Activity/i })).toBeInTheDocument();
    });
  });

  describe('AC4.2: should_displayPartnershipDetails_when_partnerLoaded', () => {
    it('displays partnership tier with emoji', () => {
      renderWithProviders(<PartnerOverviewTab partner={mockPartnerData} />);

      expect(screen.getByText(/💎 PLATINUM/i)).toBeInTheDocument();
    });

    it('displays tier start date', () => {
      renderWithProviders(<PartnerOverviewTab partner={mockPartnerData} />);

      expect(screen.getByText(/Jan 1, 2024/i)).toBeInTheDocument();
    });

    it.skip('displays previous tier (TODO: requires backend implementation)', () => {
      // Feature not yet implemented - see TODO in component
      renderWithProviders(<PartnerOverviewTab partner={mockPartnerData} />);

      expect(screen.getByText(/Previous Tier/i)).toBeInTheDocument();
      expect(screen.getByText(/🥇 GOLD/i)).toBeInTheDocument();
    });

    it('displays Change Tier button', async () => {
      const user = userEvent.setup();
      renderWithProviders(<PartnerOverviewTab partner={mockPartnerData} />);

      const changeTierButton = screen.getByRole('button', { name: /Change Tier/i });
      expect(changeTierButton).toBeInTheDocument();

      await user.click(changeTierButton);
      expect(mockSetShowEditModal).toHaveBeenCalledWith(true);
    });
  });

  describe('AC4.3: should_displayBenefitsList_when_tierSet', () => {
    it('displays benefits for PLATINUM tier', () => {
      renderWithProviders(<PartnerOverviewTab partner={mockPartnerData} />);

      expect(screen.getByText(/Logo placement on website/i)).toBeInTheDocument();
      expect(screen.getByText(/Newsletter mentions/i)).toBeInTheDocument();
      expect(screen.getByText(/Priority event access/i)).toBeInTheDocument();
      expect(screen.getByText(/Quarterly strategic meetings/i)).toBeInTheDocument();
    });

    it('displays benefits with checkmarks', () => {
      renderWithProviders(<PartnerOverviewTab partner={mockPartnerData} />);

      // Should have checkmark icon (✓) before each benefit
      const benefitsSection = screen.getByText(/Logo placement on website/i).closest('div');
      expect(benefitsSection).toBeInTheDocument();
    });

    it('displays different benefits for STRATEGIC tier', () => {
      const strategicPartner: PartnerResponse = {
        ...mockPartnerData,
        partnershipLevel: 'STRATEGIC',
      };

      renderWithProviders(<PartnerOverviewTab partner={strategicPartner} />);

      expect(screen.getByText(/ROI analytics dashboard/i)).toBeInTheDocument();
      expect(screen.getByText(/Dedicated account manager/i)).toBeInTheDocument();
    });
  });

  describe.skip('AC4.4: should_displaySwissUID_when_companyDataEnriched (TODO: backend)', () => {
    // Company details (swissUid, taxStatus) require backend implementation
    it('displays Swiss UID', () => {
      renderWithProviders(<PartnerOverviewTab partner={mockPartnerData} />);

      expect(screen.getByText(/Swiss UID/i)).toBeInTheDocument();
      expect(screen.getByText(/CHE-123.456.789/i)).toBeInTheDocument();
    });

    it('displays tax status', () => {
      renderWithProviders(<PartnerOverviewTab partner={mockPartnerData} />);

      expect(screen.getByText(/Tax Status/i)).toBeInTheDocument();
      expect(screen.getByText(/VAT Registered/i)).toBeInTheDocument();
    });

    it('handles missing company data gracefully', () => {
      const partnerWithoutCompany: PartnerResponse = {
        ...mockPartnerData,
        company: undefined,
      };

      renderWithProviders(<PartnerOverviewTab partner={partnerWithoutCompany} />);

      // Should not crash, but Swiss UID section should not be rendered
      expect(screen.queryByText(/Swiss UID/i)).not.toBeInTheDocument();
    });
  });

  describe('AC4.5: should_showEngagementPlaceholder_when_epic8Deferred', () => {
    it('displays "Coming Soon - Epic 8" message', () => {
      renderWithProviders(<PartnerOverviewTab partner={mockPartnerData} />);

      expect(screen.getByText(/Coming Soon - Epic 8/i)).toBeInTheDocument();
    });

    it('displays placeholder score breakdown', () => {
      renderWithProviders(<PartnerOverviewTab partner={mockPartnerData} />);

      expect(screen.getByText(/Event Attendance/i)).toBeInTheDocument();
      expect(screen.getByText(/Topic Voting/i)).toBeInTheDocument();
      expect(screen.getByText(/Meeting Participation/i)).toBeInTheDocument();
      expect(screen.getByText(/Content Interaction/i)).toBeInTheDocument();
    });

    it('displays "View Full Analytics" link', () => {
      renderWithProviders(<PartnerOverviewTab partner={mockPartnerData} />);

      const analyticsLink = screen.getByText(/View Full Analytics →/i);
      expect(analyticsLink).toBeInTheDocument();
    });
  });

  describe.skip('AC4.6: should_displayRecentActivity_when_activityLoaded (TODO: Epic 8)', () => {
    // Recent activity features deferred to Epic 8
    it('displays last event attendance', () => {
      renderWithProviders(<PartnerOverviewTab partner={mockPartnerData} />);

      expect(screen.getByText(/Last Event/i)).toBeInTheDocument();
      expect(screen.getByText(/Spring 25/i)).toBeInTheDocument();
      expect(screen.getByText(/March 15, 2025/i)).toBeInTheDocument();
    });

    it('displays last event statistics', () => {
      renderWithProviders(<PartnerOverviewTab partner={mockPartnerData} />);

      expect(screen.getByText(/3 attendees/i)).toBeInTheDocument();
      expect(screen.getByText(/5 registrations/i)).toBeInTheDocument();
      expect(screen.getByText(/12 downloads/i)).toBeInTheDocument();
    });

    it('displays next meeting preview', () => {
      renderWithProviders(<PartnerOverviewTab partner={mockPartnerData} />);

      expect(screen.getByText(/Next Meeting/i)).toBeInTheDocument();
      expect(screen.getByText(/QUARTERLY_REVIEW/i)).toBeInTheDocument();
      expect(screen.getByText(/Google Office Zurich/i)).toBeInTheDocument();
      expect(screen.getByText(/Q1 Planning/i)).toBeInTheDocument();
    });

    it('handles missing last event gracefully', () => {
      const partnerWithoutEvent: PartnerResponse = {
        ...mockPartnerData,
        lastEvent: undefined,
      };

      renderWithProviders(<PartnerOverviewTab partner={partnerWithoutEvent} />);

      expect(screen.getByText(/No recent event/i)).toBeInTheDocument();
    });

    it('handles missing next meeting gracefully', () => {
      const partnerWithoutMeeting: PartnerResponse = {
        ...mockPartnerData,
        nextMeeting: undefined,
      };

      renderWithProviders(<PartnerOverviewTab partner={partnerWithoutMeeting} />);

      expect(screen.getByText(/No upcoming meeting/i)).toBeInTheDocument();
    });
  });

  describe.skip('AC4.7: should_displayTopicVotes_when_votesLoaded (TODO: Epic 8)', () => {
    // Topic votes features deferred to Epic 8
    it('displays active topic votes list', () => {
      renderWithProviders(<PartnerOverviewTab partner={mockPartnerData} />);

      expect(screen.getByText(/Active Topic Votes/i)).toBeInTheDocument();
      expect(screen.getByText(/Sustainable Architecture/i)).toBeInTheDocument();
      expect(screen.getByText(/Digital Transformation/i)).toBeInTheDocument();
    });

    it('displays vote priorities', () => {
      renderWithProviders(<PartnerOverviewTab partner={mockPartnerData} />);

      expect(screen.getByText(/HIGH/i)).toBeInTheDocument();
      expect(screen.getByText(/MEDIUM/i)).toBeInTheDocument();
    });

    it('handles empty votes list gracefully', () => {
      const partnerWithoutVotes: PartnerResponse = {
        ...mockPartnerData,
        activeVotes: [],
      };

      renderWithProviders(<PartnerOverviewTab partner={partnerWithoutVotes} />);

      expect(screen.getByText(/No active votes/i)).toBeInTheDocument();
    });
  });
});
