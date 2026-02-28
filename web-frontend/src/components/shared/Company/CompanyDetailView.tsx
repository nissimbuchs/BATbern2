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
  AvatarGroup,
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
  People as UsersIcon,
  RecordVoiceOver as SpeakersIcon,
  Slideshow as SessionsIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { useNavigate, useLocation } from 'react-router-dom';
import apiClient from '@/services/api/apiClient';
import type { components } from '@/types/generated/company-api.types';
import type { User } from '@/types/user.types';
import DeleteCompanyDialog from '@/components/shared/Company/DeleteCompanyDialog';
import { Breadcrumbs } from '@/components/shared/Breadcrumbs/Breadcrumbs';
import { useUserList } from '@/hooks/useUserManagement';
import UserTable from '@/components/organizer/UserManagement/UserTable';
import UserCard from '@/components/organizer/UserManagement/UserCard';
import UserPagination from '@/components/organizer/UserManagement/UserPagination';

type CompanyDetail = components['schemas']['CompanyResponse'];

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

// ─── Company Users Panel ───────────────────────────────────────────────────────

const CompanyUsersPanel: React.FC<{ companyName: string; isMobile: boolean }> = ({
  companyName,
  isMobile,
}) => {
  const navigate = useNavigate();
  const location = useLocation();
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

  if (users.length === 0) {
    return (
      <Alert severity="info" sx={{ mt: 2 }}>
        No users associated with this company yet.
      </Alert>
    );
  }

  return (
    <Box sx={{ mt: 2 }}>
      {isMobile ? (
        <Stack spacing={2}>
          {users.map((user) => (
            <UserCard
              key={user.id}
              user={user}
              onClick={(u: User) =>
                navigate(`/organizer/users/${u.id}`, {
                  state: { from: location.pathname, fromLabel: companyName },
                })
              }
            />
          ))}
        </Stack>
      ) : (
        <UserTable
          users={users}
          onRowClick={(user: User) =>
            navigate(`/organizer/users/${user.id}`, {
              state: { from: location.pathname, fromLabel: companyName },
            })
          }
          onAction={(action, user: User) => {
            if (action === 'view')
              navigate(`/organizer/users/${user.id}`, {
                state: { from: location.pathname, fromLabel: companyName },
              });
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

// ─── Company Speakers Panel ────────────────────────────────────────────────────

const CompanySpeakersPanel: React.FC<{ companyName: string; isMobile: boolean }> = ({
  companyName,
  isMobile,
}) => {
  const navigate = useNavigate();
  const location = useLocation();
  const [page, setPage] = useState(1);
  const [limit, setLimit] = useState(20);

  const { data, isLoading, isError } = useUserList({
    filters: { role: ['SPEAKER'], company: companyName },
    pagination: { page, limit },
    enabled: !!companyName,
  });

  if (isLoading) return <Skeleton variant="rectangular" height={300} />;
  if (isError) return <Alert severity="error">Failed to load speakers.</Alert>;

  const speakers = data?.data ?? [];
  const paginationData = data?.pagination;

  if (speakers.length === 0) {
    return (
      <Alert severity="info" sx={{ mt: 2 }}>
        No speakers from this company yet.
      </Alert>
    );
  }

  return (
    <Box sx={{ mt: 2 }}>
      {isMobile ? (
        <Stack spacing={2}>
          {speakers.map((user) => (
            <UserCard
              key={user.id}
              user={user}
              onClick={(u: User) =>
                navigate(`/organizer/users/${u.id}`, {
                  state: { from: location.pathname, fromLabel: companyName },
                })
              }
            />
          ))}
        </Stack>
      ) : (
        <UserTable
          users={speakers}
          onRowClick={(user: User) =>
            navigate(`/organizer/users/${user.id}`, {
              state: { from: location.pathname, fromLabel: companyName },
            })
          }
          onAction={(action, user: User) => {
            if (action === 'view')
              navigate(`/organizer/users/${user.id}`, {
                state: { from: location.pathname, fromLabel: companyName },
              });
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

// ─── Company Sessions Panel ────────────────────────────────────────────────────

interface SessionSpeaker {
  username: string;
  firstName?: string;
  lastName?: string;
  company?: string;
  profilePictureUrl?: string;
}

interface CompanySession {
  sessionSlug: string;
  eventCode: string;
  eventTitle?: string;
  eventDate?: string;
  title: string;
  sessionType?: string;
  startTime?: string;
  room?: string;
  speakers: SessionSpeaker[];
}

const CompanySessionsPanel: React.FC<{ companyName: string }> = ({ companyName }) => {
  const navigate = useNavigate();

  const { data, isLoading, isError } = useQuery({
    queryKey: ['sessions', 'byCompany', companyName],
    queryFn: async () => {
      const res = await apiClient.get<{ data: CompanySession[] }>('/sessions', {
        params: { companyName, limit: 50 },
      });
      return res.data.data ?? [];
    },
    enabled: !!companyName,
    staleTime: 5 * 60 * 1000,
  });

  if (isLoading) return <Skeleton variant="rectangular" height={300} />;
  if (isError) return <Alert severity="error">Failed to load sessions.</Alert>;
  if (!data?.length) {
    return (
      <Alert severity="info" sx={{ mt: 2 }}>
        No sessions from this company yet.
      </Alert>
    );
  }

  return (
    <Stack spacing={2} sx={{ mt: 2 }}>
      {data.map((session) => {
        const companySpeakers = session.speakers.filter((s) => s.company === companyName);
        const otherSpeakers = session.speakers.filter((s) => s.company !== companyName);
        const allSpeakers = [...companySpeakers, ...otherSpeakers];
        const eventLabel = session.eventTitle
          ? `${session.eventCode} · ${session.eventTitle}`
          : session.eventCode;
        const dateLabel = session.eventDate
          ? new Date(session.eventDate).toLocaleDateString('de-CH', {
              year: 'numeric',
              month: 'short',
              day: 'numeric',
            })
          : null;

        return (
          <Card
            key={`${session.eventCode}-${session.sessionSlug}`}
            variant="outlined"
            onClick={() => navigate(`/events/${session.eventCode}`)}
            sx={{
              cursor: 'pointer',
              transition: 'border-color 0.15s, box-shadow 0.15s',
              '&:hover': { borderColor: 'primary.main', boxShadow: 2 },
            }}
          >
            <CardContent>
              <Stack spacing={1}>
                {/* Event badge */}
                <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                  <Chip label={eventLabel} size="small" color="primary" variant="outlined" />
                  {dateLabel && (
                    <Typography variant="caption" color="text.secondary">
                      {dateLabel}
                    </Typography>
                  )}
                  {session.room && (
                    <Typography variant="caption" color="text.secondary">
                      · {session.room}
                    </Typography>
                  )}
                </Stack>

                {/* Session title */}
                <Typography variant="subtitle1" fontWeight="medium">
                  {session.title}
                </Typography>

                {/* Speakers */}
                {allSpeakers.length > 0 && (
                  <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                    <AvatarGroup
                      max={6}
                      sx={{ '& .MuiAvatar-root': { width: 28, height: 28, fontSize: '0.75rem' } }}
                    >
                      {allSpeakers.map((speaker) => (
                        <Avatar
                          key={speaker.username}
                          src={speaker.profilePictureUrl ?? undefined}
                          alt={speaker.firstName}
                          sx={{
                            width: 28,
                            height: 28,
                            fontSize: '0.75rem',
                            border: speaker.company === companyName ? '2px solid' : undefined,
                            borderColor:
                              speaker.company === companyName ? 'primary.main' : undefined,
                          }}
                        >
                          {`${speaker.firstName?.charAt(0) ?? ''}${speaker.lastName?.charAt(0) ?? ''}`}
                        </Avatar>
                      ))}
                    </AvatarGroup>
                    <Typography variant="caption" color="text.secondary">
                      {allSpeakers
                        .map((s) => `${s.firstName ?? ''} ${s.lastName ?? ''}`.trim())
                        .filter(Boolean)
                        .join(', ')}
                    </Typography>
                  </Stack>
                )}
              </Stack>
            </CardContent>
          </Card>
        );
      })}
    </Stack>
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
            {t('actions.retry')}
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
              label={t('company.detail.tabs.users')}
              id="company-tab-0"
              aria-controls="company-tabpanel-0"
            />
            <Tab label="Speakers" id="company-tab-1" aria-controls="company-tabpanel-1" />
            <Tab label="Sessions" id="company-tab-2" aria-controls="company-tabpanel-2" />
          </Tabs>
        </Box>
      )}

      {/* Users: all users associated with this company */}
      <TabPanel value={activeTab} index={0}>
        <CompanyUsersPanel companyName={company.name} isMobile={isMobile} />
      </TabPanel>

      {/* Speakers: users with role SPEAKER from this company */}
      <TabPanel value={activeTab} index={1}>
        <CompanySpeakersPanel companyName={company.name} isMobile={isMobile} />
      </TabPanel>

      {/* Sessions: sessions where a speaker from this company participated */}
      <TabPanel value={activeTab} index={2}>
        <CompanySessionsPanel companyName={company.name} />
      </TabPanel>

      {/* Delete Confirmation Dialog */}
      <DeleteCompanyDialog
        company={company}
        open={isDeleteDialogOpen}
        onClose={() => setIsDeleteDialogOpen(false)}
        onSuccess={onBack}
      />

      {/* Bottom Navigation — mobile only */}
      <Paper
        sx={{
          position: 'fixed',
          bottom: 0,
          left: 0,
          right: 0,
          zIndex: 1100,
          display: { xs: 'block', md: 'none' },
        }}
        elevation={3}
      >
        <BottomNavigation value={activeTab} onChange={(_, v) => setActiveTab(v)}>
          <BottomNavigationAction icon={<UsersIcon />} sx={{ minWidth: 0, flex: 1 }} />
          <BottomNavigationAction icon={<SpeakersIcon />} sx={{ minWidth: 0, flex: 1 }} />
          <BottomNavigationAction icon={<SessionsIcon />} sx={{ minWidth: 0, flex: 1 }} />
        </BottomNavigation>
      </Paper>
    </Box>
  );
};
