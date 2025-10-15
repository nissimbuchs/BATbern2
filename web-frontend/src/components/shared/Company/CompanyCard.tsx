/**
 * CompanyCard Component
 *
 * Individual company card display with:
 * - Company logo (CloudFront CDN)
 * - Partner badge (⭐) and verified status (✅)
 * - Company details (name, industry, location)
 * - Associated users count
 *
 * Story: 2.5.1 - Company Management Frontend
 */

import React from 'react';
import {
  Card,
  CardContent,
  CardActionArea,
  Typography,
  Box,
  Chip,
  Avatar,
  Stack
} from '@mui/material';
import BusinessIcon from '@mui/icons-material/Business';
import PeopleIcon from '@mui/icons-material/People';
import type { CompanyListItem } from '@/types/company.types';

export interface CompanyCardProps {
  company: CompanyListItem;
  onClick: (companyId: string) => void;
  viewMode?: 'grid' | 'list';
}

export const CompanyCard: React.FC<CompanyCardProps> = ({
  company,
  onClick,
  viewMode = 'grid'
}) => {
  const handleClick = () => {
    onClick(company.id);
  };

  const handleKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      onClick(company.id);
    }
  };

  return (
    <Card
      data-testid={`company-card-${company.id}`}
      sx={{
        height: '100%',
        display: 'flex',
        flexDirection: 'column'
      }}
    >
      <CardActionArea
        onClick={handleClick}
        onKeyDown={handleKeyDown}
        role="button"
        aria-label={`View details for ${company.displayName || company.name}`}
        tabIndex={0}
        sx={{
          height: '100%',
          display: 'flex',
          flexDirection: viewMode === 'grid' ? 'column' : 'row',
          alignItems: 'flex-start'
        }}
      >
        {/* Logo Section */}
        <Box
          sx={{
            p: 2,
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center',
            minHeight: viewMode === 'grid' ? 120 : 80
          }}
        >
          {company.logoUrl ? (
            <Avatar
              src={company.logoUrl}
              alt={`${company.displayName || company.name} logo`}
              sx={{
                width: viewMode === 'grid' ? 80 : 60,
                height: viewMode === 'grid' ? 80 : 60,
                borderRadius: 1
              }}
              variant="square"
            />
          ) : (
            <Avatar
              data-testid="company-logo-fallback"
              sx={{
                width: viewMode === 'grid' ? 80 : 60,
                height: viewMode === 'grid' ? 80 : 60,
                bgcolor: 'grey.200',
                borderRadius: 1
              }}
              variant="square"
            >
              <BusinessIcon sx={{ fontSize: 40, color: 'grey.500' }} />
            </Avatar>
          )}
        </Box>

        {/* Content Section */}
        <CardContent sx={{ flexGrow: 1, width: '100%' }}>
          {/* Badges Row */}
          <Stack direction="row" spacing={0.5} sx={{ mb: 1 }}>
            {company.isPartner && (
              <Chip
                label="⭐ Partner"
                size="small"
                aria-label="Partner company"
                sx={{ height: 20, fontSize: '0.75rem' }}
              />
            )}
            {company.isVerified && (
              <Chip
                label="✅ Verified"
                size="small"
                aria-label="Verified company"
                sx={{ height: 20, fontSize: '0.75rem' }}
              />
            )}
          </Stack>

          {/* Company Name (display name preferred) */}
          <Typography
            variant="h6"
            component="h3"
            gutterBottom
            sx={{
              fontSize: '1rem',
              fontWeight: 600,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap'
            }}
          >
            {company.displayName || company.name}
          </Typography>

          {/* Legal Name (if different from display name) */}
          {company.displayName && company.displayName !== company.name && (
            <Typography
              variant="body2"
              color="text.secondary"
              sx={{
                fontSize: '0.875rem',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
                mb: 1
              }}
            >
              Legal: {company.name}
            </Typography>
          )}

          {/* Industry */}
          <Typography
            variant="body2"
            color="text.secondary"
            sx={{ mb: 0.5 }}
          >
            {company.industry}
          </Typography>

          {/* Location */}
          <Typography
            variant="body2"
            color="text.secondary"
            sx={{ mb: 1 }}
          >
            {company.location.city}, {company.location.country}
          </Typography>

          {/* Associated Users */}
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
            <PeopleIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
            <Typography variant="body2" color="text.secondary">
              {company.associatedUserCount} {company.associatedUserCount === 1 ? 'user' : 'users'}
            </Typography>
          </Box>
        </CardContent>
      </CardActionArea>
    </Card>
  );
};
