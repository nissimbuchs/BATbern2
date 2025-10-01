# Frontend Architecture

## Component Organization
```
web-frontend/src/
├── components/
│   ├── shared/                    # Reusable components across roles
│   │   ├── Layout/
│   │   ├── Navigation/
│   │   ├── Forms/
│   │   └── Company/
│   ├── organizer/                 # Organizer-specific components
│   │   ├── EventManagement/
│   │   ├── SpeakerCoordination/
│   │   └── Analytics/
│   ├── speaker/                   # Speaker-specific components
│   │   ├── Dashboard/
│   │   ├── Submissions/
│   │   └── Profile/
│   ├── partner/                   # Partner-specific components
│   │   ├── Analytics/
│   │   └── StrategicInput/
│   └── attendee/                  # Attendee-specific components
│       ├── Discovery/
│       ├── Events/
│       └── Content/
├── hooks/                         # Custom React hooks
├── services/                      # API client services
├── stores/                        # Zustand state stores
├── types/                         # TypeScript type definitions
└── utils/                         # Utility functions
```

## State Management Architecture

### State Structure
```typescript
interface AppState {
  // Authentication state
  auth: {
    user: User | null;
    currentRole: UserRole;
    availableRoles: UserRole[];
    isAuthenticated: boolean;
  };

  // UI state
  ui: {
    sidebarOpen: boolean;
    notifications: Notification[];
    loading: Record<string, boolean>;
  };

  // Domain-specific state
  events: {
    currentEvent: Event | null;
    eventList: Event[];
    filters: EventFilters;
  };

  speakers: {
    speakerList: Speaker[];
    invitations: SpeakerInvitation[];
  };

  partners: {
    topicVotes: TopicVote[];
    meetings: PartnerMeeting[];
  };

  attendees: {
    registrations: EventRegistration[];
    bookmarks: ContentBookmark[];
  };

  companies: {
    companyList: Company[];
    selectedCompany: Company | null;
  };
}
```

## Detailed Component Specifications

### Shared Component Architecture

**Layout Components:**
```typescript
// BaseLayout Component
interface BaseLayoutProps {
  children: React.ReactNode;
  currentRole: UserRole;
  user: User;
  notifications: Notification[];
}

const BaseLayout: React.FC<BaseLayoutProps> = ({
  children,
  currentRole,
  user,
  notifications
}) => {
  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <AppHeader
        user={user}
        currentRole={currentRole}
        notifications={notifications}
      />
      <Sidebar currentRole={currentRole} />
      <MainContent>{children}</MainContent>
    </Box>
  );
};

// Navigation Component Specifications
interface NavigationItem {
  id: string;
  label: string;
  icon: React.ComponentType;
  path: string;
  roles: UserRole[];
  children?: NavigationItem[];
}

const navigationConfig: Record<UserRole, NavigationItem[]> = {
  [UserRole.ORGANIZER]: [
    {
      id: 'dashboard',
      label: 'Dashboard',
      icon: DashboardIcon,
      path: '/organizer/dashboard',
      roles: [UserRole.ORGANIZER]
    },
    {
      id: 'events',
      label: 'Event Management',
      icon: EventIcon,
      path: '/organizer/events',
      roles: [UserRole.ORGANIZER],
      children: [
        { id: 'create', label: 'Create Event', icon: AddIcon, path: '/organizer/events/create', roles: [UserRole.ORGANIZER] },
        { id: 'timeline', label: 'Event Timeline', icon: TimelineIcon, path: '/organizer/events/timeline', roles: [UserRole.ORGANIZER] }
      ]
    }
  ],
  // Additional role configurations...
};
```

