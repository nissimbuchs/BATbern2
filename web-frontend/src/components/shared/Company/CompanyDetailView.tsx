import React, { useState } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Button,
  Tabs,
  Tab,
  Chip,
  Skeleton,
  Alert,
  Stack,
  Avatar,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  Paper,
  BottomNavigation,
  BottomNavigationAction,
  useTheme,
  useMediaQuery,
} from '@mui/material';
import Grid from '@mui/material/Grid';
import {
  Edit as EditIcon,
  Delete as DeleteIcon,
  CheckCircle as CheckCircleIcon,
  Business as BusinessIcon,
  Dashboard as OverviewIcon,
  People as UsersIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import type { components } from '@/types/generated/company-api.types';
import type { components as SpeakerComponents } from '@/types/generated/speakers-api.types';
import type { User } from '@/types/user.types';
import DeleteCompanyDialog from '@/components/shared/Company/DeleteCompanyDialog';
import { Breadcrumbs } from '@/components/shared/Breadcrumbs/Breadcrumbs';
import { PartnerAttendanceDashboard } from '@/components/partner/PartnerAttendanceDashboard';
import { useUserList } from '@/hooks/useUserManagement';
import UserTable from '@/components/organizer/UserManagement/UserTable';
import UserCard from '@/components/organizer/UserManagement/UserCard';
import UserPagination from '@/components/organizer/UserManagement/UserPagination';
import apiClient from '@/services/api/apiClient';

type CompanyDetail = components['schemas']['CompanyResponse'];
type SpeakerResponse = SpeakerComponents['schemas']['SpeakerResponse'];

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel({ children, value, index }: TabPanelProps) {
  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`company-tabpanel-${index}`}
      aria-labelledby={`company-tab-${index}`}
    >
      <Box sx={{ pt: 3 }}>{children}</Box>
    </div>
  );
}

// ─── Company Speakers Panel ────────────────────────────────────────────────────

const CompanySpeakersPanel: React.FC<{ companyName: string }> = ({ companyName }) => {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['speakers', 'byCompany', companyName],
    queryFn: async () => {
      const res = await apiClient.get<{ speakers: SpeakerResponse[] }>('/speakers', {
        params: { companyName, limit: 100 },
      });
      return res.data.speakers ?? [];
    },
    enabled: !!companyName,
    staleTime: 5 * 60 * 1000,
  });

  if (isLoading) return <Skeleton variant="rectangular" height={200} />;
  if (isError) return <Alert severity="error">Failed to load speakers.</Alert>;
  if (!data?.length) {
    return (
      <Card>
        <CardContent sx={{ textAlign: 'center', py: 4 }}>
          <Typography color="text.secondary">No speakers from this company yet.</Typography>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <List>
        {data.map((speaker, i) => (
          <ListItem key={speaker.username ?? i} divider={i < data.length - 1}>
            <ListItemAvatar>
              <Avatar src={speaker.profilePictureUrl ?? undefined} alt={speaker.firstName}>
                {!speaker.profilePictureUrl &&
                  `${speaker.firstName?.charAt(0) ?? ''}${speaker.lastName?.charAt(0) ?? ''}`}
              </Avatar>
            </ListItemAvatar>
            <ListItemText
              primary={`${speaker.firstName ?? ''} ${speaker.lastName ?? ''}`.trim()}
              secondary={
                <span>
                  {speaker.expertiseAreas?.join(', ') || speaker.bio?.substring(0, 80) || ''}
                </span>
              }
            />
            {speaker.workflowState && (
              <Chip label={speaker.workflowState} size="small" variant="outlined" />
            )}
          </ListItem>
        ))}
      </List>
    </Card>
  );
};

// ─── Company Users Panel ───────────────────────────────────────────────────────

