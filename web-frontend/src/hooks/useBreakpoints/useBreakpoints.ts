/**
 * useBreakpoints Hook
 * Story 1.17, Task 11b: Centralized responsive breakpoint detection
 *
 * Provides consistent breakpoint detection across all components.
 * Breakpoints: mobile (< 900px), tablet (900px-1200px), desktop (> 1200px).
 * isWideDesktop (>= 1440px, #1198): wide enough for the labelled top navigation of an organizer.
 */

import { useMediaQuery, useTheme } from '@mui/material';

export interface Breakpoints {
  isMobile: boolean;
  isTablet: boolean;
  isDesktop: boolean;
  isWideDesktop: boolean;
}

export function useBreakpoints(): Breakpoints {
  const theme = useTheme();

  const isMobile = useMediaQuery(theme.breakpoints.down('md')); // < 900px
  const isDesktop = useMediaQuery(theme.breakpoints.up('lg')); // >= 1200px
  const isTablet = !isMobile && !isDesktop; // 900px - 1200px
  const isWideDesktop = useMediaQuery(theme.breakpoints.up(1440)); // labelled top navigation

  return {
    isMobile,
    isTablet,
    isDesktop,
    isWideDesktop,
  };
}
