/**
 * Slot Assignment Service (Story 5.7 - Task 4b GREEN Phase)
 *
 * HTTP client for Slot Assignment APIs in Event Management Service
 * Features:
 * - Fetch unassigned sessions
 * - Assign timing to sessions (drag-drop)
 * - Bulk timing assignment
 * - Conflict detection
 */

import apiClient from '@/services/api/apiClient';
import { AxiosError } from 'axios';
import type {
  Session,
  SessionTimingRequest,
  BulkTimingRequest,
  BulkTimingResponse,
  ConflictAnalysisResponse,
} from '@/types/event.types';

// API base path for slot assignment endpoints
const SLOT_ASSIGNMENT_API_PATH = '/events';

/**
 * Slot Assignment Service Class
 *
 * Handles all HTTP requests to the Slot Assignment APIs
 */
class SlotAssignmentService {
  /**
   * Get sessions without timing (placeholder sessions)
   *
   * GET /api/v1/events/{eventCode}/sessions/unassigned
   *
   * @param eventCode - Event code (e.g., "BATbern142")
   * @returns Array of unassigned sessions
   */
  async getUnassignedSessions(eventCode: string): Promise<Session[]> {
    try {
      const response = await apiClient.get<Session[]>(
        `${SLOT_ASSIGNMENT_API_PATH}/${eventCode}/sessions/unassigned`
      );
      return response.data;
    } catch (error) {
      // Re-throw AxiosError to preserve 401/403 auth errors
      if (
        error instanceof AxiosError &&
        (error.response?.status === 401 || error.response?.status === 403)
      ) {
        throw error;
      }
      throw this.transformError(error);
    }
  }

  /**
   * Assign timing to a session
   *
   * PATCH /api/v1/events/{eventCode}/sessions/{sessionSlug}/timing
   *
   * @param eventCode - Event code (e.g., "BATbern142")
   * @param sessionSlug - Session slug identifier
   * @param timing - Timing assignment request
   * @returns Updated session with timing
   */
  async assignSessionTiming(
    eventCode: string,
    sessionSlug: string,
    timing: SessionTimingRequest
  ): Promise<Session> {
    try {
      const response = await apiClient.patch<Session>(
        `${SLOT_ASSIGNMENT_API_PATH}/${eventCode}/sessions/${sessionSlug}/timing`,
        timing
      );
      return response.data;
    } catch (error) {
      // Re-throw AxiosError to preserve 401/403 auth errors and 409 conflict response data
      if (
        error instanceof AxiosError &&
        (error.response?.status === 401 ||
          error.response?.status === 403 ||
          error.response?.status === 409)
      ) {
        throw error;
      }
      throw this.transformError(error);
    }
  }

  /**
   * Bulk assign timing to multiple sessions
   *
   * POST /api/v1/events/{eventCode}/sessions/bulk-timing
   *
   * @param eventCode - Event code (e.g., "BATbern142")
   * @param bulkRequest - Bulk timing assignment request
   * @returns Bulk assignment result
   */
  async bulkAssignTiming(
    eventCode: string,
    bulkRequest: BulkTimingRequest
  ): Promise<BulkTimingResponse> {
    try {
      const response = await apiClient.post<BulkTimingResponse>(
        `${SLOT_ASSIGNMENT_API_PATH}/${eventCode}/sessions/bulk-timing`,
        bulkRequest
      );
      return response.data;
    } catch (error) {
      // Re-throw AxiosError to preserve 401/403 auth errors and 409 conflict response data
      if (
        error instanceof AxiosError &&
        (error.response?.status === 401 ||
          error.response?.status === 403 ||
          error.response?.status === 409)
      ) {
        throw error;
      }
      throw this.transformError(error);
    }
  }

  /**
   * Detect session timing conflicts
   *
   * GET /api/v1/events/{eventCode}/sessions/conflicts
   *
   * @param eventCode - Event code (e.g., "BATbern142")
   * @returns Conflict analysis response
   */
  async detectConflicts(eventCode: string): Promise<ConflictAnalysisResponse> {
    try {
      const response = await apiClient.get<ConflictAnalysisResponse>(
        `${SLOT_ASSIGNMENT_API_PATH}/${eventCode}/sessions/conflicts`
      );
      return response.data;
    } catch (error) {
      // Re-throw AxiosError to preserve 401/403 auth errors
      if (
        error instanceof AxiosError &&
        (error.response?.status === 401 || error.response?.status === 403)
      ) {
        throw error;
      }
      throw this.transformError(error);
    }
  }

  /**
   * Clear all session timings for an event
   *
   * DELETE /api/v1/events/{eventCode}/sessions/timing
   *
   * @param eventCode - Event code (e.g., "BATbern142")
   * @returns Response with cleared count
   */
  async clearAllTimings(eventCode: string): Promise<{ message: string; clearedCount: number }> {
    try {
      const response = await apiClient.delete<{ message: string; clearedCount: number }>(
        `${SLOT_ASSIGNMENT_API_PATH}/${eventCode}/sessions/timing`
      );
      return response.data;
    } catch (error) {
      // Re-throw AxiosError to preserve 401/403 auth errors
      if (
        error instanceof AxiosError &&
        (error.response?.status === 401 || error.response?.status === 403)
      ) {
        throw error;
      }
      throw this.transformError(error);
    }
  }

  /**
   * Auto-assign all unassigned sessions to available time slots
   *
   * POST /api/v1/events/{eventCode}/sessions/auto-assign
   *
   * @param eventCode - Event code (e.g., "BATbern142")
   * @returns Response with assigned count
   */
  async autoAssignTimings(eventCode: string): Promise<{ message: string; assignedCount: number }> {
    try {
      const response = await apiClient.post<{ message: string; assignedCount: number }>(
        `${SLOT_ASSIGNMENT_API_PATH}/${eventCode}/sessions/auto-assign`
      );
      return response.data;
    } catch (error) {
      // Re-throw AxiosError to preserve 401/403 auth errors
      if (
        error instanceof AxiosError &&
        (error.response?.status === 401 || error.response?.status === 403)
      ) {
        throw error;
      }
      throw this.transformError(error);
    }
  }

  /**
   * Transform Axios errors into user-friendly error messages
   */
  private transformError(error: unknown): Error {
    if (error instanceof AxiosError) {
      // Extract error message from response
      const message =
        error.response?.data?.message ||
        error.response?.data?.error ||
        error.message ||
        'An unexpected error occurred';

      // Handle specific error cases
      if (error.response?.status === 404) {
        if (message.includes('Event')) {
          return new Error('Event not found');
        }
        if (message.includes('Session')) {
          return new Error('Session not found');
        }
      }

      // Include correlation ID if available (Story 1.9)
      const correlationId = error.response?.headers?.['x-correlation-id'];
      const errorMessage = correlationId
        ? `${message} (Correlation ID: ${correlationId})`
        : message;

      return new Error(errorMessage);
    }

    return error instanceof Error ? error : new Error('An unexpected error occurred');
  }
}

// Export singleton instance
export const slotAssignmentService = new SlotAssignmentService();
