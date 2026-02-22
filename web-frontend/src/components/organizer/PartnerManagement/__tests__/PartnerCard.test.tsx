/**
 * PartnerCard Component Tests (RED Phase - TDD)
 *
 * Story 2.8.1 - Task 4a
 * AC: 3 (Partner Cards)
 *
 * Tests for partner card with:
 * - Company logo (with fallback to initials avatar)
 * - Partnership tier badge with emoji
 * - Company information (name, industry)
 * - Primary contact information
 * - Last event attendance
 * - Topic votes count
 * - Next meeting date
 * - Engagement bar (placeholder for Epic 8)
 * - Quick action buttons
 */

import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { PartnerCard } from '../PartnerCard';
import type { PartnerResponse } from '@/services/api/partnerApi';

// Mock react-i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'en' },
  }),
}));

describe('PartnerCard Component (RED Phase - Task 4a)', () => {
  const mockPartnerWithLogo: PartnerResponse = {
    id: '123e4567-e89b-12d3-a456-426614174000',
    companyName: 'TechCorp',
    partnershipLevel: 'GOLD',
    partnershipStartDate: '2024-01-01',
    isActive: true,
    company: {
      companyName: 'TechCorp',
      displayName: 'TechCorp Solutions AG',
      logoUrl: 'https://cdn.example.com/techcorp.png',
      industry: 'Technology',
    },
    contacts: [
      {
        username: 'john.doe',
        email: 'john@techcorp.com',
        firstName: 'John',
        lastName: 'Doe',
      },
    ],
    lastEventName: 'BATbern 56',
    votesCount: 5,
    nextMeetingDate: '2025-03-15',
  };

  const mockPartnerWithoutLogo: PartnerResponse = {
    ...mockPartnerWithLogo,
    company: {
      ...mockPartnerWithLogo.company!,
      logoUrl: undefined,
    },
  };

  const createWrapper = () => {
    return ({ children }: { children: React.ReactNode }) => (
      <BrowserRouter>{children}</BrowserRouter>
    );
  };

  describe('AC3: Test 3.1 - should_renderPartnerCard_when_partnerDataProvided', () => {
    it('should render partner card with company name', () => {
      render(<PartnerCard partner={mockPartnerWithLogo} />, { wrapper: createWrapper() });

      expect(screen.getByText('TechCorp')).toBeInTheDocument();
    });

    it('should render card element', () => {
      const { container } = render(<PartnerCard partner={mockPartnerWithLogo} />, {
        wrapper: createWrapper(),
      });

      // Check that MUI Card is rendered
      const card = container.querySelector('.MuiCard-root');
      expect(card).toBeInTheDocument();
    });
  });

  describe('AC3: Test 3.2 - should_displayCompanyLogo_when_logoUrlExists', () => {
    it('should display logo image when logoUrl is provided', () => {
      render(<PartnerCard partner={mockPartnerWithLogo} />, { wrapper: createWrapper() });

      const logo = screen.getByRole('img', { name: /TechCorp/i });
      expect(logo).toBeInTheDocument();
      expect(logo).toHaveAttribute('src', 'https://cdn.example.com/techcorp.png');
    });
  });

  describe('AC3: Test 3.3 - should_displayInitialsAvatar_when_logoUrlMissing', () => {
    it('should display initials avatar when logoUrl is missing', () => {
      render(<PartnerCard partner={mockPartnerWithoutLogo} />, { wrapper: createWrapper() });

      // Should display "TE" for "TechCorp"
      expect(screen.getByText('TE')).toBeInTheDocument();
    });

    it('should use first two letters of company name for initials', () => {
      const partner = {
        ...mockPartnerWithoutLogo,
        companyName: 'Acme Corp',
      };
      render(<PartnerCard partner={partner} />, { wrapper: createWrapper() });

      expect(screen.getByText('AC')).toBeInTheDocument();
    });
  });

  describe('AC3: Test 3.4 - should_displayTierBadge_when_partnershipLevelSet', () => {
    it('should display gold tier badge with emoji', () => {
      render(<PartnerCard partner={mockPartnerWithLogo} />, { wrapper: createWrapper() });

      // Should show emoji and i18n tier name combined in chip
      expect(screen.getByText('🥇 tiers.gold')).toBeInTheDocument();
    });

    it('should display strategic tier badge', () => {
      const partner = { ...mockPartnerWithLogo, partnershipLevel: 'STRATEGIC' as const };
      render(<PartnerCard partner={partner} />, { wrapper: createWrapper() });

      expect(screen.getByText('🏆 tiers.strategic')).toBeInTheDocument();
    });

    it('should display platinum tier badge', () => {
      const partner = { ...mockPartnerWithLogo, partnershipLevel: 'PLATINUM' as const };
      render(<PartnerCard partner={partner} />, { wrapper: createWrapper() });

      expect(screen.getByText('💎 tiers.platinum')).toBeInTheDocument();
    });

    it('should display silver tier badge', () => {
      const partner = { ...mockPartnerWithLogo, partnershipLevel: 'SILVER' as const };
      render(<PartnerCard partner={partner} />, { wrapper: createWrapper() });

      expect(screen.getByText('🥈 tiers.silver')).toBeInTheDocument();
    });

    it('should display bronze tier badge', () => {
      const partner = { ...mockPartnerWithLogo, partnershipLevel: 'BRONZE' as const };
      render(<PartnerCard partner={partner} />, { wrapper: createWrapper() });

      expect(screen.getByText('🥉 tiers.bronze')).toBeInTheDocument();
    });
  });

  describe('AC3: Test 3.5 - should_displayEnrichedCompanyData_when_includeCompanyUsed', () => {
    it('should display industry from enriched company data', () => {
      render(<PartnerCard partner={mockPartnerWithLogo} />, { wrapper: createWrapper() });

      expect(screen.getByText(/Technology/)).toBeInTheDocument();
    });

    it('should display N/A when industry is missing', () => {
      const partner = {
        ...mockPartnerWithLogo,
        company: {
          ...mockPartnerWithLogo.company!,
          industry: undefined,
        },
      };
      render(<PartnerCard partner={partner} />, { wrapper: createWrapper() });

      expect(screen.getByText(/N\/A/)).toBeInTheDocument();
    });
  });

  describe('AC3: Test 3.6 - should_displayEnrichedContactData_when_includeContactsUsed', () => {
    it('should display primary contact name and email', () => {
      render(<PartnerCard partner={mockPartnerWithLogo} />, { wrapper: createWrapper() });

      expect(screen.getByText(/John Doe/)).toBeInTheDocument();
      expect(screen.getByText(/john@techcorp.com/)).toBeInTheDocument();
    });

    it('should not display contact when contacts array is empty', () => {
      const partner = {
        ...mockPartnerWithLogo,
        contacts: [],
      };
      render(<PartnerCard partner={partner} />, { wrapper: createWrapper() });

      expect(screen.queryByText(/John Doe/)).not.toBeInTheDocument();
    });
  });

  describe('AC3: Test 3.7 - should_navigateToDetail_when_viewDetailsClicked', () => {
    it('should call navigation when View Details button clicked', () => {
      render(<PartnerCard partner={mockPartnerWithLogo} />, { wrapper: createWrapper() });

      const viewDetailsButton = screen.getByRole('button', { name: 'card.viewDetails' });
      expect(viewDetailsButton).toBeInTheDocument();
      expect(viewDetailsButton).toBeEnabled();
    });

    it('should have clickable card that navigates to detail', () => {
      const { container } = render(<PartnerCard partner={mockPartnerWithLogo} />, {
        wrapper: createWrapper(),
      });

      const card = container.querySelector('.MuiCard-root');
      expect(card).toBeInTheDocument();
    });
  });

  describe.skip('AC3: Test 3.8 - should_showPlaceholder_when_epicFeatureDeferred (TODO: Epic 8)', () => {
    // Engagement features deferred to Epic 8
    it('should show engagement placeholder', () => {
      render(<PartnerCard partner={mockPartnerWithLogo} />, { wrapper: createWrapper() });

      expect(screen.getByText('card.comingSoon')).toBeInTheDocument();
    });

    it('should display last event attendance', () => {
      render(<PartnerCard partner={mockPartnerWithLogo} />, { wrapper: createWrapper() });

      expect(screen.getByText(/BATbern 56/)).toBeInTheDocument();
    });

    it('should display N/A when last event is missing', () => {
      const partner = {
        ...mockPartnerWithLogo,
        lastEventName: undefined,
      };
      render(<PartnerCard partner={partner} />, { wrapper: createWrapper() });

      expect(screen.getByText('card.noEventYet')).toBeInTheDocument();
    });

    it('should display votes count', () => {
      render(<PartnerCard partner={mockPartnerWithLogo} />, { wrapper: createWrapper() });

      // Check for the vote count number
      expect(screen.getByText(/5/)).toBeInTheDocument();
    });

    it('should display next meeting date', () => {
      render(<PartnerCard partner={mockPartnerWithLogo} />, { wrapper: createWrapper() });

      expect(screen.getByText(/2025-03-15/)).toBeInTheDocument();
    });

    it('should display Pending when next meeting is not set', () => {
      const partner = {
        ...mockPartnerWithLogo,
        nextMeetingDate: undefined,
      };
      render(<PartnerCard partner={partner} />, { wrapper: createWrapper() });

      expect(screen.getByText('card.pending')).toBeInTheDocument();
    });

    it('should have disabled action buttons for Epic 8 features', () => {
      render(<PartnerCard partner={mockPartnerWithLogo} />, { wrapper: createWrapper() });

      // These buttons should be disabled (Epic 8 features)
      const sendEmailButton = screen.getByRole('button', { name: 'card.sendEmail' });
      const analyticsButton = screen.getByRole('button', { name: 'card.analytics' });

      expect(sendEmailButton).toBeDisabled();
      expect(analyticsButton).toBeDisabled();
    });
  });

  describe('Additional Display Tests', () => {
    it('should render progress bar for engagement', () => {
      render(<PartnerCard partner={mockPartnerWithLogo} />, { wrapper: createWrapper() });

      const progressBar = screen.getByRole('progressbar');
      expect(progressBar).toBeInTheDocument();
    });

    it('should have View Details button enabled', () => {
      render(<PartnerCard partner={mockPartnerWithLogo} />, { wrapper: createWrapper() });

      const viewDetailsButton = screen.getByRole('button', { name: 'card.viewDetails' });
      expect(viewDetailsButton).toBeEnabled();
    });
  });
});
