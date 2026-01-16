/**
 * ArchivePage Component (Story 4.2 - Task 2b)
 *
 * Historical event archive browsing page
 * Features: grid/list toggle, infinite scroll, filtering, sorting
 */

import { useState, useEffect, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useInView } from 'react-intersection-observer';
import { useTranslation } from 'react-i18next';
import { Helmet } from 'react-helmet-async';
import { PublicLayout } from '@/components/public/PublicLayout';
import { EventCard } from '@/components/public/EventCard';
import { FilterSidebar } from '@/components/public/FilterSidebar';
import { FilterSheet } from '@/components/public/FilterSheet';
import { OpenGraphTags } from '@/components/SEO/OpenGraphTags';
import { useInfiniteEvents } from '@/hooks/useInfiniteEvents';
import { topicService } from '@/services/topicService';
import type { ArchiveFilters, EventDetailUI } from '@/types/event.types';
import type { Topic } from '@/types/topic.types';

type ViewMode = 'grid' | 'list';

export default function ArchivePage() {
  const { t } = useTranslation();
  const [searchParams, setSearchParams] = useSearchParams();
  const { ref, inView } = useInView({
    threshold: 0,
    rootMargin: '200px', // Trigger loading 200px before reaching the element
  });

  // View mode (persisted to localStorage)
  const [viewMode, setViewMode] = useState<ViewMode>(() => {
    const saved = localStorage.getItem('archive-view-mode');
    return (saved as ViewMode) || 'grid';
  });

  // Topics state (loaded from API)
  const [topics, setTopics] = useState<Topic[]>([]);
  const [topicsLoading, setTopicsLoading] = useState(true);

  // Fetch topics on component mount
  useEffect(() => {
    const fetchTopics = async () => {
      try {
        setTopicsLoading(true);
        // Fetch all topics, sorted by title
        const response = await topicService.getTopics({
          sort: 'title',
          limit: 100, // Get all topics
        });
        setTopics(response.data);
      } catch (error) {
        console.error('Failed to load topics:', error);
        setTopics([]); // Fail gracefully - empty topic list
      } finally {
        setTopicsLoading(false);
      }
    };

    fetchTopics();
  }, []);

  // Filters from URL query parameters
  const filters: ArchiveFilters = useMemo(() => {
    const topicsParam = searchParams.get('topics');
    const search = searchParams.get('q') || '';

    return {
      topics: topicsParam ? topicsParam.split(',') : [],
      search,
    };
  }, [searchParams]);

  // Sort from URL or default to newest first
  const sort = searchParams.get('sort') || '-date';

  // Fetch events with infinite scroll
  const { data, isLoading, isError, fetchNextPage, hasNextPage, isFetchingNextPage } =
    useInfiniteEvents(filters, sort);

  // Flatten all pages into single events array
  const events = data?.pages.flatMap((page) => page.data) || [];
  const totalCount = data?.pages[0]?.pagination?.totalItems || 0;

  // Auto-fetch next page when scroll trigger is in view
  useEffect(() => {
    console.log('🔍 Scroll Trigger Effect:', {
      inView,
      hasNextPage,
      isFetchingNextPage,
      eventsLength: events.length,
      totalCount,
    });
    if (inView && hasNextPage && !isFetchingNextPage) {
      console.log('✅ Fetching next page...');
      // Small delay to ensure state is stable before fetching
      const timer = setTimeout(() => {
        fetchNextPage();
      }, 100);
      return () => clearTimeout(timer);
    }
  }, [inView, hasNextPage, isFetchingNextPage, fetchNextPage, events.length, totalCount]);

  // Handle view mode toggle
  const handleViewModeChange = (mode: ViewMode) => {
    setViewMode(mode);
    localStorage.setItem('archive-view-mode', mode);
  };

  // Handle filter changes - update URL query parameters
  const handleFilterChange = (newFilters: ArchiveFilters) => {
    const params = new URLSearchParams(searchParams);

    // Update or remove topics parameter
    if (newFilters.topics && newFilters.topics.length > 0) {
      params.set('topics', newFilters.topics.join(','));
    } else {
      params.delete('topics');
    }

    // Update or remove search parameter
    if (newFilters.search) {
      params.set('q', newFilters.search);
    } else {
      params.delete('q');
    }

    setSearchParams(params);
  };

  // Handle clear filters
  const handleClearFilters = () => {
    setSearchParams({});
  };

  // Handle sort change
  const handleSortChange = (newSort: string) => {
    const params = new URLSearchParams(searchParams);
    if (newSort === '-date') {
      params.delete('sort');
    } else {
      params.set('sort', newSort);
    }
    setSearchParams(params);
  };

  // SEO metadata
  const pageUrl = `${window.location.origin}/archive${window.location.search}`;
  const pageTitle = filters.search
    ? `Search Results: ${filters.search}`
    : totalCount > 0
      ? `${totalCount} Historical Events`
      : 'Historical Events Archive';
  const pageDescription = `Browse ${totalCount > 0 ? totalCount : '54+'} BATbern conference events spanning 20+ years. Filter by time period, topics, and search presentations.`;

  return (
    <PublicLayout>
      <OpenGraphTags
        title={pageTitle}
        description={pageDescription}
        url={pageUrl}
        image="https://cdn.batbern.ch/assets/archive-cover.jpg"
      />

      {/* JSON-LD Structured Data for Archive */}
      <Helmet>
        <script type="application/ld+json">
          {JSON.stringify({
            '@context': 'https://schema.org',
            '@type': 'CollectionPage',
            name: 'BATbern Historical Events Archive',
            description: pageDescription,
            url: pageUrl,
            inLanguage: 'de-CH',
            isPartOf: {
              '@type': 'WebSite',
              name: 'BATbern',
              url: window.location.origin,
            },
            numberOfItems: totalCount,
          })}
        </script>
      </Helmet>

      <div className="container mx-auto px-4 py-8">
        {/* Header */}
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-foreground mb-2">{t('archive.title')}</h1>
          <p className="text-muted-foreground">{t('archive.description')}</p>
        </div>

        <div className="flex gap-8">
          {/* Filter Sidebar (Desktop) */}
          <aside className="hidden lg:block w-64 flex-shrink-0">
            <FilterSidebar
              filters={filters}
              topics={topics}
              onFilterChange={handleFilterChange}
              onClearFilters={handleClearFilters}
              onSortChange={handleSortChange}
              currentSort={sort}
              loading={topicsLoading}
            />
          </aside>

          {/* Filter Sheet (Mobile) */}
          <div className="lg:hidden">
            <FilterSheet
              filters={filters}
              topics={topics}
              onFilterChange={handleFilterChange}
              onClearFilters={handleClearFilters}
              onSortChange={handleSortChange}
              currentSort={sort}
              loading={topicsLoading}
            />
          </div>

          {/* Main Content */}
          <main className="flex-1">
            {/* View Toggle */}
            <div className="flex justify-between items-center mb-6">
              <div className="text-sm text-gray-600">
                {!isLoading && totalCount > 0 && `${events.length} of ${totalCount} events`}
              </div>
              <div className="flex gap-2">
                <button
                  data-testid="view-toggle-grid"
                  onClick={() => handleViewModeChange('grid')}
                  className={`px-3 py-2 rounded-md text-sm ${
                    viewMode === 'grid' ? 'bg-blue-600 text-white' : 'bg-gray-200 text-gray-700'
                  }`}
                  aria-label={t('archive.viewToggle.grid')}
                >
                  {t('archive.viewToggle.grid')}
                </button>
                <button
                  data-testid="view-toggle-list"
                  onClick={() => handleViewModeChange('list')}
                  className={`px-3 py-2 rounded-md text-sm ${
                    viewMode === 'list' ? 'bg-blue-600 text-white' : 'bg-gray-200 text-gray-700'
                  }`}
                  aria-label={t('archive.viewToggle.list')}
                >
                  {t('archive.viewToggle.list')}
                </button>
              </div>
            </div>

            {/* Error State */}
            {isError && (
              <div className="text-center py-12 text-red-600">{t('archive.errors.loadFailed')}</div>
            )}

            {/* Loading State */}
            {isLoading && (
              <div className="text-center py-12 text-gray-600">{t('archive.loading')}</div>
            )}

            {/* Event Cards */}
            {!isLoading && !isError && (
              <>
                {events.length === 0 ? (
                  <div className="text-center py-12 text-gray-600">{t('archive.noResults')}</div>
                ) : (
                  <div
                    className={
                      viewMode === 'grid'
                        ? 'grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6'
                        : 'space-y-4'
                    }
                  >
                    {events.map((event) => (
                      <EventCard
                        key={event.eventCode}
                        event={
                          {
                            ...event,
                            sessions: event.sessions ?? undefined,
                          } as EventDetailUI
                        }
                        viewMode={viewMode}
                      />
                    ))}
                  </div>
                )}

                {/* Infinite Scroll Trigger */}
                {hasNextPage && (
                  <div ref={ref} className="text-center py-32 min-h-[200px]">
                    {isFetchingNextPage ? (
                      <div className="text-gray-600">{t('archive.loadingMore')}</div>
                    ) : (
                      <div className="text-gray-400 text-sm">{t('archive.scrollForMore')}</div>
                    )}
                  </div>
                )}
              </>
            )}
          </main>
        </div>
      </div>
    </PublicLayout>
  );
}
