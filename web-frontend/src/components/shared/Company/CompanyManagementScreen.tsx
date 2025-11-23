/**
 * CompanyManagementScreen - Main screen for company management
 *
 * Features:
 * - Company list with search and filters
 * - Grid/List view toggle
 * - Create company button
 * - Routing to detail view
 * - Responsive layout
 */

import React, { useState, useCallback } from 'react';
import { Routes, Route, useNavigate, useParams } from 'react-router-dom';
import {
  Box,
  Container,
  Typography,
  Button,
  Stack,
  ToggleButtonGroup,
  ToggleButton,
  TextField,
  InputAdornment,
  useTheme,
  useMediaQuery,
} from '@mui/material';
import { Search as SearchIcon } from '@mui/icons-material';
import {
  Add as AddIcon,
  ViewModule as GridIcon,
  ViewList as ListIcon,
  UploadFile as UploadFileIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { CompanyList } from '@/components/shared/Company/CompanyList';
import CompanyFilters from '@/components/shared/Company/CompanyFilters';
import { CompanyForm } from '@/components/shared/Company/CompanyForm';
import { CompanyDetailView } from '@/components/shared/Company/CompanyDetailView';
import { CompanyBatchImportModal } from '@/components/shared/Company/CompanyBatchImportModal';
import {
  useCreateCompany,
  useUpdateCompany,
} from '@/hooks/useCompanyMutations/useCompanyMutations';
import { useCompanies } from '@/hooks/useCompanies/useCompanies';
import { useCompany } from '@/hooks/useCompany/useCompany';
import type { components } from '@/types/generated/company-api.types';
import type { CompanyFilters as CompanyFiltersType } from '@/types/company.types';

type CreateCompanyRequest = components['schemas']['CreateCompanyRequest'];
type UpdateCompanyRequest = components['schemas']['UpdateCompanyRequest'];
type Company = components['schemas']['CompanyResponse'];

// Wrapper component for detail view to access useParams
const CompanyDetailWrapper: React.FC<{
  onBack: () => void;
  onEdit: (company: Company) => void;
}> = ({ onBack, onEdit }) => {
  const { id } = useParams<{ id: string }>();
  const {
    data: company,
    isLoading,
    error,
    refetch,
  } = useCompany(id || '', { expand: ['statistics'] });

  return (
    <CompanyDetailView
      company={company || null}
      isLoading={isLoading}
      error={error?.message}
      canEdit={true}
      onEdit={() => company && onEdit(company)}
      onBack={onBack}
      onRetry={refetch}
    />
  );
};

const CompanyManagementScreen: React.FC = () => {
  const { t } = useTranslation('common');
  const navigate = useNavigate();
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const createCompanyMutation = useCreateCompany();
  const updateCompanyMutation = useUpdateCompany();

  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');
  const [filters, setFilters] = useState<CompanyFiltersType>({});
  const [searchQuery, setSearchQuery] = useState('');
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [isBatchImportOpen, setIsBatchImportOpen] = useState(false);
  const [editingCompany, setEditingCompany] = useState<Company | null>(null);
  const [pagination] = useState({ page: 1, limit: 100 });

  // Combine filters with search query
  const combinedFilters: CompanyFiltersType = {
    ...filters,
    ...(searchQuery ? { searchQuery } : {}),
  };

  // Fetch companies list with filters (including search) and logo expansion
  const { data: companiesData, isLoading: isLoadingCompanies } = useCompanies(
    pagination,
    combinedFilters,
    {
      expand: ['logo'],
    }
  );

  const handleViewModeChange = (
    _: React.MouseEvent<HTMLElement>,
    newMode: 'grid' | 'list' | null
  ) => {
    if (newMode !== null) {
      setViewMode(newMode);
    }
  };

  const handleViewModeToggle = () => {
    setViewMode((prev) => (prev === 'grid' ? 'list' : 'grid'));
  };

  const handleFilterChange = useCallback((newFilters: CompanyFiltersType) => {
    setFilters(newFilters);
  }, []);

  const handleCreateCompany = useCallback(() => {
    setIsCreateModalOpen(true);
  }, []);

  const handleCloseCreateModal = useCallback(() => {
    setIsCreateModalOpen(false);
  }, []);

  const handleSubmitCompany = useCallback(
    async (data: CreateCompanyRequest | UpdateCompanyRequest) => {
      try {
        if (editingCompany) {
          // Update existing company (Story 1.16.2: uses company name as identifier)
          await updateCompanyMutation.mutateAsync({
            name: editingCompany.name,
            data: data as UpdateCompanyRequest,
          });
          setEditingCompany(null);
        } else {
          // Create new company
          await createCompanyMutation.mutateAsync(data as CreateCompanyRequest);
          setIsCreateModalOpen(false);
        }
        // Success notification could be added here
      } catch (error) {
        // Error is handled by the mutation and will be shown in the form
        console.error('Failed to save company:', error);
      }
    },
    [createCompanyMutation, updateCompanyMutation, editingCompany]
  );

  const handleEditCompany = useCallback((company: Company) => {
    setEditingCompany(company);
  }, []);

  const handleCloseEditModal = useCallback(() => {
    setEditingCompany(null);
  }, []);

  return (
    <Box component="main" role="main" sx={{ flexGrow: 1, p: 3 }}>
      <Container maxWidth="xl">
        {/* Header Section */}
        <Stack
          direction={isMobile ? 'column' : 'row'}
          justifyContent="space-between"
          alignItems={isMobile ? 'flex-start' : 'center'}
          spacing={2}
          mb={4}
        >
          <Typography variant="h4" component="h1" gutterBottom={isMobile}>
            {t('company.title')}
          </Typography>

          <Stack direction="row" spacing={2}>
            {/* View Toggle */}
            <ToggleButtonGroup
              value={viewMode}
              exclusive
              onChange={handleViewModeChange}
              aria-label={t('company.viewMode.toggleCurrent', { mode: viewMode })}
              size="small"
            >
              <ToggleButton value="grid" aria-label={t('company.viewMode.grid')}>
                <GridIcon />
              </ToggleButton>
              <ToggleButton value="list" aria-label={t('company.viewMode.list')}>
                <ListIcon />
              </ToggleButton>
            </ToggleButtonGroup>

            {/* Batch Import Button */}
            <Button
              variant="outlined"
              startIcon={<UploadFileIcon />}
              onClick={() => setIsBatchImportOpen(true)}
              aria-label={t('company.batchImport.button')}
            >
              {t('company.batchImport.button')}
            </Button>

            {/* Create Button */}
            <Button
              variant="contained"
              startIcon={<AddIcon />}
              onClick={handleCreateCompany}
              aria-label={t('company.createCompany')}
            >
              {t('company.createCompany')}
            </Button>
          </Stack>
        </Stack>

        {/* Search and Filter Section */}
        <Stack direction={isMobile ? 'column' : 'row'} spacing={2} mb={3}>
          {/* Search Bar - Simple text field for filtering the list */}
          <Box flex={1}>
            <TextField
              fullWidth
              placeholder={t('company.search.placeholder')}
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon color="action" />
                  </InputAdornment>
                ),
              }}
              size="small"
              aria-label={t('company.search.placeholder')}
            />
          </Box>
        </Stack>

        {/* Filters */}
        <Box mb={3}>
          <CompanyFilters onFilterChange={handleFilterChange} initialFilters={filters} />
        </Box>

        {/* Content Area with Routing */}
        <Routes>
          <Route
            path="/*"
            element={
              <CompanyList
                companies={companiesData?.data || []}
                isLoading={isLoadingCompanies}
                viewMode={viewMode}
                onViewModeToggle={handleViewModeToggle}
                onCompanyClick={(id) => navigate(`${id}`)}
              />
            }
          />
          <Route
            path="/:id"
            element={
              <CompanyDetailWrapper
                onBack={() => navigate('/organizer/companies')}
                onEdit={handleEditCompany}
              />
            }
          />
        </Routes>

        {/* Create Company Modal */}
        <CompanyForm
          open={isCreateModalOpen}
          mode="create"
          onClose={handleCloseCreateModal}
          onSubmit={handleSubmitCompany}
          allowDraft={true}
          userRole="organizer"
        />

        {/* Edit Company Modal */}
        {editingCompany && (
          <CompanyForm
            open={!!editingCompany}
            mode="edit"
            initialData={editingCompany}
            onClose={handleCloseEditModal}
            onSubmit={handleSubmitCompany}
            allowDraft={false}
            userRole="organizer"
          />
        )}

        {/* Batch Import Modal */}
        <CompanyBatchImportModal
          open={isBatchImportOpen}
          onClose={() => setIsBatchImportOpen(false)}
        />
      </Container>
    </Box>
  );
};

export default CompanyManagementScreen;
