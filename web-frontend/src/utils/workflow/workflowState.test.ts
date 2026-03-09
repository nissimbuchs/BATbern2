/**
 * Tests for Workflow State Utilities
 *
 * Comprehensive test coverage for all workflow state helper functions.
 * Ensures correct behavior for progress calculation, color selection,
 * translation key generation, and state validation.
 */

import { describe, it, expect, vi } from 'vitest';
import type { TFunction } from 'i18next';
import {
  WORKFLOW_STATE_ORDER,
  getWorkflowProgress,
  getProgressColor,
  getWorkflowStateI18nKey,
  getWorkflowStateLabel,
  getWorkflowStepNumber,
  isEarlyStage,
  isLateStage,
  isValidWorkflowState,
} from './workflowState';

describe('workflowState utilities', () => {
  describe('WORKFLOW_STATE_ORDER', () => {
    it('should contain exactly 8 workflow states', () => {
      expect(WORKFLOW_STATE_ORDER).toHaveLength(8);
    });

    it('should start with CREATED', () => {
      expect(WORKFLOW_STATE_ORDER[0]).toBe('CREATED');
    });

    it('should end with ARCHIVED', () => {
      expect(WORKFLOW_STATE_ORDER[7]).toBe('ARCHIVED');
    });

    it('should contain all expected workflow states in order', () => {
      const expected = [
        'CREATED',
        'TOPIC_SELECTION',
        'SPEAKER_IDENTIFICATION',
        'SLOT_ASSIGNMENT',
        'AGENDA_PUBLISHED',
        'EVENT_LIVE',
        'EVENT_COMPLETED',
        'ARCHIVED',
      ];
      expect(WORKFLOW_STATE_ORDER).toEqual(expected);
    });
  });

  describe('getWorkflowProgress', () => {
    it('should return 13% for CREATED (step 1/8)', () => {
      expect(getWorkflowProgress('CREATED')).toBe(13);
    });

    it('should return 25% for TOPIC_SELECTION (step 2/8)', () => {
      expect(getWorkflowProgress('TOPIC_SELECTION')).toBe(25);
    });

    it('should return 38% for SPEAKER_IDENTIFICATION (step 3/8)', () => {
      expect(getWorkflowProgress('SPEAKER_IDENTIFICATION')).toBe(38);
    });

    it('should return 50% for SLOT_ASSIGNMENT (step 4/8)', () => {
      expect(getWorkflowProgress('SLOT_ASSIGNMENT')).toBe(50);
    });

    it('should return 75% for EVENT_LIVE (step 6/8)', () => {
      expect(getWorkflowProgress('EVENT_LIVE')).toBe(75);
    });

    it('should return 100% for ARCHIVED (step 8/8)', () => {
      expect(getWorkflowProgress('ARCHIVED')).toBe(100);
    });

    it('should return 0 for invalid workflow state', () => {
      expect(getWorkflowProgress('INVALID_STATE')).toBe(0);
    });

    it('should return 0 for empty string', () => {
      expect(getWorkflowProgress('')).toBe(0);
    });

    it('should return 0 for undefined (type coercion)', () => {
      expect(getWorkflowProgress(undefined as unknown as string)).toBe(0);
    });
  });

  describe('getProgressColor', () => {
    it('should return "warning" for 0% progress', () => {
      expect(getProgressColor(0)).toBe('warning');
    });

    it('should return "warning" for 29% progress (< 30%)', () => {
      expect(getProgressColor(29)).toBe('warning');
    });

    it('should return "primary" for 30% progress', () => {
      expect(getProgressColor(30)).toBe('primary');
    });

    it('should return "primary" for 50% progress', () => {
      expect(getProgressColor(50)).toBe('primary');
    });

    it('should return "primary" for 69% progress (< 70%)', () => {
      expect(getProgressColor(69)).toBe('primary');
    });

    it('should return "success" for 70% progress', () => {
      expect(getProgressColor(70)).toBe('success');
    });

    it('should return "success" for 85% progress', () => {
      expect(getProgressColor(85)).toBe('success');
    });

    it('should return "success" for 100% progress', () => {
      expect(getProgressColor(100)).toBe('success');
    });

    it('should handle edge case of exactly 30%', () => {
      expect(getProgressColor(30)).toBe('primary');
    });

    it('should handle edge case of exactly 70%', () => {
      expect(getProgressColor(70)).toBe('success');
    });
  });

  describe('getWorkflowStateI18nKey', () => {
    it('should convert CREATED to workflow.states.created', () => {
      expect(getWorkflowStateI18nKey('CREATED')).toBe('workflow.states.created');
    });

    it('should convert TOPIC_SELECTION to workflow.states.topic_selection', () => {
      expect(getWorkflowStateI18nKey('TOPIC_SELECTION')).toBe('workflow.states.topic_selection');
    });

    it('should convert SPEAKER_IDENTIFICATION to workflow.states.speaker_identification', () => {
      expect(getWorkflowStateI18nKey('SPEAKER_IDENTIFICATION')).toBe(
        'workflow.states.speaker_identification'
      );
    });

    it('should convert EVENT_LIVE to workflow.states.event_live', () => {
      expect(getWorkflowStateI18nKey('EVENT_LIVE')).toBe('workflow.states.event_live');
    });

    it('should convert ARCHIVED to workflow.states.archived', () => {
      expect(getWorkflowStateI18nKey('ARCHIVED')).toBe('workflow.states.archived');
    });

    it('should handle lowercase input by converting to lowercase', () => {
      expect(getWorkflowStateI18nKey('created')).toBe('workflow.states.created');
    });
  });

  describe('getWorkflowStateLabel', () => {
    it('should return translated label for valid state', () => {
      const mockT = vi.fn((key: string, fallback: string) => {
        if (key === 'workflow.states.created') return 'Created';
        return fallback;
      }) as unknown as TFunction;

      const label = getWorkflowStateLabel('CREATED', mockT);
      expect(label).toBe('Created');
      expect(mockT).toHaveBeenCalledWith('workflow.states.created', 'CREATED');
    });

    it('should return translated label in German locale', () => {
      const mockT = vi.fn((key: string, fallback: string) => {
        if (key === 'workflow.states.speaker_identification') return 'Referenten-Identifikation';
        return fallback;
      }) as unknown as TFunction;

      const label = getWorkflowStateLabel('SPEAKER_IDENTIFICATION', mockT);
      expect(label).toBe('Referenten-Identifikation');
      expect(mockT).toHaveBeenCalledWith(
        'workflow.states.speaker_identification',
        'SPEAKER_IDENTIFICATION'
      );
    });

    it('should use fallback for missing translation', () => {
      const mockT = vi.fn((_key: string, fallback: string) => fallback) as unknown as TFunction;

      const label = getWorkflowStateLabel('CREATED', mockT);
      expect(label).toBe('CREATED');
    });

    it('should handle all 8 workflow states', () => {
      const mockT = vi.fn((_key: string, fallback: string) => fallback) as unknown as TFunction;

      WORKFLOW_STATE_ORDER.forEach((state) => {
        const label = getWorkflowStateLabel(state, mockT);
        expect(label).toBe(state);
      });

      expect(mockT).toHaveBeenCalledTimes(8);
    });
  });

  describe('getWorkflowStepNumber', () => {
    it('should return 1 for CREATED', () => {
      expect(getWorkflowStepNumber('CREATED')).toBe(1);
    });

    it('should return 2 for TOPIC_SELECTION', () => {
      expect(getWorkflowStepNumber('TOPIC_SELECTION')).toBe(2);
    });

    it('should return 4 for SLOT_ASSIGNMENT', () => {
      expect(getWorkflowStepNumber('SLOT_ASSIGNMENT')).toBe(4);
    });

    it('should return 8 for ARCHIVED', () => {
      expect(getWorkflowStepNumber('ARCHIVED')).toBe(8);
    });

    it('should return 0 for invalid state', () => {
      expect(getWorkflowStepNumber('INVALID_STATE')).toBe(0);
    });

    it('should return 0 for empty string', () => {
      expect(getWorkflowStepNumber('')).toBe(0);
    });
  });

  describe('isEarlyStage', () => {
    it('should return true for CREATED', () => {
      expect(isEarlyStage('CREATED')).toBe(true);
    });

    it('should return true for TOPIC_SELECTION', () => {
      expect(isEarlyStage('TOPIC_SELECTION')).toBe(true);
    });

    it('should return false for SPEAKER_BRAINSTORMING', () => {
      expect(isEarlyStage('SPEAKER_BRAINSTORMING')).toBe(false);
    });

    it('should return false for SPEAKER_OUTREACH', () => {
      expect(isEarlyStage('SPEAKER_OUTREACH')).toBe(false);
    });

    it('should return false for ARCHIVED', () => {
      expect(isEarlyStage('ARCHIVED')).toBe(false);
    });

    it('should return false for invalid state', () => {
      expect(isEarlyStage('INVALID_STATE')).toBe(false);
    });

    it('should return false for empty string', () => {
      expect(isEarlyStage('')).toBe(false);
    });
  });

  describe('isLateStage', () => {
    it('should return true for EVENT_LIVE', () => {
      expect(isLateStage('EVENT_LIVE')).toBe(true);
    });

    it('should return true for EVENT_COMPLETED', () => {
      expect(isLateStage('EVENT_COMPLETED')).toBe(true);
    });

    it('should return true for ARCHIVED', () => {
      expect(isLateStage('ARCHIVED')).toBe(true);
    });

    it('should return false for CREATED', () => {
      expect(isLateStage('CREATED')).toBe(false);
    });

    it('should return false for AGENDA_PUBLISHED', () => {
      expect(isLateStage('AGENDA_PUBLISHED')).toBe(false);
    });

    it('should return false for SPEAKER_IDENTIFICATION', () => {
      expect(isLateStage('SPEAKER_IDENTIFICATION')).toBe(false);
    });

    it('should return false for invalid state', () => {
      expect(isLateStage('INVALID_STATE')).toBe(false);
    });

    it('should return false for empty string', () => {
      expect(isLateStage('')).toBe(false);
    });
  });

  describe('isValidWorkflowState', () => {
    it('should return true for all 8 valid workflow states', () => {
      WORKFLOW_STATE_ORDER.forEach((state) => {
        expect(isValidWorkflowState(state)).toBe(true);
      });
    });

    it('should return false for invalid state', () => {
      expect(isValidWorkflowState('INVALID_STATE')).toBe(false);
    });

    it('should return false for empty string', () => {
      expect(isValidWorkflowState('')).toBe(false);
    });

    it('should return false for lowercase valid state (case-sensitive)', () => {
      expect(isValidWorkflowState('created')).toBe(false);
    });

    it('should return false for partial match', () => {
      expect(isValidWorkflowState('SPEAKER')).toBe(false);
    });

    it('should return false for undefined (type coercion)', () => {
      expect(isValidWorkflowState(undefined as unknown as string)).toBe(false);
    });

    it('should return false for null (type coercion)', () => {
      expect(isValidWorkflowState(null as unknown as string)).toBe(false);
    });
  });

  describe('Integration scenarios', () => {
    it('should correctly calculate progress and color for early stage event', () => {
      const state = 'TOPIC_SELECTION';
      const progress = getWorkflowProgress(state);
      const color = getProgressColor(progress);

      expect(progress).toBe(25); // Step 2/8
      expect(color).toBe('warning'); // < 30%
      expect(isEarlyStage(state)).toBe(true);
      expect(isLateStage(state)).toBe(false);
    });

    it('should correctly calculate progress and color for mid-stage event', () => {
      const state = 'SLOT_ASSIGNMENT';
      const progress = getWorkflowProgress(state);
      const color = getProgressColor(progress);

      expect(progress).toBe(50); // Step 4/8
      expect(color).toBe('primary'); // 30-70%
      expect(isEarlyStage(state)).toBe(false);
      expect(isLateStage(state)).toBe(false);
    });

    it('should correctly calculate progress and color for late stage event', () => {
      const state = 'EVENT_LIVE';
      const progress = getWorkflowProgress(state);
      const color = getProgressColor(progress);

      expect(progress).toBe(75); // Step 6/8
      expect(color).toBe('success'); // > 70%
      expect(isEarlyStage(state)).toBe(false);
      expect(isLateStage(state)).toBe(true);
    });

    it('should correctly handle completed event (ARCHIVED)', () => {
      const state = 'ARCHIVED';
      const progress = getWorkflowProgress(state);
      const color = getProgressColor(progress);

      expect(progress).toBe(100); // Step 8/8
      expect(color).toBe('success');
      expect(isEarlyStage(state)).toBe(false);
      expect(isLateStage(state)).toBe(true);
    });

    it('should generate correct i18n key and step number', () => {
      const state = 'SPEAKER_IDENTIFICATION';
      const i18nKey = getWorkflowStateI18nKey(state);
      const stepNumber = getWorkflowStepNumber(state);

      expect(i18nKey).toBe('workflow.states.speaker_identification');
      expect(stepNumber).toBe(3); // Step 3/8
    });
  });
});
