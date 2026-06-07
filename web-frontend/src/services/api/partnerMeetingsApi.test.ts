/**
 * Partner Meetings API Client Tests
 * Story 8.3: Partner Meeting Coordination — AC1–6, 8
 *
 * Tests for meetings API functions:
 * - getMeetings: List all partner meetings
 * - createMeeting: Create a new partner meeting
 * - getMeeting: Get a single meeting by ID
 * - updateMeeting: Update meeting details
 * - sendInvite: Send ICS calendar invite
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import apiClient from '@/services/api/apiClient';
import {
  getMeetings,
  createMeeting,
  getMeeting,
  updateMeeting,
  sendInvite,
  getRsvps,
  type MeetingRsvpListResponse,
} from './partnerMeetingsApi';

// Mock meeting data that matches the PartnerMeetingDTO shape
const mockMeeting = {
  id: 'meeting-1',
  eventCode: 'BAT-42',
  meetingDate: '2026-02-15T14:00:00Z',
  location: 'Conference Room A',
  agenda: 'Discuss partnership for BAT-42',
  notes: null,
  status: 'SCHEDULED',
  createdAt: '2026-01-20T10:00:00Z',
  updatedAt: '2026-01-20T10:00:00Z',
};

describe('Partner Meetings API Client - Story 8.3', () => {
  beforeEach(() => {
    vi.spyOn(apiClient, 'get').mockResolvedValue({ data: [] });
    vi.spyOn(apiClient, 'post').mockResolvedValue({ data: {} });
    vi.spyOn(apiClient, 'patch').mockResolvedValue({ data: {} });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('getMeetings', () => {
    it('should_callGetEndpoint_when_getMeetingsInvoked', async () => {
      const meetings = [mockMeeting];
      vi.mocked(apiClient.get).mockResolvedValue({ data: meetings });

      const result = await getMeetings();

      expect(apiClient.get).toHaveBeenCalledWith('/partner-meetings');
      expect(result).toEqual(meetings);
    });

    it('should_returnEmptyArray_when_noMeetingsExist', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: [] });

      const result = await getMeetings();

      expect(result).toEqual([]);
    });

    it('should_propagateError_when_getMeetingsFails', async () => {
      vi.mocked(apiClient.get).mockRejectedValue(new Error('Forbidden'));

      await expect(getMeetings()).rejects.toThrow('Forbidden');
    });
  });

  describe('createMeeting', () => {
    it('should_callPostEndpoint_when_createMeetingInvoked', async () => {
      const request = {
        eventCode: 'BAT-42',
        meetingDate: '2026-02-15T14:00:00Z',
        location: 'Conference Room A',
        agenda: 'Planning meeting',
      };
      vi.mocked(apiClient.post).mockResolvedValue({ data: mockMeeting });

      const result = await createMeeting(request as any);

      expect(apiClient.post).toHaveBeenCalledWith('/partner-meetings', request);
      expect(result).toEqual(mockMeeting);
    });

    it('should_propagateError_when_createMeetingFails', async () => {
      vi.mocked(apiClient.post).mockRejectedValue(new Error('Bad Request'));

      await expect(createMeeting({ eventCode: 'BAT-99' } as any)).rejects.toThrow('Bad Request');
    });
  });

  describe('getMeeting', () => {
    it('should_callGetEndpoint_when_getMeetingInvoked', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: mockMeeting });

      const result = await getMeeting('meeting-1');

      expect(apiClient.get).toHaveBeenCalledWith('/partner-meetings/meeting-1');
      expect(result).toEqual(mockMeeting);
    });

    it('should_propagateError_when_meetingNotFound', async () => {
      vi.mocked(apiClient.get).mockRejectedValue(new Error('Not Found'));

      await expect(getMeeting('nonexistent')).rejects.toThrow('Not Found');
    });
  });

  describe('updateMeeting', () => {
    it('should_callPatchEndpoint_when_updateMeetingInvoked', async () => {
      const request = { agenda: 'Updated agenda', notes: 'Action items discussed' };
      const updatedMeeting = { ...mockMeeting, ...request };
      vi.mocked(apiClient.patch).mockResolvedValue({ data: updatedMeeting });

      const result = await updateMeeting('meeting-1', request as any);

      expect(apiClient.patch).toHaveBeenCalledWith('/partner-meetings/meeting-1', request);
      expect(result).toEqual(updatedMeeting);
    });

    it('should_propagateError_when_updateMeetingFails', async () => {
      vi.mocked(apiClient.patch).mockRejectedValue(new Error('Unauthorized'));

      await expect(updateMeeting('meeting-1', {} as any)).rejects.toThrow('Unauthorized');
    });
  });

  describe('sendInvite', () => {
    it('should_callPostEndpoint_when_sendInviteInvoked', async () => {
      const inviteResponse = { message: 'Invite sent', recipientCount: 3 };
      vi.mocked(apiClient.post).mockResolvedValue({ data: inviteResponse });

      const result = await sendInvite('meeting-1');

      expect(apiClient.post).toHaveBeenCalledWith('/partner-meetings/meeting-1/send-invite');
      expect(result).toEqual(inviteResponse);
    });

    it('should_propagateError_when_sendInviteFails', async () => {
      vi.mocked(apiClient.post).mockRejectedValue(new Error('Service Unavailable'));

      await expect(sendInvite('meeting-1')).rejects.toThrow('Service Unavailable');
    });
  });

  // ─── Story 10.27: getRsvps ───────────────────────────────────────────────────

  describe('getRsvps', () => {
    const mockRsvpResponse: MeetingRsvpListResponse = {
      meetingId: 'meeting-1',
      inviteSentAt: '2026-03-01T10:00:00Z',
      rsvps: [
        {
          attendeeEmail: 'alice@partner.com',
          status: 'ACCEPTED',
          respondedAt: '2026-03-02T09:00:00Z',
        },
        {
          attendeeEmail: 'bob@partner.com',
          status: 'DECLINED',
          respondedAt: '2026-03-02T10:00:00Z',
        },
      ],
      summary: { accepted: 1, declined: 1, tentative: 0 },
    };

    it('should_callGetEndpoint_when_getRsvpsInvoked', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: mockRsvpResponse });

      const result = await getRsvps('meeting-1');

      expect(apiClient.get).toHaveBeenCalledWith('/partner-meetings/meeting-1/rsvps');
      expect(result).toEqual(mockRsvpResponse);
    });

    it('should_returnRsvpList_with_summary', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: mockRsvpResponse });

      const result = await getRsvps('meeting-1');

      expect(result.rsvps).toHaveLength(2);
      expect(result.summary.accepted).toBe(1);
      expect(result.summary.declined).toBe(1);
    });

    it('should_propagateError_when_getRsvpsFails', async () => {
      vi.mocked(apiClient.get).mockRejectedValue(new Error('Not Found'));

      await expect(getRsvps('nonexistent')).rejects.toThrow('Not Found');
    });
  });
});