**Form Components:**
```typescript
// Reusable Form Components with Validation
interface FormFieldProps<T> {
  name: keyof T;
  label: string;
  required?: boolean;
  validation?: (value: any) => string | undefined;
  disabled?: boolean;
}

// AutoComplete Company Selector
interface CompanySelectorProps {
  value: Company | null;
  onChange: (company: Company | null) => void;
  allowCreate?: boolean;
  roleFilter?: UserRole;
}

const CompanySelector: React.FC<CompanySelectorProps> = ({
  value,
  onChange,
  allowCreate = false,
  roleFilter
}) => {
  const { companies, searchCompanies, createCompany } = useCompanies();

  return (
    <Autocomplete
      options={companies}
      value={value}
      onChange={(_, newValue) => onChange(newValue)}
      getOptionLabel={(option) => option.name}
      renderInput={(params) => (
        <TextField {...params} label="Company" />
      )}
      freeSolo={allowCreate}
      onInputChange={async (_, inputValue) => {
        if (inputValue.length > 2) {
          await searchCompanies(inputValue);
        }
      }}
    />
  );
};
```

### Data Visualization Components

**Library:** Recharts 2.10+

**Rationale:** React-native charting library that integrates seamlessly with Material-UI and TypeScript, providing declarative chart components with minimal configuration.

**Usage:**
- Topic usage heat maps
- Historical usage timelines
- Partner engagement analytics
- Event attendance trends

**Key Components:**

```typescript
import {
  HeatMap,
  BarChart,
  LineChart,
  Tooltip,
  ResponsiveContainer
} from 'recharts';

// Topic Usage Heat Map Component
interface TopicHeatMapProps {
  topics: Topic[];
  usageHistory: TopicUsageRecord[];
  timeRange: '12months' | '24months';
}

const TopicUsageHeatMap: React.FC<TopicHeatMapProps> = ({
  topics,
  usageHistory,
  timeRange
}) => {
  const heatMapData = useMemo(() =>
    calculateHeatMapData(topics, usageHistory, timeRange),
    [topics, usageHistory, timeRange]
  );

  return (
    <ResponsiveContainer width="100%" height={400}>
      <HeatMap
        data={heatMapData}
        margin={{ top: 20, right: 30, left: 20, bottom: 5 }}
      >
        <Tooltip content={<TopicTooltip />} />
      </HeatMap>
    </ResponsiveContainer>
  );
};
```

**Performance Requirements:**
- Heat map rendering: <500ms for 100+ topics
- Interactive updates: <100ms response time
- Lazy loading for historical data beyond 24 months

### Role-Specific Component Specifications

**Enhanced Organizer Workflow Components:**

