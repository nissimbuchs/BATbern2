/**
 * PartnerAttendanceDashboard
 * Story 8.1: AC1, AC2, AC3, AC7, AC8
 *
 * Two charts replacing the raw attendance table:
 *
 * Chart 1 — Attendance per event (ComposedChart):
 *   Bars = this company's attendees per event  |  Line = total event attendees
 *   Shows: "are we growing in step with BATbern?"
 *
 * Chart 2 — Year-over-year trend (ComposedChart):
 *   Bars = company headcount per year  |  Line (right axis) = attendance share %
 *   Shows: "are we becoming a larger or smaller slice over time?"
 *
 * Both charts computed from the existing /analytics/dashboard API payload.
 * No new backend endpoints required.
 */
import React, { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Bar,
  CartesianGrid,
  ComposedChart,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import {
  Alert,
  Box,
  Button,
  ButtonGroup,
  Card,
  CardContent,
  Container,
  Paper,
  Skeleton,
  Typography,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { getAttendanceDashboard } from '@/services/api/partnerAnalyticsApi';
import { AttendanceExportButton } from './AttendanceExportButton';
import { CHART_COLORS } from '@/components/organizer/Analytics/CHART_COLORS';

interface Props {
  companyName: string;
}

const CURRENT_YEAR = new Date().getFullYear();
const FROM_YEAR_5 = CURRENT_YEAR - 5;

// Reusable chart card wrapper
const ChartSection: React.FC<{ title: string; children: React.ReactNode }> = ({
  title,
  children,
}) => (
  <Paper variant="outlined" sx={{ p: 3, mb: 3 }}>
    <Typography variant="subtitle1" fontWeight={600} mb={2}>
      {title}
    </Typography>
    {children}
  </Paper>
);

export const PartnerAttendanceDashboard: React.FC<Props> = ({ companyName }) => {
  const { t } = useTranslation('partners');
  const [fromYear, setFromYear] = useState<number>(FROM_YEAR_5);

  const { data, isLoading, isError } = useQuery({
    queryKey: ['partnerAttendanceDashboard', companyName, fromYear],
    queryFn: () => getAttendanceDashboard(companyName, fromYear),
    staleTime: 15 * 60 * 1000,
  });

  // ─── Loading state ─────────────────────────────────────────────────────────

  if (isLoading) {
    return (
      <Container maxWidth="xl" sx={{ py: 4 }} data-testid="analytics-loading">
        <Skeleton variant="text" width={300} height={40} sx={{ mb: 2 }} />
        <Box sx={{ display: 'flex', gap: 2, mb: 3 }}>
          <Skeleton variant="rectangular" width={200} height={100} />
          <Skeleton variant="rectangular" width={200} height={100} />
        </Box>
        <Skeleton variant="rectangular" height={300} sx={{ mb: 3 }} />
        <Skeleton variant="rectangular" height={250} />
      </Container>
    );
  }

  // ─── Error state ───────────────────────────────────────────────────────────

  if (isError) {
    return (
      <Container maxWidth="xl" sx={{ py: 4 }}>
        <Alert severity="error" data-testid="analytics-error">
          {t('portal.analytics.error')}
        </Alert>
      </Container>
    );
  }

  // ─── Data preparation ──────────────────────────────────────────────────────

  const summaries = data?.attendanceSummary ?? [];

  const totalCompanyAttendees = summaries.reduce((sum, s) => sum + s.companyAttendees, 0);
  const totalAttendees = summaries.reduce((sum, s) => sum + s.totalAttendees, 0);
  const avgAttendanceRate = totalAttendees > 0 ? totalCompanyAttendees / totalAttendees : 0;

  return (
    <Container maxWidth="xl" sx={{ py: 4 }} data-testid="attendance-dashboard">
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
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
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

      {summaries.length === 0 ? (
        <Typography color="text.secondary" data-testid="no-data-message">
          {t('portal.analytics.noData')}
        </Typography>
      ) : (
        <>
          <Chart1PerEvent summaries={summaries} />
          <Chart2YearOverYear summaries={summaries} />
        </>
      )}
    </Container>
  );
};

// ─── Chart 1: Attendance per event ────────────────────────────────────────────

interface SummaryRow {
  eventCode: string;
  eventTitle: string;
  eventDate: string;
  companyAttendees: number;
  totalAttendees: number;
}

const Chart1PerEvent: React.FC<{ summaries: SummaryRow[] }> = ({ summaries }) => {
  const rows = useMemo(
    () =>
      [...summaries]
        .sort((a, b) => new Date(a.eventDate).getTime() - new Date(b.eventDate).getTime())
        .map((s) => ({
          eventCode: s.eventCode,
          title: s.eventTitle || s.eventCode,
          company: s.companyAttendees,
          total: s.totalAttendees,
        })),
    [summaries]
  );

  return (
    <ChartSection title="Attendance per Event — Your Company vs. Total">
      <ResponsiveContainer width="100%" height={320}>
        <ComposedChart data={rows} margin={{ top: 4, right: 40, left: 0, bottom: 56 }}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis
            dataKey="title"
            angle={-45}
            textAnchor="end"
            interval={0}
            tick={{ fontSize: 10 }}
          />
          <YAxis allowDecimals={false} />
          <Tooltip
            content={({ active, payload }) => {
              if (!active || !payload?.length) return null;
              // eslint-disable-next-line @typescript-eslint/no-explicit-any
              const row = payload[0].payload as any;
              return (
                <div
                  style={{
                    background: '#fff',
                    border: '1px solid #ccc',
                    borderRadius: 4,
                    padding: '8px 12px',
                    fontSize: 12,
                  }}
                >
                  <div style={{ fontWeight: 600, marginBottom: 4 }}>{row.eventCode}</div>
                  {payload.map((p) => (
                    <div key={p.dataKey as string}>
                      {p.dataKey === 'company' ? 'Your attendees' : 'Total attendees'}: {p.value}
                    </div>
                  ))}
                </div>
              );
            }}
          />
          <Bar dataKey="company" name="company" fill={CHART_COLORS.partner} />
          <Line
            type="monotone"
            dataKey="total"
            name="total"
            stroke={CHART_COLORS.primary}
            dot={false}
            strokeWidth={2}
            strokeDasharray="5 3"
          />
        </ComposedChart>
      </ResponsiveContainer>
    </ChartSection>
  );
};

// ─── Chart 2: Year-over-year trend ────────────────────────────────────────────

const Chart2YearOverYear: React.FC<{ summaries: SummaryRow[] }> = ({ summaries }) => {
  const rows = useMemo(() => {
    const byYear: Record<string, { company: number; total: number }> = {};
    for (const s of summaries) {
      const year = new Date(s.eventDate).getFullYear().toString();
      if (!byYear[year]) byYear[year] = { company: 0, total: 0 };
      byYear[year].company += s.companyAttendees;
      byYear[year].total += s.totalAttendees;
    }
    return Object.entries(byYear)
      .sort(([a], [b]) => Number(a) - Number(b))
      .map(([year, { company, total }]) => ({
        year,
        company,
        total,
        sharePct: total > 0 ? Math.round((company / total) * 100) : 0,
      }));
  }, [summaries]);

  return (
    <ChartSection title="Year-over-Year — Headcount & Attendance Share %">
      <ResponsiveContainer width="100%" height={280}>
        <ComposedChart data={rows} margin={{ top: 4, right: 48, left: 0, bottom: 4 }}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="year" />
          <YAxis yAxisId="count" allowDecimals={false} />
          <YAxis
            yAxisId="pct"
            orientation="right"
            tickFormatter={(v) => `${v}%`}
            domain={[0, 100]}
          />
          <Tooltip
            formatter={(value, name) => {
              if (name === 'sharePct') return [`${value}%`, 'Share of total'];
              if (name === 'company') return [value, 'Your attendees'];
              return [value, name];
            }}
          />
          <Bar yAxisId="count" dataKey="company" name="company" fill={CHART_COLORS.partner} />
          <Line
            yAxisId="pct"
            type="monotone"
            dataKey="sharePct"
            name="sharePct"
            stroke={CHART_COLORS.primary}
            dot={{ r: 4 }}
            strokeWidth={2}
          />
        </ComposedChart>
      </ResponsiveContainer>
    </ChartSection>
  );
};
