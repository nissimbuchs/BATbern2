/**
 * PresentationPage
 * Story 10.8a: Moderator Presentation Page — Functional
 * Story 10.8b: Framer Motion animation layer
 *   - FLIP agenda ↔ sidebar (ACs #1-4) via motion.div layout
 *   - Section spring transitions (ACs #5-7) via AnimatePresence mode="wait"
 *
 * Route owner for /present/:eventCode.
 * Manages section state, keyboard navigation, layout orchestration.
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
import { TopicBackground } from './presentation/TopicBackground';
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
import styles from './PresentationPage.module.css';

// --------------------------------------------------------------------------
// Slide variants for directional spring transition (ACs #5-7)
// --------------------------------------------------------------------------

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
  // direction: +1 = forward, -1 = back (used for slide spring ACs #5-7)
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

  // Derived values for slides that need cross-section data
  const preBreakSlugs = getPreBreakSessionSlugs(data.sessions);
  const firstPostBreakSession = getFirstPostBreakSession(data.sessions);

  // --- FLIP agenda layout derivation (ACs #1-4, #17-22) ---
  // 'sidebar'     → session slides (compact left-pinned list)
  // 'center'      → agenda-preview / agenda-recap (full center list)
  const agendaLayout: 'center' | 'sidebar' =
    currentSection?.type === 'session' ? 'sidebar' : 'center';

  // Agenda visible for session, agenda-preview, agenda-recap (hidden for all others)
  const showAgenda =
    currentSection?.type === 'session' ||
    currentSection?.type === 'agenda-preview' ||
    currentSection?.type === 'agenda-recap';

  // completedSessionSlugs only relevant for recap
  const completedSessionSlugsForAgenda =
    currentSection?.type === 'agenda-recap' ? preBreakSlugs : undefined;

  // Current session slug for sidebar highlight (AC #19)
  const currentSessionSlug =
    currentSection?.type === 'session'
      ? (currentSection.session?.sessionSlug ?? undefined)
      : undefined;

  // paddingLeft shifts slide content right when sidebar is visible (AC #17)
  const showSidebar = currentSection?.type === 'session';

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
      <TopicBackground imageUrl={data.event?.topic?.imageUrl} />

      {/* FLIP agenda — always mounted, never unmounted (ACs #1-4, #17-22)
          Framer Motion `layout` captures bounding box before/after class switch → FLIP.
          CRITICAL: CSS transforms live in PresentationPage.module.css, NOT in `animate` prop. */}
      <motion.div
        layout
        className={agendaLayout === 'sidebar' ? styles.agendaSidebar : styles.agendaCenterStage}
        style={{ visibility: showAgenda ? 'visible' : 'hidden' }}
        transition={{ type: 'spring', stiffness: 100, damping: 22, mass: 1 }}
      >
        <AgendaView
          sessions={data.sessions}
          completedSessionSlugs={completedSessionSlugsForAgenda}
          currentSessionSlug={currentSessionSlug}
          layout={agendaLayout}
        />
      </motion.div>

      {/* Current section slide — shifted right when sidebar visible (AC #17) */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          paddingLeft: showSidebar ? '320px' : 0,
          boxSizing: 'border-box',
        }}
      >
        {/* Section spring transitions (ACs #5-7) */}
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

      {/* B-key break overlay — AnimatePresence fade 0.3s (ACs #9, #23-24, #29) */}
      <BlankOverlay isActive={isBlankActive}>
        <div style={{ position: 'fixed', inset: 0, background: '#0a0d14' }}>
          <TopicBackground imageUrl={data.event?.topic?.imageUrl} />
          <BreakSlide firstPostBreakSession={firstPostBreakSession} />
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
      // AgendaView is rendered by the page-level FLIP motion.div — this slide renders heading only
      return <AgendaPreviewSlide />;

    case 'session':
      return section.session ? <SessionSlide session={section.session} /> : null;

    case 'break':
      return <BreakSlide firstPostBreakSession={firstPostBreakSession} />;

    case 'agenda-recap':
      // AgendaView is rendered by the page-level FLIP motion.div — this slide renders heading only
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
