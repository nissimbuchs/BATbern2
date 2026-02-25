/**
 * CompanyAttendanceOverTimeChart
 * Story 10.5: Analytics Dashboard (AC5)
 *
 * Stacked BarChart: X = year, stacked segments = companies.
 * Partner's own company always shown even if outside Top N.
 */

import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import type { CompanyYearAttendanceItem } from '@/services/analyticsService';
import { CHART_COLORS } from './CHART_COLORS';
import ChartCard from './ChartCard';

const EXTRA_COLORS = [
  CHART_COLORS.primary,
  CHART_COLORS.light,
  CHART_COLORS.success,
  CHART_COLORS.purple,
  CHART_COLORS.info,
  CHART_COLORS.grey,
];

interface Props {
  data: CompanyYearAttendanceItem[];
  topN: number | null;
  partnerCompany?: string | null;
  isLoading?: boolean;
}

const CompanyAttendanceOverTimeChart = ({
  data,
  topN,
  partnerCompany,
  isLoading,
}: Props) => {
  const { t } = useTranslation('organizer');

  const { years, companies, pivoted } = useMemo(() => {
    const yearSet = new Set<number>();
    const companyCount: Record<string, number> = {};
    for (const row of data) {
      yearSet.add(row.year);
      companyCount[row.companyName] =
        (companyCount[row.companyName] ?? 0) + row.attendeeCount;
    }
    let sorted = Object.entries(companyCount)
      .sort(([, a], [, b]) => b - a)
      .map(([c]) => c);

    if (topN !== null) {
      const topSlice = sorted.slice(0, topN);
      if (partnerCompany && !topSlice.includes(partnerCompany)) {
        topSlice.push(partnerCompany);
      }
      sorted = topSlice;
    }

    const yearsSorted = [...yearSet].sort();
    const pivotedRows = yearsSorted.map((year) => {
      const row: Record<string, number | string> = { year: String(year) };
      for (const company of sorted) {
        const match = data.find(
          (d) => d.year === year && d.companyName === company
        );
        row[company] = match?.attendeeCount ?? 0;
      }
      return row;
    });

    return { years: yearsSorted, companies: sorted, pivoted: pivotedRows };
  }, [data, topN, partnerCompany]);

  return (
    <ChartCard
      title={t('analytics.charts.attendanceOverTime')}
      isLoading={isLoading}
      isEmpty={!isLoading && years.length === 0}
    >
      <ResponsiveContainer width="100%" height={350}>
        <BarChart data={pivoted} margin={{ top: 4, right: 16, left: 0, bottom: 4 }}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="year" />
          <YAxis allowDecimals={false} />
          <Tooltip />
          <Legend />
          {companies.map((company, idx) => (
            <Bar
              key={company}
              dataKey={company}
              stackId="a"
              fill={
                company === partnerCompany
                  ? CHART_COLORS.partner
                  : EXTRA_COLORS[idx % EXTRA_COLORS.length]
              }
            />
          ))}
        </BarChart>
      </ResponsiveContainer>
    </ChartCard>
  );
};

export default CompanyAttendanceOverTimeChart;
