/**
 * EventManagementAdminPage Tests (Story 10.1 - Task 1, RED Phase)
 *
 * Tests:
 * - AC1: Renders 3 tabs: Event Types, Import Data, Task Templates
 * - AC1: ORGANIZER role guard — non-organizers see access denied
 * - AC1: Tab index synced with ?tab=N URL param
 * - AC1: Administration item in UserMenuDropdown navigates to /organizer/admin
 */

import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import EventManagementAdminPage from './EventManagementAdminPage';

// Mock child tab components to isolate page-level tests
vi.mock('@/components/organizer/Admin/EventTypesTab', () => ({
  EventTypesTab: () => <div data-testid="event-types-tab-content">EventTypes</div>,
}));
vi.mock('@/components/organizer/Admin/ImportDataTab', () => ({
  ImportDataTab: () => <div data-testid="import-data-tab-content">ImportData</div>,
}));
vi.mock('@/components/organizer/Admin/TaskTemplatesTab', () => ({
  TaskTemplatesTab: () => <div data-testid="task-templates-tab-content">TaskTemplates</div>,
}));

// Mock i18n
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, fallback?: string) => fallback ?? key,
    i18n: { changeLanguage: vi.fn() },
  }),
}));

let mockUserRole = 'organizer';
vi.mock('@/hooks/useAuth', () => ({
  useAuth: () => ({
    isAuthenticated: true,
    user: { username: 'testorganizer', role: mockUserRole, email: 'org@test.com' },
    login: vi.fn(),
    logout: vi.fn(),
    loading: false,
  }),
}));

const createQueryClient = () => new QueryClient({ defaultOptions: { queries: { retry: false } } });

const renderPage = (initialPath = '/organizer/admin') =>
  render(
    <QueryClientProvider client={createQueryClient()}>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path="/organizer/admin" element={<EventManagementAdminPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );

describe('EventManagementAdminPage', () => {
  beforeEach(() => {
    mockUserRole = 'organizer';
  });

  describe('AC1 — tab rendering', () => {
    it('renders 3 tabs: Event Types, Import Data, Task Templates', () => {
      renderPage();
      expect(screen.getByRole('tab', { name: /event types/i })).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: /import data/i })).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: /task templates/i })).toBeInTheDocument();
    });

    it('shows Event Types tab content by default (tab 0)', () => {
      renderPage();
      expect(screen.getByTestId('event-types-tab-content')).toBeInTheDocument();
    });

    it('shows Import Data tab content when ?tab=1', () => {
      renderPage('/organizer/admin?tab=1');
      expect(screen.getByTestId('import-data-tab-content')).toBeInTheDocument();
    });

    it('shows Task Templates tab content when ?tab=2', () => {
      renderPage('/organizer/admin?tab=2');
      expect(screen.getByTestId('task-templates-tab-content')).toBeInTheDocument();
    });

    it('switches tab on click', async () => {
      const user = userEvent.setup();
      renderPage();
      await user.click(screen.getByRole('tab', { name: /import data/i }));
      expect(screen.getByTestId('import-data-tab-content')).toBeInTheDocument();
    });
  });

  describe('AC1 — role guard', () => {
    it('shows access denied for non-organizer (speaker)', () => {
      mockUserRole = 'speaker';
      renderPage();
      expect(screen.getByText(/access denied/i)).toBeInTheDocument();
      expect(screen.queryByRole('tab', { name: /event types/i })).not.toBeInTheDocument();
    });

    it('shows access denied for partner role', () => {
      mockUserRole = 'partner';
      renderPage();
      expect(screen.getByText(/access denied/i)).toBeInTheDocument();
    });

    it('renders page for organizer role', () => {
      mockUserRole = 'organizer';
      renderPage();
      expect(screen.getByRole('tab', { name: /event types/i })).toBeInTheDocument();
    });
  });
});
