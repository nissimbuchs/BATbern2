/**
 * Type definitions for BlobTopicSelector
 * Story 10.4: Blob Topic Selector
 */

import type * as d3 from 'd3';

export type BlobType =
  | 'blue'
  | 'green'
  | 'ghost-backlog'
  | 'ghost-partner'
  | 'ghost-trend'
  | 'red-star';

export interface PartnerTopicEntry {
  companyName: string;
  logoUrl: string;
  topics: string[];
}

export interface PastEventEntry {
  eventNumber: number;
  topicName: string;
  cluster: string;
}

export interface TopicSessionData {
  partnerTopics: PartnerTopicEntry[];
  pastEvents: PastEventEntry[];
  organizerBacklog: string[];
  trendingTopics: string[];
}

export interface TopicSimilarityResponse {
  cluster: string;
  similarityScore: number;
  relatedPastEventNumbers: number[];
}

export interface AbsorbedLogo {
  companyName: string;
  logoUrl: string;
}

export interface BlueBlobNode extends d3.SimulationNodeDatum {
  id: string;
  type: 'blue';
  name: string;
  r: number;
  cluster?: string;
  similarityScore?: number;
  relatedPastEventNumbers?: number[];
  absorbedLogos: AbsorbedLogo[];
}

export interface GreenBlobNode extends d3.SimulationNodeDatum {
  id: string;
  type: 'green';
  companyName: string;
  logoUrl: string;
  topicName: string;
  r: number;
  absorbed: boolean;
  linkedBlobId?: string;
  /** Highest similarity score seen so far — used to prefer the best-matching blue blob */
  bestSimilarity?: number;
}

export interface GhostNode extends d3.SimulationNodeDatum {
  id: string;
  type: 'ghost-backlog' | 'ghost-partner' | 'ghost-trend';
  name: string;
  r: number;
}

export interface RedStarNode extends d3.SimulationNodeDatum {
  id: string;
  type: 'red-star';
  eventNumber: number;
  topicName: string;
  r: number;
  isActive: boolean;
  orbiting?: string;
  orbitAngle?: number;
}

export type SimNode = BlueBlobNode | GreenBlobNode | GhostNode | RedStarNode;

export interface SimLink {
  source: string | SimNode;
  target: string | SimNode;
  strength: number;
}
