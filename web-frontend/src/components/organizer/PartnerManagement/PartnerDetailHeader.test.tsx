/**
 * PartnerDetailHeader Component Tests (RED Phase -> GREEN Phase)
 *
 * TDD tests for Partner Detail Header component
 * Story 2.8.2: Partner Detail View
 *
 * Test Scenarios - AC1:
 * - Company logo display with CloudFront CDN
 * - Fallback to initials avatar when logo missing
 * - Partnership tier badge with emoji
 * - Company name, industry, website, location
 * - Engagement bar placeholder
 * - Action buttons (Edit, Add Note, disabled Epic 8 buttons)
 * - Back button navigation
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PartnerDetailHeader } from './PartnerDetailHeader';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import { BrowserRouter } from 'react-router-dom';
import type { PartnerResponse } from '@/services/api/partnerApi';

// Mock react-router-dom navigate
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

// Mock partner modal store
const mockOpenEditModal = vi.fn();
vi.mock('@/stores/partnerModalStore', () => ({
  usePartnerModalStore: () => ({
    openEditModal: mockOpenEditModal,
  }),
}));

const theme = createTheme();

// Helper to render with providers
const renderWithProviders = (component: React.ReactElement) => {
  return render(
    <BrowserRouter>
      <I18nextProvider i18n={i18n}>
        <ThemeProvider theme={theme}>{component}</ThemeProvider>
      </I18nextProvider>
    </BrowserRouter>
  );
};

// Mock partner data
const mockPartner: PartnerResponse = {
  id: '123e4567-e89b-12d3-a456-426614174000',
  companyName: 'GoogleZH',
  partnershipLevel: 'STRATEGIC',
  partnershipStartDate: '2024-01-01',
  isActive: true,
  company: {
    companyName: 'GoogleZH',
    displayName: 'Google Zurich',
    name: 'Google Zurich',
    industry: 'Technology',
    website: 'https://www.google.ch',
    location: 'Zurich, ZH',
    logoUrl: 'https://cdn.batbern.ch/logos/google-zh.png',
  },
};

const mockPartnerWithoutLogo: PartnerResponse = {
  ...mockPartner,
  company: {
    ...mockPartner.company!,
    logoUrl: undefined,
  },
};

describe('PartnerDetailHeader Component', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    await i18n.changeLanguage('en');
  });

  describe('AC1.1: Company Logo Display', () => {
    // Test 1.2: should_displayCompanyLogo_when_logoExists
    it('should_displayCompanyLogo_when_logoExists', async () => {
      await act(async () => {
        renderWithProviders(<PartnerDetailHeader partner={mockPartner} />);
      });

      const logo = screen.getByRole('img', { name: /google zurich/i });
      expect(logo).toBeInTheDocument();
      expect(logo).toHaveAttribute('src', 'https://cdn.batbern.ch/logos/google-zh.png');
    });

    // Test 1.3: should_displayInitialsAvatar_when_logoMissing
    it('should_displayInitialsAvatar_when_logoMissing', async () => {
      await act(async () => {
        renderWithProviders(<PartnerDetailHeader partner={mockPartnerWithoutLogo} />);
      });

      // Should show initials "GZ" for "Google Zurich"
      expect(screen.getByText('GZ')).toBeInTheDocument();
    });
  });

  describe('AC1.2: Partnership Tier Badge', () => {
    // Test 1.4: should_displayTierBadge_when_partnershipLevelSet
    it('should_displayTierBadge_when_partnershipLevelSet', async () => {
      await act(async () => {
        renderWithProviders(<PartnerDetailHeader partner={mockPartner} />);
      });

      expect(screen.getByText(/🏆/)).toBeInTheDocument();
      expect(screen.getByText(/STRATEGIC/i)).toBeInTheDocument();
    });

    it('should_displayPlatinumBadge_when_tierPlatinum', async () => {
      const platinumPartner = { ...mockPartner, partnershipLevel: 'PLATINUM' as const };
      await act(async () => {
        renderWithProviders(<PartnerDetailHeader partner={platinumPartner} />);
      });

      expect(screen.getByText(/💎/)).toBeInTheDocument();
      expect(screen.getByText(/PLATINUM/i)).toBeInTheDocument();
    });
  });

  describe('AC1.3: Company Information', () => {
    // Test 1.5: should_displayEnrichedCompanyData_when_includeUsed
    it('should_displayEnrichedCompanyData_when_includeUsed', async () => {
      await act(async () => {
        renderWithProviders(<PartnerDetailHeader partner={mockPartner} />);
      });

      expect(screen.getByText('Google Zurich')).toBeInTheDocument();
      expect(screen.getByText(/Technology/i)).toBeInTheDocument();
      expect(screen.getByText(/Zurich, ZH/i)).toBeInTheDocument();
    });

    it('should_displayWebsiteLink_when_websiteExists', async () => {
      await act(async () => {
        renderWithProviders(<PartnerDetailHeader partner={mockPartner} />);
      });

      const websiteLink = screen.getByRole('link', { name: /google\.ch/i });
      expect(websiteLink).toHaveAttribute('href', 'https://www.google.ch');
      expect(websiteLink).toHaveAttribute('target', '_blank');
    });
  });

  describe('AC1.5: Action Buttons', () => {
    // Test 1.8: should_openEditModal_when_editButtonClicked
    it('should_openEditModal_when_editButtonClicked', async () => {
      const user = userEvent.setup();
      await act(async () => {
        renderWithProviders(<PartnerDetailHeader partner={mockPartner} />);
      });

      const editButton = screen.getByRole('button', { name: /Edit Partner/i });
      await user.click(editButton);

      expect(mockOpenEditModal).toHaveBeenCalledWith(mockPartner);
    });

    it('should_haveAddNoteButton_when_rendered', async () => {
      await act(async () => {
        renderWithProviders(<PartnerDetailHeader partner={mockPartner} />);
      });

      const addNoteButton = screen.getByRole('button', { name: /Add Note/i });
      expect(addNoteButton).toBeInTheDocument();
      expect(addNoteButton).not.toBeDisabled();
    });
  });

  describe('AC1.6: Back Button Navigation', () => {
    // Back navigation is handled by PartnerDetailScreen via Breadcrumbs — not by the header
    it('should_notRenderBackButton_because_parentHandlesNavigation', async () => {
      await act(async () => {
        renderWithProviders(<PartnerDetailHeader partner={mockPartner} />);
      });

      expect(screen.queryByRole('button', { name: /back/i })).not.toBeInTheDocument();
    });
  });

  describe('Edge Cases', () => {
    it('should_handleMissingCompanyData_when_notIncluded', async () => {
      const partnerWithoutCompany = { ...mockPartner, company: undefined };
      await act(async () => {
        renderWithProviders(<PartnerDetailHeader partner={partnerWithoutCompany} />);
      });

      // Should still render with companyName
      expect(screen.getByText('GoogleZH')).toBeInTheDocument();
    });
  });
});
