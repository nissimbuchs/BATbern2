/**
 * TopicHeatMap Component (Story 5.2 - AC2)
 *
 * Displays topic usage heat map showing frequency over last 24 months.
 * Uses Recharts for visualization with quarterly grouping.
 */

import React, { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Box, Card, CardContent, Typography, Tooltip as MuiTooltip, useTheme } from '@mui/material';
import {
  ResponsiveContainer,
  ScatterChart,
  Scatter,
  XAxis,
  YAxis,
  ZAxis,
  Tooltip,
  Cell,
  Legend,
} from 'recharts';
import type { TopicUsageHistory } from '@/types/topic.types';

export interface TopicHeatMapProps {
  topicId: string;
  usageHistory: TopicUsageHistory[];
}

interface HeatMapDataPoint {
  quarter: string; // e.g., "Q1 2024"
  year: number;
  quarterNum: number;
  usageCount: number;
  totalAttendance: number;
  avgEngagement: number;
  events: number[]; // Event numbers for tooltip (GitHub Issue #379: no UUIDs in API)
  intensity: number; // 0-100 for color mapping
}

const TopicHeatMap: React.FC<TopicHeatMapProps> = ({
  topicId: _topicId, // eslint-disable-line @typescript-eslint/no-unused-vars
  usageHistory,
}) => {
  const { t } = useTranslation('organizer');
  const theme = useTheme();

  // Process usage history into quarterly buckets
  const heatMapData = useMemo(() => {
    if (!usageHistory || usageHistory.length === 0) {
      return [];
    }

    // Group by quarter
    const quarterlyData = new Map<string, HeatMapDataPoint>();

    usageHistory.forEach((usage) => {
      const date = new Date(usage.usedDate);
      const year = date.getFullYear();
      const month = date.getMonth();
      const quarterNum = Math.floor(month / 3) + 1;
      const quarterKey = `Q${quarterNum} ${year}`;

      const existing = quarterlyData.get(quarterKey);
      if (existing) {
        existing.usageCount += 1;
        existing.totalAttendance += usage.attendance;
        existing.avgEngagement =
          (existing.avgEngagement * (existing.usageCount - 1) + usage.engagementScore) /
          existing.usageCount;
        existing.events.push(usage.eventNumber);
      } else {
        quarterlyData.set(quarterKey, {
          quarter: quarterKey,
          year,
          quarterNum,
          usageCount: 1,
          totalAttendance: usage.attendance,
          avgEngagement: usage.engagementScore,
          events: [usage.eventNumber],
          intensity: 0, // Will calculate below
        });
      }
    });

    // Convert to array and calculate intensity (0-100) based on usage count
    const dataArray = Array.from(quarterlyData.values());
    const maxUsage = Math.max(...dataArray.map((d) => d.usageCount));

    return dataArray.map((point) => ({
      ...point,
      intensity: maxUsage > 0 ? (point.usageCount / maxUsage) * 100 : 0,
    }));
  }, [usageHistory]);

  // Get color based on intensity
  const getHeatColor = (intensity: number): string => {
    if (intensity === 0) return theme.palette.grey[200];
    if (intensity < 33) return theme.palette.info.light;
    if (intensity < 66) return theme.palette.warning.light;
    return theme.palette.error.light;
  };

  // Custom tooltip
  interface TooltipProps {
    active?: boolean;
    payload?: Array<{ payload: HeatMapDataPoint }>;
  }

  const CustomTooltip = ({ active, payload }: TooltipProps) => {
    if (!active || !payload || !payload.length) return null;

    const data = payload[0].payload as HeatMapDataPoint;

    return (
      <Card sx={{ minWidth: 200 }}>
        <CardContent>
          <Typography variant="subtitle2" gutterBottom>
            {data.quarter}
          </Typography>
          <Typography variant="body2">
            {t('topicBacklog.heatMap.usageCount', 'Usage Count')}: {data.usageCount}
          </Typography>
          <Typography variant="body2">
            {t('topicBacklog.heatMap.totalAttendance', 'Total Attendance')}: {data.totalAttendance}
          </Typography>
          <Typography variant="body2">
            {t('topicBacklog.heatMap.avgEngagement', 'Avg Engagement')}:{' '}
            {(data.avgEngagement * 100).toFixed(1)}%
          </Typography>
          <Typography variant="caption" color="text.secondary" sx={{ mt: 1 }}>
            {t('topicBacklog.heatMap.events', 'Events')}: {data.events.length}
          </Typography>
        </CardContent>
      </Card>
    );
  };

  if (!heatMapData || heatMapData.length === 0) {
    return (
      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            {t('topicBacklog.heatMap.title', 'Usage Heat Map')}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {t('topicBacklog.heatMap.noData', 'No usage history available for this topic.')}
          </Typography>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardContent>
        <Typography variant="h6" gutterBottom>
          {t('topicBacklog.heatMap.title', 'Usage Heat Map')}
        </Typography>
        <Typography variant="body2" color="text.secondary" gutterBottom>
          {t(
            'topicBacklog.heatMap.description',
            'Topic usage frequency over the last 24 months (grouped by quarter)'
          )}
        </Typography>

        <Box sx={{ width: '100%', height: 400, mt: 2 }}>
          <ResponsiveContainer width="100%" height="100%">
            <ScatterChart margin={{ top: 20, right: 20, bottom: 60, left: 60 }}>
              <XAxis
                type="category"
                dataKey="quarter"
                name={t('topicBacklog.heatMap.quarter', 'Quarter')}
                angle={-45}
                textAnchor="end"
                height={80}
              />
              <YAxis
                type="number"
                dataKey="usageCount"
                name={t('topicBacklog.heatMap.usageCount', 'Usage Count')}
              />
              <ZAxis type="number" dataKey="intensity" range={[100, 1000]} />
              <Tooltip content={<CustomTooltip />} cursor={{ strokeDasharray: '3 3' }} />
              <Legend
                wrapperStyle={{ paddingTop: 20 }}
                content={() => (
                  <Box sx={{ display: 'flex', justifyContent: 'center', gap: 2, mt: 2 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                      <Box
                        sx={{
                          width: 16,
                          height: 16,
                          bgcolor: theme.palette.info.light,
                          border: `1px solid ${theme.palette.divider}`,
                        }}
                      />
                      <Typography variant="caption">
                        {t('topicBacklog.heatMap.low', 'Low Usage')}
                      </Typography>
                    </Box>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                      <Box
                        sx={{
                          width: 16,
                          height: 16,
                          bgcolor: theme.palette.warning.light,
                          border: `1px solid ${theme.palette.divider}`,
                        }}
                      />
                      <Typography variant="caption">
                        {t('topicBacklog.heatMap.medium', 'Medium Usage')}
                      </Typography>
                    </Box>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                      <Box
                        sx={{
                          width: 16,
                          height: 16,
                          bgcolor: theme.palette.error.light,
                          border: `1px solid ${theme.palette.divider}`,
                        }}
                      />
                      <Typography variant="caption">
                        {t('topicBacklog.heatMap.high', 'High Usage')}
                      </Typography>
                    </Box>
                  </Box>
                )}
              />
              <Scatter name="Usage" data={heatMapData} fill={theme.palette.primary.main}>
                {heatMapData.map((entry, index) => (
                  <Cell key={`cell-${index}`} fill={getHeatColor(entry.intensity)} />
                ))}
              </Scatter>
            </ScatterChart>
          </ResponsiveContainer>
        </Box>

        <MuiTooltip
          title={t(
            'topicBacklog.heatMap.tooltip',
            'Click on a quarter to see details about events that used this topic'
          )}
        >
          <Typography variant="caption" color="text.secondary" sx={{ mt: 2, display: 'block' }}>
            {t('topicBacklog.heatMap.hint', 'Hover over data points for more information')}
          </Typography>
        </MuiTooltip>
      </CardContent>
    </Card>
  );
};

export default TopicHeatMap;
