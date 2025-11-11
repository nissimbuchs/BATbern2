# Wireframe: Inline Registration Accordion (Hybrid Approach)

**Story**: Story 4.1.5 (Registration Wizard) + Story 4.1.6 (Confirmation)
**Screen**: Homepage with Expandable Registration + Dedicated Registration Page
**User Role**: Public Visitor (Anonymous Registration)
**Related**: FR6 (Registration), ADR-005 (Anonymous Registration)
**Design System**: shadcn/ui dark theme (Story 4.1.1), Hero section (Story 4.1.3)

---

## Overview

This wireframe demonstrates a **hybrid registration approach**:
1. **Primary UX**: Inline expandable registration within hero section (accordion-style 2-step wizard)
2. **Secondary UX**: Dedicated `/register/:eventCode` route using same components (for shareable links)

### Design Principles
- **Seamless Integration**: Registration blends into hero section without modal/popup
- **Accordion Pattern**: Step 1 collapses when Step 2 expands (compact, focused experience)
- **Dark Theme Consistency**: Maintains zinc-950 background, blue-400 accents from existing design
- **Progressive Disclosure**: Only show what's needed at each step
- **Component Reuse**: Same wizard components work inline and in dedicated page

---

## State 1: Hero Section - Default State (Collapsed)

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    [Unicorn.studio Interactive Background]                 │
│                                                                            │
│                                                                            │
│                                                                            │
│               ┌─────────────────────────────────────────┐                 │
│               │  🔵 Next Event • 12 days until event    │                 │
│               └─────────────────────────────────────────┘                 │
│                                                                            │
│                                                                            │
│                       Zero Trust Journey                                  │
│                  Cloud-Native Security at Scale                           │
│                                                                            │
│                    📅 March 15, 2025                                      │
│                    📍 Kursaal Bern                                        │
│                                                                            │
│                  ┌─────────────────────────┐                              │
│                  │  Register Now           │  ← Click to expand           │
│                  └─────────────────────────┘                              │
│                  (blue-400 button, rounded-lg)                            │
│                                                                            │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
                              ↓ scroll down ↓
┌────────────────────────────────────────────────────────────────────────────┐
│                                                                            │
│  Event Logistics (EventLogistics component)                               │
│  ┌────────────┬────────────┬────────────┬────────────┐                    │
│  │ 📅 Date    │ 🕐 Time    │ 📍 Location│ 👥 Capacity│                    │
│  │ Mar 15     │ 18:00-21:00│ Kursaal    │ 87 / 120   │                    │
│  └────────────┴────────────┴────────────┴────────────┘                    │
│                                                                            │
│  Event Program (Timeline, sessions, speakers...)                          │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

**Visual Characteristics:**
- Full-screen hero (`h-screen`)
- Unicorn.studio background (z-0 layer)
- Content centered and bottom-aligned (`items-end pb-16`)
- "Register Now" button has hover state (scale-105)
- Countdown timer badge floats above title

---

## State 2: Hero Section - Expanded with Step 1 (Registration Form Visible)

