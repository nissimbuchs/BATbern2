/**
 * CompanyCard Component Tests (RED Phase - TDD)
 *
 * Tests for individual company card display
 * - Fallback logo display (logo not in CompanyListItem)
 * - Verified status badge
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
  industry: 'Technology',
  isVerified: true,
};

describe('CompanyCard Component', () => {
  it('should_displayFallbackIcon_always', () => {
    // CompanyListItem doesn't include logo, so fallback is always shown
    render(<CompanyCard company={mockCompany} onClick={vi.fn()} />);

    // Should show fallback icon
    expect(screen.getByTestId('company-logo-fallback')).toBeInTheDocument();
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
