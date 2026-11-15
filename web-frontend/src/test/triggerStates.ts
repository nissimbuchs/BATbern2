/**
 * Test helper: the trigger states the backend accepts, read from the spec. The backend builds its
 * set from the shared-kernel `EventWorkflowState` (lower case) plus `speakers_published`; the
 * spec's `EventWorkflowState` enum describes the same states, and the response conformance gate
 * fails a backend response with a state the spec lacks.
 */
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { parse } from 'yaml';
import { SPEC_DIR } from '@/test/msw/server';

export const backendTriggerStates = (): string[] => {
  const spec = parse(readFileSync(join(SPEC_DIR, 'events-core-api.openapi.yml'), 'utf8'));
  const states: string[] = spec.components.schemas.EventWorkflowState.enum;
  return [...states.map((s) => s.toLowerCase()), 'speakers_published'];
};