```
┌────────────────────────────────────────────────────────────────────────────┐
│            [Unicorn.studio Background - Semi-transparent overlay]          │
│                                                                            │
│               Zero Trust Journey                                          │
│               Cloud-Native Security at Scale                              │
│               📅 March 15, 2025  •  📍 Kursaal Bern                       │
│                                                                            │
│  ┌──────────────────────────────────────────────────────────────────────┐ │
│  │ ━━━━━━━━━━━━━━━━━━━━━●━━━━━━━━━━━━━━━━━━━━━━━━━━━○                  │ │
│  │        1. Your Details (Current)         2. Confirm Registration      │ │
│  └──────────────────────────────────────────────────────────────────────┘ │
│                                                                            │
│  ┌─────────────────────── STEP 1: YOUR DETAILS ───────────────────────┐   │
│  │ (Accordion - EXPANDED, height: auto, opacity: 1)                    │   │
│  │                                                                      │   │
│  │  Personal Information                                                │   │
│  │                                                                      │   │
│  │  First Name *                    Last Name *                         │   │
│  │  ┌─────────────────────┐        ┌─────────────────────┐             │   │
│  │  │                     │        │                     │             │   │
│  │  └─────────────────────┘        └─────────────────────┘             │   │
│  │  (shadcn Input component, zinc-900 bg, zinc-100 text)               │   │
│  │                                                                      │   │
│  │  Email Address *                                                     │   │
│  │  ┌──────────────────────────────────────────────────────┐           │   │
│  │  │                                                      │           │   │
│  │  └──────────────────────────────────────────────────────┘           │   │
│  │  ℹ️ We'll send your ticket and event updates here                   │   │
│  │  (zinc-500 helper text, text-sm)                                    │   │
│  │                                                                      │   │
│  │  Company *                       Role *                              │   │
│  │  ┌─────────────────────┐        ┌─────────────────────┐             │   │
│  │  │                     │        │                     │             │   │
│  │  └─────────────────────┘        └─────────────────────┘             │   │
│  │                                                                      │   │
│  │  ❗ First Name is required                                           │   │
│  │  (Validation error: red-400 text, only shows on blur if invalid)    │   │
│  │                                                                      │   │
│  │  [Cancel]                                         [Next: Review →]  │   │
│  │  (zinc-800 outline)                              (blue-400 solid)   │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                            │
│  ┌─────────────────── STEP 2: CONFIRM REGISTRATION ───────────────────┐   │
│  │ (Accordion - COLLAPSED, height: 0, opacity: 0, display: none)       │   │
│  │ ... content hidden ...                                               │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
                              ↓ smooth height transition (500ms)
```

**Interaction Flow:**
1. User clicks "Register Now" button
2. Button fades out (200ms)
3. Hero section expands smoothly (500ms ease-in-out)
4. Step 1 form fades in (300ms with 200ms delay)
5. Unicorn.studio background dims slightly (opacity: 0.7)
6. Page scrolls to keep form in view (auto-scroll)

**Form Behavior:**
- **Validation**: Real-time validation with react-hook-form + zod
- **Error Display**: Inline errors below fields (red-400 text)
- **Required Fields**: Asterisk (*) in label
- **Helper Text**: Light gray (zinc-500) below inputs

---

## State 3: Step 1 → Step 2 Accordion Transition

