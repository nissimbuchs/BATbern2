# Story 5.7 - Celebration Animations Wireframe

**Story**: BAT-11 - Slot Assignment & Progressive Publishing
**Priority**: Phase 3 (NICE TO HAVE - Polish)
**Related Stories**: Story 5.8 (Unified Event Page)

## Purpose

This wireframe documents celebration animations, visual feedback, and micro-interactions throughout the slot assignment workflow. Enhances user experience with delightful, purposeful animations that provide clear feedback, celebrate achievements, and guide user attention.

## Design Principles

1. **Purposeful**: Every animation serves a functional purpose (feedback, guidance, or celebration)
2. **Subtle**: Animations enhance without distracting from core workflow
3. **Performant**: 60fps minimum, GPU-accelerated where possible
4. **Respectful**: Honor `prefers-reduced-motion` setting
5. **Contextual**: Celebration intensity matches achievement significance
6. **Accessible**: Animations complement (not replace) text announcements

## Animation Categories

### 1. Micro-Interactions
- Button hover/press states
- Input focus
- Card elevation on hover
- Toggle switches
- Checkbox/radio animations

### 2. State Transitions
- Loading spinners
- Progress bar fills
- Status badge changes
- Speaker card state changes (unassigned → assigned)
- Session cell state changes (empty → filled)

### 3. Navigation Animations
- Modal entrance/exit
- Drawer slide-in/out
- Tab transitions
- Accordion expand/collapse
- Tooltip fade-in

### 4. Feedback Animations
- Success checkmarks
- Error shake
- Warning pulse
- Info bounce
- Validation feedback

### 5. Celebration Animations
- Milestone notifications (25%, 50%, 75%)
- Confetti on 100% completion
- Success banner entrance
- Achievement badges
- Completion fireworks (optional)

## 1. Micro-Interactions

### Button States

```css
/* Button hover animation */
.button {
  position: relative;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  transform: translateY(0);
}

.button::before {
  content: "";
  position: absolute;
  inset: 0;
  background: rgba(255, 255, 255, 0.1);
  opacity: 0;
  transition: opacity 0.2s ease;
}

.button:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.button:hover::before {
  opacity: 1;
}

.button:active {
  transform: translateY(0);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

/* Ripple effect on click */
@keyframes ripple {
  0% {
    transform: scale(0);
    opacity: 0.5;
  }
  100% {
    transform: scale(4);
    opacity: 0;
  }
}

.button-ripple {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.6);
  width: 20px;
  height: 20px;
  animation: ripple 0.6s ease-out;
  pointer-events: none;
}
```

**Visual Representation:**

```
Button States:

Default:
┌─────────────────┐
│ Assign Speaker  │ ← No elevation
└─────────────────┘

Hover (0.2s transition):
┌─────────────────┐
│ Assign Speaker  │ ← Lift 2px, shadow appears
└─────────────────┘
    ↑ transform: translateY(-2px)

Active/Click (0.1s):
┌─────────────────┐
│ Assign Speaker  │ ← Press down, ripple expands from click point
│      ○          │
│    ╱   ╲        │ ← Ripple animation
│  ○       ○      │
└─────────────────┘

After Click (0.2s):
Returns to default state, ripple fades out
```

### Speaker Card Hover

```css
/* Speaker card hover animation */
.speaker-card {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border: 2px solid transparent;
}

.speaker-card:hover {
  transform: translateY(-4px);
  border-color: #2196f3;
  box-shadow: 0 8px 16px rgba(33, 150, 243, 0.2);
}

.speaker-card:hover .speaker-avatar {
  transform: scale(1.05);
}

.speaker-card:hover .assign-button {
  animation: pulse-button 1s ease-in-out infinite;
}

@keyframes pulse-button {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(33, 150, 243, 0.7);
  }
  50% {
    box-shadow: 0 0 0 6px rgba(33, 150, 243, 0);
  }
}
```

**Visual Representation:**

```
Speaker Card Hover Animation (0.3s):

Before Hover:
┌─────────────────────────────────────┐
│ 🔶 Dr. Sarah Miller                 │ ← Border: transparent
│ Topic: AI in Architecture           │   No shadow
│ [Assign]                            │
└─────────────────────────────────────┘

During Hover:
┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓ ← Border: blue
┃ 🔶 Dr. Sarah Miller                 ┃   Lifts 4px
┃    (avatar scales 1.05)             ┃   Blue shadow
┃ Topic: AI in Architecture           ┃
┃ [Assign]  ← Pulsing blue ring       ┃
┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
     ╲
      ╲ Shadow extends 16px below
```

### Input Focus Animation

```css
/* Input focus animation */
.input-field {
  border: 2px solid #e0e0e0;
  transition: all 0.2s ease;
  position: relative;
}

.input-field::after {
  content: "";
  position: absolute;
  bottom: 0;
  left: 50%;
  width: 0;
  height: 2px;
  background: #2196f3;
  transition: all 0.3s ease;
}

.input-field:focus {
  border-color: #2196f3;
  box-shadow: 0 0 0 4px rgba(33, 150, 243, 0.1);
}

.input-field:focus::after {
  left: 0;
  width: 100%;
}
```

**Visual Representation:**

```
Input Focus Animation:

Default:
┌─────────────────────────────────────┐
│ Search speakers...                  │ ← Gray border
└─────────────────────────────────────┘

Focus (0.2s):
┌─────────────────────────────────────┐
│ Search speakers...│                 │ ← Blue border
└─────────────────────────────────────┘   Focus ring (0-4px spread)
 ━━━━━━━━━━━━━━━━━━                       Blue underline expands from center
      (0.3s expand animation)
```

