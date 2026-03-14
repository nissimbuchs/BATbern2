import '@testing-library/jest-dom';
import { vi } from 'vitest';

// Suppress JSDOM "Not implemented" warnings that go directly to process.stderr
// (e.g., getComputedStyle with pseudo-elements, navigation to another Document)
const originalStderrWrite = process.stderr.write.bind(process.stderr);
process.stderr.write = (chunk: string | Uint8Array, ...rest: unknown[]) => {
  const str = typeof chunk === 'string' ? chunk : Buffer.from(chunk).toString();
  if (
    str.includes('Not implemented:') ||
    str.includes('getComputedStyle') ||
    str.includes('act(') ||
    str.includes('not wrapped in act') ||
    str.includes('not configured to support act') ||
    str.includes('anchorEl') ||
    str.includes('out-of-range value') ||
    str.includes('No event data available')
  ) {
    return true;
  }
  return (originalStderrWrite as (...a: unknown[]) => boolean)(chunk, ...rest);
};

// Mock localStorage before importing i18n (which uses localStorage for language detection)
// This is needed because Node.js 22+ has built-in localStorage that conflicts with jsdom
const localStorageMock = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: (key: string) => store[key] ?? null,
    setItem: (key: string, value: string) => {
      store[key] = value.toString();
    },
    removeItem: (key: string) => {
      delete store[key];
    },
    clear: () => {
      store = {};
    },
    get length() {
      return Object.keys(store).length;
    },
    key: (index: number) => Object.keys(store)[index] ?? null,
  };
})();

Object.defineProperty(globalThis, 'localStorage', {
  value: localStorageMock,
  writable: true,
});

// Now import i18n after localStorage is mocked
import i18n from '../i18n/config'; // Initialize i18n for all tests

// Set test language to English for consistent test assertions
i18n.changeLanguage('en');

// Configure React 19 test environment to support act()
// This tells React that we're in a testing environment that supports act() wrapping
globalThis.IS_REACT_ACT_ENVIRONMENT = true;

// Mock AWS Amplify for tests (keeping existing global fetch as fallback for non-MSW requests)
global.fetch =
  global.fetch ||
  (() =>
    Promise.resolve({
      json: () => Promise.resolve({}),
      ok: true,
      status: 200,
      statusText: 'OK',
    } as Response));

// Mock IntersectionObserver
global.IntersectionObserver = class IntersectionObserver {
  readonly root: Element | null = null;
  readonly rootMargin: string = '';
  readonly thresholds: ReadonlyArray<number> = [];

  constructor() {}
  observe() {}
  unobserve() {}
  disconnect() {}
  takeRecords(): IntersectionObserverEntry[] {
    return [];
  }
} as unknown as typeof IntersectionObserver;

// Mock ResizeObserver
global.ResizeObserver = class ResizeObserver {
  constructor() {}
  observe() {}
  unobserve() {}
  disconnect() {}
};

// Mock window.scrollTo
global.window.scrollTo = vi.fn();

// Mock HTMLCanvasElement for axe-core accessibility tests
HTMLCanvasElement.prototype.getContext = vi.fn(() => ({
  measureText: vi.fn(() => ({ width: 0 })),
  fillText: vi.fn(),
  clearRect: vi.fn(),
  getImageData: vi.fn(),
  putImageData: vi.fn(),
  createImageData: vi.fn(),
  setTransform: vi.fn(),
  drawImage: vi.fn(),
  save: vi.fn(),
  fillRect: vi.fn(),
  restore: vi.fn(),
  beginPath: vi.fn(),
  moveTo: vi.fn(),
  lineTo: vi.fn(),
  closePath: vi.fn(),
  stroke: vi.fn(),
  translate: vi.fn(),
  scale: vi.fn(),
  rotate: vi.fn(),
  arc: vi.fn(),
  fill: vi.fn(),
  transform: vi.fn(),
  rect: vi.fn(),
  clip: vi.fn(),
})) as unknown as CanvasRenderingContext2D;

// Mock AWS Amplify v6
vi.mock('aws-amplify', () => ({
  Amplify: {
    configure: vi.fn(),
  },
}));

vi.mock('aws-amplify/auth', () => ({
  signIn: vi.fn(),
  signOut: vi.fn(),
  signUp: vi.fn(),
  confirmSignUp: vi.fn(),
  getCurrentUser: vi.fn(),
  fetchAuthSession: vi.fn(),
  resetPassword: vi.fn(),
  confirmResetPassword: vi.fn(),
}));

vi.mock('@aws-amplify/ui-react', () => ({
  Authenticator: ({ children }: { children: React.ReactNode }) => children,
}));

