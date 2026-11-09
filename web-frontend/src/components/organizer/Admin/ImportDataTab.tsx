/**
 * ImportDataTab (Story 10.1 - Task 3)
 *
 * Tab 1 of the Admin page.
 * Consolidates all 5 batch import modals: Events, Sessions, Companies, Speakers, Participants.
 */

import React, { useState } from 'react';
import { Box, Button, Card, CardActions, CardContent, Typography } from '@mui/material';
import Grid from '@mui/material/Grid';
import { UploadFile as UploadFileIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { EventBatchImportModal } from '@/components/shared/Event/EventBatchImportModal';
import { SessionBatchImportModal } from '@/components/shared/Session/SessionBatchImportModal';
import { CompanyBatchImportModal } from '@/components/shared/Company/CompanyBatchImportModal';
import SpeakerBatchImportModal from '@/components/organizer/UserManagement/SpeakerBatchImportModal';
import { ParticipantBatchImportModal } from '@/components/organizer/UserManagement/ParticipantBatchImportModal';

export const ImportDataTab: React.FC = () => {
  const { t } = useTranslation('admin');

  const [eventsOpen, setEventsOpen] = useState(false);
  const [sessionsOpen, setSessionsOpen] = useState(false);
  const [companiesOpen, setCompaniesOpen] = useState(false);
  const [speakersOpen, setSpeakersOpen] = useState(false);
  const [participantsOpen, setParticipantsOpen] = useState(false);

  const importCards = [
    {
      title: t('importData.events.title'),
      description: t('importData.events.description'),
      buttonLabel: t('importData.events.button'),
      testId: 'import-events-btn',
      onClick: () => setEventsOpen(true),
    },
    {
      title: t('importData.sessions.title'),
      description: t('importData.sessions.description'),
      buttonLabel: t('importData.sessions.button'),
      testId: 'import-sessions-btn',
      onClick: () => setSessionsOpen(true),
    },
    {
      title: t('importData.companies.title'),
      description: t('importData.companies.description'),
      buttonLabel: t('importData.companies.button'),
      testId: 'import-companies-btn',
      onClick: () => setCompaniesOpen(true),
    },
    {
      title: t('importData.speakers.title'),
      description: t('importData.speakers.description'),
      buttonLabel: t('importData.speakers.button'),
      testId: 'import-speakers-btn',
      onClick: () => setSpeakersOpen(true),
    },
    {
      title: t('importData.participants.title'),
      description: t('importData.participants.description'),
      buttonLabel: t('importData.participants.button'),
      testId: 'import-participants-btn',
      onClick: () => setParticipantsOpen(true),
    },
  ];

  return (
    <Box data-testid="import-data-tab">
      <Grid container spacing={3}>
        {importCards.map((card) => (
          <Grid size={{ xs: 12, sm: 6, md: 4 }} key={card.title}>
            <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
              <CardContent sx={{ flexGrow: 1 }}>
                <Typography variant="h6" gutterBottom>
                  {card.title}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {card.description}
                </Typography>
              </CardContent>
              <CardActions>
                <Button
                  startIcon={<UploadFileIcon />}
                  onClick={card.onClick}
                  data-testid={card.testId}
                >
                  {card.buttonLabel}
                </Button>
              </CardActions>
            </Card>
          </Grid>
        ))}
      </Grid>

      <EventBatchImportModal
        open={eventsOpen}
        onClose={() => setEventsOpen(false)}
        onImportComplete={() => setEventsOpen(false)}
      />
      <SessionBatchImportModal
        open={sessionsOpen}
        onClose={() => setSessionsOpen(false)}
        onImportComplete={() => setSessionsOpen(false)}
      />
      <CompanyBatchImportModal open={companiesOpen} onClose={() => setCompaniesOpen(false)} />
      <SpeakerBatchImportModal open={speakersOpen} onClose={() => setSpeakersOpen(false)} />
      <ParticipantBatchImportModal
        open={participantsOpen}
        onClose={() => setParticipantsOpen(false)}
      />
    </Box>
  );
};
