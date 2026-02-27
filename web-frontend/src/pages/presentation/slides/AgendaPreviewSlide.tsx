/**
 * AgendaPreviewSlide
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * AC #11: All sessions in chronological order, all styled as upcoming/neutral
 */
import { type JSX } from 'react';

import { AgendaView } from '../AgendaView';
import type { PresentationSession } from '@/services/presentationService';

interface AgendaPreviewSlideProps {
  sessions: PresentationSession[];
}

export function AgendaPreviewSlide({ sessions }: AgendaPreviewSlideProps): JSX.Element {
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

      <AgendaView sessions={sessions} layout="center" />
    </div>
  );
}
