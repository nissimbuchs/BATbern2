/**
 * Topic Service (Story 5.2)
 *
 * HTTP client for Topic Management APIs
 * Features:
 * - Get topics with filtering and pagination (GET /api/v1/topics)
 * - Get topic by ID (GET /api/v1/topics/{id})
 * - Create new topic (POST /api/v1/topics)
 * - Override staleness score (PUT /api/v1/topics/{id}/override-staleness)
 * - Get similar topics (GET /api/v1/topics/{id}/similar)
 * - Select topic for event (POST /api/v1/events/{eventCode}/topics)
 * - JWT authentication via interceptors
 * - Error handling with correlation IDs
 */

import apiClient from '@/services/api/apiClient';
import type {
  Topic,
  TopicListResponse,
  CreateTopicRequest,
  OverrideStalenesRequest,
  TopicFilters,
  SelectTopicForEventRequest,
  TopicSelectionResponse,
  TopicUsageHistory,
} from '@/types/topic.types';

// API base paths
const TOPICS_API_PATH = '/topics';
const EVENTS_API_PATH = '/events';

/**
 * Topic Service
 *
 * Handles all HTTP requests for topic management
 */
class TopicService {
  /**
   * Get topics with filtering and pagination (AC1)
   *
   * @param filters Optional filters for category, status, pagination, etc.
   * @returns Paginated list of topics
   * @throws Error if network failure or unauthorized
   */
  async getTopics(filters?: TopicFilters): Promise<TopicListResponse> {
    const params: Record<string, string | number> = {};

    if (filters) {
      // Build filter JSON for category/status filtering
      const filterObj: Record<string, unknown> = {};
      if (filters.category) {
        filterObj.category = filters.category;
      }
      if (filters.status) {
        filterObj.status = filters.status;
      }

      if (Object.keys(filterObj).length > 0) {
        params.filter = JSON.stringify(filterObj);
      }

      if (filters.sort) {
        params.sort = filters.sort;
      }
      if (filters.page) {
        params.page = filters.page;
      }
      if (filters.limit) {
        params.limit = filters.limit;
      }
      if (filters.include) {
        params.include = filters.include;
      }
    }

    const response = await apiClient.get<TopicListResponse>(TOPICS_API_PATH, { params });

    return response.data;
  }

  /**
   * Get topic by ID with optional includes
   *
   * @param id Topic UUID
   * @param include Comma-separated resources to include (e.g., "similarity,history")
   * @returns Topic details
   * @throws Error if topic not found or network failure
   */
  async getTopicById(id: string, include?: string): Promise<Topic> {
    const params: Record<string, string> = {};
    if (include) {
      params.include = include;
    }

    const response = await apiClient.get<Topic>(`${TOPICS_API_PATH}/${id}`, { params });

    return response.data;
  }

  /**
   * Create new topic (AC8)
   *
   * @param request Topic creation request
   * @returns Created topic
   * @throws Error if validation fails or unauthorized
   */
  async createTopic(request: CreateTopicRequest): Promise<Topic> {
    const response = await apiClient.post<Topic>(TOPICS_API_PATH, request);

    return response.data;
  }

  /**
   * Update existing topic (Story 5.2a - Edit Topic Feature)
   *
   * @param id Topic UUID
   * @param request Topic update request
   * @returns Updated topic
   * @throws Error if topic not found, validation fails, or unauthorized
   */
  async updateTopic(id: string, request: CreateTopicRequest): Promise<Topic> {
    const response = await apiClient.put<Topic>(`${TOPICS_API_PATH}/${id}`, request);

    return response.data;
  }

  /**
   * Delete topic (Story 5.2a - Delete Topic Feature)
   * Only allowed if topic has never been used (no events attached).
   *
   * @param id Topic UUID
   * @throws Error if topic not found, has been used, or unauthorized
   */
  async deleteTopic(id: string): Promise<void> {
    await apiClient.delete(`${TOPICS_API_PATH}/${id}`);
  }

  /**
   * Override staleness score with justification (AC7)
   *
   * @param id Topic UUID
   * @param request Override request with staleness score and justification
   * @returns Updated topic
   * @throws Error if topic not found or validation fails
   */
  async overrideStaleness(id: string, request: OverrideStalenesRequest): Promise<Topic> {
    const response = await apiClient.put<Topic>(
      `${TOPICS_API_PATH}/${id}/override-staleness`,
      request
    );

    return response.data;
  }

  /**
   * Get similar topics (>70% similarity) for duplicate detection (AC5)
   *
   * @param id Topic UUID
   * @returns List of similar topics
   * @throws Error if topic not found or network failure
   */
  async getSimilarTopics(id: string): Promise<Topic[]> {
    const response = await apiClient.get<Topic[]>(`${TOPICS_API_PATH}/${id}/similar`);

    return response.data;
  }

  /**
   * Get topic usage history for heat map visualization (AC2)
   *
   * @param id Topic UUID
   * @returns List of usage history records ordered by date descending
   * @throws Error if topic not found or network failure
   */
  async getTopicUsageHistory(id: string): Promise<TopicUsageHistory[]> {
    const response = await apiClient.get<TopicUsageHistory[]>(
      `${TOPICS_API_PATH}/${id}/usage-history`
    );

    return response.data;
  }

  /**
   * Select topic for event and transition workflow state (AC14)
   *
   * @param eventCode Event code (e.g., "BATbern56")
   * @param request Request with topicCode and optional justification (generated DTO)
   * @returns TopicSelectionResponse with event and topic details (generated DTO)
   * @throws Error if event/topic not found, invalid state, or unauthorized
   */
  async selectTopicForEvent(
    eventCode: string,
    request: SelectTopicForEventRequest
  ): Promise<TopicSelectionResponse> {
    const response = await apiClient.post<TopicSelectionResponse>(
      `${EVENTS_API_PATH}/${eventCode}/topics`,
      request
    );
    return response.data;
  }

  /**
   * Recalculate staleness scores for all topics (maintenance endpoint)
   *
   * @returns Success message
   * @throws Error if unauthorized (ORGANIZER only)
   */
  async recalculateStaleness(): Promise<string> {
    const response = await apiClient.post<string>(`${TOPICS_API_PATH}/recalculate-staleness`);

    return response.data;
  }

  /**
   * Calculate similarity scores for all topics (maintenance endpoint, AC4)
   *
   * @returns Success message
   * @throws Error if unauthorized (ORGANIZER only)
   */
  async calculateSimilarities(): Promise<string> {
    const response = await apiClient.post<string>(`${TOPICS_API_PATH}/calculate-similarities`);

    return response.data;
  }
}

// Export singleton instance
export const topicService = new TopicService();

// Export class for testing
export default TopicService;
