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

## Internationalization (i18n) Architecture

### Supported Languages & Locales

**Primary Language**: German (de-CH) - Swiss German locale
**Secondary Language**: English (en-US)
**Default/Fallback**: German (de-CH)

**Rationale**: BATbern serves a primarily Swiss German-speaking audience. German is the default language with English as an optional alternative for international attendees and speakers.

### Library Stack

| Library | Version | Purpose |
|---------|---------|---------|
| **i18next** | ^23.7.0 | Core i18n framework with namespace support, pluralization, interpolation |
| **react-i18next** | ^14.0.0 | React bindings with hooks (`useTranslation`) and Suspense support |
| **i18next-browser-languagedetector** | ^7.2.0 | Automatic language detection from browser, localStorage, user profile |
| **date-fns** | ^3.0.0 | Date/time formatting with locale support (de, enUS) |

**Bundle Impact**: ~14KB gzipped (i18next ecosystem)

**Rationale**: react-i18next provides the most flexible and feature-rich solution with excellent TypeScript support, namespace-based code splitting, and seamless integration with React 18.2+ and our existing tech stack.

### Translation File Structure

```
web-frontend/
├── public/
│   └── locales/
│       ├── de/
│       │   ├── common.json          # Shared UI (navigation, buttons, labels)
│       │   ├── auth.json            # Authentication & account flows
│       │   ├── validation.json      # Form validation messages
│       │   ├── organizer.json       # Organizer-specific UI
│       │   ├── speaker.json         # Speaker-specific UI
│       │   ├── partner.json         # Partner-specific UI
│       │   └── attendee.json        # Attendee-specific UI
│       └── en/
│           └── [same structure]
├── src/
│   └── i18n/
│       ├── config.ts                # i18next initialization & configuration
│       ├── types.ts                 # Auto-generated TypeScript types
│       └── utils.ts                 # Formatting helpers (dates, currency)
```

**Namespace Strategy**:
- **common**: Loaded immediately on app start (navigation, shared UI chrome)
- **auth**: Loaded on authentication routes
- **validation**: Loaded with forms (shared error messages)
- **Role-specific namespaces**: Lazy-loaded when user switches to that role context

### Translation Key Patterns

**Nested JSON Structure** (type-safe with TypeScript):

```json
{
  "auth": {
    "login": {
      "title": "Anmelden",
      "emailLabel": "E-Mail-Adresse",
      "passwordLabel": "Passwort",
      "submitButton": "Anmelden",
      "forgotPassword": "Passwort vergessen?"
    },
    "validation": {
      "emailRequired": "E-Mail ist erforderlich",
      "invalidEmail": "Ungültige E-Mail-Adresse"
    }
  },
  "common": {
    "navigation": {
      "dashboard": "Übersicht",
      "events": "Veranstaltungen",
      "profile": "Profil"
    }
  }
}
```

**Usage in Components**:
```typescript
import { useTranslation } from 'react-i18next';

const LoginForm: React.FC = () => {
  const { t } = useTranslation('auth');

  return (
    <form>
      <h1>{t('login.title')}</h1>
      <TextField label={t('login.emailLabel')} />
      <TextField label={t('login.passwordLabel')} type="password" />
      <Button>{t('login.submitButton')}</Button>
    </form>
  );
};
```

### TypeScript Type Safety

**Compile-time Type Checking for Translation Keys**:

```typescript
// src/i18n/types.ts (auto-generated from de/common.json)
import 'react-i18next';
import type common from '../../public/locales/de/common.json';
import type auth from '../../public/locales/de/auth.json';
import type validation from '../../public/locales/de/validation.json';
import type organizer from '../../public/locales/de/organizer.json';
import type speaker from '../../public/locales/de/speaker.json';
import type partner from '../../public/locales/de/partner.json';
import type attendee from '../../public/locales/de/attendee.json';

declare module 'react-i18next' {
  interface CustomTypeOptions {
    defaultNS: 'common';
    resources: {
      common: typeof common;
      auth: typeof auth;
      validation: typeof validation;
      organizer: typeof organizer;
      speaker: typeof speaker;
      partner: typeof partner;
      attendee: typeof attendee;
    };
  }
}
```

**Result**:
- IDE autocomplete for all translation keys
- **Compile-time errors** for missing or mistyped keys
- Type inference for interpolation variables

### i18next Configuration

