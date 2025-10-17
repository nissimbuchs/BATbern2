import '@testing-library/jest-dom';
import { vi } from 'vitest';
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
})) as any;

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

// Suppress JSDOM errors for CORS preflight requests to S3
// These are expected in test environment where we mock XHR/fetch
const originalError = console.error;
beforeAll(() => {
  console.error = (...args: any[]) => {
    if (
      typeof args[0] === 'string' &&
      (args[0].includes('Response for preflight has invalid HTTP status code') ||
        args[0].includes('CORS') ||
        args[0].includes('Cross-Origin'))
    ) {
      return; // Suppress CORS-related errors
    }
    originalError.call(console, ...args);
  };
});

afterAll(() => {
  console.error = originalError; // Restore original console.error
});