```
┌────────────────────────────────────────────────────────────────────────────┐
│            [Unicorn.studio Background - Semi-transparent overlay]          │
│                                                                            │
│               Zero Trust Journey                                          │
│               Cloud-Native Security at Scale                              │
│               📅 March 15, 2025  •  📍 Kursaal Bern                       │
│                                                                            │
│  ┌──────────────────────────────────────────────────────────────────────┐ │
│  │ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━●━━━━━━━━━━━━━━━━━━━━━━━━━━━ │ │
│  │        1. Your Details                   2. Confirm Registration (Current) │ │
│  └──────────────────────────────────────────────────────────────────────┘ │
│                                                                            │
│  ┌─────────────────────── STEP 1: YOUR DETAILS ───────────────────────┐   │
│  │ (Accordion - COLLAPSED, Collapsible component)                       │   │
│  │ ┌────────────────────────────────────────────────────────────────┐  │   │
│  │ │  ✓ Personal Information Complete                               │  │   │
│  │ │  John Smith • john.smith@techcorp.ch                           │  │   │
│  │ │  TechCorp AG • Senior Developer                 [Edit]         │  │   │
│  │ │  (zinc-800 bg, zinc-300 text, compact summary)                 │  │   │
│  │ └────────────────────────────────────────────────────────────────┘  │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                            │
│  ┌─────────────────── STEP 2: CONFIRM REGISTRATION ───────────────────┐   │
│  │ (Accordion - EXPANDED, height: auto, opacity: 1)                    │   │
│  │                                                                      │   │
│  │  Review Your Registration                                            │   │
│  │  ┌────────────────────────────────────────────────────────────────┐ │   │
│  │  │ Event:     Zero Trust Journey                                  │ │   │
│  │  │ Date:      March 15, 2025                                      │ │   │
│  │  │ Time:      18:00 - 21:00                                       │ │   │
│  │  │ Location:  Kursaal Bern                                        │ │   │
│  │  │ Price:     FREE (including coffee & networking)                │ │   │
│  │  │                                                                │ │   │
│  │  │ Attendee:  John Smith (john.smith@techcorp.ch)                │ │   │
│  │  │ Company:   TechCorp AG • Senior Developer                     │ │   │
│  │  │ (zinc-900/50 bg, border-zinc-800, rounded-lg, p-4)            │ │   │
│  │  └────────────────────────────────────────────────────────────────┘ │   │
│  │                                                                      │   │
│  │  Communication Preferences                                           │   │
│  │  ☑ Send me event reminders (1 week and 1 day before)                │   │
│  │  ☐ Subscribe to BATbern newsletter (monthly)                         │   │
│  │  (shadcn Checkbox, default: reminders=true, newsletter=false)        │   │
│  │                                                                      │   │
│  │  Special Requests (Optional)                                         │   │
│  │  ┌────────────────────────────────────────────────────────────────┐ │   │
│  │  │ Dietary requirements, accessibility needs, etc.                │ │   │
│  │  │                                                                │ │   │
│  │  │                                                                │ │   │
│  │  └────────────────────────────────────────────────────────────────┘ │   │
│  │  (shadcn Textarea, 4 rows, zinc-900 bg)                             │   │
│  │                                                                      │   │
│  │  Terms & Conditions                                                  │   │
│  │  ☑ I agree to the event terms and photo/video policy *               │   │
│  │  (Required checkbox, links to terms page)                            │   │
│  │                                                                      │   │
│  │  💡 Want to manage all your registrations?                           │   │
│  │     [Create a free account →]                                        │   │
│  │  (zinc-800 bg, blue-400 link, rounded-lg, p-3, mt-4)               │   │
│  │                                                                      │   │
│  │  [Cancel]  [← Back]                    [Complete Registration]      │   │
│  │  (outline)  (outline)                  (blue-400 solid, disabled    │   │
│  │                                          until terms checked)        │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

**Accordion Animation:**
- **Step 1 Collapse**: 300ms ease-out (height: auto → collapsed summary)
- **Step 2 Expand**: 300ms ease-in (height: 0 → auto), 100ms delay
- **Smooth Transition**: Uses shadcn Collapsible component with CSS transitions
- **Scroll Behavior**: Auto-scroll to keep Step 2 header visible

**Submit Button States:**
- **Disabled** (default): Gray, cursor-not-allowed, opacity-50
  - Condition: Terms checkbox unchecked
- **Enabled**: Blue-400, cursor-pointer, opacity-100
  - Condition: Terms checkbox checked
- **Loading**: "Completing Registration..." with spinner
  - Condition: API call in progress

---

## State 4: Registration Success → Redirect

```
[Immediately after successful submission]

Redirect to: /registration-confirmation/BAT-2025-000123

┌────────────────────────────────────────────────────────────────────────────┐
│  [Confetti animation - canvas-confetti package]                            │
│                                                                            │
│                    🎉 Registration Confirmed!                              │
│                                                                            │
│  ┌──────────────────────────────────────────────────────────────────────┐ │
│  │                                                                      │ │
│  │                    [QR Code - 200x200px]                             │ │
│  │                                                                      │ │
│  │         Confirmation Code: BAT-2025-000123                           │ │
│  │         (large, blue-400 text, centered)                             │ │
│  └──────────────────────────────────────────────────────────────────────┘ │
│                                                                            │
│  ℹ️ Confirmation email sent to john.smith@techcorp.ch                      │
│                                                                            │
│  What's Next?                                                              │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐              │
│  │ 📅 Add to      │  │ 💾 Download    │  │ 🔗 Share       │              │
│  │   Calendar     │  │   QR Code      │  │   Event        │              │
│  └────────────────┘  └────────────────┘  └────────────────┘              │
│                                                                            │
│  💡 Want to manage your registration and get personalized updates?         │
│     [Create Your Free Account →]                                           │
│  (prominent CTA for anonymous users to convert to full account)           │
│                                                                            │
│  [Register Another Person] (link back to /register/:eventCode)            │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

