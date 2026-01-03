/**
 * CompanyPagination Component
 *
 * Reusable pagination controls for company list
 * - Page navigation with first/last buttons
 * - Items per page selector
 * - Responsive layout
 */

import React from 'react';
import {
  Box,
  Pagination,
  FormControl,
  Select,
  MenuItem,
  Typography,
  SelectChangeEvent,
} from '@mui/material';
import { useTranslation } from 'react-i18next';

interface CompanyPaginationProps {
  page: number;
  totalPages: number;
  limit: number;
  onPageChange: (page: number) => void;
  onLimitChange: (limit: number) => void;
}

const CompanyPagination: React.FC<CompanyPaginationProps> = ({
  page,
  totalPages,
  limit,
  onPageChange,
  onLimitChange,
}) => {
  const { t } = useTranslation('common');

  const handleLimitChange = (event: SelectChangeEvent<number>) => {
    const newLimit = Number(event.target.value);
    onLimitChange(newLimit);
  };

  return (
    <Box
      sx={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        mt: 3,
        flexWrap: 'wrap',
        gap: 2,
      }}
    >
      {/* Pagination Controls */}
      <Pagination
        count={totalPages}
        page={page}
        onChange={(_, newPage) => onPageChange(newPage)}
        color="primary"
        showFirstButton
        showLastButton
        siblingCount={1}
        boundaryCount={1}
      />

      {/* Items Per Page Selector */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
        <Typography variant="body2" color="text.secondary" id="items-per-page-label">
          {t('company.pagination.itemsPerPage')}:
        </Typography>
        <FormControl size="small">
          <Select
            value={limit}
            onChange={handleLimitChange}
            aria-labelledby="items-per-page-label"
            inputProps={{
              'aria-label': t('company.pagination.itemsPerPage'),
            }}
          >
            <MenuItem value={10}>10</MenuItem>
            <MenuItem value={20}>20</MenuItem>
            <MenuItem value={50}>50</MenuItem>
            <MenuItem value={100}>100</MenuItem>
          </Select>
        </FormControl>
      </Box>
    </Box>
  );
};

export default CompanyPagination;
