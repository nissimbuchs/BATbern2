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
      registrationDeadline: z.string().optional().or(z.literal('')),
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
      ]),
      eventType: z.enum(['FULL_DAY', 'AFTERNOON', 'EVENING']).optional(),
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
  registrationDeadline?: string;
  workflowState?: components['schemas']['EventWorkflowState'];
  eventType?: components['schemas']['EventType'];
}

/** Partial form data (all fields optional) — used for PATCH. */
export type PartialEventFormData = {
  [K in keyof EventFormData]?: EventFormData[K];
};

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
