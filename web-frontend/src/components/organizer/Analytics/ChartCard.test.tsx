/**
 * ChartCard Tests
 * Story 10.5: Analytics Dashboard (AC8, AC9)
 */

import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import ChartCard from './ChartCard';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const map: Record<string, string> = {
        'analytics.labels.showDataTable': 'Show data table',
        'analytics.labels.hideDataTable': 'Hide data table',
      };
      return map[key] ?? key;
    },
  }),
}));

vi.mock('@/components/shared/BATbernLoader', () => ({
  BATbernLoader: ({ size }: { size?: number }) => (
    <div data-testid="batbern-loader" data-size={size} />
  ),
}));

vi.mock('./EmptyChartState', () => ({
  default: ({ height }: { height?: number }) => (
    <div data-testid="empty-chart-state" data-height={height} />
  ),
}));

describe('ChartCard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders the title', () => {
    render(
      <ChartCard title="Test Chart">
        <div>chart content</div>
      </ChartCard>
    );
    expect(screen.getByText('Test Chart')).toBeInTheDocument();
  });

  it('renders children when not loading and not empty', () => {
    render(
      <ChartCard title="Test Chart">
        <div data-testid="chart-body">chart content</div>
      </ChartCard>
    );
    expect(screen.getByTestId('chart-body')).toBeInTheDocument();
  });

  it('shows BATbernLoader when isLoading is true', () => {
    render(
      <ChartCard title="Test Chart" isLoading>
        <div>chart content</div>
      </ChartCard>
    );
    expect(screen.getByTestId('batbern-loader')).toBeInTheDocument();
    expect(screen.queryByText('chart content')).not.toBeInTheDocument();
  });

  it('shows EmptyChartState when isEmpty is true', () => {
    render(
      <ChartCard title="Test Chart" isEmpty>
        <div>chart content</div>
      </ChartCard>
    );
    expect(screen.getByTestId('empty-chart-state')).toBeInTheDocument();
    expect(screen.queryByText('chart content')).not.toBeInTheDocument();
  });

  it('does not show data table toggle when dataTable prop is absent', () => {
    render(
      <ChartCard title="Test Chart">
        <div>chart content</div>
      </ChartCard>
    );
    expect(screen.queryByText('Show data table')).not.toBeInTheDocument();
  });

  it('shows "Show data table" toggle when dataTable is provided and chart has data', () => {
    render(
      <ChartCard title="Test Chart" dataTable={<div>table</div>}>
        <div>chart content</div>
      </ChartCard>
    );
    expect(screen.getByRole('button', { name: /Show data table/i })).toBeInTheDocument();
  });

  it('expands and collapses the data table on toggle click', async () => {
    render(
      <ChartCard
        title="Test Chart"
        dataTable={<div data-testid="data-table-content">table data</div>}
      >
        <div>chart content</div>
      </ChartCard>
    );

    // Initially collapsed — unmountOnExit means element absent from DOM
    expect(screen.queryByTestId('data-table-content')).not.toBeInTheDocument();

    // Click to expand
    fireEvent.click(screen.getByRole('button', { name: /Show data table/i }));
    expect(screen.getByTestId('data-table-content')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Hide data table/i })).toBeInTheDocument();

    // Click to collapse — MUI Collapse transition needs waitFor
    fireEvent.click(screen.getByRole('button', { name: /Hide data table/i }));
    await waitFor(() => {
      expect(screen.queryByTestId('data-table-content')).not.toBeInTheDocument();
    });
  });

  it('renders optional controls slot', () => {
    render(
      <ChartCard title="Test Chart" controls={<button>Control</button>}>
        <div>chart content</div>
      </ChartCard>
    );
    expect(screen.getByRole('button', { name: 'Control' })).toBeInTheDocument();
  });

  it('hides data table toggle when loading', () => {
    render(
      <ChartCard title="Test Chart" isLoading dataTable={<div>table</div>}>
        <div>chart content</div>
      </ChartCard>
    );
    expect(screen.queryByText('Show data table')).not.toBeInTheDocument();
  });

  it('hides data table toggle when empty', () => {
    render(
      <ChartCard title="Test Chart" isEmpty dataTable={<div>table</div>}>
        <div>chart content</div>
      </ChartCard>
    );
    expect(screen.queryByText('Show data table')).not.toBeInTheDocument();
  });
});
