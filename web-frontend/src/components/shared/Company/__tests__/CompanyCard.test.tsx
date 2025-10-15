/**
 * CompanyCard Component Tests (RED Phase - TDD)
 *
 * Tests for individual company card display
 * - Logo display with CloudFront CDN
 * - Partner badge and verified status
 * - Click handlers for navigation
 *
 * Story: 2.5.1 - Company Management Frontend
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { userEvent } from '@testing-library/user-event';
import { CompanyCard } from '@/components/shared/Company/CompanyCard';
import type { CompanyListItem } from '@/types/company.types';

const mockCompany: CompanyListItem = {
  id: 'company-123',
  name: 'Acme Corporation',
  displayName: 'ACME Corp',
  logoUrl: 'https://cdn.batbern.ch/logos/acme.png',
  industry: 'Technology',
  location: { city: 'Bern', country: 'Switzerland' },
  isPartner: true,
  isVerified: true,
  associatedUserCount: 10
};

describe('CompanyCard Component', () => {
  it('should_displayCompanyLogo_when_logoUrlProvided', () => {
    // Test logo display from CDN
    render(<CompanyCard company={mockCompany} onClick={vi.fn()} />);

    const logo = screen.getByRole('img', { name: /acme/i });
    expect(logo).toHaveAttribute('src', mockCompany.logoUrl);
    expect(logo).toHaveAttribute('alt', 'ACME Corp logo');
  });

  it('should_displayFallbackIcon_when_logoUrlMissing', () => {
    // Test fallback when no logo
    const companyWithoutLogo = { ...mockCompany, logoUrl: undefined };
    render(<CompanyCard company={companyWithoutLogo} onClick={vi.fn()} />);

    // Should show fallback icon or placeholder
    expect(screen.getByTestId('company-logo-fallback')).toBeInTheDocument();
  });

  it('should_displayPartnerBadge_when_companyIsPartner', () => {
    // Test partner badge (⭐)
    render(<CompanyCard company={mockCompany} onClick={vi.fn()} />);

    const card = screen.getByTestId(`company-card-${mockCompany.id}`);
    expect(card).toHaveTextContent('⭐');
    expect(screen.getByLabelText(/partner/i)).toBeInTheDocument();
  });

  it('should_not_displayPartnerBadge_when_companyNotPartner', () => {
    // Test no partner badge for non-partners
    const nonPartnerCompany = { ...mockCompany, isPartner: false };
    render(<CompanyCard company={nonPartnerCompany} onClick={vi.fn()} />);

    const card = screen.getByTestId(`company-card-${mockCompany.id}`);
    expect(card).not.toHaveTextContent('⭐');
  });

  it('should_displayVerifiedBadge_when_companyVerified', () => {
    // Test verified status (✅)
    render(<CompanyCard company={mockCompany} onClick={vi.fn()} />);

    const card = screen.getByTestId(`company-card-${mockCompany.id}`);
    expect(card).toHaveTextContent('✅');
    expect(screen.getByLabelText(/verified/i)).toBeInTheDocument();
  });

  it('should_not_displayVerifiedBadge_when_companyNotVerified', () => {
    // Test no verified badge for unverified companies
    const unverifiedCompany = { ...mockCompany, isVerified: false };
    render(<CompanyCard company={unverifiedCompany} onClick={vi.fn()} />);

    const card = screen.getByTestId(`company-card-${mockCompany.id}`);
    expect(card).not.toHaveTextContent('✅');
  });

  it('should_displayCompanyName_when_rendered', () => {
    // Test company name display (legal name shown with "Legal:" prefix when different)
    render(<CompanyCard company={mockCompany} onClick={vi.fn()} />);

    expect(screen.getByText(/Legal:.*Acme Corporation/i)).toBeInTheDocument();
  });

  it('should_displayDisplayName_when_different', () => {
    // Test display name when different from legal name
    render(<CompanyCard company={mockCompany} onClick={vi.fn()} />);

    // Display name should be shown (ACME Corp)
    expect(screen.getByText(/ACME Corp/)).toBeInTheDocument();
  });

  it('should_displayIndustry_when_rendered', () => {
    // Test industry display
    render(<CompanyCard company={mockCompany} onClick={vi.fn()} />);

    expect(screen.getByText('Technology')).toBeInTheDocument();
  });

  it('should_displayLocation_when_rendered', () => {
    // Test location display
    render(<CompanyCard company={mockCompany} onClick={vi.fn()} />);

    expect(screen.getByText(/Bern.*Switzerland/i)).toBeInTheDocument();
  });

  it('should_displayAssociatedUsersCount_when_rendered', () => {
    // Test associated users count
    render(<CompanyCard company={mockCompany} onClick={vi.fn()} />);

    expect(screen.getByText(/10.*users?/i)).toBeInTheDocument();
  });

  it('should_callOnClick_when_cardClicked', async () => {
    // Test click handler
    const user = userEvent.setup();
    const onClick = vi.fn();

    render(<CompanyCard company={mockCompany} onClick={onClick} />);

    // Click the button inside the card (CardActionArea renders as button)
    const button = screen.getByRole('button', { name: /view details/i });
    await user.click(button);

    expect(onClick).toHaveBeenCalledWith(mockCompany.id);
  });

  it('should_haveAccessibleLabels_when_rendered', () => {
    // Test accessibility labels (on the button, not the card container)
    render(<CompanyCard company={mockCompany} onClick={vi.fn()} />);

    const button = screen.getByRole('button', { name: /view details/i });
    expect(button).toHaveAttribute('role', 'button');
    expect(button).toHaveAttribute('aria-label', expect.stringContaining('ACME'));
  });

  it('should_beFocusable_when_keyboard', () => {
    // Test keyboard accessibility (button is focusable)
    render(<CompanyCard company={mockCompany} onClick={vi.fn()} />);

    const button = screen.getByRole('button', { name: /view details/i });
    expect(button).toHaveAttribute('tabIndex', '0');
  });
});
