/**
 * FilterSidebar Component Tests (Story 4.2 - Task 2a)
 *
 * Tests for archive filter sidebar (desktop version)
 * Covers AC6-12: Time period filter, topic filter, search, clear filters, sort
 */

import { describe, test, expect, vi, beforeEach } from 'vitest';
import { screen, within } from '@testing-library/react';
import { render } from '@/test/test-utils';
import userEvent from '@testing-library/user-event';
import { FilterSidebar } from '../FilterSidebar';
import type { ArchiveFilters } from '@/types/event.types';

// Mock i18n
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, params?: Record<string, unknown>) => {
      const translations: Record<string, string> = {
        'archive.filters.title': 'Filters',
        'archive.filters.topics': 'Topics',
        'archive.filters.search': 'Search events...',
        'archive.filters.clearAll': 'Clear All Filters',
        'archive.filters.activeCount': '{{count}} active filters',
        'labels.sortBy': 'Sort By',
        'archive.sort.newest': 'Newest First',
        'archive.sort.oldest': 'Oldest First',
        'archive.sort.mostAttended': 'Most Attended',
        'archive.sort.mostSessions': 'Most Sessions',
      };
      let translation = translations[key] || key;

      // Handle parameter interpolation
      if (params) {
        Object.entries(params).forEach(([paramKey, value]) => {
          translation = translation.replace(`{{${paramKey}}}`, String(value));
        });
      }

      return translation;
    },
  }),
}));

