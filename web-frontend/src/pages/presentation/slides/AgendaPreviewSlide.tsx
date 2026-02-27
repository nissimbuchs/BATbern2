/**
 * AgendaPreviewSlide
 * Story 10.8a: Moderator Presentation Page — Functional
 * Story 10.8b: AgendaView moved to page-level motion.div for FLIP animation (ACs #1-4)
 *
 * AC #11: All sessions in chronological order — rendered by the page-level AgendaView.
 * This slide renders only the "Agenda" heading; the list is owned by PresentationPage.
 */
import { type JSX } from 'react';

export function AgendaPreviewSlide(): JSX.Element {
  return (
    <div
      style={{
        position: 'relative',
        zIndex: 1,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'flex-start',
        height: '100vh',
        paddingTop: '12vh',
        color: '#ffffff',
      }}
    >
      <h2
        style={{
          fontSize: '3rem',
          fontWeight: 700,
          color: '#4f9cf9',
        }}
      >
        Agenda
      </h2>
    </div>
  );
}
