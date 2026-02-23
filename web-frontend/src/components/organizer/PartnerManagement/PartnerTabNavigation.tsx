import React from 'react';
import { Tabs, Tab, Box, useTheme, useMediaQuery } from '@mui/material';

type UserRole = 'ORGANIZER' | 'PARTNER' | 'SPEAKER' | 'ATTENDEE';

interface PartnerTabNavigationProps {
  activeTab: number;
  onTabChange: (tabIndex: number) => void;
  role?: UserRole;
}

const ALL_TAB_LABELS = ['Overview', 'Contacts', 'Meetings', 'Analytics', 'Notes', 'Settings'];

// Story 8.0: Settings tab hidden for PARTNER role
const getVisibleTabs = (role?: UserRole) => {
  if (role === 'PARTNER') {
    return ALL_TAB_LABELS.filter((label) => label !== 'Settings');
  }
  return ALL_TAB_LABELS;
};

export const PartnerTabNavigation: React.FC<PartnerTabNavigationProps> = ({
  activeTab,
  onTabChange,
  role,
}) => {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm')); // <640px
  const visibleTabs = getVisibleTabs(role);

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
        {visibleTabs.map((label, index) => (
          <Tab
            key={label}
            label={label}
            id={`partner-tab-${index}`}
            aria-controls={`partner-tabpanel-${index}`}
          />
        ))}
      </Tabs>
    </Box>
  );
};
