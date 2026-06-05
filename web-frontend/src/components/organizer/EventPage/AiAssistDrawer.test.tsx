import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render } from '@testing-library/react';
import { ThemeProvider } from '@mui/material/styles';
import { theme } from '@/theme/theme';
import { AiAssistDrawer } from './AiAssistDrawer';

// Mock the AI hooks so the component renders without network
vi.mock('@/hooks/useAiAssist', () => ({
  useAiGenerateDescription: () => ({ mutate: vi.fn(), isPending: false }),
  useAiGenerateThemeImage: () => ({ mutate: vi.fn(), isPending: false }),
  useAiApplyThemeImage: () => ({ mutate: vi.fn(), isPending: false }),
}));

const renderDrawer = () =>
  render(
    <ThemeProvider theme={theme}>
      <AiAssistDrawer
        eventCode="BATbern99"
        open={true}
        onClose={vi.fn()}
        onDescriptionGenerated={vi.fn()}
        onImageGenerated={vi.fn()}
      />
    </ThemeProvider>
  );

/**
 * Concatenate the text of every emotion <style> element that mentions the given
 * class. MUI compiles a responsive sx ({ xs: '100%', sm: 480 }) into per-breakpoint
 * `@media (min-width:…)` rules — the xs value lands behind `@media (min-width:0px)`
 * and the sm+ value behind `@media (min-width:600px)`. jsdom never evaluates those
 * media queries, so inspecting the injected stylesheet is the reliable assertion.
 */
const cssForClass = (className: string): string => {
  let combined = '';
  document.querySelectorAll('style').forEach((styleEl) => {
    const css = styleEl.textContent ?? '';
    if (css.includes(`.${className}`)) combined += css + '\n';
  });
  return combined;
};

const paperEmotionClass = (paper: HTMLElement): string => {
  const cssClass = Array.from(paper.classList).find((c) => c.startsWith('css-'));
  expect(cssClass).toBeTruthy();
  return cssClass!;
};

describe('AiAssistDrawer responsive width', () => {
  const originalMatchMedia = window.matchMedia;

  beforeEach(() => {
    // Deterministic matchMedia mock — no media query matches (jsdom never applies
    // @media min-width rules to computed style, so we inspect the stylesheet directly).
    window.matchMedia = vi.fn((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })) as unknown as typeof window.matchMedia;
  });

  afterEach(() => {
    window.matchMedia = originalMatchMedia;
  });

  it('should_useFullWidthXsBaseRule_when_xsViewport', () => {
    renderDrawer();

    const paper = document.querySelector('.MuiDrawer-paper') as HTMLElement;
    expect(paper).toBeTruthy();
    const css = cssForClass(paperEmotionClass(paper));

    // xs base = width:100% behind @media (min-width:0px). A fixed-480 revert would
    // emit `width:480px` as an un-media-queried rule and NOT this min-width:0px rule.
    const xsRule = new RegExp(
      `@media\\s*\\(min-width:\\s*0px\\)\\s*\\{[^}]*width:\\s*100%[^}]*\\}`
    );
    expect(xsRule.test(css)).toBe(true);

    // Guard against a fixed pixel width applied as a non-media (base) rule:
    // strip all @media blocks, then assert the remaining base CSS has no fixed width.
    const baseOnly = css.replace(/@media[^{]+\{[\s\S]*?\}\s*\}/g, '');
    expect(/width:\s*480px/.test(baseOnly)).toBe(false);
    // A fixed-480 revert would also drop the min-width:0px → 100% rule entirely.
    expect(/width:\s*100%/.test(baseOnly)).toBe(false);
  });

  it('should_set480pxBehindMinWidth600MediaQuery_when_smViewport', () => {
    renderDrawer();

    const paper = document.querySelector('.MuiDrawer-paper') as HTMLElement;
    const css = cssForClass(paperEmotionClass(paper));

    // sm+ override (480px) lives behind @media (min-width:600px).
    const smRule = new RegExp(
      `@media\\s*\\(min-width:\\s*600px\\)\\s*\\{[^}]*width:\\s*480px[^}]*\\}`
    );
    expect(smRule.test(css)).toBe(true);
  });

  it('should_renderDrawerContent_when_open', () => {
    renderDrawer();
    expect(document.querySelector('.MuiDrawer-paper')).toBeTruthy();
  });
});
