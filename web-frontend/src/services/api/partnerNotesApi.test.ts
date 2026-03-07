/**
 * Partner Notes API Tests
 *
 * Tests for CRUD operations on partner notes.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  getPartnerNotes,
  createPartnerNote,
  updatePartnerNote,
  deletePartnerNote,
} from './partnerNotesApi';
import apiClient from './apiClient';

vi.mock('./apiClient', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}));

const mockApiClient = vi.mocked(apiClient);

const COMPANY = 'Acme Corp';

describe('partnerNotesApi', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('getPartnerNotes', () => {
    it('should GET notes for a company', async () => {
      const mockNotes = [{ id: 'note-1', content: 'First note', companyName: COMPANY }];
      mockApiClient.get.mockResolvedValue({ data: mockNotes });

      const result = await getPartnerNotes(COMPANY);

      expect(mockApiClient.get).toHaveBeenCalledWith(`/partners/${COMPANY}/notes`);
      expect(result).toEqual(mockNotes);
    });

    it('should propagate errors', async () => {
      mockApiClient.get.mockRejectedValue(new Error('Not found'));

      await expect(getPartnerNotes(COMPANY)).rejects.toThrow('Not found');
    });
  });

  describe('createPartnerNote', () => {
    it('should POST new note and return created note', async () => {
      const mockNote = { id: 'note-2', content: 'New note', companyName: COMPANY };
      mockApiClient.post.mockResolvedValue({ data: mockNote });

      const result = await createPartnerNote(COMPANY, { content: 'New note' });

      expect(mockApiClient.post).toHaveBeenCalledWith(`/partners/${COMPANY}/notes`, {
        content: 'New note',
      });
      expect(result.id).toBe('note-2');
    });
  });

  describe('updatePartnerNote', () => {
    it('should PATCH note and return updated note', async () => {
      const mockNote = { id: 'note-1', content: 'Updated note', companyName: COMPANY };
      mockApiClient.patch.mockResolvedValue({ data: mockNote });

      const result = await updatePartnerNote(COMPANY, 'note-1', { content: 'Updated note' });

      expect(mockApiClient.patch).toHaveBeenCalledWith(`/partners/${COMPANY}/notes/note-1`, {
        content: 'Updated note',
      });
      expect(result.content).toBe('Updated note');
    });
  });

  describe('deletePartnerNote', () => {
    it('should DELETE note', async () => {
      mockApiClient.delete.mockResolvedValue({ data: undefined });

      await deletePartnerNote(COMPANY, 'note-1');

      expect(mockApiClient.delete).toHaveBeenCalledWith(`/partners/${COMPANY}/notes/note-1`);
    });

    it('should propagate deletion errors', async () => {
      mockApiClient.delete.mockRejectedValue(new Error('Delete failed'));

      await expect(deletePartnerNote(COMPANY, 'note-1')).rejects.toThrow('Delete failed');
    });
  });
});
