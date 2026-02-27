/**
 * AgendaView
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * Renders the agenda list; CSS class drives center-stage vs sidebar layout instantly.
 * FLIP animation deferred to Story 10.8b.
 *
 * ACs: #11, #14, #17–22
 */
import { type JSX } from 'react';

import { format } from 'date-fns';
import type { PresentationSession } from '@/services/presentationService';
import styles from './AgendaView.module.css';

const SPEAKER_SESSION_TYPES = new Set(['keynote', 'presentation', 'workshop', 'panel_discussion']);
const BREAK_SESSION_TYPES = new Set(['break', 'lunch']);

interface AgendaViewProps {
  sessions: PresentationSession[];
  completedSessionSlugs?: string[];
  currentSessionSlug?: string;
  layout: 'center' | 'sidebar';
}

function formatTime(iso?: string | null): string {
  if (!iso) {
    return '??:??';
  }
  try {
    return format(new Date(iso), 'HH:mm');
  } catch {
    return '??:??';
  }
}

export function AgendaView({
  sessions,
  completedSessionSlugs = [],
  currentSessionSlug,
  layout,
}: AgendaViewProps): JSX.Element {
  const completedSet = new Set(completedSessionSlugs);

  // Sort sessions and filter to visible ones (speaker + break, no moderation/networking)
  const visible = [...sessions]
    .filter(
      (s) =>
        s.sessionType &&
        (SPEAKER_SESSION_TYPES.has(s.sessionType) || BREAK_SESSION_TYPES.has(s.sessionType))
    )
    .sort((a, b) => {
      if (!a.startTime) {
        return 1;
      }
      if (!b.startTime) {
        return -1;
      }
      return new Date(a.startTime).getTime() - new Date(b.startTime).getTime();
    });

  return (
    <div className={layout === 'center' ? styles.center : styles.sidebar}>
      {visible.map((s) => {
        if (s.sessionType && BREAK_SESSION_TYPES.has(s.sessionType)) {
          return (
            <div key={s.sessionSlug} className={styles.breakRow}>
              ─── Pause ─── {s.startTime ? formatTime(s.startTime) : ''}
            </div>
          );
        }

        const isCompleted = completedSet.has(s.sessionSlug);
        const isCurrent = s.sessionSlug === currentSessionSlug;
        const rowClass = [
          styles.sessionRow,
          isCompleted ? styles.completed : '',
          isCurrent ? styles.current : '',
          !isCompleted && !isCurrent ? styles.upcoming : '',
        ]
          .filter(Boolean)
          .join(' ');

        return (
          <div key={s.sessionSlug} className={rowClass}>
            <span className={styles.sessionTime}>{formatTime(s.startTime)}</span>
            <span className={styles.sessionTitle}>{s.title}</span>
            {isCompleted && <span className={styles.checkmark}>✓</span>}
          </div>
        );
      })}
    </div>
  );
}
