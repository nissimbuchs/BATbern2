/**
 * PartnerCompanyPage
 * Story 8.0: AC1, AC4, AC5 — shows the partner's own company detail, resolved from auth context
 */
import React from 'react';
import { Container, Alert, AlertTitle } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/hooks/useAuth';
import { PartnerDetailScreen } from '@/components/organizer/PartnerManagement/PartnerDetailScreen';

const PartnerCompanyPage: React.FC = () => {
  const { t } = useTranslation('partners');
  const { user } = useAuth();

  if (!user?.companyName) {
    return (
      <Container maxWidth="xl" sx={{ py: 4 }}>
        <Alert severity="error" data-testid="no-company-linked-alert">
          <AlertTitle>{t('portal.noCompanyLinked')}</AlertTitle>
          {t('portal.noCompanyLinked.detail')}
        </Alert>
      </Container>
    );
  }

  return <PartnerDetailScreen companyName={user.companyName} />;
};

export default PartnerCompanyPage;
