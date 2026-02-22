/**
 * PartnerCard Component Tests
 * Story 2.8.1 - Task 4b (GREEN Phase)
 *
 * Tests for PartnerCard component covering basic rendering and interactions
 */

import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import userEvent from '@testing-library/user-event';
import { PartnerCard } from './PartnerCard';
import { BrowserRouter } from 'react-router-dom';
import type { PartnerResponse } from '@/services/api/partnerApi';

// Mock translation
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        'tiers.strategic': 'Strategic',
        'tiers.platinum': 'Platinum',
        'tiers.gold': 'Gold',
        'tiers.silver': 'Silver',
        'tiers.bronze': 'Bronze',
        'card.engagement': 'Engagement',
        'card.engagementComingSoon': 'Coming Soon',
        'card.viewDetails': 'View Details',
        'card.sendEmail': 'Send Email',
        'card.analytics': 'Analytics',
        'card.comingSoon': 'Coming Soon',
      };
      return translations[key] || key;
    },
  }),
}));

// Mock navigate
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

const mockPartner: PartnerResponse = {
  id: 'partner-1',
  companyId: 'company-1',
  companyName: 'Test Company',
  partnershipLevel: 'GOLD',
  company: {
    logoUrl: 'https://example.com/logo.png',
    industry: 'Technology',
  },
  contacts: [
    {
      username: 'john.doe',
      firstName: 'John',
      lastName: 'Doe',
      email: 'john.doe@example.com',
    },
  ],
  engagementScore: 75,
};

const renderWithRouter = (ui: React.ReactElement) => {
  return render(<BrowserRouter>{ui}</BrowserRouter>);
};

describe('PartnerCard Component', () => {
  describe('Rendering', () => {
    it('should_renderCompanyName_when_partnerProvided', () => {
      renderWithRouter(<PartnerCard partner={mockPartner} />);

      expect(screen.getByText('Test Company')).toBeInTheDocument();
    });

    it('should_renderTierBadge_when_partnerProvided', () => {
      renderWithRouter(<PartnerCard partner={mockPartner} />);

      expect(screen.getByText(/Gold/)).toBeInTheDocument();
    });

    it('should_renderTierEmoji_when_partnerProvided', () => {
      renderWithRouter(<PartnerCard partner={mockPartner} />);

      const badge = screen.getByText(/🥇/);
      expect(badge).toBeInTheDocument();
    });

    it('should_renderCompanyLogo_when_logoUrlExists', () => {
      renderWithRouter(<PartnerCard partner={mockPartner} />);

      const logo = screen.getByAltText('Test Company');
      expect(logo).toBeInTheDocument();
      expect(logo).toHaveAttribute('src', 'https://example.com/logo.png');
    });

    it('should_renderInitialsAvatar_when_noLogoUrl', () => {
      const partnerWithoutLogo = {
        ...mockPartner,
        company: {
          ...mockPartner.company,
          logoUrl: undefined,
        },
      };

      renderWithRouter(<PartnerCard partner={partnerWithoutLogo} />);

      expect(screen.getByText('TE')).toBeInTheDocument(); // Test Company -> TE
    });

    it('should_renderIndustry_when_companyHasIndustry', () => {
      renderWithRouter(<PartnerCard partner={mockPartner} />);

      expect(screen.getByText(/Technology/)).toBeInTheDocument();
    });

    it('should_renderPrimaryContact_when_contactsExist', () => {
      renderWithRouter(<PartnerCard partner={mockPartner} />);

      expect(screen.getByText(/John Doe/)).toBeInTheDocument();
      expect(screen.getByText(/john.doe@example.com/)).toBeInTheDocument();
    });

    it('should_renderEngagementBar_when_rendered', () => {
      renderWithRouter(<PartnerCard partner={mockPartner} />);

      expect(screen.getByText(/Engagement/)).toBeInTheDocument();
      expect(screen.getByText(/Coming Soon/)).toBeInTheDocument();
      expect(screen.getByRole('progressbar')).toBeInTheDocument();
    });

    it('should_renderNoActionButtons_when_rendered', () => {
      renderWithRouter(<PartnerCard partner={mockPartner} />);

      expect(screen.queryByText('View Details')).not.toBeInTheDocument();
      expect(screen.queryByText('Send Email')).not.toBeInTheDocument();
      expect(screen.queryByText('Analytics')).not.toBeInTheDocument();
    });
  });

  describe('User Interactions', () => {
    it('should_navigateToDetails_when_cardClicked', async () => {
      const user = userEvent.setup();

      const { container } = renderWithRouter(<PartnerCard partner={mockPartner} />);

      const card = container.querySelector('.MuiCard-root') as HTMLElement;
      await user.click(card);

      expect(mockNavigate).toHaveBeenCalledWith('/organizer/partners/Test Company');
    });
  });

  describe('Tier Emojis', () => {
    it('should_displayStrategicEmoji_when_tierIsStrategic', () => {
      const strategicPartner = {
        ...mockPartner,
        partnershipLevel: 'STRATEGIC',
      };

      renderWithRouter(<PartnerCard partner={strategicPartner} />);

      expect(screen.getByText(/🏆/)).toBeInTheDocument();
    });

    it('should_displayPlatinumEmoji_when_tierIsPlatinum', () => {
      const platinumPartner = {
        ...mockPartner,
        partnershipLevel: 'PLATINUM',
      };

      renderWithRouter(<PartnerCard partner={platinumPartner} />);

      expect(screen.getByText(/💎/)).toBeInTheDocument();
    });

    it('should_displaySilverEmoji_when_tierIsSilver', () => {
      const silverPartner = {
        ...mockPartner,
        partnershipLevel: 'SILVER',
      };

      renderWithRouter(<PartnerCard partner={silverPartner} />);

      expect(screen.getByText(/🥈/)).toBeInTheDocument();
    });

    it('should_displayBronzeEmoji_when_tierIsBronze', () => {
      const bronzePartner = {
        ...mockPartner,
        partnershipLevel: 'BRONZE',
      };

      renderWithRouter(<PartnerCard partner={bronzePartner} />);

      expect(screen.getByText(/🥉/)).toBeInTheDocument();
    });
  });
});
