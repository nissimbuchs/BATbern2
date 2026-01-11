/**
 * FilterSidebar Component (Story 4.2 - Task 2b)
 *
 * Desktop filter sidebar for archive browsing
 * Handles time period, topic, search, and sort filters
 */

import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import type { ArchiveFilters } from '@/types/event.types';

interface Topic {
  id: string;
  name: string;
  code: string;
  count: number;
}

interface FilterSidebarProps {
  filters: ArchiveFilters;
  topics: Topic[];
  onFilterChange: (filters: ArchiveFilters) => void;
  onClearFilters: () => void;
  onSortChange: (sort: string) => void;
  currentSort: string;
}

type TimePeriod = 'all' | 'last5y' | '2020-2024' | '2015-2019' | '2010-2014' | 'before2010';

const TIME_PERIODS: TimePeriod[] = [
  'all',
  'last5y',
  '2020-2024',
  '2015-2019',
  '2010-2014',
  'before2010',
];

const SORT_OPTIONS = [
  { value: '-date', label: 'archive.sort.newest' },
  { value: 'date', label: 'archive.sort.oldest' },
  { value: '-attendance', label: 'archive.sort.mostAttended' },
];

export function FilterSidebar({
  filters,
  topics,
  onFilterChange,
  onClearFilters,
  onSortChange,
  currentSort,
}: FilterSidebarProps) {
  const { t } = useTranslation();
  const [searchValue, setSearchValue] = useState(filters.search || '');

  // Debounce search input (300ms)
  useEffect(() => {
    const timer = setTimeout(() => {
      onFilterChange({ ...filters, search: searchValue });
    }, 300);

    return () => clearTimeout(timer);
  }, [searchValue]);

  // Sync search value with filters prop
  useEffect(() => {
    setSearchValue(filters.search || '');
  }, [filters.search]);

  const handleTimePeriodChange = (period: TimePeriod) => {
    onFilterChange({ ...filters, timePeriod: period });
  };

  const handleTopicToggle = (topicCode: string) => {
    const currentTopics = filters.topics || [];
    const newTopics = currentTopics.includes(topicCode)
      ? currentTopics.filter((t) => t !== topicCode)
      : [...currentTopics, topicCode];

    onFilterChange({ ...filters, topics: newTopics });
  };

  const hasActiveFilters =
    (filters.timePeriod && filters.timePeriod !== 'all') ||
    (filters.topics && filters.topics.length > 0) ||
    (filters.search && filters.search.length > 0);

  // Count active filters
  const activeFilterCount =
    (filters.timePeriod && filters.timePeriod !== 'all' ? 1 : 0) +
    (filters.topics?.length || 0) +
    (filters.search && filters.search.length > 0 ? 1 : 0);

  return (
    <aside
      className="space-y-6"
      role="complementary"
      aria-label="Event filters"
      data-testid="filter-sidebar"
    >
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold">{t('archive.filters.title')}</h2>
          {activeFilterCount > 0 && (
            <p className="text-xs text-gray-600 mt-1">
              {activeFilterCount} active {activeFilterCount === 1 ? 'filter' : 'filters'}
            </p>
          )}
        </div>
        <button
          onClick={onClearFilters}
          disabled={!hasActiveFilters}
          className="text-sm text-blue-600 hover:text-blue-800 disabled:text-gray-400 disabled:cursor-not-allowed"
          data-testid="clear-filters"
        >
          {t('archive.filters.clearAll')}
        </button>
      </div>

      {/* Search */}
      <div>
        <input
          type="text"
          placeholder={t('archive.filters.search')}
          value={searchValue}
          onChange={(e) => setSearchValue(e.target.value)}
          className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
      </div>

      {/* Time Period */}
      <div>
        <h3 className="text-sm font-medium text-gray-900 mb-2">
          {t('archive.filters.timePeriod')}
        </h3>
        <div className="space-y-1">
          {TIME_PERIODS.map((period) => {
            const isActive = filters.timePeriod === period;
            const translationKey =
              period === 'all'
                ? 'archive.filters.all'
                : period === 'last5y'
                  ? 'archive.filters.last5years'
                  : `archive.filters.${period}`;

            return (
              <button
                key={period}
                onClick={() => handleTimePeriodChange(period)}
                className={`w-full text-left px-3 py-2 rounded-md text-sm ${
                  isActive ? 'bg-blue-100 text-blue-900 active' : 'hover:bg-gray-100 text-gray-700'
                }`}
              >
                {t(translationKey)}
              </button>
            );
          })}
        </div>
      </div>

      {/* Topics */}
      <div>
        <h3 className="text-sm font-medium text-gray-900 mb-2">{t('archive.filters.topics')}</h3>
        <div className="space-y-2">
          {topics.map((topic) => {
            const isChecked = filters.topics?.includes(topic.code) || false;

            return (
              <label key={topic.id} className="flex items-center cursor-pointer">
                <input
                  type="checkbox"
                  checked={isChecked}
                  onChange={() => handleTopicToggle(topic.code)}
                  aria-label={`${topic.name} (${topic.count})`}
                  className="h-4 w-4 text-blue-600 focus:ring-blue-500 border-gray-300 rounded"
                />
                <span className="ml-2 text-sm text-gray-700">
                  {topic.name} ({topic.count})
                </span>
              </label>
            );
          })}
        </div>
      </div>

      {/* Sort */}
      <div>
        <label htmlFor="sort-select" className="block text-sm font-medium text-gray-900 mb-2">
          {t('archive.sort.label')}
        </label>
        <select
          id="sort-select"
          data-testid="sort-select"
          value={currentSort}
          onChange={(e) => onSortChange(e.target.value)}
          aria-label="Sort By"
          className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          {SORT_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {t(option.label)}
            </option>
          ))}
        </select>
      </div>
    </aside>
  );
}
