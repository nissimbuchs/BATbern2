/**
 * Event API Client (Story 2.5.3 - Task 5b)
 *
 * HTTP client for Event Management Service APIs
 * Features:
 * - JWT authentication via interceptors (Story 1.17)
 * - Error handling with correlation IDs (Story 1.9)
 * - Resource expansion query builder (?include= parameter)
 * - PATCH support for partial updates
 * - Client-side validation
 */

import apiClient from '@/services/api/apiClient';
import axios, { AxiosError } from 'axios';
import type {
  Event,
  EventDetail,
  CreateEventRequest,
  UpdateEventRequest,
  PatchEventRequest,
  WorkflowState,
  CriticalTask,
  TeamActivity,
  CreateRegistrationRequest,
  Registration,
} from '@/types/event.types';
import type { components } from '@/types/generated/events-api.types';

type EventPhotoResponse = components['schemas']['EventPhotoResponse'];
type EventPhotoUploadRequest = components['schemas']['EventPhotoUploadRequest'];
type EventPhotoUploadResponse = components['schemas']['EventPhotoUploadResponse'];
type EventPhotoConfirmRequest = components['schemas']['EventPhotoConfirmRequest'];

type TeaserImageItem = components['schemas']['TeaserImageItem'];
type TeaserImageUploadUrlRequest = components['schemas']['TeaserImageUploadUrlRequest'];
type TeaserImageUploadUrlResponse = components['schemas']['TeaserImageUploadUrlResponse'];
type TeaserImageConfirmRequest = components['schemas']['TeaserImageConfirmRequest'];

// API base path for event endpoints
const EVENT_API_PATH = '/events';

// Import types from event.types.ts to avoid duplication
import type { EventListResponse, EventFilters, PaginationParams } from '@/types/event.types';

/**
 * Event API Client Class
 *
 * Handles all HTTP requests to the Event Management Service
 */
