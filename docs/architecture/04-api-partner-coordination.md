# Partner Coordination API

This document outlines the Partner Coordination Domain API, which handles partner relationship management, strategic topic voting, partner meeting coordination, and partnership lifecycle management.

## Overview

The Partner Coordination API provides endpoints for:
- Partner profile and relationship management
- Strategic topic voting and suggestions
- Partner meeting scheduling and coordination
- Topic voting and prioritization
- Partner analytics and ROI tracking

## API Endpoints

### Partner Management

(Note: While the original API design focused heavily on overflow management which is in the Event Management domain, this section would typically include partner-specific endpoints. The notification endpoints below are cross-cutting but included here for completeness.)

### Notification Management

#### List Email Templates

```yaml
GET /api/v1/notifications/templates
tags: [Notifications]
summary: List email templates
security:
  - BearerAuth: [organizer]
parameters:
  - name: templateType
    in: query
    schema:
      $ref: '#/components/schemas/TemplateType'
  - name: language
    in: query
    schema:
      type: string
      enum: [en, de]
responses:
  '200':
    description: List of email templates
    content:
      application/json:
        schema:
          type: array
          items:
            $ref: '#/components/schemas/EmailTemplate'
```

#### Create Email Template

```yaml
POST /api/v1/notifications/templates
tags: [Notifications]
summary: Create new email template
security:
  - BearerAuth: [organizer]
requestBody:
  required: true
  content:
    application/json:
      schema:
        $ref: '#/components/schemas/CreateTemplateRequest'
responses:
  '201':
    description: Template created
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/EmailTemplate'
```

#### Get Template Version History

```yaml
GET /api/v1/notifications/templates/{templateId}/versions
tags: [Notifications]
summary: Get template version history
security:
  - BearerAuth: [organizer]
parameters:
  - name: templateId
    in: path
    required: true
    schema:
      type: string
      format: uuid
responses:
  '200':
    description: Template version history
    content:
      application/json:
        schema:
          type: array
          items:
            $ref: '#/components/schemas/EmailTemplate'
```

#### Rollback Template to Previous Version

```yaml
POST /api/v1/notifications/templates/{templateId}/rollback
tags: [Notifications]
summary: Rollback template to previous version
security:
  - BearerAuth: [organizer]
parameters:
  - name: templateId
    in: path
    required: true
    schema:
      type: string
      format: uuid
requestBody:
  required: true
  content:
    application/json:
      schema:
        type: object
        required:
          - targetVersion
        properties:
          targetVersion:
            type: integer
            description: Version number to rollback to
          reason:
            type: string
            maxLength: 500
            description: Reason for rollback
responses:
  '200':
    description: Template rolled back successfully
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/EmailTemplate'
```

#### Configure A/B Test for Template

```yaml
POST /api/v1/notifications/templates/{templateId}/ab-test
tags: [Notifications]
summary: Configure A/B test variants for template
security:
  - BearerAuth: [organizer]
parameters:
  - name: templateId
    in: path
    required: true
    schema:
      type: string
      format: uuid
requestBody:
  required: true
  content:
    application/json:
      schema:
        type: object
        required:
          - variants
        properties:
          variants:
            type: array
            items:
              type: object
              properties:
                variantId:
                  type: string
                  example: "A"
                weight:
                  type: integer
                  minimum: 0
                  maximum: 100
                  example: 50
                subject:
                  type: string
                bodyHtml:
                  type: string
          duration:
            type: integer
            description: Test duration in days
            minimum: 1
            maximum: 30
responses:
  '201':
    description: A/B test configured successfully
    content:
      application/json:
        schema:
          type: object
          properties:
            testId:
              type: string
              format: uuid
            variants:
              type: array
              items:
                $ref: '#/components/schemas/EmailTemplate'
```

#### Get User Notification Preferences

```yaml
GET /api/v1/notifications/preferences
tags: [Notifications]
summary: Get user notification preferences
responses:
  '200':
    description: User preferences
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/NotificationPreferences'
```

#### Update Notification Preferences

```yaml
PUT /api/v1/notifications/preferences
tags: [Notifications]
summary: Update notification preferences
requestBody:
  required: true
  content:
    application/json:
      schema:
        $ref: '#/components/schemas/UpdatePreferencesRequest'
responses:
  '200':
    description: Preferences updated
```

#### List Escalation Rules

```yaml
GET /api/v1/notifications/escalation-rules
tags: [Notifications]
summary: List escalation rules
security:
  - BearerAuth: [organizer]
responses:
  '200':
    description: List of escalation rules
    content:
      application/json:
        schema:
          type: array
          items:
            $ref: '#/components/schemas/EscalationRule'
```

#### Create Escalation Rule

