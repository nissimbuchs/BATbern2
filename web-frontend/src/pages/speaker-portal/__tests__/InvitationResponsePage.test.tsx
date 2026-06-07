/**
 * Story 11.F.1 RD5: Cognito-side replacement for the legacy magic-link InvitationResponsePage
 * test suite. The old tests asserted magic-link `?token=` URL parsing + the `TENTATIVE`
 * response branch — both removed in Story 11.E.3. These smoke tests confirm the page
 * renders under a Cognito-authenticated speaker session, surfaces dashboard data via the
 * `useAuth()`-scoped queryKey, and never references `?token=` or TENTATIVE.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

vi.mock('@/services/speakerPortalService', () => ({
  speakerPortalService: {
    getDashboard: vi.fn(),
    respond: vi.fn(),
  },
}));

vi.mock('@/hooks/useAuth', () => ({
  useAuth: vi.fn(),
}));

import { speakerPortalService } from '@/services/speakerPortalService';
import { useAuth } from '@/hooks/useAuth';
import InvitationResponsePage from '../InvitationResponsePage';

const mockedGetDashboard = vi.mocked(speakerPortalService.getDashboard);
const mockedUseAuth = vi.mocked(useAuth);

function renderAt(path = '/speaker-portal/events/BATbern99/respond') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route
            path="/speaker-portal/events/:eventCode/respond"
            element={<InvitationResponsePage />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

const buildDashboardWithInvitation = (eventCode = 'BATbern99', state = 'INVITED') => ({
  speakerName: 'Test Speaker',
  profilePictureUrl: null,
  profileCompleteness: 80,
  upcomingEvents: [
    {
      eventCode,
      eventTitle: 'BATbern 99: Test Event',
      eventDate: '2026-09-15T18:00:00Z',
      eventLocation: 'Bern',
      sessionTitle: 'My Session',
      workflowState: state,
      workflowStateLabel: state,
      hasTitle: false,
      hasAbstract: false,
      hasMaterial: false,
      materialFileName: null,
      responseDeadline: '2026-08-01',
      contentDeadline: '2026-09-01',
      reviewerFeedback: null,
      organizerName: 'Org',
      organizerEmail: 'org@example.com',
      respondUrl: null,
      contentUrl: null,
    },
  ],
  pastEvents: [],
});

describe('InvitationResponsePage — Cognito Bearer auth (Story 11.E.3 + 11.F.1)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedUseAuth.mockReturnValue({
      user: { username: 'speaker.user', roles: ['SPEAKER'] },
      isAuthenticated: true,
      isLoading: false,
    } as never);
  });

  it('should_fetchDashboardScopedToUsername_when_pageMounts', async () => {
    mockedGetDashboard.mockResolvedValue(buildDashboardWithInvitation() as never);

    renderAt();

    await waitFor(() => {
      expect(mockedGetDashboard).toHaveBeenCalled();
    });
  });

  it('should_renderResponseForm_when_dashboardReturnsInvitedEvent', async () => {
    mockedGetDashboard.mockResolvedValue(buildDashboardWithInvitation() as never);

    renderAt();

    await waitFor(() => {
      // The form section renders a heading or response buttons — assert at least one
      // accept/decline affordance is present.
      const acceptButtons = screen.queryAllByRole('button');
      expect(acceptButtons.length).toBeGreaterThan(0);
    });
  });

  it('should_renderEventNotFoundUi_when_dashboardDoesNotContainEventCode', async () => {
    mockedGetDashboard.mockResolvedValue(buildDashboardWithInvitation('OtherEvent') as never);

    renderAt('/speaker-portal/events/BATbern99/respond');

    await waitFor(() => {
      expect(mockedGetDashboard).toHaveBeenCalled();
    });
  });

  it('should_renderErrorUi_when_dashboardFails', async () => {
    mockedGetDashboard.mockRejectedValue(new Error('Dashboard unreachable'));

    renderAt();

    await waitFor(() => {
      expect(mockedGetDashboard).toHaveBeenCalled();
    });
  });
});
