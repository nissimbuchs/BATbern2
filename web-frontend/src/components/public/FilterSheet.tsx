/**
 * FilterSheet Component (Story 4.2 - Task 2b)
 *
 * Mobile filter sheet (modal) for archive browsing
 * Wraps FilterSidebar in a sheet/modal for mobile devices
 */

import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { FilterSidebar } from './FilterSidebar';
import type { ArchiveFilters } from '@/types/event.types';
import type { Topic } from '@/types/topic.types';

interface FilterSheetProps {
  filters: ArchiveFilters;
  topics: Topic[];
  onFilterChange: (filters: ArchiveFilters) => void;
  onClearFilters: () => void;
  onSortChange: (sort: string) => void;
  currentSort: string;
  loading?: boolean;
}

export function FilterSheet({
  filters,
  topics,
  onFilterChange,
  onClearFilters,
  onSortChange,
  currentSort,
  loading = false,
}: FilterSheetProps) {
  const { t } = useTranslation();
  const [isOpen, setIsOpen] = useState(false);

  return (
    <div data-testid="filter-sheet">
      {/* Trigger Button */}
      <button
        onClick={() => setIsOpen(true)}
        className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700"
      >
        {t('archive.filters.title')}
      </button>

      {/* Modal Overlay */}
      {isOpen && (
        <div className="fixed inset-0 z-50 flex items-end justify-center bg-black bg-opacity-50">
          {/* Sheet */}
          <div className="bg-white w-full max-h-[80vh] rounded-t-lg overflow-y-auto p-6">
            <div className="flex justify-between items-center mb-4">
              <h2 className="text-lg font-semibold">{t('archive.filters.title')}</h2>
              <button
                onClick={() => setIsOpen(false)}
                className="text-gray-500 hover:text-gray-700"
              >
                ✕
              </button>
            </div>

            {/* Filter Content */}
            <FilterSidebar
              filters={filters}
              topics={topics}
              onFilterChange={(newFilters) => {
                onFilterChange(newFilters);
                setIsOpen(false);
              }}
              onClearFilters={() => {
                onClearFilters();
                setIsOpen(false);
              }}
              onSortChange={(sort) => {
                onSortChange(sort);
                setIsOpen(false);
              }}
              currentSort={currentSort}
              loading={loading}
            />
          </div>
        </div>
      )}
    </div>
  );
}
