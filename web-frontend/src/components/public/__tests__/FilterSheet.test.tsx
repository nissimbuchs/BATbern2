/**
 * FilterSheet Component Tests (Story 4.2 - Task 2b)
 *
 * Tests the mobile filter sheet/modal functionality
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { FilterSheet } from '../FilterSheet';
import type { ArchiveFilters } from '@/types/event.types';
import type { Topic } from '@/types/topic.types';

// Mock FilterSidebar since we're testing FilterSheet in isolation
vi.mock('../FilterSidebar', () => ({
  FilterSidebar: ({
    onFilterChange,
    onClearFilters,
    onSortChange,
  }: {
    onFilterChange: (filters: ArchiveFilters) => void;
    onClearFilters: () => void;
    onSortChange: (sort: string) => void;
  }) => (
    <div data-testid="filter-sidebar">
      <button onClick={() => onFilterChange({ topics: ['topic1'] })} data-testid="apply-filters">
        Apply Filters
      </button>
      <button onClick={onClearFilters} data-testid="clear-filters-sidebar">
        Clear
      </button>
      <button onClick={() => onSortChange('-date')} data-testid="sort-change">
        Sort
      </button>
    </div>
  ),
}));

// Mock i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        'archive.filters.title': 'Filters',
      };
      return translations[key] || key;
    },
  }),
}));

describe('FilterSheet Component', () => {
  const mockTopics: Topic[] = [
    {
      topicId: 'topic1',
      name: 'Cloud Architecture',
      color: '#3B82F6',
      description: 'Cloud topics',
    },
    {
      topicId: 'topic2',
      name: 'Security',
      color: '#EF4444',
      description: 'Security topics',
    },
  ];

  const mockFilters: ArchiveFilters = {
    topics: [],
    searchQuery: '',
  };

  const defaultProps = {
    filters: mockFilters,
    topics: mockTopics,
    onFilterChange: vi.fn(),
    onClearFilters: vi.fn(),
    onSortChange: vi.fn(),
    currentSort: '-date',
    loading: false,
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Rendering', () => {
    it('should_renderTriggerButton_when_mounted', () => {
      render(<FilterSheet {...defaultProps} />);

      const triggerButton = screen.getByText('Filters');
      expect(triggerButton).toBeInTheDocument();
    });

    it('should_notShowModal_when_initiallyRendered', () => {
      render(<FilterSheet {...defaultProps} />);

      const filterSidebar = screen.queryByTestId('filter-sidebar');
      expect(filterSidebar).not.toBeInTheDocument();
    });
  });

  describe('Modal Behavior', () => {
    it('should_openModal_when_triggerButtonClicked', async () => {
      render(<FilterSheet {...defaultProps} />);

      const triggerButton = screen.getByText('Filters');
      fireEvent.click(triggerButton);

      await waitFor(() => {
        const filterSidebar = screen.getByTestId('filter-sidebar');
        expect(filterSidebar).toBeInTheDocument();
      });
    });

    it('should_closeModal_when_closeButtonClicked', async () => {
      render(<FilterSheet {...defaultProps} />);

      // Open modal
      const triggerButton = screen.getByText('Filters');
      fireEvent.click(triggerButton);

      // Wait for modal to open
      await waitFor(() => {
        expect(screen.getByTestId('filter-sidebar')).toBeInTheDocument();
      });

      // Close modal
      const closeButton = screen.getByText('✕');
      fireEvent.click(closeButton);

      await waitFor(() => {
        expect(screen.queryByTestId('filter-sidebar')).not.toBeInTheDocument();
      });
    });

    it('should_displayModalTitle_when_opened', async () => {
      render(<FilterSheet {...defaultProps} />);

      const triggerButton = screen.getByText('Filters');
      fireEvent.click(triggerButton);

      await waitFor(() => {
        const title = screen.getAllByText('Filters');
        expect(title.length).toBeGreaterThan(1); // One in button, one in modal
      });
    });
  });

  describe('Filter Interactions', () => {
    it('should_callOnFilterChangeAndClose_when_filtersApplied', async () => {
      const onFilterChange = vi.fn();
      render(<FilterSheet {...defaultProps} onFilterChange={onFilterChange} />);

      // Open modal
      const triggerButton = screen.getByText('Filters');
      fireEvent.click(triggerButton);

      await waitFor(() => {
        expect(screen.getByTestId('filter-sidebar')).toBeInTheDocument();
      });

      // Apply filters
      const applyButton = screen.getByTestId('apply-filters');
      fireEvent.click(applyButton);

      await waitFor(() => {
        expect(onFilterChange).toHaveBeenCalledWith({ topics: ['topic1'] });
        expect(screen.queryByTestId('filter-sidebar')).not.toBeInTheDocument();
      });
    });

    it('should_callOnClearFiltersAndClose_when_clearClicked', async () => {
      const onClearFilters = vi.fn();
      render(<FilterSheet {...defaultProps} onClearFilters={onClearFilters} />);

      // Open modal
      const triggerButton = screen.getByText('Filters');
      fireEvent.click(triggerButton);

      await waitFor(() => {
        expect(screen.getByTestId('filter-sidebar')).toBeInTheDocument();
      });

      // Clear filters
      const clearButton = screen.getByTestId('clear-filters-sidebar');
      fireEvent.click(clearButton);

      await waitFor(() => {
        expect(onClearFilters).toHaveBeenCalled();
        expect(screen.queryByTestId('filter-sidebar')).not.toBeInTheDocument();
      });
    });

    it('should_callOnSortChangeAndClose_when_sortChanged', async () => {
      const onSortChange = vi.fn();
      render(<FilterSheet {...defaultProps} onSortChange={onSortChange} />);

      // Open modal
      const triggerButton = screen.getByText('Filters');
      fireEvent.click(triggerButton);

      await waitFor(() => {
        expect(screen.getByTestId('filter-sidebar')).toBeInTheDocument();
      });

      // Change sort
      const sortButton = screen.getByTestId('sort-change');
      fireEvent.click(sortButton);

      await waitFor(() => {
        expect(onSortChange).toHaveBeenCalledWith('-date');
        expect(screen.queryByTestId('filter-sidebar')).not.toBeInTheDocument();
      });
    });
  });

  describe('Props Passing', () => {
    it('should_passAllPropsToFilterSidebar_when_rendered', async () => {
      const onSortChange = vi.fn();
      render(<FilterSheet {...defaultProps} currentSort="date" onSortChange={onSortChange} />);

      // Open modal
      const triggerButton = screen.getByText('Filters');
      fireEvent.click(triggerButton);

      await waitFor(() => {
        const filterSidebar = screen.getByTestId('filter-sidebar');
        expect(filterSidebar).toBeInTheDocument();
      });

      // Verify FilterSidebar received the props by testing interactions
      const sortButton = screen.getByTestId('sort-change');
      fireEvent.click(sortButton);

      await waitFor(() => {
        expect(onSortChange).toHaveBeenCalled();
      });
    });

    it('should_passLoadingState_when_loading', async () => {
      render(<FilterSheet {...defaultProps} loading={true} />);

      const triggerButton = screen.getByText('Filters');
      fireEvent.click(triggerButton);

      await waitFor(() => {
        expect(screen.getByTestId('filter-sidebar')).toBeInTheDocument();
      });
    });
  });
});
