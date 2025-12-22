/**
 * MultiTopicHeatMap Component
 *
 * Displays multiple topics with their usage history over time.
 * Shows 3 events per year (Spring, Summer, Autumn) for BATbern conferences.
 * Wireframe: docs/wireframes/story-2.2-topic-backlog-management.md (lines 24-38)
 */

import React, { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Box,
  Paper,
  Typography,
  Tooltip,
  Chip,
  useTheme,
  alpha,
  Stack,
  IconButton,
} from '@mui/material';
import {
  Warning as WarningIcon,
  TrendingUp as TrendingIcon,
  ChevronLeft as ChevronLeftIcon,
  ChevronRight as ChevronRightIcon,
} from '@mui/icons-material';
import type { Topic, TopicUsageHistory } from '@/types/topic.types';

export interface TopicWithHistory extends Topic {
  usageHistory?: TopicUsageHistory[];
}

export interface MultiTopicHeatMapProps {
  topics: TopicWithHistory[];
  selectedTopicId?: string;
  onTopicSelect: (topic: Topic) => void;
  yearsToShow?: number; // Number of years to display (default: 6)
  eventLookup?: Map<number, { title: string }>; // Event number to event info lookup (GitHub Issue #379: no UUIDs in API)
}

interface EventPeriod {
  year: number;
  season: 'spring' | 'summer' | 'autumn';
  label: string; // e.g., "Spring 2024"
  key: string; // e.g., "2024-spring"
}

interface TopicUsageCell {
  period: EventPeriod;
  usageCount: number;
  intensity: number; // 0-100
  eventNumbers: number[]; // GitHub Issue #379: Event numbers instead of UUIDs
  totalAttendance: number;
}

interface TopicHeatMapRow {
  topic: Topic;
  cells: Map<string, TopicUsageCell>; // key: period.key
  totalUsage: number;
  lastUsedPeriod?: EventPeriod;
  isStale: boolean;
  isTrending: boolean;
}

