/**
 * eventTypeService on the MSW harness (Story 4.10b, Epic 17 test fidelity): the real apiClient
 * runs and every request is checked against events-core-api, so the old `/events/types` paths
 * would fail the test. Without an override the harness answers with the spec examples.
 */

import { describe, it, expect } from 'vitest';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import type { components } from '@/types/generated/events-core-api.types';
import { eventTypeService } from './eventTypeService';

type UpdateEventSlotConfigurationRequest =
  components['schemas']['UpdateEventSlotConfigurationRequest'];

const request: UpdateEventSlotConfigurationRequest = {
  minSlots: 3,
  maxSlots: 4,
  slotDuration: 45,
  theoreticalSlotsAM: false,
  breakSlots: 1,
  lunchSlots: 0,
  defaultCapacity: 220,
  typicalStartTime: '16:00',
  typicalEndTime: '19:00',
};

describe('eventTypeService', () => {
  it('should_listAllEventTypes_from_eventTypesCollection', async () => {
    const types = await eventTypeService.getAllEventTypes();

    expect(api.calls('listEventTypes')).toHaveLength(1);
    expect(types.map((t) => t.type)).toEqual(['FULL_DAY', 'AFTERNOON', 'EVENING']);
  });

  it('should_readOneEventTypeByItsType', async () => {
    await eventTypeService.getEventType('EVENING');

    expect(api.calls('getEventType')[0].params).toEqual({ type: 'EVENING' });
  });

  it('should_putTheWholeConfiguration_when_updating', async () => {
    await eventTypeService.updateEventType('EVENING', request);

    const [call] = api.calls('updateEventType');
    expect(call.method).toBe('PUT');
    expect(call.params).toEqual({ type: 'EVENING' });
    expect(call.body).toEqual(request);
  });

  it('should_rejectWithTheHttpError_when_notAnOrganizer', async () => {
    api.on('updateEventType', () =>
      HttpResponse.json({ status: 403, message: 'Forbidden' }, { status: 403 })
    );

    await expect(eventTypeService.updateEventType('EVENING', request)).rejects.toMatchObject({
      response: { status: 403 },
    });
  });
});
