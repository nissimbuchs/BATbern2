import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { AgendaView } from './AgendaView';
import type { PresentationSession } from '@/services/presentationService';

const session = (
  sessionSlug: string,
  sessionType: string,
  title: string,
  startTime: string
): PresentationSession => ({ sessionSlug, sessionType, title, startTime }) as PresentationSession;

describe('AgendaView', () => {
  // #1117: a coffee break and the apéro both read "Pause"; the public agenda names each one.
  it('should_showSessionTitle_when_structuralRowIsBreakOrApero', () => {
    render(
      <AgendaView
        layout="center"
        sessions={[
          session('talk', 'presentation', 'Digitale Souveränität', '2026-11-06T15:05:00Z'),
          session('pause', 'break', 'Kaffeepause', '2026-11-06T16:15:00Z'),
          session('apero', 'networking', 'Apéro', '2026-11-06T17:50:00Z'),
        ]}
      />
    );

    const rows = screen.getAllByTestId('agenda-break-row');
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent('Kaffeepause');
    expect(rows[1]).toHaveTextContent('Apéro');
    expect(rows[1]).not.toHaveTextContent('Pause');
  });

  it('should_fallBackToPause_when_breakHasNoTitle', () => {
    render(
      <AgendaView
        layout="center"
        sessions={[session('pause', 'break', '', '2026-11-06T16:15:00Z')]}
      />
    );

    expect(screen.getByTestId('agenda-break-row')).toHaveTextContent('Pause');
  });
});
