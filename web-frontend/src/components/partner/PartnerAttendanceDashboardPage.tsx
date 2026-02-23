/**
 * PartnerAttendanceDashboardPage
 * Story 8.1: AC1, AC2, AC3, AC7, AC8
 *
 * Thin page wrapper: resolves companyName from auth context, then renders PartnerAttendanceDashboard.
 * Mirrors PartnerCompanyPage pattern (Story 8.0).
 */
import React from 'react';
import { Container, Alert, AlertTitle } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/hooks/useAuth';
import { PartnerAttendanceDashboard } from './PartnerAttendanceDashboard';

const PartnerAttendanceDashboardPage: React.FC = () => {
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

  return <PartnerAttendanceDashboard companyName={user.companyName} />;
};

export default PartnerAttendanceDashboardPage;
