/**
 * PresentationPage
 * Story 10.8a: Moderator Presentation Page — Functional
 * Story 10.8b: Framer Motion animation layer
 *   - FLIP agenda ↔ sidebar (ACs #1-4) via layoutId on conditionally-rendered elements
 *   - Section spring transitions (ACs #5-7) via AnimatePresence mode="wait"
 *
 * FLIP strategy: Two conditional motion.div elements share layoutId="agenda-view".
 * When center-stage unmounts and sidebar mounts (or vice versa), Framer detects the
 * same layoutId appearing at a new position and performs the FLIP.
 *
 * WHY NOT `layout` on a single always-mounted element:
 *   Framer Motion's `layout` sets inline `transform` which overwrites CSS
 *   `transform: translate(-50%, -50%)` centering → element appears in wrong position.
 *   Using `layoutId` with flexbox wrappers avoids all transform composition conflicts.
 *
 * Route owner for /present/:eventCode.
 * Public — no authentication required.
 *
 * ACs: all (orchestration)
 */

import React, { type JSX, useState, useCallback } from 'react';
import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { motion, AnimatePresence } from 'framer-motion';
import { BATbernLoader } from '@/components/shared/BATbernLoader';
import { usePresentationData } from '@/hooks/usePresentationData';
import {
  usePresentationSections,
  getPreBreakSessionSlugs,
  getFirstPostBreakSession,
} from '@/hooks/usePresentationSections';
import { useKeyboardNavigation } from '@/hooks/useKeyboardNavigation';
import { useTouchNavigation } from '@/hooks/useTouchNavigation';
import { TopicBackground } from './presentation/TopicBackground';
import { TouchZones } from './presentation/TouchZones';
import { BlankOverlay } from './presentation/BlankOverlay';
import { AgendaView } from './presentation/AgendaView';
import { SectionDots } from './presentation/SectionDots';
import { WelcomeSlide } from './presentation/slides/WelcomeSlide';
import { AboutSlide } from './presentation/slides/AboutSlide';
import { CommitteeSlide } from './presentation/slides/CommitteeSlide';
import { TopicRevealSlide } from './presentation/slides/TopicRevealSlide';
import { AgendaPreviewSlide } from './presentation/slides/AgendaPreviewSlide';
import { SessionSlide } from './presentation/slides/SessionSlide';
import { BreakSlide } from './presentation/slides/BreakSlide';
import { AgendaRecapSlide } from './presentation/slides/AgendaRecapSlide';
import { UpcomingEventsSlide } from './presentation/slides/UpcomingEventsSlide';
import { AperoSlide } from './presentation/slides/AperoSlide';
import type { PresentationSection } from '@/hooks/usePresentationSections';

// --------------------------------------------------------------------------
// Animation constants
// --------------------------------------------------------------------------

/** Spring used for FLIP agenda ↔ sidebar (ACs #1-4) */
const AGENDA_FLIP_SPRING = { type: 'spring' as const, stiffness: 100, damping: 22, mass: 1 };

/** Width of the center-stage AgendaView — shared by both layouts so FLIP only animates position */
const AGENDA_CENTER_WIDTH = 'min(1100px, 88vw)';

/** Slide enter/exit variants for directional spring (ACs #5-7) */
const slideVariants = {
  enter: (dir: number) => ({ x: dir > 0 ? 80 : -80, opacity: 0 }),
  center: { x: 0, opacity: 1 },
  exit: (dir: number) => ({ x: dir > 0 ? -80 : 80, opacity: 0 }),
};
const slideTransition = { type: 'spring' as const, stiffness: 120, damping: 20 };

// --------------------------------------------------------------------------
// PresentationPage
// --------------------------------------------------------------------------