## 2. State Transition Animations

### Progress Bar Fill

```css
/* Progress bar animation */
@keyframes progress-fill {
  from {
    width: var(--from-width);
  }
  to {
    width: var(--to-width);
  }
}

.progress-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #4caf50 0%, #66bb6a 100%);
  border-radius: inherit;
  transition: width 0.6s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
}

/* Shimmer effect on progress */
.progress-bar-fill::after {
  content: "";
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(
    90deg,
    transparent,
    rgba(255, 255, 255, 0.3),
    transparent
  );
  animation: shimmer 1.5s ease-in-out;
}

@keyframes shimmer {
  0% {
    left: -100%;
  }
  100% {
    left: 100%;
  }
}
```

**Visual Representation:**

```
Progress Bar Animation (3 of 8 → 4 of 8):

Initial State (37%):
Progress: 3 of 8 (37%)
███████░░░░░░░░░░░░░░░░
│←─37%─→│

Animating (0.6s smooth):
Progress: 4 of 8 (50%)
█████████░░░░░░░░░░░░░░
│←──50%──→│
    ↑
    └─ Shimmer effect sweeps across during animation

Final State:
Progress: 4 of 8 (50%)
██████████░░░░░░░░░░░░
│←──50%──→│
```

### Speaker Card State Change (Unassigned → Assigned)

```css
/* State change animation */
@keyframes card-state-change {
  0% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.7;
    transform: scale(0.98);
  }
  100% {
    opacity: 1;
    transform: scale(1);
  }
}

.speaker-card.state-changing {
  animation: card-state-change 0.4s ease-in-out;
}

/* Border color transition */
.speaker-card {
  border: 2px solid #ff9800; /* Orange - unassigned */
  transition: border-color 0.5s ease, background-color 0.5s ease;
}

.speaker-card.assigned {
  border-color: #4caf50; /* Green - assigned */
  background: linear-gradient(135deg, #e8f5e9 0%, #f1f8e9 100%);
}

/* Icon change animation */
@keyframes icon-change {
  0% {
    content: "🔶";
    transform: rotate(0deg) scale(1);
  }
  50% {
    transform: rotate(180deg) scale(0);
  }
  100% {
    content: "✓";
    transform: rotate(360deg) scale(1);
  }
}

.speaker-card-icon {
  display: inline-block;
  transition: all 0.4s ease;
}

.speaker-card.assigned .speaker-card-icon::before {
  animation: icon-change 0.6s ease-in-out;
}
```

**Visual Representation:**

```
Speaker Card State Change (0.6s total):

Frame 1 (0s - Unassigned):
┌─────────────────────────────────────┐
│ 🔶 Dr. Sarah Miller                 │ ← Orange border
│ Topic: AI in Architecture           │   Light background
│ Status: Not assigned                │
│ [Assign]                            │
└─────────────────────────────────────┘

Frame 2 (0.2s - Transition):
┌─────────────────────────────────────┐
│ ⟲  Dr. Sarah Miller                 │ ← Border color interpolating
│    (icon rotating, scaling down)    │   Background fading to green
│ Topic: AI in Architecture           │   Slight scale pulse (0.98)
│ Status: Assigning...                │
│                                     │
└─────────────────────────────────────┘

Frame 3 (0.4s - Icon Change):
┌─────────────────────────────────────┐
│ ↻  Dr. Sarah Miller                 │ ← Checkmark rotating in
│    (new icon scaling up)            │   Green border
│ Topic: AI in Architecture           │
│ Assigned: Workshop 2, 14:00         │
│ [Edit] [Details]                    │
└─────────────────────────────────────┘

Frame 4 (0.6s - Final):
┌─────────────────────────────────────┐
│ ✓ Dr. Sarah Miller                  │ ← Fully green
│ Topic: AI in Architecture           │   Light green gradient bg
│ Assigned: Workshop 2, 14:00-15:30   │   Match score shown
│ Match: 🟢 85%                       │
│ [Edit] [Details]                    │
└─────────────────────────────────────┘
```

### Session Cell Drop Zone Highlight

```css
/* Drop zone highlight animation */
.drop-zone {
  border: 2px dashed #e0e0e0;
  transition: all 0.2s ease;
  position: relative;
}

.drop-zone.drag-over {
  border-color: #4caf50;
  border-style: solid;
  background: linear-gradient(135deg, rgba(76, 175, 80, 0.05) 0%, rgba(129, 199, 132, 0.1) 100%);
  animation: pulse-drop-zone 1s ease-in-out infinite;
}

@keyframes pulse-drop-zone {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(76, 175, 80, 0.4);
  }
  50% {
    box-shadow: 0 0 0 8px rgba(76, 175, 80, 0);
  }
}

/* Match score badge slide-in */
.match-score-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  transform: translateX(100px);
  opacity: 0;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.drop-zone.drag-over .match-score-badge {
  transform: translateX(0);
  opacity: 1;
}
```

**Visual Representation:**