class EventApiClient {
  /**
   * Get paginated list of events with optional filters
   */
  async getEvents(
    pagination: PaginationParams = { page: 1, limit: 20 },
    filters?: EventFilters,
    options?: { expand?: string[]; sort?: string }
  ): Promise<EventListResponse> {
    try {
      // Use URLSearchParams for proper URL encoding
      const params = new URLSearchParams();
      params.append('page', pagination.page.toString());
      params.append('limit', pagination.limit.toString());

      // Add includeArchived parameter (defaults to false on backend)
      // When explicitly set to false or undefined, backend excludes ARCHIVED events
      if (filters?.includeArchived !== undefined) {
        params.append('includeArchived', filters.includeArchived.toString());
      }

      // Build JSON filter object
      const filterObj: Record<string, unknown> = {};
      if (filters?.workflowState && filters.workflowState.length > 0) {
        // Send as array with $in operator for OR logic (match any of the states)
        filterObj.workflowState = { $in: filters.workflowState };
      }
      if (filters?.year) {
        filterObj.year = filters.year;
      }
      if (filters?.search) {
        // Use CONTAINS operator on title field for text search
        filterObj.title = { $contains: filters.search };
      }
      if (filters?.topicCode && filters.topicCode.length > 0) {
        // Filter by topic code(s) - Story 4.2 archive filtering
        filterObj.topicCode = { $in: filters.topicCode };
      }

      // Add filter parameter if we have filters
      // URLSearchParams will properly encode all special characters
      if (Object.keys(filterObj).length > 0) {
        params.append('filter', JSON.stringify(filterObj));
      }

      // Add include parameter for resource expansion
      if (options?.expand && options.expand.length > 0) {
        params.append('include', options.expand.join(','));
      }

      // Add sort parameter (Story 4.2 - archive sorting)
      if (options?.sort) {
        params.append('sort', options.sort);
      }

      const response = await apiClient.get<EventListResponse>(
        `${EVENT_API_PATH}?${params.toString()}`
      );

      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Get single event by eventCode with optional resource expansion
   * Story 1.16.2: Uses eventCode as identifier instead of UUID
   */
  async getEvent(eventCode: string, options?: { expand?: string[] }): Promise<EventDetail> {
    try {
      const params = new URLSearchParams();
      if (options?.expand && options.expand.length > 0) {
        params.append('include', options.expand.join(','));
      }

      const url = params.toString()
        ? `${EVENT_API_PATH}/${eventCode}?${params.toString()}`
        : `${EVENT_API_PATH}/${eventCode}`;

      const response = await apiClient.get<EventDetail>(url);
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Create a new event
   */
  async createEvent(data: CreateEventRequest): Promise<Event> {
    try {
      // Client-side validation
      this.validateEventDate(data.date, data.workflowState);
      this.validateRegistrationDeadline(data.date, data.registrationDeadline);
      this.validateVenueCapacity(data.venueCapacity);

      const response = await apiClient.post<Event>(EVENT_API_PATH, data);
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Update existing event (full update)
   * Story 1.16.2: Uses eventCode as identifier instead of UUID
   */
  async updateEvent(eventCode: string, data: UpdateEventRequest): Promise<Event> {
    try {
      // Client-side validation if dates are being updated
      if (data.date) {
        this.validateEventDate(data.date, data.workflowState);
      }
      if (data.date && data.registrationDeadline) {
        this.validateRegistrationDeadline(data.date, data.registrationDeadline);
      }

      const response = await apiClient.put<Event>(`${EVENT_API_PATH}/${eventCode}`, data);
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Partial update of event (PATCH)
   * Only sends changed fields
   */
  async patchEvent(eventCode: string, data: PatchEventRequest): Promise<Event> {
    try {
      // Validate dates if they're being updated
      if (data.date) {
        this.validateEventDate(data.date, data.workflowState);
      }

      const response = await apiClient.patch<Event>(`${EVENT_API_PATH}/${eventCode}`, data);
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Delete an event
   */
  async deleteEvent(eventCode: string): Promise<void> {
    try {
      await apiClient.delete(`${EVENT_API_PATH}/${eventCode}`);
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Get workflow state for an event
   */
  async getEventWorkflow(eventCode: string): Promise<WorkflowState> {
    try {
      const response = await apiClient.get<WorkflowState>(
        `${EVENT_API_PATH}/${eventCode}/workflow`
      );
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Get critical tasks for an event
   */
  async getCriticalTasks(eventCode: string): Promise<CriticalTask[]> {
    try {
      const response = await apiClient.get<CriticalTask[]>(
        `${EVENT_API_PATH}/${eventCode}/tasks/critical`
      );
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Get team activity for an event
   */
  async getTeamActivity(eventCode: string): Promise<TeamActivity[]> {
    try {
      const response = await apiClient.get<TeamActivity[]>(
        `${EVENT_API_PATH}/${eventCode}/activity`
      );
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Get current published event (PUBLIC ACCESS - Story 4.1.3)
   *
   * Retrieves the next upcoming published event for the public website.
   * This method works with or without authentication (public endpoint).
   *
   * @param options Optional configuration for resource expansion
   * @returns Current published event or null if none exists
   */
  async getCurrentEvent(options?: { expand?: string[] }): Promise<EventDetail | null> {
    try {
      const params = new URLSearchParams();
      if (options?.expand && options.expand.length > 0) {
        params.append('include', options.expand.join(','));
      }

      const url = params.toString()
        ? `${EVENT_API_PATH}/current?${params.toString()}`
        : `${EVENT_API_PATH}/current`;

      const response = await apiClient.get<EventDetail>(url);
      return response.data;
    } catch (error) {
      // Return null if no current event exists (404)
      if (this.isAxiosError(error) && error.response?.status === 404) {
        return null;
      }
      throw this.transformError(error);
    }
  }

  /**
   * Create event registration (PUBLIC ACCESS - Story 4.1.5)
   *
   * Allows anonymous public users to register for events.
   * Per ADR-005: Anonymous registration with email-based account linking.
   *
   * @param eventCode Event code identifier
   * @param data Registration request data
   * @returns Created registration with confirmation code
   */
  async createRegistration(
    eventCode: string,
    data: CreateRegistrationRequest
  ): Promise<{ message: string; email: string }> {
    try {
      const response = await apiClient.post<{ message: string; email: string }>(
        `${EVENT_API_PATH}/${eventCode}/registrations`,
        data
      );
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Confirm Registration (PUBLIC ACCESS - Story: Email Confirmation)
   *
   * Confirms a pending registration using the JWT token from the confirmation email.
   * Updates registration status from PENDING to CONFIRMED.
   *
   * @param eventCode Event code from URL
   * @param token JWT confirmation token from email
   * @returns Confirmation response with status
   */
  async confirmRegistration(
    eventCode: string,
    token: string
  ): Promise<{ message: string; status: string }> {
    try {
      const response = await apiClient.post<{ message: string; status: string }>(
        `/events/${eventCode}/registrations/confirm`,
        null,
        {
          params: { token },
          headers: {
            // Public endpoint - skip auth header added by interceptor
            'Skip-Auth': 'true',
          },
        }
      );
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Cancel Registration (PUBLIC ACCESS - Story 4.1.5d: Email Cancellation)
   *
   * Cancels a registration using the JWT token from the cancellation email.
   * Permanently deletes the registration from the database.
   *
   * @param eventCode Event code from URL
   * @param token JWT cancellation token from email
   * @returns Cancellation response with status
   */
  async cancelRegistration(
    eventCode: string,
    token: string
  ): Promise<{ message: string; status: string }> {
    try {
      const response = await apiClient.post<{ message: string; status: string }>(
        `/events/${eventCode}/registrations/cancel`,
        null,
        {
          params: { token },
          headers: {
            // Public endpoint - skip auth header added by interceptor
            'Skip-Auth': 'true',
          },
        }
      );
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Get registration by confirmation code (PUBLIC ACCESS - Story 4.1.5)
   *
   * Retrieves registration details using confirmation code.
   * Anyone with the confirmation code can view the registration.
   *
   * @param eventCode Event code identifier
   * @param confirmationCode Registration confirmation code
   * @returns Registration details
   */
  async getRegistration(eventCode: string, confirmationCode: string): Promise<Registration> {
    try {
      const response = await apiClient.get<Registration>(
        `${EVENT_API_PATH}/${eventCode}/registrations/${confirmationCode}`
      );
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Assign topic to event (Story 5.2a - Event Batch Import)
   *
   * Assigns a topic to an event by calling POST /events/{eventCode}/topics
   *
   * @param eventCode Event code identifier (e.g., "BATbern56")
   * @param topicCode Topic code (slug-format) to assign (ADR-003: meaningful identifiers)
   * @returns Success response
   * @throws Error if event/topic not found or unauthorized
   */
  async assignTopicToEvent(eventCode: string, topicCode: string): Promise<void> {
    try {
      await apiClient.post(`${EVENT_API_PATH}/${eventCode}/topics`, { topicCode });
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Check if event exists by event number (Story 5.2a - Event Batch Import)
   *
   * Queries the API to check if an event with the given number exists.
   * Used during batch import to determine create vs update mode.
   *
   * @param eventNumber Event number (e.g., 56 for "BATbern56")
   * @returns true if event exists, false otherwise
   * @throws Error if network failure or unauthorized
   */
  async checkEventExists(eventNumber: number): Promise<boolean> {
    try {
      // Query by event number using filter parameter
      const response = await this.getEvents({ page: 1, limit: 1 }, {
        eventNumber: eventNumber,
      } as unknown as {
        workflowState?: string[];
        year?: number;
        search?: string;
      });

      return response.data.length > 0;
    } catch (error) {
      // If 404 or other error, assume doesn't exist
      if (this.isAxiosError(error) && error.response?.status === 404) {
        return false;
      }
      throw this.transformError(error);
    }
  }

  /**
   * Type guard for Axios errors
   */
  private isAxiosError(error: unknown): error is AxiosError {
    return (error as AxiosError).isAxiosError === true;
  }

  /**
   * Client-side validation: Event date must be in the future
   * Skip validation for archived events (historical imports)
   */
  private validateEventDate(eventDate: string, workflowState?: string): void {
    // Skip validation for archived events (historical imports)
    if (workflowState === 'ARCHIVED') {
      return;
    }

    const eventDateTime = new Date(eventDate);
    const now = new Date();

    if (eventDateTime <= now) {
      throw new Error('Event date must be in the future');
    }
  }

  // ── Event Photos (Story 10.21) ────────────────────────────────────────────────

  async listEventPhotos(eventCode: string): Promise<EventPhotoResponse[]> {
    try {
      const response = await apiClient.get<EventPhotoResponse[]>(
        `${EVENT_API_PATH}/${eventCode}/photos`
      );
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  async requestEventPhotoUploadUrl(
    eventCode: string,
    request: EventPhotoUploadRequest
  ): Promise<EventPhotoUploadResponse> {
    try {
      const response = await apiClient.post<EventPhotoUploadResponse>(
        `${EVENT_API_PATH}/${eventCode}/photos/upload-url`,
        request
      );
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  async uploadPhotoToS3(uploadUrl: string, file: File): Promise<void> {
    await axios.put(uploadUrl, file, {
      headers: { 'Content-Type': file.type },
    });
  }

  async confirmEventPhotoUpload(
    eventCode: string,
    request: EventPhotoConfirmRequest
  ): Promise<EventPhotoResponse> {
    try {
      const response = await apiClient.post<EventPhotoResponse>(
        `${EVENT_API_PATH}/${eventCode}/photos/confirm`,
        request
      );
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  async deleteEventPhoto(eventCode: string, photoId: string): Promise<void> {
    try {
      await apiClient.delete(`${EVENT_API_PATH}/${eventCode}/photos/${photoId}`);
    } catch (error) {
      throw this.transformError(error);
    }
  }

  async getRecentEventPhotos(limit = 20, lastNEvents = 5): Promise<EventPhotoResponse[]> {
    try {
      const response = await apiClient.get<EventPhotoResponse[]>(
        `${EVENT_API_PATH}/recent-photos`,
        { params: { limit, lastNEvents } }
      );
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  // ── Event Teaser Images (Story 10.22) ─────────────────────────────────────────

  async requestTeaserImageUploadUrl(
    eventCode: string,
    request: TeaserImageUploadUrlRequest
  ): Promise<TeaserImageUploadUrlResponse> {
    try {
      const response = await apiClient.post<TeaserImageUploadUrlResponse>(
        `${EVENT_API_PATH}/${eventCode}/teaser-images/upload-url`,
        request
      );
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  async confirmTeaserImageUpload(
    eventCode: string,
    request: TeaserImageConfirmRequest
  ): Promise<TeaserImageItem> {
    try {
      const response = await apiClient.post<TeaserImageItem>(
        `${EVENT_API_PATH}/${eventCode}/teaser-images/confirm`,
        request
      );
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  async deleteTeaserImage(eventCode: string, imageId: string): Promise<void> {
    try {
      await apiClient.delete(`${EVENT_API_PATH}/${eventCode}/teaser-images/${imageId}`);
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Get presigned download URL for session material
   * @param sessionSlug Session identifier
   * @param materialId Material UUID
   * @returns Presigned S3 URL valid for 1 hour
   */
  async getMaterialDownloadUrl(
    eventCode: string,
    sessionSlug: string,
    materialId: string
  ): Promise<string> {
    try {
      const response = await apiClient.get<{ downloadUrl: string }>(
        `${EVENT_API_PATH}/${eventCode}/sessions/${sessionSlug}/materials/${materialId}/download`
      );
      return response.data.downloadUrl;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Client-side validation: Registration deadline must be before event date
   */
  private validateRegistrationDeadline(eventDate: string, deadline: string): void {
    const eventDateTime = new Date(eventDate);
    const deadlineDateTime = new Date(deadline);

    if (deadlineDateTime >= eventDateTime) {
      throw new Error('Registration deadline must be before event date');
    }
  }

  /**
   * Client-side validation: Venue capacity must be positive
   */
  private validateVenueCapacity(capacity: number): void {
    if (capacity <= 0) {
      throw new Error('Venue capacity must be positive');
    }
  }

  /**
   * Transform Axios errors to application errors (Story 1.9 error format)
   * Preserves correlation ID and provides user-friendly messages
   */
  private transformError(error: unknown): Error {
    if (error instanceof Error && !(error as AxiosError).isAxiosError) {
      // Already an Error (e.g., validation error), return as-is
      return error;
    }

    const axiosError = error as AxiosError;

    // Network errors
    if (!axiosError.response) {
      return new Error('Network Error: Unable to connect to server');
    }

    // HTTP errors with correlation ID
    const status = axiosError.response.status;
    const correlationId = axiosError.response.headers['x-correlation-id'];

    let message = 'An error occurred';
    if (status === 401) {
      message = 'Unauthorized: Please log in';
    } else if (status === 403) {
      message = 'Forbidden: You do not have permission to perform this action';
    } else if (status === 404) {
      message = 'Not Found: The requested event was not found';
    } else if (status === 409) {
      // Conflict - extract backend error message (e.g., duplicate registration)
      const errorData = axiosError.response.data as { message?: string; error?: string };
      message = errorData.message || errorData.error || 'Conflict: Resource already exists';
    } else if (status === 500) {
      message = 'Server Error: Please try again later';
    }

    if (correlationId) {
      message += ` (ID: ${correlationId})`;
    }

    const error_final = new Error(message);
    (error_final as Error & { status?: number }).status = status;
    return error_final;
  }
}

// Export singleton instance
export const eventApiClient = new EventApiClient();