**Confirmation Page Features:**
- Confetti animation on page load (3 seconds)
- QR code for event check-in (generated server-side)
- Calendar export (.ics file download)
- Social sharing (LinkedIn, Twitter/X, Email)
- "Create account" CTA for anonymous users
- Shareable confirmation code

---

## Alternative Flow: Dedicated Registration Page

**Route**: `/register/:eventCode`

```
┌────────────────────────────────────────────────────────────────────────────┐
│  ← Back to Event                                                           │
│                                                                            │
│  ┌──────────────────────────────────────────────────────────────────────┐ │
│  │  Event Registration                                                   │ │
│  │  Zero Trust Journey • March 15, 2025 • Kursaal Bern                  │ │
│  │  (Mini event context, compact header, zinc-900 bg)                   │ │
│  └──────────────────────────────────────────────────────────────────────┘ │
│                                                                            │
│  ┌──────────────────────────────────────────────────────────────────────┐ │
│  │ ━━━━━━━━━━━━━━━━━━━━━●━━━━━━━━━━━━━━━━━━━━━━━━━━━○                  │ │
│  │        1. Your Details (Current)         2. Confirm Registration      │ │
│  └──────────────────────────────────────────────────────────────────────┘ │
│                                                                            │
│  [Same RegistrationAccordion component as inline version]                 │
│  [Step 1 and Step 2 components identical to inline flow]                  │
│                                                                            │
│  Key Differences:                                                          │
│  - No Unicorn.studio background (simple dark background)                  │
│  - Full-page layout (not hero section)                                    │
│  - "Back to Event" breadcrumb at top                                      │
│  - Max-width container (max-w-3xl, centered)                              │
│  - Uses PublicLayout wrapper                                              │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

**Use Cases for Dedicated Page:**
1. **Shareable Links**: Event organizers share `/register/BATbern025` directly
2. **Email CTAs**: Confirmation emails link to dedicated page
3. **Social Sharing**: LinkedIn/Twitter posts link to dedicated page
4. **Bookmarks**: Users can bookmark registration page
5. **Deep Linking**: Mobile apps can deep-link to registration

**Benefits of Hybrid Approach:**
- ✅ Inline registration reduces friction for homepage visitors
- ✅ Dedicated page supports marketing/sharing use cases
- ✅ Same components = no code duplication
- ✅ Consistent UX across both flows

---

## Mobile Responsive Design

### Mobile View (< 640px)

```
┌───────────────────────────┐
│ [Unicorn.studio BG]       │
│                           │
│   Zero Trust Journey      │
│   Cloud-Native Security   │
│                           │
│   📅 March 15, 2025       │
│   📍 Kursaal Bern         │
│                           │
│   ┌───────────────────┐   │
│   │ Register Now     │   │
│   └───────────────────┘   │
│                           │
└───────────────────────────┘
        ↓ expands ↓
┌───────────────────────────┐
│   Zero Trust Journey      │
│   📅 Mar 15 • 📍 Bern     │
│                           │
│ ━━━━●━━━━━━━━━━━━━━━○    │
│   1. Details   2. Confirm │
│                           │
│ ┌─ STEP 1: DETAILS ────┐ │
│ │                      │ │
│ │ First Name *         │ │
│ │ ┌──────────────────┐ │ │
│ │ │                  │ │ │
│ │ └──────────────────┘ │ │
│ │                      │ │
│ │ Last Name *          │ │
│ │ ┌──────────────────┐ │ │
│ │ │                  │ │ │
│ │ └──────────────────┘ │ │
│ │                      │ │
│ │ (Stacked layout)     │ │
│ │                      │ │
│ │ [Cancel] [Next →]    │ │
│ └──────────────────────┘ │
│                           │
└───────────────────────────┘
```

**Mobile Optimizations:**
- **Single Column**: All fields stack vertically
- **Full Width**: Inputs use full container width
- **Larger Touch Targets**: Buttons min-height 44px (iOS guideline)
- **Simplified Header**: Event title + date only (no description)
- **Sticky Progress Bar**: Progress indicator sticks to top on scroll
- **Auto-Scroll**: Form auto-scrolls to keep input in view (avoid keyboard overlap)

---

## Component Architecture

### Component Tree

```
HomePage
└── HeroSection [Enhanced with registration state]
    ├── Hero Content (default state)
    │   ├── Countdown Badge
    │   ├── Event Title
    │   ├── Event Date & Location
    │   └── "Register Now" Button
    └── RegistrationSection (expandable)
        ├── Registration Header
        │   ├── Event Mini Summary
        │   └── Progress Indicator
        └── RegistrationWizard
            └── RegistrationAccordion
                ├── PersonalDetailsStep (Collapsible)
                │   ├── Form Fields (shadcn Input)
                │   ├── Validation (react-hook-form + zod)
                │   └── Navigation Buttons
                └── ConfirmRegistrationStep (Collapsible)
                    ├── Summary Card
                    ├── Communication Preferences (shadcn Checkbox)
                    ├── Special Requests (shadcn Textarea)
                    ├── Terms Checkbox (shadcn Checkbox)
                    ├── Account Creation CTA
                    └── Navigation Buttons