```typescript
// Enhanced Event Workflow Dashboard
interface EventWorkflowDashboardProps {
  event: Event;
  workflowState: EventWorkflowState;
  nextSteps: WorkflowStep[];
  onStateTransition: (targetState: EventWorkflowState) => Promise<void>;
}

const EventWorkflowDashboard: React.FC<EventWorkflowDashboardProps> = ({
  event,
  workflowState,
  nextSteps,
  onStateTransition
}) => {
  return (
    <Container maxWidth="lg">
      <Typography variant="h4" gutterBottom>
        Event Workflow: {event.title}
      </Typography>

      <Grid container spacing={3}>
        <Grid item xs={12} md={4}>
          <WorkflowProgressCard
            currentState={workflowState}
            completedSteps={getCompletedSteps(workflowState)}
            totalSteps={16}
          />
        </Grid>

        <Grid item xs={12} md={8}>
          <WorkflowStepDetails
            currentStep={getCurrentStep(workflowState)}
            nextSteps={nextSteps}
            onTransition={onStateTransition}
          />
        </Grid>

        <Grid item xs={12}>
          <WorkflowVisualization
            steps={getAllWorkflowSteps()}
            currentState={workflowState}
            eventType={event.eventType}
          />
        </Grid>
      </Grid>
    </Container>
  );
};

// Slot Assignment Management Interface
interface SlotAssignmentManagerProps {
  event: Event;
  slots: EventSlot[];
  speakers: SessionSpeaker[];
  preferences: SpeakerSlotPreferences[];
  onSlotAssign: (slotId: string, speakerId: string) => Promise<void>;
  onAutoAssign: () => Promise<void>;
}

const SlotAssignmentManager: React.FC<SlotAssignmentManagerProps> = ({
  event,
  slots,
  speakers,
  preferences,
  onSlotAssign,
  onAutoAssign
}) => {
  const [draggedSpeaker, setDraggedSpeaker] = useState<string | null>(null);

  return (
    <Container maxWidth="xl">
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 3 }}>
        <Typography variant="h5">Slot Assignment</Typography>
        <Button
          variant="contained"
          onClick={onAutoAssign}
          startIcon={<AutoAwesomeIcon />}
        >
          Auto-Assign Speakers
        </Button>
      </Box>

      <Grid container spacing={3}>
        <Grid item xs={12} md={8}>
          <Paper sx={{ p: 2 }}>
            <Typography variant="h6" gutterBottom>
              Event Slots ({event.eventType})
            </Typography>
            <SlotGrid
              slots={slots}
              eventType={event.eventType}
              onSlotDrop={(slotId, speakerId) => onSlotAssign(slotId, speakerId)}
              draggedSpeaker={draggedSpeaker}
            />
          </Paper>
        </Grid>

        <Grid item xs={12} md={4}>
          <Paper sx={{ p: 2 }}>
            <Typography variant="h6" gutterBottom>
              Unassigned Speakers
            </Typography>
            <UnassignedSpeakersList
              speakers={speakers.filter(s => !s.slotAssignment)}
              preferences={preferences}
              onDragStart={setDraggedSpeaker}
              onDragEnd={() => setDraggedSpeaker(null)}
            />
          </Paper>
        </Grid>
      </Grid>
    </Container>
  );
};

// Quality Review Dashboard
interface QualityReviewDashboardProps {
  pendingReviews: ContentQualityReview[];
  completedReviews: ContentQualityReview[];
  moderatorId: string;
  onReviewUpdate: (reviewId: string, status: QualityReviewStatus, feedback?: string) => Promise<void>;
}

const QualityReviewDashboard: React.FC<QualityReviewDashboardProps> = ({
  pendingReviews,
  completedReviews,
  moderatorId,
  onReviewUpdate
}) => {
  const [selectedReview, setSelectedReview] = useState<ContentQualityReview | null>(null);

  return (
    <Container maxWidth="lg">
      <Typography variant="h4" gutterBottom>
        Content Quality Review
      </Typography>

      <Tabs value={0}>
        <Tab label={`Pending Reviews (${pendingReviews.length})`} />
        <Tab label={`Completed Reviews (${completedReviews.length})`} />
      </Tabs>

      <TabPanel value={0}>
        <Grid container spacing={2}>
          {pendingReviews.map((review) => (
            <Grid item xs={12} md={6} key={review.id}>
              <ReviewCard
                review={review}
                onSelect={() => setSelectedReview(review)}
                onQuickApprove={() => onReviewUpdate(review.id, QualityReviewStatus.APPROVED)}
                onRequireChanges={() => setSelectedReview(review)}
              />
            </Grid>
          ))}
        </Grid>
      </TabPanel>

      {selectedReview && (
        <ReviewDetailModal
          review={selectedReview}
          open={!!selectedReview}
          onClose={() => setSelectedReview(null)}
          onSubmitReview={onReviewUpdate}
        />
      )}
    </Container>
  );
};

// Overflow Management & Voting Interface
interface OverflowManagementProps {
  event: Event;
  overflowSpeakers: OverflowSpeaker[];
  votes: SpeakerSelectionVote[];
  currentOrganizerId: string;
  onVote: (speakerId: string, vote: VoteType, reason?: string) => Promise<void>;
}

const OverflowManagement: React.FC<OverflowManagementProps> = ({
  event,
  overflowSpeakers,
  votes,
  currentOrganizerId,
  onVote
}) => {
  const [votingReason, setVotingReason] = useState<Record<string, string>>({});

  return (
    <Container maxWidth="lg">
      <Alert severity="warning" sx={{ mb: 3 }}>
        <AlertTitle>Speaker Overflow Detected</AlertTitle>
        We have {overflowSpeakers.length} speakers for {event.slotConfiguration.maxSlots} available slots.
        Please vote to select the final speakers.
      </Alert>

      <Grid container spacing={2}>
        {overflowSpeakers.map((speaker) => {
          const userVote = votes.find(v =>
            v.speakerId === speaker.speakerId && v.organizerId === currentOrganizerId
          );

          return (
            <Grid item xs={12} md={6} key={speaker.speakerId}>
              <SpeakerVotingCard
                speaker={speaker}
                currentVote={userVote}
                totalVotes={votes.filter(v => v.speakerId === speaker.speakerId).length}
                onVote={(vote, reason) => onVote(speaker.speakerId, vote, reason)}
                disabled={!!userVote}
              />
            </Grid>
          );
        })}
      </Grid>

      <VotingProgressSummary
        totalOrganizers={getTotalOrganizers()}
        votesReceived={getVotesReceived()}
        votingDeadline={event.overflowManagement?.votingDeadline}
      />
    </Container>
  );
};

// Real-time Notification & Alert System
interface WorkflowNotificationProps {
  notifications: WorkflowNotification[];
  onMarkAsRead: (notificationId: string) => void;
  onEscalate: (notificationId: string) => void;
}

const WorkflowNotificationCenter: React.FC<WorkflowNotificationProps> = ({
  notifications,
  onMarkAsRead,
  onEscalate
}) => {
  const unreadCount = notifications.filter(n => !n.isRead).length;

  return (
    <Badge badgeContent={unreadCount} color="error">
      <IconButton>
        <NotificationsIcon />
      </IconButton>
      <Menu>
        {notifications.map((notification) => (
          <NotificationItem
            key={notification.id}
            notification={notification}
            onMarkAsRead={() => onMarkAsRead(notification.id)}
            onEscalate={() => onEscalate(notification.id)}
          />
        ))}
      </Menu>
    </Badge>
  );
};
```

