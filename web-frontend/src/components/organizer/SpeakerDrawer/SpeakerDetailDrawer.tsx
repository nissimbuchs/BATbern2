import React, { useState } from 'react';
import { Drawer, Box, Tabs, Tab, useMediaQuery, useTheme } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { SpeakerDrawerHeader } from './SpeakerDrawerHeader';
import { OverviewTabPanel } from './OverviewTabPanel';
import { DetailsTabPanel } from './DetailsTabPanel';
import { ActivityTabPanel } from './ActivityTabPanel';
import { ContentSubmissionSubView } from './ContentSubmissionSubView';
import { QualityReviewSubView } from './QualityReviewSubView';
import { getDefaultTab } from './getDefaultTab';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';

type DrawerView = null | 'content-submission' | 'quality-review';

interface SpeakerDetailDrawerProps {
  open: boolean;
  onClose: () => void;
  speaker: SpeakerPoolEntry | null;
  eventCode: string;
}

export const SpeakerDetailDrawer: React.FC<SpeakerDetailDrawerProps> = ({
  open,
  onClose,
  speaker,
  eventCode,
}) => {
  const { t } = useTranslation('organizer');
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));

  const [tab, setTab] = useState(0);
  const [drawerView, setDrawerView] = useState<DrawerView>(null);

  // Reset tab and drawerView on speaker change or drawer open (derived state pattern)
  const [prevSpeakerKey, setPrevSpeakerKey] = useState<string | null>(null);
  const speakerKey = open && speaker ? speaker.id : null;
  if (speakerKey !== prevSpeakerKey) {
    setPrevSpeakerKey(speakerKey);
    if (speaker && open) {
      setTab(getDefaultTab(speaker));
      setDrawerView(null);
    }
  }

  const handleTabChange = (_event: React.SyntheticEvent, newValue: number) => {
    setTab(newValue);
  };

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      PaperProps={{
        sx: { width: { xs: '100%', sm: 520 } },
      }}
    >
      <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
        <SpeakerDrawerHeader speaker={speaker} onClose={onClose} />

        {speaker && drawerView === null && (
          <>
            <Tabs
              value={tab}
              onChange={handleTabChange}
              variant={isMobile ? 'scrollable' : 'standard'}
              scrollButtons={isMobile ? 'auto' : false}
              sx={{ borderBottom: 1, borderColor: 'divider', flexShrink: 0 }}
            >
              <Tab label={t('speakers.tabs.overview')} />
              <Tab label={t('speakers.tabs.details')} />
              <Tab label={t('speakers.tabs.activity')} />
            </Tabs>

            <Box sx={{ flex: 1, overflow: 'auto' }}>
              {tab === 0 && (
                <OverviewTabPanel
                  speaker={speaker}
                  eventCode={eventCode}
                  isMobile={isMobile}
                  onOpenContentSubmission={() => setDrawerView('content-submission')}
                  onOpenQualityReview={() => setDrawerView('quality-review')}
                />
              )}
              {tab === 1 && <DetailsTabPanel speaker={speaker} />}
              {tab === 2 && <ActivityTabPanel speaker={speaker} eventCode={eventCode} />}
            </Box>
          </>
        )}

        {speaker && drawerView === 'content-submission' && (
          <ContentSubmissionSubView
            speaker={speaker}
            eventCode={eventCode}
            onBack={() => setDrawerView(null)}
            onClose={onClose}
          />
        )}

        {speaker && drawerView === 'quality-review' && (
          <QualityReviewSubView
            speaker={speaker}
            eventCode={eventCode}
            onBack={() => setDrawerView(null)}
            onClose={onClose}
          />
        )}
      </Box>
    </Drawer>
  );
};
