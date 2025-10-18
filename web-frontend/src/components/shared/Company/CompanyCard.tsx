/**
 * CompanyCard Component
 *
 * Individual company card display with:
 * - Company logo (CloudFront CDN via ?include=logo)
 * - Verified status (✅)
 * - Company details (name, industry)
 *
 * Story: 2.5.1 - Company Management Frontend
 */

import React from 'react';
import { Card, CardContent, CardActionArea, Typography, Box, Chip, Avatar } from '@mui/material';
import BusinessIcon from '@mui/icons-material/Business';
import { useTranslation } from 'react-i18next';
import type { CompanyListItem } from '@/types/company.types';

export interface CompanyCardProps {
  company: CompanyListItem;
  onClick: (companyId: string) => void;
  viewMode?: 'grid' | 'list';
}

export const CompanyCard: React.FC<CompanyCardProps> = ({
  company,
  onClick,
  viewMode = 'grid',
}) => {
  const { t } = useTranslation('common');

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
        flexDirection: 'column',
      }}
    >
      <CardActionArea
        onClick={handleClick}
        onKeyDown={handleKeyDown}
        role="button"
        aria-label={t('company.viewDetails', { name: company.displayName || company.name })}
        tabIndex={0}
        sx={{
          height: '100%',
          display: 'flex',
          flexDirection: viewMode === 'grid' ? 'column' : 'row',
          alignItems: 'flex-start',
        }}
      >
        {/* Logo Section */}
        <Box
          sx={{
            p: 2,
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center',
            minHeight: viewMode === 'grid' ? 120 : 80,
          }}
        >
          <Avatar
            src={company.logo?.url}
            alt={`${company.name} logo`}
            data-testid="company-logo-fallback"
            sx={{
              width: viewMode === 'grid' ? 80 : 60,
              height: viewMode === 'grid' ? 80 : 60,
              bgcolor: 'grey.200',
              borderRadius: 1,
            }}
            variant="square"
            slotProps={{
              img: {
                crossOrigin: 'anonymous',
              },
            }}
          >
            <BusinessIcon sx={{ fontSize: 40, color: 'grey.500' }} />
          </Avatar>
        </Box>

        {/* Content Section */}
        <CardContent sx={{ flexGrow: 1, width: '100%' }}>
          {/* Badges Row */}
          {company.isVerified && (
            <Box sx={{ mb: 1 }}>
              <Chip
                label={`✅ ${t('company.badges.verified')}`}
                size="small"
                aria-label={t('company.badges.verified')}
                sx={{ height: 20, fontSize: '0.75rem' }}
              />
            </Box>
          )}

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
              whiteSpace: 'nowrap',
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
                mb: 1,
              }}
            >
              {t('company.fields.legalName', { name: company.name })}
            </Typography>
          )}

          {/* Industry */}
          {company.industry && (
            <Typography variant="body2" color="text.secondary" sx={{ mb: 0.5 }}>
              {company.industry}
            </Typography>
          )}
        </CardContent>
      </CardActionArea>
    </Card>
  );
};