**Enhanced Speaker Interface Components:**

```typescript
// Speaker Preferences Collection Interface
interface SpeakerPreferencesFormProps {
  eventId: string;
  existingPreferences?: SpeakerSlotPreferences;
  eventSlots: EventSlot[];
  onSubmit: (preferences: SpeakerSlotPreferences) => Promise<void>;
}

const SpeakerPreferencesForm: React.FC<SpeakerPreferencesFormProps> = ({
  eventId,
  existingPreferences,
  eventSlots,
  onSubmit
}) => {
  const [preferences, setPreferences] = useState<Partial<SpeakerSlotPreferences>>(
    existingPreferences || {}
  );

  return (
    <Container maxWidth="md">
      <Typography variant="h5" gutterBottom>
        Slot Preferences & Requirements
      </Typography>

      <FormSection title="Time Slot Preferences">
        <Grid container spacing={2}>
          {eventSlots.map((slot) => (
            <Grid item xs={12} md={6} key={slot.id}>
              <SlotPreferenceCard
                slot={slot}
                preference={preferences.preferredTimeSlots?.find(p =>
                  p.startTime === slot.startTime
                )?.preference || PreferenceLevel.ACCEPTABLE}
                onChange={(preference) => updateSlotPreference(slot, preference)}
              />
            </Grid>
          ))}
        </Grid>
      </FormSection>

      <FormSection title="Technical Requirements">
        <FormControlLabel
          control={
            <Checkbox
              checked={preferences.ownLaptopRequired || false}
              onChange={(e) => setPreferences({
                ...preferences,
                ownLaptopRequired: e.target.checked
              })}
            />
          }
          label="I must use my own laptop for presentation"
        />

        <TextField
          fullWidth
          multiline
          rows={3}
          label="Special Equipment or Setup Requirements"
          value={preferences.specialEquipmentNeeds || ''}
          onChange={(e) => setPreferences({
            ...preferences,
            specialEquipmentNeeds: e.target.value
          })}
          sx={{ mt: 2 }}
        />
      </FormSection>

      <Button
        variant="contained"
        onClick={() => onSubmit(preferences as SpeakerSlotPreferences)}
        fullWidth
        sx={{ mt: 3 }}
      >
        Save Preferences
      </Button>
    </Container>
  );
};

// Speaker Workflow Status Dashboard
interface SpeakerWorkflowDashboardProps {
  speaker: Speaker;
  sessions: SessionSpeaker[];
  qualityReviews: ContentQualityReview[];
  onContentSubmit: (sessionId: string, content: SubmitContentRequest) => Promise<void>;
}

const SpeakerWorkflowDashboard: React.FC<SpeakerWorkflowDashboardProps> = ({
  speaker,
  sessions,
  qualityReviews,
  onContentSubmit
}) => {
  return (
    <Container maxWidth="lg">
      <Typography variant="h4" gutterBottom>
        My Speaking Engagements
      </Typography>

      <Grid container spacing={3}>
        {sessions.map((session) => {
          const review = qualityReviews.find(r => r.sessionId === session.sessionId);

          return (
            <Grid item xs={12} md={6} key={session.sessionId}>
              <SpeakerSessionCard
                session={session}
                qualityReview={review}
                onContentSubmit={(content) => onContentSubmit(session.sessionId, content)}
              />
            </Grid>
          );
        })}
      </Grid>

      <WorkflowProgressIndicator
        sessions={sessions}
        currentState={getCurrentWorkflowState(sessions)}
      />
    </Container>
  );
};
```

