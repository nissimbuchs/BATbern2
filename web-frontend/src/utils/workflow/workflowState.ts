/**
 * Workflow State Utilities
 *
 * Centralized utilities for managing and displaying EventWorkflowState.
 * Single source of truth for workflow state logic across the application.
 *
 * Story 5.1a - Workflow State Machine Foundation
 * Refactoring: Eliminates code duplication across EventCard, WorkflowProgressBar,
 * and WorkflowProgressBarWithQuery components.
 */

import type { TFunction } from 'i18next';

/**
 * Complete 9-step workflow state order for BATbern events.
 * States progress linearly from CREATED through ARCHIVED.
 * Updated for Story 5.7 - Consolidated from 16 states to 9 states.
 */
export const WORKFLOW_STATE_ORDER = [
  'CREATED',
  'TOPIC_SELECTION',
  'SPEAKER_IDENTIFICATION',
  'SLOT_ASSIGNMENT',
  'AGENDA_PUBLISHED',
  'AGENDA_FINALIZED',
  'EVENT_LIVE',
  'EVENT_COMPLETED',
  'ARCHIVED',
] as const;

/**
 * Type representing valid workflow state values
 */
export type WorkflowStateType = (typeof WORKFLOW_STATE_ORDER)[number];

/**
 * Early stage workflow states where topic selection is relevant
 */
const EARLY_STAGE_STATES: readonly string[] = ['CREATED', 'TOPIC_SELECTION'] as const;

/**
 * Late stage workflow states where event is essentially complete
 */
const LATE_STAGE_STATES: readonly string[] = ['EVENT_LIVE', 'EVENT_COMPLETED', 'ARCHIVED'] as const;

/**
 * Calculate workflow completion percentage from current state.
 *
 * @param workflowState - The current workflow state (e.g., 'SPEAKER_IDENTIFICATION')
 * @returns Progress percentage (0-100), or 0 if state is invalid
 *
 * @example
 * getWorkflowProgress('CREATED') // Returns 11 (step 1/9)
 * getWorkflowProgress('ARCHIVED') // Returns 100 (step 9/9)
 * getWorkflowProgress('INVALID') // Returns 0
 */
export function getWorkflowProgress(workflowState: string): number {
  const currentIndex = WORKFLOW_STATE_ORDER.indexOf(workflowState as WorkflowStateType);
  if (currentIndex === -1) return 0;
  return Math.round(((currentIndex + 1) / WORKFLOW_STATE_ORDER.length) * 100);
}

/**
 * Get the appropriate color for the workflow progress bar based on completion percentage.
 *
 * Color coding follows Material-UI conventions:
 * - warning (yellow): < 30% - early stages, needs attention
 * - primary (blue): 30-70% - progressing normally
 * - success (green): > 70% - nearly complete or complete
 *
 * @param progress - Progress percentage (0-100)
 * @returns Material-UI color variant
 *
 * @example
 * getProgressColor(20) // Returns 'warning'
 * getProgressColor(50) // Returns 'primary'
 * getProgressColor(85) // Returns 'success'
 */
export function getProgressColor(progress: number): 'warning' | 'primary' | 'success' {
  if (progress < 30) return 'warning';
  if (progress < 70) return 'primary';
  return 'success';
}

/**
 * Generate the i18n translation key for a workflow state.
 *
 * Converts workflow state to lowercase_snake_case format expected by i18n.
 *
 * @param state - The workflow state (e.g., 'SPEAKER_OUTREACH')
 * @returns i18n key path (e.g., 'workflow.states.speaker_outreach')
 *
 * @example
 * getWorkflowStateI18nKey('CREATED') // Returns 'workflow.states.created'
 * getWorkflowStateI18nKey('SPEAKER_OUTREACH') // Returns 'workflow.states.speaker_outreach'
 */
export function getWorkflowStateI18nKey(state: string): string {
  return `workflow.states.${state.toLowerCase()}`;
}

/**
 * Get the translated human-readable label for a workflow state.
 *
 * Uses the i18n translation function to return localized state name.
 * Falls back to the raw state value if translation is missing.
 *
 * @param state - The workflow state
 * @param t - i18n translation function
 * @returns Translated state label (e.g., "Speaker Outreach", "Themenauswahl")
 *
 * @example
 * getWorkflowStateLabel('SPEAKER_OUTREACH', t) // Returns "Speaker Outreach" (EN) or "Referenten-Kontaktaufnahme" (DE)
 * getWorkflowStateLabel('CREATED', t) // Returns "Created" (EN) or "Erstellt" (DE)
 */
export function getWorkflowStateLabel(state: string, t: TFunction): string {
  return t(getWorkflowStateI18nKey(state), state);
}

/**
 * Get the step number for a workflow state (1-9).
 *
 * @param state - The workflow state
 * @returns Step number (1-indexed), or 0 if state is invalid
 *
 * @example
 * getWorkflowStepNumber('CREATED') // Returns 1
 * getWorkflowStepNumber('ARCHIVED') // Returns 9
 * getWorkflowStepNumber('INVALID') // Returns 0
 */
export function getWorkflowStepNumber(state: string): number {
  const index = WORKFLOW_STATE_ORDER.indexOf(state as WorkflowStateType);
  return index === -1 ? 0 : index + 1;
}

/**
 * Check if the workflow state is in an early stage (CREATED or TOPIC_SELECTION).
 *
 * Early stages are when topic selection functionality is relevant and should be displayed.
 *
 * @param state - The workflow state to check
 * @returns true if state is CREATED or TOPIC_SELECTION
 *
 * @example
 * isEarlyStage('CREATED') // Returns true
 * isEarlyStage('TOPIC_SELECTION') // Returns true
 * isEarlyStage('SPEAKER_IDENTIFICATION') // Returns false
 */
export function isEarlyStage(state: string): boolean {
  return EARLY_STAGE_STATES.includes(state);
}

/**
 * Check if the workflow state is in a late stage (EVENT_LIVE, EVENT_COMPLETED, or ARCHIVED).
 *
 * Late stages indicate the event workflow is essentially complete.
 *
 * @param state - The workflow state to check
 * @returns true if state is in late stage
 *
 * @example
 * isLateStage('ARCHIVED') // Returns true
 * isLateStage('EVENT_COMPLETED') // Returns true
 * isLateStage('CREATED') // Returns false
 */
export function isLateStage(state: string): boolean {
  return LATE_STAGE_STATES.includes(state);
}

/**
 * Validate if a string is a valid workflow state.
 *
 * @param state - The state string to validate
 * @returns true if state is one of the 9 valid workflow states
 *
 * @example
 * isValidWorkflowState('CREATED') // Returns true
 * isValidWorkflowState('SPEAKER_IDENTIFICATION') // Returns true
 * isValidWorkflowState('INVALID_STATE') // Returns false
 */
export function isValidWorkflowState(state: string): boolean {
  return WORKFLOW_STATE_ORDER.includes(state as WorkflowStateType);
}