const CompanyUsersPanel: React.FC<{ companyName: string }> = ({ companyName }) => {
  const navigate = useNavigate();
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));
  const [page, setPage] = useState(1);
  const [limit, setLimit] = useState(20);

  const { data, isLoading, isError } = useUserList({
    filters: { company: companyName },
    pagination: { page, limit },
  });

  if (isLoading) return <Skeleton variant="rectangular" height={300} />;
  if (isError) return <Alert severity="error">Failed to load users.</Alert>;

  const users = data?.data ?? [];
  const paginationData = data?.pagination;

  return (
    <Box>
      {isMobile ? (
        <Stack spacing={2}>
          {users.map((user) => (
            <UserCard
              key={user.id}
              user={user}
              onClick={(u: User) => navigate(`/organizer/users/${u.id}`)}
            />
          ))}
        </Stack>
      ) : (
        <UserTable
          users={users}
          onRowClick={(user: User) => navigate(`/organizer/users/${user.id}`)}
          onAction={(action, user: User) => {
            if (action === 'view') navigate(`/organizer/users/${user.id}`);
          }}
        />
      )}
      {paginationData && (
        <UserPagination
          page={paginationData.page}
          totalPages={paginationData.totalPages}
          limit={paginationData.limit}
          onPageChange={setPage}
          onLimitChange={setLimit}
        />
      )}
    </Box>
  );
};

// ─── Main Component ────────────────────────────────────────────────────────────

interface CompanyDetailViewProps {
  company: CompanyDetail | null;
  isLoading?: boolean;
  error?: string;
  canEdit?: boolean;
  canDelete?: boolean;
  onEdit: (companyId: string) => void;
  onBack: () => void;
  onRetry?: () => void;
}

