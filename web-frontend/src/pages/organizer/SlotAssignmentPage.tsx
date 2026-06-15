/**
 * Slot Assignment redirect (Epic 14 Phase C — 14.C.5)
 *
 * The dedicated full-page slot-assignment route was retired: slot assignment now
 * lives in-tab on the Speakers & Agenda tab (`?tab=speakers&view=slots`). This
 * component only redirects so that external/stale bookmarks of the old route —
 * including a stale `?speakerId=` — land on the in-tab Slots sub-view, carrying
 * the speaker focus through as `&speakerId=<id>`.
 *
 * History: previously a standalone page (Story 5.7 / BAT-11) rendering
 * `DragDropSlotAssignment`. The component is now mounted by `EventSpeakersTab`.
 */

import React from 'react';
import { useParams, useSearchParams, Navigate } from 'react-router-dom';

const SlotAssignmentPage: React.FC = () => {
  const { eventCode } = useParams<{ eventCode: string }>();
  const [searchParams] = useSearchParams();

  // Defensive: without an eventCode there is nowhere meaningful to land.
  if (!eventCode) {
    return <Navigate replace to="/organizer/events" />;
  }

  const params = new URLSearchParams({ tab: 'speakers', view: 'slots' });
  const speakerId = searchParams.get('speakerId');
  if (speakerId) {
    params.set('speakerId', speakerId);
  }

  return <Navigate replace to={`/organizer/events/${eventCode}?${params.toString()}`} />;
};

export default SlotAssignmentPage;
