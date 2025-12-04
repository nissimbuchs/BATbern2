/**
 * CompanyCard Component Tests
 * Story 2.5.1 - Company Management Frontend
 *
 * Tests for CompanyCard component covering rendering and interactions
 */

import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import userEvent from '@testing-library/user-event';
import { CompanyCard } from './CompanyCard';
import type { CompanyListItem } from '@/types/company.types';

// Mock translation
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, params?: Record<string, string>) => {
      if (key === 'company.viewDetails') return `View details for ${params?.name || 'company'}`;
      if (key === 'company.badges.verified') return 'Verified';
      if (key === 'company.fields.legalName') return `Legal name: ${params?.name || ''}`;
      return key;
    },
  }),
}));

const mockCompany: CompanyListItem = {
  name: 'test-company',
  displayName: 'Test Company',
  industry: 'Technology',
  website: 'https://www.testcompany.com',
  isVerified: true,
  logo: {
    url: 'https://example.com/logo.png',
  },
};

describe('CompanyCard Component', () => {
  describe('Rendering', () => {
    it('should_renderCompanyName_when_companyProvided', () => {
      const mockOnClick = vi.fn();

      render(<CompanyCard company={mockCompany} onClick={mockOnClick} />);

      expect(screen.getByText('Test Company')).toBeInTheDocument();
    });

    it('should_renderCompanyLogo_when_logoExists', () => {
      const mockOnClick = vi.fn();

      render(<CompanyCard company={mockCompany} onClick={mockOnClick} />);

      const logo = screen.getByTestId('company-logo');
      expect(logo).toBeInTheDocument();
      expect(logo).toHaveAttribute('src', 'https://example.com/logo.png');
    });

    it('should_renderFallbackIcon_when_noLogo', () => {
      const mockOnClick = vi.fn();
      const companyWithoutLogo = { ...mockCompany, logo: undefined };

      render(<CompanyCard company={companyWithoutLogo} onClick={mockOnClick} />);

      expect(screen.getByTestId('company-logo-fallback')).toBeInTheDocument();
    });

    it('should_renderVerifiedBadge_when_companyIsVerified', () => {
      const mockOnClick = vi.fn();

      render(<CompanyCard company={mockCompany} onClick={mockOnClick} />);

      expect(screen.getByText(/Verified/)).toBeInTheDocument();
    });

    it('should_notRenderVerifiedBadge_when_companyNotVerified', () => {
      const mockOnClick = vi.fn();
      const unverifiedCompany = { ...mockCompany, isVerified: false };

      render(<CompanyCard company={unverifiedCompany} onClick={mockOnClick} />);

      expect(screen.queryByText(/Verified/)).not.toBeInTheDocument();
    });

    it('should_renderIndustry_when_industryExists', () => {
      const mockOnClick = vi.fn();

      render(<CompanyCard company={mockCompany} onClick={mockOnClick} />);

      expect(screen.getByText('Technology')).toBeInTheDocument();
    });

    it('should_renderWebsite_when_websiteExists', () => {
      const mockOnClick = vi.fn();

      render(<CompanyCard company={mockCompany} onClick={mockOnClick} />);

      const websiteLink = screen.getByText('testcompany.com');
      expect(websiteLink).toBeInTheDocument();
      expect(websiteLink).toHaveAttribute('href', 'https://www.testcompany.com');
    });
  });

  describe('User Interactions', () => {
    it('should_callOnClick_when_cardClicked', async () => {
      const user = userEvent.setup();
      const mockOnClick = vi.fn();

      render(<CompanyCard company={mockCompany} onClick={mockOnClick} />);

      const card = screen.getByRole('button');
      await user.click(card);

      expect(mockOnClick).toHaveBeenCalledWith('test-company');
    });

    it('should_callOnClick_when_enterKeyPressed', async () => {
      const user = userEvent.setup();
      const mockOnClick = vi.fn();

      render(<CompanyCard company={mockCompany} onClick={mockOnClick} />);

      const card = screen.getByRole('button');
      card.focus();
      await user.keyboard('{Enter}');

      expect(mockOnClick).toHaveBeenCalledWith('test-company');
    });

    it('should_callOnClick_when_spaceKeyPressed', async () => {
      const user = userEvent.setup();
      const mockOnClick = vi.fn();

      render(<CompanyCard company={mockCompany} onClick={mockOnClick} />);

      const card = screen.getByRole('button');
      card.focus();
      await user.keyboard(' ');

      expect(mockOnClick).toHaveBeenCalledWith('test-company');
    });
  });

  describe('View Modes', () => {
    it('should_renderInGridMode_when_viewModeIsGrid', () => {
      const mockOnClick = vi.fn();

      render(<CompanyCard company={mockCompany} onClick={mockOnClick} viewMode="grid" />);

      expect(screen.getByTestId('company-card-test-company')).toBeInTheDocument();
    });

    it('should_renderInListMode_when_viewModeIsList', () => {
      const mockOnClick = vi.fn();

      render(<CompanyCard company={mockCompany} onClick={mockOnClick} viewMode="list" />);

      expect(screen.getByTestId('company-card-test-company')).toBeInTheDocument();
    });
  });
});