**Organizer Components:**

```typescript
// Event Management Dashboard
interface EventDashboardProps {
  currentEvent: Event | null;
  upcomingEvents: Event[];
  onEventSelect: (event: Event) => void;
}

const EventDashboard: React.FC<EventDashboardProps> = ({
  currentEvent,
  upcomingEvents,
  onEventSelect
}) => {
  return (
    <Grid container spacing={3}>
      <Grid item xs={12} md={8}>
        <EventTimelineCard event={currentEvent} />
      </Grid>
      <Grid item xs={12} md={4}>
        <WorkflowStatusCard event={currentEvent} />
        <SpeakerStatusCard event={currentEvent} />
      </Grid>
    </Grid>
  );
};

// Speaker Coordination Workflow
interface SpeakerWorkflowProps {
  event: Event;
  speakers: Speaker[];
  invitations: SpeakerInvitation[];
  onStateChange: (speakerId: string, newState: SpeakerState) => void;
}

const SpeakerWorkflow: React.FC<SpeakerWorkflowProps> = ({
  event,
  speakers,
  invitations,
  onStateChange
}) => {
  const workflowSteps = [
    'OPEN',
    'CONTACTED',
    'READY',
    'DECLINED_ACCEPTED',
    'FINAL_AGENDA'
  ];

  return (
    <Card>
      <CardHeader title="Speaker Pipeline" />
      <CardContent>
        {workflowSteps.map((step) => (
          <WorkflowStep
            key={step}
            step={step}
            speakers={speakers.filter(s => s.status === step)}
            onSpeakerAction={onStateChange}
          />
        ))}
      </CardContent>
    </Card>
  );
};

// Role Management Components
interface RoleManagementPanelProps {
  eventId?: string; // Optional: scope to specific event
}

const RoleManagementPanel: React.FC<RoleManagementPanelProps> = ({ eventId }) => {
  const [selectedUser, setSelectedUser] = useState<User | null>(null);
  const { data: pendingRequests } = usePendingApprovals();

  const { mutate: promoteUser } = usePromoteUser();
  const { mutate: demoteUser } = useDemoteUser();
  const { mutate: approveChange } = useApproveRoleChange();

  return (
    <Stack spacing={3}>
      <Typography variant="h5">Role Management</Typography>

      <UserSearchBar
        onUserSelected={setSelectedUser}
        placeholder="Search users by name or email..."
      />

      {selectedUser && (
        <RoleActionPanel
          user={selectedUser}
          onPromote={(role, reason) => promoteUser({
            userId: selectedUser.id,
            role,
            reason
          })}
          onDemote={(role, reason) => demoteUser({
            userId: selectedUser.id,
            role,
            reason
          })}
        />
      )}

      <PendingApprovalsQueue
        requests={pendingRequests || []}
        onApprove={(requestId, approved, comments) =>
          approveChange({
            userId: selectedUser?.id,
            changeId: requestId,
            approved,
            comments
          })
        }
      />
    </Stack>
  );
};

// API Hooks for Role Management
export const usePromoteUser = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ userId, role, reason }: PromoteUserRequest) => {
      return apiClient.post(`/api/v1/users/${userId}/roles`, { role, reason });
    },
    onSuccess: () => {
      queryClient.invalidateQueries(['users']);
      showNotification('User role promoted successfully', 'success');
    },
    onError: (error: ApiError) => {
      showNotification(error.message || 'Failed to promote user', 'error');
    },
  });
};

export const useDemoteUser = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ userId, role, reason }: DemoteUserRequest) => {
      return apiClient.delete(`/api/v1/users/${userId}/roles/${role}`, {
        data: { reason }
      });
    },
    onSuccess: (data) => {
      if (data.status === 202) {
        showNotification('Demotion request created - awaiting approval', 'info');
      } else {
        showNotification('User role demoted successfully', 'success');
      }
      queryClient.invalidateQueries(['users']);
    },
    onError: (error: ApiError) => {
      showNotification(error.message || 'Failed to demote user', 'error');
    },
  });
};

export const useApproveRoleChange = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ userId, changeId, approved, comments }: ApprovalRequest) => {
      return apiClient.post(
        `/api/v1/users/${userId}/role-changes/${changeId}/approve`,
        { approved, comments }
      );
    },
    onSuccess: () => {
      queryClient.invalidateQueries(['role-change-requests']);
      showNotification('Approval processed successfully', 'success');
    },
    onError: (error: ApiError) => {
      showNotification(error.message || 'Failed to process approval', 'error');
    },
  });
};

export const usePendingApprovals = () => {
  const { user } = useAuth();

  return useQuery({
    queryKey: ['role-change-requests', 'pending', user?.id],
    queryFn: async () => {
      const response = await apiClient.get(
        `/api/v1/users/${user.id}/role-changes?status=PENDING`
      );
      return response.data;
    },
    enabled: !!user?.id,
  });
};
```

