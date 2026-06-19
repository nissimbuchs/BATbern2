/**
 * TopicSelectionCard (Epic 14, Story 14.F.3)
 *
 * A single topic in the overlay's State-A grid, folding the old right-hand
 * detail panel onto the card: title, staleness chip + colour-coded left border,
 * category, last-used + usage count, inline similarity warning, and the
 * Select / Edit / Delete actions. A too-recent (red) topic shows "Select
 * anyway…" — the override/justification dialog lives in the overlay
 * (driven by useTopicSelection). No backend (NFR9).
 */

import React from 'react';
import { Box, Button, Card, CardContent, Chip, Stack, Typography } from '@mui/material';
import { Warning as WarningIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import type { Topic } from '@/types/topic.types';
import { RECENT_STALENESS } from './useTopicSelection';

export interface TopicSelectionCardProps {
  topic: Topic;
  hasHighSimilarity: boolean;
  isPending: boolean;
  onSelect: (topic: Topic) => void;
  onEdit: (topic: Topic) => void;
  onDelete: (topic: Topic) => void;
}

const CATEGORY_KEY: Record<string, string> = {
  technical: 'technical',
  management: 'management',
  soft_skills: 'softSkills',
  industry_trends: 'industryTrends',
  tools_platforms: 'toolsPlatforms',
};

const zoneColor = (zone: Topic['colorZone']): 'error' | 'warning' | 'success' =>
  zone === 'red' ? 'error' : zone === 'yellow' ? 'warning' : 'success';

const borderColor = (zone: Topic['colorZone']): string =>
  zone === 'red' ? 'error.main' : zone === 'yellow' ? 'warning.main' : 'success.main';

export const TopicSelectionCard: React.FC<TopicSelectionCardProps> = ({
  topic,
  hasHighSimilarity,
  isPending,
  onSelect,
  onEdit,
  onDelete,
}) => {
  const { t } = useTranslation(['events', 'organizer', 'common']);
  const isRecent = topic.stalenessScore < RECENT_STALENESS;
  const canDelete = !topic.usageCount || topic.usageCount === 0;

  return (
    <Card
      variant="outlined"
      data-testid={`topic-card-${topic.topicCode}`}
      sx={{ borderLeft: 4, borderLeftColor: borderColor(topic.colorZone), height: '100%' }}
    >
      <CardContent>
        <Stack spacing={1.5}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1 }}>
            <Typography variant="subtitle1" fontWeight={600}>
              {topic.title}
            </Typography>
            <Chip
              label={`${topic.stalenessScore}%`}
              size="small"
              color={zoneColor(topic.colorZone)}
              data-testid={`topic-card-staleness-${topic.topicCode}`}
            />
          </Box>

          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'center' }}>
            <Chip
              label={t(
                `organizer:topicBacklog.filters.categories.${CATEGORY_KEY[topic.category] ?? topic.category}`,
                topic.category
              )}
              size="small"
              variant="outlined"
            />
            {topic.usageCount !== undefined && (
              <Typography variant="caption" color="text.secondary">
                {t('organizer:topicBacklog.details.usageCount', 'Usage Count')}: {topic.usageCount}
              </Typography>
            )}
            {topic.lastUsedDate && (
              <Typography variant="caption" color="text.secondary">
                {t('organizer:topicBacklog.details.lastUsed', 'Last Used')}:{' '}
                {new Date(topic.lastUsedDate).toLocaleDateString()}
              </Typography>
            )}
          </Box>

          {hasHighSimilarity && (
            <Box
              sx={{ display: 'flex', alignItems: 'center', gap: 0.5, color: 'warning.main' }}
              data-testid={`topic-card-similarity-${topic.topicCode}`}
            >
              <WarningIcon fontSize="small" />
              <Typography variant="caption">
                {t('eventPage.topicOverlay.similarityWarning', 'Similar topic detected')}
              </Typography>
            </Box>
          )}

          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            <Button
              variant="contained"
              size="small"
              color={isRecent ? 'warning' : 'primary'}
              onClick={() => onSelect(topic)}
              disabled={isPending}
              data-testid={`topic-card-select-${topic.topicCode}`}
            >
              {isRecent
                ? t('eventPage.topicOverlay.selectAnyway', 'Select anyway…')
                : t('organizer:topicBacklog.details.selectButton', 'Select for Event')}
            </Button>
            <Button
              variant="outlined"
              size="small"
              onClick={() => onEdit(topic)}
              disabled={isPending}
              data-testid={`topic-card-edit-${topic.topicCode}`}
            >
              {t('organizer:topicBacklog.details.editButton', 'Edit')}
            </Button>
            <Button
              variant="outlined"
              size="small"
              color="error"
              onClick={() => onDelete(topic)}
              disabled={isPending || !canDelete}
              data-testid={`topic-card-delete-${topic.topicCode}`}
            >
              {t('organizer:topicBacklog.details.deleteButton', 'Delete')}
            </Button>
          </Stack>
        </Stack>
      </CardContent>
    </Card>
  );
};

export default TopicSelectionCard;