export function PresentationPage(): JSX.Element {
  const { eventCode } = useParams<{ eventCode: string }>();
  const { t } = useTranslation();

  const { data, isLoading, isInitialLoadError, refetch } = usePresentationData(eventCode ?? '');
  const sections = usePresentationSections(data.event, data.sessions);

  const [currentIndex, setCurrentIndex] = useState(0);
  const [isBlankActive, setIsBlankActive] = useState(false);
  // direction: +1 = forward, -1 = back (drives directional slide spring, ACs #5-7)
  const [direction, setDirection] = useState<number>(1);

  const goNext = useCallback(() => {
    setDirection(1);
    setCurrentIndex((i) => Math.min(i + 1, sections.length - 1));
  }, [sections.length]);

  const goPrev = useCallback(() => {
    setDirection(-1);
    setCurrentIndex((i) => Math.max(i - 1, 0));
  }, []);

  const toggleBlank = useCallback(() => {
    setIsBlankActive((active) => !active);
  }, []);

  useKeyboardNavigation({
    sectionCount: sections.length,
    currentIndex,
    isBlankActive,
    onNext: goNext,
    onPrev: goPrev,
    onToggleBlank: toggleBlank,
  });

  useTouchNavigation({
    isBlankActive,
    onNext: goNext,
    onPrev: goPrev,
    onToggleBlank: toggleBlank,
  });

  // -- Loading state --
  if (isLoading) {
    return (
      <div
        style={{
          ...fullscreenStyle('#0a0d14'),
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: '2rem',
        }}
      >
        <BATbernLoader size={120} speed="slow" />
        <div
          style={{
            fontSize: '3rem',
            fontWeight: 800,
            letterSpacing: '-0.02em',
            color: '#4f9cf9',
          }}
        >
          BAT<span style={{ color: 'rgba(255,255,255,0.7)' }}>bern</span>
        </div>
      </div>
    );
  }

  // -- Error state (AC #42) --
  if (isInitialLoadError || !data.event) {
    const hashtag = eventCode ? `#${eventCode}` : '';
    return (
      <div style={fullscreenStyle('#0a0d14')}>
        <div style={{ textAlign: 'center', color: '#ffffff', maxWidth: '600px' }}>
          <div
            style={{
              fontSize: '3rem',
              fontWeight: 800,
              color: '#4f9cf9',
              marginBottom: '0.5rem',
            }}
          >
            BATbern
          </div>
          {hashtag && (
            <div
              style={{
                fontSize: '1.5rem',
                color: 'rgba(255,255,255,0.5)',
                marginBottom: '1.5rem',
              }}
            >
              {hashtag}
            </div>
          )}
          <h1 style={{ fontSize: '2rem', marginBottom: '1rem' }}>{t('presentation.errorTitle')}</h1>
          <p style={{ fontSize: '1.25rem', color: 'rgba(255,255,255,0.6)', marginBottom: '2rem' }}>
            {t('presentation.errorMessage')}
          </p>
          <button
            onClick={refetch}
            style={{
              fontSize: '1.25rem',
              padding: '0.75rem 2rem',
              background: '#4f9cf9',
              color: '#ffffff',
              border: 'none',
              borderRadius: '8px',
              cursor: 'pointer',
            }}
          >
            {t('presentation.retryButton')}
          </button>
        </div>
      </div>
    );
  }

  const currentSection: PresentationSection | undefined = sections[currentIndex];

  // Derived values for cross-section data
  const preBreakSlugs = getPreBreakSessionSlugs(data.sessions);
  const firstPostBreakSession = getFirstPostBreakSession(data.sessions);

  // Section type flags
  const isSession = currentSection?.type === 'session';
  const isAgendaCenter =
    currentSection?.type === 'agenda-preview' || currentSection?.type === 'agenda-recap';
  const isBreakSection = currentSection?.type === 'break';

  // Current session slug for sidebar highlight (AC #19)
  const currentSessionSlug = isSession
    ? (currentSection.session?.sessionSlug ?? undefined)
    : undefined;

  // completedSessionSlugs only for agenda-recap (AC #14)
  const completedSessionSlugsForAgenda =
    currentSection?.type === 'agenda-recap' ? preBreakSlugs : undefined;

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        overflow: 'hidden',
        background: '#0a0d14',
        fontFamily:
          '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
      }}
    >
      {/* Persistent full-bleed background with Ken Burns zoom (ACs #33-36, #8) */}
      <TopicBackground imageUrl={data.event?.themeImageUrl ?? undefined} />

      {/* ----------------------------------------------------------------
          Agenda heading — separate from the FLIP element so its height
          doesn't affect the FLIP rect measurement. Lives in its own
          AnimatePresence so it can slide in/out with the section direction
          while the FLIP list animates independently. (ACs #1-4, #11, #14)
          ---------------------------------------------------------------- */}
      <AnimatePresence custom={direction}>
        {isAgendaCenter && (
          <motion.div
            key="agenda-heading"
            custom={direction}
            variants={slideVariants}
            initial="enter"
            animate="center"
            exit="exit"
            transition={slideTransition}
            style={{
              position: 'fixed',
              zIndex: 3,
              top: 'calc(50vh - 13rem)',
              left: 0,
              right: 0,
              display: 'flex',
              justifyContent: 'center',
              pointerEvents: 'none',
            }}
          >
            <h2
              style={{
                margin: 0,
                fontSize: '3rem',
                fontWeight: 700,
                color: '#4f9cf9',
              }}
            >
              Agenda
            </h2>
          </motion.div>
        )}
      </AnimatePresence>

      {/* ----------------------------------------------------------------
          FLIP agenda — center-stage (agenda-preview / agenda-recap)
          Simple centering wrapper — heading is a separate element above.
          ---------------------------------------------------------------- */}
      {isAgendaCenter && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            zIndex: 2,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            pointerEvents: 'none',
          }}
        >
          <motion.div
            layoutId="agenda-view"
            data-testid="agenda-flip-container"
            data-layout="center"
            transition={AGENDA_FLIP_SPRING}
            style={{ pointerEvents: 'auto', width: AGENDA_CENTER_WIDTH }}
          >
            <AgendaView
              sessions={data.sessions}
              completedSessionSlugs={completedSessionSlugsForAgenda}
              layout="center"
            />
          </motion.div>
        </div>
      )}

      {/* ----------------------------------------------------------------
          FLIP agenda — sidebar (session slides)
          Flexbox wrapper pins to left edge, centers vertically. (ACs #1-4, #17-22)
          ---------------------------------------------------------------- */}
      {isSession && (
        <div
          style={{
            position: 'fixed',
            left: 0,
            top: 0,
            bottom: 0,
            zIndex: 2,
            display: 'flex',
            alignItems: 'center',
            paddingLeft: '2rem',
          }}
        >
          <motion.div
            layoutId="agenda-view"
            data-testid="agenda-flip-container"
            data-layout="sidebar"
            transition={AGENDA_FLIP_SPRING}
            style={{ width: AGENDA_CENTER_WIDTH }}
            animate={{ width: '280px' }}
          >
            <AgendaView
              sessions={data.sessions}
              currentSessionSlug={currentSessionSlug}
              layout="sidebar"
            />
          </motion.div>
        </div>
      )}

      {/* Current section slide — shifted right when sidebar is visible (AC #17) */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          paddingLeft: isSession ? '320px' : 0,
          boxSizing: 'border-box',
        }}
      >
        {/* Section spring transitions — directional (ACs #5-7) */}
        <AnimatePresence mode="wait" custom={direction}>
          {currentSection && (
            <motion.div
              key={currentIndex}
              custom={direction}
              variants={slideVariants}
              initial="enter"
              animate="center"
              exit="exit"
              transition={slideTransition}
              style={{ position: 'absolute', inset: 0 }}
            >
              <SectionRenderer
                section={currentSection}
                data={data}
                firstPostBreakSession={firstPostBreakSession}
              />
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {/* Section dots progress indicator */}
      <SectionDots count={sections.length} current={currentIndex} />

      {/* Touch zones — left/right tap to navigate, bottom tap to toggle break (mobile/tablet) */}
      <TouchZones
        onNext={goNext}
        onPrev={goPrev}
        onToggleBlank={toggleBlank}
        isBlankActive={isBlankActive}
      />

      {/* B-key / bottom-zone break overlay — AnimatePresence fade 0.3s (ACs #9, #23-24, #29)
          When already on the break section, BreakSlide is rendered by SectionRenderer;
          omit it here to avoid duplicate animation instances. */}
      <BlankOverlay isActive={isBlankActive} onDismiss={toggleBlank}>
        <div style={{ position: 'fixed', inset: 0, background: '#0a0d14' }}>
          <TopicBackground imageUrl={data.event?.themeImageUrl ?? undefined} />
          {!isBreakSection && <BreakSlide />}
        </div>
      </BlankOverlay>
    </div>
  );
}

