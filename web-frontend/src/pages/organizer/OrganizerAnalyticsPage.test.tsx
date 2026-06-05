/**
 * OrganizerAnalyticsPage Tests
 * Story 10.5: Analytics Dashboard.
 *
 * Page-level test covering the responsive tab strip added for organizer mobile
 * support — the 4-tab strip must be horizontally scrollable so every tab stays
 * reachable on narrow viewports.
 */

import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import OrganizerAnalyticsPage from './OrganizerAnalyticsPage';

// Mock the tab content components to isolate page-level behaviour and avoid
// transitive data-hook / chart dependencies.
vi.mock('@/components/organizer/Analytics/OverviewTab', () => ({
  default: () => <div data-testid="overview-tab-content">Overview</div>,
}));
vi.mock('@/components/organizer/Analytics/AttendanceTab', () => ({
  default: () => <div data-testid="attendance-tab-content">Attendance</div>,
}));
vi.mock('@/components/organizer/Analytics/TopicsTab', () => ({
  default: () => <div data-testid="topics-tab-content">Topics</div>,
}));
vi.mock('@/components/organizer/Analytics/CompaniesTab', () => ({
  default: () => <div data-testid="companies-tab-content">Companies</div>,
}));

// Mock i18n — return the key (or fallback) so assertions stay locale-agnostic.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, fallback?: string) => fallback ?? key,
    i18n: { changeLanguage: vi.fn(), language: 'en' },
  }),
}));

describe('OrganizerAnalyticsPage', () => {
  it('should_renderTabStrip_when_rendered', () => {
    render(<OrganizerAnalyticsPage />);
    expect(screen.getAllByRole('tab').length).toBeGreaterThanOrEqual(4);
    expect(screen.getByTestId('overview-tab-content')).toBeInTheDocument();
  });

  it('should_renderScrollableTabStrip_when_rendered', () => {
    // The tab strip overflows narrow viewports; it must be horizontally
    // scrollable (variant="scrollable") so every tab stays reachable on mobile.
    render(<OrganizerAnalyticsPage />);
    // MUI renders the .MuiTabs-scrollableX scroller element only for variant="scrollable".
    expect(document.querySelector('.MuiTabs-scrollableX')).toBeTruthy();
  });
});
