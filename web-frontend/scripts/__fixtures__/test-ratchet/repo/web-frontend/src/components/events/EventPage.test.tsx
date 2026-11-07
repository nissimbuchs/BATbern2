/**
 * Fixture for test-ratchet.test.ts. Not a real test (vitest excludes __fixtures__).
 * vi.mock('@/services/inJsDoc') in a comment is not counted.
 */
import { vi } from 'vitest';

// vi.mock('@/services/commentedOut');
/* vi.mock('@/hooks/blockComment'); */
vi.mock('@/services/eventService');
vi.mock("@services/userService", () => ({ userService: {} }));
vi.mock(
  '@/services/multiLine',
  () => ({})
);
vi.mock('@/hooks/useEvents');
vi.mock('@hooks/useAuth');
vi.mock('./EventTabs');
vi.mock('../shared/Loader');
vi.mock('@/components/Footer');
vi.mock('@pages/Home');
vi.mock('react-i18next');
vi.mock('aws-amplify/auth');
vi.mock('@tanstack/react-query');
vi.mock('@/stores/uiStore');
vi.mocked(Object).mockReturnValue({});
const text = 'not a call: vi.mocked';
