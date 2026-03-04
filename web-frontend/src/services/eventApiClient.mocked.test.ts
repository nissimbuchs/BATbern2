/**
 * Event API Client — Mocked Tests
 *
 * Covers the methods and branches not exercised by eventApiClient.test.ts
 * (which uses real network calls). This file mocks apiClient and axios
 * to test success paths, status-specific error handling, and photo/teaser
 * methods that require mock responses.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { AxiosError } from 'axios';

vi.mock('@/services/api/apiClient', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
    defaults: { timeout: 30000 },
  },
}));

vi.mock('axios', async (importOriginal) => {
  const actual = await importOriginal<typeof import('axios')>();
  return {
    ...actual,
    default: {
      ...actual.default,
      put: vi.fn(),
    },
  };
});

import { eventApiClient } from './eventApiClient';
import apiClient from '@/services/api/apiClient';
import axios from 'axios';

const mockApiClient = vi.mocked(apiClient);
const mockAxios = vi.mocked(axios);

// Helper: build AxiosError with a given status code
function axiosErr(status: number, data?: object, correlationId?: string): AxiosError {
  const err = new AxiosError('Request failed');
  Object.defineProperty(err, 'isAxiosError', { value: true, writable: false });
  err.response = {
    status,
    data: data ?? {},
    headers: correlationId ? { 'x-correlation-id': correlationId } : {},
    statusText: String(status),
    config: {} as AxiosError['config'],
  };
  return err;
}

describe('EventApiClient — mocked', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  // ── getEvents success path ────────────────────────────────────────────────────

  describe('getEvents', () => {
    it('should return events list on success', async () => {
      const mockResponse = {
        data: [{ eventCode: 'BATbern142', title: 'BATbern #142' }],
        total: 1,
        page: 1,
        limit: 20,
      };
      mockApiClient.get.mockResolvedValue({ data: mockResponse });

      const result = await eventApiClient.getEvents();

      expect(result).toEqual(mockResponse);
    });

    it('should add filter param when workflowState filter provided', async () => {
      mockApiClient.get.mockResolvedValue({ data: { data: [], total: 0 } });

      await eventApiClient.getEvents(
        { page: 1, limit: 20 },
        { workflowState: ['ARCHIVED', 'COMPLETED'] }
      );

      const url: string = mockApiClient.get.mock.calls[0][0] as string;
      expect(url).toContain('filter=');
      expect(url).toContain('workflowState');
    });

    it('should add year filter when provided', async () => {
      mockApiClient.get.mockResolvedValue({ data: { data: [], total: 0 } });

      await eventApiClient.getEvents({ page: 1, limit: 20 }, { year: 2024 });

      const url: string = mockApiClient.get.mock.calls[0][0] as string;
      expect(url).toContain('2024');
    });

    it('should add topicCode filter when provided', async () => {
      mockApiClient.get.mockResolvedValue({ data: { data: [], total: 0 } });

      await eventApiClient.getEvents({ page: 1, limit: 20 }, { topicCode: ['cloud'] } as Parameters<
        typeof eventApiClient.getEvents
      >[1]);

      const url: string = mockApiClient.get.mock.calls[0][0] as string;
      expect(url).toContain('filter=');
    });

    it('should add includeArchived param when provided', async () => {
      mockApiClient.get.mockResolvedValue({ data: { data: [], total: 0 } });

      await eventApiClient.getEvents({ page: 1, limit: 20 }, {
        includeArchived: true,
      } as Parameters<typeof eventApiClient.getEvents>[1]);

      const url: string = mockApiClient.get.mock.calls[0][0] as string;
      expect(url).toContain('includeArchived=true');
    });
  });

  // ── getEvent success path ─────────────────────────────────────────────────────

  describe('getEvent', () => {
    it('should return event detail on success', async () => {
      const mockEvent = {
        eventCode: 'BATbern142',
        title: 'BATbern #142',
        workflowState: 'AGENDA_PUBLISHED',
      };
      mockApiClient.get.mockResolvedValue({ data: mockEvent });

      const result = await eventApiClient.getEvent('BATbern142');

      expect(result.eventCode).toBe('BATbern142');
    });

    it('should append include param when expand options provided', async () => {
      mockApiClient.get.mockResolvedValue({ data: {} });

      await eventApiClient.getEvent('BATbern142', { expand: ['sessions', 'speakers'] });

      const url: string = mockApiClient.get.mock.calls[0][0] as string;
      expect(url).toContain('include=sessions%2Cspeakers');
    });
  });

  // ── getCurrentEvent ───────────────────────────────────────────────────────────

  describe('getCurrentEvent', () => {
    it('should return event on success', async () => {
      const mockEvent = { eventCode: 'BATbern142', title: 'BATbern #142' };
      mockApiClient.get.mockResolvedValue({ data: mockEvent });

      const result = await eventApiClient.getCurrentEvent();

      expect(result?.eventCode).toBe('BATbern142');
    });

    it('should return null on 404', async () => {
      mockApiClient.get.mockRejectedValue(axiosErr(404));

      const result = await eventApiClient.getCurrentEvent();

      expect(result).toBeNull();
    });

    it('should throw transformed error on non-404', async () => {
      mockApiClient.get.mockRejectedValue(axiosErr(500));

      await expect(eventApiClient.getCurrentEvent()).rejects.toThrow('Server Error');
    });

    it('should append include param when expand provided', async () => {
      mockApiClient.get.mockResolvedValue({ data: {} });

      await eventApiClient.getCurrentEvent({ expand: ['sessions'] });

      const url: string = mockApiClient.get.mock.calls[0][0] as string;
      expect(url).toContain('include=sessions');
    });
  });

  // ── createEvent success path ──────────────────────────────────────────────────

  describe('createEvent', () => {
    it('should return created event on success', async () => {
      const future = new Date(Date.now() + 86400_000 * 60).toISOString();
      const deadline = new Date(Date.now() + 86400_000 * 50).toISOString();
      const mockEvent = { eventCode: 'BATbern143' };
      mockApiClient.post.mockResolvedValue({ data: mockEvent });

      const result = await eventApiClient.createEvent({
        title: 'New Event',
        eventNumber: 143,
        date: future,
        registrationDeadline: deadline,
        venueName: 'Venue',
        venueAddress: 'Address',
        venueCapacity: 100,
        organizerUsername: 'admin',
        eventType: 'EVENING',
        currentAttendeeCount: 0,
      });

      expect(result.eventCode).toBe('BATbern143');
    });
  });

  // ── updateEvent success path ──────────────────────────────────────────────────

  describe('updateEvent', () => {
    it('should return updated event on success', async () => {
      const mockEvent = { eventCode: 'BATbern142', title: 'Updated' };
      mockApiClient.put.mockResolvedValue({ data: mockEvent });

      const result = await eventApiClient.updateEvent('BATbern142', { title: 'Updated' });

      expect(result.title).toBe('Updated');
    });

    it('should skip date validation when date not in update', async () => {
      mockApiClient.put.mockResolvedValue({ data: {} });

      await expect(
        eventApiClient.updateEvent('BATbern142', { title: 'No date' })
      ).resolves.toBeDefined();
    });
  });

  // ── patchEvent ────────────────────────────────────────────────────────────────

  describe('patchEvent', () => {
    it('should return patched event on success', async () => {
      const mockEvent = { eventCode: 'BATbern142', title: 'Patched' };
      mockApiClient.patch.mockResolvedValue({ data: mockEvent });

      const future = new Date(Date.now() + 86400_000 * 60).toISOString();
      const result = await eventApiClient.patchEvent('BATbern142', {
        title: 'Patched',
        date: future,
      });

      expect(result.title).toBe('Patched');
    });
  });

  // ── deleteEvent ───────────────────────────────────────────────────────────────

  describe('deleteEvent', () => {
    it('should call DELETE and resolve on success', async () => {
      mockApiClient.delete.mockResolvedValue({ data: undefined });

      await expect(eventApiClient.deleteEvent('BATbern142')).resolves.toBeUndefined();

      expect(mockApiClient.delete).toHaveBeenCalledWith('/events/BATbern142');
    });
  });

  // ── createRegistration ────────────────────────────────────────────────────────

  describe('createRegistration', () => {
    it('should return message and email on success', async () => {
      mockApiClient.post.mockResolvedValue({
        data: { message: 'Registered', email: 'alice@example.com' },
      });

      const result = await eventApiClient.createRegistration('BATbern142', {
        firstName: 'Alice',
        lastName: 'Müller',
        email: 'alice@example.com',
        companyName: 'Acme',
      });

      expect(result.email).toBe('alice@example.com');
    });
  });

  // ── confirmRegistration ───────────────────────────────────────────────────────

  describe('confirmRegistration', () => {
    it('should return status on success', async () => {
      mockApiClient.post.mockResolvedValue({ data: { message: 'Confirmed', status: 'CONFIRMED' } });

      const result = await eventApiClient.confirmRegistration('BATbern142', 'jwt-token');

      expect(result.status).toBe('CONFIRMED');
      expect(mockApiClient.post).toHaveBeenCalledWith(
        '/events/BATbern142/registrations/confirm',
        null,
        expect.objectContaining({ params: { token: 'jwt-token' } })
      );
    });
  });

  // ── cancelRegistration ────────────────────────────────────────────────────────

  describe('cancelRegistration', () => {
    it('should return status on success', async () => {
      mockApiClient.post.mockResolvedValue({ data: { message: 'Cancelled', status: 'CANCELLED' } });

      const result = await eventApiClient.cancelRegistration('BATbern142', 'jwt-token');

      expect(result.status).toBe('CANCELLED');
    });
  });

  // ── getRegistration ───────────────────────────────────────────────────────────

  describe('getRegistration', () => {
    it('should return registration data', async () => {
      const mockReg = { confirmationCode: 'ABC123', status: 'CONFIRMED' };
      mockApiClient.get.mockResolvedValue({ data: mockReg });

      const result = await eventApiClient.getRegistration('BATbern142', 'ABC123');

      expect(result.confirmationCode).toBe('ABC123');
    });
  });

  // ── assignTopicToEvent ────────────────────────────────────────────────────────

  describe('assignTopicToEvent', () => {
    it('should POST topic assignment', async () => {
      mockApiClient.post.mockResolvedValue({ data: undefined });

      await eventApiClient.assignTopicToEvent('BATbern142', 'cloud');

      expect(mockApiClient.post).toHaveBeenCalledWith('/events/BATbern142/topics', {
        topicCode: 'cloud',
      });
    });
  });

  // ── checkEventExists ──────────────────────────────────────────────────────────

  describe('checkEventExists', () => {
    it('should return true when events returned', async () => {
      mockApiClient.get.mockResolvedValue({
        data: { data: [{ eventCode: 'BATbern56' }], total: 1 },
      });

      const result = await eventApiClient.checkEventExists(56);

      expect(result).toBe(true);
    });

    it('should return false when no events returned', async () => {
      mockApiClient.get.mockResolvedValue({ data: { data: [], total: 0 } });

      const result = await eventApiClient.checkEventExists(999);

      expect(result).toBe(false);
    });

    it('should throw transformed error on non-404 network error', async () => {
      // checkEventExists delegates to getEvents which transforms errors.
      // A 500 error becomes a plain "Server Error" — not swallowed.
      mockApiClient.get.mockRejectedValue(axiosErr(500));

      await expect(eventApiClient.checkEventExists(999)).rejects.toThrow('Server Error');
    });
  });

  // ── Event Photos (Story 10.21) ────────────────────────────────────────────────

  describe('listEventPhotos', () => {
    it('should return photo list', async () => {
      const mockPhotos = [{ id: 'photo-1', cloudFrontUrl: 'https://cdn.example.com/photo.jpg' }];
      mockApiClient.get.mockResolvedValue({ data: mockPhotos });

      const result = await eventApiClient.listEventPhotos('BATbern142');

      expect(mockApiClient.get).toHaveBeenCalledWith('/events/BATbern142/photos');
      expect(result).toEqual(mockPhotos);
    });
  });

  describe('requestEventPhotoUploadUrl', () => {
    it('should return upload URL response', async () => {
      const mockResponse = { uploadUrl: 'https://s3.example.com/upload', uploadId: 'up-1' };
      mockApiClient.post.mockResolvedValue({ data: mockResponse });

      const result = await eventApiClient.requestEventPhotoUploadUrl('BATbern142', {
        fileName: 'photo.jpg',
        fileSize: 200_000,
        mimeType: 'image/jpeg',
      });

      expect(result.uploadUrl).toBe('https://s3.example.com/upload');
    });
  });

  describe('uploadPhotoToS3', () => {
    it('should call axios.put with the upload URL and file', async () => {
      mockAxios.put.mockResolvedValue({ status: 200 });

      const mockFile = new File(['content'], 'photo.jpg', { type: 'image/jpeg' });
      await eventApiClient.uploadPhotoToS3('https://s3.example.com/upload', mockFile);

      expect(mockAxios.put).toHaveBeenCalledWith('https://s3.example.com/upload', mockFile, {
        headers: { 'Content-Type': 'image/jpeg' },
      });
    });
  });

  describe('confirmEventPhotoUpload', () => {
    it('should return confirmed photo', async () => {
      const mockPhoto = { id: 'photo-1', cloudFrontUrl: 'https://cdn.example.com/photo.jpg' };
      mockApiClient.post.mockResolvedValue({ data: mockPhoto });

      const result = await eventApiClient.confirmEventPhotoUpload('BATbern142', {
        uploadId: 'up-1',
        fileName: 'photo.jpg',
        fileSize: 200_000,
        mimeType: 'image/jpeg',
      });

      expect(result.id).toBe('photo-1');
    });
  });

  describe('deleteEventPhoto', () => {
    it('should call DELETE on photo endpoint', async () => {
      mockApiClient.delete.mockResolvedValue({ data: undefined });

      await eventApiClient.deleteEventPhoto('BATbern142', 'photo-1');

      expect(mockApiClient.delete).toHaveBeenCalledWith('/events/BATbern142/photos/photo-1');
    });
  });

  describe('getRecentEventPhotos', () => {
    it('should return recent photos with default params', async () => {
      const mockPhotos = [{ id: 'photo-1' }];
      mockApiClient.get.mockResolvedValue({ data: mockPhotos });

      const result = await eventApiClient.getRecentEventPhotos();

      expect(mockApiClient.get).toHaveBeenCalledWith('/events/recent-photos', {
        params: { limit: 20, lastNEvents: 5 },
      });
      expect(result).toEqual(mockPhotos);
    });

    it('should pass custom limit and lastNEvents', async () => {
      mockApiClient.get.mockResolvedValue({ data: [] });

      await eventApiClient.getRecentEventPhotos(10, 3);

      expect(mockApiClient.get).toHaveBeenCalledWith('/events/recent-photos', {
        params: { limit: 10, lastNEvents: 3 },
      });
    });
  });

  // ── Event Teaser Images (Story 10.22) ─────────────────────────────────────────

  describe('requestTeaserImageUploadUrl', () => {
    it('should return teaser image upload URL', async () => {
      const mockResponse = { uploadUrl: 'https://s3.example.com/teaser-upload', uploadId: 'ti-1' };
      mockApiClient.post.mockResolvedValue({ data: mockResponse });

      const result = await eventApiClient.requestTeaserImageUploadUrl('BATbern142', {
        fileName: 'teaser.jpg',
        fileSize: 150_000,
        mimeType: 'image/jpeg',
      });

      expect(mockApiClient.post).toHaveBeenCalledWith(
        '/events/BATbern142/teaser-images/upload-url',
        expect.objectContaining({ fileName: 'teaser.jpg' })
      );
      expect(result.uploadId).toBe('ti-1');
    });
  });

  describe('confirmTeaserImageUpload', () => {
    it('should return confirmed teaser image', async () => {
      const mockItem = { id: 'ti-1', cloudFrontUrl: 'https://cdn.example.com/teaser.jpg' };
      mockApiClient.post.mockResolvedValue({ data: mockItem });

      const result = await eventApiClient.confirmTeaserImageUpload('BATbern142', {
        uploadId: 'ti-1',
        fileName: 'teaser.jpg',
        fileSize: 150_000,
        mimeType: 'image/jpeg',
      });

      expect(result.id).toBe('ti-1');
    });
  });

  describe('deleteTeaserImage', () => {
    it('should call DELETE on teaser image endpoint', async () => {
      mockApiClient.delete.mockResolvedValue({ data: undefined });

      await eventApiClient.deleteTeaserImage('BATbern142', 'ti-1');

      expect(mockApiClient.delete).toHaveBeenCalledWith('/events/BATbern142/teaser-images/ti-1');
    });
  });

  // ── getMaterialDownloadUrl ────────────────────────────────────────────────────

  describe('getMaterialDownloadUrl', () => {
    it('should return download URL string', async () => {
      mockApiClient.get.mockResolvedValue({
        data: { downloadUrl: 'https://s3.example.com/material' },
      });

      const result = await eventApiClient.getMaterialDownloadUrl(
        'BATbern142',
        'session-1',
        'mat-uuid'
      );

      expect(result).toBe('https://s3.example.com/material');
    });
  });

  // ── transformError status codes ───────────────────────────────────────────────

  describe('transformError', () => {
    it('should produce "Unauthorized" message for 401', async () => {
      mockApiClient.get.mockRejectedValue(axiosErr(401));

      await expect(eventApiClient.getEvent('BATbern142')).rejects.toThrow('Unauthorized');
    });

    it('should produce "Forbidden" message for 403', async () => {
      mockApiClient.get.mockRejectedValue(axiosErr(403));

      await expect(eventApiClient.getEvent('BATbern142')).rejects.toThrow('Forbidden');
    });

    it('should produce "Not Found" message for 404', async () => {
      mockApiClient.get.mockRejectedValue(axiosErr(404));

      await expect(eventApiClient.getEvents()).rejects.toThrow('Not Found');
    });

    it('should include backend message for 409 conflict', async () => {
      mockApiClient.post.mockRejectedValue(axiosErr(409, { message: 'Already registered' }));

      await expect(
        eventApiClient.createRegistration('BATbern142', {
          firstName: 'Alice',
          lastName: 'Müller',
          email: 'alice@example.com',
          companyName: 'Acme',
        })
      ).rejects.toThrow('Already registered');
    });

    it('should use fallback message for 409 without message', async () => {
      mockApiClient.post.mockRejectedValue(axiosErr(409, {}));

      await expect(
        eventApiClient.createRegistration('BATbern142', {
          firstName: 'A',
          lastName: 'B',
          email: 'a@b.com',
          companyName: 'Corp',
        })
      ).rejects.toThrow('Conflict');
    });

    it('should produce "Server Error" message for 500', async () => {
      mockApiClient.get.mockRejectedValue(axiosErr(500));

      await expect(eventApiClient.getEvent('BATbern142')).rejects.toThrow('Server Error');
    });

    it('should append correlation ID to error message when present', async () => {
      mockApiClient.get.mockRejectedValue(axiosErr(404, {}, 'corr-abc'));

      await expect(eventApiClient.getEvent('BATbern142')).rejects.toThrow('corr-abc');
    });

    it('should produce "Network Error" when response is absent', async () => {
      // AxiosError already has isAxiosError=true by default; just omit response
      const netErr = new AxiosError('Network Error');
      // response is undefined by default
      mockApiClient.get.mockRejectedValue(netErr);

      await expect(eventApiClient.getEvents()).rejects.toThrow('Network Error');
    });

    it('should return plain errors as-is', async () => {
      const plainErr = new Error('plain error, not axios');
      mockApiClient.get.mockRejectedValue(plainErr);

      await expect(eventApiClient.getEvents()).rejects.toThrow('plain error, not axios');
    });
  });
});
