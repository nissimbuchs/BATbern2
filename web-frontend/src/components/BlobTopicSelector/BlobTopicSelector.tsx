/**
 * BlobTopicSelector — D3 physics blob canvas for topic selection
 * Story 10.4: Blob Topic Selector (Tasks 7–15)
 *
 * React+D3 pattern: D3 owns all SVG content inside the main <g> group.
 * React state drives structural changes (add/remove blobs, dialogs, input).
 * Tick updates are applied by D3 directly to the DOM — no React re-render per tick.
 */

import React, { useRef, useEffect, useState, useCallback } from 'react';
import * as d3 from 'd3';
import { Alert, Box, Button, Snackbar, TextField, Typography } from '@mui/material';
import { ChevronLeft, ChevronRight, FitScreen, MyLocation } from '@mui/icons-material';
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
  TopicSessionData,
  TopicSimilarityResponse,
  PartnerTopicItem,
} from './types';
import { blobTopicService } from '@/services/blobTopicService';
import AcceptTopicDialog from './AcceptTopicDialog';

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
 * Compute per-cluster forceLink attraction strengths for a partner company
 * from their list of submitted topic suggestions.
 *
 * Strength formula per cluster:
 *   rawStrength = Σ (1 + voteCount) × recencyDecay(createdAt)
 *   recencyDecay = max(0.2, 1.0 - ageMonths × 0.05)   ← linear, floor 0.2 at ~16 months
 *   normalizedStrength = min(1.0, rawStrength / 3.0)
 *
 * BUSINESS_OTHER topics are excluded — they carry no cluster signal.
 * Clusters with a computed strength below 0.05 are also excluded to avoid spurious links.
 */
function computeClusterAttractions(topics: PartnerTopicItem[]): Record<string, number> {
  const now = Date.now();
  const raw: Record<string, number> = {};

  for (const t of topics) {
    if (t.cluster === 'BUSINESS_OTHER') continue;
    const ageMonths = (now - new Date(t.createdAt).getTime()) / (1000 * 60 * 60 * 24 * 30);
    const recency = Math.max(0.2, 1.0 - ageMonths * 0.05);
    raw[t.cluster] = (raw[t.cluster] ?? 0) + (1 + t.voteCount) * recency;
  }

  const result: Record<string, number> = {};
  for (const [cluster, val] of Object.entries(raw)) {
    const normalized = Math.min(1.0, val / 3.0);
    if (normalized >= 0.05) result[cluster] = normalized;
  }
  return result;
}

// ─── component ────────────────────────────────────────────────────────────────

interface BlobTopicSelectorProps {
  eventCode: string;
  sessionData: TopicSessionData;
}

