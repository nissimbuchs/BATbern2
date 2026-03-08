/**
 * BlobTopicSelector — D3 physics blob canvas for topic selection
 * Story 10.4: Blob Topic Selector (Tasks 7–15)
 *
 * React+D3 pattern: D3 owns all SVG content inside the main <g> group.
 * React state drives structural changes (add/remove blobs, dialogs, input).
 * Tick updates are applied by D3 directly to the DOM — no React re-render per tick.
 */

import React, { useRef, useEffect, useState, useCallback } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import * as d3 from 'd3';
import { Alert, Box, Button, IconButton, Snackbar, TextField, Typography } from '@mui/material';
import {
  ChevronLeft,
  ChevronRight,
  FitScreen,
  MyLocation,
  VolumeOff,
  VolumeUp,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import type {
  SimNode,
  SimLink,
  BlueBlobNode,
  GreenBlobNode,
  GhostNode,
  RedStarNode,
  AbsorbedLogo,
  AbsorbedRedStar,
  TopicSessionData,
  TopicSimilarityResponse,
} from './types';
import { blobTopicService } from '@/services/blobTopicService';
import { topicService } from '@/services/topicService';
import AcceptTopicDialog from './AcceptTopicDialog';
import { useBlobSounds } from './useBlobSounds';

// ─── helpers ──────────────────────────────────────────────────────────────────

let _nodeCounter = 0;
const mkId = () => `node-${++_nodeCounter}`;

/**
 * Wrap text inside an SVG <text> element using <tspan> lines.
 * Lines are centered vertically around the element's y=0.
 * @param maxWidthPx  - available width in pixels
 * @param lineHeightPx - vertical distance between lines
 * @param charWidthPx  - estimated width per character (font-size dependent)
 */
function wrapSvgText(
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  textEl: d3.Selection<SVGTextElement, any, any, any>,
  text: string,
  maxWidthPx: number,
  lineHeightPx: number,
  charWidthPx: number
): void {
  textEl.text('');
  if (!text) return;
  const words = text.split(/\s+/);
  const lines: string[] = [];
  let current = '';
  for (const word of words) {
    const candidate = current ? `${current} ${word}` : word;
    if (candidate.length * charWidthPx > maxWidthPx && current) {
      lines.push(current);
      current = word;
    } else {
      current = candidate;
    }
  }
  if (current) lines.push(current);
  // Vertically center the block: first tspan offset to top of block, rest step down
  const totalHeight = (lines.length - 1) * lineHeightPx;
  lines.forEach((line, i) => {
    textEl
      .append('tspan')
      .attr('x', 0)
      .attr('dy', i === 0 ? -totalHeight / 2 : lineHeightPx)
      .text(line);
  });
}

function starPointsRelative(outer: number, inner: number, numPoints: number): string {
  const pts: string[] = [];
  for (let i = 0; i < numPoints * 2; i++) {
    const angle = (Math.PI / numPoints) * i - Math.PI / 2;
    const r = i % 2 === 0 ? outer : inner;
    pts.push(`${r * Math.cos(angle)},${r * Math.sin(angle)}`);
  }
  return pts.join(' ');
}

/**
 * Generate an organic gelée blob path string centred at (0,0).
 *
 * 8 Catmull-Rom control points are placed on a circle of radius `r`.
 * Each point's radius is perturbed by a small sinusoidal offset so the
 * outline wobbles like jelly.  `phase` is advanced by 0.018 rad/tick in
 * the D3 simulation loop, producing smooth continuous motion with no
 * extra requestAnimationFrame loop.
 *
 * Amplitude = 8% of r → noticeable but not cartoonish.
 * Stagger factor 0.9 rad between neighbours → asymmetric, organic shape.
 */
function blobPath(r: number, phase: number): string {
  const N = 8;
  // Two-layer animation, both at the same per-blob frequency so each blob
  // has its own clearly independent rhythm (driven by wobbleSpeed):
  //   breathe  — uniform in/out pulse:               ±6% of r
  //   wobble   — per-vertex deformation (staggered):  ±12% of r
  const breathe = r * 0.06 * Math.sin(phase);
  const wobbleAmp = r * 0.12;
  const pts: [number, number][] = Array.from({ length: N }, (_, i) => {
    const theta = (2 * Math.PI * i) / N;
    const localR = r + breathe + wobbleAmp * Math.sin(phase + i * 0.9);
    return [localR * Math.cos(theta), localR * Math.sin(theta)];
  });
  // d3.line with curveCatmullRomClosed produces a smooth closed curve through the 8 points.
  const gen = d3
    .line<[number, number]>()
    .x((p) => p[0])
    .y((p) => p[1])
    .curve(d3.curveCatmullRomClosed.alpha(0.5));
  return gen(pts) ?? '';
}

/** Pixels added to a blue blob's radius per absorbed company logo. */
const GROW_PER_LOGO = 6;
/** Pixels removed from a blue blob's radius per absorbed red star (= 1 × company logo penalty). */
const SHRINK_PER_RED_STAR = GROW_PER_LOGO;

/**
 * Generate ghost orbit state for a respawned or ejected ghost node.
 * Uses a random angle and a moderate orbit radius so the ghost drifts naturally.
 */
function makeRandomGhostOrbit() {
  const angle = Math.random() * Math.PI * 2;
  const radius = 300 + Math.random() * 200;
  const speed = (0.002 + Math.random() * 0.003) * (Math.random() < 0.5 ? 1 : -1);
  return {
    ghostOrbitAngle: angle,
    ghostOrbitRadius: radius,
    ghostOrbitSpeed: speed,
  };
}

// ─── component ────────────────────────────────────────────────────────────────

interface BlobTopicSelectorProps {
  eventCode: string;
  sessionData: TopicSessionData;
}

const BlobTopicSelector: React.FC<BlobTopicSelectorProps> = ({ eventCode, sessionData }) => {
  const { t } = useTranslation('organizer');
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  // D3 refs — not React state, no re-render on change
  const svgRef = useRef<SVGSVGElement>(null);
  const gRef = useRef<d3.Selection<SVGGElement, unknown, null, undefined> | null>(null);
  const simRef = useRef<d3.Simulation<SimNode, SimLink> | null>(null);
  const zoomRef = useRef<d3.ZoomBehavior<SVGSVGElement, unknown> | null>(null);
  const nodesRef = useRef<SimNode[]>([]);
  const linksRef = useRef<SimLink[]>([]);
  const mostRecentEventNumRef = useRef(0);
  const proximityTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const mergeHaloNodesRef = useRef<Set<string>>(new Set());
  const hoveredGhostRef = useRef<GhostNode | null>(null);
  /** IDs of nodes frozen during a drag so other blobs don't scatter */
  const frozenNodeIdsRef = useRef<Set<string>>(new Set());
  /** ID of the node currently being dragged — ghost orbit skips this node */
  const draggingNodeIdRef = useRef<string | null>(null);
  /** Green nodes currently in a fade transition — tick handler skips opacity updates for these */
  const fadingGreenIds = useRef<Set<string>>(new Set());
  /** ID of the currently selected blue blob — shown with a yellow ring; Delete/Backspace removes it */
  const selectedBlobIdRef = useRef<string | null>(null);
  /** Which ghost types are currently visible — read by the tick handler without stale closure */
  const visibleGhostTypesRef = useRef<Set<string>>(
    new Set(['ghost-backlog', 'ghost-partner', 'ghost-trend'])
  );
  /** Red star IDs currently mid-growth animation — tick handler skips polygon points for these */
  const growingRedIds = useRef<Set<string>>(new Set());
  /** Stable ref so the D3 tick handler (captured once at mount) can call the latest absorb fn. */
  const absorbRedStarIntoBlueFnRef = useRef<(red: RedStarNode, blue: BlueBlobNode) => void>(
    () => {}
  );

  // React state — only for UI elements that need re-renders
  const [showInput, setShowInput] = useState(false);
  const [inputValue, setInputValue] = useState('');
  const [blueBlobIds, setBlueBlobIds] = useState<string[]>([]);
  const [acceptBlob, setAcceptBlob] = useState<BlueBlobNode | null>(null);
  const [mostRecentEventNum, setMostRecentEventNum] = useState(0);
  const [acceptError, setAcceptError] = useState<string | null>(null);
  const [treeSummary, setTreeSummary] = useState<
    {
      id: string;
      name: string;
      companies: { name: string; score: number | null; reason: string | null }[];
      pastEvents: { eventNumber: number; topicName: string }[];
    }[]
  >([]);
  const [panelOpen, setPanelOpen] = useState(true);
  const [visibleGhostTypes, setVisibleGhostTypes] = useState<Set<string>>(
    new Set(['ghost-backlog', 'ghost-partner', 'ghost-trend'])
  );

  // ─── SOUND EFFECTS ────────────────────────────────────────────────────────
  const sounds = useBlobSounds();
  /** Stable ref so D3 event handlers (captured once) always call the latest sounds. */
  const soundsRef = useRef(sounds);
  useEffect(() => {
    soundsRef.current = sounds;
  }, [sounds]);

  const hasOrbitingRed = nodesRef.current.some(
    (n) => n.type === 'red-star' && (n as RedStarNode).orbiting === acceptBlob?.id
  );

  // ─── INIT SIMULATION ──────────────────────────────────────────────────────

  useEffect(() => {
    if (!svgRef.current) return;

    const svg = d3.select(svgRef.current);
    const w = window.innerWidth;
    const h = window.innerHeight;

    svg.selectAll('*').remove();

    // Dark navy background — click deselects any selected blue blob.
    // On mobile (no keyboard), tapping empty space opens the floating input (AC 10).
    // D3 zoom suppresses the click event after a pan drag, so this only fires on genuine taps.
    svg
      .append('rect')
      .attr('width', w)
      .attr('height', h)
      .attr('fill', '#0d1b2a')
      .on('click', () => {
        if (selectedBlobIdRef.current) {
          gRef.current
            ?.selectAll<SVGGElement, BlueBlobNode>('.blue-blob-group')
            .filter((d) => d.id === selectedBlobIdRef.current)
            .each(function () {
              d3.select(this).select('path.blob-shape').attr('filter', null);
            });
          selectedBlobIdRef.current = null;
          return; // deselected a blob — don't also open the input
        }
        // Open floating input on empty-canvas tap/click (mobile-friendly)
        setShowInput(true);
      });

    // Defs for filters
    const defs = svg.append('defs');
    const redGlowFilter = defs.append('filter').attr('id', 'red-glow');
    redGlowFilter
      .append('feGaussianBlur')
      .attr('in', 'SourceGraphic')
      .attr('stdDeviation', '4')
      .attr('result', 'coloredBlur');
    const feMerge = redGlowFilter.append('feMerge');
    feMerge.append('feMergeNode').attr('in', 'coloredBlur');
    feMerge.append('feMergeNode').attr('in', 'SourceGraphic');

    // Gold halo filter — SourceAlpha + feFlood so the glow is vivid gold regardless of source color
    const goldGlowFilter = defs
      .append('filter')
      .attr('id', 'gold-glow')
      .attr('x', '-60%')
      .attr('y', '-60%')
      .attr('width', '220%')
      .attr('height', '220%');
    goldGlowFilter
      .append('feGaussianBlur')
      .attr('in', 'SourceAlpha')
      .attr('stdDeviation', '10')
      .attr('result', 'blur');
    goldGlowFilter
      .append('feFlood')
      .attr('flood-color', '#ffd700')
      .attr('flood-opacity', '1')
      .attr('result', 'color');
    goldGlowFilter
      .append('feComposite')
      .attr('in', 'color')
      .attr('in2', 'blur')
      .attr('operator', 'in')
      .attr('result', 'goldHalo');
    const goldMerge = goldGlowFilter.append('feMerge');
    goldMerge.append('feMergeNode').attr('in', 'goldHalo');
    goldMerge.append('feMergeNode').attr('in', 'SourceGraphic');

    // Main group for zoom/pan
    const g = svg.append('g').attr('class', 'main-group');
    gRef.current = g as d3.Selection<SVGGElement, unknown, null, undefined>;

    // Zoom behavior
    const zoom = d3
      .zoom<SVGSVGElement, unknown>()
      .scaleExtent([0.1, 4])
      .on('zoom', (event) => {
        g.attr('transform', event.transform.toString());
      });
    zoomRef.current = zoom;
    svg.call(zoom);

    // Force simulation
    const sim = d3
      .forceSimulation<SimNode>(nodesRef.current)
      .force('charge', d3.forceManyBody<SimNode>().strength(-30))
      .force(
        'collide',
        // Red stars have zero collision radius — they are viruses that pass through defences.
        // Giving them a non-zero radius caused absorbed-but-fading stars (fixed near the blob)
        // to block subsequent stars from reaching the absorption threshold.
        d3.forceCollide<SimNode>().radius((d) => (d.type === 'red-star' ? 0 : d.r + 5))
      )
      .force('center', d3.forceCenter<SimNode>(w / 2, h / 2).strength(0.01))
      .force(
        'link',
        d3
          .forceLink<SimNode, SimLink>(linksRef.current)
          .id((d) => d.id)
          .strength((l) => (l as SimLink & { strength: number }).strength ?? 0)
      )
      .velocityDecay(0.65) // higher friction → blobs slow down much faster (default 0.4)
      .alphaMin(0) // keep tick loop alive for ghost orbit animation
      .on('tick', () => {
        if (!gRef.current) return;
        tickHandler(gRef.current);
      });

    simRef.current = sim;

    return () => {
      sim.stop();
    };
  }, []);

  // ─── INIT NODES FROM SESSION DATA ─────────────────────────────────────────

  useEffect(() => {
    if (!sessionData) return;

    const w = window.innerWidth;
    const h = window.innerHeight;

    // Use the highest event number BELOW the current event as the recency anchor.
    // Using codeNum directly breaks when test/future events have a large gap (e.g., BATbern73 while
    // real events only go up to 57) — the 12-event window would exclude all past events.
    const codeNum = parseInt(eventCode.replace(/\D/g, ''), 10);
    const previousEvents = isNaN(codeNum)
      ? sessionData.pastEvents
      : sessionData.pastEvents.filter((e) => e.eventNumber < codeNum);
    const maxEventNum =
      previousEvents.length > 0
        ? Math.max(...previousEvents.map((e) => e.eventNumber))
        : isNaN(codeNum)
          ? 0
          : codeNum;
    mostRecentEventNumRef.current = maxEventNum;
    setMostRecentEventNum(maxEventNum);

    const newNodes: SimNode[] = [];

    // Golden-angle phyllotaxis layout for ghost nodes — maximally dispersed initial positions.
    // Each ghost also gets an orbit radius + speed so it drifts around the canvas center.
    const GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5)); // ≈ 137.5° in radians
    const cx = w / 2;
    const cy = h / 2;
    let ghostOrbitIdx = 0;
    const makeGhostOrbit = () => {
      const i = ghostOrbitIdx++;
      const angle = i * GOLDEN_ANGLE;
      const ring = Math.floor(i / 10); // 10 ghosts per ring
      const radius = 320 + ring * 120;
      const speed = ((0.0025 + (i % 9) * 0.0004) / 8) * (i % 2 === 0 ? 1 : -1); // alternate CW/CCW
      return {
        ghostOrbitAngle: angle,
        ghostOrbitRadius: radius,
        ghostOrbitSpeed: speed,
        x: cx + radius * Math.cos(angle),
        y: cy + radius * Math.sin(angle),
      };
    };

    // Backlog ghosts (white) — radius driven by staleness score (83–100 → 28–40px)
    // Guard against old cached API responses where items were plain strings
    sessionData.organizerBacklog.forEach((item, i) => {
      const title = typeof item === 'string' ? item : item.title;
      const topicCode = typeof item === 'string' ? undefined : item.topicCode;
      const staleness = typeof item === 'string' ? 83 : (item.stalenessScore ?? 83);
      if (!title) return;
      const r = Math.round(28 + ((staleness - 83) / 17) * 12);
      const orb = makeGhostOrbit();
      newNodes.push({
        id: `ghost-backlog-${i}`,
        type: 'ghost-backlog',
        name: title,
        topicCode,
        r,
        ...orb,
      } as GhostNode);
    });

    // Partner ghosts — one per partner topic submission (up to 3 per partner).
    // Green blobs are no longer pre-created; they spawn dynamically when a blue topic appears
    // in the same cluster as the ghost.
    sessionData.partnerTopics.slice(0, 20).forEach((partner, pi) => {
      partner.topics.slice(0, 3).forEach((topic, ti) => {
        const orb = makeGhostOrbit();
        const r = Math.max(28, Math.min(55, 28 + topic.voteCount * 4));
        newNodes.push({
          id: `ghost-partner-${pi}-${ti}`,
          type: 'ghost-partner',
          name: topic.title,
          companyName: partner.companyName,
          logoUrl: partner.logoUrl,
          cluster: topic.cluster,
          r,
          ...orb,
        } as GhostNode);
      });
    });

    // Trend ghosts (gold shimmer)
    sessionData.trendingTopics.forEach((topic, i) => {
      const orb = makeGhostOrbit();
      newNodes.push({
        id: `ghost-trend-${i}`,
        type: 'ghost-trend',
        name: topic,
        r: 30,
        ...orb,
      } as GhostNode);
    });

    // Red star nodes (past events) — dormant constellation
    sessionData.pastEvents.forEach((evt) => {
      newNodes.push({
        id: `red-star-${evt.eventNumber}`,
        type: 'red-star',
        eventNumber: evt.eventNumber,
        topicName: evt.topicName,
        r: 42,
        isActive: false,
        rotationAngle: Math.random() * 360,
        rotationSpeed: (Math.random() < 0.5 ? 1 : -1) * (0.2 + Math.random() * 0.6),
        x: 100 + Math.random() * (w - 200),
        y: 100 + Math.random() * (h - 200),
      } as RedStarNode);
    });

    nodesRef.current = newNodes;

    if (simRef.current) {
      simRef.current.nodes(nodesRef.current);
      simRef.current.alpha(1).restart();
    }

    renderAll();
  }, [sessionData]);

  // ─── BLOB RESIZE ──────────────────────────────────────────────────────────

  /**
   * Recompute a blue blob's radius from its current absorbed-logo count and
   * smoothly animate the SVG circle to the new size.
   */
  const resizeBlue = useCallback((blue: BlueBlobNode) => {
    // Minimum = half the blob's natural radius (keeps label and orbiting items inside).
    // baseR is always ≥ 40, so minimum is always ≥ 20 — in practice ≥ 20–50 depending on name length.
    const minR = Math.round(blue.baseR * 0.5);
    blue.r = Math.max(
      minR,
      Math.min(
        140,
        blue.baseR +
          blue.absorbedLogos.length * GROW_PER_LOGO -
          blue.absorbedRedStars.length * SHRINK_PER_RED_STAR
      )
    );
    // Blob shape is a gelée path updated every tick — no explicit SVG update needed here.
    // Wake the simulation so collide force reacts to the new radius
    simRef.current?.alpha(0.15).restart();
  }, []);

  // ─── TREE PANEL SYNC ──────────────────────────────────────────────────────

  /** Snapshot the current blue-blob → absorbed-companies tree into React state */
  const syncTreeSummary = useCallback(() => {
    setTreeSummary(
      nodesRef.current
        .filter((n) => n.type === 'blue')
        .map((n) => {
          const blue = n as BlueBlobNode;
          return {
            id: blue.id,
            name: blue.name,
            companies: blue.absorbedLogos.map((l) => {
              // Dynamically spawned greens are removed from nodesRef on absorption;
              // read score/reason from the snapshot stored at absorption time.
              const green = nodesRef.current.find(
                (g) => g.type === 'green' && (g as GreenBlobNode).companyName === l.companyName
              ) as GreenBlobNode | undefined;
              const score =
                blue.cluster && green
                  ? (green.clusterAttractions[blue.cluster] ?? null)
                  : blue.cluster
                    ? 1.0 // dynamic greens always have full attraction to their triggering cluster
                    : null;
              const reason =
                blue.cluster && green?.topicsByCluster
                  ? (green.topicsByCluster[blue.cluster] ?? []).join(', ') || null
                  : (l.ghostSnapshot?.name ?? null);
              return { name: l.companyName, score, reason };
            }),
            pastEvents: blue.absorbedRedStars.map((r) => ({
              eventNumber: r.eventNumber,
              topicName: r.topicName,
            })),
          };
        })
    );
  }, []);

  // ─── RED STAR ABSORPTION ──────────────────────────────────────────────────

  /**
   * Absorb an ignited red star into a blue blob.
   * - Fades the flying red star out on the canvas.
   * - Adds an AbsorbedRedStar entry to the blue blob (rendered as a small swimming star polygon).
   * - Shrinks the blue blob by SHRINK_PER_RED_STAR to signal "this topic has recent history".
   */
  const absorbRedStarIntoBlue = useCallback(
    (red: RedStarNode, blue: BlueBlobNode) => {
      if (red.absorbed) return; // guard against double-call from concurrent ticks
      soundsRef.current.playSting();
      red.absorbed = true;
      red.attractedToBlueId = undefined;
      red.isActive = false;
      red.fx = red.x ?? 0;
      red.fy = red.y ?? 0;
      // Fade the flying star out
      gRef.current
        ?.selectAll<SVGGElement, RedStarNode>('.red-star-group')
        .filter((d) => d.id === red.id)
        .transition()
        .duration(500)
        .attr('opacity', 0)
        .on('end', () => {
          red.fx = null;
          red.fy = null;
        });
      // Add swimming red star inside the blue blob
      if (!blue.absorbedRedStars.some((r) => r.eventNumber === red.eventNumber)) {
        blue.absorbedRedStars.push({
          eventNumber: red.eventNumber,
          topicName: red.topicName,
          orbitAngle: Math.random() * Math.PI * 2,
          orbitRadius: Math.max(10, Math.min(22, blue.r * 0.28)),
          orbitSpeed: (0.004 + Math.random() * 0.005) * (Math.random() < 0.5 ? 1 : -1),
          rotationAngle: Math.random() * 360,
          rotationSpeed: (Math.random() < 0.5 ? 1 : -1) * (0.3 + Math.random() * 0.7),
        });
        resizeBlue(blue);
      }
      syncTreeSummary();
    },
    [resizeBlue, syncTreeSummary]
  );

  // Keep the stable ref in sync so the D3 tick handler (captured at mount) always calls latest.
  useEffect(() => {
    absorbRedStarIntoBlueFnRef.current = absorbRedStarIntoBlue;
  }, [absorbRedStarIntoBlue]);

  // ─── GHOST VISIBILITY TOGGLE ──────────────────────────────────────────────

  const toggleGhostType = useCallback((type: string) => {
    setVisibleGhostTypes((prev) => {
      const next = new Set(prev);
      if (next.has(type)) next.delete(type);
      else next.add(type);
      visibleGhostTypesRef.current = next;
      return next;
    });
  }, []);

  // ─── TICK HANDLER (D3 mutates DOM directly) ───────────────────────────────

  const tickHandler = useCallback((g: d3.Selection<SVGGElement, unknown, null, undefined>) => {
    // Ghost orbit — advance each ghost's angle and apply a soft spring toward the orbit target.
    // Using a spring (instead of hard fx/fy pinning) lets D3's forceCollide and forceManyBody
    // act on ghost nodes so they repel each other and other blobs, while still drifting
    // along their orbital paths.  Skips the currently-dragged node.
    const cx = window.innerWidth / 2;
    const cy = window.innerHeight / 2;
    const ORBIT_SPRING = 0.15;
    nodesRef.current.forEach((node) => {
      if (
        node.type !== 'ghost-backlog' &&
        node.type !== 'ghost-partner' &&
        node.type !== 'ghost-trend'
      )
        return;
      if (node.id === draggingNodeIdRef.current) return;
      const ghost = node as GhostNode;
      ghost.ghostOrbitAngle += ghost.ghostOrbitSpeed;
      const targetX = cx + ghost.ghostOrbitRadius * Math.cos(ghost.ghostOrbitAngle);
      const targetY = cy + ghost.ghostOrbitRadius * Math.sin(ghost.ghostOrbitAngle);
      // Free the node from hard pinning so D3 forces can act on it.
      ghost.fx = null;
      ghost.fy = null;
      // Spring pull toward orbit target — keeps ghosts roughly on orbit while allowing repulsion.
      ghost.vx = (ghost.vx ?? 0) + (targetX - (ghost.x ?? targetX)) * ORBIT_SPRING;
      ghost.vy = (ghost.vy ?? 0) + (targetY - (ghost.y ?? targetY)) * ORBIT_SPRING;
    });

    // Custom green attraction — alpha-independent so blobs keep moving until absorbed,
    // regardless of how cool the simulation has become.
    nodesRef.current.forEach((node) => {
      if (node.type !== 'green') return;
      const green = node as GreenBlobNode;
      if (green.absorbed || green.fx != null) return;
      Object.entries(green.linkedBlobsByCluster).forEach(([cluster, blueBlobId]) => {
        const blue = nodesRef.current.find((b) => b.id === blueBlobId) as BlueBlobNode | undefined;
        if (!blue) return;
        const dx = (blue.x ?? 0) - (green.x ?? 0);
        const dy = (blue.y ?? 0) - (green.y ?? 0);
        const dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 1) return;
        const strength = green.clusterAttractions[cluster] ?? 0.1;
        const pull = strength * 1.2; // px/tick² — tunable speed
        green.vx = (green.vx ?? 0) + (dx / dist) * pull;
        green.vy = (green.vy ?? 0) + (dy / dist) * pull;
      });
    });

    nodesRef.current.forEach((node) => {
      // Orbit: red stars orbiting blue blobs
      if (node.type === 'red-star') {
        const red = node as RedStarNode;
        if (red.orbiting) {
          const blue = nodesRef.current.find((n) => n.id === red.orbiting) as
            | BlueBlobNode
            | undefined;
          if (blue) {
            red.orbitAngle = (red.orbitAngle ?? 0) + 0.007;
            const orbitRadius = blue.r + red.r + 20;
            red.x = (blue.x ?? 0) + orbitRadius * Math.cos(red.orbitAngle);
            red.y = (blue.y ?? 0) + orbitRadius * Math.sin(red.orbitAngle);
            red.fx = red.x;
            red.fy = red.y;
          }
        }
      }
    });

    // Update positions of all node groups
    g.selectAll<SVGGElement, SimNode>('.node-group').attr(
      'transform',
      (d) => `translate(${d.x ?? 0},${d.y ?? 0})`
    );

    // Advance gelée wobble for blue and green blobs — each blob has its own phase offset
    // so they pulsate independently.  0.018 rad/tick ≈ one full wobble cycle every ~6 seconds.
    g.selectAll<SVGGElement, BlueBlobNode>('.blue-blob-group').each(function (d) {
      d.wobblePhase += d.wobbleSpeed;
      d3.select(this)
        .select<SVGPathElement>('path.blob-shape')
        .attr('d', blobPath(d.r, d.wobblePhase));
    });
    g.selectAll<SVGGElement, GreenBlobNode>('.green-blob-group')
      .filter((d) => !d.absorbed)
      .each(function (d) {
        d.wobblePhase += d.wobbleSpeed;
        d3.select(this)
          .select<SVGPathElement>('path.blob-shape')
          .attr('d', blobPath(d.r, d.wobblePhase));
      });

    // Ghost visibility — show/hide based on toggle state (opacity 0 = hidden but orbit continues)
    g.selectAll<SVGGElement, SimNode>('.ghost-group')
      .attr('opacity', (d) => (visibleGhostTypesRef.current.has(d.type) ? 1 : 0))
      .attr('pointer-events', (d) => (visibleGhostTypesRef.current.has(d.type) ? 'auto' : 'none'));

    // Hide entire green blob group — skip nodes currently in a fade transition
    g.selectAll<SVGGElement, GreenBlobNode>('.green-blob-group')
      .filter((d) => !fadingGreenIds.current.has(d.id))
      .attr('opacity', (d) => (d.absorbed ? 0 : 1));

    // Update attraction reason labels — shown below each green blob it is pulled toward a blue
    g.selectAll<SVGGElement, GreenBlobNode>('.green-blob-group')
      .filter((d) => !d.absorbed && !fadingGreenIds.current.has(d.id))
      .each(function (d) {
        // Show the partner's own submitted topic titles (same as tree panel)
        const lines = Object.keys(d.linkedBlobsByCluster)
          .map((cluster) => (d.topicsByCluster[cluster] ?? []).join(', '))
          .filter((s) => s.length > 0)
          .slice(0, 2);
        const grp = d3.select(this);
        grp
          .select('.green-atl-0')
          .attr('opacity', lines.length >= 1 ? 1 : 0)
          .text(lines.length >= 1 ? lines[0] : '');
        grp
          .select('.green-atl-1')
          .attr('opacity', lines.length >= 2 ? 1 : 0)
          .text(lines.length >= 2 ? lines[1] : '');
      });

    // Update red star virus body — rotation, opacity, glow, and size
    g.selectAll<SVGGElement, RedStarNode>('.red-star-group').each(function (d) {
      const grp = d3.select(this);
      // Advance slow spin; each star has its own speed and direction
      d.rotationAngle += d.rotationSpeed;
      const virusBody = grp.select<SVGGElement>('.virus-body');
      virusBody.attr('transform', `rotate(${d.rotationAngle})`);
      // Only snap polygon points and core radius when not mid-animation
      if (!growingRedIds.current.has(d.id)) {
        const visR = d.isActive ? d.r : d.r * 0.3;
        virusBody
          .select<SVGPolygonElement>('polygon')
          .attr('points', starPointsRelative(visR, visR * 0.42, 5));
        virusBody.select<SVGCircleElement>('.virus-core').attr('r', visR * 0.65);
      }
      virusBody
        .attr('opacity', d.isActive ? 1.0 : 0.15)
        .attr('filter', d.isActive ? 'url(#red-glow)' : null);
      grp.select<SVGTextElement>('text').attr('font-size', d.isActive ? '11px' : '5px');
    });

    // Red star attraction — ignited stars fly toward their target blue blob and get absorbed
    nodesRef.current.forEach((node) => {
      if (node.type !== 'red-star') return;
      const red = node as RedStarNode;
      if (!red.attractedToBlueId || red.absorbed || red.orbiting) return;
      const blue = nodesRef.current.find((n) => n.id === red.attractedToBlueId) as
        | BlueBlobNode
        | undefined;
      if (!blue) return;
      const dx = (blue.x ?? 0) - (red.x ?? 0);
      const dy = (blue.y ?? 0) - (red.y ?? 0);
      const dist = Math.sqrt(dx * dx + dy * dy);
      if (dist < red.r + blue.r + 12) {
        // +12 clears the forceCollide padding (radius + 5 per node = +10 total) so absorption
        // triggers before the collide force can push the blue blob away.
        absorbRedStarIntoBlueFnRef.current(red, blue);
      } else {
        const pull = 4; // px/tick² — slow dramatic approach; terminal v ≈ 2 px/tick at decay 0.65
        red.vx = (red.vx ?? 0) + (dx / dist) * pull;
        red.vy = (red.vy ?? 0) + (dy / dist) * pull;
      }
    });

    // Update absorbed logos inside blue blobs — scattered orbit positions.
    // Company logos (ghost-partner) orbit as <image> elements.
    // Organizer spinners (ghost-backlog) orbit as <g> elements with the BATbern spinner.
    // Trend items absorb silently (no orbit element).
    g.selectAll<SVGGElement, BlueBlobNode>('.blue-blob-group').each(function (d) {
      const grp = d3.select(this);

      // ── Organizer (backlog) spinner orbit ──────────────────────────────────
      const orgLogos = d.absorbedLogos.filter((l) => l.sourceGhostType === 'ghost-backlog');
      const orgSpinners = grp
        .selectAll<SVGGElement, AbsorbedLogo>('.absorbed-org-spinner')
        .data(orgLogos, (l) => l.ghostSnapshot.name);

      const enteredOrg = orgSpinners
        .enter()
        .append('g')
        .attr('class', 'absorbed-org-spinner')
        .attr('opacity', 0)
        .style('cursor', 'pointer')
        .on('click', (event, logo) => {
          event.stopPropagation();
          d3.select(event.currentTarget as SVGGElement)
            .transition()
            .duration(1200)
            .attr('opacity', 0)
            .on('end', () => {
              d.absorbedLogos = d.absorbedLogos.filter(
                (l) => l.ghostSnapshot.name !== logo.ghostSnapshot.name
              );
              resizeBlue(d as BlueBlobNode);
              syncTreeSummary();
              const snap = logo.ghostSnapshot;
              const ejectDist = d.r + (snap.r ?? 38) + 15;
              const orb = makeRandomGhostOrbit();
              const ghost: GhostNode = {
                id: `${logo.sourceGhostType}-ejected-${Date.now()}`,
                type: logo.sourceGhostType,
                name: snap.name,
                r: snap.r ?? 38,
                topicCode: snap.topicCode,
                companyName: snap.companyName,
                logoUrl: snap.logoUrl,
                cluster: snap.cluster,
                x: (d.x ?? 0) + ejectDist * Math.cos(logo.orbitAngle),
                y: (d.y ?? 0) + ejectDist * Math.sin(logo.orbitAngle),
                ...orb,
              };
              nodesRef.current.push(ghost);
              simRef.current?.nodes(nodesRef.current);
              renderAll();
              gRef.current
                ?.selectAll<SVGGElement, GhostNode>('.ghost-group')
                .filter((gd) => gd.id === ghost.id)
                .attr('opacity', 0)
                .transition()
                .duration(1200)
                .attr('opacity', 1);
              simRef.current?.alpha(0.2).restart();
            });
        })
        .call((entered) => {
          // Render a small BATbern spinner (same paths as ghost, scaled down to orbit size)
          const s = 0.25; // scale 100x100 viewBox to ~25px
          entered
            .append('g')
            .attr('class', 'bat-org-spinner')
            .attr('transform', `translate(-12,-12) scale(${s})`)
            .attr('pointer-events', 'none')
            .call((spinnerG) => {
              spinnerG
                .append('g')
                .attr('class', 'ghost-org-arrow ghost-org-arrow-1')
                .attr('fill', 'rgba(52,152,219,0.9)')
                .append('path')
                .attr(
                  'd',
                  'M35.822,21.061c8.877,0.261,16.278,3.112,22.344,9.105c1.02,1.007,1.862,1.383,3.196,0.678  c1.135-0.6,2.4-0.948,3.584-1.46c1.17-0.506,1.687-0.421,1.453,1.086c-0.744,4.796-1.39,9.607-2.081,14.411  c-0.306,2.128-0.647,4.251-0.936,6.381c-0.143,1.055-0.554,1.309-1.425,0.62c-5.598-4.425-11.193-8.855-16.804-13.262  c-1.002-0.787-0.533-1.142,0.32-1.479c0.972-0.384,1.941-0.774,2.907-1.172c0.489-0.202,1.214-0.249,1.232-0.898  c0.014-0.504-0.622-0.706-1.017-0.981c-7.132-4.97-17.108-5.073-24.534-0.159c-6.465,4.279-9.702,10.438-10.144,18.109  c-0.18,3.131-1.942,5.125-4.643,5.087c-2.693-0.038-4.588-2.316-4.527-5.442c0.299-15.337,12.257-28.445,27.624-30.27  C33.651,21.262,34.936,21.151,35.822,21.061z'
                );
              spinnerG
                .append('g')
                .attr('class', 'ghost-org-arrow ghost-org-arrow-2')
                .attr('fill', 'rgba(26,111,168,0.9)')
                .append('path')
                .attr(
                  'd',
                  'M63.149,76.87c-7.916-0.206-15.29-3.125-21.373-9.075c-1.033-1.01-1.879-1.349-3.197-0.648  c-1.079,0.573-2.291,0.888-3.415,1.384c-1.282,0.565-1.851,0.323-1.622-1.184c0.665-4.373,1.302-8.749,1.945-13.125  c0.33-2.248,0.654-4.498,0.97-6.748c0.296-2.105,0.518-2.219,2.137-0.944c5.268,4.146,10.511,8.324,15.794,12.452  c1.139,0.89,1.436,1.475-0.233,1.994c-0.935,0.291-1.812,0.768-2.741,1.083c-1.481,0.503-1.182,1.077-0.141,1.764  c5.296,3.493,11.052,4.59,17.262,3.319c9.38-1.92,17.277-10.642,17.434-20.875c0.05-3.239,1.767-5.249,4.389-5.391  c2.683-0.145,4.711,1.851,4.785,4.709c0.337,13.023-8.736,25.494-21.634,29.705C70.331,76.326,67.061,76.839,63.149,76.87z'
                );
            });
        });
      enteredOrg.transition().duration(1500).attr('opacity', 1);
      orgSpinners.exit().remove();

      // Every tick: advance orbit angle and reposition organizer spinners
      grp.selectAll<SVGGElement, AbsorbedLogo>('.absorbed-org-spinner').each(function (l) {
        l.orbitAngle += l.orbitSpeed;
        d3.select(this).attr(
          'transform',
          `translate(${l.orbitRadius * Math.cos(l.orbitAngle)},${l.orbitRadius * Math.sin(l.orbitAngle)})`
        );
      });

      // ── Company logo orbit ─────────────────────────────────────────────────
      const companyLogos = d.absorbedLogos.filter((l) => l.sourceGhostType === 'ghost-partner');
      const logos = grp
        .selectAll<SVGImageElement, AbsorbedLogo>('.absorbed-logo')
        .data(companyLogos, (l) => l.companyName);

      // Enter: add new logo images with click-to-eject handler; fade in on appear
      const enteredLogos = logos
        .enter()
        .append('image')
        .attr('class', 'absorbed-logo')
        .attr('width', 24)
        .attr('height', 24)
        .attr('href', (l) => l.logoUrl)
        .attr('opacity', 0) // start invisible; transition below fades in
        .style('cursor', 'pointer')
        .on('click', (event, logo) => {
          event.stopPropagation();
          // Fade out the logo, then respawn the original ghost node at the eject position
          d3.select(event.currentTarget as SVGImageElement)
            .transition()
            .duration(1200)
            .attr('opacity', 0)
            .on('end', () => {
              d.absorbedLogos = d.absorbedLogos.filter((l) => l.companyName !== logo.companyName);
              resizeBlue(d as BlueBlobNode);
              syncTreeSummary();
              // Respawn as the original ghost type at the eject position
              const snap = logo.ghostSnapshot;
              const ejectDist = d.r + (snap.r ?? 38) + 15;
              const orb = makeRandomGhostOrbit();
              const ghost: GhostNode = {
                id: `${logo.sourceGhostType}-ejected-${Date.now()}`,
                type: logo.sourceGhostType,
                name: snap.name,
                r: snap.r ?? 38,
                topicCode: snap.topicCode,
                companyName: snap.companyName,
                logoUrl: snap.logoUrl,
                cluster: snap.cluster,
                x: (d.x ?? 0) + ejectDist * Math.cos(logo.orbitAngle),
                y: (d.y ?? 0) + ejectDist * Math.sin(logo.orbitAngle),
                ...orb,
              };
              nodesRef.current.push(ghost);
              simRef.current?.nodes(nodesRef.current);
              renderAll();
              // Fade the new ghost in
              gRef.current
                ?.selectAll<SVGGElement, GhostNode>('.ghost-group')
                .filter((gd) => gd.id === ghost.id)
                .attr('opacity', 0)
                .transition()
                .duration(1200)
                .attr('opacity', 1);
              simRef.current?.alpha(0.2).restart();
            });
        });
      // Logo fade-in: newly absorbed logos appear with a soft transition
      enteredLogos.transition().duration(1500).attr('opacity', 1);

      logos.exit().remove();

      // Every tick: advance orbit angle and reposition all logos
      grp.selectAll<SVGImageElement, AbsorbedLogo>('.absorbed-logo').each(function (l) {
        l.orbitAngle += l.orbitSpeed;
        d3.select(this)
          .attr('x', l.orbitRadius * Math.cos(l.orbitAngle) - 12)
          .attr('y', l.orbitRadius * Math.sin(l.orbitAngle) - 12);
      });

      // Absorbed red stars — small red star polygons swimming inside the blue blob
      const absorbedRedStarSel = grp
        .selectAll<SVGGElement, AbsorbedRedStar>('.absorbed-red-star-group')
        .data(d.absorbedRedStars, (r) => String(r.eventNumber));

      absorbedRedStarSel
        .enter()
        .append('g')
        .attr('class', 'absorbed-red-star-group')
        .attr('opacity', 0)
        .call((entered) => {
          // .absorbed-virus-body rotates each tick; spikes + core circle inside
          const vb = entered.append('g').attr('class', 'absorbed-virus-body');
          vb.append('polygon')
            .attr('points', starPointsRelative(7, 7 * 0.42, 5))
            .attr('fill', '#e53935')
            .attr('stroke', '#ff6b6b')
            .attr('stroke-width', 0.8)
            .attr('filter', 'url(#red-glow)');
          vb.append('circle')
            .attr('class', 'virus-core')
            .attr('r', 7 * 0.65)
            .attr('fill', '#e53935');
          // Number label stays outside the rotating group
          entered
            .append('text')
            .attr('text-anchor', 'middle')
            .attr('dominant-baseline', 'central')
            .attr('y', 0)
            .attr('font-size', '5px')
            .attr('font-weight', 'bold')
            .attr('fill', '#ffffff')
            .attr('pointer-events', 'none')
            .text((r) => String(r.eventNumber));
        })
        .transition()
        .duration(700)
        .attr('opacity', 1);

      absorbedRedStarSel.exit().remove();

      // Advance orbit and spin each tick
      grp.selectAll<SVGGElement, AbsorbedRedStar>('.absorbed-red-star-group').each(function (r) {
        r.orbitAngle += r.orbitSpeed;
        r.rotationAngle += r.rotationSpeed;
        const self = d3.select(this);
        self.attr(
          'transform',
          `translate(${r.orbitRadius * Math.cos(r.orbitAngle)},${r.orbitRadius * Math.sin(r.orbitAngle)})`
        );
        self
          .select<SVGGElement>('.absorbed-virus-body')
          .attr('transform', `rotate(${r.rotationAngle})`);
      });

      // Raise topic-name text above logos and red stars so it's never hidden
      grp.select('text').raise();
    });

    // Gold halo on selected blue blob
    g.selectAll<SVGGElement, BlueBlobNode>('.blue-blob-group').each(function (d) {
      const isSelected = d.id === selectedBlobIdRef.current;
      d3.select(this)
        .select<SVGPathElement>('path.blob-shape')
        .attr('filter', isSelected ? 'url(#gold-glow)' : null);
    });
  }, []);

  // ─── RENDER ALL NODES ─────────────────────────────────────────────────────

  const renderAll = useCallback(() => {
    const g = gRef.current;
    if (!g) return;

    const nodeGroups = g
      .selectAll<SVGGElement, SimNode>('.node-group')
      .data(nodesRef.current, (d) => d.id);

    nodeGroups.exit().remove();

    const entered = nodeGroups
      .enter()
      .append('g')
      .attr('class', 'node-group')
      .attr('transform', (d) => `translate(${d.x ?? 0},${d.y ?? 0})`);

    entered.each(function (d) {
      renderNode(d3.select(this) as d3.Selection<SVGGElement, SimNode, null, undefined>, d);
    });

    // Apply drag to all node groups
    g.selectAll<SVGGElement, SimNode>('.node-group').call(
      buildDragBehavior() as d3.DragBehavior<SVGGElement, SimNode, SimNode | d3.SubjectPosition>
    );

    // Green company blobs must always render above topic/ghost blobs
    g.selectAll<SVGGElement, GreenBlobNode>('.green-blob-group').raise();

    if (simRef.current) {
      simRef.current.nodes(nodesRef.current);
      (simRef.current.force('link') as d3.ForceLink<SimNode, SimLink> | null)?.links(
        linksRef.current
      );
      simRef.current.alpha(0.3).restart();
    }
  }, []);

  const renderNode = (
    group: d3.Selection<SVGGElement, SimNode, null, undefined>,
    node: SimNode
  ) => {
    switch (node.type) {
      case 'blue': {
        const blue = node as BlueBlobNode;
        group.attr('class', 'node-group blue-blob-group');
        group
          .append('path')
          .attr('class', 'blob-shape')
          .attr('d', blobPath(blue.r, blue.wobblePhase))
          .attr('fill', '#1976d2')
          .attr('opacity', 0)
          .transition()
          .duration(400)
          .attr('opacity', 1);
        wrapSvgText(
          group
            .append('text')
            .attr('text-anchor', 'middle')
            .attr('fill', 'white')
            .attr('font-size', '12px')
            .attr('pointer-events', 'none'),
          blue.name,
          blue.r * 1.6,
          14,
          6.5
        );
        group
          .style('cursor', 'pointer')
          .on('click', (event, d) => {
            event.stopPropagation();
            const blobId = (d as BlueBlobNode).id;
            const nowSelected = selectedBlobIdRef.current !== blobId;
            selectedBlobIdRef.current = nowSelected ? blobId : null;
            if (nowSelected) soundsRef.current.playKling();
            // Apply immediately — don't wait for the next simulation tick
            const groupEl = d3.select(event.currentTarget as SVGGElement);
            groupEl
              .select<SVGPathElement>('path.blob-shape')
              .attr('filter', nowSelected ? 'url(#gold-glow)' : null);
          })
          .on('dblclick', (_, d) => {
            setAcceptBlob(d as BlueBlobNode);
          });
        break;
      }
      case 'green': {
        const green = node as GreenBlobNode;
        group.attr('class', 'node-group green-blob-group');
        group
          .append('path')
          .attr('class', 'blob-shape green-blob')
          .attr('d', blobPath(green.r, green.wobblePhase))
          .attr('fill', '#2e7d32');
        if (green.logoUrl) {
          group
            .append('image')
            .attr('href', green.logoUrl)
            .attr('x', -16)
            .attr('y', -16)
            .attr('width', 32)
            .attr('height', 32)
            .attr('preserveAspectRatio', 'xMidYMid meet')
            .attr('pointer-events', 'none');
        }
        // Attraction reason labels — shown below the blob when pulled toward a blue blob
        group
          .append('text')
          .attr('class', 'green-atl-0')
          .attr('text-anchor', 'middle')
          .attr('y', green.r + 14)
          .attr('fill', 'rgba(144,238,144,0.9)')
          .attr('font-size', '8px')
          .attr('pointer-events', 'none')
          .attr('opacity', 0);
        group
          .append('text')
          .attr('class', 'green-atl-1')
          .attr('text-anchor', 'middle')
          .attr('y', green.r + 25)
          .attr('fill', 'rgba(144,238,144,0.75)')
          .attr('font-size', '8px')
          .attr('pointer-events', 'none')
          .attr('opacity', 0);
        break;
      }
      case 'ghost-backlog':
      case 'ghost-partner':
      case 'ghost-trend': {
        const ghost = node as GhostNode;
        const fillColor =
          node.type === 'ghost-backlog'
            ? '#ffffff'
            : node.type === 'ghost-partner'
              ? 'rgba(144, 238, 144, 0.8)'
              : 'rgba(255, 215, 0, 0.8)';
        const fillOpacity = node.type === 'ghost-backlog' ? 0.25 : 0.3;
        group.attr('class', 'node-group ghost-group');
        group
          .append('circle')
          .attr('class', 'blob-shape')
          .attr('r', ghost.r)
          .attr('fill', fillColor)
          .attr('opacity', fillOpacity);
        // BATbern spinner for organizer ghosts; company logo for partner ghosts
        if (node.type === 'ghost-backlog') {
          const s = ghost.r * 0.014; // scale 100x100 viewBox to ~70% of ghost diameter
          group
            .append('g')
            .attr('class', 'bat-org-spinner')
            .attr('transform', `translate(${-ghost.r * 0.7},${-ghost.r * 0.7 + s}) scale(${s})`)
            .attr('opacity', 0.5)
            .attr('pointer-events', 'none')
            .call((spinnerG) => {
              spinnerG
                .append('g')
                .attr('class', 'ghost-org-arrow ghost-org-arrow-1')
                .attr('fill', 'rgba(52,152,219,0.85)')
                .append('path')
                .attr(
                  'd',
                  'M35.822,21.061c8.877,0.261,16.278,3.112,22.344,9.105c1.02,1.007,1.862,1.383,3.196,0.678  c1.135-0.6,2.4-0.948,3.584-1.46c1.17-0.506,1.687-0.421,1.453,1.086c-0.744,4.796-1.39,9.607-2.081,14.411  c-0.306,2.128-0.647,4.251-0.936,6.381c-0.143,1.055-0.554,1.309-1.425,0.62c-5.598-4.425-11.193-8.855-16.804-13.262  c-1.002-0.787-0.533-1.142,0.32-1.479c0.972-0.384,1.941-0.774,2.907-1.172c0.489-0.202,1.214-0.249,1.232-0.898  c0.014-0.504-0.622-0.706-1.017-0.981c-7.132-4.97-17.108-5.073-24.534-0.159c-6.465,4.279-9.702,10.438-10.144,18.109  c-0.18,3.131-1.942,5.125-4.643,5.087c-2.693-0.038-4.588-2.316-4.527-5.442c0.299-15.337,12.257-28.445,27.624-30.27  C33.651,21.262,34.936,21.151,35.822,21.061z'
                );
              spinnerG
                .append('g')
                .attr('class', 'ghost-org-arrow ghost-org-arrow-2')
                .attr('fill', 'rgba(26,111,168,0.85)')
                .append('path')
                .attr(
                  'd',
                  'M63.149,76.87c-7.916-0.206-15.29-3.125-21.373-9.075c-1.033-1.01-1.879-1.349-3.197-0.648  c-1.079,0.573-2.291,0.888-3.415,1.384c-1.282,0.565-1.851,0.323-1.622-1.184c0.665-4.373,1.302-8.749,1.945-13.125  c0.33-2.248,0.654-4.498,0.97-6.748c0.296-2.105,0.518-2.219,2.137-0.944c5.268,4.146,10.511,8.324,15.794,12.452  c1.139,0.89,1.436,1.475-0.233,1.994c-0.935,0.291-1.812,0.768-2.741,1.083c-1.481,0.503-1.182,1.077-0.141,1.764  c5.296,3.493,11.052,4.59,17.262,3.319c9.38-1.92,17.277-10.642,17.434-20.875c0.05-3.239,1.767-5.249,4.389-5.391  c2.683-0.145,4.711,1.851,4.785,4.709c0.337,13.023-8.736,25.494-21.634,29.705C70.331,76.326,67.061,76.839,63.149,76.87z'
                );
            });
        } else if (node.type === 'ghost-partner' && ghost.logoUrl) {
          // Company logo — faint background image so topic text remains readable
          const imgSize = ghost.r * 0.85;
          group
            .append('image')
            .attr('class', 'ghost-company-logo')
            .attr('href', ghost.logoUrl)
            .attr('width', imgSize)
            .attr('height', imgSize)
            .attr('x', -imgSize / 2)
            .attr('y', -imgSize / 2)
            .attr('opacity', 0.25)
            .attr('pointer-events', 'none');
        }
        wrapSvgText(
          group
            .append('text')
            .attr('text-anchor', 'middle')
            .attr('fill', 'rgba(255,255,255,0.7)')
            .attr('font-size', '9px')
            .attr('pointer-events', 'none'),
          ghost.name,
          ghost.r * 1.6,
          11,
          5
        );
        // Company name — shown only on ghost-partner nodes, small, below the topic text
        if (node.type === 'ghost-partner' && ghost.companyName) {
          group
            .append('text')
            .attr('text-anchor', 'middle')
            .attr('y', ghost.r * 0.55)
            .attr('fill', 'rgba(255,255,255,0.45)')
            .attr('font-size', '7px')
            .attr('pointer-events', 'none')
            .text(
              ghost.companyName.length > 12
                ? ghost.companyName.slice(0, 11) + '…'
                : ghost.companyName
            );
        }
        group
          .style('cursor', 'pointer')
          .on('click', (_, d) => {
            spawnBlueFromGhost(d as GhostNode);
          })
          .on('mouseover', (_, d) => {
            hoveredGhostRef.current = d as GhostNode;
          })
          .on('mouseout', () => {
            hoveredGhostRef.current = null;
          });
        break;
      }
      case 'red-star': {
        const red = node as RedStarNode;
        group.attr('class', 'node-group red-star-group');
        const visR = red.isActive ? red.r : red.r * 0.3;
        // .virus-body rotates each tick; contains spikes (polygon) + core circle on top.
        // Overlaying a circle (r = 65% of outer) covers the star's concave inner regions,
        // leaving only the pointed spike tips exposed — classic virus silhouette.
        const virusBody = group
          .append('g')
          .attr('class', 'virus-body')
          .attr('transform', `rotate(${red.rotationAngle})`)
          .attr('opacity', red.isActive ? 1.0 : 0.15)
          .attr('filter', red.isActive ? 'url(#red-glow)' : null);
        virusBody
          .append('polygon')
          .attr('points', starPointsRelative(visR, visR * 0.42, 5))
          .attr('fill', '#f44336');
        virusBody
          .append('circle')
          .attr('class', 'virus-core')
          .attr('r', visR * 0.65)
          .attr('fill', '#f44336');
        // Text label stays outside .virus-body so it never rotates
        group
          .append('text')
          .attr('text-anchor', 'middle')
          .attr('dy', '0.35em')
          .attr('fill', 'rgba(255,120,120,0.9)')
          .attr('font-size', red.isActive ? '9px' : '5px')
          .attr('pointer-events', 'none')
          .text(`#${red.eventNumber}`);
        break;
      }
    }
  };

  // ─── DRAG BEHAVIOR ────────────────────────────────────────────────────────

  const buildDragBehavior = () =>
    d3
      .drag<SVGGElement, SimNode>()
      .on('start', (event, d) => {
        if (!event.active) simRef.current?.alphaTarget(0.3).restart();
        d.fx = d.x;
        d.fy = d.y;
        draggingNodeIdRef.current = d.id;
        // Raise the dragged element so it's always visible above all others
        gRef.current
          ?.selectAll<SVGGElement, SimNode>('.node-group')
          .filter((nd) => nd.id === d.id)
          .raise();
        // Freeze every other node (that isn't already pinned, e.g. orbiting red stars)
        // so the canvas holds still while the user aims
        frozenNodeIdsRef.current.clear();
        nodesRef.current.forEach((n) => {
          if (n.id !== d.id && n.fx == null) {
            n.fx = n.x ?? 0;
            n.fy = n.y ?? 0;
            frozenNodeIdsRef.current.add(n.id);
          }
        });
      })
      .on('drag', (event, d) => {
        d.fx = event.x;
        d.fy = event.y;

        // Show merge halo only after holding in range for ≥1.5s (AC 23)
        if (proximityTimerRef.current) clearTimeout(proximityTimerRef.current);
        proximityTimerRef.current = setTimeout(() => {
          checkProximityHalo(d);
        }, 1500);
      })
      .on('end', (event, d) => {
        if (!event.active) simRef.current?.alphaTarget(0);
        draggingNodeIdRef.current = null;
        // Release all nodes frozen during this drag
        frozenNodeIdsRef.current.forEach((id) => {
          const node = nodesRef.current.find((n) => n.id === id);
          if (node) {
            node.fx = null;
            node.fy = null;
          }
        });
        frozenNodeIdsRef.current.clear();

        // Green drag-drop absorption — only triggers on explicit release over a blue blob
        if (d.type === 'green' && !(d as GreenBlobNode).absorbed) {
          const green = d as GreenBlobNode;
          const blueTarget = nodesRef.current.find((n) => {
            if (n.type !== 'blue') return false;
            const dx = (green.x ?? 0) - (n.x ?? 0);
            const dy = (green.y ?? 0) - (n.y ?? 0);
            return Math.sqrt(dx * dx + dy * dy) < green.r + n.r;
          }) as BlueBlobNode | undefined;
          if (blueTarget) {
            // Re-pin the blue blob — the frozen-nodes release above already cleared its fx/fy,
            // so without this it gets flung by the physics restart inside resizeBlue.
            blueTarget.fx = blueTarget.x ?? 0;
            blueTarget.fy = blueTarget.y ?? 0;
            blueTarget.vx = 0;
            blueTarget.vy = 0;
            setTimeout(() => {
              // Zero velocities again at release — D3 accumulates forces into vx/vy every tick
              // even while the node is pinned, so they'd cause a jump without this clear.
              blueTarget.vx = 0;
              blueTarget.vy = 0;
              blueTarget.fx = null;
              blueTarget.fy = null;
            }, 400);

            green.absorbed = true;
            soundsRef.current.playSlosh();
            if (!blueTarget.absorbedLogos.some((l) => l.companyName === green.companyName)) {
              blueTarget.absorbedLogos.push({
                companyName: green.companyName,
                logoUrl: green.logoUrl,
                orbitAngle: Math.random() * Math.PI * 2,
                orbitRadius: blueTarget.r * (0.2 + Math.random() * 0.45),
                orbitSpeed: (0.0013 + Math.random() * 0.0027) * (Math.random() < 0.5 ? 1 : -1),
                sourceGhostType: green.sourceGhostType,
                ghostSnapshot: { ...green.originalGhostSnapshot },
              });
              resizeBlue(blueTarget);
            }
            // Remove forceLinks originating from this green
            linksRef.current = linksRef.current.filter((l) => {
              const src = typeof l.source === 'string' ? l.source : (l.source as SimNode).id;
              return src !== green.id;
            });
            (simRef.current?.force('link') as d3.ForceLink<SimNode, SimLink> | null)?.links(
              linksRef.current
            );
            // Pin the green blob at its drop position so physics doesn't move it during fade
            green.fx = green.x ?? 0;
            green.fy = green.y ?? 0;
            fadingGreenIds.current.add(green.id);
            gRef.current
              ?.selectAll<SVGGElement, GreenBlobNode>('.green-blob-group')
              .filter((gd) => gd.id === green.id)
              .transition()
              .duration(1500)
              .attr('opacity', 0)
              .on('end', () => {
                fadingGreenIds.current.delete(green.id);
                // Remove the green node from simulation — it will be respawned as a ghost
                // if the blue blob is later deleted.
                nodesRef.current = nodesRef.current.filter((n) => n.id !== green.id);
                simRef.current?.nodes(nodesRef.current);
              });
            syncTreeSummary();
            clearMergeHalos();
            return;
          }
        }

        const withinRange = (a: SimNode, b: SimNode, threshold: number) => {
          const dx = (a.x ?? 0) - (b.x ?? 0);
          const dy = (a.y ?? 0) - (b.y ?? 0);
          return Math.sqrt(dx * dx + dy * dy) < a.r + b.r + threshold;
        };

        if (d.type === 'blue') {
          // Blue dropped on blue → merge (search specifically for another blue)
          const nearbyBlue = nodesRef.current.find(
            (n) => n.id !== d.id && n.type === 'blue' && withinRange(d, n, 30)
          ) as BlueBlobNode | undefined;
          if (nearbyBlue) {
            mergeBlobs(d, nearbyBlue);
            return;
          }
        } else if (d.type === 'red-star') {
          // Red star dropped on blue → orbit
          const nearbyBlue = nodesRef.current.find(
            (n) => n.type === 'blue' && withinRange(d, n, 30)
          ) as BlueBlobNode | undefined;
          if (nearbyBlue) {
            (d as RedStarNode).orbiting = nearbyBlue.id;
            d.fx = null;
            d.fy = null;
          }
        }

        d.fx = null;
        d.fy = null;
        clearMergeHalos();
      });

  const findNearbyCompatible = (node: SimNode, threshold: number): SimNode | null => {
    for (const other of nodesRef.current) {
      if (other.id === node.id) continue;
      // Only valid merge pairs: blue+blue or blue+green
      const canMerge =
        (node.type === 'blue' && other.type === 'blue') ||
        (node.type === 'blue' && other.type === 'green') ||
        (node.type === 'green' && other.type === 'blue');
      if (!canMerge) continue;
      const dx = (node.x ?? 0) - (other.x ?? 0);
      const dy = (node.y ?? 0) - (other.y ?? 0);
      const dist = Math.sqrt(dx * dx + dy * dy);
      if (dist < node.r + other.r + threshold) return other;
    }
    return null;
  };

  const checkProximityHalo = (node: SimNode) => {
    const nearby = findNearbyCompatible(node, 30);
    if (nearby && gRef.current) {
      mergeHaloNodesRef.current.add(node.id);
      mergeHaloNodesRef.current.add(nearby.id);
      gRef.current
        .selectAll<SVGGElement, SimNode>('.node-group')
        .filter((d) => mergeHaloNodesRef.current.has(d.id))
        .select<SVGPathElement | SVGCircleElement>('.blob-shape')
        .attr('stroke', 'rgba(255,255,255,0.9)')
        .attr('stroke-width', 4);
    } else {
      clearMergeHalos();
    }
  };

  const clearMergeHalos = () => {
    if (gRef.current) {
      gRef.current
        .selectAll<SVGPathElement | SVGCircleElement, SimNode>('.node-group .blob-shape')
        .attr('stroke', null)
        .attr('stroke-width', null);
    }
    mergeHaloNodesRef.current.clear();
  };

  /**
   * Merge two blue blobs: `dragged` vanishes (with its topic name),
   * `target` grows and absorbs all logos from `dragged`.
   */
  const mergeBlobs = (dragged: SimNode, target: SimNode) => {
    soundsRef.current.playFlop();
    const vanishing = dragged as BlueBlobNode;
    const surviving = target as BlueBlobNode;

    // Transfer logos — tick's data-join enter() will fade them in automatically (1500ms)
    vanishing.absorbedLogos.forEach((logo) => {
      if (!surviving.absorbedLogos.some((l) => l.companyName === logo.companyName)) {
        surviving.absorbedLogos.push({
          ...logo,
          orbitAngle: Math.random() * Math.PI * 2,
          orbitRadius: surviving.r * (0.2 + Math.random() * 0.45),
        });
      }
    });

    // Transfer absorbed red stars from the vanishing blob to the surviving blob
    vanishing.absorbedRedStars.forEach((rs) => {
      if (!surviving.absorbedRedStars.some((r) => r.eventNumber === rs.eventNumber)) {
        surviving.absorbedRedStars.push({
          ...rs,
          orbitAngle: Math.random() * Math.PI * 2,
          orbitRadius: Math.max(10, Math.min(22, surviving.r * 0.28)),
        });
      }
    });

    // Resize the surviving blob over 600ms (logos add, red stars subtract)
    resizeBlue(surviving);
    surviving.fx = null;
    surviving.fy = null;

    // Freeze the vanishing blob in place and fade it out
    vanishing.fx = vanishing.x ?? 0;
    vanishing.fy = vanishing.y ?? 0;
    gRef.current
      ?.selectAll<SVGGElement, BlueBlobNode>('.blue-blob-group')
      .filter((d) => d.id === vanishing.id)
      .transition()
      .duration(600)
      .attr('opacity', 0)
      .on('end', () => {
        nodesRef.current = nodesRef.current.filter((n) => n.id !== vanishing.id);
        linksRef.current = linksRef.current.filter(
          (l) =>
            (typeof l.target === 'string' ? l.target : (l.target as SimNode).id) !== vanishing.id &&
            (typeof l.source === 'string' ? l.source : (l.source as SimNode).id) !== vanishing.id
        );
        setBlueBlobIds((ids) => ids.filter((id) => id !== vanishing.id));
        syncTreeSummary();
        renderAll();
      });
  };

  // ─── GHOST → BLUE SPAWN ───────────────────────────────────────────────────

  const spawnBlueFromGhost = useCallback((ghost: GhostNode) => {
    nodesRef.current = nodesRef.current.filter((n) => n.id !== ghost.id);
    addBlueBlobAt(
      ghost.name,
      ghost.x ?? window.innerWidth / 2,
      ghost.y ?? window.innerHeight / 2,
      ghost.topicCode
    );
    syncTreeSummary();
  }, []);

  // ─── ADD BLUE BLOB ────────────────────────────────────────────────────────

  const addBlueBlobAt = useCallback(
    (name: string, x: number, y: number, topicCode?: string): string => {
      const id = mkId();
      const baseR = Math.max(40, Math.min(100, name.length * 5));
      const blue: BlueBlobNode = {
        id,
        type: 'blue',
        name,
        r: baseR,
        baseR,
        topicCode,
        x,
        y,
        wobblePhase: Math.random() * Math.PI * 2,
        wobbleSpeed: 0.007 + Math.random() * 0.04,
        absorbedLogos: [],
        absorbedRedStars: [],
      };

      nodesRef.current = [...nodesRef.current, blue];
      setBlueBlobIds((ids) => [...ids, id]);
      syncTreeSummary();
      renderAll();

      // Async similarity call — fires after blob spawns
      blobTopicService
        .getSimilarity(eventCode, name)
        .then((sim: TopicSimilarityResponse) => {
          const node = nodesRef.current.find((n) => n.id === id) as BlueBlobNode | undefined;
          if (!node) return;

          node.cluster = sim.cluster;
          node.similarityScore = sim.similarityScore;
          node.relatedPastEventNumbers = sim.relatedPastEventNumbers;

          // Convert matching ghost blobs into green blobs attracted to this blue
          convertGhostsToGreen(id, sim.cluster);

          // Activate matching red stars
          activateRedStars(sim.relatedPastEventNumbers, id);

          simRef.current?.alpha(0.1).restart();
        })
        .catch(() => {
          // Similarity is optional — ignore failures
        });

      return id;
    },
    [eventCode]
  );

  /**
   * When a blue blob receives its cluster assignment, find all ghost nodes whose cluster
   * matches and convert them into live green blobs attracted to this blue.
   *
   * - ghost-partner → green blob with company logo; can be dragged into blue for logo absorption
   * - ghost-backlog → green blob with BATbern spinner; same drag mechanic
   * - ghost-trend   → green blob (no logo); absorbs silently
   *
   * BUSINESS_OTHER blue blobs attract nobody — they represent unclassified topics.
   */
  const convertGhostsToGreen = (blueBlobId: string, blueCluster: string) => {
    if (blueCluster === 'BUSINESS_OTHER') return;

    const toConvert = nodesRef.current.filter((n): n is GhostNode => {
      if (n.type !== 'ghost-partner' && n.type !== 'ghost-backlog' && n.type !== 'ghost-trend')
        return false;
      const ghost = n as GhostNode;
      if (n.type === 'ghost-partner') return ghost.cluster === blueCluster;
      // ghost-backlog: topicCode IS the cluster enum value
      if (n.type === 'ghost-backlog') return ghost.topicCode === blueCluster;
      if (n.type === 'ghost-trend') return ghost.cluster === blueCluster;
      return false;
    });

    if (toConvert.length === 0) return;

    // Remove matched ghosts from the simulation
    const convertedIds = new Set(toConvert.map((g) => g.id));
    nodesRef.current = nodesRef.current.filter((n) => !convertedIds.has(n.id));

    toConvert.forEach((ghost) => {
      const r = ghost.r ?? 38;
      const green: GreenBlobNode = {
        id: `green-${ghost.id}`,
        type: 'green',
        companyName: ghost.companyName ?? ghost.name,
        logoUrl: ghost.logoUrl ?? '',
        topicName: ghost.name,
        r,
        absorbed: false,
        wobblePhase: Math.random() * Math.PI * 2,
        wobbleSpeed: 0.007 + Math.random() * 0.04,
        // Full attraction toward the triggering cluster
        clusterAttractions: { [blueCluster]: 1.0 },
        topicsByCluster: { [blueCluster]: [ghost.name] },
        linkedBlobsByCluster: {},
        sourceGhostType: ghost.type,
        originalGhostSnapshot: {
          name: ghost.name,
          r: ghost.r,
          topicCode: ghost.topicCode,
          companyName: ghost.companyName,
          logoUrl: ghost.logoUrl,
          cluster: ghost.cluster ?? (ghost.topicCode === blueCluster ? blueCluster : undefined),
        },
        x: ghost.x ?? 0,
        y: ghost.y ?? 0,
        vx: 0,
        vy: 0,
      };

      nodesRef.current.push(green);

      // Wire forceLink: green → blue with scaled-down strength for slow drift
      linksRef.current.push({ source: green.id, target: blueBlobId, strength: 0.1 });
      green.linkedBlobsByCluster[blueCluster] = blueBlobId;
    });

    simRef.current?.nodes(nodesRef.current);
    (simRef.current?.force('link') as d3.ForceLink<SimNode, SimLink> | null)?.links(
      linksRef.current
    );

    // Re-render: removes ghost SVG elements, adds green blob elements
    renderAll();
  };

  /**
   * Ignite red stars matching the given past event numbers and set them flying toward blueId.
   * Instead of repelling, ignited stars are attracted to the blue blob and absorbed on contact,
   * then swim inside it as small red star polygons (shrinking the blob to signal "recent topic").
   */
  const activateRedStars = (relatedEventNumbers: number[], blueId: string) => {
    const maxEvt = mostRecentEventNumRef.current;
    nodesRef.current.forEach((n) => {
      if (n.type !== 'red-star') return;
      const red = n as RedStarNode;
      if (
        relatedEventNumbers.includes(red.eventNumber) &&
        maxEvt - red.eventNumber <= 12 &&
        !red.isActive &&
        !red.absorbed
      ) {
        red.isActive = true;
        red.attractedToBlueId = blueId;
        red.r = 30; // slightly larger than dormant while flying in

        // Brief flare to signal ignition before the star starts flying
        growingRedIds.current.add(red.id);
        const redGrp = gRef.current
          ?.selectAll<SVGGElement, RedStarNode>('.red-star-group')
          .filter((d) => d.id === red.id);
        redGrp
          ?.select<SVGPolygonElement>('polygon')
          .transition()
          .duration(500)
          .ease(d3.easeCubicOut)
          .attr('points', starPointsRelative(30, 30 * 0.42, 5))
          .on('end', () => growingRedIds.current.delete(red.id));
        redGrp
          ?.select<SVGCircleElement>('.virus-core')
          .transition()
          .duration(500)
          .ease(d3.easeCubicOut)
          .attr('r', 30 * 0.65);
      }
    });
  };

  // ─── REMOVE BLUE BLOB BY ID ───────────────────────────────────────────────

  const removeBlueBlobById = useCallback(
    (targetId: string) => {
      const removedBlob = nodesRef.current.find((n) => n.id === targetId) as
        | BlueBlobNode
        | undefined;
      const removedRelated = removedBlob?.relatedPastEventNumbers ?? [];

      // Respawn absorbed logos/ghosts as ghost nodes of their original type
      const respawnedIds: string[] = [];
      if (removedBlob) {
        const bx = removedBlob.x ?? 0;
        const by = removedBlob.y ?? 0;
        removedBlob.absorbedLogos.forEach((logo, i) => {
          const snap = logo.ghostSnapshot;
          const angle = (i / Math.max(removedBlob.absorbedLogos.length, 1)) * Math.PI * 2;
          const dist = (removedBlob.r ?? 60) + (snap.r ?? 38) + 25;
          const orb = makeRandomGhostOrbit();
          const ghost: GhostNode = {
            id: `${logo.sourceGhostType}-respawn-${Date.now()}-${i}`,
            type: logo.sourceGhostType,
            name: snap.name,
            r: snap.r ?? 38,
            topicCode: snap.topicCode,
            companyName: snap.companyName,
            logoUrl: snap.logoUrl,
            cluster: snap.cluster,
            x: bx + dist * Math.cos(angle),
            y: by + dist * Math.sin(angle),
            ...orb,
          };
          nodesRef.current.push(ghost);
          respawnedIds.push(ghost.id);
        });

        // Deactivate absorbed red stars — return them to the dormant constellation
        removedBlob.absorbedRedStars.forEach((absorbed) => {
          const red = nodesRef.current.find(
            (n) => n.type === 'red-star' && (n as RedStarNode).eventNumber === absorbed.eventNumber
          ) as RedStarNode | undefined;
          if (red) {
            red.isActive = false;
            red.absorbed = false;
            red.attractedToBlueId = undefined;
            red.orbiting = undefined;
            red.r = 42;
          }
        });
      }

      nodesRef.current = nodesRef.current.filter((n) => n.id !== targetId);
      linksRef.current = linksRef.current.filter((l) => {
        const tgt = typeof l.target === 'string' ? l.target : (l.target as SimNode).id;
        return tgt !== targetId;
      });

      // Convert in-transit greens that were attracted to the deleted blue back to ghost nodes
      const inTransitGreens = nodesRef.current.filter((n) => {
        if (n.type !== 'green') return false;
        const g = n as GreenBlobNode;
        return !g.absorbed && Object.values(g.linkedBlobsByCluster).includes(targetId);
      }) as GreenBlobNode[];

      inTransitGreens.forEach((green, i) => {
        const snap = green.originalGhostSnapshot;
        const angle = (i / Math.max(inTransitGreens.length, 1)) * Math.PI * 2;
        const dist = (removedBlob?.r ?? 60) + green.r + 20;
        const orb = makeRandomGhostOrbit();
        const ghost: GhostNode = {
          id: `${green.sourceGhostType}-restored-${Date.now()}-${i}`,
          type: green.sourceGhostType,
          name: snap.name,
          r: snap.r ?? 38,
          topicCode: snap.topicCode,
          companyName: snap.companyName,
          logoUrl: snap.logoUrl,
          cluster: snap.cluster,
          x: (green.x ?? 0) + dist * Math.cos(angle),
          y: (green.y ?? 0) + dist * Math.sin(angle),
          ...orb,
        };
        nodesRef.current.push(ghost);
        respawnedIds.push(ghost.id);
      });

      // Remove in-transit greens from simulation
      const inTransitIds = new Set(inTransitGreens.map((g) => g.id));
      nodesRef.current = nodesRef.current.filter((n) => !inTransitIds.has(n.id));

      // Deactivate red stars whose only activator was the removed blue
      if (removedRelated.length > 0) {
        const stillActivated = new Set<number>();
        nodesRef.current
          .filter((n) => n.type === 'blue')
          .forEach((b) => {
            (b as BlueBlobNode).relatedPastEventNumbers?.forEach((num) => stillActivated.add(num));
          });
        nodesRef.current.forEach((n) => {
          if (n.type === 'red-star') {
            const red = n as RedStarNode;
            if (removedRelated.includes(red.eventNumber) && !stillActivated.has(red.eventNumber)) {
              red.isActive = false;
              red.orbiting = undefined;
              red.attractedToBlueId = undefined;
            }
          }
        });
      }

      // Release any red stars still flying toward the removed blue blob
      nodesRef.current.forEach((n) => {
        if (n.type !== 'red-star') return;
        const red = n as RedStarNode;
        if (red.attractedToBlueId === targetId) {
          red.attractedToBlueId = undefined;
          red.isActive = false;
          red.r = 28;
        }
      });

      simRef.current?.nodes(nodesRef.current);
      setBlueBlobIds((ids) => ids.filter((id) => id !== targetId));
      syncTreeSummary();
      renderAll();

      // Fade in respawned ghost nodes
      if (respawnedIds.length > 0) {
        respawnedIds.forEach((gid) => {
          gRef.current
            ?.selectAll<SVGGElement, GhostNode>('.ghost-group')
            .filter((gd) => gd.id === gid)
            .attr('opacity', 0)
            .transition()
            .duration(1200)
            .attr('opacity', 1);
        });
      }
    },
    [syncTreeSummary, renderAll]
  );

  // ─── KEYBOARD HANDLER ─────────────────────────────────────────────────────

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (acceptBlob) return; // Dialog open — let MUI handle keys

      if (e.ctrlKey && e.key === 'z') {
        e.preventDefault();
        const lastId = blueBlobIds[blueBlobIds.length - 1];
        if (lastId) {
          if (selectedBlobIdRef.current === lastId) selectedBlobIdRef.current = null;
          removeBlueBlobById(lastId);
        }
        return;
      }

      // Delete or Backspace — fade out the selected blue blob, then remove it
      if ((e.key === 'Delete' || e.key === 'Backspace') && selectedBlobIdRef.current) {
        soundsRef.current.playShutdown();
        e.preventDefault();
        const idToRemove = selectedBlobIdRef.current;
        // Keep selectedBlobIdRef set so the ring stays visible during fade-out
        gRef.current
          ?.selectAll<SVGGElement, BlueBlobNode>('.blue-blob-group')
          .filter((d) => d.id === idToRemove)
          .transition()
          .duration(3500)
          .attr('opacity', 0)
          .on('end', () => {
            selectedBlobIdRef.current = null;
            removeBlueBlobById(idToRemove);
          });
        return;
      }

      if (e.key === 'Escape') {
        setShowInput(false);
        setInputValue('');
        return;
      }

      // Space while hovering a ghost → activate it directly (AC 21)
      if (e.key === ' ' && hoveredGhostRef.current) {
        e.preventDefault();
        spawnBlueFromGhost(hoveredGhostRef.current);
        hoveredGhostRef.current = null;
        return;
      }

      // Any other printable character → open floating input
      if (!showInput && e.key.length === 1 && !e.ctrlKey && !e.metaKey && !e.altKey) {
        e.preventDefault(); // prevent the key from also being inserted by the browser into the newly-focused TextField
        setShowInput(true);
        setInputValue(e.key);
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [acceptBlob, showInput, blueBlobIds, renderAll, removeBlueBlobById]);

  // ─── WINDOW RESIZE HANDLER ────────────────────────────────────────────────

  useEffect(() => {
    const handleResize = () => {
      if (!svgRef.current || !simRef.current) return;
      const w = window.innerWidth;
      const h = window.innerHeight;
      const svg = d3.select(svgRef.current);
      svg.select('rect').attr('width', w).attr('height', h);
      (simRef.current.force('center') as d3.ForceCenter<SimNode> | null)?.x(w / 2).y(h / 2);
      simRef.current.alpha(0.3).restart();
    };
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  // ─── ADD BLUE BLOB FROM INPUT ─────────────────────────────────────────────

  const addBlueBlobFromInput = useCallback(() => {
    const name = inputValue.trim();
    if (!name) return;
    // Spawn at viewport center (where the textbox sits), converted to simulation coordinates
    const transform = svgRef.current ? d3.zoomTransform(svgRef.current) : d3.zoomIdentity;
    const [sx, sy] = transform.invert([window.innerWidth / 2, window.innerHeight / 2]);
    addBlueBlobAt(name, sx, sy);
    setInputValue('');
    setShowInput(false);
  }, [inputValue, addBlueBlobAt]);

  // ─── FIT ALL / SNAP TO ACTIVE ─────────────────────────────────────────────

  const fitAll = useCallback(() => {
    if (!svgRef.current || !zoomRef.current) return;
    const nodes = nodesRef.current.filter((n) => n.x != null && n.y != null);
    if (nodes.length === 0) return;

    const xs = nodes.map((n) => n.x!);
    const ys = nodes.map((n) => n.y!);
    const minX = Math.min(...xs) - 60;
    const maxX = Math.max(...xs) + 60;
    const minY = Math.min(...ys) - 60;
    const maxY = Math.max(...ys) + 60;
    const w = window.innerWidth;
    const h = window.innerHeight;
    const scale = Math.min(4, Math.max(0.1, Math.min(w / (maxX - minX), h / (maxY - minY)) * 0.9));
    const tx = w / 2 - scale * ((minX + maxX) / 2);
    const ty = h / 2 - scale * ((minY + maxY) / 2);

    d3.select(svgRef.current)
      .transition()
      .duration(500)
      .call(zoomRef.current.transform, d3.zoomIdentity.translate(tx, ty).scale(scale));
  }, []);

  const snapToActive = useCallback(() => {
    if (!svgRef.current || !zoomRef.current) return;
    const blueNodes = nodesRef.current.filter((n) => n.type === 'blue' && n.x != null);
    if (blueNodes.length === 0) return;

    const xs = blueNodes.map((n) => n.x!);
    const ys = blueNodes.map((n) => n.y!);
    const minX = Math.min(...xs) - 80;
    const maxX = Math.max(...xs) + 80;
    const minY = Math.min(...ys) - 80;
    const maxY = Math.max(...ys) + 80;
    const w = window.innerWidth;
    const h = window.innerHeight;
    const scale = Math.min(4, Math.max(0.1, Math.min(w / (maxX - minX), h / (maxY - minY)) * 0.9));
    const tx = w / 2 - scale * ((minX + maxX) / 2);
    const ty = h / 2 - scale * ((minY + maxY) / 2);

    d3.select(svgRef.current)
      .transition()
      .duration(500)
      .call(zoomRef.current.transform, d3.zoomIdentity.translate(tx, ty).scale(scale));
  }, []);

  // ─── ACCEPT TOPIC ─────────────────────────────────────────────────────────

  const handleAcceptConfirm = useCallback(
    async (note: string, newTopicFields?: { description: string; category: string }) => {
      if (!acceptBlob) return;
      try {
        let topicCode = acceptBlob.topicCode;

        if (!topicCode) {
          // New topic: create it first, then select it for the event
          const created = await topicService.createTopic({
            title: acceptBlob.name,
            description: newTopicFields?.description || undefined,
            category: newTopicFields?.category ?? 'technical',
          });
          topicCode = created.topicCode;
        }

        // PATCH sets both topicCode + topicSelectionNote in one call — no state machine restriction
        await blobTopicService.acceptTopic(eventCode, topicCode, note);

        // Invalidate cached event data so topic page shows the updated topic immediately (bug #4)
        await queryClient.invalidateQueries({ queryKey: ['event', eventCode] });

        navigate(`/organizer/topics?eventCode=${eventCode}`);
      } catch {
        setAcceptError(
          t('blobSelector.accept.saveError', {
            defaultValue: 'Failed to save topic selection. Please try again.',
          })
        );
      }
      setAcceptBlob(null);
    },
    [acceptBlob, eventCode, navigate, queryClient, t]
  );

  // ─── RENDER ───────────────────────────────────────────────────────────────

  return (
    <>
      <style>{`
        @keyframes blobPulse {
          0%, 100% { transform: scale(0.95); }
          50% { transform: scale(1.05); }
        }
        /* Apply pulse to the circle only — animating the group overrides D3's translate attribute */
        .ghost-group circle {
          animation: blobPulse 3s ease-in-out infinite;
          transform-box: fill-box;
          transform-origin: center;
        }
        /* BATbern spinner for organizer (backlog) ghost blobs */
        @media (prefers-reduced-motion: no-preference) {
          .ghost-org-arrow { transform-box: fill-box; }
          .ghost-org-arrow-1 {
            transform-origin: 50% 87%;
            animation: ghost-org-spin-double 3.0s linear infinite;
          }
          .ghost-org-arrow-2 {
            transform-origin: 50% 16%;
            animation: ghost-org-spin-single 3.0s linear infinite;
          }
          @keyframes ghost-org-spin-double {
            0%      { transform: rotate(0deg); }
            33.33%  { transform: rotate(0deg); animation-timing-function: ease-in-out; }
            100%    { transform: rotate(720deg); }
          }
          @keyframes ghost-org-spin-single {
            0%      { transform: rotate(0deg); }
            33.33%  { transform: rotate(0deg); animation-timing-function: ease-in-out; }
            100%    { transform: rotate(360deg); }
          }
        }
      `}</style>

      {/* Full-viewport SVG canvas — D3 renders everything inside */}
      <svg
        ref={svgRef}
        data-testid="blob-canvas"
        style={{ width: '100vw', height: '100vh', display: 'block', overflow: 'hidden' }}
      />

      {/* Ghost visibility toggles — top center */}
      <Box
        sx={{
          position: 'fixed',
          top: 16,
          left: '50%',
          transform: 'translateX(-50%)',
          zIndex: 1000,
          display: 'flex',
          gap: 0.5,
        }}
      >
        {[
          {
            type: 'ghost-backlog' as const,
            label: 'Organizer',
            dotColor: 'rgba(255,255,255,0.85)',
            activeBg: 'rgba(255,255,255,0.12)',
            activeBorder: 'rgba(255,255,255,0.35)',
            activeColor: 'rgba(255,255,255,0.9)',
            hoverBg: 'rgba(255,255,255,0.20)',
          },
          {
            type: 'ghost-partner' as const,
            label: 'Companies',
            dotColor: 'rgba(144,238,144,0.9)',
            activeBg: 'rgba(144,238,144,0.15)',
            activeBorder: 'rgba(144,238,144,0.40)',
            activeColor: 'rgba(144,238,144,0.95)',
            hoverBg: 'rgba(144,238,144,0.22)',
          },
          {
            type: 'ghost-trend' as const,
            label: 'Trending',
            dotColor: 'rgba(255,215,0,0.9)',
            activeBg: 'rgba(255,215,0,0.15)',
            activeBorder: 'rgba(255,215,0,0.40)',
            activeColor: 'rgba(255,215,0,0.95)',
            hoverBg: 'rgba(255,215,0,0.22)',
          },
        ].map(({ type, label, dotColor, activeBg, activeBorder, activeColor, hoverBg }) => {
          const active = visibleGhostTypes.has(type);
          return (
            <Button
              key={type}
              size="small"
              onClick={() => toggleGhostType(type)}
              sx={{
                bgcolor: active ? activeBg : 'rgba(255,255,255,0.04)',
                color: active ? activeColor : 'rgba(255,255,255,0.3)',
                border: `1px solid ${active ? activeBorder : 'rgba(255,255,255,0.12)'}`,
                fontSize: '11px',
                textTransform: 'none',
                minWidth: 0,
                px: 1.5,
                py: 0.5,
                borderRadius: 2,
                backdropFilter: 'blur(4px)',
                transition: 'all 0.2s',
                '&:hover': { bgcolor: active ? hoverBg : 'rgba(255,255,255,0.08)' },
              }}
            >
              <Box
                component="span"
                sx={{
                  width: 7,
                  height: 7,
                  borderRadius: '50%',
                  bgcolor: active ? dotColor : 'rgba(255,255,255,0.2)',
                  mr: 0.75,
                  display: 'inline-block',
                  flexShrink: 0,
                  transition: 'background-color 0.2s',
                }}
              />
              {label}
            </Button>
          );
        })}
      </Box>

      {/* Fixed top-right controls */}
      <Box
        sx={{
          position: 'fixed',
          top: 16,
          right: 16,
          zIndex: 1000,
          display: 'flex',
          gap: 1,
        }}
      >
        <IconButton
          data-testid="sound-toggle-button"
          aria-label={sounds.isMuted ? 'Enable sound effects' : 'Disable sound effects'}
          onClick={sounds.toggleMute}
          size="small"
          sx={{
            color: sounds.isMuted ? 'rgba(255,255,255,0.35)' : 'rgba(255,255,255,0.9)',
            bgcolor: 'rgba(255,255,255,0.08)',
            border: `1px solid ${sounds.isMuted ? 'rgba(255,255,255,0.12)' : 'rgba(255,255,255,0.3)'}`,
            borderRadius: 1,
            '&:hover': { bgcolor: 'rgba(255,255,255,0.16)' },
            transition: 'all 0.2s',
          }}
        >
          {sounds.isMuted ? <VolumeOff fontSize="small" /> : <VolumeUp fontSize="small" />}
        </IconButton>
        <Button
          data-testid="fit-all-button"
          variant="contained"
          size="small"
          startIcon={<FitScreen />}
          onClick={fitAll}
          sx={{
            bgcolor: 'rgba(255,255,255,0.15)',
            color: 'white',
            '&:hover': { bgcolor: 'rgba(255,255,255,0.25)' },
          }}
        >
          {t('blobSelector.fitAll', { defaultValue: 'Fit All' })}
        </Button>
        <Button
          data-testid="snap-to-active-button"
          variant="contained"
          size="small"
          startIcon={<MyLocation />}
          onClick={snapToActive}
          sx={{
            bgcolor: 'rgba(255,255,255,0.15)',
            color: 'white',
            '&:hover': { bgcolor: 'rgba(255,255,255,0.25)' },
          }}
        >
          {t('blobSelector.snapToActive', { defaultValue: 'Snap to Active' })}
        </Button>
      </Box>

      {/* Floating text input for blue blob summon (AC: 10) */}
      {showInput && (
        <Box
          sx={{
            position: 'fixed',
            top: '50%',
            left: '50%',
            transform: 'translate(-50%, -50%)',
            zIndex: 2000,
            bgcolor: 'rgba(13,27,42,0.95)',
            p: 2,
            borderRadius: 2,
            border: '1px solid rgba(25,118,210,0.5)',
          }}
        >
          <TextField
            autoFocus
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            onKeyDown={(e) => {
              e.stopPropagation();
              if (e.key === 'Enter') {
                addBlueBlobFromInput();
              } else if (e.key === 'Escape') {
                setShowInput(false);
                setInputValue('');
              }
            }}
            placeholder={t('blobSelector.typeTopic', {
              defaultValue: 'Type a topic and press Enter...',
            })}
            size="small"
            sx={{
              minWidth: 320,
              '& .MuiInputBase-root': { color: 'white' },
              '& .MuiOutlinedInput-notchedOutline': {
                borderColor: 'rgba(25,118,210,0.5)',
              },
              '& .MuiInputBase-input::placeholder': {
                color: 'rgba(255,255,255,0.5)',
              },
            }}
          />
        </Box>
      )}

      {/* Accept Topic Dialog (AC: 26-27) */}
      <AcceptTopicDialog
        open={acceptBlob !== null}
        blob={acceptBlob}
        hasOrbitingRed={hasOrbitingRed}
        mostRecentEventNumber={mostRecentEventNum}
        competingCandidates={nodesRef.current
          .filter((n) => n.type === 'blue' && n.id !== acceptBlob?.id)
          .map((n) => (n as BlueBlobNode).name)}
        onConfirm={handleAcceptConfirm}
        onCancel={() => setAcceptBlob(null)}
      />

      {/* Accept failure feedback (P4) */}
      <Snackbar
        open={acceptError !== null}
        autoHideDuration={4000}
        onClose={() => setAcceptError(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert severity="error" onClose={() => setAcceptError(null)}>
          {acceptError}
        </Alert>
      </Snackbar>

      {/* Topic tree panel — floating card hovering over canvas, right side */}
      <Box
        sx={{
          position: 'fixed',
          right: 16,
          top: 60,
          bottom: 16,
          display: 'flex',
          flexDirection: 'row',
          zIndex: 1000,
          borderRadius: 2,
          overflow: 'hidden',
          boxShadow: '0 4px 24px rgba(0,0,0,0.6)',
          pointerEvents: 'none', // let canvas remain interactive by default
        }}
      >
        {/* Toggle strip — always visible */}
        <Box
          sx={{
            width: 24,
            flexShrink: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            bgcolor: 'rgba(25,118,210,0.25)',
            cursor: 'pointer',
            pointerEvents: 'auto',
            '&:hover': { bgcolor: 'rgba(25,118,210,0.4)' },
            transition: 'background-color 0.2s',
          }}
          onClick={() => setPanelOpen((v) => !v)}
        >
          {panelOpen ? (
            <ChevronRight sx={{ color: 'rgba(255,255,255,0.65)', fontSize: 16 }} />
          ) : (
            <ChevronLeft sx={{ color: 'rgba(255,255,255,0.65)', fontSize: 16 }} />
          )}
        </Box>

        {/* Content — slides in/out */}
        <Box
          sx={{
            width: panelOpen ? 220 : 0,
            transition: 'width 0.25s ease',
            overflow: 'hidden',
            bgcolor: 'rgba(13,27,42,0.95)',
            pointerEvents: 'auto',
          }}
        >
          <Box sx={{ width: 220, height: '100%', display: 'flex', flexDirection: 'column' }}>
            <Box sx={{ flex: 1, overflowY: 'auto', p: 1.5 }}>
              <Typography
                sx={{
                  color: 'rgba(255,255,255,0.7)',
                  fontSize: 9,
                  textTransform: 'uppercase',
                  letterSpacing: 1.2,
                  display: 'block',
                  mb: 1.5,
                }}
              >
                Topics
              </Typography>

              {treeSummary.length === 0 ? (
                <Typography
                  sx={{ color: 'rgba(255,255,255,0.6)', fontSize: 10, fontStyle: 'italic' }}
                >
                  No topics yet
                </Typography>
              ) : (
                treeSummary.map((topic) => (
                  <Box key={topic.id} sx={{ mb: 1.5 }}>
                    {/* Topic root node */}
                    <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 0.75, mb: 0.5 }}>
                      <Box
                        sx={{
                          width: 8,
                          height: 8,
                          borderRadius: '50%',
                          bgcolor: '#1976d2',
                          flexShrink: 0,
                          mt: '3px',
                        }}
                      />
                      <Typography
                        sx={{ color: 'white', fontWeight: 600, fontSize: 11, lineHeight: 1.3 }}
                      >
                        {topic.name}
                      </Typography>
                    </Box>

                    {/* Absorbed company children */}
                    {topic.companies.length === 0 ? (
                      <Typography
                        sx={{
                          color: 'rgba(255,255,255,0.6)',
                          pl: 2,
                          fontSize: 10,
                          fontStyle: 'italic',
                        }}
                      >
                        no partners
                      </Typography>
                    ) : (
                      topic.companies.map((company) => (
                        <Box key={company.name} sx={{ pl: 1.5, py: '2px' }}>
                          {/* Company name row */}
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                            <Typography
                              sx={{
                                color: 'rgba(255,255,255,0.6)',
                                fontSize: 8,
                                lineHeight: 1,
                                flexShrink: 0,
                              }}
                            >
                              └
                            </Typography>
                            <Typography sx={{ color: 'white', fontSize: 10 }}>
                              {company.name}
                            </Typography>
                            {company.score !== null && (
                              <Typography
                                sx={{
                                  color: 'rgba(255,255,255,0.7)',
                                  fontSize: 9,
                                  ml: 'auto',
                                  pr: 0.5,
                                  fontVariantNumeric: 'tabular-nums',
                                }}
                              >
                                {Math.round(company.score * 100)}%
                              </Typography>
                            )}
                          </Box>
                          {/* Attraction reason — indented below company name */}
                          {company.reason && (
                            <Typography
                              sx={{
                                color: 'rgba(255,255,255,0.65)',
                                fontSize: 9,
                                pl: 1.5,
                                fontStyle: 'italic',
                                lineHeight: 1.4,
                              }}
                            >
                              {company.reason}
                            </Typography>
                          )}
                        </Box>
                      ))
                    )}

                    {/* Absorbed past events (red stars) */}
                    {topic.pastEvents.length > 0 && (
                      <Box sx={{ mt: 0.5 }}>
                        {topic.pastEvents.map((evt) => (
                          <Box key={evt.eventNumber} sx={{ pl: 1.5, py: '2px' }}>
                            {/* BATbern ID row */}
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                              <Typography
                                sx={{
                                  color: 'rgba(244,67,54,0.7)',
                                  fontSize: 8,
                                  lineHeight: 1,
                                  flexShrink: 0,
                                }}
                              >
                                └
                              </Typography>
                              <Typography sx={{ color: '#f44336', fontSize: 10, fontWeight: 600 }}>
                                BATbern #{evt.eventNumber}
                              </Typography>
                            </Box>
                            {/* Actual event topic title — indented below, like company reason */}
                            <Typography
                              sx={{
                                color: 'rgba(244,67,54,0.75)',
                                fontSize: 9,
                                pl: 1.5,
                                fontStyle: 'italic',
                                lineHeight: 1.4,
                              }}
                            >
                              {evt.topicName}
                            </Typography>
                          </Box>
                        ))}
                      </Box>
                    )}
                  </Box>
                ))
              )}
            </Box>

            {/* Help text — pinned to the bottom of the panel */}
            <Box
              sx={{
                px: 1.5,
                py: 1,
                borderTop: '1px solid rgba(255,255,255,0.15)',
                flexShrink: 0,
              }}
            >
              {[
                'Type to add a new topic',
                'Drag over to merge',
                'Click (yellow ring) + Delete to remove',
                'Double-click to select for event',
              ].map((hint) => (
                <Typography
                  key={hint}
                  sx={{
                    color: 'rgba(255,255,255,0.75)',
                    fontSize: 8.5,
                    lineHeight: 1.6,
                    display: 'flex',
                    alignItems: 'baseline',
                    gap: 0.5,
                  }}
                >
                  <Box component="span" sx={{ color: 'rgba(255,255,255,0.45)', flexShrink: 0 }}>
                    ›
                  </Box>
                  {hint}
                </Typography>
              ))}
            </Box>
          </Box>
        </Box>
      </Box>
    </>
  );
};

export default BlobTopicSelector;
