/**
 * BATbern brand color palette for Recharts visualizations.
 * Story 10.5: Analytics Dashboard (AC1)
 *
 * Source: web-frontend/src/theme/theme.ts (swissColors)
 */

export const CHART_COLORS = {
  primary: '#2C5F7C', // organizer blue — main bars, primary series
  light: '#4A90B8', // new attendees, secondary series
  dark: '#1A3A4D', // trend lines, borders
  partner: '#E67E22', // partner accent — returning attendees, own-company highlight
  success: '#27AE60', // positive metrics
  error: '#E74C3C', // warnings, out-of-bounds
  info: '#3498DB', // info/attendee
  purple: '#9B59B6', // additional series
  grey: '#95A5A6', // disabled/secondary
} as const;

/** Category color mapping (aligned with blob selector clusters from Story 10.4) */
export const CATEGORY_COLORS: Record<string, string> = {
  ARCHITECTURE: '#2C5F7C',
  technical: '#2C5F7C',
  SECURITY: '#E74C3C',
  DATA: '#27AE60',
  CLOUD_INFRA: '#4A90B8',
  AI_ML: '#9B59B6',
  MOBILE: '#E67E22',
  BUSINESS_OTHER: '#95A5A6',
  management: '#9B59B6',
  soft_skills: '#27AE60',
  industry_trends: '#E67E22',
  tools_platforms: '#4A90B8',
  DEFAULT: '#3498DB',
};

export const getCategoryColor = (category?: string | null): string =>
  (category && CATEGORY_COLORS[category]) || CATEGORY_COLORS.DEFAULT;
