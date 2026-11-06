/**
 * #1238: the public page's registration state matrix, one row per cell of the issue's target table.
 * S0 not published · S1 open · S2 open, full · S3 closed before event day · S4 event day · S5 after.
 */
import { describe, it, expect } from 'vitest';
import {
  getRegistrationSituation,
  getRegistrationView,
  isEventDayReached,
  type MyRegistrationStatus,
  type RegistrationSituation,
} from './homePagePhase';

// Event on Tuesday 10 Nov 2026, 18:00 Bern; registration deadline 5 Nov.
const EVENT_DATE = '2026-11-10T17:00:00Z';
const DEADLINE = '2026-11-05T22:59:00Z';
const BEFORE_DEADLINE = Date.parse('2026-11-01T12:00:00Z');
const AFTER_DEADLINE = Date.parse('2026-11-07T12:00:00Z');
const DAY_BEFORE_LATE = Date.parse('2026-11-09T22:30:00Z'); // 23:30 Bern, still the day before
const EVENT_DAY_EARLY = Date.parse('2026-11-09T23:30:00Z'); // 00:30 Bern on the event day

type Ev = Parameters<typeof getRegistrationView>[0];

function event(overrides: Partial<Ev> = {}): Ev {
  return {
    date: EVENT_DATE,
    registrationDeadline: DEADLINE,
    workflowState: 'AGENDA_PUBLISHED',
    currentPublishedPhase: 'AGENDA',
    registrationCapacity: 253,
    spotsRemaining: 8,
    confirmedCount: 245,
    waitlistCount: 0,
    ...overrides,
  } as Ev;
}

const FULL = { spotsRemaining: 0, confirmedCount: 253, waitlistCount: 4 };

/** The event and clock for each situation. */
const SITUATIONS: Record<RegistrationSituation, { ev: Ev; now: number; archive?: boolean }> = {
  S0: {
    ev: event({ currentPublishedPhase: null, workflowState: 'CREATED' }),
    now: BEFORE_DEADLINE,
  },
  S1: { ev: event(), now: BEFORE_DEADLINE },
  S2: { ev: event(FULL), now: BEFORE_DEADLINE },
  S3: { ev: event(), now: AFTER_DEADLINE },
  S4: { ev: event({ workflowState: 'EVENT_LIVE' }), now: AFTER_DEADLINE },
  S5: { ev: event({ workflowState: 'EVENT_COMPLETED' }), now: AFTER_DEADLINE },
};

function view(
  s: RegistrationSituation,
  status: MyRegistrationStatus | null | undefined,
  auth: boolean
) {
  const { ev, now, archive } = SITUATIONS[s];
  return getRegistrationView(ev, status, { isAuthenticated: auth, isArchiveMode: archive, now });
}

const ALL: RegistrationSituation[] = ['S0', 'S1', 'S2', 'S3', 'S4', 'S5'];

describe('getRegistrationSituation', () => {
  it.each(ALL)('should classify %s', (s) => {
    const { ev, now } = SITUATIONS[s];
    expect(getRegistrationSituation(ev, false, now)).toBe(s);
  });

  it('should be S5 in the archive view whatever the state', () => {
    expect(getRegistrationSituation(event(), true, BEFORE_DEADLINE)).toBe('S5');
  });

  it('should be S2 when an organizer added people over the capacity (negative spots, G10)', () => {
    expect(getRegistrationSituation(event({ spotsRemaining: -3 }), false, BEFORE_DEADLINE)).toBe(
      'S2'
    );
  });

  it('should be S1 without a capacity', () => {
    expect(
      getRegistrationSituation(
        event({ registrationCapacity: null, spotsRemaining: null }),
        false,
        BEFORE_DEADLINE
      )
    ).toBe('S1');
  });

  it('should be S4 from 00:00 Bern on the event day even before the EVENT_LIVE scheduler ran', () => {
    expect(getRegistrationSituation(event(), false, EVENT_DAY_EARLY)).toBe('S4');
    expect(getRegistrationSituation(event(), false, DAY_BEFORE_LATE)).toBe('S3');
  });

  it('should fall back to the event date when no deadline is set (S3 does not occur)', () => {
    const noDeadline = event({ registrationDeadline: undefined as unknown as string });
    expect(getRegistrationSituation(noDeadline, false, AFTER_DEADLINE)).toBe('S1');
    expect(getRegistrationSituation(noDeadline, false, EVENT_DAY_EARLY)).toBe('S4');
  });
});

