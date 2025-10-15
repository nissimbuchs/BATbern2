/**
 * Company Management Types
 *
 * TypeScript interfaces for Company Management feature
 * Based on Story 2.5.1 and backend API from Story 1.14
 */

export interface Company {
  id: string;
  name: string;
  displayName?: string;
  swissUID?: string;
  website?: string;
  industry: string;
  sector?: 'Public' | 'Private' | 'Non-profit' | 'Government';
  location: {
    city: string;
    canton: string;
    country: string;
  };
  description?: string;
  logoUrl?: string;
  logoS3Key?: string;
  logoFileId?: string;
  isVerified: boolean;
  verificationStatus: 'Pending' | 'Verified' | 'Rejected';
  createdAt: string;
  updatedAt: string;
  createdBy: string;
}

export interface CompanyListItem {
  id: string;
  name: string;
  displayName?: string;
  logoUrl?: string;
  industry: string;
  location: { city: string; country: string };
  isPartner: boolean;
  isVerified: boolean;
  associatedUserCount: number;
}

export interface CompanyDetail extends Company {
  statistics?: CompanyStatistics;
  logo?: CompanyLogo;
  // Partner and User associations deferred to future stories
}

export interface CompanyStatistics {
  totalEvents: number;
  totalPresentations: number;
  totalAttendees: number;
  firstEvent?: string;
  mostRecentEvent?: string;
  topicExpertise: { topic: string; count: number }[];
}

export interface CompanyLogo {
  url: string;
  s3Key: string;
  fileId: string;
  uploadedAt: string;
}

export interface CreateCompanyRequest {
  name: string;
  displayName?: string;
  swissUID?: string;
  website?: string;
  industry?: string;
  description?: string;
}

export interface UpdateCompanyRequest extends Partial<CreateCompanyRequest> {}

export interface CompanyFilters {
  isPartner?: boolean;
  isVerified?: boolean;
  industry?: string;
  searchQuery?: string;
}

export interface CompanyStore {
  filters: CompanyFilters;
  viewMode: 'grid' | 'list';
  selectedCompanyId?: string;
  isCreateModalOpen: boolean;
  isEditModalOpen: boolean;
  setFilters: (filters: CompanyFilters) => void;
  toggleViewMode: () => void;
  setSelectedCompanyId: (id?: string) => void;
  openCreateModal: () => void;
  closeCreateModal: () => void;
  openEditModal: (id: string) => void;
  closeEditModal: () => void;
}

export interface PaginationParams {
  page: number;
  limit: number;
}

export interface PaginationMeta {
  currentPage: number;
  totalPages: number;
  totalItems: number;
  itemsPerPage: number;
  hasNextPage: boolean;
  hasPreviousPage: boolean;
}

export interface CompanyListResponse {
  data: CompanyListItem[];
  pagination: PaginationMeta;
}

export interface PresignedUrlResponse {
  presignedUrl: string;
  fileId: string;
  expiresAt: string;
}

export interface LogoUploadConfirmation {
  logoUrl: string;
  logoS3Key: string;
  logoFileId: string;
}
