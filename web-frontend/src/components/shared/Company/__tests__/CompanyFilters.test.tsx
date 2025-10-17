/**
 * CompanyFilters Component Tests (RED Phase)
 *
 * Tests for filter panel with URL persistence
 * AC2: Search & Filters
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, useSearchParams } from 'react-router-dom';
import userEvent from '@testing-library/user-event';
import CompanyFilters from '@/components/shared/Company/CompanyFilters';

// Mock useSearchParams to test URL persistence
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useSearchParams: vi.fn(() => [new URLSearchParams(), vi.fn()]),
  };
});

const renderWithRouter = (ui: React.ReactElement, { route = '/' } = {}) => {
  return render(
    <MemoryRouter initialEntries={[route]}>
      {ui}
    </MemoryRouter>
  );
};

describe('CompanyFilters Component', () => {
  const mockOnFilterChange = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Filter Controls Rendering', () => {
    it('should_renderAllFilterOptions_when_componentMounted', () => {
      render(<CompanyFilters onFilterChange={mockOnFilterChange} />);

      // Partner filter removed - no longer supported by backend

      // Should have verification filter
      expect(screen.getByRole('checkbox', { name: /verified companies only/i })).toBeInTheDocument();

      // Should have industry select
      expect(screen.getByRole('combobox', { name: /industry/i })).toBeInTheDocument();
    });

    it('should_renderClearFiltersButton_when_componentMounted', () => {
      render(<CompanyFilters onFilterChange={mockOnFilterChange} />);

      expect(screen.getByRole('button', { name: /clear all filters/i })).toBeInTheDocument();
    });

    it('should_displayFilterCount_when_filtersApplied', () => {
      render(
        <CompanyFilters
          onFilterChange={mockOnFilterChange}
          initialFilters={{ isVerified: true }}
        />
      );

      // Should show badge with count of active filters (only 1 since isPartner removed)
      expect(screen.getByText('1')).toBeInTheDocument();
    });
  });

  // Partner Filter removed - no longer supported by backend

  describe('Verification Filter', () => {
    it('should_callOnFilterChange_when_verifiedCheckboxClicked', async () => {
      const user = userEvent.setup();
      render(<CompanyFilters onFilterChange={mockOnFilterChange} />);

      const verifiedCheckbox = screen.getByRole('checkbox', { name: /verified companies only/i });
      await user.click(verifiedCheckbox);

      expect(mockOnFilterChange).toHaveBeenCalledWith(expect.objectContaining({
        isVerified: true
      }));
    });
  });

  describe('Industry Filter', () => {
    it('should_displayIndustryOptions_when_selectClicked', async () => {
      const user = userEvent.setup();
      render(<CompanyFilters onFilterChange={mockOnFilterChange} />);

      const industrySelect = screen.getByRole('combobox', { name: /industry/i });
      await user.click(industrySelect);

      // Should show industry options
      await waitFor(() => {
        expect(screen.getByRole('option', { name: /cloud computing/i })).toBeInTheDocument();
        expect(screen.getByRole('option', { name: /devops/i })).toBeInTheDocument();
        expect(screen.getByRole('option', { name: /financial services/i })).toBeInTheDocument();
      });
    });

    it('should_callOnFilterChange_when_industrySelected', async () => {
      const user = userEvent.setup();
      render(<CompanyFilters onFilterChange={mockOnFilterChange} />);

      const industrySelect = screen.getByRole('combobox', { name: /industry/i });
      await user.click(industrySelect);

      const cloudOption = await screen.findByRole('option', { name: /cloud computing/i });
      await user.click(cloudOption);

      expect(mockOnFilterChange).toHaveBeenCalledWith(expect.objectContaining({
        industry: 'Cloud Computing'
      }));
    });
  });

  describe('Clear All Filters', () => {
    it('should_clearAllFilters_when_clearButtonClicked', async () => {
      const user = userEvent.setup();
      render(
        <CompanyFilters
          onFilterChange={mockOnFilterChange}
          initialFilters={{ isVerified: true, industry: 'Cloud Computing' }}
        />
      );

      const clearButton = screen.getByRole('button', { name: /clear all filters/i });
      await user.click(clearButton);

      expect(mockOnFilterChange).toHaveBeenCalledWith({});
    });

    it('should_disableClearButton_when_noFiltersActive', () => {
      render(<CompanyFilters onFilterChange={mockOnFilterChange} />);

      const clearButton = screen.getByRole('button', { name: /clear all filters/i });
      expect(clearButton).toBeDisabled();
    });

    it('should_enableClearButton_when_filtersActive', () => {
      render(
        <CompanyFilters
          onFilterChange={mockOnFilterChange}
          initialFilters={{ isVerified: true }}
        />
      );

      const clearButton = screen.getByRole('button', { name: /clear all filters/i });
      expect(clearButton).not.toBeDisabled();
    });
  });

  describe('URL Persistence', () => {
    it('should_persistFiltersInURL_when_filtersChanged', async () => {
      const mockSetSearchParams = vi.fn();
      vi.mocked(useSearchParams).mockReturnValue([new URLSearchParams(), mockSetSearchParams]);

      const user = userEvent.setup();
      render(<CompanyFilters onFilterChange={mockOnFilterChange} />);

      const verifiedCheckbox = screen.getByRole('checkbox', { name: /verified companies only/i });
      await user.click(verifiedCheckbox);

      await waitFor(() => {
        expect(mockSetSearchParams).toHaveBeenCalledWith(expect.any(URLSearchParams));
      });
    });

    it('should_loadFiltersFromURL_when_componentMounted', () => {
      const searchParams = new URLSearchParams();
      searchParams.set('isVerified', 'true');
      searchParams.set('industry', 'Cloud Computing');

      vi.mocked(useSearchParams).mockReturnValue([searchParams, vi.fn()]);

      render(<CompanyFilters onFilterChange={mockOnFilterChange} />);

      // Should check verified filter from URL
      const verifiedCheckbox = screen.getByRole('checkbox', { name: /verified companies only/i });
      expect(verifiedCheckbox).toBeChecked();
    });

    it('should_updateURL_when_filtersCleared', async () => {
      const mockSetSearchParams = vi.fn();
      const searchParams = new URLSearchParams();
      searchParams.set('isVerified', 'true');

      vi.mocked(useSearchParams).mockReturnValue([searchParams, mockSetSearchParams]);

      const user = userEvent.setup();
      render(<CompanyFilters onFilterChange={mockOnFilterChange} initialFilters={{ isVerified: true }} />);

      const clearButton = screen.getByRole('button', { name: /clear all filters/i });
      await user.click(clearButton);

      await waitFor(() => {
        expect(mockSetSearchParams).toHaveBeenCalledWith(new URLSearchParams());
      });
    });
  });

  describe('Responsive Design', () => {
    it('should_collapseFilters_when_mobileViewport', () => {
      // Mock mobile viewport
      global.innerWidth = 375;
      global.dispatchEvent(new Event('resize'));

      render(<CompanyFilters onFilterChange={mockOnFilterChange} />);

      // On mobile, filters should be in a collapsible section
      expect(screen.getByRole('button', { name: /filters/i })).toBeInTheDocument();
    });

    it('should_expandFilters_when_desktopViewport', () => {
      // Mock desktop viewport
      global.innerWidth = 1920;
      global.dispatchEvent(new Event('resize'));

      render(<CompanyFilters onFilterChange={mockOnFilterChange} />);

      // On desktop, all filters should be visible
      const filterContainer = screen.getByTestId('company-filters');
      expect(filterContainer).toBeVisible();
    });
  });
});
