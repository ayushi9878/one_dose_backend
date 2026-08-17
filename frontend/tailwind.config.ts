import type { Config } from 'tailwindcss';

/**
 * CareFlow's visual language: a calm teal primary with restrained clinical
 * status colours. Every status hue is chosen to stay legible against both the
 * light surface and its own tinted background at WCAG AA.
 */
const config: Config = {
  content: ['./src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        canvas: '#f6f8fa',
        surface: '#ffffff',
        'surface-muted': '#f9fafb',
        border: {
          DEFAULT: '#e4e8ed',
          strong: '#d0d7de',
        },
        ink: {
          DEFAULT: '#0f1c2e',
          muted: '#5a6b7f',
          subtle: '#8494a6',
        },
        brand: {
          50: '#eef7f6',
          100: '#d3ebe8',
          200: '#a8d7d2',
          300: '#72bcb5',
          400: '#449d96',
          500: '#2b8079',
          600: '#1f6761',
          700: '#1b524e',
          800: '#174240',
          900: '#123433',
        },
        risk: {
          none: '#5a6b7f',
          low: '#2f855a',
          medium: '#b7791f',
          high: '#c53030',
          critical: '#822727',
        },
      },
      fontFamily: {
        sans: ['var(--font-sans)', 'system-ui', 'sans-serif'],
        mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'monospace'],
      },
      fontSize: {
        'display': ['2rem', { lineHeight: '2.4rem', letterSpacing: '-0.02em' }],
      },
      borderRadius: {
        card: '0.75rem',
      },
      boxShadow: {
        card: '0 1px 2px rgba(15, 28, 46, 0.04), 0 1px 3px rgba(15, 28, 46, 0.06)',
        raised: '0 4px 12px rgba(15, 28, 46, 0.08)',
        overlay: '0 8px 24px rgba(15, 28, 46, 0.12)',
      },
      transitionDuration: {
        DEFAULT: '150ms',
      },
    },
  },
  plugins: [],
};

export default config;
