/**
 * UserAvatar Component
 *
 * Shared component for displaying user avatar with name and optional company.
 * Used across UserManagement and EventManagement components.
 *
 * Features:
 * - Avatar with initials fallback
 * - Consistent color generation from name
 * - Optional company display
 * - Configurable sizes
 * - Tooltip support
 */

import React from 'react';
import { Avatar, Box, Typography, Tooltip, Stack } from '@mui/material';

interface UserAvatarProps {
  /** User's full name or firstName/lastName separately */
  name?: string;
  firstName?: string;
  lastName?: string;
  /** Company name (optional) */
  company?: string;
  /** Profile picture URL (optional) */
  profilePictureUrl?: string;
  /** Avatar size in pixels (default: 40) */
  size?: number;
  /** Show company below name (default: true if company provided) */
  showCompany?: boolean;
  /** Use horizontal layout (avatar + text side-by-side) (default: true) */
  horizontal?: boolean;
  /** Show tooltip with full name (default: false) */
  showTooltip?: boolean;
  /** Custom background color (optional - will generate from name if not provided) */
  bgcolor?: string;
}

// Helper function to generate initials from name
const getInitials = (firstName?: string, lastName?: string, fullName?: string): string => {
  if (fullName) {
    const parts = fullName.trim().split(' ');
    if (parts.length === 0) return '?';
    if (parts.length === 1) return parts[0].charAt(0).toUpperCase();
    return (parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
  }

  if (!firstName && !lastName) return '?';
  const first = firstName?.charAt(0).toUpperCase() || '';
  const last = lastName?.charAt(0).toUpperCase() || '';
  return `${first}${last}`;
};

// Helper function to generate consistent avatar color from name
const stringToColor = (str: string): string => {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = str.charCodeAt(i) + ((hash << 5) - hash);
  }
  const hue = hash % 360;
  return `hsl(${hue}, 60%, 50%)`;
};

export const UserAvatar: React.FC<UserAvatarProps> = ({
  name,
  firstName,
  lastName,
  company,
  profilePictureUrl,
  size = 40,
  showCompany = !!company,
  horizontal = true,
  showTooltip = false,
  bgcolor,
}) => {
  // Determine full name
  const fullName =
    name || (firstName && lastName ? `${firstName} ${lastName}` : firstName || lastName || '');

  // Generate initials
  const initials = getInitials(firstName, lastName, name);

  // Generate background color if not provided
  const avatarBgColor = bgcolor || stringToColor(fullName);

  const avatarElement = (
    <Avatar
      src={profilePictureUrl}
      alt={fullName}
      sx={{
        bgcolor: avatarBgColor,
        width: size,
        height: size,
        fontSize: size * 0.4,
      }}
    >
      {initials}
    </Avatar>
  );

  const content = horizontal ? (
    <Stack direction="row" spacing={2} alignItems="center">
      {showTooltip ? (
        <Tooltip title={fullName} arrow>
          {avatarElement}
        </Tooltip>
      ) : (
        avatarElement
      )}
      <Box>
        <Typography variant="body2" fontWeight="medium">
          {fullName}
        </Typography>
        {showCompany && company && (
          <Typography variant="caption" color="text.secondary">
            {company}
          </Typography>
        )}
      </Box>
    </Stack>
  ) : (
    <Stack direction="column" spacing={1} alignItems="center">
      {showTooltip ? (
        <Tooltip title={fullName} arrow>
          {avatarElement}
        </Tooltip>
      ) : (
        avatarElement
      )}
      <Box textAlign="center">
        <Typography variant="body2" fontWeight="medium">
          {fullName}
        </Typography>
        {showCompany && company && (
          <Typography variant="caption" color="text.secondary">
            {company}
          </Typography>
        )}
      </Box>
    </Stack>
  );

  return content;
};

export default UserAvatar;
