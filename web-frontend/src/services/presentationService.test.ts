/**
 * presentationService against the MSW harness (Story 10.8a; moved off the apiClient mock in
 * Story 4.9, when the presentation settings moved from CUMS to the EMS app settings store).
 *
 * The real apiClient sends each call; the harness checks it against docs/api and answers with
 * the spec example or a typed override.
 */
import { describe, it, expect } from 'vitest';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import { futureEvent, pastEvent } from '@/test/fixtures';
import {
  getPresentationData,
  getPublicOrganizers,
  getUpcomingEvents,
  getPresentationSettings,
  updatePresentationSettings,
  getGlobalTeaserImages,
  presentationService,
  PRESENTATION_SETTING_KEY,
} from './presentationService';

const page = <T>(data: T[]) => ({
  data,
  pagination: {
    page: 1,
    limit: 10,
    totalItems: data.length,
    totalPages: 1,
    hasNext: false,
    hasPrev: false,
  },
});

describe('presentationService', () => {
  describe('getPresentationData', () => {
    // #1112: the organizer endpoint /events/{code} returns no sessions to anonymous callers
    // (since #1072), which left the agenda slide empty. The public read model carries them.
    it('reads the event from the public read model', async () => {
      await getPresentationData('BATbern60');

      const [call] = api.calls('getPublicEvent');
      expect(call.params).toEqual({ eventCode: 'BATbern60' });
    });
  });

  describe('getPublicOrganizers', () => {
    it('reads the public organizer list', async () => {
      await getPublicOrganizers();

      expect(api.calls('getAllOrganizers')).toHaveLength(1);
    });
  });

  describe('getUpcomingEvents', () => {
    it('returns strictly future events sorted by date, limited to 3, without the current one', async () => {
      api.on('listPublicEvents', () =>
        page([
          futureEvent(30, { eventCode: 'BATbern64' }),
          futureEvent(10, { eventCode: 'BATbern62' }),
          futureEvent(5, { eventCode: 'BATbern61' }),
          futureEvent(20, { eventCode: 'BATbern63' }),
          futureEvent(2, { eventCode: 'BATbern60' }),
          pastEvent(1, { eventCode: 'BATbern59' }),
        ])
      );

      const result = await getUpcomingEvents('BATbern60');

      expect(result.map((e) => e.eventCode)).toEqual(['BATbern61', 'BATbern62', 'BATbern63']);
      // the public read model, not the organizer list (whose `status` parameter EMS ignored)
      const [call] = api.calls('listPublicEvents');
      expect(call.query.get('scope')).toBe('upcoming');
      expect(call.query.get('limit')).toBe('10');
      expect(api.calls('listEvents')).toHaveLength(0);
    });

    it('returns nothing when every event is past', async () => {
      api.on('listPublicEvents', () => page([pastEvent(3, { eventCode: 'BATbern59' })]));

      expect(await getUpcomingEvents()).toEqual([]);
    });
  });

  describe('presentation settings (EMS app settings since 4.9)', () => {
    it('reads them anonymously from GET /public/settings/presentation', async () => {
      api.on('getPresentationSettings', () => ({ aboutText: 'Über BATbern', partnerCount: 11 }));

      const result = await getPresentationSettings();

      expect(result).toEqual({ aboutText: 'Über BATbern', partnerCount: 11 });
      expect(api.calls('getPresentationSettings')[0].url.pathname).toMatch(
        /\/public\/settings\/presentation$/
      );
    });

    it('saves them as JSON under the app setting key presentation', async () => {
      const result = await updatePresentationSettings({
        aboutText: 'Plattform für O’Neill & Brünnimann',
        partnerCount: 12,
      });

      const [call] = api.calls('setAdminSetting');
      expect(call.method).toBe('PUT');
      expect(call.params).toEqual({ key: PRESENTATION_SETTING_KEY });
      expect(JSON.parse(call.body?.value ?? '')).toEqual({
        aboutText: 'Plattform für O’Neill & Brünnimann',
        partnerCount: 12,
      });
      expect(result).toEqual({ aboutText: 'Plattform für O’Neill & Brünnimann', partnerCount: 12 });
    });

    it('rejects with the 400 EMS answers for an invalid value', async () => {
      api.on('setAdminSetting', () =>
        HttpResponse.json({ status: 400, message: 'Invalid value' }, { status: 400 })
      );

      await expect(
        updatePresentationSettings({ aboutText: ' ', partnerCount: -1 })
      ).rejects.toMatchObject({ response: { status: 400 } });
    });
  });

  describe('getGlobalTeaserImages', () => {
    it('reads the global teaser images under the reserved event code _global', async () => {
      await getGlobalTeaserImages();

      const call = api.allCalls().find((c) => c.url.pathname.endsWith('/_global/teaser-images'));
      expect(call?.method).toBe('GET');
    });
  });

  it('exposes all functions on the service object', () => {
    expect(presentationService.getPresentationSettings).toBe(getPresentationSettings);
    expect(presentationService.updatePresentationSettings).toBe(updatePresentationSettings);
  });
});
