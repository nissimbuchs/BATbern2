import { vi } from 'vitest';

vi.mock('../services/api/apiClient');
vi.mock('./useClock');
vi.mock('./helpers');
vi.mock('./DateBadge/index');
vi.mock('../contexts/useConfig');
vi.mock('../../outside/Thing');
vi.mock(import('../hooks/usePaging'));
