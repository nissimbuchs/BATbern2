/**
 * Material-UI Theme Configuration
 * Story 1.17: Swiss Design Standards
 *
 * Swiss design principles:
 * - Clean, minimalist aesthetic
 * - Grid-based layouts with precise alignment
 * - Sans-serif typography (Helvetica/Arial)
 * - Limited color palette with high contrast
 * - Emphasis on readability and hierarchy
 * - Functional, objective design
 */

import { createTheme, ThemeOptions } from '@mui/material/styles';

// Swiss Design Color Palette
const swissColors = {
  // Primary: Swiss red (based on Swiss flag)
  primary: {
    main: '#FF0000',
    light: '#FF3333',
    dark: '#CC0000',
    contrastText: '#FFFFFF',
  },
  // Secondary: Neutral gray for balance
  secondary: {
    main: '#2C2C2C',
    light: '#4A4A4A',
    dark: '#1A1A1A',
    contrastText: '#FFFFFF',
  },
  // Error states
  error: {
    main: '#D32F2F',
    light: '#EF5350',
    dark: '#C62828',
    contrastText: '#FFFFFF',
  },
  // Warning states
  warning: {
    main: '#ED6C02',
    light: '#FF9800',
    dark: '#E65100',
    contrastText: '#FFFFFF',
  },
  // Info states
  info: {
    main: '#0288D1',
    light: '#03A9F4',
    dark: '#01579B',
    contrastText: '#FFFFFF',
  },
  // Success states
  success: {
    main: '#2E7D32',
    light: '#4CAF50',
    dark: '#1B5E20',
    contrastText: '#FFFFFF',
  },
  // Grayscale for text and backgrounds
  grey: {
    50: '#FAFAFA',
    100: '#F5F5F5',
    200: '#EEEEEE',
    300: '#E0E0E0',
    400: '#BDBDBD',
    500: '#9E9E9E',
    600: '#757575',
    700: '#616161',
    800: '#424242',
    900: '#212121',
  },
  // Background colors
  background: {
    default: '#FFFFFF',
    paper: '#FAFAFA',
  },
  // Text colors
  text: {
    primary: '#212121',
    secondary: '#616161',
    disabled: '#9E9E9E',
  },
};

// Swiss Typography System
const swissTypography = {
  fontFamily: ['Helvetica Neue', 'Helvetica', 'Arial', 'sans-serif'].join(','),
  fontSize: 16,
  fontWeightLight: 300,
  fontWeightRegular: 400,
  fontWeightMedium: 500,
  fontWeightBold: 700,
  // Heading styles with Swiss hierarchy
  h1: {
    fontSize: '2.5rem', // 40px
    fontWeight: 700,
    lineHeight: 1.2,
    letterSpacing: '-0.01562em',
  },
  h2: {
    fontSize: '2rem', // 32px
    fontWeight: 700,
    lineHeight: 1.25,
    letterSpacing: '-0.00833em',
  },
  h3: {
    fontSize: '1.75rem', // 28px
    fontWeight: 600,
    lineHeight: 1.3,
    letterSpacing: '0em',
  },
  h4: {
    fontSize: '1.5rem', // 24px
    fontWeight: 600,
    lineHeight: 1.35,
    letterSpacing: '0.00735em',
  },
  h5: {
    fontSize: '1.25rem', // 20px
    fontWeight: 500,
    lineHeight: 1.4,
    letterSpacing: '0em',
  },
  h6: {
    fontSize: '1rem', // 16px
    fontWeight: 500,
    lineHeight: 1.5,
    letterSpacing: '0.0075em',
  },
  // Body text
  body1: {
    fontSize: '1rem', // 16px
    fontWeight: 400,
    lineHeight: 1.5,
    letterSpacing: '0.00938em',
  },
  body2: {
    fontSize: '0.875rem', // 14px
    fontWeight: 400,
    lineHeight: 1.43,
    letterSpacing: '0.01071em',
  },
  // Button text
  button: {
    fontSize: '0.875rem', // 14px
    fontWeight: 500,
    lineHeight: 1.75,
    letterSpacing: '0.02857em',
    textTransform: 'uppercase' as const,
  },
  // Captions and overline
  caption: {
    fontSize: '0.75rem', // 12px
    fontWeight: 400,
    lineHeight: 1.66,
    letterSpacing: '0.03333em',
  },
  overline: {
    fontSize: '0.75rem', // 12px
    fontWeight: 500,
    lineHeight: 2.66,
    letterSpacing: '0.08333em',
    textTransform: 'uppercase' as const,
  },
};

// Component style overrides
const swissComponents = {
  MuiButton: {
    styleOverrides: {
      root: {
        borderRadius: 4,
        padding: '8px 16px',
        fontWeight: 500,
        textTransform: 'uppercase' as const,
        boxShadow: 'none',
        '&:hover': {
          boxShadow: 'none',
        },
      },
      contained: {
        '&:hover': {
          boxShadow: '0px 2px 4px rgba(0, 0, 0, 0.1)',
        },
      },
    },
  },
  MuiCard: {
    styleOverrides: {
      root: {
        borderRadius: 8,
        boxShadow: '0px 2px 8px rgba(0, 0, 0, 0.08)',
      },
    },
  },
  MuiTextField: {
    styleOverrides: {
      root: {
        '& .MuiOutlinedInput-root': {
          borderRadius: 4,
        },
      },
    },
  },
  MuiAppBar: {
    styleOverrides: {
      root: {
        boxShadow: '0px 1px 4px rgba(0, 0, 0, 0.1)',
      },
    },
  },
};

// Spacing system (8px base grid)
const spacing = 8;

// Breakpoints (standard Material-UI breakpoints)
const breakpoints = {
  values: {
    xs: 0,
    sm: 600,
    md: 960,
    lg: 1280,
    xl: 1920,
  },
};

// Shape configuration
const shape = {
  borderRadius: 4,
};

// Create the theme
const themeOptions: ThemeOptions = {
  palette: {
    primary: swissColors.primary,
    secondary: swissColors.secondary,
    error: swissColors.error,
    warning: swissColors.warning,
    info: swissColors.info,
    success: swissColors.success,
    grey: swissColors.grey,
    background: swissColors.background,
    text: swissColors.text,
  },
  typography: swissTypography,
  spacing,
  breakpoints,
  shape,
  components: swissComponents,
};

export const theme = createTheme(themeOptions);

// Export theme for use in application
export default theme;
