/**
 * PartnerPortalLayout tests
 * Story 8.0: AC3
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { PartnerPortalLayout } from './PartnerPortalLayout';

// Mock react-i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        'portal.nav.myCompany': 'My Company',
        'portal.nav.analytics': 'Analytics',
        'portal.nav.topics': 'Topics',
      };
      return translations[key] ?? key;
    },
  }),
}));

const renderWithRouter = (initialPath = '/partners/company') => {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <PartnerPortalLayout />
    </MemoryRouter>
  );
};

describe('PartnerPortalLayout', () => {
  it('should_renderThreeNavTabs_when_mounted', () => {
    renderWithRouter();

    expect(screen.getByRole('tab', { name: 'My Company' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Analytics' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Topics' })).toBeInTheDocument();
  });

  it('should_highlightMyCompanyTab_when_pathIsPartnersCompany', () => {
    renderWithRouter('/partners/company');

    const myCompanyTab = screen.getByRole('tab', { name: 'My Company' });
    expect(myCompanyTab).toHaveAttribute('aria-selected', 'true');
  });

  it('should_highlightAnalyticsTab_when_pathIsPartnersAnalytics', () => {
    renderWithRouter('/partners/analytics');

    const analyticsTab = screen.getByRole('tab', { name: 'Analytics' });
    expect(analyticsTab).toHaveAttribute('aria-selected', 'true');
  });

  it('should_highlightTopicsTab_when_pathIsPartnersTopics', () => {
    renderWithRouter('/partners/topics');

    const topicsTab = screen.getByRole('tab', { name: 'Topics' });
    expect(topicsTab).toHaveAttribute('aria-selected', 'true');
  });
});
