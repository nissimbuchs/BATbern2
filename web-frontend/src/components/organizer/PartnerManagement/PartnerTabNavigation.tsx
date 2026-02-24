import React from 'react';
import { Tabs, Tab, Box, useTheme, useMediaQuery } from '@mui/material';
import { useTranslation } from 'react-i18next';

type UserRole = 'ORGANIZER' | 'PARTNER' | 'SPEAKER' | 'ATTENDEE';

interface PartnerTabNavigationProps {
  activeTab: number;
  onTabChange: (tabIndex: number) => void;
  role?: UserRole;
}

export const PartnerTabNavigation: React.FC<PartnerTabNavigationProps> = ({
  activeTab,
  onTabChange,
  role,
}) => {
  const { t } = useTranslation('partners');
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm')); // <640px

  const allTabs = [
    { key: 'overview', label: t('detail.tabs.overview') },
    { key: 'contacts', label: t('detail.tabs.contacts') },
    { key: 'meetings', label: t('detail.tabs.meetings') },
    { key: 'analytics', label: t('detail.tabs.analytics') },
    { key: 'notes', label: t('detail.tabs.notes') },
    { key: 'settings', label: t('detail.tabs.settings') },
  ];

  // Story 8.0: Settings tab hidden for PARTNER role
  // Story 8.4: Notes tab also hidden for PARTNER (organizer-internal notes)
  const visibleTabs =
    role === 'PARTNER'
      ? allTabs.filter((tab) => tab.key !== 'settings' && tab.key !== 'notes')
      : allTabs;

  const handleChange = (_event: React.SyntheticEvent, newValue: number) => {
    onTabChange(newValue);
  };

  const handleKeyDown = (event: React.KeyboardEvent) => {
    const totalTabs = visibleTabs.length;

    switch (event.key) {
      case 'ArrowRight':
        event.preventDefault();
        onTabChange((activeTab + 1) % totalTabs);
        break;
      case 'ArrowLeft':
        event.preventDefault();
        onTabChange((activeTab - 1 + totalTabs) % totalTabs);
        break;
      case 'Home':
        event.preventDefault();
        onTabChange(0);
        break;
      case 'End':
        event.preventDefault();
        onTabChange(totalTabs - 1);
        break;
      default:
        break;
    }
  };

  return (
    <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
      <Tabs
        value={activeTab}
        onChange={handleChange}
        onKeyDown={handleKeyDown}
        aria-label="Partner detail tabs"
        variant="scrollable"
        scrollButtons="auto"
        orientation={isMobile ? 'vertical' : 'horizontal'}
      >
        {visibleTabs.map((tab, index) => (
          <Tab
            key={tab.key}
            label={tab.label}
            id={`partner-tab-${index}`}
            aria-controls={`partner-tabpanel-${index}`}
          />
        ))}
      </Tabs>
    </Box>
  );
};
