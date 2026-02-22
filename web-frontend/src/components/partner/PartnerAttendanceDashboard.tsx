/**
 * PartnerAttendanceDashboard
 * Story 8.1: AC1, AC2, AC3, AC7, AC8
 *
 * Displays per-event attendance table with KPI cards and a 5yr/all-history toggle.
 * Desktop layout only (no mobile breakpoints per SM decision 2026-02-21).
 */
import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Box,
  Button,
  ButtonGroup,
  Card,
  CardContent,
  Container,
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
  Alert,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { getAttendanceDashboard } from '@/services/api/partnerAnalyticsApi';
import { AttendanceExportButton } from './AttendanceExportButton';

interface Props {
  companyName: string;
}

const CURRENT_YEAR = new Date().getFullYear();
const FROM_YEAR_5 = CURRENT_YEAR - 5;

export const PartnerAttendanceDashboard: React.FC<Props> = ({ companyName }) => {
  const { t } = useTranslation('partners');
  const [fromYear, setFromYear] = useState<number>(FROM_YEAR_5);

  const { data, isLoading, isError } = useQuery({
    queryKey: ['partnerAttendanceDashboard', companyName, fromYear],
    queryFn: () => getAttendanceDashboard(companyName, fromYear),
    staleTime: 15 * 60 * 1000, // 15 minutes — mirrors server-side cache
  });

  // ─── Loading state ────────────────────────────────────────────────────────

  if (isLoading) {
    return (
      <Container maxWidth="xl" sx={{ py: 4 }} data-testid="analytics-loading">
        <Skeleton variant="text" width={300} height={40} sx={{ mb: 2 }} />
        <Box sx={{ display: 'flex', gap: 2, mb: 3 }}>
          <Skeleton variant="rectangular" width={200} height={100} />
          <Skeleton variant="rectangular" width={200} height={100} />
        </Box>
        <Skeleton variant="rectangular" height={300} />
      </Container>
    );
  }

  // ─── Error state ──────────────────────────────────────────────────────────

  if (isError) {
    return (
      <Container maxWidth="xl" sx={{ py: 4 }}>
        <Alert severity="error" data-testid="analytics-error">
          {t('portal.analytics.error')}
        </Alert>
      </Container>
    );
  }

  // ─── KPI computations ─────────────────────────────────────────────────────

  const summaries = data?.attendanceSummary ?? [];
  const totalCompanyAttendees = summaries.reduce((sum, s) => sum + s.companyAttendees, 0);
  const totalAttendees = summaries.reduce((sum, s) => sum + s.totalAttendees, 0);
  const avgAttendanceRate = totalAttendees > 0 ? totalCompanyAttendees / totalAttendees : 0;

  return (
    <Container maxWidth="xl" sx={{ py: 4 }} data-testid="attendance-dashboard">
      {/* Page title */}
      <Typography variant="h5" sx={{ mb: 3 }}>
        {t('portal.analytics.title')}
      </Typography>

      {/* KPI cards */}
      <Box sx={{ display: 'flex', gap: 2, mb: 3 }}>
        <Card sx={{ minWidth: 200 }} data-testid="kpi-attendance-rate">
          <CardContent>
            <Typography variant="subtitle2" color="text.secondary">
              {t('portal.analytics.kpi.attendanceRate')}
            </Typography>
            <Typography variant="h4">{(avgAttendanceRate * 100).toFixed(1)}%</Typography>
          </CardContent>
        </Card>

        <Card sx={{ minWidth: 200 }} data-testid="kpi-cost-per-attendee">
          <CardContent>
            <Typography variant="subtitle2" color="text.secondary">
              {t('portal.analytics.kpi.costPerAttendee')}
            </Typography>
            <Typography variant="h4">
              {data?.costPerAttendee != null
                ? `${t('portal.analytics.kpi.currency')} ${data.costPerAttendee.toFixed(2)}`
                : 'N/A'}
            </Typography>
          </CardContent>
        </Card>
      </Box>

      {/* Range toggle + export */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <ButtonGroup variant="outlined" size="small" data-testid="range-toggle">
          <Button
            variant={fromYear === FROM_YEAR_5 ? 'contained' : 'outlined'}
            onClick={() => setFromYear(FROM_YEAR_5)}
            data-testid="range-5years"
          >
            {t('portal.analytics.range.last5years')}
          </Button>
          <Button
            variant={fromYear === CURRENT_YEAR - 20 ? 'contained' : 'outlined'}
            onClick={() => setFromYear(CURRENT_YEAR - 20)}
            data-testid="range-allhistory"
          >
            {t('portal.analytics.range.allHistory')}
          </Button>
        </ButtonGroup>

        <AttendanceExportButton companyName={companyName} fromYear={fromYear} />
      </Box>

      {/* Attendance table */}
      {summaries.length === 0 ? (
        <Typography color="text.secondary" data-testid="no-data-message">
          {t('portal.analytics.noData')}
        </Typography>
      ) : (
        <Table data-testid="attendance-table" size="small">
          <TableHead>
            <TableRow>
              <TableCell>{t('portal.analytics.table.event')}</TableCell>
              <TableCell>{t('portal.analytics.table.date')}</TableCell>
              <TableCell align="right">{t('portal.analytics.table.yourAttendees')}</TableCell>
              <TableCell align="right">{t('portal.analytics.table.totalAttendees')}</TableCell>
              <TableCell align="right">{t('portal.analytics.table.percentage')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {summaries.map((row) => {
              const pct =
                row.totalAttendees > 0
                  ? ((row.companyAttendees / row.totalAttendees) * 100).toFixed(1)
                  : '0.0';
              const dateStr = new Date(row.eventDate).toLocaleDateString('de-CH');

              return (
                <TableRow key={row.eventCode}>
                  <TableCell>{row.eventCode}</TableCell>
                  <TableCell>{dateStr}</TableCell>
                  <TableCell align="right">{row.companyAttendees}</TableCell>
                  <TableCell align="right">{row.totalAttendees}</TableCell>
                  <TableCell align="right">{pct}%</TableCell>
                </TableRow>
              );
            })}

            {/* Totals row */}
            <TableRow sx={{ fontWeight: 'bold' }}>
              <TableCell colSpan={2}>
                <strong>Total</strong>
              </TableCell>
              <TableCell align="right">
                <strong>{totalCompanyAttendees}</strong>
              </TableCell>
              <TableCell align="right">
                <strong>{totalAttendees}</strong>
              </TableCell>
              <TableCell align="right">
                <strong>
                  {totalAttendees > 0
                    ? ((totalCompanyAttendees / totalAttendees) * 100).toFixed(1)
                    : '0.0'}
                  %
                </strong>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      )}
    </Container>
  );
};