```
Drop Zone Highlight (during drag):

Default State:
┌─────────────────────────────────────┐
│ 14:00 - 15:30                       │ ← Dashed gray border
│ Workshop 2                          │   White background
│ Room B                              │
│ ○ Not assigned                      │
└─────────────────────────────────────┘

Drag Over (0.2s transition):
┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓ ← Solid green border
┃ 14:00 - 15:30            🟢 85%     ┃   Pulsing green shadow
┃ Workshop 2                   ↑      ┃   Light green gradient bg
┃ Room B                 badge slides ┃
┃ ⊕ Drop Dr. Miller here              ┃
┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
     ╲
      ╲ Pulsing shadow (0-8px)

Drop Confirmed (0.3s):
Flash green, then transition to assigned state
```

## 3. Navigation Animations

### Modal Entrance/Exit

```css
/* Modal overlay fade */
@keyframes modal-overlay-fade-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes modal-overlay-fade-out {
  from {
    opacity: 1;
  }
  to {
    opacity: 0;
  }
}

.modal-overlay {
  background: rgba(0, 0, 0, 0.5);
  animation: modal-overlay-fade-in 0.2s ease;
}

.modal-overlay.closing {
  animation: modal-overlay-fade-out 0.2s ease;
}

/* Modal dialog entrance */
@keyframes modal-dialog-entrance {
  from {
    opacity: 0;
    transform: scale(0.9) translateY(-20px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

@keyframes modal-dialog-exit {
  from {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
  to {
    opacity: 0;
    transform: scale(0.9) translateY(-20px);
  }
}

.modal-dialog {
  animation: modal-dialog-entrance 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.modal-dialog.closing {
  animation: modal-dialog-exit 0.2s cubic-bezier(0.4, 0, 1, 1);
}

/* Mobile bottom sheet */
@keyframes bottom-sheet-slide-up {
  from {
    transform: translateY(100%);
  }
  to {
    transform: translateY(0);
  }
}

@keyframes bottom-sheet-slide-down {
  from {
    transform: translateY(0);
  }
  to {
    transform: translateY(100%);
  }
}

.bottom-sheet {
  animation: bottom-sheet-slide-up 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.bottom-sheet.closing {
  animation: bottom-sheet-slide-down 0.2s cubic-bezier(0.4, 0, 1, 1);
}
```

**Visual Representation:**

```
Modal Entrance (0.3s):

Frame 1 (0s):
[Background: normal view]

Frame 2 (0.1s):
[Background: darkening]
┌─────────────────────────────────────┐
│                                     │ ← Modal scaling up (0.9 → 1.0)
│    ╔═══════════════════════════╗   │   Sliding up (opacity 0 → 1)
│    ║  Auto-Assign Sessions     ║   │
│    ║                           ║   │
│    ╚═══════════════════════════╝   │
└─────────────────────────────────────┘

Frame 3 (0.3s):
[Background: dark overlay 50%]
┌─────────────────────────────────────┐
│    ┌───────────────────────────┐   │ ← Modal fully visible
│    │  Auto-Assign Sessions     │   │   Scale 1.0, opacity 1
│    │  Choose strategy...       │   │
│    │  [Options]                │   │
│    │  [Preview] [Cancel]       │   │
│    └───────────────────────────┘   │
└─────────────────────────────────────┘
```

### Drawer Slide-In/Out

```css
/* Drawer slide animation */
@keyframes drawer-slide-in {
  from {
    transform: translateX(100%);
  }
  to {
    transform: translateX(0);
  }
}

@keyframes drawer-slide-out {
  from {
    transform: translateX(0);
  }
  to {
    transform: translateX(100%);
  }
}

.drawer {
  position: fixed;
  top: 0;
  right: 0;
  height: 100vh;
  width: 400px;
  background: white;
  box-shadow: -4px 0 16px rgba(0, 0, 0, 0.2);
  animation: drawer-slide-in 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.drawer.closing {
  animation: drawer-slide-out 0.25s cubic-bezier(0.4, 0, 1, 1);
}

/* Drawer overlay */
.drawer-overlay {
  animation: modal-overlay-fade-in 0.2s ease;
}

.drawer-overlay.closing {
  animation: modal-overlay-fade-out 0.25s ease;
}
```

**Visual Representation:**

```
Drawer Slide-In (Speaker Preferences):

Frame 1 (0s):
┌─────────────────────────────────────┐
│ Main Content                        │
│ [View Preferences] ← User clicks    │
│                                     │
│                                     │
└─────────────────────────────────────┘

Frame 2 (0.15s):
┌─────────────────────────────────────┐┌────
│ Main Content                        ││Pref
│ (darkening slightly)                ││Dr.
│                                     ││
│                                     ││[X]
└─────────────────────────────────────┘└────
                                        ↑
                                 Drawer sliding in from right

Frame 3 (0.3s):
┌───────────────────────────┐┌─────────────┐
│ Main Content              ││Preferences  │
│ (dark overlay 30%)        ││Dr. S. Miller│
│                           ││             │
│                           ││Time: ☀️ PM  │
│                           ││A/V: 🎤📽️   │
│                           ││[Close]      │
└───────────────────────────┘└─────────────┘
                             400px drawer
```

### Accordion Expand/Collapse

```css
/* Accordion animation */
.accordion-content {
  max-height: 0;
  overflow: hidden;
  transition: max-height 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.accordion-content.expanded {
  max-height: 2000px; /* Larger than content */
}

.accordion-icon {
  transition: transform 0.3s ease;
}

.accordion.expanded .accordion-icon {
  transform: rotate(180deg);
}

/* Content fade-in during expansion */
@keyframes accordion-content-fade {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.accordion-content.expanded > * {
  animation: accordion-content-fade 0.2s ease 0.1s both;
}

.accordion-content.expanded > *:nth-child(2) {
  animation-delay: 0.15s;
}

.accordion-content.expanded > *:nth-child(3) {
  animation-delay: 0.2s;
}
```

