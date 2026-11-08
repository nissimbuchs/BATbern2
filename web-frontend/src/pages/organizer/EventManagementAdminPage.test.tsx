/**
 * EventManagementAdminPage Tests (Story 10.1 - Task 1; on the real tree since 17.0c2)
 *
 * The page renders with its real tabs, hooks and services, real i18n (en), and the auth state
 * through the real `AuthContext` (`src/test/auth.tsx`). The API answers through the MSW harness;
 * only `window.matchMedia` (which jsdom lacks) is stubbed for the mobile layout.
 *
 * Tests:
 * - AC1: Renders the tabs: Event Types, Import Data, Task Templates
 * - AC1: ORGANIZER role guard — non-organizers see access denied, and nothing is loaded
 * - AC1: Tab index synced with ?tab=N URL param
 * - Mobile: bottom navigation instead of the tab strip, same URL behaviour
 */

import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes, useLocation } from 'react-router-dom';
import { vi, describe, it, expect, afterEach } from 'vitest';
import EventManagementAdminPage from './EventManagementAdminPage';
import { api, type ResponseBody } from '@/test/msw/api';
import { renderWithAuth, signedInAs, signedOut } from '@/test/auth';
import type { UseAuthReturn } from '@/contexts/AuthContext';

type EventTypeConfig = ResponseBody<'listEventTypes', 200>[number];
type TaskTemplate = ResponseBody<'listAllTemplates', 200>[number];

const eventTypeConfig = (
  type: EventTypeConfig['type'],
  overrides: Partial<EventTypeConfig> = {}
): EventTypeConfig => ({
  type,
  minSlots: 6,
  maxSlots: 8,
  slotDuration: 45,
  theoreticalSlotsAM: type === 'FULL_DAY',
  breakSlots: type === 'FULL_DAY' ? 2 : 1,
  lunchSlots: type === 'FULL_DAY' ? 1 : 0,
  defaultCapacity: 200,
  typicalStartTime: '13:00',
  typicalEndTime: '19:00',
  moderationStartDuration: 5,
  moderationEndDuration: 5,
  breakDuration: 20,
  lunchDuration: 60,
  aperitifSlots: 1,
  aperitifDuration: 90,
  aperitifPosition: 'end',
  ...overrides,
});

const taskTemplate = (overrides: Partial<TaskTemplate>): TaskTemplate => ({
  id: 'b7e1c2a0-0000-4000-8000-000000000001',
  name: 'Venue booking',
  triggerState: 'topic_selection',
  dueDateType: 'relative_to_event',
  dueDateOffsetDays: 90,
  isDefault: true,
  createdAt: '2026-01-10T09:00:00Z',
  updatedAt: '2026-01-10T09:00:00Z',
  ...overrides,
});

const givenAdminData = () => {
  api.on('listEventTypes', () => [
    eventTypeConfig('FULL_DAY', {
      defaultCapacity: 300,
      typicalStartTime: '09:00',
      typicalEndTime: '16:00',
    }),
    eventTypeConfig('EVENING', { minSlots: 3, maxSlots: 4, typicalStartTime: '16:00' }),
  ]);
  api.on('listAllTemplates', () => [
    taskTemplate({ name: 'Venue booking' }),
    taskTemplate({
      id: 'b7e1c2a0-0000-4000-8000-000000000002',
      name: 'Order wine for the apéro',
      triggerState: 'agenda_published',
      isDefault: false,
      createdByUsername: 'anna.keller',
    }),
  ]);
};

/** Shows the router location, so a test can assert the ?tab=N the page writes. */
const LocationProbe = () => {
  const location = useLocation();
  return <div data-testid="location">{`${location.pathname}${location.search}`}</div>;
};

const renderPage = (
  initialPath = '/organizer/admin',
  auth: UseAuthReturn = signedInAs('organizer')
) => {
  givenAdminData();
  return renderWithAuth(
    <Routes>
      <Route
        path="/organizer/admin"
        element={
          <>
            <EventManagementAdminPage />
            <LocationProbe />
          </>
        }
      />
    </Routes>,
    { auth, initialEntries: [initialPath] }
  );
};

/** The real Event Types tab: one card per configured event type. */
const expectEventTypesTab = async () => {
  expect(await screen.findByText('Full Day Event Preview')).toBeInTheDocument();
  expect(screen.getByText('Evening Event Preview')).toBeInTheDocument();
  expect(screen.getByText('Capacity: 300')).toBeInTheDocument();
};

/** The real Import Data tab: one import card per entity, its modals closed. */
const expectImportDataTab = async () => {
  for (const label of [
    'Import Events',
    'Import Sessions',
    'Import Companies',
    'Import Speakers',
    'Import Participants',
  ]) {
    expect(await screen.findByRole('button', { name: label })).toBeInTheDocument();
  }
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
};

/** The real Task Templates tab: default and custom templates from the API. */
const expectTaskTemplatesTab = async () => {
  expect(await screen.findByText('Venue booking')).toBeInTheDocument();
  expect(screen.getByText('Default Templates')).toBeInTheDocument();
  expect(screen.getByText('Custom Templates')).toBeInTheDocument();
  const custom = screen.getByTestId('custom-templates-list');
  expect(within(custom).getByText('Order wine for the apéro')).toBeInTheDocument();
  expect(within(custom).queryByText('Venue booking')).not.toBeInTheDocument();
};

