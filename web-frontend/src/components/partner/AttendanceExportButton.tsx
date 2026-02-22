/**
 * AttendanceExportButton
 * Story 8.1: AC4 — Export attendance table as XLSX.
 */
import React, { useState } from 'react';
import { Button, CircularProgress } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { exportAttendanceReport } from '@/services/api/partnerAnalyticsApi';

interface Props {
  companyName: string;
  fromYear?: number;
}

export const AttendanceExportButton: React.FC<Props> = ({ companyName, fromYear }) => {
  const { t } = useTranslation('partners');
  const [loading, setLoading] = useState(false);

  const handleExport = async () => {
    setLoading(true);
    try {
      await exportAttendanceReport(companyName, fromYear);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Button
      variant="outlined"
      size="small"
      onClick={handleExport}
      disabled={loading}
      data-testid="export-button"
      startIcon={loading ? <CircularProgress size={16} /> : undefined}
    >
      {loading ? t('portal.analytics.export.loading') : t('portal.analytics.export.button')}
    </Button>
  );
};
