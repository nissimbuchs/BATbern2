/**
 * TopicSelectionOverlay (Epic 14, Story 14.F.3)
 *
 * The focused, in-page topic experience opened from Details ▸ Info "Change topic"
 * (replaces the standalone /organizer/topics route for the event-bound flow).
 * Two states (UX spec §9; FR40/41/42, AR7):
 *   A. Pick — filters on a horizontal top bar + a single card grid (or heat-map).
 *   B. Brainstorm — the selected topic pins to the top + SpeakerBrainstormingPanel.
 *
 * Recompose, not rewrite (NFR9): reuses useTopics / useSelectTopicForEvent
 * (via useTopicSelection) / useSimilarTopics / useUserList / CreateTopicModal /
 * SpeakerBrainstormingPanel / MultiTopicHeatMap. No backend; search is
 * client-side over the loaded page. The standalone route is untouched.
 */

import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormControl,
  IconButton,
  InputLabel,
  List,
  ListItem,
  ListItemText,
  MenuItem,
  Select,
  Stack,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import {
  Add as AddIcon,
  Close as CloseIcon,
  ViewList as ListIcon,
  ViewModule as HeatMapIcon,
} from '@mui/icons-material';
import type { SelectChangeEvent } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTopics, useTopic } from '@/hooks/useTopics';
import { useEvents } from '@/hooks/useEvents';
import { useUserList } from '@/hooks/useUserManagement/useUserList';
import { topicService } from '@/services/topicService';
import type { Event } from '@/types/event.types';
import type { Topic, TopicFilters, TopicStatus } from '@/types/topic.types';
import { CreateTopicModal } from '@/components/TopicBacklogManager/CreateTopicModal';
import { SpeakerBrainstormingPanel } from '@/components/SpeakerBrainstormingPanel/SpeakerBrainstormingPanel';
import { MultiTopicHeatMap } from '@/components/TopicHeatMap';
import { TopicSelectionCard } from './TopicSelectionCard';
import { useTopicSelection } from './useTopicSelection';

export interface TopicSelectionOverlayProps {
  open: boolean;
  eventCode: string;
  /** The event's current topic (event.topicCode) — opens straight into State B when set. */
  currentTopicCode?: string;
  onClose: () => void;
  onConfirmed?: (topicCode: string) => void;
}

type ViewMode = 'list' | 'heatMap';