export const MultiTopicHeatMap: React.FC<MultiTopicHeatMapProps> = ({
  topics,
  selectedTopicId,
  onTopicSelect,
  yearsToShow = 3,
  eventLookup,
}) => {
  const { t } = useTranslation('organizer');
  const theme = useTheme();

  // State for year navigation (offset from current year)
  const [yearOffset, setYearOffset] = useState(0);

  // Generate event periods (3 per year for the selected year range)
  const eventPeriods = useMemo(() => {
    const currentYear = new Date().getFullYear();
    const endYear = currentYear + yearOffset;
    const startYear = endYear - yearsToShow + 1;
    const periods: EventPeriod[] = [];

    for (let year = startYear; year <= endYear; year++) {
      periods.push(
        {
          year,
          season: 'spring',
          label: t('topicBacklog.heatMap.spring', 'Spring') + ` ${year}`,
          key: `${year}-spring`,
        },
        {
          year,
          season: 'summer',
          label: t('topicBacklog.heatMap.summer', 'Summer') + ` ${year}`,
          key: `${year}-summer`,
        },
        {
          year,
          season: 'autumn',
          label: t('topicBacklog.heatMap.autumn', 'Autumn') + ` ${year}`,
          key: `${year}-autumn`,
        }
      );
    }

    return periods;
  }, [yearsToShow, yearOffset, t]);

  // Calculate date range for display
  const dateRangeText = useMemo(() => {
    const years = Array.from(new Set(eventPeriods.map((p) => p.year))).sort();
    return `${years[0]} - ${years[years.length - 1]}`;
  }, [eventPeriods]);

  // Navigation handlers
  const handlePreviousYears = () => {
    setYearOffset((prev) => prev - yearsToShow);
  };

  const handleNextYears = () => {
    if (yearOffset < 0) {
      setYearOffset((prev) => Math.min(0, prev + yearsToShow));
    }
  };

  // Disable "Next" if we're at current year
  const isAtCurrentYear = yearOffset === 0;

  // Process topics into heat map rows
  const heatMapRows = useMemo(() => {
    // Debug: Check if topics have usage history
    const topicsWithHistory = topics.filter((t) => t.usageHistory && t.usageHistory.length > 0);
    console.debug(
      `📊 Processing ${topics.length} topics, ${topicsWithHistory.length} have usage history`
    );
    if (topicsWithHistory.length > 0) {
      const sampleHistory = topicsWithHistory[0].usageHistory?.[0];
      console.debug(`Sample usage history:`, sampleHistory);
    }

    const rows: TopicHeatMapRow[] = topics.map((topic) => {
      const cells = new Map<string, TopicUsageCell>();

      // Process usage history
      if (topic.usageHistory && topic.usageHistory.length > 0) {
        topic.usageHistory.forEach((usage: TopicUsageHistory) => {
          // GitHub Issue #379: Use eventDate (actual event date) instead of usedDate
          // Fallback to usedDate for backward compatibility
          const eventDateStr = usage.eventDate || usage.usedDate;
          const date = new Date(eventDateStr);
          const year = date.getFullYear();
          const month = date.getMonth();

          // Map month to BATbern season
          // JavaScript months: 0=Jan, 1=Feb, 2=Mar, ..., 11=Dec
          let season: 'spring' | 'summer' | 'autumn';
          if (month >= 0 && month <= 3) {
            // January-April
            season = 'spring';
          } else if (month >= 4 && month <= 7) {
            // May-August
            season = 'summer';
          } else {
            // September-December
            season = 'autumn';
          }

          const periodKey = `${year}-${season}`;
          const period = eventPeriods.find((p) => p.key === periodKey);

          if (period) {
            const existing = cells.get(periodKey);
            if (existing) {
              existing.usageCount += 1;
              existing.eventNumbers.push(usage.eventNumber);
              existing.totalAttendance += usage.attendance;
            } else {
              cells.set(periodKey, {
                period,
                usageCount: 1,
                intensity: 0, // Will calculate below
                eventNumbers: [usage.eventNumber],
                totalAttendance: usage.attendance,
              });
            }
          }
        });
      }

      // Calculate intensity for each cell (0-100)
      const maxUsageInTopic = Math.max(...Array.from(cells.values()).map((c) => c.usageCount), 1);
      cells.forEach((cell) => {
        cell.intensity = (cell.usageCount / maxUsageInTopic) * 100;
      });

      const totalUsage = topic.usageHistory?.length || 0;
      const lastUsedDate = topic.lastUsedDate ? new Date(topic.lastUsedDate) : null;
      let lastUsedPeriod: EventPeriod | undefined;

      if (lastUsedDate) {
        const year = lastUsedDate.getFullYear();
        const month = lastUsedDate.getMonth();
        let season: 'spring' | 'summer' | 'autumn';
        if (month >= 0 && month <= 3)
          season = 'spring'; // Jan-Apr
        else if (month >= 4 && month <= 7)
          season = 'summer'; // May-Aug
        else season = 'autumn'; // Sep-Dec

        lastUsedPeriod = eventPeriods.find((p) => p.year === year && p.season === season);
      }

      // Determine if stale (not used in last 4 events = ~16 months)
      const isStale =
        topic.stalenessScore !== undefined ? topic.stalenessScore < 30 : totalUsage === 0;

      // Determine if trending (used in last 2 events)
      const recentPeriods = eventPeriods.slice(-2);
      const isTrending = recentPeriods.some((period) => cells.has(period.key));

      return {
        topic,
        cells,
        totalUsage,
        lastUsedPeriod,
        isStale,
        isTrending,
      };
    });

    // Sort by total usage (most used first)
    return rows.sort((a, b) => b.totalUsage - a.totalUsage);
  }, [topics, eventPeriods]);

  // Get color based on intensity
  const getHeatColor = (intensity: number): string => {
    if (intensity === 0) return theme.palette.grey[200];
    if (intensity < 33) return alpha(theme.palette.info.main, 0.3);
    if (intensity < 66) return alpha(theme.palette.warning.main, 0.5);
    return alpha(theme.palette.error.main, 0.7);
  };

  if (topics.length === 0) {
    return (
      <Paper sx={{ p: 3, textAlign: 'center' }}>
        <Typography variant="body1" color="text.secondary">
          {t('topicBacklog.heatMap.noTopics', 'No topics available to display.')}
        </Typography>
      </Paper>
    );
  }

  return (
    <Paper sx={{ p: 2, height: '70vh', overflow: 'auto' }}>
      <Typography variant="h6" gutterBottom>
        {t('topicBacklog.heatMap.title', 'Topic Usage Heat Map')}
      </Typography>
      <Typography variant="caption" color="text.secondary" sx={{ mb: 1, display: 'block' }}>
        {t(
          'topicBacklog.heatMap.subtitle',
          'Darker colors indicate more usage. Click a topic to view details.'
        )}
      </Typography>

      {/* Year Range Navigation */}
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 1,
          mb: 2,
        }}
      >
        <Tooltip title={t('topicBacklog.heatMap.navigation.previous', 'Previous years')}>
          <IconButton onClick={handlePreviousYears} size="small">
            <ChevronLeftIcon />
          </IconButton>
        </Tooltip>
        <Typography variant="body2" fontWeight="medium" sx={{ minWidth: 100, textAlign: 'center' }}>
          {dateRangeText}
        </Typography>
        <Tooltip
          title={
            isAtCurrentYear
              ? t('topicBacklog.heatMap.navigation.atCurrent', 'At current year')
              : t('topicBacklog.heatMap.navigation.next', 'Next years')
          }
        >
          <span>
            <IconButton onClick={handleNextYears} size="small" disabled={isAtCurrentYear}>
              <ChevronRightIcon />
            </IconButton>
          </span>
        </Tooltip>
      </Box>

      <Box sx={{ overflowX: 'auto' }}>
        {/* Header Row - Years */}
        <Box sx={{ display: 'flex', mb: 1, minWidth: 'max-content' }}>
          <Box sx={{ width: 200, flexShrink: 0 }} /> {/* Topic name column */}
          {Array.from(new Set(eventPeriods.map((p) => p.year))).map((year) => (
            <Box
              key={year}
              sx={{
                width: 180, // 3 events * 60px each
                textAlign: 'center',
                borderBottom: 1,
                borderColor: 'divider',
                pb: 0.5,
              }}
            >
              <Typography variant="caption" fontWeight="bold">
                {year}
              </Typography>
            </Box>
          ))}
        </Box>

        {/* Subheader Row - Seasons */}
        <Box sx={{ display: 'flex', mb: 1, minWidth: 'max-content' }}>
          <Box sx={{ width: 200, flexShrink: 0 }} />
          {eventPeriods.map((period) => (
            <Box
              key={period.key}
              sx={{
                width: 60,
                textAlign: 'center',
                borderBottom: 1,
                borderColor: 'divider',
                pb: 0.5,
              }}
            >
              <Typography variant="caption" fontSize="0.7rem">
                {period.season.charAt(0).toUpperCase()}
              </Typography>
            </Box>
          ))}
        </Box>

        {/* Topic Rows */}
        {heatMapRows.map((row) => (
          <Box
            key={row.topic.topicCode}
            onClick={() => onTopicSelect(row.topic)}
            sx={{
              display: 'flex',
              mb: 0.5,
              cursor: 'pointer',
              minWidth: 'max-content',
              backgroundColor:
                selectedTopicId === row.topic.topicCode
                  ? alpha(theme.palette.primary.main, 0.1)
                  : 'transparent',
              '&:hover': {
                backgroundColor: alpha(theme.palette.primary.main, 0.05),
              },
              borderRadius: 1,
              p: 0.5,
            }}
          >
            {/* Topic Name */}
            <Box
              sx={{
                width: 200,
                flexShrink: 0,
                display: 'flex',
                alignItems: 'center',
                gap: 0.5,
                pr: 1,
              }}
            >
              <Typography
                variant="body2"
                noWrap
                fontWeight={selectedTopicId === row.topic.topicCode ? 'bold' : 'normal'}
              >
                {row.topic.title}
              </Typography>
              {row.isStale && (
                <Tooltip title={t('topicBacklog.heatMap.stale', 'Stale - not used recently')}>
                  <WarningIcon fontSize="small" color="warning" />
                </Tooltip>
              )}
              {row.isTrending && (
                <Tooltip title={t('topicBacklog.heatMap.trending', 'Trending - used recently')}>
                  <TrendingIcon fontSize="small" color="success" />
                </Tooltip>
              )}
            </Box>

            {/* Usage Cells */}
            {eventPeriods.map((period) => {
              const cell = row.cells.get(period.key);
              const hasUsage = cell && cell.usageCount > 0;

              // Get event titles for this cell using eventNumber as lookup key
              const eventTitles = hasUsage
                ? cell.eventNumbers.map((eventNumber) => {
                    const event = eventLookup?.get(eventNumber);
                    // Debug: log if event not found
                    if (!event && eventLookup && eventLookup.size > 0) {
                      console.debug(
                        `Event number ${eventNumber} not found in lookup map. Available keys:`,
                        Array.from(eventLookup.keys()).slice(0, 5)
                      );
                    }
                    return event ? `#${eventNumber}: ${event.title}` : `#${eventNumber}`;
                  })
                : [];

              // Display text: show event numbers
              const displayText = hasUsage ? cell.eventNumbers.join(', ') : '';

              // Tooltip content
              const tooltipContent = hasUsage
                ? eventTitles.length > 0
                  ? `${period.label}\n${eventTitles.join('\n')}\n${cell.totalAttendance} attendees`
                  : `${period.label}: ${cell.usageCount} event(s), ${cell.totalAttendance} attendees\nEvent numbers: ${cell.eventNumbers.join(', ')}`
                : `${period.label}: No usage`;

              return (
                <Tooltip key={period.key} title={tooltipContent} arrow>
                  <Box
                    sx={{
                      width: 60,
                      height: 24,
                      backgroundColor: hasUsage
                        ? getHeatColor(cell.intensity)
                        : theme.palette.grey[100],
                      border: 1,
                      borderColor: 'divider',
                      borderRadius: 0.5,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      transition: 'all 0.2s',
                      '&:hover': {
                        transform: 'scale(1.1)',
                        zIndex: 1,
                      },
                    }}
                  >
                    {hasUsage && (
                      <Typography variant="caption" fontSize="0.65rem" fontWeight="bold" noWrap>
                        {displayText}
                      </Typography>
                    )}
                  </Box>
                </Tooltip>
              );
            })}
          </Box>
        ))}
      </Box>

      {/* Legend */}
      <Stack direction="row" spacing={2} sx={{ mt: 2, flexWrap: 'wrap' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
          <Box
            sx={{
              width: 16,
              height: 16,
              backgroundColor: theme.palette.grey[200],
              border: 1,
              borderColor: 'divider',
            }}
          />
          <Typography variant="caption">
            {t('topicBacklog.heatMap.legend.noUsage', 'No usage')}
          </Typography>
        </Box>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
          <Box
            sx={{
              width: 16,
              height: 16,
              backgroundColor: alpha(theme.palette.info.main, 0.3),
              border: 1,
              borderColor: 'divider',
            }}
          />
          <Typography variant="caption">
            {t('topicBacklog.heatMap.legend.low', 'Low usage')}
          </Typography>
        </Box>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
          <Box
            sx={{
              width: 16,
              height: 16,
              backgroundColor: alpha(theme.palette.warning.main, 0.5),
              border: 1,
              borderColor: 'divider',
            }}
          />
          <Typography variant="caption">
            {t('topicBacklog.heatMap.legend.medium', 'Medium usage')}
          </Typography>
        </Box>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
          <Box
            sx={{
              width: 16,
              height: 16,
              backgroundColor: alpha(theme.palette.error.main, 0.7),
              border: 1,
              borderColor: 'divider',
            }}
          />
          <Typography variant="caption">
            {t('topicBacklog.heatMap.legend.high', 'High usage')}
          </Typography>
        </Box>
        <Chip
          icon={<WarningIcon />}
          label={t('topicBacklog.heatMap.legend.stale', 'Stale')}
          size="small"
          color="warning"
          variant="outlined"
        />
        <Chip
          icon={<TrendingIcon />}
          label={t('topicBacklog.heatMap.legend.trending', 'Trending')}
          size="small"
          color="success"
          variant="outlined"
        />
      </Stack>
    </Paper>
  );
};
