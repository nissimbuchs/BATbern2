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
    it('renders the overview tab with partnership details', () => {
      renderWithProviders(<PartnerOverviewTab partner={mockPartnerData} />);

      // Should have Partnership Details section
      expect(screen.getByRole('heading', { name: /Partnership Details/i })).toBeInTheDocument();

      // Engagement Metrics and Recent Activity panels were removed post-epic-8
      expect(
        screen.queryByRole('heading', { name: /Engagement Metrics/i })
      ).not.toBeInTheDocument();
      expect(screen.queryByRole('heading', { name: /Recent Activity/i })).not.toBeInTheDocument();
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
});
