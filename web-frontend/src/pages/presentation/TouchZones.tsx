/**
 * TouchZones
 * Story 10.8 mobile enhancement: tap zones for touch/tablet navigation.
 *
 * Three invisible tap areas overlaid on the presentation canvas:
 *   - Left  zone (20% w, full height - bottom)  → go to previous section
 *   - Right zone (20% w, full height - bottom)  → go to next section
 *   - Bottom zone (full w, 15% h)               → toggle break overlay (≡ B key)
 *
 * Tap vs swipe is distinguished per zone: movement > TAP_MAX_MOVE = swipe
 * (handled by useTouchNavigation at window level), movement ≤ TAP_MAX_MOVE = tap
 * (handled here). Calling e.preventDefault() in onTouchEnd stops the
 * synthetic click from double-firing.
 *
 * Visual design: zones are transparent by default. Each zone shows a subtle
 * icon (10% opacity) that brightens on hover (40%) and flashes on tap.
 * This is minimally distracting during projection while remaining discoverable.
 */

import React, { type JSX, useRef, useState, useCallback } from 'react';

/** Max pixel movement for a gesture to still count as a tap (not a swipe). */
const TAP_MAX_MOVE = 20;

/** Duration of the visual tap-flash in ms. */
const FLASH_DURATION_MS = 200;

interface TouchZonesProps {
  onNext: () => void;
  onPrev: () => void;
  onToggleBlank: () => void;
  isBlankActive: boolean;
}

export function TouchZones({
  onNext,
  onPrev,
  onToggleBlank,
  isBlankActive,
}: TouchZonesProps): JSX.Element {
  return (
    <>
      {/* Left zone — go back */}
      <TapZone
        onTap={onPrev}
        icon="‹"
        iconFontSize="3rem"
        style={{
          position: 'fixed',
          zIndex: 10,
          left: 0,
          top: 0,
          width: '20%',
          bottom: '15%',
        }}
        aria-label="Previous section"
      />

      {/* Right zone — advance */}
      <TapZone
        onTap={onNext}
        icon="›"
        iconFontSize="3rem"
        style={{
          position: 'fixed',
          zIndex: 10,
          right: 0,
          top: 0,
          width: '20%',
          bottom: '15%',
        }}
        aria-label="Next section"
      />

      {/* Bottom zone — toggle break (B key) */}
      <TapZone
        onTap={onToggleBlank}
        icon={isBlankActive ? '▶' : '⏸'}
        iconFontSize="1.4rem"
        style={{
          position: 'fixed',
          zIndex: 10,
          left: 0,
          right: 0,
          bottom: 0,
          height: '15%',
        }}
        alignItems="flex-start"
        paddingTop="0.6rem"
        aria-label={isBlankActive ? 'Resume presentation' : 'Show break'}
      />
    </>
  );
}

// ---------------------------------------------------------------------------
// TapZone — individual tappable region
// ---------------------------------------------------------------------------

interface TapZoneProps {
  onTap: () => void;
  icon: string;
  iconFontSize?: string;
  style: React.CSSProperties;
  alignItems?: 'center' | 'flex-start' | 'flex-end';
  paddingTop?: string;
  'aria-label'?: string;
}

function TapZone({
  onTap,
  icon,
  iconFontSize = '2rem',
  style,
  alignItems = 'center',
  paddingTop = '0',
  'aria-label': ariaLabel,
}: TapZoneProps): JSX.Element {
  const touchStartRef = useRef<{ x: number; y: number } | null>(null);
  const [flashing, setFlashing] = useState(false);
  const [hovered, setHovered] = useState(false);

  const triggerTap = useCallback(() => {
    setFlashing(true);
    setTimeout(() => setFlashing(false), FLASH_DURATION_MS);
    onTap();
  }, [onTap]);

  return (
    <div
      role="button"
      aria-label={ariaLabel}
      tabIndex={-1}
      style={{
        ...style,
        cursor: 'pointer',
        userSelect: 'none',
        WebkitUserSelect: 'none' as React.CSSProperties['userSelect'],
        WebkitTapHighlightColor: 'transparent',
        display: 'flex',
        alignItems,
        justifyContent: 'center',
        paddingTop,
        background: flashing
          ? 'rgba(79, 156, 249, 0.20)'
          : hovered
            ? 'rgba(255, 255, 255, 0.04)'
            : 'transparent',
        transition: 'background 0.15s ease',
      }}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      /* Mouse click — mouse/trackpad fallback */
      onClick={triggerTap}
      /* Touch tap — avoids the ~300ms click delay and double-fire */
      onTouchStart={(e) => {
        const t = e.touches[0];
        touchStartRef.current = { x: t.clientX, y: t.clientY };
      }}
      onTouchEnd={(e) => {
        if (!touchStartRef.current) return;
        const t = e.changedTouches[0];
        const dx = Math.abs(t.clientX - touchStartRef.current.x);
        const dy = Math.abs(t.clientY - touchStartRef.current.y);
        touchStartRef.current = null;

        if (dx <= TAP_MAX_MOVE && dy <= TAP_MAX_MOVE) {
          // Prevent the synthetic click from double-firing
          e.preventDefault();
          triggerTap();
        }
        // Otherwise: it's a swipe — useTouchNavigation handles it at window level
      }}
    >
      <span
        style={{
          fontSize: iconFontSize,
          lineHeight: 1,
          opacity: hovered || flashing ? 0.5 : 0.12,
          transition: 'opacity 0.2s ease',
          color: '#ffffff',
          pointerEvents: 'none',
          fontWeight: 300,
          letterSpacing: '-0.05em',
        }}
      >
        {icon}
      </span>
    </div>
  );
}