```typescript
// src/i18n/config.ts
import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';

// Import all translation files (bundled at build time)
import commonDe from '../../public/locales/de/common.json';
import authDe from '../../public/locales/de/auth.json';
import validationDe from '../../public/locales/de/validation.json';
import organizerDe from '../../public/locales/de/organizer.json';
import speakerDe from '../../public/locales/de/speaker.json';
import partnerDe from '../../public/locales/de/partner.json';
import attendeeDe from '../../public/locales/de/attendee.json';

import commonEn from '../../public/locales/en/common.json';
import authEn from '../../public/locales/en/auth.json';
import validationEn from '../../public/locales/en/validation.json';
import organizerEn from '../../public/locales/en/organizer.json';
import speakerEn from '../../public/locales/en/speaker.json';
import partnerEn from '../../public/locales/en/partner.json';
import attendeeEn from '../../public/locales/en/attendee.json';

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      de: {
        common: commonDe,
        auth: authDe,
        validation: validationDe,
        organizer: organizerDe,
        speaker: speakerDe,
        partner: partnerDe,
        attendee: attendeeDe,
      },
      en: {
        common: commonEn,
        auth: authEn,
        validation: validationEn,
        organizer: organizerEn,
        speaker: speakerEn,
        partner: partnerEn,
        attendee: attendeeEn,
      },
    },

    fallbackLng: 'de',
    defaultNS: 'common',
    ns: ['common', 'auth', 'validation', 'organizer', 'speaker', 'partner', 'attendee'],

    detection: {
      order: ['localStorage', 'navigator', 'htmlTag'],
      caches: ['localStorage'],
      lookupLocalStorage: 'batbern-language',
    },

    interpolation: {
      escapeValue: false, // React already escapes by default
    },

    react: {
      useSuspense: true, // Use React Suspense for async loading
    },
  });

export default i18n;
```

### Language Detection & Selection

**Detection Priority Order**:
1. **User Profile** (authenticated users with saved language preference in database)
2. **LocalStorage** (`batbern-language` key for returning users)
3. **Browser Language** (`navigator.language`)
4. **Default Fallback**: German (`de`)

**Language Switcher Component**:
```typescript
// src/components/shared/LanguageSwitcher.tsx
import { useTranslation } from 'react-i18next';
import { MenuItem, Select } from '@mui/material';
import { useAuthStore } from '@/stores/authStore';

const LanguageSwitcher: React.FC = () => {
  const { i18n } = useTranslation();
  const { user, updateUserLanguage } = useAuthStore();

  const handleLanguageChange = async (newLang: 'de' | 'en') => {
    await i18n.changeLanguage(newLang);
    document.documentElement.lang = newLang;

    // Update user profile if authenticated
    if (user) {
      await updateUserLanguage(newLang);
    }
  };

  return (
    <Select
      value={i18n.language}
      onChange={(e) => handleLanguageChange(e.target.value as 'de' | 'en')}
      size="small"
    >
      <MenuItem value="de">🇨🇭 Deutsch</MenuItem>
      <MenuItem value="en">🇬🇧 English</MenuItem>
    </Select>
  );
};
```

**Placement**: Header navigation (top-right, next to user profile menu)

### State Management Integration

```typescript
// src/stores/uiStore.ts (Zustand)
interface UIState {
  locale: 'de' | 'en';
  setLocale: (locale: 'de' | 'en') => void;
  // ... other UI state
}

export const useUIStore = create<UIState>((set) => ({
  locale: 'de',
  setLocale: (locale) => {
    set({ locale });
    i18n.changeLanguage(locale); // Sync with i18next
    document.documentElement.lang = locale; // Update HTML lang attribute
  },
}));
```

**Persistence**:
- LocalStorage: `batbern-language` key (automatic via i18next-browser-languagedetector)
- Database: User profile `preferredLanguage` field (synchronized on language change for authenticated users)

### Date & Time Formatting

**Using date-fns with Locale Support**:

```typescript
// src/i18n/utils.ts
import { format, parseISO } from 'date-fns';
import { de, enUS } from 'date-fns/locale';
import { useTranslation } from 'react-i18next';

const localeMap = {
  de: de,
  en: enUS,
};

export const useFormatDate = () => {
  const { i18n } = useTranslation();

  return {
    formatDate: (date: Date | string, formatStr: string = 'PPP') => {
      const dateObj = typeof date === 'string' ? parseISO(date) : date;
      return format(dateObj, formatStr, { locale: localeMap[i18n.language] });
    },

    formatDateTime: (date: Date | string) => {
      const dateObj = typeof date === 'string' ? parseISO(date) : date;
      return format(dateObj, 'PPP p', { locale: localeMap[i18n.language] });
    },
  };
};

// Usage in components:
const EventCard: React.FC<{ event: Event }> = ({ event }) => {
  const { formatDateTime } = useFormatDate();

  return <div>{formatDateTime(event.startTime)}</div>;
  // German: "15. März 2024, 14:30"
  // English: "March 15, 2024, 2:30 PM"
};
```

