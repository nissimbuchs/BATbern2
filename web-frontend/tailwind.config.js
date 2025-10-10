/**
 * Tailwind CSS Configuration
 * Story 1.17: Utility-first styling with Swiss design principles
 */

/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      // Swiss Design Color Palette (Front-End Spec Section 6.2)
      colors: {
        primary: {
          DEFAULT: '#2C5F7C', // Professional blue
          light: '#4A90B8',
          dark: '#1A3A4D',
        },
        secondary: {
          DEFAULT: '#4A90B8', // Secondary blue
          light: '#6BAED6',
          dark: '#2C5F7C',
        },
        accent: {
          DEFAULT: '#E67E22', // Partner role, important highlights
          light: '#F39C12',
          dark: '#D35400',
        },
        success: {
          DEFAULT: '#27AE60',
          light: '#4CAF50',
          dark: '#1B5E20',
        },
        warning: {
          DEFAULT: '#F39C12',
          light: '#FFA726',
          dark: '#E65100',
        },
        error: {
          DEFAULT: '#E74C3C',
          light: '#EF5350',
          dark: '#C62828',
        },
        info: {
          DEFAULT: '#3498DB', // Attendee role
          light: '#5DADE2',
          dark: '#2874A6',
        },
        gray: {
          50: '#FAFAFA',
          100: '#ECF0F1', // Neutral 100 from spec
          200: '#EEEEEE',
          300: '#BDC3C7', // Neutral 300 from spec
          400: '#BDBDBD',
          500: '#95A5A6', // Neutral 500 from spec
          600: '#757575',
          700: '#7F8C8D', // Neutral 700 from spec
          800: '#424242',
          900: '#34495E', // Neutral 900 from spec
        },
      },
      // Swiss Typography (matching Material-UI theme)
      fontFamily: {
        sans: ['Helvetica Neue', 'Helvetica', 'Arial', 'sans-serif'],
      },
      // Spacing scale (8px grid system)
      spacing: {
        0: '0',
        1: '0.125rem', // 2px
        2: '0.25rem', // 4px
        3: '0.375rem', // 6px
        4: '0.5rem', // 8px
        5: '0.625rem', // 10px
        6: '0.75rem', // 12px
        8: '1rem', // 16px
        10: '1.25rem', // 20px
        12: '1.5rem', // 24px
        16: '2rem', // 32px
        20: '2.5rem', // 40px
        24: '3rem', // 48px
        32: '4rem', // 64px
        40: '5rem', // 80px
        48: '6rem', // 96px
        56: '7rem', // 112px
        64: '8rem', // 128px
      },
      // Border radius (matching Material-UI theme)
      borderRadius: {
        none: '0',
        sm: '0.125rem', // 2px
        DEFAULT: '0.25rem', // 4px
        md: '0.375rem', // 6px
        lg: '0.5rem', // 8px
        xl: '0.75rem', // 12px
        '2xl': '1rem', // 16px
        full: '9999px',
      },
      // Box shadows
      boxShadow: {
        sm: '0px 1px 2px rgba(0, 0, 0, 0.05)',
        DEFAULT: '0px 2px 4px rgba(0, 0, 0, 0.1)',
        md: '0px 4px 8px rgba(0, 0, 0, 0.1)',
        lg: '0px 8px 16px rgba(0, 0, 0, 0.1)',
        xl: '0px 12px 24px rgba(0, 0, 0, 0.15)',
      },
    },
  },
  plugins: [],
  // Disable Tailwind's CSS reset to avoid conflicts with Material-UI
  corePlugins: {
    preflight: false,
  },
};
