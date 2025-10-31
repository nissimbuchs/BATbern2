/**
 * Event Management Dashboard Page
 * Story 2.5.3, Task 4: Navigation Integration & Routing
 * Updated Task 8b: Connected to actual dashboard component
 *
 * Main event management dashboard for organizers.
 * Displays active events pipeline, critical tasks, and team activity.
 */

import React from 'react';
import { EventManagementDashboard as DashboardComponent } from '@/components/organizer/EventManagement';

const EventManagementDashboard: React.FC = () => {
  return <DashboardComponent />;
};

export default EventManagementDashboard;
