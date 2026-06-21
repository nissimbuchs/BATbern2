import { readFileSync } from 'fs';
import { resolve } from 'path';
import { describe, it, expect } from 'vitest';

/**
 * Story 15.9 — OWASP-LLM LLM02 (insecure output handling), frontend guard.
 *
 * AI-generated text (event descriptions, abstract feedback) is untrusted. React auto-escapes text
 * children, so the LLM02 mitigation on the client is simply: **never render AI output via
 * `dangerouslySetInnerHTML`**. This test fails if any AI-output render site reintroduces it.
 *
 * If a site legitimately needs HTML rendering, sanitize first (e.g. DOMPurify) and remove it from
 * this list with a comment — do not silently delete the guard.
 */
const AI_OUTPUT_RENDER_SITES = [
  'src/components/organizer/EventPage/AiAssistDrawer.tsx',
  'src/components/organizer/EventPage/EventInfoTab.tsx',
  'src/components/organizer/SpeakerDrawer/QualityReviewSubView.tsx',
  'src/components/public/EventCard.tsx',
  'src/components/public/Hero/HeroSection.tsx',
];

describe('AI output rendering — LLM02 escaping guard (Story 15.9)', () => {
  it.each(AI_OUTPUT_RENDER_SITES)('does not use dangerouslySetInnerHTML in %s', (relativePath) => {
    const source = readFileSync(resolve(process.cwd(), relativePath), 'utf-8');
    expect(source).not.toContain('dangerouslySetInnerHTML');
  });
});
