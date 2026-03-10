import { describe, it, expect } from 'vitest';
import { getHomepagePhase, getSectionVisibility, type HomePagePhase } from './homePagePhase';
import type { EventDetail } from '@/types/event.types';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const photo = { id: '1' };

function makeEvent(
  overrides: Partial<Pick<EventDetail, 'workflowState' | 'currentPublishedPhase'>>
): EventDetail {
  return {
    eventCode: 'BATbern42',
    title: 'Test Event',
    date: '2026-06-01',
    registrationDeadline: '2026-05-28',
    venueName: 'Bern Expo',
    venueAddress: 'Mingerstrasse 6, 3014 Bern',
    venueCapacity: 200,
    workflowState: 'AGENDA_PUBLISHED',
    currentPublishedPhase: 'AGENDA',
    ...overrides,
  } as EventDetail;
}

// ---------------------------------------------------------------------------
// getHomepagePhase
// ---------------------------------------------------------------------------

describe('getHomepagePhase', () => {
  it('returns ARCHIVE when isArchiveMode=true', () => {
    const event = makeEvent({ workflowState: 'ARCHIVED' });
    expect(getHomepagePhase(event, true, undefined)).toEqual({
      kind: 'ARCHIVE',
      hasEventPhotos: false,
    });
  });

  it('returns ARCHIVE with hasEventPhotos=true when photos provided in archive mode', () => {
    const event = makeEvent({ workflowState: 'ARCHIVED' });
    expect(getHomepagePhase(event, true, [photo])).toEqual({
      kind: 'ARCHIVE',
      hasEventPhotos: true,
    });
  });

  it('returns POST_EVENT with hasEventPhotos=true when EVENT_COMPLETED and photos exist', () => {
    const event = makeEvent({ workflowState: 'EVENT_COMPLETED', currentPublishedPhase: 'AGENDA' });
    expect(getHomepagePhase(event, false, [photo])).toEqual({
      kind: 'POST_EVENT',
      hasEventPhotos: true,
    });
  });

  it('returns POST_EVENT with hasEventPhotos=false when EVENT_COMPLETED and no photos', () => {
    const event = makeEvent({ workflowState: 'EVENT_COMPLETED', currentPublishedPhase: 'AGENDA' });
    expect(getHomepagePhase(event, false, [])).toEqual({
      kind: 'POST_EVENT',
      hasEventPhotos: false,
    });
  });

  it('returns POST_EVENT with hasEventPhotos=false when EVENT_COMPLETED and photos undefined', () => {
    const event = makeEvent({ workflowState: 'EVENT_COMPLETED', currentPublishedPhase: 'AGENDA' });
    expect(getHomepagePhase(event, false, undefined)).toEqual({
      kind: 'POST_EVENT',
      hasEventPhotos: false,
    });
  });

  it('returns COMING_SOON when currentPublishedPhase is null', () => {
    const event = makeEvent({
      workflowState: 'TOPIC_SELECTION',
      currentPublishedPhase: undefined,
    });
    expect(getHomepagePhase(event, false, undefined)).toEqual({ kind: 'COMING_SOON' });
  });

  it('returns PRE_EVENT TOPIC when phase is TOPIC', () => {
    const event = makeEvent({ workflowState: 'TOPIC_SELECTION', currentPublishedPhase: 'TOPIC' });
    expect(getHomepagePhase(event, false, undefined)).toEqual({ kind: 'PRE_EVENT', sub: 'TOPIC' });
  });

  it('returns PRE_EVENT SPEAKERS when phase is SPEAKERS', () => {
    const event = makeEvent({
      workflowState: 'SPEAKER_IDENTIFICATION',
      currentPublishedPhase: 'SPEAKERS',
    });
    expect(getHomepagePhase(event, false, undefined)).toEqual({
      kind: 'PRE_EVENT',
      sub: 'SPEAKERS',
    });
  });

  it('returns PRE_EVENT AGENDA when phase is AGENDA', () => {
    const event = makeEvent({ workflowState: 'AGENDA_PUBLISHED', currentPublishedPhase: 'AGENDA' });
    expect(getHomepagePhase(event, false, undefined)).toEqual({ kind: 'PRE_EVENT', sub: 'AGENDA' });
  });

  it('archive mode takes precedence over EVENT_COMPLETED', () => {
    const event = makeEvent({ workflowState: 'EVENT_COMPLETED' });
    expect(getHomepagePhase(event, true, [photo])).toEqual({
      kind: 'ARCHIVE',
      hasEventPhotos: true,
    });
  });
});

