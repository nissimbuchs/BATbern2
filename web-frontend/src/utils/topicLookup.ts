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
 * Get or create topic by category name
 *
 * This function:
 * 1. Checks cache first for performance
 * 2. Fetches from API if not cached
 * 3. Creates new topic if not found
 * 4. Caches result for subsequent calls
 *
 * @param category Topic category name (e.g., "Frontend & UI", "Cloud & Infrastructure")
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
export async function getOrCreateTopicByCategory(category: string): Promise<string> {
  // Check cache first
  if (topicCache.has(category)) {
    return topicCache.get(category)!;
  }

  // Fetch from API with category filter
  const response = await topicService.getTopics({
    category,
  });

  // If topic exists, cache and return
  if (response.data.length > 0) {
    const topicId = response.data[0].id;
    topicCache.set(category, topicId);
    return topicId;
  }

  // Topic doesn't exist - create it
  const createRequest: CreateTopicRequest = {
    title: category,
    category: category,
    description: `Topic category: ${category}`,
  };

  const newTopic = await topicService.createTopic(createRequest);

  // Cache newly created topic
  topicCache.set(category, newTopic.id);

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