**Visual Representation:**

```
Accordion Expand Animation:

Collapsed:
┌─────────────────────────────────────┐
│ Speakers (5 unassigned)      [▼]    │ ← Click to expand
└─────────────────────────────────────┘

Expanding (0.3s):
┌─────────────────────────────────────┐
│ Speakers (5 unassigned)      [▲]    │ ← Icon rotating
├─────────────────────────────────────┤   (0° → 180° over 0.3s)
│ ┌─────────────────────────────────┐ │
│ │ Dr. Sarah Miller                │ │ ← Content fading in
│ └─────────────────────────────────┘ │   Staggered (0.1s, 0.15s, 0.2s)
│ ┌─────────────────────────────────┐ │
│ │ Prof. John Chen                 │ │
│ └─────────────────────────────────┘ │
│ ... (more speakers appearing)       │
└─────────────────────────────────────┘

Expanded:
┌─────────────────────────────────────┐
│ Speakers (5 unassigned)      [▲]    │
├─────────────────────────────────────┤
│ ┌─────────────────────────────────┐ │
│ │ Dr. Sarah Miller                │ │
│ │ [Assign] [Preferences]          │ │
│ └─────────────────────────────────┘ │
│ ┌─────────────────────────────────┐ │
│ │ Prof. John Chen                 │ │
│ │ [Assign] [Preferences]          │ │
│ └─────────────────────────────────┘ │
│ ... (all speakers visible)          │
└─────────────────────────────────────┘
```

## 4. Feedback Animations

### Success Checkmark

```css
/* Success checkmark animation */
@keyframes checkmark-circle {
  0% {
    stroke-dashoffset: 166;
    opacity: 0;
    transform: scale(0);
  }
  50% {
    stroke-dashoffset: 166;
    opacity: 1;
    transform: scale(1.1);
  }
  100% {
    stroke-dashoffset: 0;
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes checkmark-check {
  0% {
    stroke-dashoffset: 48;
    opacity: 0;
  }
  50% {
    stroke-dashoffset: 48;
    opacity: 0;
  }
  100% {
    stroke-dashoffset: 0;
    opacity: 1;
  }
}

.checkmark-circle {
  stroke-dasharray: 166;
  stroke-dashoffset: 166;
  stroke: #4caf50;
  fill: none;
  stroke-width: 4;
  animation: checkmark-circle 0.6s cubic-bezier(0.65, 0, 0.45, 1) forwards;
}

.checkmark-check {
  stroke-dasharray: 48;
  stroke-dashoffset: 48;
  stroke: #4caf50;
  stroke-width: 4;
  animation: checkmark-check 0.6s 0.3s cubic-bezier(0.65, 0, 0.45, 1) forwards;
}
```

**Visual Representation:**

```
Success Checkmark Animation (0.9s total):

Frame 1 (0s):
    ○    ← Empty, scaled to 0

Frame 2 (0.3s):
    ◯    ← Circle drawing (scale 1.1)
         ← Stroke drawing clockwise

Frame 3 (0.6s):
    ⦿    ← Circle complete, checkmark starts
    ╱    ← Check drawing from bottom-left

Frame 4 (0.9s):
    ✓    ← Checkmark complete
         ← Scale settles to 1.0

Final:
┌─────────────────────────────────────┐
│         ✓                           │
│  Assignment Confirmed!              │
│  Dr. Miller → Workshop 2            │
└─────────────────────────────────────┘
```

### Error Shake

```css
/* Error shake animation */
@keyframes shake {
  0%, 100% {
    transform: translateX(0);
  }
  10%, 30%, 50%, 70%, 90% {
    transform: translateX(-10px);
  }
  20%, 40%, 60%, 80% {
    transform: translateX(10px);
  }
}

.error-shake {
  animation: shake 0.5s ease-in-out;
}

/* Error icon bounce */
@keyframes error-bounce {
  0%, 100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.1);
  }
}

.error-icon {
  animation: error-bounce 0.5s ease-in-out 3;
}
```

**Visual Representation:**

```
Error Shake Animation (0.5s):

User attempts invalid assignment:

Frame 1 (0s):
┌─────────────────────────────────────┐
│ Dr. Sarah Miller                    │
│ [Assign]  ← Click                   │
└─────────────────────────────────────┘

Frame 2 (0.1s):
  ┌─────────────────────────────────────┐
  │ Dr. Sarah Miller                    │ ← Shake left 10px
  └─────────────────────────────────────┘

Frame 3 (0.2s):
      ┌─────────────────────────────────────┐
      │ Dr. Sarah Miller                    │ ← Shake right 10px
      └─────────────────────────────────────┘

Frame 4 (0.3s-0.5s):
Continues shaking with decreasing amplitude

Final (0.5s):
┌─────────────────────────────────────┐
│ ⚠️  Dr. Sarah Miller                │ ← Error icon bouncing
│ Cannot assign: No available slots   │
│ [View Conflicts]                    │
└─────────────────────────────────────┘
```

### Warning Pulse

```css
/* Warning pulse animation */
@keyframes warning-pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(255, 152, 0, 0.7);
  }
  50% {
    box-shadow: 0 0 0 10px rgba(255, 152, 0, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(255, 152, 0, 0);
  }
}

.warning-badge {
  animation: warning-pulse 1.5s ease-in-out infinite;
}

/* Warning icon glow */
@keyframes warning-glow {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.6;
  }
}

.warning-icon {
  animation: warning-glow 2s ease-in-out infinite;
}
```

