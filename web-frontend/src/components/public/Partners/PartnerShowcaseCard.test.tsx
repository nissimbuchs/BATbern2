/**
 * PartnerShowcaseCard Tests
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PartnerShowcaseCard } from './PartnerShowcaseCard';

describe('PartnerShowcaseCard', () => {
  it('should_renderLogo_when_logoUrlProvided', () => {
    render(
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
    render(
      <PartnerShowcaseCard
        companyName="Test Company"
        partnershipLevel="PLATINUM"
        partnershipStartDate="2023-01-15"
        website="https://example.com"
      />
    );

    expect(screen.getByText('TE')).toBeDefined();
  });

  it('should_displayDate', () => {
    render(
      <PartnerShowcaseCard
        companyName="Test Company"
        partnershipLevel="STRATEGIC"
        partnershipStartDate="2023-06-01"
        website="https://example.com"
      />
    );

    expect(screen.getByText(/since Jun 2023/)).toBeDefined();
  });

  it('should_openWebsite_when_clicked', async () => {
    const user = userEvent.setup();
    const windowOpenSpy = vi.spyOn(window, 'open').mockImplementation(() => null);

    const { container } = render(
      <PartnerShowcaseCard
        companyName="Test Company"
        partnershipLevel="GOLD"
        partnershipStartDate="2023-01-15"
        website="https://example.com"
      />
    );

    // Click on the card div
    const card = container.querySelector('.cursor-pointer');
    expect(card).toBeDefined();
    await user.click(card!);

    expect(windowOpenSpy).toHaveBeenCalledWith(
      'https://example.com',
      '_blank',
      'noopener,noreferrer'
    );

    windowOpenSpy.mockRestore();
  });

  it('should_showDisabledStyle_when_noWebsiteProvided', () => {
    const { container } = render(
      <PartnerShowcaseCard
        companyName="Test Company"
        partnershipLevel="SILVER"
        partnershipStartDate="2023-01-15"
      />
    );

    const card = container.firstChild;
    expect(card).toBeDefined();
    // Check for opacity-70 and cursor-default classes
    expect((card as HTMLElement).className).toContain('opacity-70');
    expect((card as HTMLElement).className).toContain('cursor-default');
  });
});