RegistrationPage (Dedicated Route)
└── PublicLayout
    ├── Breadcrumb ("Back to Event")
    └── RegistrationWizard [Same component as inline]
        └── RegistrationAccordion [Reused]
```

### State Management

```typescript
// HeroSection.tsx state
const [isRegistrationExpanded, setIsRegistrationExpanded] = useState(false);

// RegistrationWizard.tsx state
const [currentStep, setCurrentStep] = useState(1); // 1 or 2
const [formData, setFormData] = useState({
  firstName: '',
  lastName: '',
  email: '',
  company: '',
  role: '',
  termsAccepted: false,
  communicationPreferences: {
    newsletterSubscribed: false,
    eventReminders: true,
  },
  specialRequests: '',
});
const [isSubmitting, setIsSubmitting] = useState(false);
```

---

## API Requirements

### Registration Flow APIs

1. **Check Duplicate Email** (optional, before submission)
   - `GET /api/v1/events/{eventCode}/registrations?filter[email]={email}`
   - Returns: 200 with empty array (OK) or 200 with registration (duplicate)
   - UI: Show warning if duplicate found

2. **Submit Registration**
   - `POST /api/v1/events/{eventCode}/registrations`
   - Payload:
     ```json
     {
       "email": "john.smith@techcorp.ch",
       "firstName": "John",
       "lastName": "Smith",
       "company": "TechCorp AG",
       "role": "Senior Developer",
       "termsAccepted": true,
       "communicationPreferences": {
         "newsletterSubscribed": false,
         "eventReminders": true
       },
       "specialRequests": "Vegetarian meal"
     }
     ```
   - Response (201 Created):
     ```json
     {
       "id": "reg_123",
       "confirmationCode": "BAT-2025-000123",
       "eventCode": "BATbern025",
       "status": "confirmed",
       "registrationDate": "2025-03-01T10:30:00Z",
       "firstName": "John",
       "lastName": "Smith",
       "email": "john.smith@techcorp.ch",
       "company": "TechCorp AG"
     }
     ```

3. **Get Registration for Confirmation Page**
   - `GET /api/v1/events/{eventCode}/registrations/{confirmationCode}`
   - Public access (no auth required)
   - Returns: Full registration details

4. **Get QR Code**
   - `GET /api/v1/events/{eventCode}/registrations/{confirmationCode}/qr`
   - Public access
   - Returns: PNG image (200x200px)

---

## Design System Integration

### Colors (from Story 4.1.1)
```css
/* Background */
--background: 240 10% 3.9%; /* zinc-950 */

/* Text */
--foreground: 240 5% 96%; /* zinc-100 */

/* Accent */
--primary: 200 98% 39%; /* blue-400 */

/* Card Background */
--card: 240 4% 16%; /* zinc-900/50 */

/* Border */
--border: 240 4% 26%; /* zinc-800 */

/* Muted */
--muted-foreground: 240 4% 46%; /* zinc-500 */

