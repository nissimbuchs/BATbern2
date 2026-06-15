/**
 * SlotAssignmentPage redirect tests (Epic 14 Phase C — 14.C.5)
 *
 * The dedicated `/organizer/events/:eventCode/slot-assignment` route is retired.
 * The page now redirects to the in-tab Slots sub-view, preserving `?speakerId=`
 * as the in-tab speaker focus.
 */

import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { MemoryRouter, Routes, Route, useLocation } from 'react-router-dom';
import SlotAssignmentPage from '../SlotAssignmentPage';

// Reads the current router location so tests can assert the redirect target.
const LocationProbe: React.FC = () => {
  const location = useLocation();
  return (
    <div
      data-testid="location-probe"
      data-pathname={location.pathname}
      data-search={location.search}
    />
  );
};

const renderAt = (initialEntry: string) =>
  render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route
          path="/organizer/events/:eventCode/slot-assignment"
          element={<SlotAssignmentPage />}
        />
        <Route path="/organizer/events/:eventCode" element={<LocationProbe />} />
        <Route path="/organizer/events" element={<LocationProbe />} />
      </Routes>
    </MemoryRouter>
  );

describe('SlotAssignmentPage redirect (14.C.5)', () => {
  it('should_redirectToInTabSlotsView_when_openedWithoutSpeakerId', () => {
    renderAt('/organizer/events/BATbern142/slot-assignment');
    const probe = screen.getByTestId('location-probe');
    expect(probe.getAttribute('data-pathname')).toBe('/organizer/events/BATbern142');
    const search = probe.getAttribute('data-search') ?? '';
    expect(search).toContain('tab=speakers');
    expect(search).toContain('view=slots');
    expect(search).not.toContain('speakerId');
  });

  it('should_preserveSpeakerId_when_redirecting', () => {
    renderAt('/organizer/events/BATbern142/slot-assignment?speakerId=jane.smith');
    const probe = screen.getByTestId('location-probe');
    expect(probe.getAttribute('data-pathname')).toBe('/organizer/events/BATbern142');
    const search = probe.getAttribute('data-search') ?? '';
    expect(search).toContain('tab=speakers');
    expect(search).toContain('view=slots');
    expect(search).toContain('speakerId=jane.smith');
  });
});
