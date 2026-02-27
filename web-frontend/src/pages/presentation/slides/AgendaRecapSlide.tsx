/**
 * AgendaRecapSlide
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * AC #14: AgendaView with completedSessions = pre-break session slugs.
 * Pre-break sessions are greyed with ✓, post-break sessions lit.
 */
import { type JSX } from 'react';

import { AgendaView } from '../AgendaView';
import type { PresentationSession } from '@/services/presentationService';

interface AgendaRecapSlideProps {
  sessions: PresentationSession[];
  completedSessionSlugs: string[];
}

export function AgendaRecapSlide({
  sessions,
  completedSessionSlugs,
}: AgendaRecapSlideProps): JSX.Element {
  return (
    <div
      style={{
        position: 'relative',
        zIndex: 1,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        height: '100vh',
        color: '#ffffff',
      }}
    >
      <h2
        style={{
          fontSize: '3rem',
          fontWeight: 700,
          marginBottom: '2rem',
          color: '#4f9cf9',
        }}
      >
        Agenda
      </h2>

      <AgendaView
        sessions={sessions}
        completedSessionSlugs={completedSessionSlugs}
        layout="center"
      />
    </div>
  );
}