### Number & Currency Formatting

**Using Native Intl API** (no additional library needed):

```typescript
// src/i18n/utils.ts
export const useFormatNumber = () => {
  const { i18n } = useTranslation();

  const localeMap = {
    de: 'de-CH',
    en: 'en-US',
  };

  return {
    formatCurrency: (amount: number) => {
      return new Intl.NumberFormat(localeMap[i18n.language], {
        style: 'currency',
        currency: 'CHF',
      }).format(amount);
    },

    formatNumber: (value: number, decimals: number = 2) => {
      return new Intl.NumberFormat(localeMap[i18n.language], {
        minimumFractionDigits: decimals,
        maximumFractionDigits: decimals,
      }).format(value);
    },
  };
};

// Usage:
const { formatCurrency } = useFormatNumber();
formatCurrency(1500.5);
// German (de-CH): "CHF 1'500.50"
// English (en-US): "CHF 1,500.50"
```

### Pluralization

**Built-in i18next Pluralization**:

```json
{
  "speakers": {
    "count_zero": "Keine Referenten",
    "count_one": "{{count}} Referent",
    "count_other": "{{count}} Referenten"
  },
  "events": {
    "registered_one": "Sie sind für {{count}} Veranstaltung registriert",
    "registered_other": "Sie sind für {{count}} Veranstaltungen registriert"
  }
}
```

```typescript
const { t } = useTranslation('common');

t('speakers.count', { count: 0 });  // "Keine Referenten"
t('speakers.count', { count: 1 });  // "1 Referent"
t('speakers.count', { count: 5 });  // "5 Referenten"
```

### Content Translation Strategy

**What Gets Translated**:

