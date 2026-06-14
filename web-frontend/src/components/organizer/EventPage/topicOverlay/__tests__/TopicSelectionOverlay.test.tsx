/**
 * TopicSelectionOverlay tests (Epic 14, Story 14.F.3)
 *
 * State A (pick) ↔ State B (brainstorm), client-side search, select→pin→continue.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import type { Topic } from '@/types/topic.types';

const selectMutate = vi.fn();
const navigateMock = vi.fn();

const topics: Topic[] = [
  {
    topicCode: 'cloud-native',
    title: 'Cloud Native',
    category: 'technical',
    stalenessScore: 90,
    colorZone: 'green',
    usageCount: 0,
    similarityScores: [],
  },
  {
    topicCode: 'ai-ops',
    title: 'AI Ops',
    category: 'technical',
    stalenessScore: 85,
    colorZone: 'green',
    usageCount: 0,
    similarityScores: [],
  },
] as unknown as Topic[];

vi.mock('@/hooks/useTopics', () => ({
  useTopics: () => ({ data: { data: topics, pagination: {} }, isLoading: false, isError: false }),
  useTopic: () => ({ data: { title: 'Cloud Native', stalenessScore: 90 } }),
  useSelectTopicForEvent: () => ({ mutate: selectMutate, isPending: false }),
  useSimilarTopics: () => ({ data: [] }),
}));
vi.mock('@/hooks/useEvents', () => ({ useEvents: () => ({ data: { data: [] } }) }));
vi.mock('@/hooks/useUserManagement/useUserList', () => ({
  useUserList: () => ({ data: { data: [] } }),
}));
vi.mock('@/services/topicService', () => ({ topicService: { deleteTopic: vi.fn() } }));
vi.mock('@tanstack/react-query', () => ({
  useMutation: () => ({ mutate: vi.fn(), isPending: false }),
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
}));
vi.mock('react-router-dom', () => ({ useNavigate: () => navigateMock }));
vi.mock('@/components/TopicBacklogManager/CreateTopicModal', () => ({
  CreateTopicModal: () => null,
}));
vi.mock('@/components/SpeakerBrainstormingPanel/SpeakerBrainstormingPanel', () => ({
  SpeakerBrainstormingPanel: () => <div data-testid="brainstorm-panel" />,
}));
vi.mock('@/components/TopicHeatMap', () => ({
  MultiTopicHeatMap: () => <div data-testid="heatmap" />,
}));
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: string | Record<string, unknown>) =>
      typeof opts === 'string' ? opts : ((opts?.defaultValue as string) ?? key),
  }),
}));

import { TopicSelectionOverlay } from '../TopicSelectionOverlay';

const setup = (props: Partial<React.ComponentProps<typeof TopicSelectionOverlay>> = {}) =>
  render(
    <TopicSelectionOverlay
      open
      eventCode="BAT54"
      onClose={vi.fn()}
      onConfirmed={vi.fn()}
      {...props}
    />
  );

beforeEach(() => {
  vi.clearAllMocks();
  selectMutate.mockImplementation((_vars, opts) => opts?.onSuccess?.());
});

describe('TopicSelectionOverlay', () => {
  it('opens in pick state with a card grid', () => {
    setup();
    expect(screen.getByTestId('topic-overlay-grid')).toBeInTheDocument();
    expect(screen.getByTestId('topic-card-cloud-native')).toBeInTheDocument();
    expect(screen.getByTestId('topic-card-ai-ops')).toBeInTheDocument();
  });

  it('opens directly in brainstorm state when the event already has a topic', () => {
    setup({ currentTopicCode: 'cloud-native' });
    expect(screen.getByTestId('topic-overlay-pinned-banner')).toBeInTheDocument();
    expect(screen.getByTestId('brainstorm-panel')).toBeInTheDocument();
  });

  it('filters the grid client-side by search', () => {
    setup();
    fireEvent.change(screen.getByTestId('topic-overlay-search'), { target: { value: 'cloud' } });
    expect(screen.getByTestId('topic-card-cloud-native')).toBeInTheDocument();
    expect(screen.queryByTestId('topic-card-ai-ops')).not.toBeInTheDocument();
  });

  it('selecting a safe topic pins it and switches to brainstorm', () => {
    const onConfirmed = vi.fn();
    setup({ onConfirmed });
    fireEvent.click(screen.getByTestId('topic-card-select-cloud-native'));
    expect(selectMutate).toHaveBeenCalled();
    expect(onConfirmed).toHaveBeenCalledWith('cloud-native');
    expect(screen.getByTestId('topic-overlay-pinned-banner')).toBeInTheDocument();
  });

  it('Continue to outreach navigates to Speakers · Pool and closes', () => {
    const onClose = vi.fn();
    setup({ currentTopicCode: 'cloud-native', onClose });
    fireEvent.click(screen.getByTestId('topic-overlay-continue'));
    expect(navigateMock).toHaveBeenCalledWith('/organizer/events/BAT54?tab=speakers&view=pool');
    expect(onClose).toHaveBeenCalled();
  });
});
