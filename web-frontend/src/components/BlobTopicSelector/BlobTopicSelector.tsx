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
import { Alert, Box, Button, Snackbar, TextField } from '@mui/material';
import { FitScreen, MyLocation } from '@mui/icons-material';
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
} from './types';
import { blobTopicService } from '@/services/blobTopicService';
import AcceptTopicDialog from './AcceptTopicDialog';

// ─── helpers ──────────────────────────────────────────────────────────────────

let _nodeCounter = 0;
const mkId = () => `node-${++_nodeCounter}`;

function starPointsRelative(outer: number, inner: number, numPoints: number): string {
  const pts: string[] = [];
  for (let i = 0; i < numPoints * 2; i++) {
    const angle = (Math.PI / numPoints) * i - Math.PI / 2;
    const r = i % 2 === 0 ? outer : inner;
    pts.push(`${r * Math.cos(angle)},${r * Math.sin(angle)}`);
  }
  return pts.join(' ');
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

  // React state — only for UI elements that need re-renders
  const [showInput, setShowInput] = useState(false);
  const [inputValue, setInputValue] = useState('');
  const [blueBlobIds, setBlueBlobIds] = useState<string[]>([]);
  const [acceptBlob, setAcceptBlob] = useState<BlueBlobNode | null>(null);
  const [mostRecentEventNum, setMostRecentEventNum] = useState(0);
  const [acceptError, setAcceptError] = useState<string | null>(null);

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
      const radius = 180 + ring * 100;
      const speed = (0.0025 + (i % 9) * 0.0004) * (i % 2 === 0 ? 1 : -1); // alternate CW/CCW
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
      // Ghost for each partner topic (up to 3)
      partner.topics.slice(0, 3).forEach((topic, ti) => {
        const orb = makeGhostOrbit();
        newNodes.push({
          id: `ghost-partner-${pi}-${ti}`,
          type: 'ghost-partner',
          name: topic,
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
        topicName: partner.topics[0] ?? '',
        r: 45,
        absorbed: false,
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
            red.orbitAngle = (red.orbitAngle ?? 0) + 0.02;
            const orbitRadius = blue.r + red.r + 20;
            red.x = (blue.x ?? 0) + orbitRadius * Math.cos(red.orbitAngle);
            red.y = (blue.y ?? 0) + orbitRadius * Math.sin(red.orbitAngle);
            red.fx = red.x;
            red.fy = red.y;
          }
        }
      }

      // Absorption: green blobs absorbed by nearby blue blobs
      if (node.type === 'green') {
        const green = node as GreenBlobNode;
        if (!green.absorbed) {
          nodesRef.current.forEach((n2) => {
            if (n2.type !== 'blue') return;
            const blue = n2 as BlueBlobNode;
            const dx = (green.x ?? 0) - (blue.x ?? 0);
            const dy = (green.y ?? 0) - (blue.y ?? 0);
            const dist = Math.sqrt(dx * dx + dy * dy);
            if (dist < green.r + blue.r - 10) {
              green.absorbed = true;
              if (!blue.absorbedLogos.some((l) => l.companyName === green.companyName)) {
                // Assign a random orbit position so logos scatter inside the blue blob
                blue.absorbedLogos.push({
                  companyName: green.companyName,
                  logoUrl: green.logoUrl,
                  orbitAngle: Math.random() * Math.PI * 2,
                  orbitRadius: blue.r * (0.2 + Math.random() * 0.45),
                  orbitSpeed: (0.004 + Math.random() * 0.008) * (Math.random() < 0.5 ? 1 : -1),
                });
              }
            }
          });
        }
      }
    });

    // Update positions of all node groups
    g.selectAll<SVGGElement, SimNode>('.node-group').attr(
      'transform',
      (d) => `translate(${d.x ?? 0},${d.y ?? 0})`
    );

    // Hide entire green blob group (circle + logo + label) when absorbed
    // Previously only the circle was hidden, leaving the logo floating at its position
    g.selectAll<SVGGElement, GreenBlobNode>('.green-blob-group').attr('opacity', (d) =>
      d.absorbed ? 0 : 1
    );

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

      // Enter: add new logo images with click-to-eject handler
      logos
        .enter()
        .append('image')
        .attr('class', 'absorbed-logo')
        .attr('width', 24)
        .attr('height', 24)
        .attr('href', (l) => l.logoUrl)
        .style('cursor', 'pointer')
        .on('click', (event, logo) => {
          event.stopPropagation();
          // Remove logo from blue blob
          d.absorbedLogos = d.absorbedLogos.filter((l) => l.companyName !== logo.companyName);
          // Find the green node and restore it
          const green = nodesRef.current.find(
            (n) => n.type === 'green' && (n as GreenBlobNode).companyName === logo.companyName
          ) as GreenBlobNode | undefined;
          if (green) {
            green.absorbed = false;
            green.bestSimilarity = undefined;
            green.linkedBlobId = undefined;
            // Eject in the direction of the logo's current orbit angle
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
          }
          simRef.current?.alpha(0.2).restart();
        });

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
        group
          .append('text')
          .attr('text-anchor', 'middle')
          .attr('dy', '0.35em')
          .attr('fill', 'white')
          .attr('font-size', '12px')
          .attr('pointer-events', 'none')
          .text(blue.name);
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
        group
          .append('text')
          .attr('text-anchor', 'middle')
          .attr('dy', '0.35em')
          .attr('fill', 'rgba(255,255,255,0.7)')
          .attr('font-size', '9px')
          .attr('pointer-events', 'none')
          .text(ghost.name.length > 18 ? ghost.name.slice(0, 16) + '…' : ghost.name);
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

    larger.fx = null;
    larger.fy = null;
    renderAll();
  };

  // ─── GHOST → BLUE SPAWN ───────────────────────────────────────────────────

  const spawnBlueFromGhost = useCallback((ghost: GhostNode) => {
    nodesRef.current = nodesRef.current.filter((n) => n.id !== ghost.id);
    addBlueBlobAt(ghost.name, ghost.x ?? window.innerWidth / 2, ghost.y ?? window.innerHeight / 2);
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
          updateGreenLinks(id, sim.similarityScore);

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

  const updateGreenLinks = (blueBlobId: string, strength: number) => {
    nodesRef.current.forEach((n) => {
      if (n.type !== 'green') return;
      const green = n as GreenBlobNode;

      // Only re-route this green if the new blue is at least as attractive (P1 fix)
      if (strength < (green.bestSimilarity ?? 0)) return;

      green.bestSimilarity = strength;

      // Remove old link from this green
      linksRef.current = linksRef.current.filter((l) => {
        const src = typeof l.source === 'string' ? l.source : (l.source as SimNode).id;
        return src !== green.id;
      });

      // Add new link toward this blue blob
      linksRef.current.push({ source: green.id, target: blueBlobId, strength });
      green.linkedBlobId = blueBlobId;
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

          // Reset green best-similarity for greens that were linked to the removed blue (P3)
          nodesRef.current.forEach((n) => {
            if (n.type === 'green') {
              const green = n as GreenBlobNode;
              if (green.linkedBlobId === lastId) {
                green.bestSimilarity = undefined;
                green.linkedBlobId = undefined;
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
        .ghost-group { animation: blobPulse 3s ease-in-out infinite; }
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
    </>
  );
};

export default BlobTopicSelector;