**Visual Representation:**

```
Warning Pulse (continuous):

Frame 1 (0s):
┌─────────────────────────────────────┐
│ ⚠️  3 sessions have low match       │ ← No shadow
│ [Review] [Ignore]                   │
└─────────────────────────────────────┘

Frame 2 (0.5s):
┌─────────────────────────────────────┐
│ ⚠️  3 sessions have low match       │ ← Orange shadow expanding
│ [Review] [Ignore]                   │   (0 → 10px)
└─────────────────────────────────────┘
       ╲╲╲╲╲╲╲╲╲

Frame 3 (1.5s):
┌─────────────────────────────────────┐
│ ⚠️  3 sessions have low match       │ ← Shadow faded
│ [Review] [Ignore]                   │
└─────────────────────────────────────┘

Repeats continuously, icon also pulsing opacity
```

## 5. Celebration Animations

### Milestone Notifications (25%, 50%, 75%)

```css
/* Milestone toast slide-in */
@keyframes milestone-slide-in {
  from {
    transform: translateY(-100px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

@keyframes milestone-slide-out {
  from {
    transform: translateY(0);
    opacity: 1;
  }
  to {
    transform: translateY(-100px);
    opacity: 0;
  }
}

.milestone-toast {
  position: fixed;
  top: 80px;
  left: 50%;
  transform: translateX(-50%);
  animation: milestone-slide-in 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.milestone-toast.hiding {
  animation: milestone-slide-out 0.3s cubic-bezier(0.4, 0, 1, 1);
}

/* Milestone icon bounce */
@keyframes milestone-icon-bounce {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}

.milestone-icon {
  animation: milestone-icon-bounce 0.6s ease-in-out 3;
}

/* Milestone sparkle */
@keyframes sparkle {
  0% {
    opacity: 0;
    transform: scale(0) rotate(0deg);
  }
  50% {
    opacity: 1;
  }
  100% {
    opacity: 0;
    transform: scale(1.5) rotate(180deg);
  }
}

.milestone-sparkle {
  position: absolute;
  animation: sparkle 1s ease-out forwards;
}
```

**Visual Representation:**

```
Milestone Toast (25% - First Quarter):

Frame 1 (0s):
[Toast above viewport, invisible]

Frame 2 (0.2s):
┌─────────────────────────────────────┐
│ 🎯 Good progress!                   │ ← Sliding down from top
│ 2 of 8 sessions assigned            │   Icon bouncing
└─────────────────────────────────────┘
      ↓ translateY(-100px → 0)

Frame 3 (0.4s):
┌─────────────────────────────────────┐
│ 🎯 Good progress!   ✨              │ ← Fully visible
│ 2 of 8 sessions assigned   ✨       │   Sparkles appearing
│ 6 more to go!                       │   around edges
└─────────────────────────────────────┘

Stays visible for 3 seconds, then slides up

Milestone Variations:

25% (2 of 8):
┌─────────────────────────────────────┐
│ 🎯 Good progress!                   │
│ 2 of 8 sessions assigned            │
│ 6 more to go!                       │
└─────────────────────────────────────┘

50% (4 of 8):
┌─────────────────────────────────────┐
│ 🔥 Halfway there!                   │
│ 4 of 8 sessions assigned            │
│ Avg match: 85% (excellent!)         │
└─────────────────────────────────────┘

75% (6 of 8):
┌─────────────────────────────────────┐
│ 🚀 Almost done!                     │
│ 6 of 8 sessions assigned            │
│ Only 2 sessions left!               │
└─────────────────────────────────────┘
```

### Confetti Celebration (100% Complete)

```typescript
// Confetti particle system
interface ConfettiParticle {
  x: number;
  y: number;
  vx: number;
  vy: number;
  rotation: number;
  rotationSpeed: number;
  color: string;
  size: number;
  gravity: number;
}

function createConfetti(count: number = 100): ConfettiParticle[] {
  const colors = ['#FF6B6B', '#4ECDC4', '#45B7D1', '#FFA07A', '#98D8C8', '#F7DC6F'];
  const particles: ConfettiParticle[] = [];

  for (let i = 0; i < count; i++) {
    particles.push({
      x: Math.random() * window.innerWidth,
      y: -20,
      vx: (Math.random() - 0.5) * 4,
      vy: Math.random() * 3 + 2,
      rotation: Math.random() * 360,
      rotationSpeed: (Math.random() - 0.5) * 10,
      color: colors[Math.floor(Math.random() * colors.length)],
      size: Math.random() * 10 + 5,
      gravity: 0.3 + Math.random() * 0.2,
    });
  }

  return particles;
}

function animateConfetti(particles: ConfettiParticle[], ctx: CanvasRenderingContext2D) {
  ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height);

  particles.forEach((p) => {
    // Update position
    p.x += p.vx;
    p.y += p.vy;
    p.vy += p.gravity;
    p.rotation += p.rotationSpeed;

    // Draw particle
    ctx.save();
    ctx.translate(p.x, p.y);
    ctx.rotate((p.rotation * Math.PI) / 180);
    ctx.fillStyle = p.color;
    ctx.fillRect(-p.size / 2, -p.size / 2, p.size, p.size);
    ctx.restore();
  });

  // Continue animation if particles are visible
  if (particles.some((p) => p.y < ctx.canvas.height)) {
    requestAnimationFrame(() => animateConfetti(particles, ctx));
  }
}
```

