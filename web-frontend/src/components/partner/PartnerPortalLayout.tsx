/**
 * PartnerPortalLayout
 * Story 8.0: AC3 — top-level navigation for partner portal
 */
import React from 'react';
import { Outlet, NavLink, useLocation } from 'react-router-dom';
import { Box, Tabs, Tab } from '@mui/material';
import { useTranslation } from 'react-i18next';

const NAV_ITEMS = [
  { labelKey: 'portal.nav.myCompany', path: '/partners/company' },
  { labelKey: 'portal.nav.analytics', path: '/partners/analytics' },
  { labelKey: 'portal.nav.topics', path: '/partners/topics' },
] as const;

export const PartnerPortalLayout: React.FC = () => {
  const { t } = useTranslation('partners');
  const location = useLocation();

  // Determine active tab index from current path
  const activeIndex = NAV_ITEMS.findIndex((item) => location.pathname.startsWith(item.path));
  const currentTab = activeIndex >= 0 ? activeIndex : 0;

  return (
    <Box>
      <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
        <Tabs value={currentTab} aria-label="Partner portal navigation">
          {NAV_ITEMS.map((item, index) => (
            <Tab
              key={item.path}
              label={t(item.labelKey)}
              id={`partner-portal-tab-${index}`}
              aria-controls={`partner-portal-tabpanel-${index}`}
              component={NavLink}
              to={item.path}
            />
          ))}
        </Tabs>
      </Box>
      <Outlet />
    </Box>
  );
};
