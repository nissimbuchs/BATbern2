/**
 * PresentationPage
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * Route owner for /present/:eventCode.
 * Manages section state, keyboard navigation, layout orchestration.
 * Public — no authentication required.
 *
 * ACs: all (orchestration)
 */

import React, { type JSX, useState, useCallback } from 'react';
import { useParams } from 'react-router-dom';
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

// --------------------------------------------------------------------------
// Types
// --------------------------------------------------------------------------

/** Direction tracked for 10.8b directional spring transitions */
type NavDirection = 'forward' | 'back';

// --------------------------------------------------------------------------
// PresentationPage
// --------------------------------------------------------------------------

export function PresentationPage(): JSX.Element {
  const { eventCode } = useParams<{ eventCode: string }>();

  const { data, isLoading, isInitialLoadError, refetch } = usePresentationData(eventCode ?? '');
  const sections = usePresentationSections(data.event, data.sessions);

  const [currentIndex, setCurrentIndex] = useState(0);
  const [isBlankActive, setIsBlankActive] = useState(false);
  // direction is stored for Story 10.8b spring transitions
  const [, setDirection] = useState<NavDirection>('forward');

  const goNext = useCallback(() => {
    setDirection('forward');
    setCurrentIndex((i) => Math.min(i + 1, sections.length - 1));
  }, [sections.length]);

  const goPrev = useCallback(() => {
    setDirection('back');
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
    return (
      <div style={fullscreenStyle('#0a0d14')}>
        <div style={{ textAlign: 'center', color: '#ffffff', maxWidth: '600px' }}>
          <div
            style={{
              fontSize: '3rem',
              fontWeight: 800,
              color: '#4f9cf9',
              marginBottom: '1rem',
            }}
          >
            BATbern
          </div>
          <h1 style={{ fontSize: '2rem', marginBottom: '1rem' }}>
            Event konnte nicht geladen werden
          </h1>
          <p style={{ fontSize: '1.25rem', color: 'rgba(255,255,255,0.6)', marginBottom: '2rem' }}>
            Bitte überprüfe die Netzwerkverbindung und versuche es erneut.
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
            Erneut versuchen
          </button>
        </div>
      </div>
    );
  }

  const currentSection: PresentationSection | undefined = sections[currentIndex];

  // Derived values for slides that need cross-section data
  const preBreakSlugs = getPreBreakSessionSlugs(data.sessions);
  const firstPostBreakSession = getFirstPostBreakSession(data.sessions);

  // Sidebar visible for session slides only (ACs #17-18)
  // Break and agenda-recap are full-screen; sidebar reappears on the next session slide.
  const showSidebar = currentSection?.type === 'session';

  // Current session slug for sidebar highlight (AC #19)
  const currentSessionSlug =
    currentSection?.type === 'session'
      ? (currentSection.session?.sessionSlug ?? undefined)
      : undefined;

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
      {/* Persistent full-bleed background (AC #33-36) */}
      <TopicBackground imageUrl={data.event?.topic?.imageUrl} />

      {/* Sidebar AgendaView — visible for session/break/recap sections (ACs #17-22) */}
      {showSidebar && (
        <div
          style={{
            position: 'fixed',
            left: 0,
            top: 0,
            bottom: 0,
            zIndex: 2,
            display: 'flex',
            alignItems: 'center',
          }}
        >
          <AgendaView
            sessions={data.sessions}
            completedSessionSlugs={
              currentSection?.type === 'agenda-recap' ? preBreakSlugs : undefined
            }
            currentSessionSlug={currentSessionSlug}
            layout="sidebar"
          />
        </div>
      )}

      {/* Current section slide — shifted right when sidebar is visible so content
          centres in the remaining screen space (sidebar is 280px at left: 2rem ≈ 312px).
          Slides use height: 100vh internally so the wrapper just needs position + paddingLeft. */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          paddingLeft: showSidebar ? '320px' : 0,
          boxSizing: 'border-box',
        }}
      >
        {currentSection && (
          <SectionRenderer
            section={currentSection}
            data={data}
            preBreakSlugs={preBreakSlugs}
            firstPostBreakSession={firstPostBreakSession}
          />
        )}
      </div>

      {/* Section dots progress indicator */}
      <SectionDots count={sections.length} current={currentIndex} />

      {/* B-key break overlay (ACs #23-24, #29) */}
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
  preBreakSlugs: string[];
  firstPostBreakSession: ReturnType<typeof getFirstPostBreakSession>;
}

function SectionRenderer({
  section,
  data,
  preBreakSlugs,
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
      return <AgendaPreviewSlide sessions={data.sessions} />;

    case 'session':
      return section.session ? <SessionSlide session={section.session} /> : null;

    case 'break':
      return <BreakSlide firstPostBreakSession={firstPostBreakSession} />;

    case 'agenda-recap':
      return <AgendaRecapSlide sessions={data.sessions} completedSessionSlugs={preBreakSlugs} />;

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
