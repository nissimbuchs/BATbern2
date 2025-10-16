/**
 * CompanyList Component Tests (RED Phase - TDD)
 *
 * Tests for CompanyList component covering:
 * - AC1: Company List Display (grid/list toggle, partner badge, verified status)
 * - AC2: Search & Filters integration
 *
 * Story: 2.5.1 - Company Management Frontend
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { userEvent } from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { CompanyList } from '@/components/shared/Company/CompanyList';
import type { CompanyListItem } from '@/types/company.types';

// Test data - updated to match backend CompanyListItem schema
const mockCompanies: CompanyListItem[] = [
  {
    id: '1',
    name: 'Test Company 1',
    displayName: 'Test Corp 1',
    industry: 'Technology',
    isVerified: true,
    // Removed: logoUrl, location, isPartner, associatedUserCount (not in backend schema)
  },
  {
    id: '2',
    name: 'Test Company 2',
    industry: 'Finance',
    isVerified: false,
    // Removed: logoUrl, location, isPartner, associatedUserCount (not in backend schema)
  }
];

// Test wrapper with React Query
const createTestWrapper = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false }
    }
  });

  return function Wrapper({ children }: { children: React.ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
};

describe('CompanyList Component', () => {
  describe('AC1: Company List Display', () => {
    it('should_renderCompanyList_when_companiesLoaded', async () => {
      // Test 1.1: Display paginated company list
      render(
        <CompanyList
          companies={mockCompanies}
          isLoading={false}
          viewMode="grid"
          onViewModeToggle={vi.fn()}
        />,
        { wrapper: createTestWrapper() }
      );

      // Should display both companies (by displayName or name)
      expect(screen.getByText('Test Corp 1')).toBeInTheDocument();
      expect(screen.getByText('Test Company 2')).toBeInTheDocument();

      // Should display industry
      expect(screen.getByText('Technology')).toBeInTheDocument();
      expect(screen.getByText('Finance')).toBeInTheDocument();

      // Location removed - no longer in backend schema
    });

    // Partner badge test removed - isPartner field no longer in backend schema

    it('should_displayVerifiedStatus_when_companyVerified', () => {
      // Test 1.3: Show verified status (✅) when applicable
      render(
        <CompanyList
          companies={mockCompanies}
          isLoading={false}
          viewMode="grid"
          onViewModeToggle={vi.fn()}
        />,
        { wrapper: createTestWrapper() }
      );

      // First company should have verified badge
      const company1Card = screen.getByText('Test Corp 1').closest('[data-testid^="company-card"]');
      expect(company1Card).toHaveTextContent('✅');

      // Second company should NOT have verified badge
      const company2Card = screen.getByText('Test Company 2').closest('[data-testid^="company-card"]');
      expect(company2Card).not.toHaveTextContent('✅');
    });

    it('should_toggleViewMode_when_gridListButtonClicked', async () => {
      // Test 1.4: Toggle between grid and list view
      const user = userEvent.setup();
      const onViewModeToggle = vi.fn();

      render(
        <CompanyList
          companies={mockCompanies}
          isLoading={false}
          viewMode="grid"
          onViewModeToggle={onViewModeToggle}
        />,
        { wrapper: createTestWrapper() }
      );

      // Find toggle button
      const toggleButton = screen.getByRole('button', { name: /view mode|toggle/i });
      await user.click(toggleButton);

      // Should call toggle function
      expect(onViewModeToggle).toHaveBeenCalledTimes(1);
    });

    // Associated users count test removed - associatedUserCount field no longer in backend schema

    it('should_displayLoadingSkeleton_when_loading', () => {
      // Loading state test
      render(
        <CompanyList
          companies={[]}
          isLoading={true}
          viewMode="grid"
          onViewModeToggle={vi.fn()}
        />,
        { wrapper: createTestWrapper() }
      );

      // Should show skeleton loaders
      const skeletons = screen.getAllByTestId(/skeleton/i);
      expect(skeletons.length).toBeGreaterThan(0);
    });

    it('should_displayEmptyState_when_noCompanies', () => {
      // Empty state test
      render(
        <CompanyList
          companies={[]}
          isLoading={false}
          viewMode="grid"
          onViewModeToggle={vi.fn()}
        />,
        { wrapper: createTestWrapper() }
      );

      // Should show empty state message
      expect(screen.getByText(/no companies found/i)).toBeInTheDocument();
    });
  });

  describe('AC1: Grid vs List Layout', () => {
    it('should_displayGridLayout_when_viewModeIsGrid', () => {
      // Grid view layout test
      render(
        <CompanyList
          companies={mockCompanies}
          isLoading={false}
          viewMode="grid"
          onViewModeToggle={vi.fn()}
        />,
        { wrapper: createTestWrapper() }
      );

      const container = screen.getByTestId('company-list-container');
      expect(container).toHaveAttribute('data-view-mode', 'grid');
    });

    it('should_displayListLayout_when_viewModeIsList', () => {
      // List view layout test
      render(
        <CompanyList
          companies={mockCompanies}
          isLoading={false}
          viewMode="list"
          onViewModeToggle={vi.fn()}
        />,
        { wrapper: createTestWrapper() }
      );

      const container = screen.getByTestId('company-list-container');
      expect(container).toHaveAttribute('data-view-mode', 'list');
    });
  });
});
