import React, { useState } from 'react';
import { Card, CardContent } from '@/components/public/ui/card';
import { cn } from '@/lib/utils';
import type { components } from '@/types/generated/events-api.types';
import { ExtendSessionSheet } from './ExtendSessionSheet';
import { DelaySessionSheet } from './DelaySessionSheet';

type WatchSessionDetail = components['schemas']['WatchSessionDetail'];

interface ActiveSessionCardProps {
  activeSession: WatchSessionDetail | null;
  nextSession: WatchSessionDetail | null;
  remainingSeconds: number;
  elapsedSeconds: number;
  shouldShowExtend: boolean;
  shouldShowDelay: boolean;
  isActionInFlight: boolean;
  sendExtend: (minutes: number) => void;
  sendDelay: (minutes: number) => void;
}

function formatCountdown(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

function formatTime(iso: string | null | undefined): string {
  if (!iso) return '';
  return new Date(iso).toLocaleTimeString('de-CH', { hour: '2-digit', minute: '2-digit' });
}

function speakerNames(session: WatchSessionDetail): string {
  if (!session.speakers?.length) return '';
  return session.speakers
    .map((s) => [s.firstName, s.lastName].filter(Boolean).join(' '))
    .filter(Boolean)
    .join(', ');
}

export const ActiveSessionCard: React.FC<ActiveSessionCardProps> = ({
  activeSession,
  nextSession,
  remainingSeconds,
  elapsedSeconds,
  shouldShowExtend,
  shouldShowDelay,
  isActionInFlight,
  sendExtend,
  sendDelay,
}) => {
  const [extendOpen, setExtendOpen] = useState(false);
  const [delayOpen, setDelayOpen] = useState(false);

  if (!activeSession) {
    return (
      <Card className="mb-4 border-border bg-card text-card-foreground">
        <CardContent className="py-10 text-center">
          <div className="text-4xl mb-3">⏱</div>
          <p className="text-xl font-semibold text-muted-foreground">Keine aktive Session</p>
          {nextSession && (
            <div className="mt-5">
              <p className="text-sm text-muted-foreground uppercase tracking-widest mb-2">
                Nächste Session
              </p>
              <p className="text-xl font-bold text-foreground">{nextSession.title}</p>
              {speakerNames(nextSession) && (
                <p className="text-base text-muted-foreground mt-1">{speakerNames(nextSession)}</p>
              )}
              <p className="text-lg text-primary font-mono mt-2">
                {formatTime(nextSession.scheduledStartTime)}
              </p>
            </div>
          )}
        </CardContent>
      </Card>
    );
  }

  const totalSeconds =
    activeSession.durationMinutes != null
      ? activeSession.durationMinutes * 60
      : elapsedSeconds + remainingSeconds;
  const progress = totalSeconds > 0 ? Math.min((elapsedSeconds / totalSeconds) * 100, 100) : 0;
  const isOvertime = remainingSeconds === 0 && elapsedSeconds > 0;
  const speakers = speakerNames(activeSession);

  // Urgency levels mirroring Watch app SessionTimerEngine exactly:
  // normal (>300s): primary blue | caution (120–300s): primary blue
  // warning (60–120s): orange | critical (<60s): orange | overtime: red
  type UrgencyLevel = 'normal' | 'caution' | 'warning' | 'critical' | 'overtime';
  const urgency: UrgencyLevel = isOvertime
    ? 'overtime'
    : remainingSeconds <= 60
      ? 'critical'
      : remainingSeconds <= 120
        ? 'warning'
        : remainingSeconds <= 300
          ? 'caution'
          : 'normal';

  const countdownColor =
    urgency === 'overtime'
      ? 'text-red-400'
      : urgency === 'warning' || urgency === 'critical'
        ? 'text-orange-400'
        : 'text-primary';

  const progressColor =
    urgency === 'overtime'
      ? 'bg-red-500'
      : urgency === 'warning' || urgency === 'critical'
        ? 'bg-orange-500'
        : 'bg-primary';

  const borderColor =
    urgency === 'overtime'
      ? 'border-red-500'
      : urgency === 'warning' || urgency === 'critical'
        ? 'border-orange-500'
        : 'border-primary/40';

  // Status label matching Watch (German)
  const urgencyLabel =
    urgency === 'overtime'
      ? 'ÜBERZEIT'
      : urgency === 'critical' || urgency === 'warning'
        ? '2 MIN NOCH'
        : urgency === 'caution'
          ? '5 MIN NOCH'
          : 'IM PLAN';

  const urgencyLabelColor =
    urgency === 'overtime'
      ? 'text-red-400'
      : urgency === 'warning' || urgency === 'critical'
        ? 'text-orange-400'
        : urgency === 'caution'
          ? 'text-primary'
          : 'text-muted-foreground';

  // Pulse the countdown at critical (<1 min) and overtime — visual urgency cue (replaces haptics)
  const countdownPulse = urgency === 'critical' || urgency === 'overtime';

  const overtimeSeconds = isOvertime ? elapsedSeconds - (totalSeconds > 0 ? totalSeconds : 0) : 0;

  return (
    <>
      <Card className={cn('mb-4 border-2 bg-card text-card-foreground', borderColor)}>
        <CardContent className="p-5">
          {/* Live badge + time range */}
          <div className="flex items-center gap-2 mb-4">
            <span className="inline-block w-2.5 h-2.5 rounded-full bg-red-500 animate-pulse shrink-0" />
            <span className="text-red-400 font-bold tracking-[0.2em] text-sm">LIVE</span>
            <span className="ml-auto text-sm text-muted-foreground font-mono">
              {formatTime(activeSession.scheduledStartTime)}
              {' – '}
              {formatTime(activeSession.scheduledEndTime)}
            </span>
          </div>

          {/* Session title */}
          <h2 className="text-3xl font-bold leading-tight text-foreground mb-2">
            {activeSession.title}
          </h2>

          {/* Speaker */}
          {speakers && <p className="text-lg text-muted-foreground mb-4">{speakers}</p>}

          {/* Countdown */}
          <div className="text-center py-4">
            <p
              className={cn(
                'text-8xl font-bold font-mono leading-none',
                countdownColor,
                countdownPulse && 'animate-pulse'
              )}
            >
              {isOvertime
                ? `+${formatCountdown(overtimeSeconds)}`
                : formatCountdown(remainingSeconds)}
            </p>
            <p
              className={cn('text-sm font-bold tracking-[0.2em] mt-2 uppercase', urgencyLabelColor)}
            >
              {urgencyLabel}
            </p>
          </div>

          {/* Progress bar */}
          <div className="w-full h-2 bg-muted rounded-full mb-5 overflow-hidden">
            <div
              className={cn('h-full rounded-full transition-all duration-1000', progressColor)}
              style={{ width: `${progress}%` }}
            />
          </div>

          {/* Action buttons */}
          <div className="flex gap-3">
            {shouldShowDelay && (
              <button
                disabled={isActionInFlight}
                onClick={() => setDelayOpen(true)}
                className="flex-1 py-4 rounded-xl text-base font-bold border-2 border-amber-500 text-amber-400 hover:bg-amber-500/10 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                ← Verzögern
              </button>
            )}
            {shouldShowExtend && (
              <button
                onClick={() => setExtendOpen(true)}
                className="flex-1 py-4 rounded-xl text-base font-bold border-2 border-primary text-primary hover:bg-primary/10 transition-colors"
              >
                ↔ Zeit anpassen
              </button>
            )}
          </div>
        </CardContent>
      </Card>

      <ExtendSessionSheet
        open={extendOpen}
        onClose={() => setExtendOpen(false)}
        onExtend={sendExtend}
      />
      <DelaySessionSheet
        open={delayOpen}
        onClose={() => setDelayOpen(false)}
        onDelay={sendDelay}
        disabled={isActionInFlight}
      />
    </>
  );
};