describe('isEventDayReached', () => {
  it('should switch at midnight Bern time, not UTC', () => {
    expect(isEventDayReached({ date: EVENT_DATE }, DAY_BEFORE_LATE)).toBe(false);
    expect(isEventDayReached({ date: EVENT_DATE }, EVENT_DAY_EARLY)).toBe(true);
  });
});

describe('getRegistrationView: register button in the hero (anonymous, no registration, CANCELLED)', () => {
  const expected: Record<RegistrationSituation, 'REGISTER' | 'WAITLIST' | null> = {
    S0: null,
    S1: 'REGISTER',
    S2: 'WAITLIST', // G8
    S3: null,
    S4: null,
    S5: null,
  };
  it.each(ALL)('%s', (s) => {
    expect(view(s, undefined, false).registerCta).toBe(expected[s]);
    expect(view(s, null, true).registerCta).toBe(expected[s]);
    expect(view(s, 'CANCELLED', true).registerCta).toBe(expected[s]);
  });
  it.each(ALL)('should offer no register button to an active registration in %s', (s) => {
    for (const status of ['REGISTERED', 'CONFIRMED', 'WAITLIST'] as const) {
      expect(view(s, status, true).registerCta).toBeNull();
    }
  });
});

describe('getRegistrationView: own status, once, in the hero (G4, G5)', () => {
  const shown: Record<RegistrationSituation, boolean> = {
    S0: false,
    S1: true,
    S2: true,
    S3: true,
    S4: true,
    S5: false,
  };
  it.each(ALL)('%s', (s) => {
    for (const status of ['REGISTERED', 'CONFIRMED', 'WAITLIST'] as const) {
      expect(view(s, status, true).ownStatus).toBe(shown[s] ? status : null);
    }
  });
});

describe('getRegistrationView: CANCELLED (G6)', () => {
  const again: Record<RegistrationSituation, boolean> = {
    S0: false,
    S1: true,
    S2: true,
    S3: false,
    S4: false,
    S5: false,
  };
  it.each(ALL)('%s', (s) => {
    const v = view(s, 'CANCELLED', true);
    expect(v.ownStatus).toBe(s === 'S0' || s === 'S5' ? null : 'CANCELLED');
    expect(v.registerAgain).toBe(again[s]);
  });
});

describe('getRegistrationView: unregister (E2: until the end of the day before the event)', () => {
  const loggedIn: Record<RegistrationSituation, boolean> = {
    S0: false,
    S1: true,
    S2: true,
    S3: true, // G5: today there is no way after the deadline
    S4: false,
    S5: false,
  };
  it.each(ALL)('logged in, %s', (s) => {
    for (const status of ['REGISTERED', 'CONFIRMED', 'WAITLIST'] as const) {
      expect(view(s, status, true).unregister).toBe(loggedIn[s]);
    }
  });
  it.each(ALL)('anonymous cancel link, %s', (s) => {
    expect(view(s, undefined, false).anonymousCancelLink).toBe(loggedIn[s]);
  });
  it('should still allow it at 23:30 Bern the day before, and not at 00:30 on the day', () => {
    expect(
      getRegistrationView(event(), 'CONFIRMED', { isAuthenticated: true, now: DAY_BEFORE_LATE })
        .unregister
    ).toBe(true);
    expect(
      getRegistrationView(event(), 'CONFIRMED', { isAuthenticated: true, now: EVENT_DAY_EARLY })
        .unregister
    ).toBe(false);
  });
});

describe('getRegistrationView: "not registered with x@…" for a logged-in visitor without a registration (G9)', () => {
  const shown: Record<RegistrationSituation, boolean> = {
    S0: false,
    S1: true,
    S2: true,
    S3: true,
    S4: true,
    S5: false,
  };
  it.each(ALL)('%s', (s) => {
    expect(view(s, null, true).notRegisteredNotice).toBe(shown[s]);
    expect(view(s, undefined, false).notRegisteredNotice).toBe(false);
  });
});

