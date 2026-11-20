/**
 * Shared event-identity form schema + helpers (Epic 14, Story 14.F.2).
 *
 * Extracted verbatim from EventForm so the modal AND the in-tab Details · Info
 * editor validate identically with zero drift. Pure module — no React, no
 * component state.
 */

import { z } from 'zod';
import type { PatchEventRequest } from '@/types/event.types';
import type { components } from '@/types/generated/events-core-api.types';

/**
 * Converts legacy lowercase snake_case event types to UPPER_CASE enum values
 * (migration from old `full_day` to new `FULL_DAY`).
 */
export function normalizeEventType(
  eventType: string | undefined
): components['schemas']['EventType'] {
  if (!eventType) return 'FULL_DAY';
  const typeMap: Record<string, components['schemas']['EventType']> = {
    full_day: 'FULL_DAY',
    afternoon: 'AFTERNOON',
    evening: 'EVENING',
    FULL_DAY: 'FULL_DAY',
    AFTERNOON: 'AFTERNOON',
    EVENING: 'EVENING',
  };
  return typeMap[eventType] || 'FULL_DAY';
}

/**
 * The fields of CreateEventRequest the server requires (events-core-api); organizerUsername
 * comes from the signed-in user, not from the form. A draft is checked for these.
 */
export const SERVER_REQUIRED_FIELDS = [
  'title',
  'date',
  'eventType',
  'venueName',
  'venueAddress',
  'venueCapacity',
  'registrationDeadline',
] as const;

/** Validation schema factory (needs `t` for translated messages). */
export const createEventSchema = (t: (key: string) => string) =>
  z
    .object({
      eventNumber: z.coerce
        .number({ message: t('validation.eventNumberRequired') })
        .positive(t('validation.eventNumberPositive')),
      title: z
        .string()
        .min(1, t('validation.titleRequired'))
        .min(10, t('validation.titleMinLength')),
      description: z.string().min(1, t('validation.descriptionRequired')),
      date: z.string().min(1, t('validation.eventDateRequired')),
      registrationDeadline: z.string().min(1, t('validation.registrationDeadlineRequired')),
      venueName: z.string().min(1, t('validation.venueNameRequired')),
      venueAddress: z.string().min(1, t('validation.venueAddressRequired')),
      venueCapacity: z.coerce
        .number({ message: t('validation.capacityRequired') })
        .positive(t('validation.capacityPositive')),
      workflowState: z.enum([
        'CREATED',
        'TOPIC_SELECTION',
        'SPEAKER_IDENTIFICATION',
        'SLOT_ASSIGNMENT',
        'AGENDA_PUBLISHED',
        'EVENT_LIVE',
        'EVENT_COMPLETED',
        'ARCHIVED',
        'CANCELLED',
      ]),
      eventType: z.enum(['FULL_DAY', 'AFTERNOON', 'EVENING'], {
        message: t('validation.eventTypeRequired'),
      }),
    })
    .refine(
      (data) => {
        if (!data.registrationDeadline) return true;
        const eventDate = new Date(data.date);
        const deadline = new Date(data.registrationDeadline);
        return deadline <= eventDate;
      },
      {
        message: t('validation.registrationDeadline'),
        path: ['registrationDeadline'],
      }
    );

/** Explicit form-data type (z.infer doesn't handle the transforms cleanly). */
export interface EventFormData {
  eventNumber: number;
  title: string;
  description: string;
  date: string;
  venueName: string;
  venueAddress: string;
  venueCapacity: number;
  registrationDeadline: string;
  workflowState?: components['schemas']['EventWorkflowState'];
  eventType: components['schemas']['EventType'];
}

/** Partial form data (all fields optional) — used for PATCH. */
export type PartialEventFormData = {
  [K in keyof EventFormData]?: EventFormData[K];
};

/**
 * The form's raw values run through each field's schema, the conversion the zodResolver applies
 * on save: a number input yields "57", the schema's `z.coerce.number` makes it 57. A field that
 * does not pass its schema is left out, so auto-save never sends a value the save would refuse.
 * Auto-save used the raw snapshot and sent `eventNumber: "57"` (#1223 sweep).
 */
export function coerceFormFields(
  schema: ReturnType<typeof createEventSchema>,
  data: Record<string, unknown>
): PartialEventFormData {
  const shape = schema.shape as Record<string, z.ZodType>;
  const coerced: Record<string, unknown> = {};
  Object.entries(data).forEach(([key, value]) => {
    const field = shape[key];
    if (!field) return;
    const parsed = field.safeParse(value);
    if (parsed.success) coerced[key] = parsed.data;
  });
  return coerced as PartialEventFormData;
}

/** Return only the fields that differ from the initial snapshot (for PATCH). */
export function getChangedFields(
  currentData: PartialEventFormData,
  initialFormData: PartialEventFormData
): PartialEventFormData {
  const changedFields: Record<string, unknown> = {};
  (Object.keys(currentData) as Array<keyof EventFormData>).forEach((key) => {
    if (currentData[key] !== initialFormData[key]) {
      changedFields[key] = currentData[key];
    }
  });
  return changedFields as PartialEventFormData;
}

/**
 * Transform date-only fields (YYYY-MM-DD) to ISO 8601 (date-time) for PATCH,
 * per the OpenAPI spec.
 */
export function transformDatesForApi(data: PartialEventFormData): PatchEventRequest {
  const transformed = { ...data } as Record<string, unknown>;
  if (transformed.date && typeof transformed.date === 'string') {
    transformed.date = new Date(transformed.date).toISOString();
  }
  if (transformed.registrationDeadline && typeof transformed.registrationDeadline === 'string') {
    transformed.registrationDeadline = new Date(transformed.registrationDeadline).toISOString();
  }
  return transformed as PatchEventRequest;
}
