/**
 * CompanyCell Component
 *
 * Displays company logo (circular avatar) and name in a table cell.
 * Delegates to CompanyLogo shared component for fetching and rendering.
 */

import React from 'react';
import { Typography } from '@mui/material';
import CompanyLogo from '@/components/shared/Company/CompanyLogo';

interface CompanyCellProps {
  companyId: string | null | undefined;
}

const CompanyCell: React.FC<CompanyCellProps> = ({ companyId }) => {
  if (!companyId) {
    return (
      <Typography variant="body2" color="text.secondary">
        -
      </Typography>
    );
  }

  return <CompanyLogo companyName={companyId} variant="full" maxWidth={80} maxHeight={40} />;
};

export default CompanyCell;
