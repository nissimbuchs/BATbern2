/**
 * Topic Management Page
 * Story 5.2: Topic Selection & Speaker Brainstorming
 *
 * Container page for TopicBacklogManager component with organizer dashboard integration
 */

import React, { useMemo } from 'react';
import { Box } from '@mui/material';
import { TopicBacklogManager } from '@/components/TopicBacklogManager';
import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Breadcrumbs } from '@/components/shared/Breadcrumbs';
import type { BreadcrumbItem } from '@/components/shared/Breadcrumbs';
import { useEvent } from '@/hooks/useEvents';

/**
 * Topic Management Page Component
 *
 * Provides the main container for topic selection and management.
 * If eventCode is provided in query params, enables event-specific topic selection.
 */
const TopicManagementPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const eventCode = searchParams.get('eventCode');
  const { t } = useTranslation('events');

  // Fetch event data if eventCode is provided
  const { data: event } = useEvent(eventCode || undefined);

  // Build breadcrumb items (memoized to prevent re-renders)
  const breadcrumbItems: BreadcrumbItem[] = useMemo(() => {
    if (eventCode && event) {
      // Event-specific topic management
      return [
        { label: t('navigation.events', 'Events'), path: '/organizer/events' },
        {
          label: event.title || t('common.loading', 'Loading...'),
          path: `/organizer/events/${eventCode}`,
        },
        { label: t('navigation.topicManagement', 'Topic Management') },
      ];
    }
    // Standalone topic management
    return [
      { label: t('navigation.events', 'Events'), path: '/organizer/events' },
      { label: t('navigation.topicManagement', 'Topic Management') },
    ];
  }, [eventCode, event?.title, t]);

  console.log('[TopicManagementPage] Rendering', {
    eventCode,
    searchParamsKeys: Array.from(searchParams.keys()),
    fullUrl: window.location.href,
  });

  return (
    <Box>
      <Box sx={{ py: 3, px: 3 }}>
        {/* Breadcrumbs */}
        <Breadcrumbs items={breadcrumbItems} marginBottom={2} />
      </Box>

      <TopicBacklogManager eventCode={eventCode || undefined} />
    </Box>
  );
};

export default TopicManagementPage;
