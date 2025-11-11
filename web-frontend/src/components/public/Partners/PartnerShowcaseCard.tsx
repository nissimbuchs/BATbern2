/**
 * PartnerShowcaseCard Component
 * Displays partner logo prominently for homepage showcase
 * Shows tier and start date, links to company website
 */

import { Card, CardActionArea, Box, Typography, Chip, Stack } from '@mui/material';
import { format } from 'date-fns';

interface PartnerShowcaseCardProps {
  companyName: string;
  logoUrl?: string;
  partnershipLevel: 'STRATEGIC' | 'PLATINUM' | 'GOLD' | 'SILVER' | 'BRONZE';
  partnershipStartDate: string;
  website?: string;
}

// Tier emoji and color mapping
const TIER_CONFIG = {
  STRATEGIC: { emoji: '🏆', color: '#FFD700', label: 'Strategic' },
  PLATINUM: { emoji: '💎', color: '#E5E4E2', label: 'Platinum' },
  GOLD: { emoji: '🥇', color: '#FFD700', label: 'Gold' },
  SILVER: { emoji: '🥈', color: '#C0C0C0', label: 'Silver' },
  BRONZE: { emoji: '🥉', color: '#CD7F32', label: 'Bronze' },
};

export const PartnerShowcaseCard = ({
  companyName,
  logoUrl,
  partnershipLevel,
  partnershipStartDate,
  website,
}: PartnerShowcaseCardProps) => {
  const tierConfig = TIER_CONFIG[partnershipLevel];
  const initials = companyName.substring(0, 2).toUpperCase();
  const formattedDate = format(new Date(partnershipStartDate), 'MMM yyyy');

  const handleClick = () => {
    if (website) {
      window.open(website, '_blank', 'noopener,noreferrer');
    }
  };

  return (
    <Card
      sx={{
        width: 360,
        height: 200,
        flexShrink: 0,
        bgcolor: 'transparent',
        boxShadow: 'none',
        transition: 'transform 0.2s',
        '&:hover': {
          transform: 'translateY(-4px)',
        },
      }}
    >
      <CardActionArea
        onClick={handleClick}
        disabled={!website}
        sx={{
          height: '100%',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          alignItems: 'center',
          p: 1,
        }}
      >
        {/* Logo */}
        <Box
          sx={{
            mb: 0.5,
            width: '100%',
            height: 160,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            overflow: 'hidden',
          }}
        >
          {logoUrl ? (
            <Box
              component="img"
              src={logoUrl}
              alt={companyName}
              sx={{
                maxWidth: '100%',
                maxHeight: '100%',
                objectFit: 'contain',
              }}
            />
          ) : (
            <Box
              sx={{
                width: 200,
                height: 100,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                borderRadius: 1,
                bgcolor: 'primary.main',
                color: 'white',
                fontSize: '2.5rem',
                fontWeight: 600,
              }}
            >
              {initials}
            </Box>
          )}
        </Box>

        {/* Tier and Date - Horizontal Layout */}
        <Stack direction="row" spacing={1} alignItems="center">
          <Chip
            label={`${tierConfig.emoji} ${tierConfig.label}`}
            size="small"
            sx={{
              fontWeight: 500,
              bgcolor: `${tierConfig.color}15`,
              border: `1px solid ${tierConfig.color}40`,
            }}
          />
          <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 500 }}>
            since {formattedDate}
          </Typography>
        </Stack>
      </CardActionArea>
    </Card>
  );
};
