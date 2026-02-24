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
  const { t } = useTranslation();

  const [eventsOpen, setEventsOpen] = useState(false);
  const [sessionsOpen, setSessionsOpen] = useState(false);
  const [companiesOpen, setCompaniesOpen] = useState(false);
  const [speakersOpen, setSpeakersOpen] = useState(false);
  const [participantsOpen, setParticipantsOpen] = useState(false);

  const importCards = [
    {
      title: t('import.events.title', 'Events'),
      description: t('import.events.description', 'Import historical event data from CSV or JSON.'),
      buttonLabel: t('import.events.button', 'Import Events'),
      testId: 'import-events-btn',
      onClick: () => setEventsOpen(true),
    },
    {
      title: t('import.sessions.title', 'Sessions'),
      description: t('import.sessions.description', 'Import session data for existing events.'),
      buttonLabel: t('import.sessions.button', 'Import Sessions'),
      testId: 'import-sessions-btn',
      onClick: () => setSessionsOpen(true),
    },
    {
      title: t('import.companies.title', 'Companies'),
      description: t('import.companies.description', 'Import company / partner data.'),
      buttonLabel: t('import.companies.button', 'Import Companies'),
      testId: 'import-companies-btn',
      onClick: () => setCompaniesOpen(true),
    },
    {
      title: t('import.speakers.title', 'Speakers'),
      description: t('import.speakers.description', 'Import speaker profiles from legacy data.'),
      buttonLabel: t('import.speakers.button', 'Import Speakers'),
      testId: 'import-speakers-btn',
      onClick: () => setSpeakersOpen(true),
    },
    {
      title: t('import.participants.title', 'Participants'),
      description: t('import.participants.description', 'Import attendee / participant records.'),
      buttonLabel: t('import.participants.button', 'Import Participants'),
      testId: 'import-participants-btn',
      onClick: () => setParticipantsOpen(true),
    },
  ];

  return (
    <Box>
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
