import React from 'react';
import { cn } from '@/lib/utils';
import type { components } from '@/types/generated/events-api.types';

type WatchSessionDetail = components['schemas']['WatchSessionDetail'];

interface AgendaListProps {
  sessions: WatchSessionDetail[];
}

const BREAK_TYPES = new Set(['break', 'lunch', 'networking']);

function formatTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleTimeString('de-CH', { hour: '2-digit', minute: '2-digit' });
}

function speakerNames(session: WatchSessionDetail): string {
  if (!session.speakers?.length) return '';
  return session.speakers
    .map((s) => [s.firstName, s.lastName].filter(Boolean).join(' '))
    .filter(Boolean)
    .join(', ');
}

function SessionRow({ session }: { session: WatchSessionDetail }) {
  const isActive = session.status === 'ACTIVE';
  const isDone = session.status === 'COMPLETED';
  const isBreak = BREAK_TYPES.has(session.sessionType ?? '');
  const speakers = speakerNames(session);

  return (
    <div
      className={cn(
        'flex items-start gap-3 py-3 px-2 rounded-lg mb-1 border-l-2 transition-colors',
        isActive ? 'bg-primary/10 border-primary' : 'border-transparent'
      )}
    >
      {/* Status icon */}
      <div className="mt-0.5 shrink-0">
        {isDone ? (
          <span className="text-green-500 text-lg">✓</span>
        ) : isActive ? (
          <span className="text-primary text-lg">▶</span>
        ) : isBreak ? (
          <span className="text-muted-foreground text-lg">☕</span>
        ) : (
          <span className="text-muted-foreground/40 text-lg">○</span>
        )}
      </div>

      {/* Content */}
      <div className="flex-1 min-w-0">
        <p
          className={cn(
            'text-base font-medium leading-snug',
            isDone
              ? 'line-through text-muted-foreground/50'
              : isActive
                ? 'text-foreground font-bold'
                : 'text-foreground/80'
          )}
        >
          {session.title}
        </p>
        {speakers && !isDone && <p className="text-sm text-muted-foreground mt-0.5">{speakers}</p>}
        <p className="text-sm text-muted-foreground/60 font-mono mt-0.5">
          {formatTime(session.scheduledStartTime)}
          {session.durationMinutes != null ? ` · ${session.durationMinutes} min` : ''}
        </p>
      </div>
    </div>
  );
}

export const AgendaList: React.FC<AgendaListProps> = ({ sessions }) => {
  if (sessions.length === 0) {
    return <p className="text-center text-muted-foreground py-6">Keine Sessions verfügbar</p>;
  }

  return (
    <div>
      <p className="text-xs font-semibold text-muted-foreground uppercase tracking-[0.2em] mb-2">
        Programm
      </p>
      <div className="border-t border-border mb-2" />
      <div>
        {sessions.map((session) => (
          <SessionRow key={session.sessionSlug} session={session} />
        ))}
      </div>
    </div>
  );
};
