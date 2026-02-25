/**
 * DataTable Tests
 * Story 10.5: Analytics Dashboard (AC9)
 */

import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import DataTable, { ColumnDef } from './DataTable';

interface Row {
  id: string;
  name: string;
  count: number;
}

const columns: ColumnDef<Record<string, unknown>>[] = [
  { key: 'id', label: 'ID' },
  { key: 'name', label: 'Name' },
  { key: 'count', label: 'Count', align: 'right' },
];

const rows: Record<string, unknown>[] = [
  { id: '1', name: 'Alpha', count: 30 },
  { id: '2', name: 'Beta', count: 10 },
  { id: '3', name: 'Gamma', count: 20 },
];

describe('DataTable', () => {
  it('renders all column headers', () => {
    render(<DataTable columns={columns} rows={rows} rowKey="id" />);
    expect(screen.getByText('ID')).toBeInTheDocument();
    expect(screen.getByText('Name')).toBeInTheDocument();
    expect(screen.getByText('Count')).toBeInTheDocument();
  });

  it('renders all row data', () => {
    render(<DataTable columns={columns} rows={rows} rowKey="id" />);
    expect(screen.getByText('Alpha')).toBeInTheDocument();
    expect(screen.getByText('Beta')).toBeInTheDocument();
    expect(screen.getByText('Gamma')).toBeInTheDocument();
    expect(screen.getByText('30')).toBeInTheDocument();
    expect(screen.getByText('10')).toBeInTheDocument();
    expect(screen.getByText('20')).toBeInTheDocument();
  });

  it('sorts ascending by clicking a column header', () => {
    render(<DataTable columns={columns} rows={rows} rowKey="id" />);

    // Click "Count" header to sort ascending
    fireEvent.click(screen.getByText('Count'));

    const cells = screen.getAllByRole('cell');
    // First data row should be the one with count=10 (Beta)
    expect(cells.some((c) => c.textContent === 'Beta')).toBe(true);
    const nameIndex = cells.findIndex((c) => c.textContent === 'Beta');
    const alphaIndex = cells.findIndex((c) => c.textContent === 'Alpha');
    const gammaIndex = cells.findIndex((c) => c.textContent === 'Gamma');
    // Beta (10) < Gamma (20) < Alpha (30) when sorted ascending
    expect(nameIndex).toBeLessThan(gammaIndex);
    expect(gammaIndex).toBeLessThan(alphaIndex);
  });

  it('reverses sort to descending on second click of same column', () => {
    render(<DataTable columns={columns} rows={rows} rowKey="id" />);

    // Click "Count" twice → descending
    fireEvent.click(screen.getByText('Count'));
    fireEvent.click(screen.getByText('Count'));

    const cells = screen.getAllByRole('cell');
    const alphaIndex = cells.findIndex((c) => c.textContent === 'Alpha');
    const gammaIndex = cells.findIndex((c) => c.textContent === 'Gamma');
    const betaIndex = cells.findIndex((c) => c.textContent === 'Beta');
    // Alpha (30) > Gamma (20) > Beta (10) when sorted descending
    expect(alphaIndex).toBeLessThan(gammaIndex);
    expect(gammaIndex).toBeLessThan(betaIndex);
  });

  it('renders empty table when no rows provided', () => {
    render(<DataTable columns={columns} rows={[]} rowKey="id" />);
    expect(screen.getByText('ID')).toBeInTheDocument();
    expect(screen.queryByText('Alpha')).not.toBeInTheDocument();
  });

  it('applies custom format function for cell rendering', () => {
    const customColumns: ColumnDef<Record<string, unknown>>[] = [
      { key: 'id', label: 'ID' },
      {
        key: 'count',
        label: 'Count',
        format: (val) => <strong data-testid="formatted">{String(val)}</strong>,
      },
    ];

    render(<DataTable columns={customColumns} rows={[rows[0]]} rowKey="id" />);
    expect(screen.getByTestId('formatted')).toBeInTheDocument();
    expect(screen.getByTestId('formatted').textContent).toBe('30');
  });
});
