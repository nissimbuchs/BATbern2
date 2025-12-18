/**
 * Workflow Service (Story 5.1a - Task 9)
 *
 * HTTP client for Event Workflow Status APIs
 * Features:
 * - Get workflow status (GET /api/v1/events/{code}/workflow/status)
 * - Transition workflow state (PUT /api/v1/events/{code}/workflow/transition)
 * - JWT authentication via interceptors
 * - Error handling with correlation IDs
 */

import apiClient from '@/services/api/apiClient';
import type { components } from '@/types/generated/events-api.types';

// Type definitions from OpenAPI spec
export type WorkflowStatusDto = components['schemas']['WorkflowStatusDto'];
export type TransitionStateRequest = components['schemas']['TransitionStateRequest'];
export type EventWorkflowState = components['schemas']['EventWorkflowState'];
export type Event = components['schemas']['Event'];

// API base path for workflow endpoints
const WORKFLOW_API_PATH = '/events';

/**
 * Workflow Service
 *
 * Handles all HTTP requests for event workflow state management
 */
class WorkflowService {
  /**
   * Get current workflow status for an event
   *
   * @param eventCode The event code (e.g., "BATbern56")
   * @returns WorkflowStatusDto with current state, next states, blockers, and validation messages
   * @throws Error if event not found or network failure
   */
  async getWorkflowStatus(eventCode: string): Promise<WorkflowStatusDto> {
    const response = await apiClient.get<WorkflowStatusDto>(
      `${WORKFLOW_API_PATH}/${eventCode}/workflow/status`
    );

    return response.data;
  }

  /**
   * Transition event to target workflow state
   *
   * @param eventCode The event code (e.g., "BATbern56")
   * @param targetState The target workflow state (e.g., "TOPIC_SELECTION")
   * @param override Optional flag to bypass workflow validation (defaults to false)
   * @param reason Optional reason for overriding validation (for audit trail)
   * @returns Updated Event with new workflow state
   * @throws Error if invalid transition, validation fails, or unauthorized
   */
  async transitionWorkflowState(
    eventCode: string,
    targetState: EventWorkflowState,
    override?: boolean,
    reason?: string
  ): Promise<Event> {
    const requestBody: TransitionStateRequest = {
      targetState,
      overrideValidation: override ?? false,
    };

    // Add override reason if provided
    if (reason) {
      requestBody.overrideReason = reason;
    }

    const response = await apiClient.put<Event>(
      `${WORKFLOW_API_PATH}/${eventCode}/workflow/transition`,
      requestBody
    );

    return response.data;
  }
}

// Export singleton instance
export const workflowService = new WorkflowService();

// Export class for testing
export default WorkflowService;
