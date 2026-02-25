/**
 * Email Template Service (Story 10.2)
 *
 * HTTP client for Email Template Management API (event-management-service)
 * Features:
 * - JWT authentication via interceptors
 * - Generated types from OpenAPI spec (ADR-006)
 * - ORGANIZER-only operations (RBAC)
 */

import apiClient from '@/services/api/apiClient';
import type { components } from '@/types/generated/events-api.types';

type EmailTemplateResponse = components['schemas']['EmailTemplateResponse'];
type CreateEmailTemplateRequest = components['schemas']['CreateEmailTemplateRequest'];
type UpdateEmailTemplateRequest = components['schemas']['UpdateEmailTemplateRequest'];

const BASE_PATH = '/email-templates';

export interface ListEmailTemplatesParams {
  category?: string;
  isLayout?: boolean;
}

class EmailTemplateService {
  async listTemplates(params?: ListEmailTemplatesParams): Promise<EmailTemplateResponse[]> {
    const response = await apiClient.get<EmailTemplateResponse[]>(BASE_PATH, { params });
    return response.data;
  }

  async getTemplate(templateKey: string, locale: string): Promise<EmailTemplateResponse> {
    const response = await apiClient.get<EmailTemplateResponse>(
      `${BASE_PATH}/${encodeURIComponent(templateKey)}/${encodeURIComponent(locale)}`
    );
    return response.data;
  }

  async createTemplate(req: CreateEmailTemplateRequest): Promise<EmailTemplateResponse> {
    const response = await apiClient.post<EmailTemplateResponse>(BASE_PATH, req);
    return response.data;
  }

  async updateTemplate(
    templateKey: string,
    locale: string,
    req: UpdateEmailTemplateRequest
  ): Promise<EmailTemplateResponse> {
    const response = await apiClient.put<EmailTemplateResponse>(
      `${BASE_PATH}/${encodeURIComponent(templateKey)}/${encodeURIComponent(locale)}`,
      req
    );
    return response.data;
  }

  async deleteTemplate(templateKey: string, locale: string): Promise<void> {
    await apiClient.delete(
      `${BASE_PATH}/${encodeURIComponent(templateKey)}/${encodeURIComponent(locale)}`
    );
  }
}

export const emailTemplateService = new EmailTemplateService();
export type { EmailTemplateResponse, CreateEmailTemplateRequest, UpdateEmailTemplateRequest };
