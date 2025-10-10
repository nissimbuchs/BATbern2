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
  });
});
