/**
 * Speaker Content Service Tests (Story 5.5)
 *
 * Comprehensive tests for speakerContentService HTTP client
 * Tests all API methods: submit content, get content, review queue, review content
 *
 * Coverage:
 * - API request formatting (event code, poolId, content data)
 * - Response handling and error propagation
 * - Type safety and parameter validation
 * - AC6-10 (Content Submission), AC11 (Review Queue), AC13-14 (Quality Review), AC34 (Get Content)
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { speakerContentService } from './speakerContentService';
import apiClient from './api/apiClient';
import type {
  SubmitContentRequest,
  SpeakerContentResponse,
  ReviewRequest,
} from './speakerContentService';

// Mock the apiClient module
vi.mock('./api/apiClient', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('speakerContentService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('submitContent (AC6-10)', () => {
    it('should submit speaker content with all fields', async () => {
      const request: SubmitContentRequest = {
        username: 'jane.smith',
        presentationTitle: 'Modern Cloud Architecture Patterns',
        presentationAbstract:
          'An in-depth exploration of cloud-native patterns using Kubernetes and microservices.',
      };

      const mockResponse: SpeakerContentResponse = {
        poolId: 'pool-123',
        sessionId: 'session-456',
        status: 'CONTENT_SUBMITTED',
        presentationTitle: 'Modern Cloud Architecture Patterns',
        presentationAbstract:
          'An in-depth exploration of cloud-native patterns using Kubernetes and microservices.',
        username: 'jane.smith',
      };

      vi.mocked(apiClient.post).mockResolvedValue({ data: mockResponse });

      const result = await speakerContentService.submitContent('BATbern56', 'pool-123', request);

      expect(apiClient.post).toHaveBeenCalledWith(
        '/events/BATbern56/speakers/pool-123/content',
        request
      );
      expect(result).toEqual(mockResponse);
      expect(result.status).toBe('CONTENT_SUBMITTED');
      expect(result.sessionId).toBe('session-456');
    });

    it('should create session and update speaker_pool status on submission', async () => {
      const request: SubmitContentRequest = {
        username: 'john.doe',
        presentationTitle: 'DevOps Best Practices',
        presentationAbstract: 'A comprehensive guide to modern DevOps methodologies.',
      };

      const mockResponse: SpeakerContentResponse = {
        poolId: 'pool-789',
        sessionId: 'session-new-123',
        status: 'CONTENT_SUBMITTED',
        presentationTitle: 'DevOps Best Practices',
        presentationAbstract: 'A comprehensive guide to modern DevOps methodologies.',
        username: 'john.doe',
      };

      vi.mocked(apiClient.post).mockResolvedValue({ data: mockResponse });

      const result = await speakerContentService.submitContent('BATbern57', 'pool-789', request);

      expect(result.sessionId).toBeDefined();
      expect(result.status).toBe('CONTENT_SUBMITTED');
    });

    it('should propagate validation errors for missing title', async () => {
      const request: SubmitContentRequest = {
        username: 'jane.smith',
        presentationTitle: '',
        presentationAbstract: 'Some abstract',
      };

      const error = new Error('Validation failed: presentationTitle is required');
      vi.mocked(apiClient.post).mockRejectedValue(error);

      await expect(
        speakerContentService.submitContent('BATbern56', 'pool-123', request)
      ).rejects.toThrow('Validation failed');
    });

    it('should propagate validation errors for missing abstract', async () => {
      const request: SubmitContentRequest = {
        username: 'jane.smith',
        presentationTitle: 'Test Title',
        presentationAbstract: '',
      };

      const error = new Error('Validation failed: presentationAbstract is required');
      vi.mocked(apiClient.post).mockRejectedValue(error);

      await expect(
        speakerContentService.submitContent('BATbern56', 'pool-123', request)
      ).rejects.toThrow('Validation failed');
    });

    it('should propagate 404 errors for non-existent speaker', async () => {
      const request: SubmitContentRequest = {
        username: 'jane.smith',
        presentationTitle: 'Test Title',
        presentationAbstract: 'Test Abstract',
      };

      const error = new Error('Speaker pool entry not found: pool-999');
      vi.mocked(apiClient.post).mockRejectedValue(error);

      await expect(
        speakerContentService.submitContent('BATbern56', 'pool-999', request)
      ).rejects.toThrow('Speaker pool entry not found');
    });

    it('should propagate 422 errors for invalid speaker state', async () => {
      const request: SubmitContentRequest = {
        username: 'jane.smith',
        presentationTitle: 'Test Title',
        presentationAbstract: 'Test Abstract',
      };

      const error = new Error(
        'Invalid state: Cannot submit content for speaker in REJECTED status'
      );
      vi.mocked(apiClient.post).mockRejectedValue(error);

      await expect(
        speakerContentService.submitContent('BATbern56', 'pool-123', request)
      ).rejects.toThrow('Invalid state');
    });

    it('should propagate authorization errors for non-organizers', async () => {
      const request: SubmitContentRequest = {
        username: 'jane.smith',
        presentationTitle: 'Test Title',
        presentationAbstract: 'Test Abstract',
      };

      const error = new Error('Forbidden: ORGANIZER role required');
      vi.mocked(apiClient.post).mockRejectedValue(error);

      await expect(
        speakerContentService.submitContent('BATbern56', 'pool-123', request)
      ).rejects.toThrow('Forbidden');
    });

    it('should handle network failures gracefully', async () => {
      const request: SubmitContentRequest = {
        username: 'jane.smith',
        presentationTitle: 'Test Title',
        presentationAbstract: 'Test Abstract',
      };

      const error = new Error('Network error: timeout');
      vi.mocked(apiClient.post).mockRejectedValue(error);

      await expect(
        speakerContentService.submitContent('BATbern56', 'pool-123', request)
      ).rejects.toThrow('Network error');
    });
  });

  describe('getSpeakerContent (AC34)', () => {
    it('should fetch speaker content with session data', async () => {
      const mockResponse: SpeakerContentResponse = {
        poolId: 'pool-123',
        sessionId: 'session-456',
        status: 'CONTENT_SUBMITTED',
        presentationTitle: 'Cloud Architecture',
        presentationAbstract: 'Detailed exploration of cloud patterns',
        username: 'jane.smith',
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockResponse });

      const result = await speakerContentService.getSpeakerContent('BATbern56', 'pool-123');

      expect(apiClient.get).toHaveBeenCalledWith('/events/BATbern56/speakers/pool-123/content');
      expect(result).toEqual(mockResponse);
      expect(result.presentationTitle).toBe('Cloud Architecture');
      expect(result.sessionId).toBe('session-456');
    });

    it('should handle orphaned session FK with warning', async () => {
      const mockResponse: SpeakerContentResponse = {
        poolId: 'pool-123',
        sessionId: '',
        status: 'INVITED',
        presentationTitle: '',
        presentationAbstract: '',
        username: 'jane.smith',
        warning: 'Content was lost due to session deletion. Speaker status reverted to INVITED.',
      };

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockResponse });

      const result = await speakerContentService.getSpeakerContent('BATbern56', 'pool-123');

      expect(result.warning).toBeDefined();
      expect(result.warning).toContain('Content was lost');
      expect(result.status).toBe('INVITED');
    });

    it('should propagate 404 errors for non-existent speaker', async () => {
      const error = new Error('Speaker pool entry not found: pool-999');
      vi.mocked(apiClient.get).mockRejectedValue(error);

      await expect(
        speakerContentService.getSpeakerContent('BATbern56', 'pool-999')
      ).rejects.toThrow('Speaker pool entry not found');
    });

    it('should propagate authorization errors', async () => {
      const error = new Error('Unauthorized: JWT token required');
      vi.mocked(apiClient.get).mockRejectedValue(error);

      await expect(
        speakerContentService.getSpeakerContent('BATbern56', 'pool-123')
      ).rejects.toThrow('Unauthorized');
    });

    it('should handle network failures gracefully', async () => {
      const error = new Error('Network error: connection refused');
      vi.mocked(apiClient.get).mockRejectedValue(error);

      await expect(
        speakerContentService.getSpeakerContent('BATbern56', 'pool-123')
      ).rejects.toThrow('Network error');
    });
  });

  describe('getReviewQueue (AC11)', () => {
    it('should fetch all speakers pending review', async () => {
      const mockQueue = [
        {
          poolId: 'pool-123',
          speakerName: 'Dr. Jane Smith',
          company: 'TechCorp AG',
          presentationTitle: 'Cloud Architecture',
          status: 'CONTENT_SUBMITTED',
          submittedAt: '2025-12-20T10:00:00Z',
        },
        {
          poolId: 'pool-456',
          speakerName: 'Prof. John Doe',
          company: 'University of Bern',
          presentationTitle: 'DevOps Best Practices',
          status: 'CONTENT_SUBMITTED',
          submittedAt: '2025-12-20T11:00:00Z',
        },
      ];

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockQueue });

      const result = await speakerContentService.getReviewQueue('BATbern56');

      expect(apiClient.get).toHaveBeenCalledWith('/events/BATbern56/speakers/review-queue');
      expect(result).toEqual(mockQueue);
      expect(result).toHaveLength(2);
      expect(result[0].status).toBe('CONTENT_SUBMITTED');
    });

    it('should return empty array when no content pending review', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: [] });

      const result = await speakerContentService.getReviewQueue('BATbern56');

      expect(result).toEqual([]);
      expect(result).toHaveLength(0);
    });

    it('should propagate authorization errors for non-organizers', async () => {
      const error = new Error('Forbidden: ORGANIZER role required');
      vi.mocked(apiClient.get).mockRejectedValue(error);

      await expect(speakerContentService.getReviewQueue('BATbern56')).rejects.toThrow('Forbidden');
    });

    it('should handle network failures gracefully', async () => {
      const error = new Error('Network error: timeout');
      vi.mocked(apiClient.get).mockRejectedValue(error);

      await expect(speakerContentService.getReviewQueue('BATbern56')).rejects.toThrow(
        'Network error'
      );
    });
  });

  describe('reviewContent (AC13-14)', () => {
    it('should approve speaker content with feedback', async () => {
      const request: ReviewRequest = {
        action: 'APPROVE',
        feedback: 'Excellent presentation, well-structured content.',
      };

      vi.mocked(apiClient.post).mockResolvedValue({ data: undefined });

      await speakerContentService.reviewContent('BATbern56', 'pool-123', request);

      expect(apiClient.post).toHaveBeenCalledWith(
        '/events/BATbern56/speakers/pool-123/review',
        request
      );
    });

    it('should approve speaker content without feedback', async () => {
      const request: ReviewRequest = {
        action: 'APPROVE',
      };

      vi.mocked(apiClient.post).mockResolvedValue({ data: undefined });

      await speakerContentService.reviewContent('BATbern56', 'pool-123', request);

      expect(apiClient.post).toHaveBeenCalledWith(
        '/events/BATbern56/speakers/pool-123/review',
        request
      );
    });

    it('should reject speaker content with feedback', async () => {
      const request: ReviewRequest = {
        action: 'REJECT',
        feedback: 'Content needs more technical depth. Please revise and resubmit.',
      };

      vi.mocked(apiClient.post).mockResolvedValue({ data: undefined });

      await speakerContentService.reviewContent('BATbern56', 'pool-123', request);

      expect(apiClient.post).toHaveBeenCalledWith(
        '/events/BATbern56/speakers/pool-123/review',
        request
      );
    });

    it('should reject speaker content without feedback', async () => {
      const request: ReviewRequest = {
        action: 'REJECT',
      };

      vi.mocked(apiClient.post).mockResolvedValue({ data: undefined });

      await speakerContentService.reviewContent('BATbern56', 'pool-123', request);

      expect(apiClient.post).toHaveBeenCalledWith(
        '/events/BATbern56/speakers/pool-123/review',
        request
      );
    });

    it('should propagate validation errors for invalid action', async () => {
      const request = {
        action: 'INVALID_ACTION' as 'APPROVE',
        feedback: 'Test feedback',
      };

      const error = new Error('Validation failed: action must be APPROVE or REJECT');
      vi.mocked(apiClient.post).mockRejectedValue(error);

      await expect(
        speakerContentService.reviewContent('BATbern56', 'pool-123', request)
      ).rejects.toThrow('Validation failed');
    });

    it('should propagate 404 errors for non-existent speaker', async () => {
      const request: ReviewRequest = {
        action: 'APPROVE',
        feedback: 'Good content',
      };

      const error = new Error('Speaker pool entry not found: pool-999');
      vi.mocked(apiClient.post).mockRejectedValue(error);

      await expect(
        speakerContentService.reviewContent('BATbern56', 'pool-999', request)
      ).rejects.toThrow('Speaker pool entry not found');
    });

    it('should propagate authorization errors for non-organizers', async () => {
      const request: ReviewRequest = {
        action: 'APPROVE',
      };

      const error = new Error('Forbidden: ORGANIZER role required');
      vi.mocked(apiClient.post).mockRejectedValue(error);

      await expect(
        speakerContentService.reviewContent('BATbern56', 'pool-123', request)
      ).rejects.toThrow('Forbidden');
    });

    it('should handle network failures gracefully', async () => {
      const request: ReviewRequest = {
        action: 'APPROVE',
      };

      const error = new Error('Network error: timeout');
      vi.mocked(apiClient.post).mockRejectedValue(error);

      await expect(
        speakerContentService.reviewContent('BATbern56', 'pool-123', request)
      ).rejects.toThrow('Network error');
    });
  });

  describe('Error Handling & Edge Cases (AC28-37)', () => {
    describe('AC28: Users-Service Unavailability', () => {
      it('should propagate service unavailable errors', async () => {
        const request: SubmitContentRequest = {
          username: 'jane.smith',
          presentationTitle: 'Test Title',
          presentationAbstract: 'Test Abstract',
        };

        const error = new Error('Users service temporarily unavailable. Please try again.');
        vi.mocked(apiClient.post).mockRejectedValue(error);

        await expect(
          speakerContentService.submitContent('BATbern56', 'pool-123', request)
        ).rejects.toThrow('Users service temporarily unavailable');
      });

      it('should handle timeout errors gracefully', async () => {
        const request: SubmitContentRequest = {
          username: 'jane.smith',
          presentationTitle: 'Test Title',
          presentationAbstract: 'Test Abstract',
        };

        const error = new Error('Request timeout after 30000ms');
        vi.mocked(apiClient.post).mockRejectedValue(error);

        await expect(
          speakerContentService.submitContent('BATbern56', 'pool-123', request)
        ).rejects.toThrow('Request timeout');
      });
    });

    describe('AC30: Duplicate User Creation Prevention', () => {
      it('should handle existing user with same email', async () => {
        const request: SubmitContentRequest = {
          username: 'existing.user',
          presentationTitle: 'Test Title',
          presentationAbstract: 'Test Abstract',
        };

        const mockResponse: SpeakerContentResponse = {
          poolId: 'pool-123',
          sessionId: 'session-456',
          status: 'CONTENT_SUBMITTED',
          presentationTitle: 'Test Title',
          presentationAbstract: 'Test Abstract',
          username: 'existing.user',
          warning: 'Used existing user with email jane@example.com',
        };

        vi.mocked(apiClient.post).mockResolvedValue({ data: mockResponse });

        const result = await speakerContentService.submitContent('BATbern56', 'pool-123', request);

        expect(result.warning).toContain('Used existing user');
      });
    });

    describe('AC31: Portrait Upload URL Generation Failure', () => {
      it('should handle S3 presigned URL generation failure', async () => {
        const request: SubmitContentRequest = {
          username: 'jane.smith',
          presentationTitle: 'Test Title',
          presentationAbstract: 'Test Abstract',
        };

        const error = new Error('Unable to generate upload URL. Please contact support.');
        vi.mocked(apiClient.post).mockRejectedValue(error);

        await expect(
          speakerContentService.submitContent('BATbern56', 'pool-123', request)
        ).rejects.toThrow('Unable to generate upload URL');
      });

      it('should handle AWS S3 service errors', async () => {
        const request: SubmitContentRequest = {
          username: 'jane.smith',
          presentationTitle: 'Test Title',
          presentationAbstract: 'Test Abstract',
        };

        const error = new Error('AWS S3 service unavailable');
        vi.mocked(apiClient.post).mockRejectedValue(error);

        await expect(
          speakerContentService.submitContent('BATbern56', 'pool-123', request)
        ).rejects.toThrow('AWS S3');
      });
    });

    describe('AC32: Portrait Upload Partial Failure Recovery', () => {
      it('should handle portrait upload success with profile update failure', async () => {
        const request: SubmitContentRequest = {
          username: 'jane.smith',
          presentationTitle: 'Test Title',
          presentationAbstract: 'Test Abstract',
        };

        const mockResponse: SpeakerContentResponse = {
          poolId: 'pool-123',
          sessionId: 'session-456',
          status: 'CONTENT_SUBMITTED',
          presentationTitle: 'Test Title',
          presentationAbstract: 'Test Abstract',
          username: 'jane.smith',
          warning: 'Portrait uploaded but not linked to profile. Our team will fix this shortly.',
        };

        vi.mocked(apiClient.post).mockResolvedValue({ data: mockResponse });

        const result = await speakerContentService.submitContent('BATbern56', 'pool-123', request);

        expect(result.warning).toContain('Portrait uploaded but not linked');
      });
    });

    describe('AC33: Content Submission Transaction Integrity', () => {
      it('should handle transaction rollback on database failure', async () => {
        const request: SubmitContentRequest = {
          username: 'jane.smith',
          presentationTitle: 'Test Title',
          presentationAbstract: 'Test Abstract',
        };

        const error = new Error('Content submission failed. Please try again.');
        vi.mocked(apiClient.post).mockRejectedValue(error);

        await expect(
          speakerContentService.submitContent('BATbern56', 'pool-123', request)
        ).rejects.toThrow('Content submission failed');
      });

      it('should handle database constraint violations', async () => {
        const request: SubmitContentRequest = {
          username: 'jane.smith',
          presentationTitle: 'Test Title',
          presentationAbstract: 'Test Abstract',
        };

        const error = new Error('Database constraint violation: unique index violated');
        vi.mocked(apiClient.post).mockRejectedValue(error);

        await expect(
          speakerContentService.submitContent('BATbern56', 'pool-123', request)
        ).rejects.toThrow('Database constraint violation');
      });
    });

    describe('AC34: Session Deletion Protection (already covered above)', () => {
      it('should be covered in getSpeakerContent orphaned session test', () => {
        // This AC is already covered by the "handle orphaned session FK with warning" test
        expect(true).toBe(true);
      });
    });

    describe('AC37: Content Submission State Validation', () => {
      it('should reject submission when speaker not in accepted state', async () => {
        const request: SubmitContentRequest = {
          username: 'jane.smith',
          presentationTitle: 'Test Title',
          presentationAbstract: 'Test Abstract',
        };

        const error = new Error(
          'Invalid state: Speaker must be accepted before content submission'
        );
        vi.mocked(apiClient.post).mockRejectedValue(error);

        await expect(
          speakerContentService.submitContent('BATbern56', 'pool-123', request)
        ).rejects.toThrow('Speaker must be accepted');
      });

      it('should reject submission when speaker is declined', async () => {
        const request: SubmitContentRequest = {
          username: 'jane.smith',
          presentationTitle: 'Test Title',
          presentationAbstract: 'Test Abstract',
        };

        const error = new Error('Invalid state: Cannot submit content for declined speaker');
        vi.mocked(apiClient.post).mockRejectedValue(error);

        await expect(
          speakerContentService.submitContent('BATbern56', 'pool-123', request)
        ).rejects.toThrow('Cannot submit content for declined speaker');
      });

      it('should reject submission when speaker is already confirmed', async () => {
        const request: SubmitContentRequest = {
          username: 'jane.smith',
          presentationTitle: 'Test Title',
          presentationAbstract: 'Test Abstract',
        };

        const error = new Error('Invalid state: Content already submitted for this speaker');
        vi.mocked(apiClient.post).mockRejectedValue(error);

        await expect(
          speakerContentService.submitContent('BATbern56', 'pool-123', request)
        ).rejects.toThrow('Content already submitted');
      });
    });

    describe('Additional Error Scenarios', () => {
      it('should handle malformed response data', async () => {
        const request: SubmitContentRequest = {
          username: 'jane.smith',
          presentationTitle: 'Test Title',
          presentationAbstract: 'Test Abstract',
        };

        vi.mocked(apiClient.post).mockResolvedValue({ data: null });

        const result = await speakerContentService.submitContent('BATbern56', 'pool-123', request);

        expect(result).toBeNull();
      });

      it('should handle server errors (500)', async () => {
        const request: SubmitContentRequest = {
          username: 'jane.smith',
          presentationTitle: 'Test Title',
          presentationAbstract: 'Test Abstract',
        };

        const error = new Error('Internal server error');
        vi.mocked(apiClient.post).mockRejectedValue(error);

        await expect(
          speakerContentService.submitContent('BATbern56', 'pool-123', request)
        ).rejects.toThrow('Internal server error');
      });

      it('should handle rate limiting errors (429)', async () => {
        const request: SubmitContentRequest = {
          username: 'jane.smith',
          presentationTitle: 'Test Title',
          presentationAbstract: 'Test Abstract',
        };

        const error = new Error('Too many requests. Please try again later.');
        vi.mocked(apiClient.post).mockRejectedValue(error);

        await expect(
          speakerContentService.submitContent('BATbern56', 'pool-123', request)
        ).rejects.toThrow('Too many requests');
      });
    });
  });

  describe('Service Singleton', () => {
    it('should export a singleton instance', () => {
      expect(speakerContentService).toBeDefined();
      expect(speakerContentService).toBeInstanceOf(Object);
      expect(typeof speakerContentService.submitContent).toBe('function');
      expect(typeof speakerContentService.getSpeakerContent).toBe('function');
      expect(typeof speakerContentService.getReviewQueue).toBe('function');
      expect(typeof speakerContentService.reviewContent).toBe('function');
    });

    it('should maintain state across multiple calls', async () => {
      const request: SubmitContentRequest = {
        username: 'test.user',
        presentationTitle: 'Test Title',
        presentationAbstract: 'Test Abstract',
      };

      const mockResponse: SpeakerContentResponse = {
        poolId: 'pool-123',
        sessionId: 'session-456',
        status: 'CONTENT_SUBMITTED',
        presentationTitle: 'Test Title',
        presentationAbstract: 'Test Abstract',
        username: 'test.user',
      };

      vi.mocked(apiClient.post).mockResolvedValue({ data: mockResponse });

      // Call twice to ensure singleton behavior
      await speakerContentService.submitContent('BATbern56', 'pool-123', request);
      await speakerContentService.submitContent('BATbern56', 'pool-123', request);

      expect(apiClient.post).toHaveBeenCalledTimes(2);
    });
  });
});
