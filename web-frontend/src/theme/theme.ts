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

// Swiss Design Color Palette (Front-End Spec Section 6.2)
const swissColors = {
  // Primary: Professional blue (Navigation, CTAs, organizer role)
  primary: {
    main: '#2C5F7C', // rgb(44, 95, 124) - AA contrast on white
    light: '#4A90B8', // rgb(74, 144, 184) - Secondary blue, speaker role
    dark: '#1A3A4D', // rgb(26, 58, 77) - Hover states, AAA contrast on white
    contrastText: '#FFFFFF',
  },
  // Secondary: Professional blue for highlights and secondary actions
  secondary: {
    main: '#4A90B8', // rgb(74, 144, 184) - Speaker role indicator
    light: '#6BAED6',
    dark: '#2C5F7C',
    contrastText: '#FFFFFF',
  },
  // Accent: Partner role indicator and important highlights
  accent: {
    main: '#E67E22', // rgb(230, 126, 34) - Partner role, AA contrast on white
    light: '#F39C12',
    dark: '#D35400',
    contrastText: '#FFFFFF',
  },
  // Error states
  error: {
    main: '#E74C3C', // rgb(231, 76, 60) - Front-End Spec
    light: '#EF5350',
    dark: '#C62828',
    contrastText: '#FFFFFF',
  },
  // Warning states
  warning: {
    main: '#F39C12', // rgb(243, 156, 18) - Front-End Spec
    light: '#FFA726',
    dark: '#E65100',
    contrastText: '#000000',
  },
  // Info states (Attendee role indicator)
  info: {
    main: '#3498DB', // rgb(52, 152, 219) - Front-End Spec, attendee role
    light: '#5DADE2',
    dark: '#2874A6',
    contrastText: '#FFFFFF',
  },
  // Success states
  success: {
    main: '#27AE60', // rgb(39, 174, 96) - Front-End Spec
    light: '#4CAF50',
    dark: '#1B5E20',
    contrastText: '#FFFFFF',
  },
  // Grayscale for text and backgrounds (Front-End Spec Neutral colors)
  grey: {
    50: '#FAFAFA',
    100: '#ECF0F1', // Neutral 100 from spec
    200: '#EEEEEE',
    300: '#BDC3C7', // Neutral 300 from spec
    400: '#BDBDBD',
    500: '#95A5A6', // Neutral 500 from spec
    600: '#757575',
    700: '#7F8C8D', // Neutral 700 from spec
    800: '#424242',
    900: '#34495E', // Neutral 900 from spec - primary text
  },
  // Background colors
  background: {
    default: '#FFFFFF',
    paper: '#FAFAFA',
  },
  // Text colors (Front-End Spec)
  text: {
    primary: '#34495E', // Neutral 900 - AAA contrast on white
    secondary: '#7F8C8D', // Neutral 700 - AA contrast on white
    disabled: '#95A5A6', // Neutral 500
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

// Component style overrides with WCAG 2.1 AA accessibility
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
        // WCAG 2.1 AA Focus Indicator (2.4.7)
        '&:focus-visible': {
          outline: '3px solid #0288D1',
          outlineOffset: '2px',
        },
      },
      contained: {
        '&:hover': {
          boxShadow: '0px 2px 4px rgba(0, 0, 0, 0.1)',
        },
      },
    },
  },
  MuiIconButton: {
    styleOverrides: {
      root: {
        // WCAG 2.1 AA Focus Indicator for icon buttons
        '&:focus-visible': {
          outline: '3px solid #0288D1',
          outlineOffset: '2px',
          backgroundColor: 'rgba(2, 136, 209, 0.08)',
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
          // Focus indicator for form inputs
          '&.Mui-focused fieldset': {
            borderWidth: '2px',
            borderColor: '#0288D1',
          },
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
  MuiLink: {
    styleOverrides: {
      root: {
        // WCAG 2.1 AA Focus Indicator for links
        '&:focus-visible': {
          outline: '3px solid #0288D1',
          outlineOffset: '2px',
          textDecoration: 'underline',
        },
      },
    },
  },
  MuiTab: {
    styleOverrides: {
      root: {
        // Focus indicator for navigation tabs
        '&:focus-visible': {
          outline: '3px solid #0288D1',
          outlineOffset: '2px',
        },
      },
    },
  },
  MuiMenuItem: {
    styleOverrides: {
      root: {
        // Focus indicator for menu items
        '&:focus-visible': {
          outline: '2px solid #0288D1',
          outlineOffset: '-2px',
          backgroundColor: 'rgba(2, 136, 209, 0.08)',
        },
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
    // @ts-expect-error - Custom accent color for partner role
    accent: swissColors.accent,
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
