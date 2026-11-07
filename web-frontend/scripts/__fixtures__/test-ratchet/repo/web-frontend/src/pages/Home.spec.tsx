import { vi } from 'vitest';

// A *.spec.tsx file is scanned like *.test.tsx; vi.doMock counts like vi.mock.
vi.doMock('@/hooks/useHome', () => ({}));
