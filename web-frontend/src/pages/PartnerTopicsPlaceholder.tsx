/**
 * PartnerTopicsPlaceholder
 * Story 8.0: AC4 — temporary placeholder; Story 8.2 replaces this entirely
 */
import React from 'react';
import { Container, Typography } from '@mui/material';
import { useTranslation } from 'react-i18next';

const PartnerTopicsPlaceholder: React.FC = () => {
  const { t } = useTranslation('partners');

  return (
    <Container maxWidth="xl" sx={{ py: 4 }}>
      <Typography variant="h5" color="text.secondary" data-testid="topics-placeholder">
        {t('portal.topics.comingSoon')}
      </Typography>
    </Container>
  );
};

export default PartnerTopicsPlaceholder;
