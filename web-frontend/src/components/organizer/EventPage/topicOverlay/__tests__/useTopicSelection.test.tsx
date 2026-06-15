/**
 * useTopicSelection tests (Epic 14, Story 14.F.3)
 *
 * Verifies the safe / similar / override routing and that a successful commit
 * calls onConfirmed with the topic code.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import type { Topic } from '@/types/topic.types';

const mutate = vi.fn();
let similarData: Topic[] = [];

vi.mock('@/hooks/useTopics', () => ({
  useSelectTopicForEvent: () => ({ mutate, isPending: false }),
  useSimilarTopics: () => ({ data: similarData }),
}));

import { useTopicSelection } from '../useTopicSelection';

const topic = (over: Partial<Topic> = {}): Topic =>
  ({
    topicCode: 'cloud-native',
    title: 'Cloud Native',
    category: 'technical',
    stalenessScore: 90,
    colorZone: 'green',
    usageCount: 0,
    similarityScores: [],
    ...over,
  }) as unknown as Topic;

beforeEach(() => {
  vi.clearAllMocks();
  similarData = [];
});

describe('useTopicSelection', () => {
  it('commits directly for a safe topic (no dialog) and calls onConfirmed', () => {
    const onConfirmed = vi.fn();
    mutate.mockImplementation((_vars, opts) => opts?.onSuccess?.());
    const { result } = renderHook(() => useTopicSelection({ eventCode: 'BAT54', onConfirmed }));

    act(() => result.current.requestSelect(topic()));

    expect(mutate).toHaveBeenCalledWith(
      { eventCode: 'BAT54', request: { topicCode: 'cloud-native', justification: undefined } },
      expect.any(Object)
    );
    expect(onConfirmed).toHaveBeenCalledWith('cloud-native');
    expect(result.current.mode).toBe('idle');
  });

  it('opens the override dialog for a too-recent topic (stalenessScore < 50)', () => {
    const { result } = renderHook(() => useTopicSelection({ eventCode: 'BAT54' }));
    act(() => result.current.requestSelect(topic({ stalenessScore: 30 })));

    expect(result.current.mode).toBe('override');
    expect(mutate).not.toHaveBeenCalled();
  });

  it('opens the similar dialog when a >0.7 similarity exists', () => {
    similarData = [topic({ topicCode: 'twin', title: 'Twin Topic' })];
    const { result } = renderHook(() => useTopicSelection({ eventCode: 'BAT54' }));
    act(() =>
      result.current.requestSelect(
        topic({
          similarityScores: [{ topicCode: 'twin', score: 0.82 }] as Topic['similarityScores'],
        })
      )
    );

    expect(result.current.mode).toBe('similar');
    expect(result.current.similarTopics).toHaveLength(1);
    expect(mutate).not.toHaveBeenCalled();
  });

  it('commit() sends the justification and confirms', () => {
    const onConfirmed = vi.fn();
    mutate.mockImplementation((_vars, opts) => opts?.onSuccess?.());
    const { result } = renderHook(() => useTopicSelection({ eventCode: 'BAT54', onConfirmed }));

    act(() => result.current.requestSelect(topic({ stalenessScore: 20 })));
    act(() => result.current.setJustification('Worth revisiting'));
    act(() => result.current.commit());

    expect(mutate).toHaveBeenCalledWith(
      {
        eventCode: 'BAT54',
        request: { topicCode: 'cloud-native', justification: 'Worth revisiting' },
      },
      expect.any(Object)
    );
    expect(onConfirmed).toHaveBeenCalledWith('cloud-native');
  });

  it('cancel() clears the acting topic and dialog', () => {
    const { result } = renderHook(() => useTopicSelection({ eventCode: 'BAT54' }));
    act(() => result.current.requestSelect(topic({ stalenessScore: 10 })));
    expect(result.current.mode).toBe('override');
    act(() => result.current.cancel());
    expect(result.current.mode).toBe('idle');
    expect(result.current.actingTopic).toBeNull();
  });
});