export const CompanyDetailView: React.FC<CompanyDetailViewProps> = ({
  company,
  isLoading = false,
  error,
  canEdit = true,
  canDelete = false,
  onEdit,
  onBack,
  onRetry,
}) => {
  const { t } = useTranslation('common');
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));
  const [activeTab, setActiveTab] = useState(0);
  const [isDeleteDialogOpen, setIsDeleteDialogOpen] = useState(false);

  const handleTabChange = (_event: React.SyntheticEvent, newValue: number) => {
    setActiveTab(newValue);
  };

  const handleEdit = () => {
    if (company) onEdit(company.name);
  };

  const handleDelete = () => {
    if (company) setIsDeleteDialogOpen(true);
  };

  if (isLoading) {
    return (
      <Box data-testid="detail-view-skeleton" sx={{ p: 3 }}>
        <Skeleton variant="rectangular" width="100%" height={200} sx={{ mb: 2 }} />
        <Skeleton variant="text" width="60%" height={40} sx={{ mb: 1 }} />
        <Skeleton variant="text" width="40%" height={30} />
      </Box>
    );
  }

  if (error) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
        {onRetry && (
          <Button variant="contained" onClick={onRetry}>
            {t('company.errors.retry')}
          </Button>
        )}
      </Box>
    );
  }

  if (!company) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="info">{t('company.detail.noData')}</Alert>
      </Box>
    );
  }

  return (
    <Box data-testid="company-detail-view" sx={{ p: { xs: 1.5, sm: 3 }, pb: 8 }}>
      {/* Breadcrumbs */}
      <Breadcrumbs
        items={[
          { label: t('company.title'), path: '/organizer/companies' },
          { label: company.displayName || company.name },
        ]}
      />

      {/* Action buttons */}
      {(canEdit || canDelete) && (
        <Box sx={{ mb: 3, display: 'flex', justifyContent: 'flex-end' }}>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
            {canDelete && (
              <Button
                variant="outlined"
                color="error"
                startIcon={<DeleteIcon />}
                onClick={handleDelete}
              >
                {t('actions.delete')}
              </Button>
            )}
            {canEdit && (
              <Button variant="contained" startIcon={<EditIcon />} onClick={handleEdit}>
                {t('actions.edit')}
              </Button>
            )}
          </Stack>
        </Box>
      )}

      {/* Company Profile Card */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Grid container spacing={3} component="div">
            <Grid size={{ xs: 12, sm: 3 }}>
              <Box
                sx={{
                  display: 'flex',
                  justifyContent: 'center',
                  alignItems: 'center',
                  minHeight: 180,
                }}
              >
                {company.logo?.url ? (
                  <Box
                    component="img"
                    src={company.logo.url}
                    alt={`${company.displayName || company.name} logo`}
                    sx={{ maxWidth: 150, maxHeight: 150, objectFit: 'contain' }}
                  />
                ) : (
                  <Box
                    sx={{
                      width: 150,
                      height: 150,
                      bgcolor: 'grey.200',
                      borderRadius: 1,
                      display: 'flex',
                      justifyContent: 'center',
                      alignItems: 'center',
                    }}
                  >
                    <BusinessIcon sx={{ fontSize: 60, color: 'grey.500' }} />
                  </Box>
                )}
              </Box>
            </Grid>

            <Grid size={{ xs: 12, sm: 9 }}>
              <Stack spacing={2}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                  <Typography
                    variant="h4"
                    component="h1"
                    sx={{ fontSize: { xs: '1.5rem', sm: '2.125rem' } }}
                  >
                    {company.displayName || company.name}
                  </Typography>
                  {company.isVerified && (
                    <Chip
                      data-testid="verified-badge"
                      icon={<CheckCircleIcon />}
                      label={t('company.badges.verified')}
                      color="success"
                      size="small"
                    />
                  )}
                </Box>

                {company.displayName && company.displayName !== company.name && (
                  <Typography variant="subtitle1" color="text.secondary">
                    {t('company.fields.legalName', { name: company.name })}
                  </Typography>
                )}

                <Grid container spacing={2} component="div">
                  {company.swissUID && (
                    <Grid size={{ xs: 12, sm: 6 }}>
                      <Typography variant="body2" color="text.secondary">
                        Swiss UID
                      </Typography>
                      <Typography variant="body1">{company.swissUID}</Typography>
                    </Grid>
                  )}
                  {company.industry && (
                    <Grid size={{ xs: 12, sm: 6 }}>
                      <Typography variant="body2" color="text.secondary">
                        Industry
                      </Typography>
                      <Typography variant="body1">{company.industry}</Typography>
                    </Grid>
                  )}
                  {company.website && (
                    <Grid size={{ xs: 12, sm: 6 }}>
                      <Typography variant="body2" color="text.secondary">
                        Website
                      </Typography>
                      <Typography variant="body1">
                        <a href={company.website} target="_blank" rel="noopener noreferrer">
                          {company.website}
                        </a>
                      </Typography>
                    </Grid>
                  )}
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <Typography variant="body2" color="text.secondary">
                      Created By
                    </Typography>
                    <Typography variant="body1" sx={{ fontFamily: 'monospace' }}>
                      {company.createdBy}
                    </Typography>
                  </Grid>
                </Grid>

                {company.description && (
                  <Box>
                    <Typography variant="body2" color="text.secondary" gutterBottom>
                      Description
                    </Typography>
                    <Typography variant="body1">{company.description}</Typography>
                  </Box>
                )}
              </Stack>
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      {/* Top Tabs — desktop only */}
      {!isMobile && (
        <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 2 }}>
          <Tabs value={activeTab} onChange={handleTabChange} aria-label="company detail tabs">
            <Tab
              label={t('company.detail.tabs.overview')}
              id="company-tab-0"
              aria-controls="company-tabpanel-0"
            />
            <Tab
              label={t('company.detail.tabs.users')}
              id="company-tab-1"
              aria-controls="company-tabpanel-1"
            />
          </Tabs>
        </Box>
      )}

      {/* Overview: attendance stats + speakers from this company */}
      <TabPanel value={activeTab} index={0}>
        <PartnerAttendanceDashboard companyName={company.name} />
        <Typography variant="h6" sx={{ mt: 4, mb: 2 }}>
          Speakers
        </Typography>
        <CompanySpeakersPanel companyName={company.name} />
      </TabPanel>

      {/* Users: all users associated with this company */}
      <TabPanel value={activeTab} index={1}>
        <CompanyUsersPanel companyName={company.name} />
      </TabPanel>

      {/* Delete Confirmation Dialog */}
      <DeleteCompanyDialog
        company={company}
        open={isDeleteDialogOpen}
        onClose={() => setIsDeleteDialogOpen(false)}
        onSuccess={onBack}
      />

      {/* Bottom Navigation — always visible */}
      <Paper sx={{ position: 'fixed', bottom: 0, left: 0, right: 0, zIndex: 1100 }} elevation={3}>
        <BottomNavigation value={activeTab} onChange={(_, v) => setActiveTab(v)}>
          <BottomNavigationAction icon={<OverviewIcon />} sx={{ minWidth: 0, flex: 1 }} />
          <BottomNavigationAction icon={<UsersIcon />} sx={{ minWidth: 0, flex: 1 }} />
        </BottomNavigation>
      </Paper>
    </Box>
  );
};
