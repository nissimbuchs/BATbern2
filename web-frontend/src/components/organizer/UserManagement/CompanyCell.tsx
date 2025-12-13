/**
 * CompanyCell Component
 *
 * Displays company logo and name in a table cell with lazy loading.
 * Uses React Query for caching company data.
 */

import React from 'react';
import { Box, Avatar, Typography, Skeleton } from '@mui/material';
import { Business as BusinessIcon } from '@mui/icons-material';
import { useCompany } from '@/hooks/useCompany/useCompany';

interface CompanyCellProps {
  companyId: string | null | undefined;
}

const CompanyCell: React.FC<CompanyCellProps> = ({ companyId }) => {
  // Only fetch if companyId is provided
  const {
    data: company,
    isLoading,
    isError,
  } = useCompany(companyId || '', {
    expand: ['logo'],
  });

  // No company associated
  if (!companyId) {
    return (
      <Typography variant="body2" color="text.secondary">
        -
      </Typography>
    );
  }

  // Loading state - show skeleton
  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
        <Skeleton variant="circular" width={24} height={24} />
        <Skeleton variant="text" width={100} />
      </Box>
    );
  }

  // Error state or company not found - show companyId as fallback
  if (isError || !company) {
    return (
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
        <Avatar sx={{ width: 24, height: 24, bgcolor: 'grey.300' }}>
          <BusinessIcon sx={{ fontSize: 16 }} />
        </Avatar>
        <Typography variant="body2" color="text.secondary">
          {companyId}
        </Typography>
      </Box>
    );
  }

  // Success - show logo and name
  const logoUrl = company.logo?.url;
  const displayName = company.displayName || company.name;

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
      <Avatar
        src={logoUrl}
        sx={{ width: 24, height: 24, bgcolor: 'grey.100' }}
        alt={`${displayName} logo`}
      >
        <BusinessIcon sx={{ fontSize: 16, color: 'grey.500' }} />
      </Avatar>
      <Typography variant="body2" color="text.secondary" noWrap>
        {displayName}
      </Typography>
    </Box>
  );
};

export default CompanyCell;
