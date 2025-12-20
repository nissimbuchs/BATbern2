# i18n Translation Pattern

**Category**: Frontend - Internationalization
**Used in Stories**: 2.5.3 (Event Management Frontend)
**Last Updated**: 2025-12-20
**Source**: Extracted from Story 2.5.3

## Overview

Implement multi-language support using react-i18next with type-safe translation keys and namespace organization.

**Use this pattern when**:
- Supporting multiple languages (German/English)
- Organizing translations by feature domain
- Implementing type-safe translation keys
- Managing locale-specific formatting (dates, numbers, currency)

## Prerequisites

```bash
npm install react-i18next i18next
npm install date-fns  # for locale-aware date formatting
```

## Implementation Steps

### Step 1: Translation File Structure

Organize translations by namespace (feature domain):

```
public/locales/
├── de/                    # German translations
│   ├── common.json        # Shared UI labels
│   ├── auth.json          # Authentication
│   ├── validation.json    # Validation errors
│   └── {domain}.json      # Domain-specific (events, partners, etc.)
└── en/                    # English translations
    ├── common.json
    ├── auth.json
    ├── validation.json
    └── {domain}.json
```

### Step 2: Translation File Format

```json
{
  "navigation": {
    "dashboard": "Events",
    "createEvent": "Create Event",
    "eventTimeline": "Timeline"
  },
  "dashboard": {
    "title": "Event Management",
    "activeEvents": "Active Events"
  },
  "form": {
    "createEvent": "Create New Event",
    "title": "Title",
    "save": "Save",
    "cancel": "Cancel"
  },
  "validation": {
    "titleRequired": "Title is required",
    "titleMinLength": "Title must be at least {{min}} characters"
  },
  "errors": {
    "loadFailed": "Failed to load events",
    "notFound": "Event not found",
    "correlationId": "Correlation ID"
  },
  "confirmations": {
    "deleteTitle": "Delete Event?",
    "deleteMessage": "Are you sure you want to delete this event?",
    "deleteImpact": "{{registrations}} registrations, {{speakers}} speakers",
    "confirm": "Confirm",
    "cancel": "Cancel"
  }
}
```

### Step 3: i18n Configuration

```typescript
// src/i18n/config.ts
import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';

// Import translation files
import enCommon from '../../public/locales/en/common.json';
import deCommon from '../../public/locales/de/common.json';
import enEvents from '../../public/locales/en/events.json';
import deEvents from '../../public/locales/de/events.json';

i18n
  .use(LanguageDetector)  // Detect user language
  .use(initReactI18next)  // Pass i18n to react-i18next
  .init({
    resources: {
      en: {
        common: enCommon,
        events: enEvents,
      },
      de: {
        common: deCommon,
        events: deEvents,
      },
    },
    fallbackLng: 'en',
    defaultNS: 'common',
    ns: ['common', 'events'],  // Available namespaces
    interpolation: {
      escapeValue: false,  // React already escapes
    },
  });

export default i18n;
```

### Step 4: Use Translations in Components

```typescript
import { useTranslation } from 'react-i18next';

export const EventManagementDashboard: React.FC = () => {
  const { t } = useTranslation('events');  // Load 'events' namespace

  return (
    <Container>
      <Typography variant="h4">{t('dashboard.title')}</Typography>
      <Button>{t('form.createEvent')}</Button>

      {/* With interpolation */}
      <Typography>
        {t('validation.titleMinLength', { min: 10 })}
      </Typography>

      {/* With pluralization */}
      <Typography>
        {t('dashboard.eventCount', { count: events.length })}
      </Typography>
    </Container>
  );
};
```

### Step 5: Language Switcher Component

```typescript
import { useTranslation } from 'react-i18next';
import { MenuItem, Select } from '@mui/material';

export const LanguageSwitcher: React.FC = () => {
  const { i18n } = useTranslation();

  const changeLanguage = (lng: string) => {
    i18n.changeLanguage(lng);
    localStorage.setItem('preferredLanguage', lng);  // Persist choice
  };

  return (
    <Select
      value={i18n.language}
      onChange={(e) => changeLanguage(e.target.value)}
    >
      <MenuItem value="en">English</MenuItem>
      <MenuItem value="de">Deutsch</MenuItem>
    </Select>
  );
};
```

