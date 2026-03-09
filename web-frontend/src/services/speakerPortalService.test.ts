/**
 * Speaker Portal Service Tests (Story 6.1a/6.2a/6.2b/6.3/6.4)
 *
 * Tests for all speaker portal endpoints:
 * - Token validation
 * - Response submission
 * - Dashboard
 * - Profile (get/update/photo)
 * - Content (info/draft/submit/materials)
 * - Error handling
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { speakerPortalService } from './speakerPortalService';
import type {
  TokenValidationResult,
  SpeakerResponseResult,
  SpeakerDashboard,
  SpeakerProfile,
  PresignedPhotoUploadResponse,
  PhotoConfirmResponse,
  SpeakerContentInfo,
  ContentDraftResponse,
  ContentSubmitResponse,
  MaterialUploadResponse,
  MaterialConfirmResponse,
} from './speakerPortalService';
import apiClient from './api/apiClient';

vi.mock('./api/apiClient', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
  },
}));

const mockApiClient = vi.mocked(apiClient);

describe('speakerPortalService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  // ── validateToken ────────────────────────────────────────────────────────────

  describe('validateToken', () => {
    it('should return token validation result on success', async () => {
      const mockResult: TokenValidationResult = {
        valid: true,
        speakerName: 'Alice Müller',
        eventCode: 'BATbern142',
        eventTitle: 'BATbern #142',
        eventDate: '2026-04-15T18:00:00Z',
        alreadyResponded: false,
      };
      mockApiClient.post.mockResolvedValue({ data: mockResult });

      const result = await speakerPortalService.validateToken('abc-token');

      expect(mockApiClient.post).toHaveBeenCalledWith(
        '/speaker-portal/validate-token',
        { token: 'abc-token' },
        { headers: { 'Skip-Auth': 'true' } }
      );
      expect(result).toEqual(mockResult);
    });

    it('should throw transformed error on network failure', async () => {
      const networkError = { isAxiosError: true, response: undefined, message: 'Network Error' };
      mockApiClient.post.mockRejectedValue(networkError);

      await expect(speakerPortalService.validateToken('bad-token')).rejects.toThrow(
        'Network Error: Unable to connect to server'
      );
    });

    it('should include correlation ID in error message when present', async () => {
      const axiosError = {
        isAxiosError: true,
        response: {
          status: 400,
          data: { errorCode: 'INVALID_TOKEN', message: 'Token is invalid' },
          headers: { 'x-correlation-id': 'corr-123' },
        },
      };
      mockApiClient.post.mockRejectedValue(axiosError);

      await expect(speakerPortalService.validateToken('bad-token')).rejects.toThrow('corr-123');
    });

    it('should attach previousResponse and respondedAt on 409 conflict', async () => {
      const axiosError = {
        isAxiosError: true,
        response: {
          status: 409,
          data: {
            errorCode: 'ALREADY_RESPONDED',
            message: 'Already responded',
            previousResponse: 'ACCEPT',
            respondedAt: '2026-03-01T10:00:00Z',
          },
          headers: {},
        },
      };
      mockApiClient.post.mockRejectedValue(axiosError);

      try {
        await speakerPortalService.validateToken('token');
        expect.fail('should have thrown');
      } catch (err: unknown) {
        const e = err as Error & { previousResponse?: string; respondedAt?: string };
        expect(e.previousResponse).toBe('ACCEPT');
        expect(e.respondedAt).toBe('2026-03-01T10:00:00Z');
      }
    });

    it('should re-throw plain Error instances unchanged', async () => {
      const plainError = new Error('Something went wrong');
      mockApiClient.post.mockRejectedValue(plainError);

      await expect(speakerPortalService.validateToken('token')).rejects.toThrow(
        'Something went wrong'
      );
    });
  });

  // ── respond ──────────────────────────────────────────────────────────────────

  describe('respond', () => {
    it('should submit ACCEPT response and return result', async () => {
      const mockResult: SpeakerResponseResult = {
        success: true,
        speakerName: 'Alice',
        eventName: 'BATbern #142',
        nextSteps: ['Please upload your slides'],
      };
      mockApiClient.post.mockResolvedValue({ data: mockResult });

      const result = await speakerPortalService.respond({
        token: 'tok',
        response: 'ACCEPT',
        preferences: { timeSlot: 'morning' },
      });

      expect(mockApiClient.post).toHaveBeenCalledWith(
        '/speaker-portal/respond',
        { token: 'tok', response: 'ACCEPT', preferences: { timeSlot: 'morning' } },
        { headers: { 'Skip-Auth': 'true' } }
      );
      expect(result.success).toBe(true);
    });

    it('should submit DECLINE response', async () => {
      const mockResult: SpeakerResponseResult = {
        success: true,
        speakerName: 'Bob',
        eventName: 'BATbern #142',
        nextSteps: [],
      };
      mockApiClient.post.mockResolvedValue({ data: mockResult });

      await speakerPortalService.respond({ token: 'tok', response: 'DECLINE', reason: 'Busy' });

      expect(mockApiClient.post).toHaveBeenCalledWith(
        '/speaker-portal/respond',
        { token: 'tok', response: 'DECLINE', reason: 'Busy' },
        expect.anything()
      );
    });

    it('should throw on API error', async () => {
      mockApiClient.post.mockRejectedValue({
        isAxiosError: true,
        response: { status: 404, data: { message: 'Not found' }, headers: {} },
      });

      await expect(
        speakerPortalService.respond({ token: 'tok', response: 'ACCEPT' })
      ).rejects.toThrow();
    });
  });

  // ── getDashboard ─────────────────────────────────────────────────────────────

  describe('getDashboard', () => {
    it('should fetch dashboard with token as query param', async () => {
      const mockDashboard: SpeakerDashboard = {
        speakerName: 'Alice',
        profilePictureUrl: null,
        profileCompleteness: 80,
        upcomingEvents: [],
        pastEvents: [],
      };
      mockApiClient.get.mockResolvedValue({ data: mockDashboard });

      const result = await speakerPortalService.getDashboard('tok');

      expect(mockApiClient.get).toHaveBeenCalledWith('/speaker-portal/dashboard', {
        params: { token: 'tok' },
        headers: { 'Skip-Auth': 'true' },
      });
      expect(result.speakerName).toBe('Alice');
    });

    it('should throw on network error', async () => {
      mockApiClient.get.mockRejectedValue({ isAxiosError: true, response: undefined });

      await expect(speakerPortalService.getDashboard('tok')).rejects.toThrow();
    });
  });

  // ── getProfile ───────────────────────────────────────────────────────────────

  describe('getProfile', () => {
    it('should fetch speaker profile', async () => {
      const mockProfile: SpeakerProfile = {
        username: 'alice',
        email: 'alice@example.com',
        firstName: 'Alice',
        lastName: 'Müller',
        bio: null,
        profilePictureUrl: null,
        expertiseAreas: [],
        speakingTopics: [],
        linkedInUrl: null,
        languages: ['de', 'en'],
        profileCompleteness: 60,
        missingFields: ['bio'],
      };
      mockApiClient.get.mockResolvedValue({ data: mockProfile });

      const result = await speakerPortalService.getProfile('tok');

      expect(mockApiClient.get).toHaveBeenCalledWith('/speaker-portal/profile', {
        params: { token: 'tok' },
        headers: { 'Skip-Auth': 'true' },
      });
      expect(result.username).toBe('alice');
    });
  });

  // ── updateProfile ────────────────────────────────────────────────────────────

  describe('updateProfile', () => {
    it('should patch profile and return updated data', async () => {
      const mockProfile: SpeakerProfile = {
        username: 'alice',
        email: 'alice@example.com',
        firstName: 'Alice',
        lastName: 'Müller',
        bio: 'Updated bio',
        profilePictureUrl: null,
        expertiseAreas: ['Java'],
        speakingTopics: ['Microservices'],
        linkedInUrl: null,
        languages: ['de'],
        profileCompleteness: 85,
        missingFields: [],
      };
      mockApiClient.patch.mockResolvedValue({ data: mockProfile });

      const result = await speakerPortalService.updateProfile({
        token: 'tok',
        bio: 'Updated bio',
        expertiseAreas: ['Java'],
      });

      expect(mockApiClient.patch).toHaveBeenCalledWith(
        '/speaker-portal/profile',
        { token: 'tok', bio: 'Updated bio', expertiseAreas: ['Java'] },
        { headers: { 'Skip-Auth': 'true' } }
      );
      expect(result.bio).toBe('Updated bio');
    });
  });

  // ── getPhotoPresignedUrl ──────────────────────────────────────────────────────

  describe('getPhotoPresignedUrl', () => {
    it('should return presigned upload URL', async () => {
      const mockResponse: PresignedPhotoUploadResponse = {
        uploadUrl: 'https://s3.example.com/upload',
        uploadId: 'upload-123',
        s3Key: 'photos/alice.jpg',
        expiresIn: 3600,
        maxSizeBytes: 5_000_000,
      };
      mockApiClient.post.mockResolvedValue({ data: mockResponse });

      const result = await speakerPortalService.getPhotoPresignedUrl({
        token: 'tok',
        fileName: 'photo.jpg',
        fileSize: 200_000,
        contentType: 'image/jpeg',
      });

      expect(mockApiClient.post).toHaveBeenCalledWith(
        '/speaker-portal/profile/photo/presigned-url',
        expect.objectContaining({ token: 'tok', fileName: 'photo.jpg' }),
        { headers: { 'Skip-Auth': 'true' } }
      );
      expect(result.uploadUrl).toBe('https://s3.example.com/upload');
    });
  });

  // ── confirmPhotoUpload ────────────────────────────────────────────────────────

  describe('confirmPhotoUpload', () => {
    it('should confirm upload and return photo URL', async () => {
      const mockResponse: PhotoConfirmResponse = {
        profilePictureUrl: 'https://cdn.example.com/alice.jpg',
      };
      mockApiClient.post.mockResolvedValue({ data: mockResponse });

      const result = await speakerPortalService.confirmPhotoUpload({
        token: 'tok',
        uploadId: 'upload-123',
        s3Key: 'photos/alice.jpg',
      });

      expect(result.profilePictureUrl).toBe('https://cdn.example.com/alice.jpg');
    });
  });

  // ── getContentInfo ────────────────────────────────────────────────────────────

  describe('getContentInfo', () => {
    it('should fetch content info', async () => {
      const mockContent: SpeakerContentInfo = {
        speakerName: 'Alice',
        eventCode: 'BATbern142',
        eventTitle: 'BATbern #142',
        hasSessionAssigned: true,
        sessionTitle: 'Kubernetes Security',
        canSubmitContent: true,
        contentStatus: null,
        hasDraft: false,
        draftTitle: null,
        draftAbstract: null,
        draftVersion: null,
        lastSavedAt: null,
        needsRevision: false,
        reviewerFeedback: null,
        reviewedAt: null,
        reviewedBy: null,
        hasMaterial: false,
        materialUrl: null,
        materialFileName: null,
      };
      mockApiClient.get.mockResolvedValue({ data: mockContent });

      const result = await speakerPortalService.getContentInfo('tok');

      expect(mockApiClient.get).toHaveBeenCalledWith('/speaker-portal/content', {
        params: { token: 'tok' },
        headers: { 'Skip-Auth': 'true' },
      });
      expect(result.hasSessionAssigned).toBe(true);
    });
  });

  // ── saveDraft ─────────────────────────────────────────────────────────────────

  describe('saveDraft', () => {
    it('should save draft and return save timestamp', async () => {
      const mockResponse: ContentDraftResponse = {
        draftId: 'draft-abc',
        savedAt: '2026-03-01T10:00:00Z',
      };
      mockApiClient.post.mockResolvedValue({ data: mockResponse });

      const result = await speakerPortalService.saveDraft({
        token: 'tok',
        title: 'My Talk',
        contentAbstract: 'An abstract',
      });

      expect(mockApiClient.post).toHaveBeenCalledWith(
        '/speaker-portal/content/draft',
        { token: 'tok', title: 'My Talk', contentAbstract: 'An abstract' },
        { headers: { 'Skip-Auth': 'true' } }
      );
      expect(result.draftId).toBe('draft-abc');
    });
  });

  // ── submitContent ─────────────────────────────────────────────────────────────

  describe('submitContent', () => {
    it('should submit content and return submission details', async () => {
      const mockResponse: ContentSubmitResponse = {
        submissionId: 'sub-xyz',
        version: 1,
        status: 'SUBMITTED',
        sessionTitle: 'Kubernetes Security',
      };
      mockApiClient.post.mockResolvedValue({ data: mockResponse });

      const result = await speakerPortalService.submitContent({
        token: 'tok',
        title: 'Kubernetes Security',
        contentAbstract: 'A talk about k8s security',
      });

      expect(mockApiClient.post).toHaveBeenCalledWith(
        '/speaker-portal/content/submit',
        expect.objectContaining({ token: 'tok', title: 'Kubernetes Security' }),
        { headers: { 'Skip-Auth': 'true' } }
      );
      expect(result.status).toBe('SUBMITTED');
    });
  });

  // ── getMaterialPresignedUrl ───────────────────────────────────────────────────

  describe('getMaterialPresignedUrl', () => {
    it('should return material upload URL', async () => {
      const mockResponse: MaterialUploadResponse = {
        uploadUrl: 'https://s3.example.com/material-upload',
        uploadId: 'mat-123',
        s3Key: 'materials/slides.pdf',
        fileExtension: 'pdf',
        expiresInMinutes: 60,
        requiredHeaders: { 'Content-Type': 'application/pdf' },
      };
      mockApiClient.post.mockResolvedValue({ data: mockResponse });

      const result = await speakerPortalService.getMaterialPresignedUrl({
        token: 'tok',
        fileName: 'slides.pdf',
        fileSize: 1_000_000,
        mimeType: 'application/pdf',
      });

      expect(mockApiClient.post).toHaveBeenCalledWith(
        '/speaker-portal/materials/presigned-url',
        expect.objectContaining({ token: 'tok', fileName: 'slides.pdf' }),
        { headers: { 'Skip-Auth': 'true' } }
      );
      expect(result.fileExtension).toBe('pdf');
    });
  });

  // ── confirmMaterialUpload ─────────────────────────────────────────────────────

  describe('confirmMaterialUpload', () => {
    it('should confirm material upload', async () => {
      const mockResponse: MaterialConfirmResponse = {
        materialId: 'mat-abc',
        uploadId: 'mat-123',
        fileName: 'slides.pdf',
        cloudFrontUrl: 'https://cdn.example.com/slides.pdf',
        materialType: 'PRESENTATION',
        uploadedAt: '2026-03-01T10:00:00Z',
      };
      mockApiClient.post.mockResolvedValue({ data: mockResponse });

      const result = await speakerPortalService.confirmMaterialUpload({
        token: 'tok',
        uploadId: 'mat-123',
        fileName: 'slides.pdf',
        fileExtension: 'pdf',
        fileSize: 1_000_000,
        mimeType: 'application/pdf',
        materialType: 'PRESENTATION',
      });

      expect(mockApiClient.post).toHaveBeenCalledWith(
        '/speaker-portal/materials/confirm',
        expect.objectContaining({ token: 'tok', uploadId: 'mat-123' }),
        { headers: { 'Skip-Auth': 'true' } }
      );
      expect(result.cloudFrontUrl).toBe('https://cdn.example.com/slides.pdf');
    });
  });
});
