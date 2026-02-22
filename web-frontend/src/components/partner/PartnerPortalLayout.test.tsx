/**
 * PartnerPortalLayout tests
 * Story 8.0: AC3 — layout now renders Outlet only (nav is in top AppHeader)
 */
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { PartnerPortalLayout } from './PartnerPortalLayout';

const renderWithRouter = (initialPath = '/partners/company') => {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route element={<PartnerPortalLayout />}>
          <Route path="/partners/company" element={<div>Company Content</div>} />
          <Route path="/partners/topics" element={<div>Topics Content</div>} />
        </Route>
      </Routes>
    </MemoryRouter>
  );
};

describe('PartnerPortalLayout', () => {
  it('should_renderOutletContent_when_mounted', () => {
    renderWithRouter('/partners/company');
    expect(screen.getByText('Company Content')).toBeInTheDocument();
  });

  it('should_renderTopicsContent_when_pathIsPartnerTopics', () => {
    renderWithRouter('/partners/topics');
    expect(screen.getByText('Topics Content')).toBeInTheDocument();
  });

  it('should_notRenderSecondaryTabNav_when_mounted', () => {
    renderWithRouter('/partners/company');
    // Secondary tab bar was removed — no MUI Tabs in layout
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument();
  });
});
