/**
 * aiPromptService on the MSW harness (Story 4.10b, Epic 17 test fidelity): the real apiClient
 * runs and every request is checked against event-ai-api. The reset is the delete of the
 * override, `DELETE /ai-prompts/{promptKey}/override` (was `POST …/reset`).
 */

import { describe, it, expect } from 'vitest';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import {
  listAiPrompts,
  getAiPrompt,
  updateAiPrompt,
  deleteAiPromptOverride,
  type AiPromptResponse,
} from './aiPromptService';

const prompt = (overrides: Partial<AiPromptResponse> = {}): AiPromptResponse => ({
  promptKey: 'event_description',
  displayName: 'Event Description',
  promptText: 'Schreibe über Brünnimann’s Vortrag',
  defaultText: 'Write a German event description',
  updatedAt: '2026-11-10T08:00:00Z',
  ...overrides,
});

describe('aiPromptService', () => {
  it('should_listPrompts', async () => {
    api.on('listAiPrompts', () => [prompt(), prompt({ promptKey: 'theme_image' })]);

    const prompts = await listAiPrompts();

    expect(prompts.map((p) => p.promptKey)).toEqual(['event_description', 'theme_image']);
  });

  it('should_getOnePromptByKey', async () => {
    api.on('getAiPrompt', ({ params }) => prompt({ promptKey: params.promptKey }));

    await expect(getAiPrompt('abstract_quality')).resolves.toMatchObject({
      promptKey: 'abstract_quality',
    });
  });

  it('should_putThePromptText_when_updating', async () => {
    api.on('updateAiPrompt', ({ body }) => prompt({ promptText: body?.promptText }));

    const updated = await updateAiPrompt('event_description', 'Neuer Text für O’Neill');

    expect(api.calls('updateAiPrompt')[0].body).toEqual({ promptText: 'Neuer Text für O’Neill' });
    expect(updated.promptText).toBe('Neuer Text für O’Neill');
  });

  it('should_deleteTheOverride_when_resettingToDefault', async () => {
    api.on('deleteAiPromptOverride', () =>
      prompt({ promptText: 'Write a German event description' })
    );

    const reset = await deleteAiPromptOverride('event_description');

    const [call] = api.calls('deleteAiPromptOverride');
    expect(call.method).toBe('DELETE');
    expect(call.params.promptKey).toBe('event_description');
    expect(reset.promptText).toBe(reset.defaultText);
  });

  it('should_rejectWithTheHttpError_when_promptIsUnknown', async () => {
    api.on('deleteAiPromptOverride', () =>
      HttpResponse.json({ message: 'not found' }, { status: 404 })
    );

    await expect(deleteAiPromptOverride('no_such_prompt')).rejects.toMatchObject({
      response: { status: 404 },
    });
  });
});
