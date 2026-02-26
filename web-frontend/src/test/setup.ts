import '@testing-library/jest-dom';
import { vi } from 'vitest';

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

// Suppress JSDOM errors for CORS preflight requests to S3 and Network errors
// These are expected in test environment where we mock XHR/fetch
vi.spyOn(console, 'error').mockImplementation((...args: unknown[]) => {
  if (
    typeof args[0] === 'string' &&
    (args[0].includes('Response for preflight has invalid HTTP status code') ||
      args[0].includes('CORS') ||
      args[0].includes('Cross-Origin') ||
      args[0].includes('Network error') ||
      args[0].includes('Unauthorized') ||
      args[0].includes('Forbidden') ||
      args[0].includes('Server error') ||
      args[0].includes('API error'))
  ) {
    return; // Suppress expected API and network errors in tests
  }
  // For other errors, log them (important for debugging)
  console.warn(...args);
});
