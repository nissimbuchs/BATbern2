/**
 * Topic Management Page
 * Story 5.2: Topic Selection & Speaker Brainstorming
 *
 * Container page for TopicBacklogManager component with organizer dashboard integration
 */

import React, { useMemo } from 'react';
import { Box, Button } from '@mui/material';
import { BubbleChart as BubbleChartIcon } from '@mui/icons-material';
import { TopicBacklogManager } from '@/components/TopicBacklogManager';
import { useSearchParams, useNavigate } from 'react-router-dom';
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
  const navigate = useNavigate();

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

  return (
    <Box>
      <Box sx={{ py: 3, px: 3 }}>
        {/* Breadcrumbs */}
        <Breadcrumbs items={breadcrumbItems} marginBottom={2} />

        {/* Blob Selector button — only when eventCode is present (AC: 1) */}
        {eventCode && (
          <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
            <Button
              variant="outlined"
              startIcon={<BubbleChartIcon />}
              onClick={() => navigate(`/organizer/events/${eventCode}/topic-blob`)}
              data-testid="blob-selector-button"
            >
              {t('navigation.blobSelector', 'Blob Selector')}
            </Button>
          </Box>
        )}
      </Box>

      <TopicBacklogManager eventCode={eventCode || undefined} />
    </Box>
  );
};

export default TopicManagementPage;
