/**
 * FilterSidebar Component (Story 4.2 - Task 2b)
 *
 * Desktop filter sidebar for archive browsing
 * Handles time period, topic, search, and sort filters
 */

import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import type { ArchiveFilters } from '@/types/event.types';
import type { Topic } from '@/types/topic.types';

interface FilterSidebarProps {
  filters: ArchiveFilters;
  topics: Topic[];
  onFilterChange: (filters: ArchiveFilters) => void;
  onClearFilters: () => void;
  onSortChange: (sort: string) => void;
  currentSort: string;
  loading?: boolean;
}

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
  loading = false,
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

  const handleTopicToggle = (topicCode: string) => {
    const currentTopics = filters.topics || [];
    const newTopics = currentTopics.includes(topicCode)
      ? currentTopics.filter((t) => t !== topicCode)
      : [...currentTopics, topicCode];

    onFilterChange({ ...filters, topics: newTopics });
  };

  const hasActiveFilters =
    (filters.topics && filters.topics.length > 0) || (filters.search && filters.search.length > 0);

  // Count active filters
  const activeFilterCount =
    (filters.topics?.length || 0) + (filters.search && filters.search.length > 0 ? 1 : 0);

  return (
    <aside
      className="space-y-6"
      role="complementary"
      aria-label={t('filters.ariaLabel')}
      data-testid="filter-sidebar"
    >
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold">{t('archive.filters.title')}</h2>
          {activeFilterCount > 0 && (
            <p className="text-xs text-gray-600 mt-1">
              {t('archive.filters.activeCount', { count: activeFilterCount })}
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
          data-testid="search-input"
        />
      </div>

      {/* Topics */}
      <div data-testid="topic-filter">
        <h3 className="text-sm font-medium text-gray-900 mb-2">{t('archive.filters.topics')}</h3>
        <div className="space-y-2">
          {loading ? (
            <div className="text-sm text-gray-500">{t('archive.loadingTopics')}</div>
          ) : topics.length === 0 ? (
            <div className="text-sm text-gray-500">{t('archive.noTopicsAvailable')}</div>
          ) : (
            topics.map((topic) => {
              const isChecked = filters.topics?.includes(topic.topicCode) || false;

              return (
                <label key={topic.topicCode} className="flex items-center cursor-pointer">
                  <input
                    type="checkbox"
                    checked={isChecked}
                    onChange={() => handleTopicToggle(topic.topicCode)}
                    aria-label={`${topic.title} (${topic.usageCount})`}
                    className="h-4 w-4 text-blue-600 focus:ring-blue-500 border-gray-300 rounded"
                  />
                  <span className="ml-2 text-sm text-gray-700">
                    {topic.title} ({topic.usageCount})
                  </span>
                </label>
              );
            })
          )}
        </div>
      </div>

      {/* Sort */}
      <div>
        <label htmlFor="sort-select" className="block text-sm font-medium text-gray-900 mb-2">
          {t('labels.sortBy')}
        </label>
        <select
          id="sort-select"
          data-testid="sort-select"
          value={currentSort}
          onChange={(e) => onSortChange(e.target.value)}
          aria-label={t('labels.sortBy')}
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
