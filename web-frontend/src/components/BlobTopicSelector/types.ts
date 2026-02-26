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

export interface BacklogItem {
  title: string;
  /** Stable slug identifier — present for all backlog topics from the organizer's topic table. */
  topicCode: string;
  /**
   * Staleness score 0–100 (all backlog items >= 83).
   * Used to size ghost blobs: higher = more overdue = bigger.
   */
  stalenessScore: number;
}

export interface TopicSessionData {
  partnerTopics: PartnerTopicEntry[];
  pastEvents: PastEventEntry[];
  organizerBacklog: BacklogItem[];
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

export interface AbsorbedRedStar {
  eventNumber: number;
  topicName: string;
  /** Orbit state — set on absorption, updated every tick */
  orbitAngle: number;
  orbitRadius: number;
  orbitSpeed: number;
  /** Slow virus spin (degrees per tick, positive or negative). */
  rotationAngle: number;
  rotationSpeed: number;
}

export interface BlueBlobNode extends d3.SimulationNodeDatum {
  id: string;
  type: 'blue';
  name: string;
  r: number;
  /** Original radius derived from topic-name length; r grows with absorbed logos. */
  baseR: number;
  /** Phase (radians) for sinusoidal gelée wobble; advanced each D3 tick. */
  wobblePhase: number;
  /** Advance rate for wobblePhase per tick — randomized so blobs never sync. */
  wobbleSpeed: number;
  /**
   * Set when the blob was spawned from an organizer backlog ghost.
   * Absence means the topic is free-form and must be created before selecting for the event.
   */
  topicCode?: string;
  cluster?: string;
  similarityScore?: number;
  relatedPastEventNumbers?: number[];
  absorbedLogos: AbsorbedLogo[];
  absorbedRedStars: AbsorbedRedStar[];
}

export interface GreenBlobNode extends d3.SimulationNodeDatum {
  id: string;
  type: 'green';
  companyName: string;
  logoUrl: string;
  topicName: string;
  r: number;
  absorbed: boolean;
  /** Phase (radians) for sinusoidal gelée wobble; advanced each D3 tick. */
  wobblePhase: number;
  /** Advance rate for wobblePhase per tick — randomized so blobs never sync. */
  wobbleSpeed: number;
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
  /**
   * Topic titles submitted by this partner, keyed by cluster.
   * Used to show the attraction reason in the topic tree panel.
   */
  topicsByCluster: Record<string, string[]>;
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
  /** Present only for ghost-backlog nodes — the existing topic's stable code. */
  topicCode?: string;
  /** Present only for ghost-partner nodes — the company that submitted this topic. */
  companyName?: string;
}

export interface RedStarNode extends d3.SimulationNodeDatum {
  id: string;
  type: 'red-star';
  eventNumber: number;
  topicName: string;
  r: number;
  isActive: boolean;
  /** Set when auto-ignited: the blue blob ID this star is flying toward for absorption. */
  attractedToBlueId?: string;
  /** True once this red star has been absorbed into a blue blob. */
  absorbed?: boolean;
  orbiting?: string;
  orbitAngle?: number;
  /** Current rotation angle in degrees — drives the slow virus spin. */
  rotationAngle: number;
  /** Rotation speed in degrees per tick; positive = clockwise, negative = counter-clockwise. */
  rotationSpeed: number;
}

export type SimNode = BlueBlobNode | GreenBlobNode | GhostNode | RedStarNode;

export interface SimLink {
  source: string | SimNode;
  target: string | SimNode;
  strength: number;
}
