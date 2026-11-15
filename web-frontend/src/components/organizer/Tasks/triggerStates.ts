/**
 * The task trigger states the backend accepts (`TaskTemplateService.VALID_TRIGGER_STATES`): every
 * event workflow state in lower case, plus `speakers_published`, the one trigger that is not a
 * state. Both task dialogs offer exactly these (#1218); labels are `events:tasks.workflowStates.*`.
 */
import type { components } from '@/types/generated/events-core-api.types';

type EventWorkflowState = components['schemas']['EventWorkflowState'];

// A Record over the generated union: a state added to the spec fails the type check here.
const WORKFLOW_STATES: Record<EventWorkflowState, true> = {
  CREATED: true,
  TOPIC_SELECTION: true,
  SPEAKER_IDENTIFICATION: true,
  SLOT_ASSIGNMENT: true,
  AGENDA_PUBLISHED: true,
  EVENT_LIVE: true,
  EVENT_COMPLETED: true,
  ARCHIVED: true,
};

export const TRIGGER_SPEAKERS_PUBLISHED = 'speakers_published';

export const TRIGGER_STATES: readonly string[] = [
  ...Object.keys(WORKFLOW_STATES).map((state) => state.toLowerCase()),
  TRIGGER_SPEAKERS_PUBLISHED,
];

export const isTriggerState = (value: string | null | undefined): boolean =>
  !!value && TRIGGER_STATES.includes(value);
