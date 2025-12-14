/**
 * Topic Management Page
 * Story 5.2: Topic Selection & Speaker Brainstorming
 *
 * Container page for TopicBacklogManager component with organizer dashboard integration
 */

import React from 'react';
import { Box, Container, Typography } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { TopicBacklogManager } from '@/components/TopicBacklogManager';
import { useSearchParams } from 'react-router-dom';

/**
 * Topic Management Page Component
 *
 * Provides the main container for topic selection and management.
 * If eventCode is provided in query params, enables event-specific topic selection.
 */
const TopicManagementPage: React.FC = () => {
  const { t } = useTranslation('organizer');
  const [searchParams] = useSearchParams();
  const eventCode = searchParams.get('eventCode');

  console.log('[TopicManagementPage] Rendering', {
    eventCode,
    searchParamsKeys: Array.from(searchParams.keys()),
    fullUrl: window.location.href,
  });

  return (
    <Container maxWidth="xl" sx={{ py: 4 }}>
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          {t('topicBacklog.pageTitle')}
        </Typography>
        <Typography variant="body1" color="text.secondary">
          {t('topicBacklog.pageDescription')}
        </Typography>
      </Box>

      <TopicBacklogManager eventCode={eventCode || undefined} />
    </Container>
  );
};

export default TopicManagementPage;