describe('EventManagementAdminPage', () => {
  describe('AC1 — tab rendering', () => {
    it('renders 3 tabs: Event Types, Import Data, Task Templates', async () => {
      renderPage();
      expect(screen.getByRole('heading', { level: 1, name: 'Administration' })).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: /event types/i })).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: /import data/i })).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: /task templates/i })).toBeInTheDocument();
      await expectEventTypesTab();
    });

    it('shows Event Types tab content by default (tab 0)', async () => {
      renderPage();
      expect(screen.getByRole('tab', { name: /event types/i })).toHaveAttribute(
        'aria-selected',
        'true'
      );
      await expectEventTypesTab();
      expect(api.calls('listEventTypes')).toHaveLength(1);
      // Only the selected tab mounts: the other tabs load nothing.
      expect(api.calls('listAllTemplates')).toHaveLength(0);
    });

    it('shows Import Data tab content when ?tab=1', async () => {
      renderPage('/organizer/admin?tab=1');
      expect(screen.getByRole('tab', { name: /import data/i })).toHaveAttribute(
        'aria-selected',
        'true'
      );
      await expectImportDataTab();
      expect(screen.queryByText('Full Day Event Preview')).not.toBeInTheDocument();
    });

    it('shows Task Templates tab content when ?tab=2', async () => {
      renderPage('/organizer/admin?tab=2');
      await expectTaskTemplatesTab();
      expect(api.calls('listAllTemplates')).toHaveLength(1);
      expect(api.calls('listEventTypes')).toHaveLength(0);
    });

    it('switches tab on click', async () => {
      const user = userEvent.setup();
      renderPage();
      await expectEventTypesTab();

      await user.click(screen.getByRole('tab', { name: /import data/i }));

      await expectImportDataTab();
      expect(screen.queryByText('Full Day Event Preview')).not.toBeInTheDocument();
      expect(screen.getByTestId('location')).toHaveTextContent('/organizer/admin?tab=1');
    });

    it('should_renderScrollableTabStrip_when_manyTabs', () => {
      // The 9-tab strip overflows narrow viewports; it must be horizontally
      // scrollable (variant="scrollable") so every tab stays reachable on mobile.
      renderPage();
      const tabs = screen.getByTestId('admin-tabs');
      expect(within(tabs).getAllByRole('tab')).toHaveLength(9);
      // MUI renders a scrollable scroller element only for variant="scrollable".
      expect(tabs.querySelector('.MuiTabs-scrollableX')).toBeTruthy();
    });
  });

  describe('Mobile bottom navigation', () => {
    const originalMatchMedia = window.matchMedia;

    const installMobileMatchMedia = (viewportWidth: number) => {
      window.matchMedia = vi.fn((query: string) => {
        const maxMatch = /max-width:\s*([\d.]+)px/.exec(query);
        const minMatch = /min-width:\s*([\d.]+)px/.exec(query);
        let matches = false;
        if (maxMatch) matches = viewportWidth <= parseFloat(maxMatch[1]);
        else if (minMatch) matches = viewportWidth >= parseFloat(minMatch[1]);
        return {
          matches,
          media: query,
          onchange: null,
          addListener: vi.fn(),
          removeListener: vi.fn(),
          addEventListener: vi.fn(),
          removeEventListener: vi.fn(),
          dispatchEvent: vi.fn(),
        };
      }) as unknown as typeof window.matchMedia;
    };

    afterEach(() => {
      window.matchMedia = originalMatchMedia;
    });

    it('should_renderBottomNavigationInsteadOfTabStrip_when_mobileViewport', async () => {
      installMobileMatchMedia(375);
      renderPage();

      // The scrollable desktop tab strip is gone; the fixed bottom nav replaces it.
      expect(screen.queryByTestId('admin-tabs')).not.toBeInTheDocument();
      expect(screen.getByTestId('admin-bottom-nav')).toBeInTheDocument();
      // Tab content still renders below the nav.
      await expectEventTypesTab();
    });

    it('should_keepTabUrlBehaviour_when_bottomNavItemClicked', async () => {
      installMobileMatchMedia(375);
      const user = userEvent.setup();
      renderPage();

      await user.click(screen.getByRole('button', { name: /import data/i }));

      await expectImportDataTab();
      expect(screen.getByTestId('location')).toHaveTextContent('/organizer/admin?tab=1');
    });
  });

  describe('AC1 — role guard', () => {
    const expectAccessDenied = () => {
      expect(screen.getByText('Access Denied')).toBeInTheDocument();
      expect(screen.getByText('This page is for organizers only.')).toBeInTheDocument();
      expect(screen.queryByRole('tab', { name: /event types/i })).not.toBeInTheDocument();
      expect(screen.queryByTestId('admin-tabs')).not.toBeInTheDocument();
      // Nothing behind the guard is loaded.
      expect(api.allCalls()).toHaveLength(0);
    };

    it('shows access denied for non-organizer (speaker)', () => {
      renderPage('/organizer/admin', signedInAs('speaker'));
      expectAccessDenied();
    });

    it('shows access denied for partner role', () => {
      renderPage('/organizer/admin', signedInAs('partner'));
      expectAccessDenied();
    });

    it('shows access denied for attendee role and when signed out', () => {
      const { rerenderWithAuth } = renderPage('/organizer/admin', signedInAs('attendee'));
      expectAccessDenied();

      rerenderWithAuth(
        <Routes>
          <Route path="/organizer/admin" element={<EventManagementAdminPage />} />
        </Routes>,
        signedOut()
      );
      expectAccessDenied();
    });

    it('renders page for organizer role', async () => {
      renderPage('/organizer/admin', signedInAs('organizer'));
      expect(screen.queryByText('Access Denied')).not.toBeInTheDocument();
      expect(screen.getByRole('tab', { name: /event types/i })).toBeInTheDocument();
      await expectEventTypesTab();
      expect(api.calls('listEventTypes')).toHaveLength(1);
    });
  });
});
