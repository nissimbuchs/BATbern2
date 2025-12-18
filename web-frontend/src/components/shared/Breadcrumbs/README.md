# Breadcrumbs Component

Reusable breadcrumb navigation component providing contextual navigation trails across the BATbern platform.

## Features

- ✅ **Clickable Navigation Links**: Automatically handles routing with React Router
- ✅ **Automatic Home Icon**: First breadcrumb item displays a home icon
- ✅ **Current Page Indicator**: Last item or items without paths are non-clickable
- ✅ **i18n Support**: Fully compatible with react-i18next translations
- ✅ **Accessible**: Proper ARIA labels and semantic HTML
- ✅ **Consistent Styling**: Uses MUI components with NavigateNext separator

## Requirements

Based on Story 1.17 (React Frontend Foundation):

- Required for **Organizer** role
- Optional for other roles
- Shows contextual location in app hierarchy
- All intermediate items are clickable links

## Installation

The component is already available in the shared components directory:

```tsx
import { Breadcrumbs } from '@/components/shared/Breadcrumbs';
import type { BreadcrumbItem } from '@/components/shared/Breadcrumbs';
```

## Basic Usage

### Simple Breadcrumb Trail

```tsx
import { Breadcrumbs } from '@/components/shared/Breadcrumbs';

function MyPage() {
  const breadcrumbItems = [
    { label: 'Home', path: '/organizer/events' },
    { label: 'Events', path: '/organizer/events' },
    { label: 'Current Page' }, // No path = current page (not clickable)
  ];

  return <Breadcrumbs items={breadcrumbItems} />;
}
```

### With i18n Translation

```tsx
import { useTranslation } from 'react-i18next';
import { Breadcrumbs } from '@/components/shared/Breadcrumbs';

function MyPage() {
  const { t } = useTranslation('organizer');

  const breadcrumbItems = [
    { label: t('breadcrumbs.home'), path: '/organizer/events' },
    { label: t('breadcrumbs.events'), path: '/organizer/events' },
    { label: t('breadcrumbs.currentPage') },
  ];

  return <Breadcrumbs items={breadcrumbItems} />;
}
```

### Dynamic Breadcrumbs (with Event Context)

```tsx
import { useEvent } from '@/hooks/useEvents';
import { Breadcrumbs } from '@/components/shared/Breadcrumbs';

function EventDetailPage({ eventCode }: { eventCode: string }) {
  const { data: eventData } = useEvent(eventCode);
  const { t } = useTranslation();

  const breadcrumbItems = eventData
    ? [
        { label: t('breadcrumbs.home'), path: '/organizer/events' },
        { label: eventData.title, path: `/organizer/events/${eventCode}` },
        { label: t('breadcrumbs.edit') },
      ]
    : [
        { label: t('breadcrumbs.home'), path: '/organizer/events' },
        { label: t('breadcrumbs.loading') },
      ];

  return <Breadcrumbs items={breadcrumbItems} />;
}
```

### Custom Margin and Aria Label

```tsx
<Breadcrumbs items={breadcrumbItems} marginBottom={5} ariaLabel="custom navigation breadcrumb" />
```

## API Reference

### BreadcrumbItem

```typescript
interface BreadcrumbItem {
  /** Display label for the breadcrumb */
  label: string;

  /** Navigation path (if undefined, item is not clickable - represents current page) */
  path?: string;

  /** Optional icon to display before label (only for first item if not provided) */
  icon?: React.ReactNode;
}
```

### BreadcrumbsProps

```typescript
interface BreadcrumbsProps {
  /** Array of breadcrumb items to display */
  items: BreadcrumbItem[];

  /** Optional aria-label for accessibility (defaults to "breadcrumb") */
  ariaLabel?: string;

  /** Optional margin bottom spacing (defaults to 3) */
  marginBottom?: number;
}
```

## Implementation Examples

### 1. Topic Backlog Manager

```tsx
// With event context
const breadcrumbItems =
  eventCode && eventData
    ? [
        { label: t('breadcrumbs.home'), path: '/organizer/events' },
        { label: eventData.title, path: `/organizer/events/${eventCode}` },
        { label: t('breadcrumbs.topicSelection') },
      ]
    : [
        { label: t('breadcrumbs.home'), path: '/organizer/events' },
        { label: t('breadcrumbs.manageTopics') },
      ];

<Breadcrumbs items={breadcrumbItems} />;
```

### 2. Event Types Management

```tsx
const breadcrumbItems = [
  { label: t('breadcrumbs.home'), path: '/organizer/events' },
  { label: t('breadcrumbs.settings'), path: '/organizer/settings' },
  { label: t('breadcrumbs.eventTypes') },
];

<Breadcrumbs items={breadcrumbItems} />;
```

### 3. Event Detail View

```tsx
const breadcrumbItems = [
  { label: t('breadcrumbs.home'), path: '/organizer/events' },
  { label: event.title, path: `/organizer/events/${event.eventCode}` },
  { label: t('breadcrumbs.edit') },
];

<Breadcrumbs items={breadcrumbItems} />;
```

## Styling

The component uses MUI's Breadcrumbs component with:

- **Separator**: NavigateNext icon
- **Link Color**: `inherit` (adapts to theme)
- **Current Page Color**: `text.primary`
- **Hover Effect**: Underline on hover
- **Home Icon**: Automatically added to first item

## Accessibility

- Proper semantic HTML using `<nav>` and `<ol>`
- ARIA label for screen readers
- Keyboard navigation support (tab, enter)
- Clear visual distinction between clickable and non-clickable items

## Testing

Run tests with:

```bash
npm test -- Breadcrumbs.test.tsx
```

Test coverage includes:

- ✅ Rendering breadcrumb items
- ✅ Home icon display on first item
- ✅ Navigation on link click
- ✅ Non-clickable last item
- ✅ Non-clickable items without paths
- ✅ Custom margin bottom
- ✅ Custom aria-label

## Migration Guide

If you have existing inline breadcrumb implementations, refactor them to use this component:

**Before:**

```tsx
<MuiBreadcrumbs separator={<NavigateNextIcon />}>
  <Link onClick={() => navigate('/home')}>
    <HomeIcon /> Home
  </Link>
  <Link onClick={() => navigate('/events')}>Events</Link>
  <Typography>Current</Typography>
</MuiBreadcrumbs>
```

**After:**

```tsx
<Breadcrumbs
  items={[
    { label: 'Home', path: '/home' },
    { label: 'Events', path: '/events' },
    { label: 'Current' },
  ]}
/>
```

## Related Documentation

- [Story 1.17 - React Frontend Foundation](/docs/stories/1.17.react-frontend-foundation.md)
- [Front-end Spec - Navigation](/docs/front-end-spec.md)
- [Wireframe - Main Navigation](/docs/wireframes/story-1.17-main-navigation.md)
