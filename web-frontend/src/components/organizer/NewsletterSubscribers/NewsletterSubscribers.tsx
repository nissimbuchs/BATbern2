/**
 * NewsletterSubscribers — Route container
 *
 * Analogous to UserManagement.tsx
 *
 * Story 10.28: Newsletter Subscriber Management Page
 */

import React from 'react';
import { Box, Typography } from '@mui/material';
import { useTranslation } from 'react-i18next';
import NewsletterSubscriberList from './NewsletterSubscriberList';

const NewsletterSubscribers: React.FC = () => {
  const { t } = useTranslation('newsletterSubscribers');

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h4" component="h1" gutterBottom>
        {t('title')}
      </Typography>
      <NewsletterSubscriberList />
    </Box>
  );
};

export default NewsletterSubscribers;
