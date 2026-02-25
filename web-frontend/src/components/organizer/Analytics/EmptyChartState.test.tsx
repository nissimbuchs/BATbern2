/**
 * EmptyChartState Tests
 * Story 10.5: Analytics Dashboard (AC8)
 */

import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import EmptyChartState from './EmptyChartState';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const map: Record<string, string> = {
        'analytics.labels.noData': 'No data available for this period',
      };
      return map[key] ?? key;
    },
  }),
}));

describe('EmptyChartState', () => {
  it('renders the data-testid attribute', () => {
    render(<EmptyChartState />);
    expect(screen.getByTestId('empty-chart-state')).toBeInTheDocument();
  });

  it('shows default no-data message from i18n key', () => {
    render(<EmptyChartState />);
    expect(
      screen.getByText('No data available for this period')
    ).toBeInTheDocument();
  });

  it('shows a custom message when provided', () => {
    render(<EmptyChartState message="No events found" />);
    expect(screen.getByText('No events found')).toBeInTheDocument();
    expect(
      screen.queryByText('No data available for this period')
    ).not.toBeInTheDocument();
  });
});