| Content Type | Translation Approach |
|--------------|---------------------|
| **UI Chrome** (navigation, buttons, labels, help text) | ✅ Fully translated (de/en) |
| **Form Validation Messages** | ✅ Fully translated (de/en) |
| **System Notifications** | ✅ Fully translated based on user's UI language |
| **Email Templates** | ✅ Fully translated (separate AWS SES templates for de/en) |
| **Event Titles & Descriptions** | ❌ Single language (organizer's choice, typically German) |
| **Speaker Abstracts** | ❌ Original language preserved with language metadata field |
| **Company Names** | ❌ Never translated |
| **User-Generated Content** | ❌ Preserved in original language |

**Rationale**:
- Swiss audience is primarily German-speaking
- Event content is typically German with occasional English speakers
- Preserving original language maintains authenticity and avoids poor machine translation
- Language metadata allows future filtering/search by content language

**User-Generated Content Handling**:
- Database schema includes optional `language` field (ISO 639-1: 'de', 'en')
- UI displays language badge for multilingual events
- No automatic translation - content shown in original language

### URL Structure & SEO

**Query Parameter Approach**:
```
https://batbern.ch/current-event?lang=de
https://batbern.ch/current-event?lang=en
```

**HTML Lang Attribute**: Dynamically updated
```html
<html lang="de"> <!-- or lang="en" -->
```

**Meta Tags** (for public pages):
```typescript
// Update on language change
<meta name="description" content={t('meta.description')} />
<link rel="alternate" hreflang="de" href="?lang=de" />
<link rel="alternate" hreflang="en" href="?lang=en" />
```

### CI/CD Translation Validation

**Pre-commit Hook: Translation Key Parity Check**

```bash
#!/bin/bash
# .husky/pre-commit

# Validate that all translation keys exist in both de and en
node scripts/validate-translations.js

if [ $? -ne 0 ]; then
  echo "❌ Translation validation failed. Ensure all keys exist in both de and en."
  exit 1
fi
```

**Validation Script**:
```javascript
// scripts/validate-translations.js
const fs = require('fs');
const path = require('path');

const namespaces = ['common', 'auth', 'validation', 'organizer', 'speaker', 'partner', 'attendee'];
const languages = ['de', 'en'];

function flattenKeys(obj, prefix = '') {
  return Object.keys(obj).reduce((acc, key) => {
    const fullKey = prefix ? `${prefix}.${key}` : key;
    if (typeof obj[key] === 'object' && obj[key] !== null) {
      return acc.concat(flattenKeys(obj[key], fullKey));
    }
    return acc.concat(fullKey);
  }, []);
}

let hasErrors = false;

namespaces.forEach(ns => {
  const deFile = path.join(__dirname, `../public/locales/de/${ns}.json`);
  const enFile = path.join(__dirname, `../public/locales/en/${ns}.json`);

  const deKeys = flattenKeys(JSON.parse(fs.readFileSync(deFile, 'utf-8')));
  const enKeys = flattenKeys(JSON.parse(fs.readFileSync(enFile, 'utf-8')));

  const missingInEn = deKeys.filter(k => !enKeys.includes(k));
  const missingInDe = enKeys.filter(k => !deKeys.includes(k));

  if (missingInEn.length > 0) {
    console.error(`❌ [${ns}] Missing in English:`, missingInEn);
    hasErrors = true;
  }

  if (missingInDe.length > 0) {
    console.error(`❌ [${ns}] Missing in German:`, missingInDe);
    hasErrors = true;
  }
});

if (hasErrors) {
  process.exit(1);
} else {
  console.log('✅ All translation keys are in sync between de and en.');
  process.exit(0);
}
```

**GitHub Actions CI Check**:
```yaml
# .github/workflows/ci.yml
- name: Validate Translations
  run: npm run validate:translations
```

### Testing Strategy

**Translation Key Tests**:
```typescript
// __tests__/i18n/translations.test.ts
import { describe, it, expect } from 'vitest';
import commonDe from '@/locales/de/common.json';
import commonEn from '@/locales/en/common.json';

describe('Translation Completeness', () => {
  it('should have matching keys in de and en', () => {
    const deKeys = flattenKeys(commonDe);
    const enKeys = flattenKeys(commonEn);

    expect(deKeys).toEqual(enKeys);
  });
});
```

**Component Translation Tests**:
```typescript
// __tests__/components/LoginForm.test.tsx
import { render, screen } from '@testing-library/react';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import LoginForm from '@/components/auth/LoginForm';

describe('LoginForm i18n', () => {
  it('renders in German by default', async () => {
    await i18n.changeLanguage('de');
    render(
      <I18nextProvider i18n={i18n}>
        <LoginForm />
      </I18nextProvider>
    );

    expect(screen.getByText('Anmelden')).toBeInTheDocument();
  });

  it('renders in English when language is changed', async () => {
    await i18n.changeLanguage('en');
    render(
      <I18nextProvider i18n={i18n}>
        <LoginForm />
      </I18nextProvider>
    );

    expect(screen.getByText('Sign In')).toBeInTheDocument();
  });
});
```

**E2E Language Switching Tests** (Playwright):
```typescript
// e2e/i18n.spec.ts
import { test, expect } from '@playwright/test';

test('should switch language from German to English', async ({ page }) => {
  await page.goto('/');

  // Verify default German
  await expect(page.locator('h1')).toHaveText('Willkommen bei BATbern');

  // Switch to English
  await page.selectOption('[data-testid="language-switcher"]', 'en');

  // Verify English
  await expect(page.locator('h1')).toHaveText('Welcome to BATbern');

  // Verify persistence (reload page)
  await page.reload();
  await expect(page.locator('h1')).toHaveText('Welcome to BATbern');
});
```

### Accessibility Considerations

**Screen Reader Support**:
```typescript
// Update HTML lang attribute and announce change
const handleLanguageChange = async (newLang: 'de' | 'en') => {
  await i18n.changeLanguage(newLang);
  document.documentElement.lang = newLang;

  // Announce language change to screen readers
  const announcement = document.createElement('div');
  announcement.setAttribute('role', 'status');
  announcement.setAttribute('aria-live', 'polite');
  announcement.textContent = newLang === 'de'
    ? 'Sprache wurde auf Deutsch geändert'
    : 'Language changed to English';
  document.body.appendChild(announcement);
  setTimeout(() => announcement.remove(), 1000);
};
```

**Keyboard Navigation**:
- Language switcher fully keyboard accessible
- Focus management maintained during language switch

### Performance Optimization

**Bundle Size Management**:
- All translations bundled at build time (~50KB total for de + en)
- Vite tree-shaking removes unused namespaces
- Common namespace loaded immediately (~5KB)
- Role-specific namespaces lazy-loaded on route navigation

**Caching**:
- Language preference cached in localStorage
- Translations cached by browser (static assets with cache headers)

**Initial Load Performance**:
```
Initial bundle: common.json (de) + auth.json (de) = ~8KB
On role switch: Load role namespace = ~5KB additional
On language switch: Load en namespaces = ~25KB additional (lazy)
```
