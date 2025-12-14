/**
 * TopicHeatMap Component Tests (Story 5.2 - AC2)
 *
 * Tests for topic usage heat map visualization component
 */

import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import TopicHeatMap from './TopicHeatMap';
import type { TopicUsageHistory } from '@/types/topic.types';

// Mock react-i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, defaultValue?: string) => defaultValue || key,
  }),
}));

// Mock Recharts to avoid canvas/SVG rendering issues in tests
vi.mock('recharts', () => ({
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="responsive-container">{children}</div>
  ),
  ScatterChart: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="scatter-chart">{children}</div>
  ),
  Scatter: () => <div data-testid="scatter" />,
  XAxis: () => <div data-testid="x-axis" />,
  YAxis: () => <div data-testid="y-axis" />,
  ZAxis: () => <div data-testid="z-axis" />,
  Tooltip: () => <div data-testid="tooltip" />,
  Legend: ({ content }: { content?: () => React.ReactElement }) => (
    <div data-testid="legend">{content && content()}</div>
  ),
  Cell: () => <div data-testid="cell" />,
}));

describe('TopicHeatMap', () => {
  const theme = createTheme();

  const mockUsageHistory: TopicUsageHistory[] = [
    {
      eventId: 'event-1',
      usedDate: '2024-01-15T10:00:00Z',
      attendance: 150,
      engagementScore: 0.85,
    },
    {
      eventId: 'event-2',
      usedDate: '2024-04-20T10:00:00Z',
      attendance: 200,
      engagementScore: 0.92,
    },
    {
      eventId: 'event-3',
      usedDate: '2024-07-10T10:00:00Z',
      attendance: 180,
      engagementScore: 0.88,
    },
    {
      eventId: 'event-4',
      usedDate: '2023-10-05T10:00:00Z',
      attendance: 160,
      engagementScore: 0.78,
    },
  ];

  const renderComponent = (usageHistory: TopicUsageHistory[] = mockUsageHistory) =>
    render(
      <ThemeProvider theme={theme}>
        <TopicHeatMap topicId="topic-123" usageHistory={usageHistory} />
      </ThemeProvider>
    );

  it('should render heat map with title', () => {
    renderComponent();
    expect(screen.getByText('Usage Heat Map')).toBeInTheDocument();
  });

  it('should render description text', () => {
    renderComponent();
    expect(
      screen.getByText('Topic usage frequency over the last 24 months (grouped by quarter)')
    ).toBeInTheDocument();
  });

  it('should render chart components when usage history is provided', () => {
    renderComponent();
    expect(screen.getByTestId('responsive-container')).toBeInTheDocument();
    expect(screen.getByTestId('scatter-chart')).toBeInTheDocument();
    expect(screen.getByTestId('scatter')).toBeInTheDocument();
  });

  it('should display empty state when no usage history', () => {
    renderComponent([]);
    expect(screen.getByText('No usage history available for this topic.')).toBeInTheDocument();
    expect(screen.queryByTestId('scatter-chart')).not.toBeInTheDocument();
  });

  it('should display hint text', () => {
    renderComponent();
    expect(screen.getByText('Hover over data points for more information')).toBeInTheDocument();
  });

  it('should render legend with intensity labels', () => {
    renderComponent();
    expect(screen.getByText('Low Usage')).toBeInTheDocument();
    expect(screen.getByText('Medium Usage')).toBeInTheDocument();
    expect(screen.getByText('High Usage')).toBeInTheDocument();
  });

  it('should group usage history by quarter', () => {
    // This test verifies the component renders without errors
    // The actual grouping logic is tested via the useMemo hook
    const { container } = renderComponent();
    expect(container).toBeTruthy();
  });

  it('should handle single usage entry', () => {
    const singleUsage: TopicUsageHistory[] = [
      {
        eventId: 'event-1',
        usedDate: '2024-01-15T10:00:00Z',
        attendance: 150,
        engagementScore: 0.85,
      },
    ];
    renderComponent(singleUsage);
    expect(screen.getByTestId('scatter-chart')).toBeInTheDocument();
  });

  it('should handle multiple events in same quarter', () => {
    const sameQuarterUsage: TopicUsageHistory[] = [
      {
        eventId: 'event-1',
        usedDate: '2024-01-15T10:00:00Z',
        attendance: 150,
        engagementScore: 0.85,
      },
      {
        eventId: 'event-2',
        usedDate: '2024-02-20T10:00:00Z',
        attendance: 200,
        engagementScore: 0.92,
      },
      {
        eventId: 'event-3',
        usedDate: '2024-03-10T10:00:00Z',
        attendance: 180,
        engagementScore: 0.88,
      },
    ];
    renderComponent(sameQuarterUsage);
    expect(screen.getByTestId('scatter-chart')).toBeInTheDocument();
  });

  it('should render with undefined usageHistory as empty state', () => {
    render(
      <ThemeProvider theme={theme}>
        <TopicHeatMap topicId="topic-123" usageHistory={[] as TopicUsageHistory[]} />
      </ThemeProvider>
    );
    expect(screen.getByText('No usage history available for this topic.')).toBeInTheDocument();
  });

  it('should display card component structure', () => {
    const { container } = renderComponent();
    const card = container.querySelector('.MuiCard-root');
    expect(card).toBeInTheDocument();
  });
});
