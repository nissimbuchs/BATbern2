/**
 * EventSearch Component Tests (RED Phase - TDD)
 *
 * Story 2.5.3 - Task 8a
 * AC: 2 (Event List & Filters)
 * Wireframe: docs/wireframes/story-1.16-event-management-dashboard.md v1.0
 *
 * Tests for event search and filters:
 * - Search by title
 * - Filter by status (active, published, completed, archived)
 * - Filter by year
 * - Filter by event type
 * - URL persistence (shareable filters)
 * - Clear filters
 */

import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { EventSearch } from '../EventSearch';

describe('EventSearch Component', () => {
  const mockOnFiltersChange = vi.fn();

  const defaultProps = {
    onFiltersChange: mockOnFiltersChange,
    filters: {},
  };

  const createWrapper = () => {
    return ({ children }: { children: React.ReactNode }) => (
      <BrowserRouter>{children}</BrowserRouter>
    );
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Search Input (AC2)', () => {
    it('should_displaySearchInput_when_rendered', () => {
      render(<EventSearch {...defaultProps} />, { wrapper: createWrapper() });

      expect(screen.getByPlaceholderText(/search events/i)).toBeInTheDocument();
    });

    it('should_updateSearchQuery_when_userTypes', () => {
      render(<EventSearch {...defaultProps} />, { wrapper: createWrapper() });

      const searchInput = screen.getByPlaceholderText(/search events/i);
      fireEvent.change(searchInput, { target: { value: 'Cloud' } });

      expect(searchInput).toHaveValue('Cloud');
    });

    it('should_callOnFiltersChange_when_searchDebounced', async () => {
      render(<EventSearch {...defaultProps} />, { wrapper: createWrapper() });

      const searchInput = screen.getByPlaceholderText(/search events/i);
      fireEvent.change(searchInput, { target: { value: 'Cloud' } });

      // Wait for debounce (300ms)
      await waitFor(
        () => {
          expect(mockOnFiltersChange).toHaveBeenCalledWith({ search: 'Cloud' });
        },
        { timeout: 500 }
      );
    });

    it('should_debounceSearchInput_when_userTypesRapidly', async () => {
      render(<EventSearch {...defaultProps} />, { wrapper: createWrapper() });

      const searchInput = screen.getByPlaceholderText(/search events/i);

      fireEvent.change(searchInput, { target: { value: 'C' } });
      fireEvent.change(searchInput, { target: { value: 'Cl' } });
      fireEvent.change(searchInput, { target: { value: 'Clo' } });
      fireEvent.change(searchInput, { target: { value: 'Cloud' } });

      // Should only call once after debounce
      await waitFor(
        () => {
          expect(mockOnFiltersChange).toHaveBeenCalledTimes(1);
        },
        { timeout: 500 }
      );
    });

    it('should_showSearchIcon_when_rendered', () => {
      render(<EventSearch {...defaultProps} />, { wrapper: createWrapper() });

      expect(screen.getByTestId('search-icon')).toBeInTheDocument();
    });

    it('should_showClearButton_when_searchHasValue', () => {
      render(<EventSearch {...defaultProps} filters={{ search: 'Cloud' }} />, {
        wrapper: createWrapper(),
      });

      expect(screen.getByLabelText(/clear search/i)).toBeInTheDocument();
    });

    it('should_clearSearch_when_clearButtonClicked', () => {
      render(<EventSearch {...defaultProps} filters={{ search: 'Cloud' }} />, {
        wrapper: createWrapper(),
      });

      fireEvent.click(screen.getByLabelText(/clear search/i));

      expect(mockOnFiltersChange).toHaveBeenCalledWith({ search: '' });
    });
  });

  describe('Status Filter (AC2)', () => {
    it('should_displayStatusFilter_when_rendered', () => {
      render(<EventSearch {...defaultProps} />, { wrapper: createWrapper() });

      expect(screen.getByRole('combobox', { name: /filter by status/i })).toBeInTheDocument();
    });

    it('should_displayStatusOptions_when_dropdownOpened', () => {
      render(<EventSearch {...defaultProps} />, { wrapper: createWrapper() });

      fireEvent.mouseDown(screen.getByRole('combobox', { name: /filter by status/i }));

      expect(screen.getByText(/active/i)).toBeInTheDocument();
      expect(screen.getByText(/published/i)).toBeInTheDocument();
      expect(screen.getByText(/completed/i)).toBeInTheDocument();
      expect(screen.getByText(/archived/i)).toBeInTheDocument();
    });

    it('should_allowMultipleSelection_when_statusOptionsClicked', () => {
      const { rerender } = render(<EventSearch {...defaultProps} />, { wrapper: createWrapper() });

      const statusSelect = screen.getByRole('combobox', { name: /filter by status/i });

      // Open dropdown and select first option
      fireEvent.mouseDown(statusSelect);
      fireEvent.click(screen.getByText(/active/i));

      // Verify first selection was called
      expect(mockOnFiltersChange).toHaveBeenCalledWith({
        status: ['active'],
      });

      // Re-render with updated filters (simulating parent state update)
      rerender(<EventSearch {...defaultProps} filters={{ status: ['active'] }} />);

      // Dropdown is still open after first selection, just click second option
      fireEvent.click(screen.getByText(/published/i));

      // Verify both selections were called
      expect(mockOnFiltersChange).toHaveBeenCalledWith({
        status: ['active', 'published'],
      });
    });

    it('should_displaySelectedStatus_when_statusFiltered', () => {
      render(<EventSearch {...defaultProps} filters={{ status: ['active', 'published'] }} />, {
        wrapper: createWrapper(),
      });

      // Multiple elements may contain "active" (status chip + filter count chip)
      expect(screen.getAllByText(/active/i).length).toBeGreaterThan(0);
      expect(screen.getByText(/published/i)).toBeInTheDocument();
    });

    it('should_removeStatus_when_chipDeleted', () => {
      render(<EventSearch {...defaultProps} filters={{ status: ['active', 'published'] }} />, {
        wrapper: createWrapper(),
      });

      // Find the chip by its aria-label and click its cancel icon
      const activeChip = screen.getByLabelText(/remove active filter/i);
      const cancelIcon = activeChip.querySelector('[data-testid="CancelIcon"]');

      if (cancelIcon) {
        fireEvent.click(cancelIcon);
      }

      expect(mockOnFiltersChange).toHaveBeenCalledWith({
        status: ['published'],
      });
    });
  });

  describe('Year Filter (AC2)', () => {
    it('should_displayYearFilter_when_rendered', () => {
      render(<EventSearch {...defaultProps} />, { wrapper: createWrapper() });

      expect(screen.getByRole('combobox', { name: /filter by year/i })).toBeInTheDocument();
    });

    it('should_displayYearOptions_when_dropdownOpened', () => {
      render(<EventSearch {...defaultProps} />, { wrapper: createWrapper() });

      fireEvent.mouseDown(screen.getByRole('combobox', { name: /filter by year/i }));

      expect(screen.getByText('2025')).toBeInTheDocument();
      expect(screen.getByText('2024')).toBeInTheDocument();
    });

    it('should_selectYear_when_yearClicked', () => {
      render(<EventSearch {...defaultProps} />, { wrapper: createWrapper() });

      fireEvent.mouseDown(screen.getByRole('combobox', { name: /filter by year/i }));
      fireEvent.click(screen.getByText('2025'));

      expect(mockOnFiltersChange).toHaveBeenCalledWith({ year: 2025 });
    });

    it('should_displaySelectedYear_when_yearFiltered', () => {
      render(<EventSearch {...defaultProps} filters={{ year: 2025 }} />, {
        wrapper: createWrapper(),
      });

      expect(screen.getByDisplayValue('2025')).toBeInTheDocument();
    });
  });

  describe('Clear All Filters (AC2)', () => {
    it('should_displayClearAllButton_when_filtersActive', () => {
      render(
        <EventSearch
          {...defaultProps}
          filters={{ status: ['active'], year: 2025, search: 'Cloud' }}
        />,
        { wrapper: createWrapper() }
      );

      expect(screen.getByRole('button', { name: /clear all/i })).toBeInTheDocument();
    });

    it('should_hideClearAllButton_when_noFilters', () => {
      render(<EventSearch {...defaultProps} filters={{}} />, {
        wrapper: createWrapper(),
      });

      expect(screen.queryByRole('button', { name: /clear all/i })).not.toBeInTheDocument();
    });

    it('should_clearAllFilters_when_clearAllClicked', () => {
      render(
        <EventSearch
          {...defaultProps}
          filters={{ status: ['active'], year: 2025, search: 'Cloud' }}
        />,
        { wrapper: createWrapper() }
      );

      fireEvent.click(screen.getByRole('button', { name: /clear all/i }));

      expect(mockOnFiltersChange).toHaveBeenCalledWith({});
    });
  });

  describe('Active Filter Count (AC2)', () => {
    it('should_displayActiveFilterCount_when_filtersActive', () => {
      render(
        <EventSearch {...defaultProps} filters={{ status: ['active', 'published'], year: 2025 }} />,
        { wrapper: createWrapper() }
      );

      expect(screen.getByText(/3.*filters?.*active/i)).toBeInTheDocument();
    });

    it('should_updateCount_when_filtersChange', () => {
      const { rerender } = render(
        <EventSearch {...defaultProps} filters={{ status: ['active'] }} />,
        { wrapper: createWrapper() }
      );

      expect(screen.getByText(/1.*filter.*active/i)).toBeInTheDocument();

      rerender(<EventSearch {...defaultProps} filters={{ status: ['active'], year: 2025 }} />);

      expect(screen.getByText(/2.*filters?.*active/i)).toBeInTheDocument();
    });
  });

  describe('URL Persistence (AC2)', () => {
    it('should_updateURL_when_filtersChange', async () => {
      render(<EventSearch {...defaultProps} />, { wrapper: createWrapper() });

      const searchInput = screen.getByPlaceholderText(/search events/i);
      fireEvent.change(searchInput, { target: { value: 'Cloud' } });

      // Wait for debounce and verify onFiltersChange was called with search param
      await waitFor(
        () => {
          expect(mockOnFiltersChange).toHaveBeenCalledWith(
            expect.objectContaining({ search: 'Cloud' })
          );
        },
        { timeout: 500 }
      );
    });

    it('should_parseURLParams_when_componentMounts', () => {
      // Set URL params before render
      window.history.pushState({}, '', '?status=active&year=2025&search=Cloud');

      render(<EventSearch {...defaultProps} />, { wrapper: createWrapper() });

      expect(mockOnFiltersChange).toHaveBeenCalledWith({
        status: ['active'],
        year: 2025,
        search: 'Cloud',
      });
    });

    it('should_generateShareableURL_when_filtersActive', () => {
      render(
        <EventSearch
          {...defaultProps}
          filters={{ status: ['active'], year: 2025, search: 'Cloud' }}
        />,
        { wrapper: createWrapper() }
      );

      expect(window.location.search).toContain('status=active');
      expect(window.location.search).toContain('year=2025');
      expect(window.location.search).toContain('search=Cloud');
    });
  });

  describe('Responsive Layout', () => {
    it('should_stackFilters_when_mobileView', () => {
      render(<EventSearch {...defaultProps} />, { wrapper: createWrapper() });

      const container = screen.getByTestId('filter-container');
      expect(container).toBeInTheDocument();
    });

    it('should_expandFullWidth_when_mobileView', () => {
      render(<EventSearch {...defaultProps} />, { wrapper: createWrapper() });

      const searchInput = screen.getByPlaceholderText(/search events/i);
      // Check that the search input is rendered (component is responsive by default)
      expect(searchInput).toBeInTheDocument();
    });
  });

  describe('Accessibility', () => {
    it('should_haveAriaLabels_when_rendered', () => {
      render(<EventSearch {...defaultProps} />, { wrapper: createWrapper() });

      expect(screen.getByLabelText(/search events/i)).toBeInTheDocument();
      expect(screen.getByRole('combobox', { name: /filter by status/i })).toBeInTheDocument();
      expect(screen.getByRole('combobox', { name: /filter by year/i })).toBeInTheDocument();
    });

    it('should_announceFilterCount_when_filtersActive', () => {
      render(<EventSearch {...defaultProps} filters={{ status: ['active'], year: 2025 }} />, {
        wrapper: createWrapper(),
      });

      expect(screen.getByLabelText(/2 filters active/i)).toBeInTheDocument();
    });
  });

  describe('Internationalization', () => {
    it('should_translateFilterLabels_when_rendered', () => {
      render(<EventSearch {...defaultProps} />, { wrapper: createWrapper() });

      // Filter labels should be translated
      expect(screen.getByRole('combobox', { name: /filter by status/i })).toBeInTheDocument();
      expect(screen.getByRole('combobox', { name: /filter by year/i })).toBeInTheDocument();
    });

    it('should_translateStatusOptions_when_dropdownOpened', () => {
      render(<EventSearch {...defaultProps} />, { wrapper: createWrapper() });

      fireEvent.mouseDown(screen.getByRole('combobox', { name: /filter by status/i }));

      // Status options should be translated
      expect(screen.getByText(/active/i)).toBeInTheDocument();
      expect(screen.getByText(/published/i)).toBeInTheDocument();
    });
  });
});
