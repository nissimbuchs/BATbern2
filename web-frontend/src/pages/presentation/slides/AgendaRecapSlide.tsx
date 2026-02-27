/**
 * AgendaRecapSlide
 * Story 10.8a: Moderator Presentation Page — Functional
 * Story 10.8b: AgendaView moved to page-level motion.div for FLIP animation (ACs #1-4)
 *
 * AC #14: AgendaView with completedSessions — rendered by the page-level AgendaView.
 * This slide renders only the "Agenda" heading; the list is owned by PresentationPage.
 */
import { type JSX } from 'react';

export function AgendaRecapSlide(): JSX.Element {
  // Heading + list are rendered by the page-level AgendaView (layoutId="agenda-view").
  return <div />;
}