**Speaker Components:**

```typescript
// Speaker Dashboard Component
interface SpeakerDashboardProps {
  speaker: Speaker;
  invitations: SpeakerInvitation[];
  submissions: SpeakerSubmission[];
}

const SpeakerDashboard: React.FC<SpeakerDashboardProps> = ({
  speaker,
  invitations,
  submissions
}) => {
  return (
    <Container maxWidth="lg">
      <Typography variant="h4" gutterBottom>
        Welcome, {speaker.firstName}
      </Typography>

      <Grid container spacing={3}>
        <Grid item xs={12} md={6}>
          <PendingInvitationsCard invitations={invitations} />
        </Grid>
        <Grid item xs={12} md={6}>
          <UpcomingSessionsCard speaker={speaker} />
        </Grid>
        <Grid item xs={12}>
          <SubmissionHistoryCard submissions={submissions} />
        </Grid>
      </Grid>
    </Container>
  );
};

// Material Submission Form
interface MaterialSubmissionProps {
  sessionId: string;
  existingSubmission?: SpeakerSubmission;
  onSubmit: (submission: SpeakerSubmissionData) => Promise<void>;
}

const MaterialSubmissionForm: React.FC<MaterialSubmissionProps> = ({
  sessionId,
  existingSubmission,
  onSubmit
}) => {
  const [formData, setFormData] = useState<SpeakerSubmissionData>({
    title: existingSubmission?.title || '',
    abstract: existingSubmission?.abstract || '',
    biography: existingSubmission?.biography || '',
    materials: existingSubmission?.materials || []
  });

  return (
    <Card>
      <CardHeader title="Submit Session Materials" />
      <CardContent>
        <Stack spacing={3}>
          <TextField
            label="Session Title"
            value={formData.title}
            onChange={(e) => setFormData(prev => ({ ...prev, title: e.target.value }))}
            fullWidth
            required
          />

          <TextField
            label="Abstract"
            value={formData.abstract}
            onChange={(e) => setFormData(prev => ({ ...prev, abstract: e.target.value }))}
            multiline
            rows={4}
            fullWidth
            required
          />

          <FileUploadZone
            acceptedTypes={['.pdf', '.pptx', '.jpg', '.png']}
            maxSize={10 * 1024 * 1024} // 10MB
            onFilesAdd={(files) => setFormData(prev => ({
              ...prev,
              materials: [...prev.materials, ...files]
            }))}
          />
        </Stack>
      </CardContent>
    </Card>
  );
};
```