// ---------------------------------------------------------------------------
// getSectionVisibility — COMING_SOON
// ---------------------------------------------------------------------------

describe('getSectionVisibility — COMING_SOON', () => {
  const phase: HomePagePhase = { kind: 'COMING_SOON' };
  const vis = getSectionVisibility(phase);

  it('hides event description', () => expect(vis.eventDescription).toBe(false));
  it('shows event logistics', () => expect(vis.eventLogistics).toBe(true));
  it('shows venue map', () => expect(vis.venueMap).toBe(true));
  it('hides speaker grid', () => expect(vis.speakerGrid).toBe(false));
  it('hides session cards', () => expect(vis.sessionCards).toBe(false));
  it('hides event program', () => expect(vis.eventProgram).toBe(false));
  it('hides event photos marquee', () => expect(vis.eventPhotosMarquee).toBe(false));
  it('shows testimonials', () => expect(vis.testimonials).toBe(true));
  it('does not skip testimonials photo row', () =>
    expect(vis.testimonialsSkipPhotoRow).toBe(false));
  it('shows upcoming events', () => expect(vis.upcomingEvents).toBe(true));
  it('hides back link', () => expect(vis.backLink).toBe(false));
  it('hides session materials', () => expect(vis.showSessionMaterials).toBe(false));
});

// ---------------------------------------------------------------------------
// getSectionVisibility — PRE_EVENT TOPIC
// ---------------------------------------------------------------------------

describe('getSectionVisibility — PRE_EVENT TOPIC', () => {
  const phase: HomePagePhase = { kind: 'PRE_EVENT', sub: 'TOPIC' };
  const vis = getSectionVisibility(phase);

  it('shows event description', () => expect(vis.eventDescription).toBe(true));
  it('shows event logistics', () => expect(vis.eventLogistics).toBe(true));
  it('shows venue map', () => expect(vis.venueMap).toBe(true));
  it('hides speaker grid', () => expect(vis.speakerGrid).toBe(false));
  it('hides session cards', () => expect(vis.sessionCards).toBe(false));
  it('hides event program', () => expect(vis.eventProgram).toBe(false));
  it('shows testimonials', () => expect(vis.testimonials).toBe(true));
  it('shows upcoming events', () => expect(vis.upcomingEvents).toBe(true));
  it('hides session materials', () => expect(vis.showSessionMaterials).toBe(false));
});

// ---------------------------------------------------------------------------
// getSectionVisibility — PRE_EVENT SPEAKERS
// ---------------------------------------------------------------------------

describe('getSectionVisibility — PRE_EVENT SPEAKERS', () => {
  const phase: HomePagePhase = { kind: 'PRE_EVENT', sub: 'SPEAKERS' };
  const vis = getSectionVisibility(phase);

  it('shows speaker grid', () => expect(vis.speakerGrid).toBe(true));
  it('shows session cards', () => expect(vis.sessionCards).toBe(true));
  it('hides event program', () => expect(vis.eventProgram).toBe(false));
  it('hides session materials', () => expect(vis.showSessionMaterials).toBe(false));
});

// ---------------------------------------------------------------------------
// getSectionVisibility — PRE_EVENT AGENDA
// ---------------------------------------------------------------------------

describe('getSectionVisibility — PRE_EVENT AGENDA', () => {
  const phase: HomePagePhase = { kind: 'PRE_EVENT', sub: 'AGENDA' };
  const vis = getSectionVisibility(phase);

  it('shows speaker grid', () => expect(vis.speakerGrid).toBe(true));
  it('hides session cards (replaced by timetable)', () => expect(vis.sessionCards).toBe(false));
  it('shows event program', () => expect(vis.eventProgram).toBe(true));
  it('hides session materials', () => expect(vis.showSessionMaterials).toBe(false));
});

