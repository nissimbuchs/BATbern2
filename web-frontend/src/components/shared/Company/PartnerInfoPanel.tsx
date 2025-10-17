import React from 'react';
import { Card, CardContent, Typography, Box } from '@mui/material';

interface PartnerInfoPanelProps {
  companyId: string;
}

/**
 * PartnerInfoPanel component
 *
 * NOTE: This is a placeholder component for displaying partnership information.
 * Full implementation will be done in a separate Partner Service story (Epic 8).
 * This component currently displays a placeholder message.
 */
export const PartnerInfoPanel: React.FC<PartnerInfoPanelProps> = ({ companyId }) => {
  return (
    <Card>
      <CardContent>
        <Typography variant="h6" gutterBottom>
          Partnership Information
        </Typography>
        <Box sx={{ py: 2 }}>
          <Typography variant="body2" color="text.secondary">
            Partnership details for company {companyId} will be displayed here once the Partner
            Service integration is complete (Epic 8).
          </Typography>
        </Box>
      </CardContent>
    </Card>
  );
};