/* Destructive (Errors) */
--destructive: 0 84% 60%; /* red-400 */
```

### Typography
- **Headings**: font-light (font-weight: 300)
- **Body**: font-normal (font-weight: 400)
- **Labels**: font-medium (font-weight: 500)
- **Font Family**: Sans-serif (system default)

### Spacing
- **Container Padding**: px-4 (mobile), px-6 (tablet), px-8 (desktop)
- **Section Spacing**: py-12 (mobile), py-16 (desktop)
- **Form Field Spacing**: space-y-4 (between fields)
- **Button Spacing**: gap-4 (between buttons)

### Shadows
- **Card Shadow**: shadow-lg (large shadow for cards)
- **Button Hover**: shadow-md (medium shadow on hover)

---

## Accessibility Considerations

### WCAG 2.1 AA Compliance

1. **Keyboard Navigation**
   - All form fields accessible via Tab key
   - "Enter" key submits current step
   - "Escape" key cancels registration (with confirmation)
   - Focus indicators visible (blue-400 ring)

2. **Screen Reader Support**
   - Form labels properly associated with inputs
   - Error messages announced via aria-live regions
   - Progress indicator has aria-current
   - Required fields marked with aria-required="true"

3. **Color Contrast**
   - Text: zinc-100 on zinc-950 (21:1 ratio) ✅
   - Accent: blue-400 on zinc-950 (8.2:1 ratio) ✅
   - Errors: red-400 on zinc-950 (5.1:1 ratio) ✅

4. **Form Validation**
   - Inline errors with descriptive messages
   - Error summary at top of form (if multiple errors)
   - Focus moves to first error on submission

---

## Performance Considerations

1. **Lazy Loading**
   - RegistrationWizard component lazy-loaded on button click
   - Reduces initial bundle size

2. **Animations**
   - CSS transitions (GPU-accelerated)
   - No layout thrashing (will-change: height)
   - Smooth 60fps animations

3. **API Optimization**
   - Debounce email duplicate check (300ms)
   - Single API call on final submission
   - Optimistic UI updates

4. **Form State**
   - Local state management (no global state overhead)
   - Form data persisted in localStorage (recovery on refresh)

---

## Testing Scenarios

### Manual Testing Checklist

**Inline Registration Flow:**
- [ ] Click "Register Now" → Hero expands smoothly
- [ ] Fill Step 1 with valid data → "Next" button enabled
- [ ] Click "Next" → Step 1 collapses, Step 2 expands
- [ ] Click "Back" → Step 2 collapses, Step 1 expands (data preserved)
- [ ] Submit without terms → Error shown
- [ ] Submit with terms → Success redirect

**Dedicated Page Flow:**
- [ ] Navigate to `/register/BATbern025` → Page loads
- [ ] Same wizard behavior as inline
- [ ] "Back to Event" → Returns to homepage

**Validation:**
- [ ] Empty required field → Error on blur
- [ ] Invalid email format → Error on blur
- [ ] Valid data → No errors, green checkmark

**Responsive:**
- [ ] Mobile (< 640px) → Single column layout
- [ ] Tablet (640px-1024px) → Two-column fields
- [ ] Desktop (> 1024px) → Full layout with margins

**Error Handling:**
- [ ] Duplicate email → Warning modal
- [ ] Network error → Retry message
- [ ] Server error → Error message with support contact

---

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2025-11-08 | 1.0 | Initial wireframe creation for inline registration accordion | Sally (UX Expert) |

---

## Notes

- **Component Reuse**: RegistrationWizard and child components work in both inline and dedicated page contexts
- **Design Consistency**: All components follow shadcn dark theme established in Story 4.1.1
- **Anonymous Registration**: Per ADR-005, no authentication required for registration
- **Account Linking**: "Create account" CTA shown after registration for anonymous users to convert

**Dependencies:**
- Story 4.1.1 (shadcn dark theme) ✅
- Story 4.1.3 (Hero section) ✅
- shadcn components: input, textarea, checkbox, collapsible ✅
- Backend: `POST /api/v1/events/{eventCode}/registrations` ✅

**Next Steps:**
- Implement RegistrationWizard container component
- Implement PersonalDetailsStep with validation
- Implement ConfirmRegistrationStep with preferences
- Enhance HeroSection with expansion logic
- Create RegistrationPage for dedicated route