describe('getRegistrationView: "registration closed" notice', () => {
  it.each(ALL)('%s', (s) => {
    const closed = s === 'S3' || s === 'S4';
    expect(view(s, undefined, false).closedNotice).toBe(closed);
    expect(view(s, null, true).closedNotice).toBe(closed);
    expect(view(s, 'CANCELLED', true).closedNotice).toBe(closed);
    expect(view(s, 'CONFIRMED', true).closedNotice).toBe(false);
  });
});

describe('getRegistrationView: place numbers, one block (G1, G2, G3, E1, E3, E4)', () => {
  it('should hide them before publication (S0) and after the event (S5, E4)', () => {
    expect(view('S0', undefined, false).places).toBeNull();
    expect(view('S5', undefined, false).places).toBeNull();
    expect(
      getRegistrationView(event(), undefined, {
        isAuthenticated: false,
        isArchiveMode: true,
        now: BEFORE_DEADLINE,
      }).places
    ).toBeNull();
  });

  it('should show total = taken + free and the waitlist on top (BATbern60 live values)', () => {
    expect(view('S1', undefined, false).places).toEqual({
      total: 253,
      taken: 245,
      free: 8,
      waitlist: 0,
      note: null,
    });
  });

  it('should say "full" in S2 and "closed" in S3/S4, with the same numbers (G1, E3)', () => {
    expect(view('S2', undefined, false).places?.note).toBe('FULL');
    expect(view('S3', undefined, false).places).toEqual({
      total: 253,
      taken: 245,
      free: 8,
      waitlist: 0,
      note: 'CLOSED',
    });
    expect(view('S4', undefined, false).places?.note).toBe('CLOSED');
  });

  it('should never show a negative "free" number when over capacity', () => {
    const v = getRegistrationView(event({ spotsRemaining: -3, confirmedCount: 256 }), undefined, {
      isAuthenticated: false,
      now: BEFORE_DEADLINE,
    });
    expect(v.places).toMatchObject({ total: 253, taken: 256, free: 0 });
  });

  it('should show only "taken" without a capacity', () => {
    const v = getRegistrationView(
      event({ registrationCapacity: null, spotsRemaining: null, confirmedCount: 80 }),
      undefined,
      { isAuthenticated: false, now: BEFORE_DEADLINE }
    );
    expect(v.places).toMatchObject({ total: null, taken: 80, free: null });
  });
});

describe('getRegistrationView: /register/{code} (G7, G10)', () => {
  const page: Record<RegistrationSituation, string> = {
    // S0 keeps the wizard for now: the post-deploy @smoke registers on an unpublished throwaway
    // event, so "not open yet" waits for the owner's decision (see #1238).
    S0: 'WIZARD',
    S1: 'WIZARD',
    S2: 'WIZARD_WAITLIST',
    S3: 'CLOSED',
    S4: 'CLOSED',
    S5: 'CLOSED',
  };
  it.each(ALL)('%s', (s) => {
    expect(view(s, undefined, false).registerPage).toBe(page[s]);
  });
  it('should ask for the waitlist confirmation when over capacity (G10)', () => {
    expect(
      getRegistrationView(event({ spotsRemaining: -2 }), undefined, {
        isAuthenticated: false,
        now: BEFORE_DEADLINE,
      }).registerPage
    ).toBe('WIZARD_WAITLIST');
  });
});

describe('getRegistrationView: own registration is loaded wherever it can be shown (G9)', () => {
  it.each(ALL)('%s', (s) => {
    expect(view(s, undefined, true).loadMyRegistration).toBe(s !== 'S0' && s !== 'S5');
  });
  it('should load it in the TOPIC and SPEAKERS phases too, not only AGENDA_PUBLISHED / EVENT_LIVE', () => {
    for (const phase of ['TOPIC', 'SPEAKERS'] as const) {
      const ev = event({
        currentPublishedPhase: phase,
        workflowState: 'SPEAKER_BRAINSTORMING',
      } as Partial<Ev>);
      expect(
        getRegistrationView(ev, undefined, { isAuthenticated: true, now: BEFORE_DEADLINE })
          .loadMyRegistration
      ).toBe(true);
    }
  });
});