```yaml
POST /api/v1/notifications/escalation-rules
tags: [Notifications]
summary: Create escalation rule
security:
  - BearerAuth: [organizer]
requestBody:
  required: true
  content:
    application/json:
      schema:
        $ref: '#/components/schemas/CreateEscalationRuleRequest'
responses:
  '201':
    description: Escalation rule created
```

## Schemas

### Partner Analytics

```yaml
PartnerAnalytics:
  type: object
  properties:
    totalEmployeeAttendance:
      type: integer
      description: Total number of partner employees who attended events
    averageAttendancePerEvent:
      type: number
      format: double
      description: Average employee attendance per event
    contentEngagementScore:
      type: number
      format: double
      description: Engagement score based on content access and downloads
    brandExposureMetrics:
      type: object
      properties:
        logoDisplayCount:
          type: integer
        websiteMentions:
          type: integer
        emailNewsletterReach:
          type: integer
    roiCalculations:
      type: object
      properties:
        estimatedReach:
          type: integer
        costPerImpression:
          type: number
          format: double
        totalInvestment:
          type: number
          format: double
        estimatedValue:
          type: number
          format: double
```

### Notification Templates

```yaml
EmailTemplate:
  type: object
  properties:
    id:
      type: string
      format: uuid
    templateType:
      $ref: '#/components/schemas/TemplateType'
    language:
      type: string
      enum: [en, de]
    version:
      type: integer
      description: Template version number (auto-incremented)
    isActive:
      type: boolean
      description: Whether this version is currently active
      default: true
    subject:
      type: string
    bodyHtml:
      type: string
    bodyText:
      type: string
    variables:
      type: array
      items:
        type: string
      description: List of template variables (e.g., {{speakerName}}, {{eventTitle}})
    abTestConfig:
      type: object
      description: A/B testing configuration for this template
      properties:
        enabled:
          type: boolean
          default: false
        variantId:
          type: string
          description: Unique identifier for this variant (A, B, C, etc.)
        weight:
          type: integer
          minimum: 0
          maximum: 100
          description: Percentage of recipients to receive this variant
        metrics:
          type: object
          properties:
            sentCount:
              type: integer
            openRate:
              type: number
              format: double
            clickRate:
              type: number
              format: double
            conversionRate:
              type: number
              format: double
    previousVersionId:
      type: string
      format: uuid
      description: Reference to previous template version for history
    createdAt:
      type: string
      format: date-time
    createdBy:
      type: string
      format: uuid
    updatedAt:
      type: string
      format: date-time
    updatedBy:
      type: string
      format: uuid

TemplateType:
  type: string
  enum:
    - speaker_invitation
    - speaker_confirmation
    - speaker_reminder
    - event_announcement
    - registration_confirmation
    - partner_report
    - organizer_notification
    - quality_review_feedback
    - overflow_notification
```

### Notification Preferences

```yaml
NotificationPreferences:
  type: object
  properties:
    userId:
      type: string
      format: uuid
    channels:
      type: object
      description: Per-channel notification preferences
      properties:
        email:
          type: object
          properties:
            enabled:
              type: boolean
              default: true
            frequency:
              type: string
              enum: [immediate, daily_digest, weekly_digest]
              default: immediate
        inApp:
          type: object
          properties:
            enabled:
              type: boolean
              default: true
            soundEnabled:
              type: boolean
              default: false
        push:
          type: object
          properties:
            enabled:
              type: boolean
              default: false
            deviceTokens:
              type: array
              items:
                type: string
        sms:
          type: object
          properties:
            enabled:
              type: boolean
              default: false
            phoneNumber:
              type: string
            criticalOnly:
              type: boolean
              default: true
    quietHours:
      type: object
      description: Do not disturb settings
      properties:
        enabled:
          type: boolean
          default: false
        startTime:
          type: string
          pattern: '^([01]?[0-9]|2[0-3]):[0-5][0-9]$'
          example: "22:00"
          description: Start time in HH:MM format (24-hour)
        endTime:
          type: string
          pattern: '^([01]?[0-9]|2[0-3]):[0-5][0-9]$'
          example: "08:00"
          description: End time in HH:MM format (24-hour)
        timezone:
          type: string
          example: "Europe/Zurich"
          description: IANA timezone identifier
        allowCritical:
          type: boolean
          default: true
          description: Allow critical notifications during quiet hours
    notificationTypes:
      type: object
      description: Granular control per notification type across all channels
      properties:
        speakerInvitations:
          $ref: '#/components/schemas/NotificationTypePreference'
        eventUpdates:
          $ref: '#/components/schemas/NotificationTypePreference'
        deadlineReminders:
          $ref: '#/components/schemas/NotificationTypePreference'
        qualityReviewFeedback:
          $ref: '#/components/schemas/NotificationTypePreference'
        organizerAlerts:
          $ref: '#/components/schemas/NotificationTypePreference'
        partnerCommunications:
          $ref: '#/components/schemas/NotificationTypePreference'
        systemAlerts:
          $ref: '#/components/schemas/NotificationTypePreference'

NotificationTypePreference:
  type: object
  description: Per-type notification channel preferences
  properties:
    email:
      type: boolean
      default: true
    inApp:
      type: boolean
      default: true
    push:
      type: boolean
      default: false
    sms:
      type: boolean
      default: false
```

