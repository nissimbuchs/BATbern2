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

export interface PartnerTopicItem {
  title: string;
  /** BatbernCluster name — e.g. "AI_ML", "SECURITY", "BUSINESS_OTHER" */
  cluster: string;
  voteCount: number;
  /** ISO instant string — used as recency proxy for attraction strength */
  createdAt: string;
}

export interface PartnerTopicEntry {
  companyName: string;
  logoUrl: string;
  topics: PartnerTopicItem[];
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
  /** Orbit state — set on absorption, updated every tick */
  orbitAngle: number;
  orbitRadius: number;
  orbitSpeed: number;
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
  /**
   * Precomputed per-cluster attraction strengths (0–1).
   * Built once at node creation from the company's topic submissions.
   * Key = BatbernCluster name; absent key = no attraction to that cluster.
   */
  clusterAttractions: Record<string, number>;
  /**
   * Active forceLink per cluster: clusterName → blueBlobId currently linked.
   * A green can simultaneously link to multiple blue blobs (one per cluster).
   */
  linkedBlobsByCluster: Record<string, string>;
}

export interface GhostNode extends d3.SimulationNodeDatum {
  id: string;
  type: 'ghost-backlog' | 'ghost-partner' | 'ghost-trend';
  name: string;
  r: number;
  /** Ghost orbit state — updated every tick while node is a ghost */
  ghostOrbitAngle: number;
  ghostOrbitRadius: number;
  ghostOrbitSpeed: number;
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
