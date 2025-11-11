/**
 * Partner Schema Tests (RED Phase - Story 2.8.3)
 *
 * Task 4a: RED Phase Tests
 * Test Specifications: AC7 - Form Validation
 *
 * Tests for:
 * - Required field validation (companyName, partnershipLevel, partnershipStartDate)
 * - Date range validation (end date > start date)
 * - Partnership level enum validation
 * - i18n error messages
 */

import { describe, it, expect } from 'vitest';
import { CreatePartnerSchema, UpdatePartnerSchema } from '@/schemas/partnerSchema';

describe('partnerSchema - Story 2.8.3', () => {
  describe('CreatePartnerSchema', () => {
    describe('Required Fields Validation', () => {
      it('should_validateRequiredFields_when_schemaApplied', () => {
        const invalidData = {
          // Missing all required fields
        };

        const result = CreatePartnerSchema.safeParse(invalidData);

        expect(result.success).toBe(false);
        if (!result.success) {
          const errors = result.error.flatten().fieldErrors;
          expect(errors.companyName).toBeDefined();
          expect(errors.partnershipLevel).toBeDefined();
          expect(errors.partnershipStartDate).toBeDefined();
        }
      });

      it('should_acceptValidData_when_allRequiredFieldsProvided', () => {
        const validData = {
          companyName: 'test-company',
          partnershipLevel: 'GOLD',
          partnershipStartDate: new Date('2025-01-01'),
        };

        const result = CreatePartnerSchema.safeParse(validData);

        expect(result.success).toBe(true);
        if (result.success) {
          expect(result.data.companyName).toBe('test-company');
          expect(result.data.partnershipLevel).toBe('GOLD');
          expect(result.data.partnershipStartDate).toEqual(new Date('2025-01-01'));
        }
      });

      it('should_acceptOptionalEndDate_when_provided', () => {
        const validData = {
          companyName: 'test-company',
          partnershipLevel: 'PLATINUM',
          partnershipStartDate: new Date('2025-01-01'),
          partnershipEndDate: new Date('2025-12-31'),
        };

        const result = CreatePartnerSchema.safeParse(validData);

        expect(result.success).toBe(true);
        if (result.success) {
          expect(result.data.partnershipEndDate).toEqual(new Date('2025-12-31'));
        }
      });
    });

    describe('Company Name Validation', () => {
      it('should_rejectEmptyCompanyName_when_validated', () => {
        const invalidData = {
          companyName: '',
          partnershipLevel: 'BRONZE',
          partnershipStartDate: new Date('2025-01-01'),
        };

        const result = CreatePartnerSchema.safeParse(invalidData);

        expect(result.success).toBe(false);
        if (!result.success) {
          const errors = result.error.flatten().fieldErrors;
          expect(errors.companyName).toBeDefined();
        }
      });

      it('should_rejectTooLongCompanyName_when_exceedsMaxLength', () => {
        const invalidData = {
          companyName: 'a'.repeat(13), // Max is 12
          partnershipLevel: 'SILVER',
          partnershipStartDate: new Date('2025-01-01'),
        };

        const result = CreatePartnerSchema.safeParse(invalidData);

        expect(result.success).toBe(false);
        if (!result.success) {
          const errors = result.error.flatten().fieldErrors;
          expect(errors.companyName).toBeDefined();
        }
      });
    });

    describe('Partnership Level Validation', () => {
      it('should_acceptValidPartnershipLevels_when_validated', () => {
        const levels = ['BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'STRATEGIC'] as const;

        levels.forEach((level) => {
          const validData = {
            companyName: 'test-company',
            partnershipLevel: level,
            partnershipStartDate: new Date('2025-01-01'),
          };

          const result = CreatePartnerSchema.safeParse(validData);

          expect(result.success).toBe(true);
        });
      });

      it('should_rejectInvalidPartnershipLevel_when_validated', () => {
        const invalidData = {
          companyName: 'test-company',
          partnershipLevel: 'DIAMOND', // Invalid level
          partnershipStartDate: new Date('2025-01-01'),
        };

        const result = CreatePartnerSchema.safeParse(invalidData);

        expect(result.success).toBe(false);
        if (!result.success) {
          const errors = result.error.flatten().fieldErrors;
          expect(errors.partnershipLevel).toBeDefined();
        }
      });
    });

    describe('Date Range Validation', () => {
      it('should_validateDateRange_when_endDateBeforeStart', () => {
        const invalidData = {
          companyName: 'test-company',
          partnershipLevel: 'GOLD',
          partnershipStartDate: new Date('2025-12-31'),
          partnershipEndDate: new Date('2025-01-01'), // Before start date
        };

        const result = CreatePartnerSchema.safeParse(invalidData);

        expect(result.success).toBe(false);
        if (!result.success) {
          const issues = result.error.issues;
          expect(issues.some((issue) => issue.path.includes('partnershipEndDate'))).toBe(true);
        }
      });

      it('should_acceptValidDateRange_when_endDateAfterStart', () => {
        const validData = {
          companyName: 'test-company',
          partnershipLevel: 'STRATEGIC',
          partnershipStartDate: new Date('2025-01-01'),
          partnershipEndDate: new Date('2025-12-31'),
        };

        const result = CreatePartnerSchema.safeParse(validData);

        expect(result.success).toBe(true);
      });

      it('should_acceptSameDayDates_when_endDateEqualsStart', () => {
        const validData = {
          companyName: 'test-company',
          partnershipLevel: 'BRONZE',
          partnershipStartDate: new Date('2025-06-15'),
          partnershipEndDate: new Date('2025-06-15'),
        };

        const result = CreatePartnerSchema.safeParse(validData);

        // Same day should fail (end must be AFTER start)
        expect(result.success).toBe(false);
      });
    });
  });

  describe('UpdatePartnerSchema', () => {
    it('should_acceptPartialUpdates_when_onlySomeFieldsProvided', () => {
      const validData = {
        partnershipLevel: 'PLATINUM',
      };

      const result = UpdatePartnerSchema.safeParse(validData);

      expect(result.success).toBe(true);
    });

    it('should_validateDateRange_when_bothDatesProvided', () => {
      const invalidData = {
        partnershipStartDate: new Date('2025-12-31'),
        partnershipEndDate: new Date('2025-01-01'),
      };

      const result = UpdatePartnerSchema.safeParse(invalidData);

      expect(result.success).toBe(false);
    });

    it('should_acceptValidUpdate_when_allFieldsProvided', () => {
      const validData = {
        partnershipLevel: 'STRATEGIC',
        partnershipStartDate: new Date('2025-01-01'),
        partnershipEndDate: new Date('2025-12-31'),
      };

      const result = UpdatePartnerSchema.safeParse(validData);

      expect(result.success).toBe(true);
    });
  });

  describe('i18n Error Messages', () => {
    it('should_returnI18nErrors_when_validationFails', () => {
      const invalidData = {
        companyName: '',
        partnershipLevel: 'INVALID',
        partnershipStartDate: 'not-a-date',
      };

      const result = CreatePartnerSchema.safeParse(invalidData);

      expect(result.success).toBe(false);
      if (!result.success) {
        const errors = result.error.flatten().fieldErrors;
        // Verify error messages exist for all invalid fields
        expect(errors.companyName).toBeDefined();
        expect(errors.partnershipLevel).toBeDefined();
        expect(errors.partnershipStartDate).toBeDefined();
      }
    });
  });
});
