/**
 * Topic Lookup/Cache Utility (Story 5.2a - Event Batch Import with Topics)
 *
 * Provides efficient topic lookup by category with caching to minimize API calls
 * during batch event imports.
 *
 * Features:
 * - In-memory cache (category → topicId mapping)
 * - On-demand topic creation when category doesn't exist
 * - Cache management utilities for testing
 *
 * Usage:
 * ```typescript
 * const topicId = await getOrCreateTopicByCategory('Frontend & UI');
 * ```
 */

import { topicService } from '@/services/topicService';
import type { CreateTopicRequest } from '@/types/topic.types';

/**
 * In-memory cache: category → topicId
 * Cleared between batch imports to ensure fresh data
 */
const topicCache = new Map<string, string>();

/**
 * Get or create topic by title
 *
 * This function:
 * 1. Checks cache first for performance
 * 2. Fetches from API by title (not category) if not cached
 * 3. Creates new topic if not found
 * 4. Caches result for subsequent calls
 *
 * NOTE: The JSON file's "category" field represents the topic TITLE, not the topic's category field.
 * We filter by title to find existing topics.
 *
 * @param topicTitle Topic title (e.g., "Frontend & UI", "Cloud & Infrastructure")
 * @returns Topic ID (UUID)
 * @throws Error if API call fails or topic creation fails
 *
 * @example
 * ```typescript
 * // First call fetches from API
 * const id1 = await getOrCreateTopicByCategory('Security');
 *
 * // Second call uses cache (no API call)
 * const id2 = await getOrCreateTopicByCategory('Security');
 * ```
 */
export async function getOrCreateTopicByCategory(topicTitle: string): Promise<string> {
  // Check cache first
  if (topicCache.has(topicTitle)) {
    return topicCache.get(topicTitle)!;
  }

  // Fetch all topics and find by title (API doesn't support title filter yet)
  const response = await topicService.getTopics({
    limit: 100, // Get all topics to search by title
  });

  // Find topic by title (case-insensitive match)
  const matchingTopic = response.data.find(
    (topic) => topic.title.toLowerCase() === topicTitle.toLowerCase()
  );

  if (matchingTopic) {
    topicCache.set(topicTitle, matchingTopic.id);
    return matchingTopic.id;
  }

  // Topic doesn't exist - create it
  // Use 'technical' as default category since we can't infer from title alone
  const createRequest: CreateTopicRequest = {
    title: topicTitle,
    category: 'technical', // Default to technical category
    description: `Topic: ${topicTitle}`,
  };

  const newTopic = await topicService.createTopic(createRequest);

  // Cache newly created topic
  topicCache.set(topicTitle, newTopic.id);

  return newTopic.id;
}

/**
 * Clear the topic cache
 *
 * Useful for:
 * - Testing (clear cache between tests)
 * - Forcing fresh API lookups
 * - Memory management for long-running imports
 *
 * @example
 * ```typescript
 * clearTopicCache(); // All subsequent lookups will hit API
 * ```
 */
export function clearTopicCache(): void {
  topicCache.clear();
}

/**
 * Get current size of topic cache
 *
 * Useful for:
 * - Monitoring cache efficiency
 * - Testing cache behavior
 * - Debugging import issues
 *
 * @returns Number of cached category → topicId mappings
 *
 * @example
 * ```typescript
 * console.log(`Cached ${getTopicCacheSize()} topics`);
 * ```
 */
export function getTopicCacheSize(): number {
  return topicCache.size;
}