```css
/* Confetti canvas overlay */
.confetti-canvas {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  pointer-events: none;
  z-index: 9999;
}

/* Success banner entrance (after confetti starts) */
@keyframes success-banner-entrance {
  from {
    opacity: 0;
    transform: translateY(50px) scale(0.9);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.success-banner {
  animation: success-banner-entrance 0.6s cubic-bezier(0.4, 0, 0.2, 1) 0.5s both;
}

/* Success banner glow pulse */
@keyframes success-glow {
  0%, 100% {
    box-shadow: 0 4px 20px rgba(76, 175, 80, 0.3);
  }
  50% {
    box-shadow: 0 8px 40px rgba(76, 175, 80, 0.5);
  }
}

.success-banner {
  animation: success-banner-entrance 0.6s cubic-bezier(0.4, 0, 0.2, 1) 0.5s both,
             success-glow 2s ease-in-out 1.1s infinite;
}
```

**Visual Representation:**

```
Confetti Celebration (100% Complete):

Frame 1 (0s - Assignment completes):
Progress: 8 of 8 (100%)
██████████████████████████

Frame 2 (0.1s - Confetti starts):
┌─────────────────────────────────────┐
│ ▪ ▫ ▪                               │ ← Confetti particles
│      ▫  ▪                           │   appearing at top
│   ▪       ▫                         │   Multiple colors
│                                     │   Falling, rotating
│                                     │
│                                     │
└─────────────────────────────────────┘

Frame 3 (0.5s - Success banner appears):
┌─────────────────────────────────────┐
│ ▪ ▫ ▪  ▫  ▪                         │
│      ▫  ▪   ▫  ▪                    │ ← Confetti falling
│   ▪       ▫    ▪  ▫                 │   throughout screen
│      ▫                              │
│ ┌─────────────────────────────────┐ │
│ │ 🎉 SUCCESS! 🎉                  │ │ ← Banner sliding up
│ │ All Session Timings Assigned!   │ │   from below
│ └─────────────────────────────────┘ │
└─────────────────────────────────────┘

Frame 4 (1s - Full celebration):
┌─────────────────────────────────────┐
│      ▫  ▪   ▫  ▪                    │
│   ▪ ▫    ▪  ▫    ▪  ▫               │ ← Confetti spread
│ ▫    ▪  ▫    ▪  ▫    ▪              │   across screen
│   ▪  ▫    ▪  ▫    ▪                 │
│ ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓ │
│ ┃ 🎉 🎊 SUCCESS! 🎊 🎉            ┃ │ ← Banner glowing
│ ┃ All Session Timings Assigned!   ┃ │   Pulsing green shadow
│ ┃                                  ┃ │
│ ┃ 8 of 8 sessions (100%)           ┃ │
│ ┃ Average match: 82% (High!)       ┃ │
│ ┃                                  ┃ │
│ ┃ [View Publishing Options →]     ┃ │ ← CTA button pulsing
│ ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛ │
│ ▫    ▪  ▫    ▪  ▫    ▪              │
└─────────────────────────────────────┘

Frame 5 (3s - Confetti settling):
┌─────────────────────────────────────┐
│                                     │
│                                     │
│ ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓ │
│ ┃ 🎉 SUCCESS!                     ┃ │ ← Banner remains
│ ┃ All Session Timings Assigned!   ┃ │   Gentle glow pulse
│ ┃ 8 of 8 (100%) │ Avg: 82%        ┃ │
│ ┃ [View Publishing Options →]     ┃ │
│ ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛ │
│                   ▪  ▫              │ ← Few particles
│                        ▪            │   still settling
└─────────────────────────────────────┘
```

### Achievement Badges (Optional)

```css
/* Achievement badge pop-in */
@keyframes badge-pop-in {
  0% {
    opacity: 0;
    transform: scale(0) rotate(-180deg);
  }
  50% {
    transform: scale(1.2) rotate(10deg);
  }
  100% {
    opacity: 1;
    transform: scale(1) rotate(0deg);
  }
}

.achievement-badge {
  animation: badge-pop-in 0.6s cubic-bezier(0.68, -0.55, 0.265, 1.55);
}

/* Badge shine effect */
@keyframes badge-shine {
  0% {
    left: -100%;
  }
  100% {
    left: 100%;
  }
}

.achievement-badge::after {
  content: "";
  position: absolute;
  top: 0;
  left: -100%;
  width: 50%;
  height: 100%;
  background: linear-gradient(
    90deg,
    transparent,
    rgba(255, 255, 255, 0.6),
    transparent
  );
  animation: badge-shine 2s ease-in-out 1;
}
```

**Visual Representation:**

```
Achievement Badge Animation:

Trigger: User completes all 8 sessions with 90%+ average match

Frame 1 (0s):
[Badge invisible, scaled to 0]

Frame 2 (0.3s):
    ╱ ╲
   │ ⭐ │  ← Badge rotating in, scaling up
    ╲ ╱     (0 → 1.2 scale, -180° → 10° rotation)

Frame 3 (0.6s):
  ┌─────┐
  │ ⭐  │  ← Badge settled (scale 1.0, rotation 0°)
  │Match│    Shine sweeping across
  │Master│
  └─────┘

Final Display:
┌─────────────────────────────────────┐
│ 🏆 Achievement Unlocked!            │
│                                     │
│   ┌─────┐                           │
│   │ ⭐  │  Match Master               │
│   │ 90%+│  Achieved 90%+ average    │
│   └─────┘  match score              │
│                                     │
│ [Share] [Close]                     │
└─────────────────────────────────────┘

Achievements List:
- First Assignment: Assign your first session (appears at 1/8)
- Quarter Complete: 25% progress (appears at 2/8)
- Halfway Hero: 50% progress (appears at 4/8)
- Almost There: 75% progress (appears at 6/8)
- Perfect Match: 100% sessions with 90%+ avg score (appears at 8/8)
- Speed Demon: Complete all assignments in under 5 minutes
- Preference Master: All sessions match speaker time preferences
```