// --------------------------------------------------------------------------
// SectionRenderer — maps PresentationSection to the correct slide component
// --------------------------------------------------------------------------

interface SectionRendererProps {
  section: PresentationSection;
  data: ReturnType<typeof usePresentationData>['data'];
  firstPostBreakSession: ReturnType<typeof getFirstPostBreakSession>;
}

function SectionRenderer({
  section,
  data,
  firstPostBreakSession,
}: SectionRendererProps): JSX.Element | null {
  switch (section.type) {
    case 'welcome':
      return data.event ? <WelcomeSlide event={data.event} /> : null;

    case 'about':
      return (
        <AboutSlide
          aboutText={data.settings?.aboutText ?? ''}
          partnerCount={data.settings?.partnerCount ?? 0}
        />
      );

    case 'committee':
      return <CommitteeSlide organizers={data.organizers} />;

    case 'topic-reveal':
      return data.event ? <TopicRevealSlide event={data.event} /> : null;

    case 'agenda-preview':
      // AgendaView rendered by page-level layoutId="agenda-view" — slide shows heading only
      return <AgendaPreviewSlide />;

    case 'session':
      return section.session ? <SessionSlide session={section.session} /> : null;

    case 'break':
      return <BreakSlide firstPostBreakSession={firstPostBreakSession} />;

    case 'agenda-recap':
      // AgendaView rendered by page-level layoutId="agenda-view" — slide shows heading only
      return <AgendaRecapSlide />;

    case 'upcoming-events':
      return <UpcomingEventsSlide events={data.upcomingEvents} />;

    case 'apero':
      return <AperoSlide />;

    default:
      return null;
  }
}

// --------------------------------------------------------------------------
// Helpers
// --------------------------------------------------------------------------

function fullscreenStyle(bg: string): React.CSSProperties {
  return {
    position: 'fixed',
    inset: 0,
    background: bg,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontFamily:
      '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
  };
}

export default PresentationPage;