**Partner Components:**

```typescript
// Topic Voting Interface
interface TopicVotingProps {
  availableTopics: Topic[];
  existingVotes: TopicVote[];
  votingPower: number;
  onVoteSubmit: (votes: TopicVoteData[]) => Promise<void>;
}

const TopicVotingInterface: React.FC<TopicVotingProps> = ({
  availableTopics,
  existingVotes,
  votingPower,
  onVoteSubmit
}) => {
  const [votes, setVotes] = useState<Record<string, number>>({});
  const totalVotesUsed = Object.values(votes).reduce((sum, vote) => sum + vote, 0);

  return (
    <Card>
      <CardHeader
        title="Strategic Topic Voting"
        subheader={`${votingPower - totalVotesUsed} votes remaining`}
      />
      <CardContent>
        <Stack spacing={2}>
          {availableTopics.map((topic) => (
            <TopicVoteCard
              key={topic.id}
              topic={topic}
              currentVote={votes[topic.id] || 0}
              onVoteChange={(newVote) => setVotes(prev => ({ ...prev, [topic.id]: newVote }))}
              disabled={totalVotesUsed >= votingPower && !votes[topic.id]}
            />
          ))}
        </Stack>
      </CardContent>
    </Card>
  );
};
```

**Attendee Components:**