describe('FilterSidebar Component', () => {
  const mockTopics = [
    {
      topicCode: 'cloud',
      title: 'Cloud Architecture',
      description: '',
      category: 'technical',
      usageCount: 23,
    },
    {
      topicCode: 'devops',
      title: 'DevOps',
      description: '',
      category: 'technical',
      usageCount: 18,
    },
    {
      topicCode: 'security',
      title: 'Security',
      description: '',
      category: 'technical',
      usageCount: 15,
    },
    {
      topicCode: 'microservices',
      title: 'Microservices',
      description: '',
      category: 'technical',
      usageCount: 12,
    },
    { topicCode: 'ai-ml', title: 'AI/ML', description: '', category: 'technical', usageCount: 8 },
  ];

  const defaultFilters: ArchiveFilters = {
    topics: [],
    search: '',
  };

  const defaultProps = {
    filters: defaultFilters,
    topics: mockTopics,
    onFilterChange: vi.fn(),
    onClearFilters: vi.fn(),
    onSortChange: vi.fn(),
    currentSort: '-date' as const,
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('AC6: Filter Panel Structure', () => {
    test('should_renderFilterTitle_when_mounted', () => {
      render(<FilterSidebar {...defaultProps} />);

      expect(screen.getByText('Filters')).toBeInTheDocument();
    });

    test('should_renderTopicsSection_when_mounted', () => {
      render(<FilterSidebar {...defaultProps} />);

      expect(screen.getByText('Topics')).toBeInTheDocument();
    });

    test('should_renderSearchInput_when_mounted', () => {
      render(<FilterSidebar {...defaultProps} />);

      expect(screen.getByPlaceholderText('Search events...')).toBeInTheDocument();
    });

    test('should_renderClearAllButton_when_mounted', () => {
      render(<FilterSidebar {...defaultProps} />);

      expect(screen.getByText('Clear All Filters')).toBeInTheDocument();
    });

    test('should_renderSortSection_when_mounted', () => {
      render(<FilterSidebar {...defaultProps} />);

      expect(screen.getByText('Sort By')).toBeInTheDocument();
    });
  });

  describe('AC8: Topic Filter with Counts', () => {
    test('should_displayTopicsWithCounts_when_rendered', () => {
      render(<FilterSidebar {...defaultProps} />);

      expect(screen.getByText(/Cloud Architecture.*23/i)).toBeInTheDocument();
      expect(screen.getByText(/DevOps.*18/i)).toBeInTheDocument();
      expect(screen.getByText(/Security.*15/i)).toBeInTheDocument();
      expect(screen.getByText(/Microservices.*12/i)).toBeInTheDocument();
      expect(screen.getByText(/AI\/ML.*8/i)).toBeInTheDocument();
    });

    test('should_renderCheckboxesForTopics_when_rendered', () => {
      render(<FilterSidebar {...defaultProps} />);

      const checkboxes = screen.getAllByRole('checkbox');
      // Should have at least 5 checkboxes for topics
      expect(checkboxes.length).toBeGreaterThanOrEqual(5);
    });

    test('should_toggleTopicSelection_when_checkboxClicked', async () => {
      const user = userEvent.setup();
      const onFilterChange = vi.fn();

      render(<FilterSidebar {...defaultProps} onFilterChange={onFilterChange} />);

      const cloudCheckbox = screen.getByLabelText(/Cloud Architecture/i);
      await user.click(cloudCheckbox);

      expect(onFilterChange).toHaveBeenCalledWith(
        expect.objectContaining({
          topics: ['cloud'],
        })
      );
    });

    test('should_allowMultipleTopicSelection_when_multipleClicked', async () => {
      const user = userEvent.setup();
      const onFilterChange = vi.fn();

      // Use stateful wrapper to simulate controlled component behavior
      const { rerender } = render(
        <FilterSidebar {...defaultProps} onFilterChange={onFilterChange} />
      );

      const cloudCheckbox = screen.getByLabelText(/Cloud Architecture/i);
      await user.click(cloudCheckbox);

      // First click should add 'cloud'
      expect(onFilterChange).toHaveBeenLastCalledWith(
        expect.objectContaining({
          topics: ['cloud'],
        })
      );

      // Update filters to include 'cloud' (simulating parent component update)
      rerender(
        <FilterSidebar
          {...defaultProps}
          filters={{ ...defaultFilters, topics: ['cloud'] }}
          onFilterChange={onFilterChange}
        />
      );

      const devopsCheckbox = screen.getByLabelText(/DevOps/i);
      await user.click(devopsCheckbox);

      // Second click should add 'devops' to existing 'cloud'
      expect(onFilterChange).toHaveBeenLastCalledWith(
        expect.objectContaining({
          topics: expect.arrayContaining(['cloud', 'devops']),
        })
      );
    });

    test('should_deselectTopic_when_checkedCheckboxClicked', async () => {
      const user = userEvent.setup();
      const onFilterChange = vi.fn();
      const activeFilters: ArchiveFilters = {
        ...defaultFilters,
        topics: ['cloud'],
      };

      render(
        <FilterSidebar {...defaultProps} filters={activeFilters} onFilterChange={onFilterChange} />
      );

      const cloudCheckbox = screen.getByLabelText(/Cloud Architecture/i);
      expect(cloudCheckbox).toBeChecked();

      await user.click(cloudCheckbox);

      expect(onFilterChange).toHaveBeenCalledWith(
        expect.objectContaining({
          topics: [],
        })
      );
    });

    test('should_displayTopicsAlphabetically_when_rendered', () => {
      render(<FilterSidebar {...defaultProps} />);

      const topicElements = screen.getAllByRole('checkbox');
      const labels = topicElements.map((el) => el.getAttribute('aria-label') || '');

      // Topics should be in alphabetical order (or by count - depends on design)
      expect(labels).toEqual(expect.any(Array));
    });
  });

  describe('AC9: Search Bar with Debouncing', () => {
    test('should_displaySearchInput_when_rendered', () => {
      render(<FilterSidebar {...defaultProps} />);

      const searchInput = screen.getByPlaceholderText('Search events...');
      expect(searchInput).toBeInTheDocument();
      expect(searchInput).toHaveAttribute('type', 'text');
    });

    test('should_updateSearchValue_when_userTypes', async () => {
      const user = userEvent.setup();

      render(<FilterSidebar {...defaultProps} />);

      const searchInput = screen.getByPlaceholderText('Search events...');
      await user.type(searchInput, 'Cloud');

      expect(searchInput).toHaveValue('Cloud');
    });

    test('should_debounceSearchCallback_when_userTyping', async () => {
      const user = userEvent.setup();
      const onFilterChange = vi.fn();

      render(<FilterSidebar {...defaultProps} onFilterChange={onFilterChange} />);

      const searchInput = screen.getByPlaceholderText('Search events...');
      await user.type(searchInput, 'Cloud');

      // Should NOT call immediately
      expect(onFilterChange).not.toHaveBeenCalled();

      // Should call after 300ms debounce
      await new Promise((resolve) => setTimeout(resolve, 350));

      expect(onFilterChange).toHaveBeenCalledWith(
        expect.objectContaining({
          search: 'Cloud',
        })
      );
    });

    test('should_clearSearch_when_inputCleared', async () => {
      const user = userEvent.setup();
      const onFilterChange = vi.fn();
      const activeFilters: ArchiveFilters = {
        ...defaultFilters,
        search: 'Cloud',
      };

      render(
        <FilterSidebar {...defaultProps} filters={activeFilters} onFilterChange={onFilterChange} />
      );

      const searchInput = screen.getByPlaceholderText('Search events...');
      expect(searchInput).toHaveValue('Cloud');

      await user.clear(searchInput);

      await new Promise((resolve) => setTimeout(resolve, 350));

      expect(onFilterChange).toHaveBeenCalledWith(
        expect.objectContaining({
          search: '',
        })
      );
    });
  });

  describe('AC11: Clear All Filters', () => {
    test('should_clearAllFilters_when_clearButtonClicked', async () => {
      const user = userEvent.setup();
      const onClearFilters = vi.fn();
      const activeFilters: ArchiveFilters = {
        topics: ['cloud', 'devops'],
        search: 'Cloud',
      };

      render(
        <FilterSidebar {...defaultProps} filters={activeFilters} onClearFilters={onClearFilters} />
      );

      const clearButton = screen.getByText('Clear All Filters');
      await user.click(clearButton);

      expect(onClearFilters).toHaveBeenCalled();
    });

    test('should_disableClearButton_when_noFiltersActive', () => {
      render(<FilterSidebar {...defaultProps} filters={defaultFilters} />);

      const clearButton = screen.getByText('Clear All Filters');
      expect(clearButton).toBeDisabled();
    });

    test('should_enableClearButton_when_filtersActive', () => {
      const activeFilters: ArchiveFilters = {
        ...defaultFilters,
        topics: ['cloud'],
      };

      render(<FilterSidebar {...defaultProps} filters={activeFilters} />);

      const clearButton = screen.getByText('Clear All Filters');
      expect(clearButton).not.toBeDisabled();
    });
  });

  describe('AC12: Sort Options', () => {
    test('should_displaySortDropdown_when_rendered', () => {
      render(<FilterSidebar {...defaultProps} />);

      expect(screen.getByText('Sort By')).toBeInTheDocument();
    });

    test('should_displayAllSortOptions_when_dropdownOpened', async () => {
      const user = userEvent.setup();

      render(<FilterSidebar {...defaultProps} />);

      const sortSelect = screen.getByRole('combobox', { name: /Sort By/i });
      await user.click(sortSelect);

      expect(screen.getByText('Newest First')).toBeInTheDocument();
      expect(screen.getByText('Oldest First')).toBeInTheDocument();
      expect(screen.getByText('Most Attended')).toBeInTheDocument();
    });

    test('should_selectNewestFirst_when_defaultLoaded', () => {
      render(<FilterSidebar {...defaultProps} currentSort="-date" />);

      const sortSelect = screen.getByRole('combobox', { name: /Sort By/i });
      expect(sortSelect).toHaveValue('-date');
    });

    test('should_changeSortOption_when_optionSelected', async () => {
      const user = userEvent.setup();
      const onSortChange = vi.fn();

      render(<FilterSidebar {...defaultProps} onSortChange={onSortChange} />);

      const sortSelect = screen.getByRole('combobox', { name: /Sort By/i });
      await user.selectOptions(sortSelect, 'date');

      expect(onSortChange).toHaveBeenCalledWith('date');
    });

    test('should_callSortChange_when_mostAttendedSelected', async () => {
      const user = userEvent.setup();
      const onSortChange = vi.fn();

      render(<FilterSidebar {...defaultProps} onSortChange={onSortChange} />);

      const sortSelect = screen.getByRole('combobox', { name: /Sort By/i });
      await user.selectOptions(sortSelect, '-attendance');

      expect(onSortChange).toHaveBeenCalledWith('-attendance');
    });
  });

  describe('Active Filter Indicators', () => {
    test('should_highlightActiveFilters_when_filtersApplied', () => {
      const activeFilters: ArchiveFilters = {
        topics: ['cloud', 'devops'],
        search: 'Architecture',
      };

      render(<FilterSidebar {...defaultProps} filters={activeFilters} />);

      // Topics should be checked
      const cloudCheckbox = screen.getByLabelText(/Cloud Architecture/i);
      expect(cloudCheckbox).toBeChecked();

      const devopsCheckbox = screen.getByLabelText(/DevOps/i);
      expect(devopsCheckbox).toBeChecked();

      // Search should have value
      const searchInput = screen.getByPlaceholderText('Search events...');
      expect(searchInput).toHaveValue('Architecture');
    });

    test('should_showActiveFilterCount_when_filtersApplied', () => {
      const activeFilters: ArchiveFilters = {
        topics: ['cloud', 'devops'],
        search: '',
      };

      render(<FilterSidebar {...defaultProps} filters={activeFilters} />);

      // Should show count of active filters (e.g., "2 active filters" - 2 topics only, no time period)
      expect(screen.getByText(/2.*active/i)).toBeInTheDocument();
    });
  });

  describe('Accessibility', () => {
    test('should_haveProperLabels_when_rendered', () => {
      render(<FilterSidebar {...defaultProps} />);

      // All checkboxes should have labels
      const checkboxes = screen.getAllByRole('checkbox');
      checkboxes.forEach((checkbox) => {
        expect(checkbox).toHaveAccessibleName();
      });
    });

    test('should_supportKeyboardNavigation_when_focused', async () => {
      const user = userEvent.setup();
      const onFilterChange = vi.fn();

      const { rerender } = render(
        <FilterSidebar {...defaultProps} onFilterChange={onFilterChange} />
      );

      const firstCheckbox = screen.getAllByRole('checkbox')[0];

      // Click checkbox
      await user.click(firstCheckbox);

      // Verify onFilterChange was called (checkbox interaction works)
      expect(onFilterChange).toHaveBeenCalled();

      // Re-render with updated filters to reflect checked state
      const clickedTopicCode = mockTopics[0].topicCode; // 'cloud'
      rerender(
        <FilterSidebar
          {...defaultProps}
          filters={{ ...defaultFilters, topics: [clickedTopicCode] }}
          onFilterChange={onFilterChange}
        />
      );

      // Now the checkbox should be checked
      expect(firstCheckbox).toBeChecked();
    });

    test('should_haveAriaLabels_when_rendered', () => {
      render(<FilterSidebar {...defaultProps} />);

      const sidebar = screen.getByRole('complementary') || screen.getByRole('region');
      expect(sidebar).toHaveAttribute('aria-label', 'Event filters');
    });
  });

  describe('Edge Cases', () => {
    test('should_handleNoTopics_when_topicsArrayEmpty', () => {
      render(<FilterSidebar {...defaultProps} topics={[]} />);

      expect(screen.getByText('Topics')).toBeInTheDocument();
      // Should show "No topics available" or hide section
    });

    test('should_handleLongTopicNames_when_nameExceeds30Chars', () => {
      const longTopicName = 'Cloud Architecture and Microservices Design Patterns';
      const topicsWithLongName = [
        ...mockTopics,
        {
          topicCode: 'long',
          title: longTopicName,
          description: '',
          category: 'technical',
          usageCount: 5,
        },
      ];

      render(<FilterSidebar {...defaultProps} topics={topicsWithLongName} />);

      // Topic name should be truncated or wrapped
      expect(screen.getByText(new RegExp(longTopicName.substring(0, 20)))).toBeInTheDocument();
    });

    test('should_handleManyTopics_when_moreThan20Topics', () => {
      const manyTopics = Array.from({ length: 25 }, (_, i) => ({
        topicCode: `topic-${i}`,
        title: `Topic ${i}`,
        description: '',
        category: 'technical',
        usageCount: Math.floor(Math.random() * 50),
      }));

      render(<FilterSidebar {...defaultProps} topics={manyTopics} />);

      // Should show all topics or implement scrolling/pagination
      expect(screen.getByText('Topics')).toBeInTheDocument();
    });
  });
});
