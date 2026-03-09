/**
 * LiveControlPage
 *
 * Mobile-optimized organizer live session control page.
 * Route: /organizer/events/:eventCode/live-control
 *
 * Dark theme using shadcn/Tailwind design system with BATbern blue (--primary).
 * No AuthLayout wrapper — fullscreen for phone use during events.
 */

import React from 'react';
import { useParams } from 'react-router-dom';
import { useLiveSessionControl } from '@/hooks/useLiveSessionControl/useLiveSessionControl';
import type { ConnectionStatus } from '@/hooks/useLiveSessionControl/useLiveSessionControl';
import { ActiveSessionCard } from '@/components/organizer/LiveControl/ActiveSessionCard';
import { AgendaList } from '@/components/organizer/LiveControl/AgendaList';
import { cn } from '@/lib/utils';

function ConnectionBadge({ status }: { status: ConnectionStatus }) {
  const base = 'inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold';
  switch (status) {
    case 'connected':
      return (
        <span className={cn(base, 'bg-green-500/20 text-green-400 border border-green-500/30')}>
          <span className="w-1.5 h-1.5 rounded-full bg-green-400 animate-pulse" />
          Verbunden
        </span>
      );
    case 'reconnecting':
      return (
        <span className={cn(base, 'bg-amber-500/20 text-amber-400 border border-amber-500/30')}>
          <span className="w-1.5 h-1.5 rounded-full bg-amber-400 animate-pulse" />
          Verbinde...
        </span>
      );
    case 'offline':
      return (
        <span className={cn(base, 'bg-red-500/20 text-red-400 border border-red-500/30')}>
          <span className="w-1.5 h-1.5 rounded-full bg-red-400" />
          Offline
        </span>
      );
    default:
      return (
        <span className={cn(base, 'bg-muted text-muted-foreground border border-border')}>
          <span className="w-1.5 h-1.5 rounded-full bg-muted-foreground animate-pulse" />
          Verbinde...
        </span>
      );
  }
}

const LiveControlPage: React.FC = () => {
  const { eventCode } = useParams<{ eventCode: string }>();

  const {
    sessions,
    activeSession,
    nextSession,
    remainingSeconds,
    elapsedSeconds,
    shouldShowExtend,
    shouldShowDelay,
    isActionInFlight,
    connectionStatus,
    sendExtend,
    sendDelay,
    isLoadingInitial,
  } = useLiveSessionControl(eventCode);

  return (
    // dark class activates the dark CSS variable theme from index.css
    <div className="dark min-h-screen bg-background text-foreground font-sans">
      <div className="max-w-lg mx-auto px-4 py-4">
        {/* Header */}
        <div className="flex items-center justify-between mb-4">
          <div>
            <p className="text-xs font-semibold text-muted-foreground uppercase tracking-[0.2em]">
              Live Steuerung
            </p>
            <p className="text-sm font-medium text-primary">{eventCode}</p>
          </div>
          <ConnectionBadge status={connectionStatus} />
        </div>

        {/* Loading */}
        {isLoadingInitial && sessions.length === 0 ? (
          <div className="flex justify-center items-center min-h-[40vh]">
            <div className="w-10 h-10 rounded-full border-2 border-primary border-t-transparent animate-spin" />
          </div>
        ) : (
          <>
            <ActiveSessionCard
              activeSession={activeSession}
              nextSession={nextSession}
              remainingSeconds={remainingSeconds}
              elapsedSeconds={elapsedSeconds}
              shouldShowExtend={shouldShowExtend}
              shouldShowDelay={shouldShowDelay}
              isActionInFlight={isActionInFlight}
              sendExtend={sendExtend}
              sendDelay={sendDelay}
            />
            {sessions.length > 0 && <AgendaList sessions={sessions} />}
          </>
        )}

        {connectionStatus === 'offline' && (
          <p className="mt-4 text-center text-sm text-muted-foreground">
            Live-Updates nicht verfügbar. Bitte Seite neu laden.
          </p>
        )}
      </div>
    </div>
  );
};

export default LiveControlPage;
