/**
 * SessionsPerCompanyChart
 * Story 10.5: Analytics Dashboard (AC5)
 *
 * BarChart: each bar = one company sorted descending by session count.
 * Secondary indicator: unique speaker count shown as label inside/above bar.
 */

import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  LabelList,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import type { CompanySessionItem } from '@/services/analyticsService';
import { CHART_COLORS } from './CHART_COLORS';
import ChartCard from './ChartCard';
import DataTable from './DataTable';
import type { ColumnDef } from './DataTable';

interface Props {
  data: CompanySessionItem[];
  topN: number | null;
  partnerCompany?: string | null;
  isLoading?: boolean;
}

const SessionsPerCompanyChart = ({ data, topN, partnerCompany, isLoading }: Props) => {
  const { t } = useTranslation('organizer');

  const filtered = useMemo(() => {
    if (topN === null) {
      return data;
    }
    const slice = data.slice(0, topN);
    if (partnerCompany && !slice.find((d) => d.companyName === partnerCompany)) {
      const own = data.find((d) => d.companyName === partnerCompany);
      if (own) {
        slice.push(own);
      }
    }
    return slice;
  }, [data, topN, partnerCompany]);

  // Build a map for XAxis tick formatting
  const displayNameMap = useMemo(
    () => Object.fromEntries(filtered.map((d) => [d.companyName, d.displayName ?? d.companyName])),
    [filtered]
  );

  const columns: ColumnDef<CompanySessionItem>[] = [
    { key: 'displayName', label: 'Company' },
    { key: 'sessionCount', label: t('analytics.labels.sessions'), align: 'right' },
    { key: 'uniqueSpeakers', label: t('common:navigation.speakers'), align: 'right' },
  ];

  return (
    <ChartCard
      title={t('analytics.charts.sessionsPerCompany')}
      isLoading={isLoading}
      isEmpty={!isLoading && filtered.length === 0}
      dataTable={<DataTable columns={columns} rows={filtered} rowKey="companyName" />}
    >
      <ResponsiveContainer width="100%" height={350}>
        <BarChart data={filtered} margin={{ top: 24, right: 16, left: 0, bottom: 64 }}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis
            dataKey="companyName"
            tickFormatter={(v) => displayNameMap[v] ?? v}
            angle={-45}
            textAnchor="end"
            interval={0}
            tick={{ fontSize: 10 }}
          />
          <YAxis allowDecimals={false} />
          <Tooltip
            content={({ active, payload, label }) => {
              if (!active || !payload?.length) return null;
              const item = payload[0].payload as CompanySessionItem;
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
                  <p style={{ fontWeight: 600, marginBottom: 4 }}>
                    {displayNameMap[label as string] ?? label}
                  </p>
                  <p>
                    {t('analytics.labels.sessions')}: {item.sessionCount}
                  </p>
                  <p>
                    {t('common:navigation.speakers')}: {item.uniqueSpeakers}
                  </p>
                </div>
              );
            }}
          />
          <Bar dataKey="sessionCount" name="sessionCount">
            <LabelList
              dataKey="sessionCount"
              position="top"
              style={{ fontSize: 10, fill: '#555' }}
            />
            {filtered.map((entry) => (
              <Cell
                key={entry.companyName}
                fill={
                  entry.companyName === partnerCompany ? CHART_COLORS.partner : CHART_COLORS.primary
                }
              />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </ChartCard>
  );
};

export default SessionsPerCompanyChart;
