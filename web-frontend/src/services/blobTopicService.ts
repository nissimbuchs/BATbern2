/**
 * Blob Topic Selector API Service
 * Story 10.4: Blob Topic Selector (Task 16)
 *
 * Epic 17, entry 8 (ADR-016: EMS does not call partner-coordination): the canvas data is composed
 * here. EMS gives past events, backlog and trending topics; the partner topics and logos come from
 * partner-coordination; EMS classifies the partner topic titles into clusters.
 */

import apiClient from '@/services/api/apiClient';
import { getTopics, type TopicDTO } from '@/services/api/partnerTopicsApi';
import { listPartnerLogos } from '@/services/api/partnerApi';
import type { components } from '@/types/generated/topics-api.types';
import type {
  PartnerTopicEntry,
  TopicSessionData,
  TopicSimilarityResponse,
} from '@/components/BlobTopicSelector/types';

type EmsSessionData = components['schemas']['TopicSessionDataResponse'];
export type TopicClassification = components['schemas']['TopicClassification'];

/** The spec's `maxItems` for `POST /topics/classifications`; a longer list goes in several calls. */
export const MAX_CLASSIFY_TITLES = 500;

/** Group of the partner topics that carry no company (community suggestions), as before. */
const UNKNOWN_COMPANY = 'Unknown';

/** createdAt of a topic without one (EMS used `Instant.EPOCH`). */
const EPOCH = '1970-01-01T00:00:00Z';

/** Logos are optional: without them the topics still show. */
const loadLogos = (): Promise<Record<string, string>> => listPartnerLogos().catch(() => ({}));

/**
 * Partner topics grouped by company (first appearance order; PCS lists by votes), each topic with
 * its cluster. Any failure of the topic list or the classification degrades to no partner topics,
 * as when EMS composed them.
 */
async function loadPartnerTopics(): Promise<PartnerTopicEntry[]> {
  try {
    const topics = await getTopics();
    if (topics.length === 0) return [];

    // Each title once; a blank title (the operation refuses it) keeps the fallback cluster.
    const titles = [...new Set(topics.map((t) => t.title).filter((t) => t.trim() !== ''))];
    const [clusters, logos] = await Promise.all([
      titles.length > 0 ? blobTopicService.classifyTopics(titles) : Promise.resolve([]),
      loadLogos(),
    ]);
    const clusterOf = new Map(clusters.map((c) => [c.topic, c.cluster]));

    const groups = new Map<string, TopicDTO[]>();
    for (const topic of topics) {
      const company = topic.suggestedByCompany ?? UNKNOWN_COMPANY;
      groups.set(company, [...(groups.get(company) ?? []), topic]);
    }
    return [...groups].map(([companyName, items]) => ({
      companyName,
      logoUrl: logos[companyName] ?? null,
      topics: items.map((t) => ({
        title: t.title,
        cluster: clusterOf.get(t.title) ?? 'BUSINESS_OTHER',
        voteCount: t.voteCount,
        createdAt: t.createdAt ?? EPOCH,
      })),
    }));
  } catch {
    return [];
  }
}

export const blobTopicService = {
  /**
   * The canvas bootstrap data: EMS session data and the partner topics in parallel. An EMS
   * failure fails the whole call (as before); a partner-side failure leaves `partnerTopics` empty.
   */
  getSessionData: async (eventCode: string): Promise<TopicSessionData> => {
    const [response, partnerTopics] = await Promise.all([
      apiClient.get<EmsSessionData>(`/events/${eventCode}/topic-session-data`),
      loadPartnerTopics(),
    ]);
    return { ...response.data, partnerTopics };
  },

  /** Cluster per title, in request order; more than {@link MAX_CLASSIFY_TITLES} go in several calls. */
  classifyTopics: async (titles: string[]): Promise<TopicClassification[]> => {
    const chunks: string[][] = [];
    for (let i = 0; i < titles.length; i += MAX_CLASSIFY_TITLES) {
      chunks.push(titles.slice(i, i + MAX_CLASSIFY_TITLES));
    }
    const answers = await Promise.all(
      chunks.map((topics) =>
        apiClient.post<components['schemas']['TopicClassificationResponse']>(
          '/topics/classifications',
          { topics }
        )
      )
    );
    return answers.flatMap((a) => a.data.classifications);
  },

  getSimilarity: async (eventCode: string, topic: string): Promise<TopicSimilarityResponse> => {
    const response = await apiClient.post<TopicSimilarityResponse>(
      `/events/${eventCode}/topic-similarity`,
      { topic }
    );
    return response.data;
  },

  acceptTopic: async (eventCode: string, topicCode: string, note: string): Promise<void> => {
    await apiClient.patch(`/events/${eventCode}`, {
      topicCode,
      topicSelectionNote: note,
    });
  },
};
