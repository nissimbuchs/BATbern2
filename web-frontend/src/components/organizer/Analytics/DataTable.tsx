/**
 * DataTable
 * Story 10.5: Analytics Dashboard (AC9)
 *
 * Sortable MUI table for chart data tables.
 * Used inside ChartCard's collapsible section.
 */

import {
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TableSortLabel,
} from '@mui/material';
import { useState } from 'react';

export interface ColumnDef<T> {
  key: keyof T & string;
  label: string;
  align?: 'left' | 'right' | 'center';
  format?: (value: T[keyof T], row: T) => React.ReactNode;
}

interface DataTableProps<T extends Record<string, unknown>> {
  columns: ColumnDef<T>[];
  rows: T[];
  rowKey: keyof T & string;
}

type SortDir = 'asc' | 'desc';

const DataTable = <T extends Record<string, unknown>>({
  columns,
  rows,
  rowKey,
}: DataTableProps<T>) => {
  const [orderBy, setOrderBy] = useState<keyof T>(rowKey);
  const [direction, setDirection] = useState<SortDir>('asc');

  const handleSort = (col: keyof T) => {
    if (col === orderBy) {
      setDirection((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setOrderBy(col);
      setDirection('asc');
    }
  };

  const sorted = [...rows].sort((a, b) => {
    const av = a[orderBy];
    const bv = b[orderBy];
    if (av == null) return 1;
    if (bv == null) return -1;
    const cmp = av < bv ? -1 : av > bv ? 1 : 0;
    return direction === 'asc' ? cmp : -cmp;
  });

  return (
    <TableContainer
      component={Paper}
      variant="outlined"
      sx={{ maxHeight: 300 }}
    >
      <Table size="small" stickyHeader>
        <TableHead>
          <TableRow>
            {columns.map((col) => (
              <TableCell
                key={col.key}
                align={col.align ?? 'left'}
                sortDirection={orderBy === col.key ? direction : false}
              >
                <TableSortLabel
                  active={orderBy === col.key}
                  direction={orderBy === col.key ? direction : 'asc'}
                  onClick={() => handleSort(col.key as keyof T)}
                >
                  {col.label}
                </TableSortLabel>
              </TableCell>
            ))}
          </TableRow>
        </TableHead>
        <TableBody>
          {sorted.map((row) => (
            <TableRow key={String(row[rowKey])}>
              {columns.map((col) => (
                <TableCell key={col.key} align={col.align ?? 'left'}>
                  {col.format
                    ? col.format(row[col.key as keyof T], row)
                    : String(row[col.key as keyof T] ?? '')}
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
};

export default DataTable;
