/**
 * UserCard Component
 *
 * Individual user card display with:
 * - Profile picture with fallback to initials
 * - User name and email
 * - Role badges
 * - Company affiliation
 * - Active/Inactive status
 *
 * Story: 2.5.2 - User Management Frontend
 */

import React from 'react';
import { Card, CardContent, CardActionArea, Typography, Box, Chip, Avatar } from '@mui/material';
import { Business as BusinessIcon, Email as EmailIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import type { User, Role } from '@/types/user.types';
import { ROLE_ICONS } from '@/types/user.types';
import CompanyCell from './CompanyCell';

export interface UserCardProps {
  user: User;
  onClick: (user: User) => void;
}

export const UserCard: React.FC<UserCardProps> = ({ user, onClick }) => {
  const { t } = useTranslation('userManagement');

  const handleClick = () => {
    onClick(user);
  };

  const handleKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      onClick(user);
    }
  };

  const getRoleBadgeColor = (role: Role): 'primary' | 'secondary' | 'success' | 'default' => {
    switch (role) {
      case 'ORGANIZER':
        return 'primary';
      case 'SPEAKER':
        return 'secondary';
      case 'PARTNER':
        return 'success';
      default:
        return 'default';
    }
  };

  return (
    <Card
      data-testid={`user-card-${user.id}`}
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
        aria-label={t('table.viewUser', { name: `${user.firstName} ${user.lastName}` })}
        tabIndex={0}
        sx={{
          height: '100%',
        }}
      >
        <CardContent sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
          {/* Top Section: Avatar + Info */}
          <Box sx={{ display: 'flex', gap: 2, mb: 2 }}>
            {/* Profile Picture */}
            <Avatar
              src={user.profilePictureUrl || undefined}
              sx={{
                width: 80,
                height: 80,
                bgcolor: 'primary.main',
                fontSize: '2rem',
                flexShrink: 0,
              }}
              imgProps={{ loading: 'lazy' }}
              alt={`${user.firstName} ${user.lastName}`}
            >
              {user.firstName[0]}
              {user.lastName[0]}
            </Avatar>

            {/* Info Column */}
            <Box
              sx={{ flexGrow: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 0.5 }}
            >
              {/* Status Badge */}
              <Box>
                <Chip
                  label={user.active ? t('status.active') : t('status.inactive')}
                  size="small"
                  color={user.active ? 'success' : 'default'}
                  sx={{ height: 20, fontSize: '0.7rem' }}
                />
              </Box>

              {/* User Name */}
              <Typography
                variant="h6"
                component="h3"
                sx={{
                  fontSize: '1rem',
                  fontWeight: 600,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                  lineHeight: 1.2,
                }}
              >
                {user.firstName} {user.lastName}
              </Typography>

              {/* Email */}
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                <EmailIcon sx={{ fontSize: 14, color: 'text.secondary', flexShrink: 0 }} />
                <Typography
                  variant="body2"
                  color="text.secondary"
                  sx={{
                    fontSize: '0.8rem',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                  }}
                >
                  {user.email}
                </Typography>
              </Box>

              {/* Company */}
              {user.companyId && (
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                  <BusinessIcon sx={{ fontSize: 14, color: 'text.secondary', flexShrink: 0 }} />
                  <Box sx={{ flexGrow: 1, minWidth: 0 }}>
                    <CompanyCell companyId={user.companyId} />
                  </Box>
                </Box>
              )}
            </Box>
          </Box>

          {/* Roles Section - Bottom */}
          <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', mt: 'auto' }}>
            {user.roles.map((role) => (
              <Chip
                key={role}
                label={t(`filters.role.${role.toLowerCase()}`)}
                size="small"
                color={getRoleBadgeColor(role as Role)}
                icon={<span>{ROLE_ICONS[role as Role]}</span>}
                sx={{ height: 24, fontSize: '0.75rem' }}
              />
            ))}
          </Box>
        </CardContent>
      </CardActionArea>
    </Card>
  );
};

export default UserCard;