## Advanced Patterns

### Locale-Specific Formatting

```typescript
import { format } from 'date-fns';
import { de, enUS } from 'date-fns/locale';
import { useTranslation } from 'react-i18next';

export const DateDisplay: React.FC<{ date: Date }> = ({ date }) => {
  const { i18n } = useTranslation();

  const locale = i18n.language === 'de' ? de : enUS;

  return (
    <Typography>
      {format(date, 'PPP', { locale })}  // Locale-aware format
    </Typography>
  );
};

// Number/Currency Formatting
export const PriceDisplay: React.FC<{ amount: number }> = ({ amount }) => {
  const { i18n } = useTranslation();

  const formatted = new Intl.NumberFormat(i18n.language, {
    style: 'currency',
    currency: 'CHF',  // Swiss Franc
  }).format(amount);

  return <Typography>{formatted}</Typography>;  // CHF 1'500.50 (de) or CHF 1,500.50 (en)
};
```

### Type-Safe Translation Keys

```typescript
// src/types/i18n.d.ts
import 'react-i18next';
import enEvents from '../../public/locales/en/events.json';

declare module 'react-i18next' {
  interface CustomTypeOptions {
    defaultNS: 'common';
    resources: {
      events: typeof enEvents;
    };
  }
}

// Now TypeScript autocomplete works for translation keys:
const { t } = useTranslation('events');
t('dashboard.title');  // ✅ Autocomplete + Type checking
t('dashboard.invalid');  // ❌ TypeScript error
```

## Common Pitfalls

### Pitfall 1: Hardcoded Strings
**Problem**: Text not translated, inconsistent with other languages
**Solution**: All UI text must use `t()` function

```typescript
// ❌ WRONG - hardcoded string
<Button>Create Event</Button>

// ✅ CORRECT - translation key
<Button>{t('form.createEvent')}</Button>
```

### Pitfall 2: Forgetting Namespace
**Problem**: Translation keys not found, shows key instead of text
**Solution**: Specify namespace when using `useTranslation`

```typescript
// ❌ WRONG - missing namespace
const { t } = useTranslation();
t('dashboard.title');  // Looks in 'common' namespace

// ✅ CORRECT - specify namespace
const { t } = useTranslation('events');
t('dashboard.title');  // Looks in 'events' namespace
```

### Pitfall 3: Not Handling Pluralization
**Problem**: Showing "1 events" instead of "1 event"
**Solution**: Use plural keys

```json
{
  "eventCount": "{{count}} event",
  "eventCount_plural": "{{count}} events"
}
```

```typescript
t('eventCount', { count: 1 });  // "1 event"
t('eventCount', { count: 5 });  // "5 events"
```

## Story-Specific Adaptations

### Story 2.5.3: Event Management Translations

**Namespace**: `events`
**Files**: `public/locales/de/events.json`, `public/locales/en/events.json`

**Key Sections**:
- `navigation`: Menu items (dashboard, createEvent, eventTimeline)
- `dashboard`: Dashboard labels (title, activeEvents, criticalTasks, teamActivity)
- `form`: Form labels and buttons (createEvent, title, description, save, cancel)
- `validation`: Validation error messages (titleRequired, titleMinLength, eventDateFuture)
- `errors`: API error messages (loadFailed, notFound, unauthorized, correlationId)
- `confirmations`: Confirmation dialogs (deleteTitle, deleteMessage, deleteImpact)
- `workflow`: Workflow labels (progress, step, viewDetails)
- `autoSave`: Auto-save indicators (saving, saved, failed, conflict)

**Total Keys**: 220+ translation keys covering all Event Management UI

## Related Templates

- `react-component-pattern.md` - Component structure with i18n
- `form-validation-pattern.md` - Translating validation errors

## References

- **react-i18next Docs**: https://react.i18next.com/
- **Story 1.17**: React Frontend Foundation (i18n infrastructure)
- **Story 2.5.3**: Event Management Frontend (lines 960-1098)
