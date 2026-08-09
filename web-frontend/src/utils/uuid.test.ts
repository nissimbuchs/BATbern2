// @vitest-environment jsdom
/**
 * safeRandomUUID tests
 *
 * The bug being pinned: crypto.randomUUID() exists ONLY in a secure context (HTTPS or
 * localhost). A dev server browsed over plain http:// by LAN IP has no randomUUID, and
 * the TypeError aborted every outgoing request from the apiClient interceptor.
 */

import { describe, it, expect, vi, afterEach } from 'vitest';
import { safeRandomUUID } from './uuid';

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

describe('safeRandomUUID', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should_useNativeRandomUUID_when_available', () => {
    const spy = vi
      .spyOn(crypto, 'randomUUID')
      .mockReturnValue('11111111-2222-4333-8444-555555555555');

    expect(safeRandomUUID()).toBe('11111111-2222-4333-8444-555555555555');
    expect(spy).toHaveBeenCalled();
  });

  it('should_stillReturnValidUuid_when_randomUUIDIsMissing', () => {
    // Exactly what an insecure-context browser looks like.
    vi.spyOn(crypto, 'randomUUID').mockImplementation(() => {
      throw new TypeError('crypto.randomUUID is not a function');
    });
    // @ts-expect-error — deleting the method is the point of the test
    crypto.randomUUID = undefined;

    const id = safeRandomUUID();

    expect(id).toMatch(UUID_V4);
  });

  it('should_notThrow_when_randomUUIDIsMissing', () => {
    // @ts-expect-error — simulating an insecure context
    crypto.randomUUID = undefined;

    expect(() => safeRandomUUID()).not.toThrow();
  });

  it('should_produceDistinctValues_when_calledRepeatedly', () => {
    // @ts-expect-error — simulating an insecure context
    crypto.randomUUID = undefined;

    const ids = new Set(Array.from({ length: 200 }, () => safeRandomUUID()));

    expect(ids.size).toBe(200);
  });
});