// Mock Tone.js — BlobTopicSelector uses it for sound effects.
// jsdom has no Web Audio API; this prevents "AudioContext is not defined" errors
// in any test that imports from the BlobTopicSelector directory tree.
vi.mock('tone', () => {
  const makeNode = () => ({
    connect: vi.fn().mockReturnThis(),
    toDestination: vi.fn().mockReturnThis(),
    dispose: vi.fn(),
  });
  const makeSynth = () => ({
    ...makeNode(),
    volume: { value: 0, rampTo: vi.fn() },
    triggerAttack: vi.fn(),
    triggerRelease: vi.fn(),
    triggerAttackRelease: vi.fn(),
    releaseAll: vi.fn(),
    frequency: {
      value: 440,
      setValueAtTime: vi.fn(),
      exponentialRampToValueAtTime: vi.fn(),
    },
  });
  return {
    start: vi.fn().mockResolvedValue(undefined),
    now: vi.fn().mockReturnValue(0),
    getDestination: vi.fn().mockReturnValue({ mute: false }),
    PolySynth: vi.fn().mockImplementation(() => makeSynth()),
    Synth: vi.fn().mockImplementation(() => makeSynth()),
    NoiseSynth: vi.fn().mockImplementation(() => makeSynth()),
    PluckSynth: vi
      .fn()
      .mockImplementation(() => ({ ...makeNode(), triggerAttack: vi.fn(), volume: { value: 0 } })),
    FMSynth: vi.fn().mockImplementation(() => makeSynth()),
    MembraneSynth: vi.fn().mockImplementation(() => makeSynth()),
    MetalSynth: vi.fn().mockImplementation(() => makeSynth()),
    Reverb: vi.fn().mockImplementation(() => makeNode()),
    Filter: vi.fn().mockImplementation(() => makeNode()),
  };
});

// Mock framer-motion — renders motion.* as plain HTML elements in JSDOM.
// AnimatePresence renders children immediately (no animation timing in tests).
vi.mock('framer-motion', () => {
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const React = require('react');
  /* eslint-disable @typescript-eslint/no-unused-vars */
  const forwardMotion = (tag: string) =>
    React.forwardRef(
      (
        {
          children,
          layout: _layout,
          variants: _variants,
          custom: _custom,
          initial: _initial,
          animate: _animate,
          exit: _exit,
          transition: _transition,
          ...rest
        }: Record<string, unknown>,
        ref: unknown
      ) => React.createElement(tag, { ...rest, ref }, children)
    );
  /* eslint-enable @typescript-eslint/no-unused-vars */
  const motion = new Proxy({}, { get: (_target, prop: string) => forwardMotion(prop) });
  return {
    motion,
    AnimatePresence: ({ children }: { children: React.ReactNode }) =>
      React.createElement(React.Fragment, null, children),
    useAnimation: () => ({ start: vi.fn(), stop: vi.fn() }),
    useMotionValue: (init: unknown) => ({ get: () => init, set: vi.fn() }),
    useTransform: () => ({ get: vi.fn() }),
  };
});

// Suppress known JSDOM/MUI/React environment warnings in tests.
// These are not bugs — they are artifacts of running MUI components in JSDOM.
vi.spyOn(console, 'error').mockImplementation((...args: unknown[]) => {
  const msg = args.map(String).join(' ');
  if (
    // Network / CORS errors (expected with mocked fetch)
    msg.includes('Response for preflight has invalid HTTP status code') ||
    msg.includes('CORS') ||
    msg.includes('Cross-Origin') ||
    msg.includes('Network error') ||
    msg.includes('Unauthorized') ||
    msg.includes('Forbidden') ||
    msg.includes('Server error') ||
    msg.includes('API error') ||
    // MUI: anchorEl invalid — JSDOM has no layout engine, getBoundingClientRect returns zeros
    msg.includes('anchorEl') ||
    // MUI: out-of-range Select value — mock data may not match MenuItem options
    msg.includes('out-of-range value') ||
    // MUI: disabled button child in Tooltip — JSDOM event limitation
    (msg.includes('disabled') && msg.includes('Tooltip')) ||
    // React 19 act() warnings — Vitest async state update artifacts
    msg.includes('not wrapped in act') ||
    msg.includes('not configured to support act') ||
    msg.includes('inside a test was not wrapped') ||
    // JSDOM: getComputedStyle, navigation, and other unimplemented features
    msg.includes('Not implemented') ||
    msg.includes('getComputedStyle') ||
    // Event data lookup map (normal initial render state)
    msg.includes('No event data available')
  ) {
    return;
  }
  // For other errors, log them (important for debugging)
  console.warn(...args);
});

// Suppress known MUI/React warnings that go through console.warn or console.debug
const suppressPatterns = [
  'anchorEl',
  'out-of-range value',
  'Tooltip',
  'not wrapped in act',
  'not configured to support act',
  'inside a test was not wrapped',
  'Not implemented',
  'No event data available',
  'getComputedStyle',
];

function shouldSuppress(args: unknown[]): boolean {
  const msg = args.map(String).join(' ');
  return suppressPatterns.some((pattern) => msg.includes(pattern));
}

const originalWarn = console.warn.bind(console);
vi.spyOn(console, 'warn').mockImplementation((...args: unknown[]) => {
  if (shouldSuppress(args)) return;
  originalWarn(...args);
});

const originalDebug = console.debug.bind(console);
vi.spyOn(console, 'debug').mockImplementation((...args: unknown[]) => {
  if (shouldSuppress(args)) return;
  originalDebug(...args);
});
