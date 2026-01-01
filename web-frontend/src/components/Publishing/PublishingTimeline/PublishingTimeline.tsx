import React from 'react';
import { Box, Step, StepLabel, Stepper, Typography, Chip } from '@mui/material';
import {
  CheckCircle as CheckIcon,
  RadioButtonUnchecked as PendingIcon,
  FiberManualRecord as CurrentIcon,
} from '@mui/icons-material';
import { PublishingPhase } from '@/types/event.types';

interface AutoPublishSchedule {
  phase: PublishingPhase;
  scheduledDate: string;
}

export interface PublishingTimelineProps {
  eventCode: string;
  currentPhase: PublishingPhase;
  publishedPhases: PublishingPhase[];
  eventDate: string;
  autoPublishSchedule?: AutoPublishSchedule[];
  publishedDates?: Partial<Record<PublishingPhase, string>>;
  scheduledDates?: Partial<Record<PublishingPhase, string>>;
}

const phases: Array<{ key: PublishingPhase | 'updates'; label: string }> = [
  { key: 'topic', label: 'Topic' },
  { key: 'speakers', label: 'Speakers' },
  { key: 'agenda', label: 'Agenda' },
  { key: 'updates', label: 'Updates' },
];

export const PublishingTimeline: React.FC<PublishingTimelineProps> = ({
  currentPhase,
  publishedPhases,
  eventDate,
  autoPublishSchedule = [],
  publishedDates = {},
  scheduledDates = {},
}) => {
  const getPhaseClass = (phaseKey: string): string => {
    if (phaseKey === currentPhase) return 'current';
    if (publishedPhases.includes(phaseKey as PublishingPhase)) return 'complete';
    return 'pending';
  };

  const getPhaseIcon = (phaseKey: string) => {
    const phaseClass = getPhaseClass(phaseKey);
    if (phaseClass === 'complete')
      return <CheckIcon color="success" data-testid={`icon-complete-${phaseKey}`} />;
    if (phaseClass === 'current')
      return <CurrentIcon color="primary" data-testid={`icon-current-${phaseKey}`} />;
    return <PendingIcon color="disabled" data-testid={`icon-pending-${phaseKey}`} />;
  };

  const getScheduledDate = (phaseKey: string): string | null => {
    if (phaseKey === 'topic') return 'Now';

    const schedule = autoPublishSchedule.find((s) => s.phase === phaseKey);
    if (schedule) {
      const date = new Date(schedule.scheduledDate);
      const now = new Date();
      const diffDays = Math.ceil((date.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));

      if (diffDays > 0) {
        return `${diffDays} days`;
      }
      return date.toLocaleDateString();
    }

    // Default milestones based on phase
    const eventDateObj = new Date(eventDate);
    if (phaseKey === 'speakers') {
      const speakersDate = new Date(eventDateObj);
      speakersDate.setMonth(speakersDate.getMonth() - 1);
      return speakersDate.toLocaleDateString();
    }
    if (phaseKey === 'agenda') {
      const agendaDate = new Date(eventDateObj);
      agendaDate.setDate(agendaDate.getDate() - 14);
      return agendaDate.toLocaleDateString();
    }
    if (phaseKey === 'updates') {
      return eventDateObj.toLocaleDateString();
    }
    return null;
  };

  const getAutoPublishLabel = (phaseKey: string): React.ReactNode | null => {
    const schedule = autoPublishSchedule.find((s) => s.phase === phaseKey);
    if (!schedule) return null;

    const date = new Date(schedule.scheduledDate);
    const now = new Date();
    const diffDays = Math.ceil((date.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));

    if (diffDays > 0) {
      return (
        <Chip
          label={`Auto-publish in ${diffDays} days`}
          size="small"
          color="info"
          data-testid={`auto-publish-${phaseKey}`}
        />
      );
    }
    return (
      <Chip
        label="Auto-publish scheduled"
        size="small"
        color="default"
        data-testid={`auto-publish-${phaseKey}`}
      />
    );
  };

  const activeStep = phases.findIndex((p) => p.key === currentPhase);
  const completedSteps = publishedPhases.length;
  const progressPercent = completedSteps === 0 ? 0 : (completedSteps / phases.length) * 100;

  return (
    <Box>
      {/* Progress Bar */}
      <Box sx={{ mb: 3 }}>
        <Box
          sx={{
            width: '100%',
            height: '8px',
            bgcolor: 'grey.200',
            borderRadius: '4px',
            overflow: 'hidden',
          }}
          data-testid="progress-line"
        >
          <Box
            sx={{
              width: `${progressPercent}%`,
              height: '100%',
              bgcolor: 'primary.main',
              transition: 'width 0.3s ease',
            }}
            data-testid="progress-fill"
          />
        </Box>
        <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: 'block' }}>
          {completedSteps} of {phases.length} phases published ({Math.round(progressPercent)}%)
        </Typography>
      </Box>

      {/* Timeline Stepper */}
      <Stepper
        activeStep={activeStep}
        orientation="vertical"
        sx={{
          '& .MuiStepConnector-line': {
            minHeight: '40px',
          },
        }}
      >
        {phases.map((phase) => {
          const phaseClass = getPhaseClass(phase.key);
          const scheduledDate = getScheduledDate(phase.key);
          const autoPublishLabel = getAutoPublishLabel(phase.key);

          return (
            <Step
              key={phase.key}
              completed={phaseClass === 'complete'}
              active={phaseClass === 'current'}
            >
              <StepLabel
                icon={getPhaseIcon(phase.key)}
                data-testid={`timeline-phase-${phase.key}`}
                className={phaseClass}
                optional={
                  <Box>
                    {(() => {
                      // Show published date if phase is published
                      const publishedDate = publishedDates[phase.key as PublishingPhase];
                      if (publishedDate && phaseClass === 'complete') {
                        const date = new Date(publishedDate);
                        return (
                          <Typography
                            variant="caption"
                            color="text.secondary"
                            data-testid={`published-date-${phase.key}`}
                          >
                            Published on{' '}
                            {date.toLocaleDateString('en-US', {
                              month: 'short',
                              day: 'numeric',
                              year: 'numeric',
                            })}
                          </Typography>
                        );
                      }

                      // Show scheduled date if provided
                      const schedDate = scheduledDates[phase.key as PublishingPhase];
                      if (schedDate) {
                        const date = new Date(schedDate);
                        return (
                          <Typography
                            variant="caption"
                            color="text.secondary"
                            data-testid={`scheduled-date-${phase.key}`}
                          >
                            Scheduled:{' '}
                            {date.toLocaleDateString('en-US', {
                              month: 'short',
                              day: 'numeric',
                              year: 'numeric',
                            })}
                          </Typography>
                        );
                      }

                      // Show default milestone date
                      if (scheduledDate) {
                        return (
                          <Typography
                            variant="caption"
                            color="text.secondary"
                            data-testid={`milestone-${phase.key}`}
                          >
                            {scheduledDate}
                          </Typography>
                        );
                      }

                      return null;
                    })()}
                    {autoPublishLabel}
                  </Box>
                }
              >
                <Typography
                  variant="subtitle1"
                  sx={{
                    fontWeight: phaseClass === 'current' ? 600 : 400,
                    color:
                      phaseClass === 'complete'
                        ? 'success.main'
                        : phaseClass === 'current'
                          ? 'primary.main'
                          : 'text.secondary',
                  }}
                >
                  {phase.label}
                </Typography>
              </StepLabel>
            </Step>
          );
        })}
      </Stepper>
    </Box>
  );
};