## 6. Loading States

### Skeleton Loaders

```css
/* Skeleton shimmer effect */
@keyframes skeleton-shimmer {
  0% {
    background-position: -1000px 0;
  }
  100% {
    background-position: 1000px 0;
  }
}

.skeleton {
  background: linear-gradient(
    90deg,
    #f0f0f0 25%,
    #e0e0e0 50%,
    #f0f0f0 75%
  );
  background-size: 1000px 100%;
  animation: skeleton-shimmer 2s infinite linear;
  border-radius: 4px;
}

.skeleton-speaker-card {
  width: 100%;
  height: 120px;
  margin-bottom: 12px;
}

.skeleton-session-cell {
  width: 100%;
  height: 80px;
}
```

**Visual Representation:**

```
Skeleton Loaders (while data loading):

Speaker Pool Loading:
┌─────────────────────────────────────┐
│ SPEAKERS                            │
├─────────────────────────────────────┤
│ ┌─────────────────────────────────┐ │
│ │░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│ │ ← Shimmer effect
│ │░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│ │   moving left→right
│ └─────────────────────────────────┘ │
│ ┌─────────────────────────────────┐ │
│ │░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│ │
│ │░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│ │
│ └─────────────────────────────────┘ │
│ ... (more skeletons)                │
└─────────────────────────────────────┘

Session Grid Loading:
┌─────────────────────────────────────┐
│ SESSION TIMELINE                    │
├─────────────────────────────────────┤
│ ░░░░  ░░░░░  ░░░░░  ░░░░░          │ ← Shimmer blocks
│ ░░░░  ░░░░░  ░░░░░  ░░░░░          │   representing
│       ░░░░░         ░░░░░          │   session cells
│       ░░░░░         ░░░░░          │
│ ░░░░                ░░░░░          │
└─────────────────────────────────────┘
```

### Spinner Animations

```css
/* Circular spinner */
@keyframes spinner-rotate {
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
}

@keyframes spinner-dash {
  0% {
    stroke-dasharray: 1, 150;
    stroke-dashoffset: 0;
  }
  50% {
    stroke-dasharray: 90, 150;
    stroke-dashoffset: -35;
  }
  100% {
    stroke-dasharray: 90, 150;
    stroke-dashoffset: -124;
  }
}

.spinner {
  animation: spinner-rotate 2s linear infinite;
}

.spinner-path {
  stroke: #2196f3;
  stroke-linecap: round;
  animation: spinner-dash 1.5s ease-in-out infinite;
}

/* Dot pulse loader */
@keyframes dot-pulse {
  0%, 100% {
    transform: scale(1);
    opacity: 1;
  }
  50% {
    transform: scale(1.5);
    opacity: 0.5;
  }
}

.dot-loader span {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #2196f3;
  margin: 0 4px;
}

.dot-loader span:nth-child(1) {
  animation: dot-pulse 1s ease-in-out infinite;
}

.dot-loader span:nth-child(2) {
  animation: dot-pulse 1s ease-in-out 0.2s infinite;
}

.dot-loader span:nth-child(3) {
  animation: dot-pulse 1s ease-in-out 0.4s infinite;
}
```

**Visual Representation:**

```
Loading Spinners:

Circular Spinner:
    ╱─╲      ← Rotating circle
   │   │       Dash animating around edge
    ╲─╱        (0° → 360° continuous)

Auto-Assigning...

Dot Pulse Loader:
● ○ ○  → ○ ● ○  → ○ ○ ●  → ● ○ ○
   (dots pulsing sequentially, 0.2s delay)

Processing assignments...
```

## 7. Reduced Motion Alternatives

```css
/* Respect prefers-reduced-motion */
@media (prefers-reduced-motion: reduce) {
  /* Disable all animations */
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }

  /* Replace confetti with simple checkmark */
  .confetti-canvas {
    display: none;
  }

  .success-banner-reduced-motion {
    display: block; /* Hidden by default, shown when reduced motion */
  }

  .success-banner-reduced-motion::before {
    content: "✓✓✓ ";
    font-size: 32px;
    color: #4caf50;
  }

  /* Replace progress bar animation with instant change */
  .progress-bar-fill {
    transition: none;
  }

  /* Replace modal slide with fade only */
  .modal-dialog {
    animation: none;
    opacity: 1;
  }

  .modal-overlay {
    animation: none;
    opacity: 1;
  }

  /* Replace shake with border flash */
  @keyframes border-flash-reduced {
    0%, 100% {
      border-color: #f44336;
    }
    50% {
      border-color: #ff5252;
    }
  }

  .error-shake {
    animation: border-flash-reduced 0.3s ease 2;
  }
}
```

## Performance Optimization

### GPU Acceleration

