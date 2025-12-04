/**
 * Event Type Service (Story 5.1 - Task 3b)
 *
 * HTTP client for Event Type APIs (Event Management Service)
 * Features:
 * - JWT authentication via interceptors (Story 1.17)
 * - Error handling with correlation IDs (Story 1.9)
 * - Generated types from OpenAPI spec (ADR-006)
 * - ORGANIZER-only PUT operations (RBAC)
 */

import apiClient from '@/services/api/apiClient';
import type { components } from '@/types/generated/events-api.types';

// Import generated types from OpenAPI spec (ADR-006 compliance)
type EventType = components['schemas']['EventType'];
type EventSlotConfigurationResponse = components['schemas']['EventSlotConfigurationResponse'];
type UpdateEventSlotConfigurationRequest =
  components['schemas']['UpdateEventSlotConfigurationRequest'];

// API base path for event type endpoints
const EVENT_TYPES_API_PATH = '/events/types';

/**
 * Event Type Service Class
 *
 * Handles all HTTP requests to Event Type APIs
 * Uses generated types for compile-time safety (ADR-006)
 */
class EventTypeService {
  /**
   * Get all event type configurations
   *
   * @returns Array of event type configurations (FULL_DAY, AFTERNOON, EVENING)
   * @throws Error with correlationId if request fails
   */
  async getAllEventTypes(): Promise<EventSlotConfigurationResponse[]> {
    const response = await apiClient.get<EventSlotConfigurationResponse[]>(EVENT_TYPES_API_PATH);
    return response.data;
  }

  /**
   * Get specific event type configuration
   *
   * @param type - Event type enum value (FULL_DAY, AFTERNOON, EVENING)
   * @returns Event type configuration
   * @throws Error with correlationId if request fails or type not found (404)
   */
  async getEventType(type: EventType): Promise<EventSlotConfigurationResponse> {
    const response = await apiClient.get<EventSlotConfigurationResponse>(
      `${EVENT_TYPES_API_PATH}/${type}`
    );
    return response.data;
  }

  /**
   * Update event type configuration (ORGANIZER only)
   *
   * @param type - Event type enum value to update
   * @param config - Updated configuration (all fields required)
   * @returns Updated event type configuration
   * @throws Error with correlationId if request fails
   * @throws 403 Forbidden if user is not ORGANIZER
   * @throws 400 Bad Request if validation fails
   */
  async updateEventType(
    type: EventType,
    config: UpdateEventSlotConfigurationRequest
  ): Promise<EventSlotConfigurationResponse> {
    const response = await apiClient.put<EventSlotConfigurationResponse>(
      `${EVENT_TYPES_API_PATH}/${type}`,
      config
    );
    return response.data;
  }
}

// Export singleton instance
export const eventTypeService = new EventTypeService();