```typescript
// Content Discovery Engine
interface ContentDiscoveryProps {
  searchQuery: string;
  filters: ContentFilters;
  results: SearchResult[];
  onSearch: (query: string, filters: ContentFilters) => void;
  onBookmark: (contentId: string) => void;
}

const ContentDiscovery: React.FC<ContentDiscoveryProps> = ({
  searchQuery,
  filters,
  results,
  onSearch,
  onBookmark
}) => {
  return (
    <Container maxWidth="lg">
      <Box sx={{ mb: 3 }}>
        <SearchBar
          value={searchQuery}
          onSearch={(query) => onSearch(query, filters)}
          placeholder="Search 20+ years of BATbern content..."
        />
        <FilterPanel
          filters={filters}
          onChange={(newFilters) => onSearch(searchQuery, newFilters)}
        />
      </Box>

      <Grid container spacing={2}>
        {results.map((result) => (
          <Grid item xs={12} md={6} lg={4} key={result.id}>
            <ContentCard
              content={result}
              onBookmark={() => onBookmark(result.id)}
              onView={() => window.open(result.url, '_blank')}
            />
          </Grid>
        ))}
      </Grid>
    </Container>
  );
};

// Event Registration Component
interface EventRegistrationProps {
  event: Event;
  existingRegistration?: EventRegistration;
  onRegister: (registration: RegistrationData) => Promise<void>;
}

const EventRegistration: React.FC<EventRegistrationProps> = ({
  event,
  existingRegistration,
  onRegister
}) => {
  const [registrationData, setRegistrationData] = useState<RegistrationData>({
    attendeeType: existingRegistration?.attendeeType || 'GENERAL',
    dietaryRequirements: existingRegistration?.dietaryRequirements || '',
    networkingOptIn: existingRegistration?.networkingOptIn || true
  });

  return (
    <Card>
      <CardHeader title={`Register for ${event.title}`} />
      <CardContent>
        <Stack spacing={3}>
          <EventDetailsCard event={event} />

          <FormControl fullWidth>
            <InputLabel>Attendee Type</InputLabel>
            <Select
              value={registrationData.attendeeType}
              onChange={(e) => setRegistrationData(prev => ({
                ...prev,
                attendeeType: e.target.value as AttendeeType
              }))}
            >
              <MenuItem value="GENERAL">General Attendee</MenuItem>
              <MenuItem value="STUDENT">Student</MenuItem>
              <MenuItem value="SPEAKER">Speaker</MenuItem>
            </Select>
          </FormControl>

          <TextField
            label="Dietary Requirements (Optional)"
            value={registrationData.dietaryRequirements}
            onChange={(e) => setRegistrationData(prev => ({
              ...prev,
              dietaryRequirements: e.target.value
            }))}
            multiline
            rows={2}
            fullWidth
          />

          <FormControlLabel
            control={
              <Checkbox
                checked={registrationData.networkingOptIn}
                onChange={(e) => setRegistrationData(prev => ({
                  ...prev,
                  networkingOptIn: e.target.checked
                }))}
              />
            }
            label="I'd like to participate in networking activities"
          />
        </Stack>
      </CardContent>
    </Card>
  );
};
```

### Component Development Standards

**TypeScript Interface Patterns:**
```typescript
// Props interfaces should be explicitly defined
interface ComponentNameProps {
  // Required props first
  requiredProp: string;
  onAction: (data: ActionData) => void;

  // Optional props second with defaults
  variant?: 'primary' | 'secondary';
  disabled?: boolean;
  children?: React.ReactNode;
}

// Use generic constraints for flexible components
interface DataTableProps<T extends { id: string }> {
  data: T[];
  columns: TableColumn<T>[];
  onRowClick?: (row: T) => void;
}
```

**Error Boundary Implementation:**
```typescript
class ComponentErrorBoundary extends React.Component<
  { children: React.ReactNode; fallback: React.ComponentType<{ error: Error }> },
  { hasError: boolean; error: Error | null }
> {
  constructor(props: any) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error) {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    console.error('Component Error:', error, errorInfo);
    // Log to monitoring service
    Sentry.captureException(error, { extra: errorInfo });
  }

  render() {
    if (this.state.hasError && this.state.error) {
      const FallbackComponent = this.props.fallback;
      return <FallbackComponent error={this.state.error} />;
    }

    return this.props.children;
  }
}
```

## Routing Architecture

```
/                              # Public home page
/current-event                 # Current BATbern event landing
/auth/login                    # Authentication

# Organizer routes
/organizer/dashboard          # Organizer main dashboard
/organizer/events             # Event management
/organizer/speakers           # Speaker database
/organizer/analytics          # Cross-domain analytics

# Speaker routes
/speaker/dashboard            # Speaker main dashboard
/speaker/invitations          # Speaking invitations
/speaker/sessions             # My sessions
/speaker/profile              # Speaker profile management

# Partner routes
/partner/dashboard            # Partner main dashboard (voting, meetings, profile)
/partner/voting               # Topic voting interface

# Attendee routes
/attendee/dashboard           # Attendee main dashboard
/attendee/events              # Event discovery
/attendee/search              # Content search
/attendee/content             # My bookmarked content

# Shared routes (role-adaptive)
/companies                    # Company management
/profile                      # User profile (adapts to role)
```
