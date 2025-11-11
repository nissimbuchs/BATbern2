/**
 * PartnerShowcaseCard Tests
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PartnerShowcaseCard } from './PartnerShowcaseCard';
import { ThemeProvider, createTheme } from '@mui/material/styles';

const theme = createTheme();

const renderWithTheme = (component: React.ReactElement) => {
  return render(<ThemeProvider theme={theme}>{component}</ThemeProvider>);
};

describe('PartnerShowcaseCard', () => {
  it('should_renderLogo_when_logoUrlProvided', () => {
    renderWithTheme(
      <PartnerShowcaseCard
        companyName="Test Company"
        logoUrl="https://example.com/logo.png"
        partnershipLevel="GOLD"
        partnershipStartDate="2023-01-15"
        website="https://example.com"
      />
    );

    const logo = screen.getByAltText('Test Company');
    expect(logo).toBeDefined();
    expect(logo.getAttribute('src')).toBe('https://example.com/logo.png');
  });

  it('should_renderInitials_when_noLogoProvided', () => {
    renderWithTheme(
      <PartnerShowcaseCard
        companyName="Test Company"
        partnershipLevel="PLATINUM"
        partnershipStartDate="2023-01-15"
        website="https://example.com"
      />
    );

    expect(screen.getByText('TE')).toBeDefined();
  });

  it('should_displayTierAndDate_horizontally', () => {
    renderWithTheme(
      <PartnerShowcaseCard
        companyName="Test Company"
        partnershipLevel="STRATEGIC"
        partnershipStartDate="2023-06-01"
        website="https://example.com"
      />
    );

    expect(screen.getByText(/🏆 Strategic/)).toBeDefined();
    expect(screen.getByText(/since Jun 2023/)).toBeDefined();
  });

  it('should_openWebsite_when_clicked', async () => {
    const user = userEvent.setup();
    const windowOpenSpy = vi.spyOn(window, 'open').mockImplementation(() => null);

    renderWithTheme(
      <PartnerShowcaseCard
        companyName="Test Company"
        partnershipLevel="GOLD"
        partnershipStartDate="2023-01-15"
        website="https://example.com"
      />
    );

    const card = screen.getByRole('button');
    await user.click(card);

    expect(windowOpenSpy).toHaveBeenCalledWith(
      'https://example.com',
      '_blank',
      'noopener,noreferrer'
    );

    windowOpenSpy.mockRestore();
  });

  it('should_beDisabled_when_noWebsiteProvided', () => {
    renderWithTheme(
      <PartnerShowcaseCard
        companyName="Test Company"
        partnershipLevel="SILVER"
        partnershipStartDate="2023-01-15"
      />
    );

    const card = screen.getByRole('button');
    expect(card.classList.contains('Mui-disabled')).toBe(true);
  });
});