// ---------------------------------------------------------------------------
// getSectionVisibility — POST_EVENT with photos
// ---------------------------------------------------------------------------

describe('getSectionVisibility — POST_EVENT hasEventPhotos=true', () => {
  const phase: HomePagePhase = { kind: 'POST_EVENT', hasEventPhotos: true };
  const vis = getSectionVisibility(phase);

  it('shows event description', () => expect(vis.eventDescription).toBe(true));
  it('shows event logistics', () => expect(vis.eventLogistics).toBe(true));
  it('hides venue map', () => expect(vis.venueMap).toBe(false));
  it('shows speaker grid', () => expect(vis.speakerGrid).toBe(true));
  it('shows session cards', () => expect(vis.sessionCards).toBe(true));
  it('hides event program', () => expect(vis.eventProgram).toBe(false));
  it('shows event photos marquee', () => expect(vis.eventPhotosMarquee).toBe(true));
  it('shows testimonials', () => expect(vis.testimonials).toBe(true));
  it('skips testimonials photo row (own photos shown above)', () =>
    expect(vis.testimonialsSkipPhotoRow).toBe(true));
  it('shows upcoming events', () => expect(vis.upcomingEvents).toBe(true));
  it('hides back link', () => expect(vis.backLink).toBe(false));
  it('shows session materials', () => expect(vis.showSessionMaterials).toBe(true));
});

// ---------------------------------------------------------------------------
// getSectionVisibility — POST_EVENT without photos
// ---------------------------------------------------------------------------

describe('getSectionVisibility — POST_EVENT hasEventPhotos=false', () => {
  const phase: HomePagePhase = { kind: 'POST_EVENT', hasEventPhotos: false };
  const vis = getSectionVisibility(phase);

  it('hides event photos marquee', () => expect(vis.eventPhotosMarquee).toBe(false));
  it('does not skip testimonials photo row', () =>
    expect(vis.testimonialsSkipPhotoRow).toBe(false));
  it('shows session materials', () => expect(vis.showSessionMaterials).toBe(true));
});

// ---------------------------------------------------------------------------
// getSectionVisibility — ARCHIVE with photos
// ---------------------------------------------------------------------------

describe('getSectionVisibility — ARCHIVE hasEventPhotos=true', () => {
  const phase: HomePagePhase = { kind: 'ARCHIVE', hasEventPhotos: true };
  const vis = getSectionVisibility(phase);

  it('shows event description', () => expect(vis.eventDescription).toBe(true));
  it('shows event logistics', () => expect(vis.eventLogistics).toBe(true));
  it('hides venue map', () => expect(vis.venueMap).toBe(false));
  it('shows speaker grid', () => expect(vis.speakerGrid).toBe(true));
  it('shows session cards', () => expect(vis.sessionCards).toBe(true));
  it('hides event program', () => expect(vis.eventProgram).toBe(false));
  it('shows event photos marquee', () => expect(vis.eventPhotosMarquee).toBe(true));
  it('shows testimonials', () => expect(vis.testimonials).toBe(true));
  it('skips testimonials photo row', () => expect(vis.testimonialsSkipPhotoRow).toBe(true));
  it('shows upcoming events', () => expect(vis.upcomingEvents).toBe(true));
  it('shows back link', () => expect(vis.backLink).toBe(true));
  it('shows session materials', () => expect(vis.showSessionMaterials).toBe(true));
});

// ---------------------------------------------------------------------------
// getSectionVisibility — ARCHIVE without photos
// ---------------------------------------------------------------------------

describe('getSectionVisibility — ARCHIVE hasEventPhotos=false', () => {
  const phase: HomePagePhase = { kind: 'ARCHIVE', hasEventPhotos: false };
  const vis = getSectionVisibility(phase);

  it('hides event photos marquee', () => expect(vis.eventPhotosMarquee).toBe(false));
  it('does not skip testimonials photo row', () =>
    expect(vis.testimonialsSkipPhotoRow).toBe(false));
  it('shows back link', () => expect(vis.backLink).toBe(true));
  it('shows session materials', () => expect(vis.showSessionMaterials).toBe(true));
});
