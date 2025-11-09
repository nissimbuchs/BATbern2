import React from 'react';
import { Box, Typography, List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import type { PartnershipLevel } from '@/types/partner';

interface TierBenefitsPreviewProps {
  tier: PartnershipLevel;
}

const tierBenefits: Record<PartnershipLevel, string[]> = {
  STRATEGIC: [
    'Logo placement on website',
    'Newsletter mentions',
    'Priority event access',
    'Quarterly strategic meetings',
    'ROI analytics dashboard',
    'Dedicated account manager',
  ],
  PLATINUM: [
    'Logo placement on website',
    'Newsletter mentions',
    'Priority event access',
    'Quarterly strategic meetings',
  ],
  GOLD: ['Logo placement on website', 'Newsletter mentions', 'Priority event access'],
  SILVER: ['Newsletter mentions', 'Event access'],
  BRONZE: ['Event access'],
};

export const TierBenefitsPreview: React.FC<TierBenefitsPreviewProps> = ({ tier }) => {
  const benefits = tierBenefits[tier] || [];

  return (
    <Box sx={{ mt: 2 }}>
      <Typography variant="h6" gutterBottom>
        Partnership Benefits
      </Typography>
      <List dense>
        {benefits.map((benefit, index) => (
          <ListItem key={index} sx={{ py: 0.5 }}>
            <ListItemIcon sx={{ minWidth: 36 }}>
              <CheckCircleIcon color="success" fontSize="small" />
            </ListItemIcon>
            <ListItemText primary={`✓ ${benefit}`} primaryTypographyProps={{ variant: 'body2' }} />
          </ListItem>
        ))}
      </List>
    </Box>
  );
};