```css
/* Use transform and opacity for animations (GPU-accelerated) */
.animated-element {
  will-change: transform, opacity; /* Hint to browser */
  transform: translateZ(0); /* Force GPU layer */
}

/* Avoid animating expensive properties */
/* ✅ Good - GPU accelerated */
.good-animation {
  transition: transform 0.3s ease, opacity 0.3s ease;
}

/* ❌ Bad - CPU intensive */
.bad-animation {
  transition: width 0.3s ease, height 0.3s ease, top 0.3s ease, left 0.3s ease;
}
```

### Animation Batching

```typescript
// Batch DOM updates with requestAnimationFrame
function updateProgress(newProgress: number) {
  requestAnimationFrame(() => {
    // Update progress bar
    progressBar.style.width = `${newProgress}%`;
    progressText.textContent = `${assignedCount} of ${totalCount} (${newProgress}%)`;

    // Update speaker cards
    updateSpeakerCardStates();

    // Update session cells
    updateSessionCellStates();
  });
}
```

### Lazy Animation Loading

```typescript
// Only load confetti library when needed
async function celebrate() {
  if (!window.confetti) {
    const { default: confetti } = await import('canvas-confetti');
    window.confetti = confetti;
  }

  window.confetti({
    particleCount: 100,
    spread: 70,
    origin: { y: 0.6 },
  });
}
```

## Testing

### Animation Testing Checklist

**Visual Testing:**
- [ ] All animations run at 60fps
- [ ] No jank or stuttering during transitions
- [ ] Animations complete at expected duration
- [ ] Reduced motion alternatives work correctly
- [ ] Mobile animations are performant

**Functional Testing:**
- [ ] Animations don't block user interactions
- [ ] Modal animations complete before focus trap
- [ ] Progress animations sync with data updates
- [ ] Success celebrations trigger at correct time
- [ ] Confetti cleans up properly (no memory leaks)

**Accessibility Testing:**
- [ ] Screen readers announce state changes
- [ ] Animations respect prefers-reduced-motion
- [ ] Focus management works during animations
- [ ] Keyboard navigation not interrupted by animations

### Automated Tests

```typescript
// Test animation completion
test('should complete progress bar animation', async ({ page }) => {
  await page.goto('/organizer/events/batbern-2025/slot-assignment');

  // Perform assignment
  await performAssignment(page, 'Dr. Sarah Miller', 'Workshop 2');

  // Wait for animation to complete (0.6s)
  await page.waitForTimeout(700);

  // Verify progress bar updated
  const progressText = await page.locator('.progress-text').textContent();
  expect(progressText).toContain('4 of 8');

  const progressWidth = await page.locator('.progress-bar-fill').evaluate(
    (el) => getComputedStyle(el).width
  );
  expect(progressWidth).toBe('50%'); // 4/8 = 50%
});

// Test confetti triggers
test('should show confetti on 100% completion', async ({ page }) => {
  await page.goto('/organizer/events/batbern-2025/slot-assignment');

  // Assign all 8 sessions
  await assignAllSessions(page);

  // Verify confetti canvas visible
  const confettiCanvas = await page.locator('.confetti-canvas');
  await expect(confettiCanvas).toBeVisible();

  // Wait for confetti to complete (3s)
  await page.waitForTimeout(3500);

  // Verify success banner visible
  const successBanner = await page.locator('.success-banner');
  await expect(successBanner).toBeVisible();
});

// Test reduced motion
test('should respect prefers-reduced-motion', async ({ page, context }) => {
  // Emulate reduced motion preference
  await context.emulateMedia({ reducedMotion: 'reduce' });

  await page.goto('/organizer/events/batbern-2025/slot-assignment');

  // Perform assignment
  await performAssignment(page, 'Dr. Sarah Miller', 'Workshop 2');

  // Verify no confetti
  const confettiCanvas = await page.locator('.confetti-canvas');
  await expect(confettiCanvas).toBeHidden();

  // Verify instant progress update (no animation)
  const progressWidth = await page.locator('.progress-bar-fill').evaluate(
    (el) => getComputedStyle(el).transitionDuration
  );
  expect(progressWidth).toBe('0.01ms'); // Near-instant
});
```

## Implementation Priority

### Must-Have (MVP):
1. Progress bar fill animation
2. Speaker card state change (unassigned → assigned)
3. Success toast notifications
4. Basic loading spinners

### Should-Have (Post-MVP):
1. Milestone notifications (25%, 50%, 75%)
2. Confetti celebration (100%)
3. Hover/focus micro-interactions
4. Modal entrance/exit animations

### Nice-to-Have (Polish):
1. Achievement badges
2. Particle effects
3. Advanced confetti patterns
4. Celebration sounds (optional)

## Summary

This wireframe documents comprehensive celebration animations and visual feedback for the slot assignment workflow:

**Key Animation Categories:**
1. **Micro-Interactions**: Button hovers, card elevations, input focus
2. **State Transitions**: Progress bars, status changes, loading states
3. **Navigation**: Modal/drawer entrance/exit, accordion expand
4. **Feedback**: Success checkmarks, error shakes, warning pulses
5. **Celebrations**: Milestone toasts, confetti, achievement badges

**Performance:**
- 60fps minimum for all animations
- GPU-accelerated transforms and opacity
- Lazy loading for heavy libraries (confetti)
- Reduced motion alternatives for accessibility

**Testing:**
- Automated Playwright tests for all major animations
- Visual regression testing
- Performance profiling
- Accessibility compliance

---

**Next Steps:**
1. Implement core animations (progress bar, state transitions)
2. Add celebration animations (milestones, confetti)
3. Test across devices and browsers
4. Verify reduced motion alternatives
5. Performance test on low-end devices
