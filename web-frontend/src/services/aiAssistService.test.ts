/**
 * AI Assist Service Tests
 *
 * HTTP is stubbed at the boundary with axios-mock-adapter on apiClient.
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import apiClient from '@/services/api/apiClient';
import {
  analyzeAbstract,
  applyThemeImage,
  generateEventDescription,
  getThemeImageJob,
  startThemeImageJob,
} from './aiAssistService';

describe('aiAssistService', () => {
  let mock: MockAdapter;

  beforeEach(() => {
    mock = new MockAdapter(apiClient);
  });

  afterEach(() => {
    mock.restore();
  });

  it('should_postDescription_when_generateEventDescription', async () => {
    mock.onPost('/events/BATbern60/ai/description').reply(200, { description: 'Text' });

    await expect(generateEventDescription('BATbern60')).resolves.toEqual({ description: 'Text' });
    expect(mock.history.post).toHaveLength(1);
    expect(mock.history.post[0].data).toBeUndefined();
  });

  it('should_passErrorThrough_when_descriptionFails', async () => {
    mock.onPost('/events/BATbern60/ai/description').reply(503);

    await expect(generateEventDescription('BATbern60')).rejects.toMatchObject({
      response: { status: 503 },
    });
  });

  it('should_postWithoutQuery_when_startThemeImageJobWithoutSeed', async () => {
    mock.onPost('/events/BATbern60/ai/theme-image').reply(202, { jobId: 'j1', status: 'PENDING' });

    await expect(startThemeImageJob('BATbern60')).resolves.toEqual({
      jobId: 'j1',
      status: 'PENDING',
    });
    expect(mock.history.post[0].url).toBe('/events/BATbern60/ai/theme-image');
  });

  it('should_encodeSeedInQuery_when_startThemeImageJobWithSeed', async () => {
    mock
      .onPost('/events/BATbern60/ai/theme-image?seed=wood%20%26%20glass')
      .reply(202, { jobId: 'j2', status: 'PENDING' });

    await expect(startThemeImageJob('BATbern60', 'wood & glass')).resolves.toMatchObject({
      jobId: 'j2',
    });
    expect(mock.history.post[0].url).toBe(
      '/events/BATbern60/ai/theme-image?seed=wood%20%26%20glass'
    );
  });

  it('should_getJob_when_getThemeImageJob', async () => {
    const job = {
      jobId: 'j1',
      status: 'DONE',
      imageUrl: 'https://cdn.example.com/i.png',
      s3Key: 'k',
    };
    mock.onGet('/events/BATbern60/ai/theme-image/jobs/j1').reply(200, job);

    await expect(getThemeImageJob('BATbern60', 'j1')).resolves.toEqual(job);
  });

  it('should_postImageUrl_when_applyThemeImage', async () => {
    mock.onPost('/events/BATbern60/ai/theme-image/apply').reply(200);

    await applyThemeImage('BATbern60', 'https://cdn.example.com/i.png');

    expect(JSON.parse(mock.history.post[0].data)).toEqual({
      imageUrl: 'https://cdn.example.com/i.png',
    });
  });

  it('should_postAnalyzeAbstract_when_analyzeAbstract', async () => {
    const analysis = {
      noPromotionScore: 90,
      noPromotionFeedback: 'Good',
      lessonsLearnedScore: 80,
      lessonsLearnedFeedback: 'Clear',
      wordCount: 120,
      shortenedAbstract: null,
    };
    mock.onPost('/speakers/sp-1/ai/analyze-abstract').reply(200, analysis);

    await expect(analyzeAbstract('sp-1')).resolves.toEqual(analysis);
  });
});
