/**
 * emailTemplateService Tests (Story 10.2 — Code Review fix)
 *
 * Tests all HTTP methods in EmailTemplateService:
 * - listTemplates (with and without filters)
 * - getTemplate
 * - createTemplate
 * - updateTemplate
 * - deleteTemplate
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { emailTemplateService } from './emailTemplateService';
import apiClient from './api/apiClient';

vi.mock('./api/apiClient', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

const mockTemplate = {
  templateKey: 'speaker-invitation',
  locale: 'de',
  category: 'SPEAKER',
  subject: 'Einladung',
  htmlBody: '<p>Hello</p>',
  variables: null,
  isLayout: false,
  layoutKey: 'batbern-default',
  isSystemTemplate: true,
  updatedAt: '2026-02-24T10:00:00Z',
};

describe('emailTemplateService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('listTemplates', () => {
    it('calls GET /email-templates without params', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: [mockTemplate] });

      const result = await emailTemplateService.listTemplates();

      expect(apiClient.get).toHaveBeenCalledWith('/email-templates', { params: undefined });
      expect(result).toHaveLength(1);
      expect(result[0].templateKey).toBe('speaker-invitation');
    });

    it('passes isLayout filter to API', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: [] });

      await emailTemplateService.listTemplates({ isLayout: true });

      expect(apiClient.get).toHaveBeenCalledWith('/email-templates', {
        params: { isLayout: true },
      });
    });

    it('passes category filter to API', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: [] });

      await emailTemplateService.listTemplates({ category: 'SPEAKER' });

      expect(apiClient.get).toHaveBeenCalledWith('/email-templates', {
        params: { category: 'SPEAKER' },
      });
    });
  });

  describe('getTemplate', () => {
    it('calls GET /email-templates/{key}/{locale}', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: mockTemplate });

      const result = await emailTemplateService.getTemplate('speaker-invitation', 'de');

      expect(apiClient.get).toHaveBeenCalledWith('/email-templates/speaker-invitation/de');
      expect(result.templateKey).toBe('speaker-invitation');
    });

    it('URL-encodes template key with slashes', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: mockTemplate });

      await emailTemplateService.getTemplate('batbern-default', 'en');

      expect(apiClient.get).toHaveBeenCalledWith('/email-templates/batbern-default/en');
    });
  });

  describe('createTemplate', () => {
    it('calls POST /email-templates with request body', async () => {
      vi.mocked(apiClient.post).mockResolvedValue({
        data: { ...mockTemplate, isSystemTemplate: false },
      });

      const req = {
        templateKey: 'my-custom',
        locale: 'de' as const,
        category: 'SPEAKER' as const,
        subject: 'Betreff',
        htmlBody: '<p>body</p>',
        isLayout: false,
        layoutKey: 'batbern-default',
      };

      const result = await emailTemplateService.createTemplate(req);

      expect(apiClient.post).toHaveBeenCalledWith('/email-templates', req);
      expect(result.isSystemTemplate).toBe(false);
    });
  });

  describe('updateTemplate', () => {
    it('calls PUT /email-templates/{key}/{locale}', async () => {
      const updated = { ...mockTemplate, subject: 'New Subject' };
      vi.mocked(apiClient.put).mockResolvedValue({ data: updated });

      const result = await emailTemplateService.updateTemplate('speaker-invitation', 'de', {
        subject: 'New Subject',
      });

      expect(apiClient.put).toHaveBeenCalledWith('/email-templates/speaker-invitation/de', {
        subject: 'New Subject',
      });
      expect(result.subject).toBe('New Subject');
    });
  });

  describe('deleteTemplate', () => {
    it('calls DELETE /email-templates/{key}/{locale}', async () => {
      vi.mocked(apiClient.delete).mockResolvedValue({ data: undefined });

      await emailTemplateService.deleteTemplate('my-custom', 'de');

      expect(apiClient.delete).toHaveBeenCalledWith('/email-templates/my-custom/de');
    });
  });
});
