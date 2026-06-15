/**
 * useTopicSelection (Epic 14, Story 14.F.3)
 *
 * The select-a-topic-for-an-event decision logic, lifted out of TopicDetailsPanel
 * so the focused overlay's card grid can reuse it once (no per-card duplication).
 *
 * Routing mirrors TopicDetailsPanel exactly:
 *   - stalenessScore < 50 (too recent)  → 'override'  (justification required)
 *   - else high similarity (>0.7)        → 'similar'   (confirm, justification optional)
 *   - else                               → commit directly
 *
 * On a successful select it calls `onConfirmed(topicCode)` (the overlay then pins
 * the topic and switches to brainstorming). Recompose, not rewrite (NFR9) — no
 * new backend; reuses `useSelectTopicForEvent` + `useSimilarTopics`.
 */

import { useState } from 'react';
import { useSelectTopicForEvent, useSimilarTopics } from '@/hooks/useTopics';
import type { Topic } from '@/types/topic.types';

export const SIMILARITY_THRESHOLD = 0.7;
export const RECENT_STALENESS = 50;

export type TopicSelectionMode = 'idle' | 'similar' | 'override';

export interface UseTopicSelectionOptions {
  eventCode: string;
  onConfirmed?: (topicCode: string) => void;
}

export interface UseTopicSelectionResult {
  /** The topic currently going through a confirm dialog (null when none). */
  actingTopic: Topic | null;
  /** Which confirm dialog (if any) is open. */
  mode: TopicSelectionMode;
  justification: string;
  setJustification: (value: string) => void;
  isPending: boolean;
  /** High-similarity (>0.7) topics for the acting topic — drives the similar dialog list. */
  similarTopics: Topic[];
  /** Does this topic have any >0.7 similarity score? (drives the inline card warning) */
  hasHighSimilarity: (topic: Topic) => boolean;
  /** Entry point from a card's Select button — routes to direct commit / similar / override. */
  requestSelect: (topic: Topic) => void;
  /** Confirm the acting topic from inside a dialog. */
  commit: () => void;
  /** Dismiss any open dialog without selecting. */
  cancel: () => void;
}

export function useTopicSelection({
  eventCode,
  onConfirmed,
}: UseTopicSelectionOptions): UseTopicSelectionResult {
  const [actingTopic, setActingTopic] = useState<Topic | null>(null);
  const [mode, setMode] = useState<TopicSelectionMode>('idle');
  const [justification, setJustification] = useState('');

  const selectMutation = useSelectTopicForEvent();
  // Only fetches when a topic is acting (id empty → query disabled).
  const { data: similarTopicsRaw } = useSimilarTopics(actingTopic?.topicCode ?? '');

  const hasHighSimilarity = (topic: Topic): boolean =>
    (topic.similarityScores ?? []).some((s) => (s.score ?? 0) > SIMILARITY_THRESHOLD);

  const similarTopics: Topic[] = (similarTopicsRaw ?? []).filter((st) => {
    const sim = (actingTopic?.similarityScores ?? []).find((s) => s.topicCode === st.topicCode);
    return !!sim && (sim.score ?? 0) > SIMILARITY_THRESHOLD;
  });

  const reset = () => {
    setActingTopic(null);
    setMode('idle');
    setJustification('');
  };

  const doCommit = (topic: Topic, just?: string) => {
    selectMutation.mutate(
      { eventCode, request: { topicCode: topic.topicCode, justification: just || undefined } },
      {
        onSuccess: () => {
          onConfirmed?.(topic.topicCode);
          reset();
        },
      }
    );
  };

  const requestSelect = (topic: Topic) => {
    setActingTopic(topic);
    setJustification('');
    if (topic.stalenessScore < RECENT_STALENESS) {
      setMode('override');
    } else if (hasHighSimilarity(topic)) {
      setMode('similar');
    } else {
      setMode('idle');
      doCommit(topic);
    }
  };

  const commit = () => {
    if (actingTopic) {
      doCommit(actingTopic, justification);
    }
  };

  return {
    actingTopic,
    mode,
    justification,
    setJustification,
    isPending: selectMutation.isPending,
    similarTopics,
    hasHighSimilarity,
    requestSelect,
    commit,
    cancel: reset,
  };
}

export default useTopicSelection;
