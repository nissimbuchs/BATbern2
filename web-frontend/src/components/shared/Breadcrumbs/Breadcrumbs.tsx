/**
 * Breadcrumbs Component (Story 1.17 - Deferred Navigation Feature)
 *
 * Reusable breadcrumb navigation component providing contextual navigation trails.
 * Required for organizer role, optional for other roles.
 *
 * Features:
 * - Clickable navigation links
 * - Automatic home icon on first item
 * - Support for dynamic breadcrumb items
 * - i18n support for labels
 * - Accessible with proper ARIA labels
 * - MUI NavigateNext separator
 *
 * Usage:
 * ```tsx
 * <Breadcrumbs
 *   items={[
 *     { label: 'Home', path: '/organizer/events' },
 *     { label: 'Event Detail', path: '/organizer/events/BATbern56' },
 *     { label: 'Edit' } // Current page (no path = not clickable)
 *   ]}
 * />
 * ```
 */

import React from 'react';
import { useNavigate } from 'react-router-dom';
import { Breadcrumbs as MuiBreadcrumbs, Link, Typography, Box } from '@mui/material';
import { Home as HomeIcon, NavigateNext as NavigateNextIcon } from '@mui/icons-material';

export interface BreadcrumbItem {
  /** Display label for the breadcrumb */
  label: string;
  /** Navigation path (if undefined, item is not clickable - represents current page) */
  path?: string;
  /** Optional icon to display before label (only for first item if not provided) */
  icon?: React.ReactNode;
}

export interface BreadcrumbsProps {
  /** Array of breadcrumb items to display */
  items: BreadcrumbItem[];
  /** Optional aria-label for accessibility (defaults to "breadcrumb") */
  ariaLabel?: string;
  /** Optional margin bottom spacing (defaults to 3) */
  marginBottom?: number;
}

/**
 * Breadcrumbs component for contextual navigation.
 * Automatically adds Home icon to first item and handles navigation.
 */
export const Breadcrumbs: React.FC<BreadcrumbsProps> = React.memo(
  ({ items, ariaLabel = 'breadcrumb', marginBottom = 3 }) => {
    const navigate = useNavigate();

    const handleClick = React.useCallback(
      (path: string) => (event: React.MouseEvent) => {
        event.preventDefault();
        navigate(path);
      },
      [navigate]
    );

    return (
      <MuiBreadcrumbs
        separator={<NavigateNextIcon fontSize="small" />}
        aria-label={ariaLabel}
        sx={{ mb: marginBottom }}
      >
        {items.map((item, index) => {
          const isFirst = index === 0;
          const isLast = index === items.length - 1;
          const showHomeIcon = isFirst && !item.icon;

          // Current page (no path) - not clickable
          if (!item.path || isLast) {
            return (
              <Typography key={index} color="text.primary">
                {item.label}
              </Typography>
            );
          }

          // Clickable link
          return (
            <Link
              key={index}
              underline="hover"
              color="inherit"
              sx={{
                display: 'flex',
                alignItems: 'center',
                cursor: 'pointer',
              }}
              onClick={handleClick(item.path)}
            >
              {showHomeIcon && <HomeIcon sx={{ mr: 0.5 }} fontSize="inherit" />}
              {item.icon && <Box sx={{ mr: 0.5, display: 'flex' }}>{item.icon}</Box>}
              {item.label}
            </Link>
          );
        })}
      </MuiBreadcrumbs>
    );
  }
);

Breadcrumbs.displayName = 'Breadcrumbs';

export default Breadcrumbs;
