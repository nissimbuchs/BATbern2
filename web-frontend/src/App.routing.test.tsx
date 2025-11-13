/**
 * App.tsx Routing Integration Tests
 * Story 2.8.2: Partner Detail View - Task 13 (RED Phase)
 * Tests for Partner Detail Screen routing integration
 */

import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import * as partnerApi from '@/services/api/partnerApi';
import { PartnerDetailScreen } from '@/components/organizer/PartnerManagement/PartnerDetailScreen';

// Mock the partner API
vi.mock('@/services/api/partnerApi');

// Mock auth hook to return authenticated organizer
vi.mock('@hooks/useAuth', () => ({
  useAuth: () => ({
    isAuthenticated: true,
    user: { username: 'testorganizer', role: 'ORGANIZER' },
    login: vi.fn(),
    logout: vi.fn(),
    loading: false,
  }),
}));

const createTestQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        cacheTime: 0,
      },
    },
  });

const mockPartnerDetail = {
  id: '550e8400-e29b-41d4-a716-446655440001',
  companyName: 'GoogleZH',
  partnershipLevel: 'GOLD',
  partnershipStartDate: '2022-01-01',
  tierStartDate: '2023-01-01',
  previousTier: 'SILVER',
  isActive: true,
  renewalDate: '2026-01-01',
  autoRenewal: true,
  company: {
    companyName: 'GoogleZH',
    legalName: 'Google Switzerland GmbH',
    industry: 'Technology',
    website: 'https://www.google.ch',
    location: 'Zurich, Switzerland',
    swissUid: 'CHE-123.456.789',
    taxStatus: 'VAT Registered',
  },
  statistics: {
    eventsAttended: 24,
    lastEventName: 'Spring 25',
    activeVotes: 5,
    totalMeetings: 12,
  },
  lastEvent: {
    eventName: 'Spring 25',
    date: '2025-03-15',
    attendeeCount: 150,
    registrations: 180,
    downloads: 45,
  },
  nextMeeting: {
    type: 'Strategic Review',
    scheduledDate: '2025-06-20',
    location: 'Bern Office',
  },
};

describe('App Routing Integration - Partner Detail Screen', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // Test 13.1: Route should exist and accept companyName parameter
  it('should_renderPartnerDetail_when_routeWithCompanyNameAccessed', async () => {
    (partnerApi.getPartnerDetail as any).mockResolvedValue(mockPartnerDetail);

    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/partners/GoogleZH']}>
          <Routes>
            <Route path="/partners/:companyName" element={<PartnerDetailScreen />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );

    // Should call API with companyName from route param
    await waitFor(() => {
      expect(partnerApi.getPartnerDetail).toHaveBeenCalledWith('GoogleZH', expect.any(String));
    });

    // Should render partner detail screen
    await waitFor(() => {
      expect(screen.getByText(/GoogleZH/i)).toBeInTheDocument();
    });
  });

  // Test 13.2: Route should validate companyName max 12 chars
  it('should_handle404_when_companyNameExceedsMaxLength', async () => {
    const longCompanyName = 'ThisIsAVeryLongCompanyName'; // > 12 chars

    (partnerApi.getPartnerDetail as any).mockRejectedValue({
      response: { status: 400 },
    });

    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[`/partners/${longCompanyName}`]}>
          <Routes>
            <Route path="/partners/:companyName" element={<PartnerDetailScreen />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );

    // Should show error state (either validation error or 404)
    await waitFor(() => {
      expect(screen.getByText(/Error Loading Partner|Partner Not Found/i)).toBeInTheDocument();
    });
  });

  // Test 13.3: Direct URL access with companyName should work
  it('should_loadPartnerDetail_when_directURLAccessWithCompanyName', async () => {
    (partnerApi.getPartnerDetail as any).mockResolvedValue(mockPartnerDetail);

    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/partners/GoogleZH']}>
          <Routes>
            <Route path="/partners/:companyName" element={<PartnerDetailScreen />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );

    // Should load partner detail directly without going through directory
    await waitFor(() => {
      expect(partnerApi.getPartnerDetail).toHaveBeenCalledWith('GoogleZH', expect.any(String));
    });
  });

  // Test 13.4: 404 handling for invalid companyName
  it('should_show404Error_when_invalidCompanyNameProvided', async () => {
    (partnerApi.getPartnerDetail as any).mockRejectedValue({
      response: { status: 404, data: { message: 'Partner not found' } },
    });

    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/partners/NonExistentCompany']}>
          <Routes>
            <Route path="/partners/:companyName" element={<PartnerDetailScreen />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );

    // Should show 404 error
    await waitFor(() => {
      expect(screen.getByText(/Partner Not Found/i)).toBeInTheDocument();
    });
  });

  // Test 13.5: Route parameter should be extracted correctly
  it('should_extractCompanyName_when_routeParameterProvided', async () => {
    (partnerApi.getPartnerDetail as any).mockResolvedValue(mockPartnerDetail);

    const queryClient = createTestQueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/partners/TestCompany']}>
          <Routes>
            <Route path="/partners/:companyName" element={<PartnerDetailScreen />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );

    // Should extract 'TestCompany' from route param
    await waitFor(() => {
      expect(partnerApi.getPartnerDetail).toHaveBeenCalledWith('TestCompany', expect.any(String));
    });
  });
});
