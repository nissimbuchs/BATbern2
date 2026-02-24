/**
 * Onboarding Overlay
 * Story 10.4: Blob Topic Selector (Task 15)
 * 10-second scripted D3 animation shown on first visit.
 * No text, auto-dismisses, sets localStorage flag.
 */

import React, { useEffect, useRef } from 'react';
import * as d3 from 'd3';
import { Box } from '@mui/material';

export const ONBOARDING_KEY = 'batbern_blob_onboarding_seen';

function starPointsAbsolute(
  cx: number,
  cy: number,
  outerR: number,
  innerR: number,
  numPoints = 5
): string {
  const pts: string[] = [];
  for (let i = 0; i < numPoints * 2; i++) {
    const angle = (Math.PI / numPoints) * i - Math.PI / 2;
    const r = i % 2 === 0 ? outerR : innerR;
    pts.push(`${cx + r * Math.cos(angle)},${cy + r * Math.sin(angle)}`);
  }
  return pts.join(' ');
}

interface OnboardingOverlayProps {
  onComplete: () => void;
}

const OnboardingOverlay: React.FC<OnboardingOverlayProps> = ({ onComplete }) => {
  const svgRef = useRef<SVGSVGElement>(null);

  useEffect(() => {
    if (!svgRef.current) return;

    const svg = d3.select(svgRef.current);
    const w = window.innerWidth;
    const h = window.innerHeight;
    const cx = w / 2;
    const cy = h / 2;

    const g = svg.append('g');

    // Ghost blob (white, will pulse then awaken)
    const ghost = g
      .append('circle')
      .attr('cx', cx)
      .attr('cy', cy + 60)
      .attr('r', 50)
      .attr('fill', 'rgba(255,255,255,0.25)')
      .attr('stroke', 'rgba(255,255,255,0.5)')
      .attr('stroke-width', 2);

    // Blue blob (appears when ghost awakens)
    const blue = g
      .append('circle')
      .attr('cx', cx)
      .attr('cy', cy + 60)
      .attr('r', 55)
      .attr('fill', '#1976d2')
      .attr('opacity', 0);

    // Green blob (drifts toward blue)
    const green = g
      .append('circle')
      .attr('cx', cx + 200)
      .attr('cy', cy - 50)
      .attr('r', 40)
      .attr('fill', '#2e7d32')
      .attr('opacity', 0);

    // Red star (ignites at 6s)
    const red = g
      .append('polygon')
      .attr('points', starPointsAbsolute(cx - 180, cy + 20, 35, 14, 5))
      .attr('fill', '#f44336')
      .attr('opacity', 0.15);

    // Phase 1 (0-2s): ghost pulses
    (function pulse() {
      ghost
        .transition()
        .duration(500)
        .attr('r', 55)
        .transition()
        .duration(500)
        .attr('r', 50)
        .transition()
        .duration(500)
        .attr('r', 55)
        .transition()
        .duration(500)
        .attr('r', 50);
    })();

    // Phase 2 (2-4s): ghost awakens → blue
    const t2 = setTimeout(() => {
      ghost.transition().duration(500).attr('opacity', 0);
      blue.transition().duration(800).attr('opacity', 1);
    }, 2000);

    // Phase 3 (4-6s): green drifts toward blue
    const t3 = setTimeout(() => {
      green
        .transition()
        .duration(400)
        .attr('opacity', 1)
        .transition()
        .duration(1600)
        .ease(d3.easeQuadInOut)
        .attr('cx', cx + 60)
        .attr('cy', cy + 70);
    }, 4000);

    // Phase 4 (6-8s): red star ignites
    const t4 = setTimeout(() => {
      red.transition().duration(600).attr('opacity', 1).attr('fill', '#ff1744');
    }, 6000);

    // Phase 5 (8-10s): settle + complete
    const t5 = setTimeout(() => {
      localStorage.setItem(ONBOARDING_KEY, 'true');
      onComplete();
    }, 10000);

    return () => {
      clearTimeout(t2);
      clearTimeout(t3);
      clearTimeout(t4);
      clearTimeout(t5);
      g.remove();
    };
  }, [onComplete]);

  return (
    <Box
      sx={{
        position: 'fixed',
        inset: 0,
        zIndex: 9999,
        pointerEvents: 'none',
      }}
    >
      <svg ref={svgRef} width="100%" height="100%" style={{ position: 'absolute', inset: 0 }} />
    </Box>
  );
};

export default OnboardingOverlay;
