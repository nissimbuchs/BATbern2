/**
 * Topic Management Page
 * Story 5.2: Topic Selection & Speaker Brainstorming
 *
 * Container page for TopicBacklogManager component with organizer dashboard integration
 */

import React from 'react';
import { Box } from '@mui/material';
import { TopicBacklogManager } from '@/components/TopicBacklogManager';
import { useSearchParams } from 'react-router-dom';

/**
 * Topic Management Page Component
 *
 * Provides the main container for topic selection and management.
 * If eventCode is provided in query params, enables event-specific topic selection.
 */
const TopicManagementPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const eventCode = searchParams.get('eventCode');

  console.log('[TopicManagementPage] Rendering', {
    eventCode,
    searchParamsKeys: Array.from(searchParams.keys()),
    fullUrl: window.location.href,
  });

  return (
    <Box>
      <TopicBacklogManager eventCode={eventCode || undefined} />
    </Box>
  );
};

export default TopicManagementPage;