export const TopicSelectionOverlay: React.FC<TopicSelectionOverlayProps> = ({
  open,
  eventCode,
  currentTopicCode,
  onClose,
  onConfirmed,
}) => {
  const { t } = useTranslation(['events', 'organizer', 'common']);
  const navigate = useNavigate();
  const theme = useTheme();
  const queryClient = useQueryClient();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));

  const [phase, setPhase] = useState<'pick' | 'brainstorm'>(
    currentTopicCode ? 'brainstorm' : 'pick'
  );
  const [confirmedTopicCode, setConfirmedTopicCode] = useState<string | undefined>(
    currentTopicCode
  );
  const [filters, setFilters] = useState<TopicFilters>({
    page: 1,
    limit: 50,
    sort: '-stalenessScore',
    include: 'history',
  });
  const [search, setSearch] = useState('');
  const [viewMode, setViewMode] = useState<ViewMode>('list');
  const [createOpen, setCreateOpen] = useState(false);
  const [editingTopic, setEditingTopic] = useState<Topic | null>(null);
  const [deletingTopic, setDeletingTopic] = useState<Topic | null>(null);

  // On (re)open, reset the phase to match the event's current topic.
  useEffect(() => {
    if (open) {
      setConfirmedTopicCode(currentTopicCode);
      setPhase(currentTopicCode ? 'brainstorm' : 'pick');
      setSearch('');
    }
  }, [open, currentTopicCode]);

  const handleConfirmed = (topicCode: string) => {
    setConfirmedTopicCode(topicCode);
    setPhase('brainstorm');
    onConfirmed?.(topicCode);
  };

  const selection = useTopicSelection({ eventCode, onConfirmed: handleConfirmed });

  const { data, isLoading, isError } = useTopics(filters);
  const { data: confirmedTopic } = useTopic(confirmedTopicCode ?? '');
  const { data: allEventsData } = useEvents({ page: 1, limit: 1000 });
  const { data: organizersData } = useUserList({
    filters: { role: ['ORGANIZER'] },
    pagination: { page: 1, limit: 100 },
  });

  const deleteTopicMutation = useMutation({
    mutationFn: (topicCode: string) => topicService.deleteTopic(topicCode),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['topics'] });
      setDeletingTopic(null);
    },
  });

  const eventLookup = useMemo(() => {
    const map = new Map<number, { title: string }>();
    allEventsData?.data?.forEach((e: Event) => map.set(e.eventNumber, { title: e.title }));
    return map;
  }, [allEventsData]);

  const organizers = useMemo(
    () =>
      (organizersData?.data ?? []).map((u) => ({
        id: u.id,
        name: `${u.firstName} ${u.lastName}`.trim() || u.id,
      })),
    [organizersData]
  );

  const visibleTopics = useMemo(() => {
    const topics = data?.data ?? [];
    const q = search.trim().toLowerCase();
    return q ? topics.filter((tp) => tp.title.toLowerCase().includes(q)) : topics;
  }, [data, search]);

  const handleFilter =
    (key: keyof TopicFilters) =>
    (e: SelectChangeEvent<string>): void =>
      setFilters((prev) => ({ ...prev, [key]: e.target.value || undefined, page: 1 }));

  const renderTopBar = () => (
    <Stack
      direction={{ xs: 'column', md: 'row' }}
      spacing={1.5}
      alignItems={{ xs: 'stretch', md: 'center' }}
      sx={{ mb: 2 }}
      data-testid="topic-overlay-filter-bar"
    >
      <TextField
        size="small"
        label={t('eventPage.topicOverlay.searchPlaceholder', 'Search topics')}
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        sx={{ flexGrow: 1, minWidth: 160 }}
        inputProps={{ 'data-testid': 'topic-overlay-search' }}
      />
      <FormControl size="small" sx={{ minWidth: 140 }}>
        <InputLabel>{t('organizer:topicBacklog.filters.category', 'Category')}</InputLabel>
        <Select
          value={filters.category ?? ''}
          label={t('organizer:topicBacklog.filters.category', 'Category')}
          onChange={handleFilter('category')}
          data-testid="topic-overlay-filter-category"
        >
          <MenuItem value="">{t('organizer:topicBacklog.filters.all', 'All Categories')}</MenuItem>
          <MenuItem value="technical">
            {t('organizer:topicBacklog.filters.categories.technical', 'Technical')}
          </MenuItem>
          <MenuItem value="management">
            {t('organizer:topicBacklog.filters.categories.management', 'Management')}
          </MenuItem>
          <MenuItem value="soft_skills">
            {t('organizer:topicBacklog.filters.categories.softSkills', 'Soft Skills')}
          </MenuItem>
          <MenuItem value="industry_trends">
            {t('organizer:topicBacklog.filters.categories.industryTrends', 'Industry Trends')}
          </MenuItem>
          <MenuItem value="tools_platforms">
            {t('organizer:topicBacklog.filters.categories.toolsPlatforms', 'Tools & Platforms')}
          </MenuItem>
        </Select>
      </FormControl>
      <FormControl size="small" sx={{ minWidth: 140 }}>
        <InputLabel>{t('common:labels.status', 'Status')}</InputLabel>
        <Select
          value={(filters.status as string) ?? ''}
          label={t('common:labels.status', 'Status')}
          onChange={(e) =>
            setFilters((prev) => ({
              ...prev,
              status: (e.target.value as TopicStatus) || undefined,
              page: 1,
            }))
          }
        >
          <MenuItem value="">
            {t('organizer:topicBacklog.filters.allStatuses', 'All Statuses')}
          </MenuItem>
          <MenuItem value="AVAILABLE">
            {t('organizer:topicBacklog.filters.statuses.available', 'Available')}
          </MenuItem>
          <MenuItem value="CAUTION">
            {t('organizer:topicBacklog.filters.statuses.caution', 'Caution')}
          </MenuItem>
          <MenuItem value="UNAVAILABLE">
            {t('organizer:topicBacklog.filters.statuses.unavailable', 'Too Recent')}
          </MenuItem>
        </Select>
      </FormControl>
      <FormControl size="small" sx={{ minWidth: 150 }}>
        <InputLabel>{t('common:labels.sortBy', 'Sort By')}</InputLabel>
        <Select
          value={filters.sort ?? '-stalenessScore'}
          label={t('common:labels.sortBy', 'Sort By')}
          onChange={handleFilter('sort')}
        >
          <MenuItem value="-stalenessScore">
            {t('organizer:topicBacklog.filters.sortOptions.safestFirst', 'Safest First')}
          </MenuItem>
          <MenuItem value="stalenessScore">
            {t('organizer:topicBacklog.filters.sortOptions.recentFirst', 'Recent First')}
          </MenuItem>
          <MenuItem value="-createdAt">
            {t('organizer:topicBacklog.filters.sortOptions.newestFirst', 'Newest First')}
          </MenuItem>
          <MenuItem value="title">
            {t('organizer:topicBacklog.filters.sortOptions.titleAZ', 'Title (A-Z)')}
          </MenuItem>
          <MenuItem value="-usageCount">
            {t('organizer:topicBacklog.filters.sortOptions.mostUsed', 'Most Used')}
          </MenuItem>
        </Select>
      </FormControl>
      <ToggleButtonGroup
        size="small"
        exclusive
        value={viewMode}
        onChange={(_e, mode) => mode && setViewMode(mode)}
        aria-label={t('organizer:topicBacklog.viewMode.label', 'View mode')}
      >
        <ToggleButton value="list" data-testid="topic-overlay-view-list">
          <ListIcon fontSize="small" />
        </ToggleButton>
        <ToggleButton value="heatMap" data-testid="topic-overlay-view-heatmap">
          <HeatMapIcon fontSize="small" />
        </ToggleButton>
      </ToggleButtonGroup>
      <Button
        variant="contained"
        size="small"
        startIcon={<AddIcon />}
        onClick={() => {
          setEditingTopic(null);
          setCreateOpen(true);
        }}
        data-testid="topic-overlay-new-topic"
      >
        {t('organizer:topicBacklog.createNew', 'New topic')}
      </Button>
    </Stack>
  );

  const renderPick = () => (
    <Box>
      {renderTopBar()}
      {isLoading && (
        <Typography color="text.secondary">{t('common:status.loading', 'Loading…')}</Typography>
      )}
      {isError && (
        <Alert severity="error">
          {t('organizer:topicBacklog.error.loadFailed', 'Failed to load topics')}
        </Alert>
      )}
      {data && viewMode === 'heatMap' && (
        <MultiTopicHeatMap
          topics={visibleTopics}
          onTopicSelect={(tp) => selection.requestSelect(tp)}
          eventLookup={eventLookup}
        />
      )}
      {data && viewMode === 'list' && visibleTopics.length === 0 && (
        <Typography color="text.secondary" data-testid="topic-overlay-empty">
          {t('eventPage.topicOverlay.emptyHint', 'No topics match — adjust filters or search.')}
        </Typography>
      )}
      {data && viewMode === 'list' && visibleTopics.length > 0 && (
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr', lg: '1fr 1fr 1fr' },
            gap: 2,
          }}
          data-testid="topic-overlay-grid"
        >
          {visibleTopics.map((tp) => (
            <TopicSelectionCard
              key={tp.topicCode}
              topic={tp}
              hasHighSimilarity={selection.hasHighSimilarity(tp)}
              isPending={selection.isPending}
              onSelect={selection.requestSelect}
              onEdit={(topic) => {
                setEditingTopic(topic);
                setCreateOpen(true);
              }}
              onDelete={(topic) => setDeletingTopic(topic)}
            />
          ))}
        </Box>
      )}
    </Box>
  );

  const renderBrainstorm = () => (
    <Box>
      <Alert
        severity="success"
        data-testid="topic-overlay-pinned-banner"
        action={
          <Button
            color="inherit"
            size="small"
            onClick={() => setPhase('pick')}
            data-testid="topic-overlay-change-topic"
          >
            {t('eventPage.topicOverlay.banner.change', 'Change topic')}
          </Button>
        }
        sx={{ mb: 2 }}
      >
        {t('eventPage.topicOverlay.banner.selected', 'Topic selected')}:{' '}
        <strong>{confirmedTopic?.title ?? confirmedTopicCode}</strong>
        {confirmedTopic?.stalenessScore !== undefined && ` · ${confirmedTopic.stalenessScore}%`}
      </Alert>
      <SpeakerBrainstormingPanel eventCode={eventCode} organizers={organizers} />
    </Box>
  );

  return (
    <>
      <Dialog
        open={open}
        onClose={onClose}
        fullScreen={isMobile}
        maxWidth="lg"
        fullWidth
        data-testid="topic-overlay"
      >
        <DialogTitle
          sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}
        >
          {phase === 'pick'
            ? t('eventPage.topicOverlay.pickHeading', 'Choose a topic')
            : t('eventPage.topicOverlay.title', 'Topic & speakers')}
          <IconButton
            onClick={onClose}
            data-testid="topic-overlay-close"
            aria-label={t('common:actions.close', 'Close')}
          >
            <CloseIcon />
          </IconButton>
        </DialogTitle>
        <DialogContent dividers>
          {phase === 'pick' ? renderPick() : renderBrainstorm()}
        </DialogContent>
        {phase === 'brainstorm' && (
          <DialogActions>
            <Button onClick={onClose} data-testid="topic-overlay-skip">
              {t('eventPage.topicOverlay.footer.skip', 'Skip for now')}
            </Button>
            <Button
              variant="contained"
              onClick={() => {
                navigate(`/organizer/events/${eventCode}?tab=speakers&view=pool`);
                onClose();
              }}
              data-testid="topic-overlay-continue"
            >
              {t('eventPage.topicOverlay.footer.continue', 'Continue to outreach →')}
            </Button>
          </DialogActions>
        )}
      </Dialog>

      {/* Create / Edit topic */}
      <CreateTopicModal
        open={createOpen}
        topic={editingTopic}
        onClose={() => {
          setCreateOpen(false);
          setEditingTopic(null);
        }}
      />

      {/* Similarity confirm (high >0.7) */}
      <Dialog
        open={selection.mode === 'similar'}
        onClose={selection.cancel}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>
          {t('organizer:topicBacklog.dialogs.similar.title', 'Similar Topics Detected')}
        </DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t(
              'organizer:topicBacklog.dialogs.similar.message',
              'The following topics are highly similar. Proceed anyway?'
            )}
          </DialogContentText>
          <List dense>
            {selection.similarTopics.map((st) => (
              <ListItem key={st.topicCode}>
                <ListItemText primary={st.title} />
              </ListItem>
            ))}
          </List>
          <TextField
            fullWidth
            multiline
            rows={3}
            label={t(
              'organizer:topicBacklog.dialogs.similar.justification',
              'Justification (optional)'
            )}
            value={selection.justification}
            onChange={(e) => selection.setJustification(e.target.value)}
            sx={{ mt: 1 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={selection.cancel}>{t('common:actions.cancel', 'Cancel')}</Button>
          <Button
            variant="contained"
            onClick={selection.commit}
            data-testid="topic-overlay-similar-confirm"
          >
            {t('organizer:topicBacklog.dialogs.similar.confirm', 'Select Anyway')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Override (too recent) — justification required */}
      <Dialog
        open={selection.mode === 'override'}
        onClose={selection.cancel}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>
          {t('organizer:topicBacklog.dialogs.override.title', 'Override Staleness Warning')}
        </DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t(
              'organizer:topicBacklog.dialogs.override.message',
              'This topic was used recently. Please provide a justification for reusing it.'
            )}
          </DialogContentText>
          <TextField
            fullWidth
            required
            multiline
            rows={4}
            label={t('organizer:topicBacklog.dialogs.override.justification', 'Justification')}
            value={selection.justification}
            onChange={(e) => selection.setJustification(e.target.value)}
            sx={{ mt: 1 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={selection.cancel}>{t('common:actions.cancel', 'Cancel')}</Button>
          <Button
            variant="contained"
            onClick={selection.commit}
            disabled={!selection.justification.trim()}
            data-testid="topic-overlay-override-confirm"
          >
            {t('organizer:topicBacklog.dialogs.override.confirm', 'Override and Select')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Delete confirm */}
      <Dialog open={!!deletingTopic} onClose={() => setDeletingTopic(null)} maxWidth="sm" fullWidth>
        <DialogTitle>
          {t('organizer:topicBacklog.dialogs.delete.title', 'Delete Topic')}
        </DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t(
              'organizer:topicBacklog.dialogs.delete.message',
              'Delete "{{title}}"? This cannot be undone.',
              {
                title: deletingTopic?.title ?? '',
              }
            )}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeletingTopic(null)} disabled={deleteTopicMutation.isPending}>
            {t('common:actions.cancel', 'Cancel')}
          </Button>
          <Button
            color="error"
            variant="contained"
            disabled={deleteTopicMutation.isPending}
            onClick={() => deletingTopic && deleteTopicMutation.mutate(deletingTopic.topicCode)}
            data-testid="topic-overlay-delete-confirm"
          >
            {t('organizer:topicBacklog.dialogs.delete.confirm', 'Delete Topic')}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default TopicSelectionOverlay;
