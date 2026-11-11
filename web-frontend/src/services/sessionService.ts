/**
 * Session API Client
 *
 * HTTP client for Session Management APIs in Event Management Service
 * Features:
 * - JWT authentication via interceptors
 * - Error handling with correlation IDs
 * - Batch import support for historical sessions
 */

import apiClient from '@/services/api/apiClient';
import { isAxiosError } from 'axios';
import type {
  BatchImportSessionRequest,
  SessionBatchImportResult,
} from '@/types/sessionImport.types';
import type { components } from '@/types/generated/event-sessions-api.types';

type CreateSessionRequest = components['schemas']['CreateSessionRequest'];
type CreateSessionResponse = components['schemas']['CreateSessionResponse'];

// API base path for session endpoints
const SESSION_API_PATH = '/events';

/** Most sessions the server imports in one request (`imports` maxItems, Story 4.10b). */
export const IMPORT_CHUNK = 100;

/**
 * A batch import that failed after earlier chunks were saved: `partial` holds what the server
 * saved before the failure (its `details` are the first `partial.details.length` items, in
 * order), so the caller can show it. The message is that of the failed request.
 */
export class SessionImportError extends Error {
  constructor(
    message: string,
    readonly partial: SessionBatchImportResult
  ) {
    super(message);
    this.name = 'SessionImportError';
  }
}

/**
 * Session API Client Class
 *
 * Handles all HTTP requests to the Session Management APIs
 */
class SessionApiClient {
  /**
   * Batch import sessions for a specific event
   *
   * POST /api/v1/events/{eventCode}/sessions with `{ imports }` (Story 4.10b: the import is a mode
   * of the session create; was `POST …/sessions/batch-import`). The server takes at most
   * {@link IMPORT_CHUNK} items per request, so a longer list goes in several requests whose
   * results are summed. An empty list sends nothing. When a later chunk fails, the earlier ones
   * are saved already: the error is a {@link SessionImportError} carrying their totals.
   *
   * @param eventCode - Event code (e.g., "BATbern142")
   * @param sessions - Array of session import requests
   * @returns Batch import result with success/skip/fail counts
   */
  async batchImportSessions(
    eventCode: string,
    sessions: BatchImportSessionRequest[]
  ): Promise<SessionBatchImportResult> {
    const total: SessionBatchImportResult = {
      totalProcessed: 0,
      successfullyCreated: 0,
      updated: 0,
      skipped: 0,
      failed: 0,
      details: [],
    };
    try {
      for (let start = 0; start < sessions.length; start += IMPORT_CHUNK) {
        const body: CreateSessionRequest = { imports: sessions.slice(start, start + IMPORT_CHUNK) };
        // Use extended timeout for batch import (120s) to handle large PDF downloads
        const response = await apiClient.post<CreateSessionResponse>(
          `${SESSION_API_PATH}/${eventCode}/sessions`,
          body,
          {
            timeout: 120000, // 2 minutes - needed for downloading large PDFs from CDN
          }
        );
        const part = response.data;
        total.totalProcessed += part.totalProcessed ?? 0;
        total.successfullyCreated += part.successfullyCreated ?? 0;
        total.updated += part.updated ?? 0;
        total.skipped += part.skipped ?? 0;
        total.failed += part.failed ?? 0;
        total.details.push(...(part.details ?? []));
      }
      return total;
    } catch (error) {
      const transformed = this.transformError(error);
      if (total.details.length > 0) {
        throw new SessionImportError(transformed.message, total);
      }
      throw transformed;
    }
  }

  /**
   * Update session details (title, description, duration)
   *
   * PATCH /api/v1/events/{eventCode}/sessions/{sessionSlug}
   *
   * @param eventCode - Event code (e.g., "BATbern142")
   * @param sessionSlug - Session slug identifier
   * @param updates - Session update data (title, description, durationMinutes)
   * @returns Updated session data
   */
  async updateSession(
    eventCode: string,
    sessionSlug: string,
    updates: {
      title?: string;
      description?: string;
      durationMinutes?: number;
    }
  ): Promise<void> {
    try {
      await apiClient.patch(`${SESSION_API_PATH}/${eventCode}/sessions/${sessionSlug}`, updates);
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Transform Axios errors into user-friendly error messages
   */
  private transformError(error: unknown): Error {
    if (isAxiosError(error)) {
      // Extract error message from response
      const message =
        error.response?.data?.message ||
        error.response?.data?.error ||
        error.message ||
        'An unexpected error occurred';

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
export const sessionApiClient = new SessionApiClient();

// Export class for testing
export { SessionApiClient };