const BlobTopicSelector: React.FC<BlobTopicSelectorProps> = ({ eventCode, sessionData }) => {
  const { t } = useTranslation('organizer');
  const navigate = useNavigate();

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

  // React state — only for UI elements that need re-renders
  const [showInput, setShowInput] = useState(false);
  const [inputValue, setInputValue] = useState('');
  const [blueBlobIds, setBlueBlobIds] = useState<string[]>([]);
  const [acceptBlob, setAcceptBlob] = useState<BlueBlobNode | null>(null);
  const [mostRecentEventNum, setMostRecentEventNum] = useState(0);
  const [acceptError, setAcceptError] = useState<string | null>(null);
  const [treeSummary, setTreeSummary] = useState<
    { id: string; name: string; companies: string[] }[]
  >([]);
  const [panelOpen, setPanelOpen] = useState(true);

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

    // Dark navy background
    svg.append('rect').attr('width', w).attr('height', h).attr('fill', '#0d1b2a');

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
        d3.forceCollide<SimNode>().radius((d) => d.r + 5)
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

    const maxEventNum = sessionData.pastEvents.reduce((max, e) => Math.max(max, e.eventNumber), 0);
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
      const speed = ((0.0025 + (i % 9) * 0.0004) / 3) * (i % 2 === 0 ? 1 : -1); // alternate CW/CCW
      return {
        ghostOrbitAngle: angle,
        ghostOrbitRadius: radius,
        ghostOrbitSpeed: speed,
        x: cx + radius * Math.cos(angle),
        y: cy + radius * Math.sin(angle),
      };
    };

    // Backlog ghosts (white, 0.25 opacity)
    sessionData.organizerBacklog.forEach((title, i) => {
      const orb = makeGhostOrbit();
      newNodes.push({
        id: `ghost-backlog-${i}`,
        type: 'ghost-backlog',
        name: title,
        r: 35,
        ...orb,
      } as GhostNode);
    });

    // Partner ghosts + green blobs
    sessionData.partnerTopics.slice(0, 20).forEach((partner, pi) => {
      // Ghost for each partner topic (up to 3) — topics are now objects, use .title
      partner.topics.slice(0, 3).forEach((topic, ti) => {
        const orb = makeGhostOrbit();
        newNodes.push({
          id: `ghost-partner-${pi}-${ti}`,
          type: 'ghost-partner',
          name: topic.title,
          r: 35,
          ...orb,
        } as GhostNode);
      });

      // Green blob for partner interest
      newNodes.push({
        id: `green-${pi}`,
        type: 'green',
        companyName: partner.companyName,
        logoUrl: partner.logoUrl,
        topicName: partner.topics[0]?.title ?? '',
        r: 45,
        absorbed: false,
        clusterAttractions: computeClusterAttractions(partner.topics),
        linkedBlobsByCluster: {},
        x: 100 + Math.random() * (w - 200),
        y: 100 + Math.random() * (h - 200),
      } as GreenBlobNode);
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
        r: 28,
        isActive: false,
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
            companies: blue.absorbedLogos.map((l) => l.companyName),
          };
        })
    );
  }, []);

  // ─── TICK HANDLER (D3 mutates DOM directly) ───────────────────────────────

  const tickHandler = useCallback((g: d3.Selection<SVGGElement, unknown, null, undefined>) => {
    const maxEvt = mostRecentEventNumRef.current;

    // Ghost orbit — advance each ghost's angle and pin it to the orbit path via fx/fy.
    // Skips the currently-dragged node so the user has full position control.
    const cx = window.innerWidth / 2;
    const cy = window.innerHeight / 2;
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
      ghost.fx = cx + ghost.ghostOrbitRadius * Math.cos(ghost.ghostOrbitAngle);
      ghost.fy = cy + ghost.ghostOrbitRadius * Math.sin(ghost.ghostOrbitAngle);
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

    // Hide entire green blob group — skip nodes currently in a fade transition
    g.selectAll<SVGGElement, GreenBlobNode>('.green-blob-group')
      .filter((d) => !fadingGreenIds.current.has(d.id))
      .attr('opacity', (d) => (d.absorbed ? 0 : 1));

    // Update red star opacity + glow
    g.selectAll<SVGGElement, RedStarNode>('.red-star-group')
      .select<SVGPolygonElement>('polygon')
      .attr('opacity', (d) => (d.isActive ? 1.0 : 0.15))
      .attr('filter', (d) => (d.isActive ? 'url(#red-glow)' : null));

    // Red repulsion from blue blobs when active
    nodesRef.current.forEach((node) => {
      if (node.type === 'red-star') {
        const red = node as RedStarNode;
        if (red.isActive && !red.orbiting) {
          const strength = Math.max(0, 1 - (maxEvt - red.eventNumber) / 6) * 150;
          nodesRef.current.forEach((n2) => {
            if (n2.type !== 'blue') return;
            const dx = (red.x ?? 0) - (n2.x ?? 0);
            const dy = (red.y ?? 0) - (n2.y ?? 0);
            const dist = Math.sqrt(dx * dx + dy * dy);
            if (dist > 0) {
              red.vx = (red.vx ?? 0) + (dx / dist) * (strength / 500);
              red.vy = (red.vy ?? 0) + (dy / dist) * (strength / 500);
            }
          });
        }
      }
    });

    // Update absorbed logos inside blue blobs — scattered orbit positions
    g.selectAll<SVGGElement, BlueBlobNode>('.blue-blob-group').each(function (d) {
      const grp = d3.select(this);
      const logos = grp
        .selectAll<SVGImageElement, AbsorbedLogo>('.absorbed-logo')
        .data(d.absorbedLogos, (l) => l.companyName);

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
          // Fade out the logo, then restore the green blob with a matching fade-in
          d3.select(event.currentTarget as SVGImageElement)
            .transition()
            .duration(1200)
            .attr('opacity', 0)
            .on('end', () => {
              d.absorbedLogos = d.absorbedLogos.filter((l) => l.companyName !== logo.companyName);
              syncTreeSummary();
              const green = nodesRef.current.find(
                (n) => n.type === 'green' && (n as GreenBlobNode).companyName === logo.companyName
              ) as GreenBlobNode | undefined;
              if (green) {
                green.absorbed = false;
                green.linkedBlobsByCluster = {};
                const ejectDist = d.r + green.r + 15;
                green.x = (d.x ?? 0) + ejectDist * Math.cos(logo.orbitAngle);
                green.y = (d.y ?? 0) + ejectDist * Math.sin(logo.orbitAngle);
                green.vx = Math.cos(logo.orbitAngle) * 3;
                green.vy = Math.sin(logo.orbitAngle) * 3;
                green.fx = null;
                green.fy = null;
                linksRef.current = linksRef.current.filter((l) => {
                  const src = typeof l.source === 'string' ? l.source : (l.source as SimNode).id;
                  return src !== green.id;
                });
                (simRef.current?.force('link') as d3.ForceLink<SimNode, SimLink> | null)?.links(
                  linksRef.current
                );
                // Fade in the restored green blob
                fadingGreenIds.current.add(green.id);
                gRef.current
                  ?.selectAll<SVGGElement, GreenBlobNode>('.green-blob-group')
                  .filter((gd) => gd.id === green.id)
                  .attr('opacity', 0)
                  .transition()
                  .duration(1200)
                  .attr('opacity', 1)
                  .on('end', () => fadingGreenIds.current.delete(green.id));
              }
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

      // Raise topic-name text above logos so it's never hidden
      grp.select('text').raise();
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
          .append('circle')
          .attr('r', blue.r)
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
        group.style('cursor', 'pointer').on('dblclick', (_, d) => {
          setAcceptBlob(d as BlueBlobNode);
        });
        break;
      }
      case 'green': {
        const green = node as GreenBlobNode;
        group.attr('class', 'node-group green-blob-group');
        group
          .append('circle')
          .attr('class', 'green-blob')
          .attr('r', green.r)
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
          .attr('r', ghost.r)
          .attr('fill', fillColor)
          .attr('opacity', fillOpacity);
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
        group
          .append('polygon')
          .attr('points', starPointsRelative(red.r, red.r * 0.42, 5))
          .attr('fill', '#f44336')
          .attr('opacity', red.isActive ? 1.0 : 0.15)
          .attr('filter', red.isActive ? 'url(#red-glow)' : null);
        group
          .append('text')
          .attr('text-anchor', 'middle')
          .attr('dy', red.r + 14)
          .attr('fill', 'rgba(255,120,120,0.8)')
          .attr('font-size', '9px')
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
            green.absorbed = true;
            if (!blueTarget.absorbedLogos.some((l) => l.companyName === green.companyName)) {
              blueTarget.absorbedLogos.push({
                companyName: green.companyName,
                logoUrl: green.logoUrl,
                orbitAngle: Math.random() * Math.PI * 2,
                orbitRadius: blueTarget.r * (0.2 + Math.random() * 0.45),
                orbitSpeed: (0.0013 + Math.random() * 0.0027) * (Math.random() < 0.5 ? 1 : -1),
              });
            }
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
                green.fx = null;
                green.fy = null;
              });
            syncTreeSummary();
            clearMergeHalos();
            return;
          }
        }

        const nearby = findNearbyCompatible(d, 30);
        if (nearby) {
          if (d.type === 'red-star' && nearby.type === 'blue') {
            // Red star dropped on blue → orbit
            (d as RedStarNode).orbiting = nearby.id;
            d.fx = null;
            d.fy = null;
          } else if (
            d.type === 'blue' &&
            nearby.type === 'blue' &&
            mergeHaloNodesRef.current.has(d.id)
          ) {
            // Two blue blobs merge
            mergeBlobs(d, nearby);
            return;
          }
        }

        d.fx = null;
        d.fy = null;
        clearMergeHalos();
      });

  const findNearbyCompatible = (node: SimNode, threshold: number): SimNode | null => {
    for (const other of nodesRef.current) {
      if (other.id === node.id) continue;
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
        .select<SVGCircleElement>('circle')
        .attr('stroke', 'rgba(255,255,255,0.9)')
        .attr('stroke-width', 4);
    } else {
      clearMergeHalos();
    }
  };

  const clearMergeHalos = () => {
    if (gRef.current) {
      gRef.current
        .selectAll<SVGCircleElement, SimNode>('.node-group circle')
        .attr('stroke', null)
        .attr('stroke-width', null);
    }
    mergeHaloNodesRef.current.clear();
  };

  const mergeBlobs = (a: SimNode, b: SimNode) => {
    const larger = a.r >= b.r ? (a as BlueBlobNode) : (b as BlueBlobNode);
    const smaller = a.r < b.r ? (a as BlueBlobNode) : (b as BlueBlobNode);

    // Absorb logos from smaller into larger
    smaller.absorbedLogos.forEach((logo) => {
      if (!larger.absorbedLogos.some((l) => l.companyName === logo.companyName)) {
        larger.absorbedLogos.push(logo);
      }
    });
    larger.r = Math.min(100, larger.r + 8);

    // Remove smaller node + its links
    nodesRef.current = nodesRef.current.filter((n) => n.id !== smaller.id);
    linksRef.current = linksRef.current.filter(
      (l) =>
        (typeof l.target === 'string' ? l.target : (l.target as SimNode).id) !== smaller.id &&
        (typeof l.source === 'string' ? l.source : (l.source as SimNode).id) !== smaller.id
    );
    setBlueBlobIds((ids) => ids.filter((id) => id !== smaller.id));
    syncTreeSummary();

    larger.fx = null;
    larger.fy = null;
    renderAll();
  };

  // ─── GHOST → BLUE SPAWN ───────────────────────────────────────────────────

  const spawnBlueFromGhost = useCallback((ghost: GhostNode) => {
    nodesRef.current = nodesRef.current.filter((n) => n.id !== ghost.id);
    addBlueBlobAt(ghost.name, ghost.x ?? window.innerWidth / 2, ghost.y ?? window.innerHeight / 2);
    syncTreeSummary();
  }, []);

  // ─── ADD BLUE BLOB ────────────────────────────────────────────────────────

  const addBlueBlobAt = useCallback(
    (name: string, x: number, y: number): string => {
      const id = mkId();
      const r = Math.max(40, Math.min(100, name.length * 5));
      const blue: BlueBlobNode = {
        id,
        type: 'blue',
        name,
        r,
        x,
        y,
        absorbedLogos: [],
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

          // Update forceLink strengths for green → blue
          updateGreenLinks(id, sim.cluster);

          // Activate matching red stars
          activateRedStars(sim.relatedPastEventNumbers);

          simRef.current?.alpha(0.3).restart();
        })
        .catch(() => {
          // Similarity is optional — ignore failures
        });

      return id;
    },
    [eventCode]
  );

  /**
   * Wire green blobs toward a newly spawned blue blob.
   *
   * Each green can hold one forceLink per cluster simultaneously (multi-link).
   * When a new blue appears for cluster X, every green that has submitted topics
   * in cluster X gets a link with strength = clusterAttractions[X].
   * If the green was already linked to an older blue for the same cluster,
   * that link is replaced (only one blue per cluster per green at a time).
   *
   * BUSINESS_OTHER blue blobs attract nobody — they represent unclassified topics.
   */
  const updateGreenLinks = (blueBlobId: string, blueCluster: string) => {
    if (blueCluster === 'BUSINESS_OTHER') return;

    nodesRef.current.forEach((n) => {
      if (n.type !== 'green') return;
      const green = n as GreenBlobNode;

      const strength = green.clusterAttractions[blueCluster] ?? 0;
      if (strength < 0.05) return;

      // Remove existing link for this cluster (replaced by the new blue)
      const oldBlueid = green.linkedBlobsByCluster[blueCluster];
      if (oldBlueid) {
        linksRef.current = linksRef.current.filter((l) => {
          const src = typeof l.source === 'string' ? l.source : (l.source as SimNode).id;
          const tgt = typeof l.target === 'string' ? l.target : (l.target as SimNode).id;
          return !(src === green.id && tgt === oldBlueid);
        });
      }

      // Add link toward this blue blob for this cluster
      linksRef.current.push({ source: green.id, target: blueBlobId, strength });
      green.linkedBlobsByCluster[blueCluster] = blueBlobId;
    });

    (simRef.current?.force('link') as d3.ForceLink<SimNode, SimLink> | null)?.links(
      linksRef.current
    );
  };

  const activateRedStars = (relatedEventNumbers: number[]) => {
    const maxEvt = mostRecentEventNumRef.current;
    nodesRef.current.forEach((n) => {
      if (n.type !== 'red-star') return;
      const red = n as RedStarNode;
      if (relatedEventNumbers.includes(red.eventNumber) && maxEvt - red.eventNumber <= 6) {
        red.isActive = true;
      }
    });
  };

  // ─── KEYBOARD HANDLER ─────────────────────────────────────────────────────

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (acceptBlob) return; // Dialog open — let MUI handle keys

      if (e.ctrlKey && e.key === 'z') {
        e.preventDefault();
        const lastId = blueBlobIds[blueBlobIds.length - 1];
        if (lastId) {
          const removedBlob = nodesRef.current.find((n) => n.id === lastId) as
            | BlueBlobNode
            | undefined;
          const removedRelated = removedBlob?.relatedPastEventNumbers ?? [];

          nodesRef.current = nodesRef.current.filter((n) => n.id !== lastId);
          linksRef.current = linksRef.current.filter((l) => {
            const tgt = typeof l.target === 'string' ? l.target : (l.target as SimNode).id;
            return tgt !== lastId;
          });

          // Clear linkedBlobsByCluster entries pointing at the removed blue
          nodesRef.current.forEach((n) => {
            if (n.type === 'green') {
              const green = n as GreenBlobNode;
              for (const cluster of Object.keys(green.linkedBlobsByCluster)) {
                if (green.linkedBlobsByCluster[cluster] === lastId) {
                  delete green.linkedBlobsByCluster[cluster];
                }
              }
            }
          });

          // Deactivate red stars whose only activator was the removed blue (P3)
          if (removedRelated.length > 0) {
            const stillActivated = new Set<number>();
            nodesRef.current
              .filter((n) => n.type === 'blue')
              .forEach((b) => {
                (b as BlueBlobNode).relatedPastEventNumbers?.forEach((num) =>
                  stillActivated.add(num)
                );
              });
            nodesRef.current.forEach((n) => {
              if (n.type === 'red-star') {
                const red = n as RedStarNode;
                if (
                  removedRelated.includes(red.eventNumber) &&
                  !stillActivated.has(red.eventNumber)
                ) {
                  red.isActive = false;
                  red.orbiting = undefined;
                }
              }
            });
          }

          setBlueBlobIds((ids) => ids.slice(0, -1));
          syncTreeSummary();
          renderAll();
        }
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
        setShowInput(true);
        setInputValue(e.key);
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [acceptBlob, showInput, blueBlobIds, renderAll]);

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
    addBlueBlobAt(name, window.innerWidth + 50, window.innerHeight / 2);
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
    async (topicCode: string, note: string) => {
      try {
        await blobTopicService.acceptTopic(eventCode, topicCode, note);
        navigate(`/organizer/events/${eventCode}?tab=speakers`);
      } catch {
        setAcceptError(
          t('blobSelector.accept.saveError', {
            defaultValue: 'Failed to save topic selection. Please try again.',
          })
        );
      }
      setAcceptBlob(null);
    },
    [eventCode, navigate, t]
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
      `}</style>

      {/* Full-viewport SVG canvas — D3 renders everything inside */}
      <svg
        ref={svgRef}
        data-testid="blob-canvas"
        style={{ width: '100vw', height: '100vh', display: 'block', overflow: 'hidden' }}
      />

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
          <Box sx={{ width: 220, height: '100%', overflowY: 'auto', p: 1.5 }}>
            <Typography
              sx={{
                color: 'rgba(255,255,255,0.35)',
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
                sx={{ color: 'rgba(255,255,255,0.25)', fontSize: 10, fontStyle: 'italic' }}
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
                        color: 'rgba(255,255,255,0.25)',
                        pl: 2,
                        fontSize: 10,
                        fontStyle: 'italic',
                      }}
                    >
                      no partners
                    </Typography>
                  ) : (
                    topic.companies.map((company) => (
                      <Box
                        key={company}
                        sx={{ display: 'flex', alignItems: 'center', gap: 0.5, pl: 1.5, py: '2px' }}
                      >
                        <Typography
                          sx={{
                            color: 'rgba(255,255,255,0.2)',
                            fontSize: 8,
                            lineHeight: 1,
                            flexShrink: 0,
                          }}
                        >
                          └
                        </Typography>
                        <Typography sx={{ color: 'rgba(144,238,144,0.85)', fontSize: 10 }}>
                          {company}
                        </Typography>
                      </Box>
                    ))
                  )}
                </Box>
              ))
            )}
          </Box>
        </Box>
      </Box>
    </>
  );
};

export default BlobTopicSelector;
