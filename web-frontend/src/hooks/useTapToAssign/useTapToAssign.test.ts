import { describe, it, expect, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useTapToAssign } from './useTapToAssign';

describe('useTapToAssign', () => {
  const setup = () => {
    const commit = vi.fn().mockResolvedValue(undefined);
    const hook = renderHook(() => useTapToAssign({ commit }));
    return { commit, hook };
  };

  it('arms a pool session and ASSIGNs it onto an empty slot', async () => {
    const { commit, hook } = setup();

    act(() => hook.result.current.armFromPool('s1'));
    expect(hook.result.current.armedSessionSlug).toBe('s1');
    expect(hook.result.current.hasArmed).toBe(true);

    await act(async () => {
      await hook.result.current.tapSlot('SPEAKER_SLOT-2', null);
    });

    expect(commit).toHaveBeenCalledWith('s1', 'SPEAKER_SLOT-2', 'ASSIGN');
    expect(hook.result.current.hasArmed).toBe(false); // cleared after commit
  });

  it('INSERTs when a pool session is tapped onto an occupied slot', async () => {
    const { commit, hook } = setup();

    act(() => hook.result.current.armFromPool('s1'));
    await act(async () => {
      await hook.result.current.tapSlot('SPEAKER_SLOT-1', 'occupant');
    });

    expect(commit).toHaveBeenCalledWith('s1', 'SPEAKER_SLOT-1', 'INSERT');
  });

  it('SWAPs when an armed slot session is tapped onto another occupied slot', async () => {
    const { commit, hook } = setup();

    act(() => hook.result.current.armFromSlot('a', 'SPEAKER_SLOT-1'));
    expect(hook.result.current.isArmed('a')).toBe(true);

    await act(async () => {
      await hook.result.current.tapSlot('SPEAKER_SLOT-2', 'b');
    });

    expect(commit).toHaveBeenCalledWith('a', 'SPEAKER_SLOT-2', 'SWAP');
  });

  it('ASSIGNs (move) when an armed slot session is tapped onto an empty slot', async () => {
    const { commit, hook } = setup();

    act(() => hook.result.current.armFromSlot('a', 'SPEAKER_SLOT-1'));
    await act(async () => {
      await hook.result.current.tapSlot('SPEAKER_SLOT-3', null);
    });

    expect(commit).toHaveBeenCalledWith('a', 'SPEAKER_SLOT-3', 'ASSIGN');
  });

  it('disarms (no commit) when the armed pool session is tapped again', () => {
    const { commit, hook } = setup();

    act(() => hook.result.current.armFromPool('s1'));
    act(() => hook.result.current.armFromPool('s1'));

    expect(hook.result.current.hasArmed).toBe(false);
    expect(commit).not.toHaveBeenCalled();
  });

  it('cancels the pick-up when the armed slot is tapped as a target', async () => {
    const { commit, hook } = setup();

    act(() => hook.result.current.armFromSlot('a', 'SPEAKER_SLOT-1'));
    await act(async () => {
      await hook.result.current.tapSlot('SPEAKER_SLOT-1', 'a');
    });

    expect(commit).not.toHaveBeenCalled();
    expect(hook.result.current.hasArmed).toBe(false);
  });

  it('does nothing when tapping a slot with nothing armed', async () => {
    const { commit, hook } = setup();

    await act(async () => {
      await hook.result.current.tapSlot('SPEAKER_SLOT-1', null);
    });

    expect(commit).not.toHaveBeenCalled();
  });
});
