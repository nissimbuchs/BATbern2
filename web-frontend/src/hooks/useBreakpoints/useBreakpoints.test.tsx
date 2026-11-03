/**
 * useBreakpoints Hook Tests
 * Story 1.17, Task 11b: Test responsive breakpoint detection hook
 */

import { describe, it, expect } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useBreakpoints } from './useBreakpoints';
import { ThemeProvider, createTheme } from '@mui/material';
import React from 'react';

describe('useBreakpoints', () => {
  const theme = createTheme();

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <ThemeProvider theme={theme}>{children}</ThemeProvider>
  );

  it('should_returnBreakpointObject_when_called', () => {
    const { result } = renderHook(() => useBreakpoints(), { wrapper });

    expect(result.current).toHaveProperty('isMobile');
    expect(result.current).toHaveProperty('isTablet');
    expect(result.current).toHaveProperty('isDesktop');
    expect(result.current).toHaveProperty('isWideDesktop');
  });

  // #1198: the labelled top navigation needs about 1395 px for an organizer.
  it('should_reportWideDesktop_onlyFrom1440px', () => {
    const at = (width: number) => {
      window.matchMedia = ((query: string) => {
        const min = /min-width:\s*([\d.]+)px/.exec(query);
        const max = /max-width:\s*([\d.]+)px/.exec(query);
        const matches = (!min || width >= Number(min[1])) && (!max || width <= Number(max[1]));
        return {
          matches,
          media: query,
          onchange: null,
          addListener: () => {},
          removeListener: () => {},
          addEventListener: () => {},
          removeEventListener: () => {},
          dispatchEvent: () => false,
        };
      }) as typeof window.matchMedia;
      return renderHook(() => useBreakpoints(), { wrapper }).result.current;
    };
    const original = window.matchMedia;
    try {
      expect(at(1280)).toMatchObject({ isDesktop: true, isWideDesktop: false });
      expect(at(1439)).toMatchObject({ isWideDesktop: false });
      expect(at(1440)).toMatchObject({ isDesktop: true, isWideDesktop: true });
    } finally {
      window.matchMedia = original;
    }
  });
});
