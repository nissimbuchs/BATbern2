/**
 * Partner Schema (Story 2.8.3)
 *
 * Task 4b: GREEN Phase Implementation
 * Task 12: Internationalization Support
 * AC7: Form Validation
 * AC12: i18n support
 *
 * Zod validation schemas for partner create/edit forms:
 * - CreatePartnerSchema: Validates create partnership form
 * - UpdatePartnerSchema: Validates update partnership form (partial updates)
 *
 * Validation rules:
 * - Company name: Required, max 12 characters (meaningful ID per ADR-003)
 * - Partnership level: Required, must be valid enum value
 * - Start date: Required
 * - End date: Optional, must be after start date
 */

import { z } from 'zod';
import type { TFunction } from 'i18next';

/**
 * Partnership level enum matching backend API
 */
const createPartnershipLevelEnum = (t: TFunction) =>
  z.enum(['bronze', 'silver', 'gold', 'platinum', 'strategic'], {
    errorMap: () => ({ message: t('partners:validation.tierRequired') }),
  });

/**
 * Create Partner Schema Factory
 *
 * Used for validating create partnership form data
 * @param t - i18n translate function
 */
export const createPartnerSchema = (t: TFunction) =>
  z
    .object({
      companyName: z
        .string()
        .min(1, t('partners:validation.companyRequired'))
        .max(12, 'Company name must be at most 12 characters'),
      partnershipLevel: createPartnershipLevelEnum(t),
      partnershipStartDate: z.date({ required_error: t('partners:validation.startDateRequired') }),
      partnershipEndDate: z.date().optional(),
    })
    .refine(
      (data) => !data.partnershipEndDate || data.partnershipEndDate > data.partnershipStartDate,
      {
        message: t('partners:validation.endDateBeforeStart'),
        path: ['partnershipEndDate'],
      }
    );

/**
 * Update Partner Schema Factory
 *
 * Used for validating update partnership form data
 * All fields are optional for partial updates
 * @param t - i18n translate function
 */
export const updatePartnerSchema = (t: TFunction) =>
  z
    .object({
      partnershipLevel: createPartnershipLevelEnum(t).optional(),
      partnershipStartDate: z.date().optional(),
      partnershipEndDate: z.date().optional(),
    })
    .refine(
      (data) => {
        // Only validate date range if both dates are provided
        if (data.partnershipStartDate && data.partnershipEndDate) {
          return data.partnershipEndDate > data.partnershipStartDate;
        }
        return true;
      },
      {
        message: t('partners:validation.endDateBeforeStart'),
        path: ['partnershipEndDate'],
      }
    );

// Legacy exports for backward compatibility (will use default English messages)
const PartnershipLevelEnum = z.enum(['bronze', 'silver', 'gold', 'platinum', 'strategic'], {
  errorMap: () => ({ message: 'Partnership tier is required' }),
});

export const CreatePartnerSchema = z
  .object({
    companyName: z
      .string()
      .min(1, 'Company is required')
      .max(12, 'Company name must be at most 12 characters'),
    partnershipLevel: PartnershipLevelEnum,
    partnershipStartDate: z.date({ required_error: 'Start date is required' }),
    partnershipEndDate: z.date().optional(),
  })
  .refine(
    (data) => !data.partnershipEndDate || data.partnershipEndDate > data.partnershipStartDate,
    {
      message: 'End date must be after start date',
      path: ['partnershipEndDate'],
    }
  );

export const UpdatePartnerSchema = z
  .object({
    partnershipLevel: PartnershipLevelEnum.optional(),
    partnershipStartDate: z.date().optional(),
    partnershipEndDate: z.date().optional(),
  })
  .refine(
    (data) => {
      // Only validate date range if both dates are provided
      if (data.partnershipStartDate && data.partnershipEndDate) {
        return data.partnershipEndDate > data.partnershipStartDate;
      }
      return true;
    },
    {
      message: 'End date must be after start date',
      path: ['partnershipEndDate'],
    }
  );

/**
 * Infer TypeScript types from schemas
 */
export type CreatePartnerFormData = z.infer<typeof CreatePartnerSchema>;
export type UpdatePartnerFormData = z.infer<typeof UpdatePartnerSchema>;
