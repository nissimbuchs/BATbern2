import apiClient from '@/services/api/apiClient';
import type { components } from '@/types/generated/partner-notes-api.types';

export type PartnerNoteDTO = components['schemas']['PartnerNoteDTO'];
export type CreateNoteRequest = components['schemas']['CreateNoteRequest'];
export type UpdateNoteRequest = components['schemas']['UpdateNoteRequest'];

export const getPartnerNotes = async (companyName: string): Promise<PartnerNoteDTO[]> => {
  const response = await apiClient.get<PartnerNoteDTO[]>(`/partners/${companyName}/notes`);
  return response.data;
};

export const createPartnerNote = async (
  companyName: string,
  req: CreateNoteRequest
): Promise<PartnerNoteDTO> => {
  const response = await apiClient.post<PartnerNoteDTO>(`/partners/${companyName}/notes`, req);
  return response.data;
};

export const updatePartnerNote = async (
  companyName: string,
  noteId: string,
  req: UpdateNoteRequest
): Promise<PartnerNoteDTO> => {
  const response = await apiClient.patch<PartnerNoteDTO>(
    `/partners/${companyName}/notes/${noteId}`,
    req
  );
  return response.data;
};

export const deletePartnerNote = async (companyName: string, noteId: string): Promise<void> => {
  await apiClient.delete(`/partners/${companyName}/notes/${noteId}`);
};
