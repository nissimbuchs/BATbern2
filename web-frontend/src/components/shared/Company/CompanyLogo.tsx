/**
 * CompanyLogo
 *
 * Shared component for displaying a company logo in two variants:
 *
 * - "avatar": small circular Avatar + optional name text (used in table cells, lists)
 * - "full":   full-size img with objectFit:contain + name as tooltip (used in cards, topic table)
 *
 * Both variants use React Query (10-min stale time via useCompany) so logos are cached
 * across all usages and fetched only once per company per session.
 */

import React from 'react';
import { Avatar, Box, Skeleton, Tooltip, Typography } from '@mui/material';
import { Business as BusinessIcon } from '@mui/icons-material';
import { useCompany } from '@/hooks/useCompany/useCompany';

interface CompanyLogoProps {
  companyName: string;
  /** 'avatar' = circular Avatar + name text; 'full' = full img with tooltip. Default: 'avatar' */
  variant?: 'avatar' | 'full';
  /** Avatar diameter in px (avatar variant only). Default: 24 */
  avatarSize?: number;
  /** Max width in px (full variant only). Default: 80 */
  maxWidth?: number;
  /** Max height in px (full variant only). Default: 48 */
  maxHeight?: number;
  /** Show display name alongside the avatar (avatar variant only). Default: true */
  showName?: boolean;
}

const CompanyLogo: React.FC<CompanyLogoProps> = ({
  companyName,
  variant = 'avatar',
  avatarSize = 24,
  maxWidth = 80,
  maxHeight = 48,
  showName = true,
}) => {
  const { data: company, isLoading, isError } = useCompany(companyName, { expand: ['logo'] });

  // ─── Loading ───────────────────────────────────────────────────────────────

  if (isLoading) {
    return variant === 'full' ? (
      <Skeleton
        variant="rectangular"
        width={maxWidth}
        height={maxHeight / 2}
        sx={{ borderRadius: 1 }}
      />
    ) : (
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
        <Skeleton variant="circular" width={avatarSize} height={avatarSize} />
        {showName && <Skeleton variant="text" width={80} />}
      </Box>
    );
  }

  const logoUrl = company?.logo?.url;
  const displayName = company?.displayName || company?.name || companyName;

  // ─── Full variant ──────────────────────────────────────────────────────────

  if (variant === 'full') {
    if (!logoUrl) {
      return (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <BusinessIcon sx={{ fontSize: 20, color: 'grey.400' }} />
          <Typography variant="body2" color="text.secondary">
            {displayName}
          </Typography>
        </Box>
      );
    }
    return (
      <Tooltip title={displayName}>
        <Box
          component="img"
          src={logoUrl}
          alt={displayName}
          loading="lazy"
          sx={{ maxWidth, maxHeight, objectFit: 'contain', display: 'block' }}
        />
      </Tooltip>
    );
  }

  // ─── Avatar variant ────────────────────────────────────────────────────────

  if (isError || !company) {
    return (
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
        <Avatar sx={{ width: avatarSize, height: avatarSize, bgcolor: 'grey.300' }}>
          <BusinessIcon sx={{ fontSize: avatarSize * 0.65 }} />
        </Avatar>
        {showName && (
          <Typography variant="body2" color="text.secondary">
            {companyName}
          </Typography>
        )}
      </Box>
    );
  }

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
      <Avatar
        src={logoUrl}
        alt={displayName}
        sx={{ width: avatarSize, height: avatarSize, bgcolor: 'grey.100' }}
        slotProps={{ img: { loading: 'lazy' } }}
      >
        <BusinessIcon sx={{ fontSize: avatarSize * 0.65, color: 'grey.500' }} />
      </Avatar>
      {showName && (
        <Typography variant="body2" color="text.secondary" noWrap>
          {displayName}
        </Typography>
      )}
    </Box>
  );
};

export default CompanyLogo;