### Escalation Rules

```yaml
EscalationRule:
  type: object
  properties:
    id:
      type: string
      format: uuid
    eventId:
      type: string
      format: uuid
      description: Specific event ID (null for global rules)
    triggerType:
      type: string
      enum:
        - deadline_approaching
        - deadline_missed
        - quality_review_pending
        - speaker_response_overdue
        - slot_assignment_pending
    thresholdHours:
      type: integer
      description: Hours before/after trigger condition
    escalationLevel:
      type: string
      enum: [organizer, organizer_team, admin]
    notificationChannels:
      type: array
      items:
        type: string
        enum: [email, real_time, sms]
    active:
      type: boolean
      default: true
    createdAt:
      type: string
      format: date-time
```

### Create Template Request

```yaml
CreateTemplateRequest:
  type: object
  required:
    - templateType
    - language
    - subject
    - bodyHtml
  properties:
    templateType:
      $ref: '#/components/schemas/TemplateType'
    language:
      type: string
      enum: [en, de]
    subject:
      type: string
      maxLength: 200
    bodyHtml:
      type: string
    bodyText:
      type: string
    variables:
      type: array
      items:
        type: string
```

### Create Escalation Rule Request

```yaml
CreateEscalationRuleRequest:
  type: object
  required:
    - triggerType
    - thresholdHours
    - escalationLevel
  properties:
    eventId:
      type: string
      format: uuid
    triggerType:
      type: string
      enum:
        - deadline_approaching
        - deadline_missed
        - quality_review_pending
        - speaker_response_overdue
        - slot_assignment_pending
    thresholdHours:
      type: integer
      minimum: 1
    escalationLevel:
      type: string
      enum: [organizer, organizer_team, admin]
    notificationChannels:
      type: array
      items:
        type: string
        enum: [email, real_time, sms]
```

### Update Preferences Request

```yaml
UpdatePreferencesRequest:
  type: object
  description: Update notification preferences (partial update supported)
  properties:
    channels:
      type: object
      properties:
        email:
          type: object
          properties:
            enabled:
              type: boolean
            frequency:
              type: string
              enum: [immediate, daily_digest, weekly_digest]
        inApp:
          type: object
          properties:
            enabled:
              type: boolean
            soundEnabled:
              type: boolean
        push:
          type: object
          properties:
            enabled:
              type: boolean
            deviceTokens:
              type: array
              items:
                type: string
        sms:
          type: object
          properties:
            enabled:
              type: boolean
            phoneNumber:
              type: string
            criticalOnly:
              type: boolean
    quietHours:
      type: object
      properties:
        enabled:
          type: boolean
        startTime:
          type: string
          pattern: '^([01]?[0-9]|2[0-3]):[0-5][0-9]$'
        endTime:
          type: string
          pattern: '^([01]?[0-9]|2[0-3]):[0-5][0-9]$'
        timezone:
          type: string
        allowCritical:
          type: boolean
    notificationTypes:
      type: object
      description: Per-notification-type channel preferences
      additionalProperties:
        $ref: '#/components/schemas/NotificationTypePreference'
```

## Partner Coordination Features

### Strategic Topic Voting

Partners can provide strategic input on event topics through:
- **Topic Suggestions**: Partners suggest topics aligned with industry trends
- **Topic Voting**: Partners vote on proposed topics for upcoming events
- **Priority Ranking**: Partners rank topics by strategic importance
- **Feedback Loop**: Partners receive updates on topic selection outcomes

### Meeting Coordination

Partner meetings are coordinated through:
- **Meeting Scheduling**: Organizers schedule strategic planning meetings with partners
- **Availability Collection**: Partners submit availability preferences
- **Meeting Reminders**: Automated reminders sent before scheduled meetings
- **Meeting Notes**: Shared meeting notes and action items

### Partnership Analytics

Partners receive comprehensive analytics including:
- **Attendance Metrics**: Employee attendance across all events
- **Engagement Tracking**: Content downloads and interaction metrics
- **Brand Exposure**: Logo displays, mentions, and promotional reach
- **ROI Calculations**: Estimated value vs. partnership investment

### Notification System

The notification system supports partner coordination through:
- **Multi-channel Delivery**: Email, real-time web notifications, SMS
- **Preference Management**: Partners control notification frequency and channels
- **Template Customization**: Branded email templates for partner communications
- **Escalation Rules**: Automated escalation for time-sensitive partner matters
