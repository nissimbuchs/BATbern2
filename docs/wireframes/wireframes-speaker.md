# BATbern Speaker Portal - Complete Wireframes

## Overview
This document contains detailed wireframes for all speaker interfaces, covering the complete speaker journey from invitation through post-event activities, supporting the speaker management workflow (Steps 4-7, 9-10 of the 16-step process).

---

## 1. Speaker Portal Dashboard (Main Landing)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ BATbern Speaker Portal                            Peter Muller ▼  [🔔 2] [Profile]  │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─────────────────────────────────────────────────────────────────────────────┐    │
│  │ Welcome back, Peter! Here's your speaker dashboard                          │    │
│  └─────────────────────────────────────────────────────────────────────────────┘    │
│                                                                                       │
│  ┌──────────────── MY EVENTS ──────────────────┬──── QUICK ACTIONS ─────────────┐   │
│  │                                              │                                 │   │
│  │  ┌─────────────────────────────────────┐     │  [📝 Update Profile]           │   │
│  │  │ UPCOMING                             │     │  [📊 View Past Talks]          │   │
│  │  │                                      │     │  [📧 Contact Organizer]        │   │
│  │  │ Spring Conference 2025               │     │  [📚 Resource Center]          │   │
│  │  │ May 15, 2025 • Kursaal Bern         │     │  [👥 Speaker Network]          │   │
│  │  │                                      │     │                                 │   │
│  │  │ Status: ✓ Confirmed                 │     │  ──────────────────            │   │
│  │  │ Slot: 09:45 - 10:30 (45 min)       │     │                                 │   │
│  │  │ Topic: Kubernetes Best Practices     │     │  NOTIFICATIONS                  │   │
│  │  │                                      │     │                                 │   │
│  │  │ Tasks Due:                          │     │  🔴 Abstract revision needed    │   │
│  │  │ ⚠️ Final slides - Due Apr 30       │     │      Due in 2 days             │   │
│  │  │ ✓ Abstract submitted                │     │      [Review Feedback]          │   │
│  │  │ ✓ Bio & photo uploaded              │     │                                 │   │
│  │  │                                      │     │  ℹ️ Event schedule published    │   │
│  │  │ [View Event] [Submit Materials]     │     │      Your talk: 09:45 AM       │   │
│  │  └─────────────────────────────────────┘     │      [View Schedule]           │   │
│  │                                              │                                 │   │
│  │  ┌─────────────────────────────────────┐     └─────────────────────────────────┘ │
│  │  │ INVITATIONS                          │                                        │
│  │  │                                      │                                        │
│  │  │ 🆕 Autumn Conference 2025            │                                        │
│  │  │ Nov 20, 2025 • Tech Park Zurich     │                                        │
│  │  │                                      │                                        │
│  │  │ Topic: Cloud Security Trends         │                                        │
│  │  │ Response needed by: Mar 15          │                                        │
│  │  │                                      │                                        │
│  │  │ [Accept] [Decline] [Need More Info] │                                        │
│  │  └─────────────────────────────────────┘                                        │
│  └──────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                       │
│  ┌─── YOUR IMPACT ───────────────────┬─── UPCOMING DEADLINES ───────────────────┐   │
│  │                                   │                                            │   │
│  │  Total Talks: 12                  │  Mar 10  Autumn Conference response       │   │
│  │  Total Attendees: 2,847           │  Apr 30  Spring Conference final slides   │   │
│  │  Avg Rating: 4.7/5                │  May 01  Speaker dinner RSVP              │   │
│  │  Downloads: 3,421                 │  May 10  Tech check appointment           │   │
│  │                                   │                                            │   │
│  │  Top Topics:                      │  [Sync to Calendar] [Email Reminders]     │   │
│  │  • Kubernetes (5 talks)           │                                            │   │
│  │  • DevOps (4 talks)               │                                            │   │
│  │  • Cloud Native (3 talks)         │                                            │   │
│  └───────────────────────────────────┴────────────────────────────────────────────┘  │
│                                                                                       │
│  ┌─── RECENT ACTIVITY ───────────────────────────────────────────────────────────┐   │
│  │                                                                                │   │
│  │  Yesterday   Organizer reviewed your abstract - 1 revision requested          │   │
│  │  Mar 5       You uploaded speaker photo                                       │   │
│  │  Mar 3       You accepted Spring Conference invitation                        │   │
│  │  Mar 1       New invitation received for Autumn Conference                    │   │
│  └────────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### Key Interactive Elements:
- **Event cards**: Click to expand full details and requirements
- **Status indicators**: Visual progress of submission tasks
- **Quick actions**: One-click access to common speaker tasks
- **Notifications**: Prioritized alerts with direct action buttons
- **Calendar sync**: Export deadlines to personal calendar

---

## 2. Invitation Response Interface (From Email Link)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ BATbern Event Platform                                          [Login] [Register]   │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐ │
│  │                     You're Invited to Speak at BATbern!                          │ │
│  │                          Autumn Conference 2025                                   │ │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── EVENT DETAILS ─────────────────────────────────────────────────────────────┐  │
│  │                                                                                 │  │
│  │  🎯 Topic Request:  Cloud Security Trends & Best Practices                     │  │
│  │  📅 Date:          November 20, 2025                                          │  │
│  │  📍 Location:      Tech Park, Zurich                                          │  │
│  │  ⏱️ Format:        45-minute presentation + 15-min Q&A                        │  │
│  │  👥 Expected:      200+ IT professionals                                      │  │
│  │  🎟️ Your Role:     Main track speaker, afternoon session                     │  │
│  │                                                                                 │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── WHY WE CHOSE YOU ──────────────────────────────────────────────────────────┐  │
│  │                                                                                 │  │
│  │  "Your expertise in cloud security architecture and your recent work on        │  │
│  │  zero-trust implementations makes you the perfect speaker for this topic.      │  │
│  │  Your previous talk on Kubernetes security was highly rated (4.8/5) and        │  │
│  │  we believe our audience would greatly benefit from your insights."            │  │
│  │                                                                                 │  │
│  │  - Sally Organizer, Event Lead                                                 │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── YOUR RESPONSE ──────────────────────────────────────────────────────────────┐ │
│  │                                                                                 │  │
│  │  Please respond by: March 15, 2025 (10 days remaining)                        │  │
│  │                                                                                 │  │
│  │  ┌───────────────┐  ┌───────────────┐  ┌────────────────────┐                 │  │
│  │  │               │  │               │  │                    │                 │  │
│  │  │   ✓ ACCEPT    │  │   ✗ DECLINE   │  │   ? NEED MORE INFO │                 │  │
│  │  │               │  │               │  │                    │                 │  │
│  │  │ I'm excited   │  │ Unfortunately │  │  I'm interested    │                 │  │
│  │  │ to speak!     │  │ not available │  │  but need details  │                 │  │
│  │  │               │  │               │  │                    │                 │  │
│  │  └───────────────┘  └───────────────┘  └────────────────────┘                 │  │
│  │                                                                                 │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── IF ACCEPTING - QUICK PREFERENCES ──────────────────────────────────────────┐  │
│  │                                                                                 │  │
│  │  Preferred Time Slot:                Technical Requirements:                  │  │
│  │  ○ Morning (09:00-12:00)            ☐ Mac adapter needed                      │  │
│  │  ● Afternoon (13:00-17:00)          ☐ Remote presentation option              │  │
│  │  ○ No preference                    ☐ Special audio/video needs               │  │
│  │                                                                                 │  │
│  │  Travel Requirements:                Initial Presentation Title:               │  │
│  │  ○ Local (no accommodation)         ┌────────────────────────────────┐        │  │
│  │  ○ Need accommodation                │ Zero-Trust Security in Cloud   │        │  │
│  │  ○ Virtual participation            │ Native Environments            │        │  │
│  │                                     └────────────────────────────────┘        │  │
│  │                                                                                 │  │
│  │  Comments for Organizer:                                                       │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐          │  │
│  │  │ Happy to speak! Can we discuss covering specific compliance     │          │  │
│  │  │ requirements for Swiss financial sector?                        │          │  │
│  │  └─────────────────────────────────────────────────────────────────┘          │  │
│  │                                                                                 │  │
│  │                              [Submit Response →]                               │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── WHAT HAPPENS NEXT ──────────────────────────────────────────────────────────┐ │
│  │  1. Submit your response (you can update it later)                            │  │
│  │  2. We'll confirm your slot within 48 hours                                   │  │
│  │  3. You'll receive a speaker portal account                                   │  │
│  │  4. Submit materials at your convenience (deadline: Oct 20)                   │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Material Submission Wizard (Multi-Step)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back to Dashboard            Submit Speaker Materials                    [Save Draft]│
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  Spring Conference 2025 - Kubernetes Best Practices                                  │
│                                                                                       │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐ │
│  │  Step 1         Step 2         Step 3         Step 4         Step 5    Review   │ │
│  │    ●━━━━━━━━━━━━━○━━━━━━━━━━━━━○━━━━━━━━━━━━━○━━━━━━━━━━━━━○━━━━━━━━━━○       │ │
│  │  Basic Info    Abstract      Biography       Photo         Presentation         │ │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── STEP 1: BASIC INFORMATION ─────────────────────────────────────────────────┐  │
│  │                                                                                 │  │
│  │  Presentation Title * (max 100 characters)                                     │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐          │  │
│  │  │ Kubernetes Best Practices for Production Environments           │ 58/100    │  │
│  │  └─────────────────────────────────────────────────────────────────┘          │  │
│  │                                                                                 │  │
│  │  Subtitle (optional, max 150 characters)                                       │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐          │  │
│  │  │ Scaling, Security, and Monitoring in Enterprise K8s             │ 52/150    │  │
│  │  └─────────────────────────────────────────────────────────────────┘          │  │
│  │                                                                                 │  │
│  │  Target Audience Level *                                                       │  │
│  │  ○ Beginner (New to the topic)                                               │  │
│  │  ● Intermediate (Some experience)                                             │  │
│  │  ○ Advanced (Expert practitioners)                                            │  │
│  │  ○ Mixed (All levels welcome)                                                 │  │
│  │                                                                                 │  │
│  │  Primary Technology Focus * (select up to 3)                                   │  │
│  │  ☑ Kubernetes  ☑ DevOps  ☑ Cloud Native  ☐ Security  ☐ Monitoring            │  │
│  │                                                                                 │  │
│  │  Presentation Language *                                                       │  │
│  │  ● English    ○ German    ○ English with German slides                        │  │
│  │                                                                                 │  │
│  │  Session Format Preference                                                     │  │
│  │  ● Presentation only (45 min)                                                 │  │
│  │  ○ Presentation (30 min) + Demo (15 min)                                      │  │
│  │  ○ Workshop style (interactive)                                                │  │
│  │                                                                                 │  │
│  │  Special Requirements                                                          │  │
│  │  ☐ Live internet connection required                                          │  │
│  │  ☐ Multiple screens/projectors                                                │  │
│  │  ☐ Audience laptops needed                                                    │  │
│  │  ☑ Will conduct live demo (need backup plan)                                 │  │
│  │                                                                                 │  │
│  │                     [← Previous]  [Save Draft]  [Next Step →]                 │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── PROGRESS & TIPS ────────────────────────────────────────────────────────────┐ │
│  │  ✓ Auto-saved 30 seconds ago                                                   │  │
│  │  💡 Tip: Clear titles help attendees choose the right sessions                 │  │
│  │  📊 20% complete - Estimated time remaining: 8 minutes                         │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘

[STEP 2: ABSTRACT]
┌─────────────────────────────────────────────────────────────────────────────────────┐
│  ┌──── STEP 2: PRESENTATION ABSTRACT ─────────────────────────────────────────────┐  │
│  │                                                                                 │  │
│  │  Abstract * (max 1000 characters)                                              │  │
│  │  Your abstract should include:                                                 │  │
│  │  • What attendees will learn                                                   │  │
│  │  • Key takeaways                                                               │  │
│  │  • ⚠️ Must include "Lessons Learned" from real experience                     │  │
│  │                                                                                 │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐          │  │
│  │  │ This talk explores production-ready Kubernetes practices based   │          │  │
│  │  │ on 3 years of managing 50+ clusters at scale.                   │          │  │
│  │  │                                                                   │          │  │
│  │  │ You'll learn:                                                    │          │  │
│  │  │ • Cluster architecture patterns that scale                       │          │  │
│  │  │ • Security hardening beyond defaults                             │          │  │
│  │  │ • Monitoring strategies that actually work                       │          │  │
│  │  │ • Cost optimization techniques                                   │          │  │
│  │  │                                                                   │          │  │
│  │  │ Lessons learned: We'll share real failures including our         │          │  │
│  │  │ 2-hour production outage, DNS mysteries, and how we reduced     │          │  │
│  │  │ our cloud costs by 40% while improving reliability.             │          │  │
│  │  └─────────────────────────────────────────────────────────────────┘          │  │
│  │                                                              789/1000          │  │
│  │                                                                                 │  │
│  │  ✓ Length requirement met                                                      │  │
│  │  ✓ Lessons learned included                                                    │  │
│  │  ⚠️ Consider adding specific technologies/tools                               │  │
│  │                                                                                 │  │
│  │  Key Learning Objectives * (3-5 bullet points)                                 │  │
│  │  1. ┌──────────────────────────────────────────────────────────┐              │  │
│  │     │ Design scalable cluster architectures for 1000+ nodes    │              │  │
│  │     └──────────────────────────────────────────────────────────┘              │  │
│  │  2. ┌──────────────────────────────────────────────────────────┐              │  │
│  │     │ Implement security scanning in CI/CD pipelines           │              │  │
│  │     └──────────────────────────────────────────────────────────┘              │  │
│  │  3. ┌──────────────────────────────────────────────────────────┐              │  │
│  │     │ Build observable systems with Prometheus & Grafana       │              │  │
│  │     └──────────────────────────────────────────────────────────┘              │  │
│  │  [+ Add Learning Objective]                                                    │  │
│  │                                                                                 │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────┘

[STEP 3: BIOGRAPHY]
┌─────────────────────────────────────────────────────────────────────────────────────┐
│  ┌──── STEP 3: SPEAKER BIOGRAPHY ─────────────────────────────────────────────────┐  │
│  │                                                                                 │  │
│  │  Professional Bio * (max 500 characters)                                       │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐          │  │
│  │  │ Peter Muller is a Principal Cloud Architect at TechCorp with    │          │  │
│  │  │ 10+ years in cloud native technologies. He leads the platform   │          │  │
│  │  │ team managing Kubernetes infrastructure for 500+ microservices.  │          │  │
│  │  │ Peter is a CNCF contributor and speaks regularly at DevOps      │          │  │
│  │  │ conferences across Europe. He holds certifications in CKA,      │          │  │
│  │  │ CKAD, and AWS Solutions Architect Professional.                 │          │  │
│  │  └─────────────────────────────────────────────────────────────────┘          │  │
│  │                                                              420/500          │  │
│  │                                                                                 │  │
│  │  Company/Organization *                        Job Title *                     │  │
│  │  ┌──────────────────────┐                     ┌──────────────────────┐       │  │
│  │  │ TechCorp AG          │                     │ Principal Architect  │       │  │
│  │  └──────────────────────┘                     └──────────────────────┘       │  │
│  │                                                                                 │  │
│  │  Social/Professional Links (optional)                                          │  │
│  │  LinkedIn:  [linkedin.com/in/pmuller_____________________________]            │  │
│  │  Twitter/X: [@pmuller_devops_____________________________________]            │  │
│  │  GitHub:    [github.com/pmuller__________________________________]            │  │
│  │  Website:   [https://pmuller.tech________________________________]            │  │
│  │                                                                                 │  │
│  │  Previous Speaking Experience                                                  │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐          │  │
│  │  │ • KubeCon EU 2024 - "Scaling Kubernetes"                       │          │  │
│  │  │ • DevOps Days Zurich 2023 - "GitOps Best Practices"           │          │  │
│  │  │ • BATbern Spring 2023 - "Container Security" (Rating: 4.8/5)   │          │  │
│  │  └─────────────────────────────────────────────────────────────────┘          │  │
│  │                                                                                 │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────┘

[STEP 4: PHOTO UPLOAD]
┌─────────────────────────────────────────────────────────────────────────────────────┐
│  ┌──── STEP 4: SPEAKER PHOTO ─────────────────────────────────────────────────────┐  │
│  │                                                                                 │  │
│  │  Upload Professional Photo *                                                   │  │
│  │                                                                                 │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐          │  │
│  │  │                                                                   │          │  │
│  │  │                      ┌─────────────────┐                         │          │  │
│  │  │                      │                 │                         │          │  │
│  │  │                      │                 │                         │          │  │
│  │  │                      │   📷 Drop       │                         │          │  │
│  │  │                      │   Photo Here    │                         │          │  │
│  │  │                      │                 │                         │          │  │
│  │  │                      │   or Browse     │                         │          │  │
│  │  │                      └─────────────────┘                         │          │  │
│  │  │                                                                   │          │  │
│  │  │              [Browse Files]  or drag and drop                    │          │  │
│  │  │                                                                   │          │  │
│  │  └─────────────────────────────────────────────────────────────────┘          │  │
│  │                                                                                 │  │
│  │  Requirements:                      Current Photo:                            │  │
│  │  • Minimum 400x400px               ┌──────────┐                               │  │
│  │  • Maximum 5MB                     │ [Photo   │                               │  │
│  │  • JPG or PNG format               │  Preview │                               │  │
│  │  • Professional headshot           │  Here]   │                               │  │
│  │  • Good lighting                   └──────────┘                               │  │
│  │  • Recent (within 2 years)         peter-muller.jpg                           │  │
│  │                                     2.3MB • 800x800px                         │  │
│  │                                     [Remove] [Replace]                        │  │
│  │                                                                                 │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Speaker Profile Management

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back to Dashboard                   My Speaker Profile                   [Preview] │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─── PUBLIC PROFILE ──────────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  ┌────────┐  Peter Muller                          [Edit]                       │ │
│  │  │ [Photo]│  Principal Cloud Architect @ TechCorp AG                            │ │
│  │  │        │  Zurich, Switzerland                                                │ │
│  │  └────────┘  Member since: March 2020 • 12 talks • 4.7★ avg rating             │ │
│  │                                                                                  │ │
│  │  Bio:                                                                            │ │
│  │  ┌──────────────────────────────────────────────────────────────────────────┐   │ │
│  │  │ Cloud native enthusiast with 10+ years building scalable platforms.       │   │ │
│  │  │ Specializing in Kubernetes, DevOps, and cloud architecture. CNCF          │   │ │
│  │  │ contributor and regular conference speaker across Europe.                 │   │ │
│  │  └──────────────────────────────────────────────────────────────────────────┘   │ │
│  │                                                                                  │ │
│  │  [🔗 LinkedIn] [🐦 Twitter] [💻 GitHub] [🌐 Website]                           │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── EXPERTISE & TOPICS ──────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Core Expertise (select 3-5)                                                    │ │
│  │  ☑ Kubernetes     ☑ Cloud Architecture    ☑ DevOps                             │ │
│  │  ☑ Microservices  ☐ Security             ☐ Data Engineering                    │ │
│  │  ☐ AI/ML          ☐ Blockchain            ☐ IoT                                │ │
│  │                                                                                  │ │
│  │  Topics I Can Speak About                              [+ Add Topic]            │ │
│  │  ┌────────────────────────────────────────────────────────────────────┐        │ │
│  │  │ • Kubernetes at Scale                               [Remove]       │        │ │
│  │  │ • GitOps and CI/CD Best Practices                   [Remove]       │        │ │
│  │  │ • Cloud Cost Optimization                           [Remove]       │        │ │
│  │  │ • Container Security                                [Remove]       │        │ │
│  │  │ • Microservices Architecture                        [Remove]       │        │ │
│  │  └────────────────────────────────────────────────────────────────────┘        │ │
│  │                                                                                  │ │
│  │  Languages                          Presentation Formats                        │ │
│  │  ☑ English (Fluent)                ☑ Keynote (30-45 min)                       │ │
│  │  ☑ German (Native)                 ☑ Technical Deep Dive (45-60 min)           │ │
│  │  ☐ French (Basic)                  ☑ Workshop (Half/Full day)                  │ │
│  │                                     ☐ Panel Discussions                         │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── AVAILABILITY & PREFERENCES ──────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  General Availability                                                           │ │
│  │  ● Available for speaking engagements                                          │ │
│  │  ○ Limited availability (specify below)                                        │ │
│  │  ○ Not currently accepting invitations                                         │ │
│  │                                                                                  │ │
│  │  Blocked Dates (Not available)                          [+ Add Dates]          │ │
│  │  • July 15-30, 2025 (Vacation)                                                 │ │
│  │  • September 10-15, 2025 (KubeCon)                                             │ │
│  │                                                                                  │ │
│  │  Travel Preferences                      Speaking Fees                         │ │
│  │  ☑ Local events (no travel)             ○ Pro bono only                       │ │
│  │  ☑ Switzerland                          ● Negotiable                           │ │
│  │  ☑ Europe                               ○ Standard fee: CHF _______           │ │
│  │  ☐ Worldwide                                                                   │ │
│  │                                                                                  │ │
│  │  ☑ Notify me about relevant speaking opportunities                             │ │
│  │  ☑ Show profile in speaker directory                                           │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  [Save Changes] [Cancel] [Preview Public View]                                       │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Event Participation Timeline

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back          Spring Conference 2025 - Your Timeline                    [Export]   │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  Event: Cloud Native Architecture       Date: May 15, 2025      Your Slot: 09:45 AM │
│                                                                                       │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐ │
│  │                           YOUR EVENT TIMELINE                                    │ │
│  │                                                                                   │ │
│  │  March                 April                  May                                │ │
│  │  ─────┬─────────────────┬──────────────────────┬───────────────────────         │ │
│  │       │                 │                      │                                 │ │
│  │   Mar 3             Apr 1                 May 1              May 15              │ │
│  │     ↓                 ↓                     ↓                  ↓                 │ │
│  │   ✓ Accepted      ⚠️ Abstract Due      Final Slides      EVENT DAY              │ │
│  │   Invitation        Revision               Due                                   │ │
│  │                                                                                   │ │
│  │   ✓ Mar 5         ✓ Mar 20            ○ Apr 30           ○ May 10               │ │
│  │   Bio Submitted   Photo Uploaded      Slides Due         Tech Check              │ │
│  │                                                                                   │ │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── UPCOMING MILESTONES ─────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  ⚠️ OVERDUE - Abstract Revision                          Due: Apr 1 (2 days ago)│ │
│  │     Moderator feedback requires addressing 2 points                             │ │
│  │     [View Feedback] [Submit Revision]                                           │ │
│  │                                                                                  │ │
│  │  📊 Final Presentation Slides                           Due: Apr 30 (27 days)   │ │
│  │     PDF format, 16:9 aspect ratio, max 30 slides                              │ │
│  │     [Upload Slides] [View Guidelines]                                           │ │
│  │                                                                                  │ │
│  │  🎤 Tech Check Appointment                              May 10 (Time TBD)       │ │
│  │     Test AV equipment, screen sharing, backup plans                            │ │
│  │     [Schedule Time Slot] [Technical Requirements]                              │ │
│  │                                                                                  │ │
│  │  🍽️ Speaker Dinner RSVP                                May 14, 19:00           │ │
│  │     Restaurant Volkshaus, Zurich                                               │ │
│  │     [✓ Attending] [Dietary Restrictions]                                       │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── EVENT DAY SCHEDULE ───────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Your Presentation Day: May 15, 2025                                            │ │
│  │                                                                                  │ │
│  │  08:30  Speaker arrival & check-in                                              │ │
│  │  08:45  Tech setup in main hall                                                 │ │
│  │  09:00  Morning session begins                                                  │ │
│  │  09:45  YOUR PRESENTATION (45 min)  ← You are here                             │ │
│  │  10:30  Q&A Session (15 min)                                                    │ │
│  │  10:45  Coffee break & networking                                               │ │
│  │  11:00  Next speaker                                                            │ │
│  │                                                                                  │ │
│  │  [View Full Agenda] [Download Schedule] [Add to Calendar]                       │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── PREPARATION CHECKLIST ────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Pre-Event (Complete by May 10)          Event Day                             │ │
│  │  ☑ Submit bio and photo                  ☐ Arrive 45 min early                │ │
│  │  ☑ Provide abstract                      ☐ Bring laptop + adapter             │ │
│  │  ☐ Upload final slides                   ☐ Have backup on USB                 │ │
│  │  ☐ Complete tech check                   ☐ Test presentation mode              │ │
│  │  ☐ Review attendee questions             ☐ Bring business cards               │ │
│  │  ☐ Prepare backup demos                  ☐ Stay for networking                │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Speaker Communication Hub

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back                    Communication Center                         [Compose]      │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─── CONVERSATIONS ──────────────┬─── ACTIVE CHAT ─────────────────────────────────┐│
│  │                                │                                                  ││
│  │ [All] [Organizers] [Speakers]  │  Sally Organizer - Event Lead                   ││
│  │                                │  Spring Conference 2025                          ││
│  │ ┌──────────────────────────┐   │                                                  ││
│  │ │ 🔴 Sally O. - Organizer  │   │  ┌──────────────────────────────────────────────┐││
│  │ │ Abstract feedback         │   │  │ Sally: Hi Peter! I reviewed your abstract  │││
│  │ │ 2 new messages           │   │  │ and it looks great. Just two small points: │││
│  │ │                          │   │  │ 1. Can you mention specific tools?         │││
│  │ └──────────────────────────┘   │  │ 2. Add a customer success story?          │││
│  │                                │  │                          Yesterday, 14:30   │││
│  │ ┌──────────────────────────┐   │  ├──────────────────────────────────────────────┤││
│  │ │ Mark T. - Speaker Coord  │   │  │ You: Thanks Sally! I'll add Prometheus/    │││
│  │ │ Logistics update         │   │  │ Grafana details and our Swiss Re case.     │││
│  │ │ Read                      │   │  │                          Yesterday, 16:45   │││
│  │ └──────────────────────────┘   │  ├──────────────────────────────────────────────┤││
│  │                                │  │ Sally: Perfect! Also, would you be         │││
│  │ ┌──────────────────────────┐   │  │ interested in joining our panel discussion │││
│  │ │ Tech Support              │   │  │ after your talk? Topic: "K8s Future"       │││
│  │ │ AV requirements           │   │  │                            Today, 09:15     │││
│  │ │ Resolved ✓                │   │  ├──────────────────────────────────────────────┤││
│  │ └──────────────────────────┘   │  │ Sally: No pressure - just thought your     │││
│  │                                │  │ expertise would add great value!            │││
│  │ ┌──────────────────────────┐   │  │ [👍] [👎] [Reply]          Today, 09:16     │││
│  │ │ Group: All Speakers      │   │  └──────────────────────────────────────────────┘││
│  │ │ Event updates            │   │                                                  ││
│  │ │ 12 members               │   │  ┌──────────────────────────────────────────────┐││
│  │ └──────────────────────────┘   │  │ Type your message...                        │││
│  │                                │  │                                              │││
│  └────────────────────────────────┘  └──────────────────────────────────────────────┘││
│                                       [📎 Attach] [😊] [Send]                        ││
│                                                                                       │
│  ┌─── ANNOUNCEMENTS ────────────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  📢 From: Event Team                                         Mar 28, 10:00      │ │
│  │  Subject: Agenda Published - Check Your Time Slots                              │ │
│  │  The preliminary agenda is now live. Please confirm your time slots...          │ │
│  │  [Read More]                                                                    │ │
│  │                                                                                  │ │
│  │  📢 From: Tech Support                                       Mar 25, 14:00      │ │
│  │  Subject: Presentation Template Available                                       │ │
│  │  Download the official BATbern PowerPoint template from...                      │ │
│  │  [Download Template]                                                            │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── QUICK CONTACTS ───────────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Event Team:                    Technical Support:                              │ │
│  │  Sally O. (Lead)                📧 tech@batbern.ch                             │ │
│  │  📧 sally@batbern.ch           📱 Emergency: +41 79 XXX XXXX                   │ │
│  │  💬 Available now                                                               │ │
│  │                                 Venue Contact:                                  │ │
│  │  Mark T. (Speakers)             Kursaal Bern                                   │ │
│  │  📧 mark@batbern.ch            📱 +41 31 XXX XXXX                              │ │
│  │  💬 Away - back at 14:00                                                        │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 7. Presentation Upload & Management

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back               Presentation Materials - Spring Conference 2025                 │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  Kubernetes Best Practices for Production Environments                               │
│  Due: April 30, 2025 (27 days remaining)                    Status: In Progress      │
│                                                                                       │
│  ┌─── PRESENTATION SLIDES ─────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Main Presentation *                                                            │ │
│  │  ┌──────────────────────────────────────────────────────────────────────┐      │ │
│  │  │                                                                        │      │ │
│  │  │                         📊 Drop PowerPoint/PDF                        │      │ │
│  │  │                             or Browse Files                           │      │ │
│  │  │                                                                        │      │ │
│  │  │                    Accepted: .pptx, .pdf, .key                        │      │ │
│  │  │                    Max size: 50MB                                     │      │ │
│  │  │                                                                        │      │ │
│  │  │                         [Browse Files]                                │      │ │
│  │  └──────────────────────────────────────────────────────────────────────┘      │ │
│  │                                                                                  │ │
│  │  ✓ k8s-best-practices-v2.pptx                                                  │ │
│  │    Uploaded: Mar 28, 14:30 • 28 slides • 12.3MB                                │ │
│  │    [Preview] [Replace] [Delete]                                                 │ │
│  │                                                                                  │ │
│  │  Version History:                                                               │ │
│  │  • v2 - Current (Mar 28)                                                        │ │
│  │  • v1 - Initial draft (Mar 20) [Restore]                                        │ │
│  │                                                                                  │ │
│  │  Backup/Demo Materials (optional)                                               │ │
│  │  ┌──────────────────────────────────────────────────────────────────────┐      │ │
│  │  │ + Add backup slides, demo scripts, or video fallbacks                │      │ │
│  │  └──────────────────────────────────────────────────────────────────────┘      │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── SUPPLEMENTARY MATERIALS ─────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Code Examples / GitHub Repository                                              │ │
│  │  ┌─────────────────────────────────────────────────────────────┐               │ │
│  │  │ https://github.com/pmuller/k8s-production-patterns          │               │ │
│  │  └─────────────────────────────────────────────────────────────┘               │ │
│  │                                                                                  │ │
│  │  Additional Resources for Attendees                                             │ │
│  │  ┌─────────────────────────────────────────────────────────────┐               │ │
│  │  │ • Monitoring Setup Guide (monitoring-guide.pdf)             │ [Remove]      │ │
│  │  │ • Example Configurations (configs.zip)                      │ [Remove]      │ │
│  │  │ • Recommended Reading List (resources.md)                   │ [Remove]      │ │
│  │  └─────────────────────────────────────────────────────────────┘               │ │
│  │  [+ Add Resource]                                                               │ │
│  │                                                                                  │ │
│  │  ☑ Make resources available for download after event                           │ │
│  │  ☑ Include my contact info for follow-up questions                             │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── PRESENTATION REQUIREMENTS ────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  ✓ Format: 16:9 aspect ratio                  ⚠️ Branding: Add BATbern logo    │ │
│  │  ✓ Duration: Fits 45-minute slot              ✓ Accessibility: High contrast   │ │
│  │  ✓ Font size: Minimum 24pt                    ⚠️ Page numbers: Add to slides   │ │
│  │                                                                                  │ │
│  │  [Download Template] [View Guidelines] [Check Accessibility]                    │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  [Save Progress] [Submit for Review] [Request Feedback]                              │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Speaker Community & Networking

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back                        BATbern Speaker Community                    [Search]  │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─── SPEAKER NETWORK ─────────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Fellow Speakers at Spring Conference 2025                      [View All 8]    │ │
│  │                                                                                  │ │
│  │  ┌────────────────┐ ┌────────────────┐ ┌────────────────┐ ┌────────────────┐  │ │
│  │  │     Sara K.    │ │   Thomas W.    │ │    Anna L.     │ │    Marc B.     │  │ │
│  │  │   [Photo]      │ │   [Photo]      │ │   [Photo]      │ │   [Photo]      │  │ │
│  │  │                │ │                │ │                │ │                │  │ │
│  │  │ Docker Expert  │ │ Cloud Security │ │ AI/ML Engineer │ │ DevOps Lead    │  │ │
│  │  │ ⭐ 4.9 (15)    │ │ ⭐ 4.7 (8)     │ │ ⭐ 4.8 (6)     │ │ ⭐ New Speaker │  │ │
│  │  │                │ │                │ │                │ │                │  │ │
│  │  │ [Connect]      │ │ [Connect]      │ │ [Connected ✓]  │ │ [Connect]      │  │ │
│  │  └────────────────┘ └────────────────┘ └────────────────┘ └────────────────┘  │ │
│  │                                                                                  │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── KNOWLEDGE SHARING ──────────────┬─── MENTORSHIP ──────────────────────────────┐│
│  │                                    │                                              ││
│  │  Recent Discussions                │  Mentor Matching                             ││
│  │                                    │                                              ││
│  │  "Tips for first-time speakers?"   │  Find a Mentor:                             ││
│  │  By Marc B. • 12 replies • 2h ago  │  ○ Presentation skills                      ││
│  │  [View Thread]                     │  ● Technical topics (K8s, Cloud)            ││
│  │                                    │  ○ Career development                       ││
│  │  "Demo disaster recovery plans"    │                                              ││
│  │  By Sara K. • 8 replies • 1d ago   │  Available Mentors:                         ││
│  │  [View Thread]                     │  • Peter M. (You) - 2 mentees               ││
│  │                                    │  • Dr. Weber - 1 mentee                     ││
│  │  "Handling difficult questions"    │  • Lisa Chen - 3 mentees                    ││
│  │  By You • 15 replies • 3d ago      │                                              ││
│  │  [View Thread]                     │  [Become a Mentor] [Find Mentor]            ││
│  │                                    │                                              ││
│  │  [Start Discussion]                │                                              ││
│  └────────────────────────────────────┴──────────────────────────────────────────────┘│
│                                                                                       │
│  ┌─── RESOURCES & TOOLS ────────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Speaker Toolkit                          Shared by Community                   │ │
│  │                                                                                  │ │
│  │  📚 Presentation Templates                📊 "K8s Security Checklist"          │ │
│  │     BATbern branded, ready to use            By Thomas W., last week           │ │
│  │     [Download Pack]                          [View] [Download]                 │ │
│  │                                                                                  │ │
│  │  🎯 Demo Best Practices Guide             💡 "Live Coding Tips & Tricks"        │ │
│  │     Avoid common pitfalls                    By Sara K., 2 weeks ago          │ │
│  │     [Read Guide]                             [Read] [Bookmark]                 │ │
│  │                                                                                  │ │
│  │  🎤 Public Speaking Course                📝 "Abstract Writing Workshop"        │ │
│  │     Free for BATbern speakers                Recording from last month         │ │
│  │     [Enroll Now]                             [Watch] [Materials]              │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── YOUR SPEAKING STATS ──────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Performance Dashboard                    Growth Tracking                       │ │
│  │                                                                                  │ │
│  │  Latest Talk Rating: 4.8/5 ⭐            Topics Covered: 8                     │ │
│  │  Total Attendees: 1,247                  Events Spoken: 12                     │ │
│  │  Slide Downloads: 892                    Years Active: 4                        │ │
│  │  Questions Answered: 67                  Ranking: Top 15%                       │ │
│  │                                                                                  │ │
│  │  Feedback Highlights:                    Next Goals:                            │ │
│  │  "Clear explanations" (18x)              • Reach 1500 attendees ☐              │ │
│  │  "Great demos" (12x)                     • Speak at 15 events ☐                │ │
│  │  "Engaging presenter" (10x)              • Mentor 3 speakers ☐                 │ │
│  │                                                                                  │ │
│  │  [View Detailed Analytics] [Download Speaker Report]                            │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Mobile Responsive Views (Examples)

### Mobile: Speaker Dashboard (iPhone 375px)
```
┌─────────────────────┐
│ ☰  BATbern  🔔2  👤 │
├─────────────────────┤
│                     │
│ Hi Peter! 👋        │
│                     │
│ ┌─────────────────┐ │
│ │ UPCOMING EVENT  │ │
│ │                 │ │
│ │ Spring Conf'25  │ │
│ │ May 15 • 09:45  │ │
│ │                 │ │
│ │ ⚠️ Slides due   │ │
│ │    Apr 30       │ │
│ │                 │ │
│ │ [View] [Upload] │ │
│ └─────────────────┘ │
│                     │
│ ┌─────────────────┐ │
│ │ NEW INVITATION  │ │
│ │                 │ │
│ │ Autumn Conf'25  │ │
│ │ Respond by 3/15 │ │
│ │                 │ │
│ │ [Respond Now]   │ │
│ └─────────────────┘ │
│                     │
│ Quick Actions:      │
│ [📝 Materials]      │
│ [💬 Messages]       │
│ [📊 My Stats]       │
│                     │
└─────────────────────┘
```

### Mobile: Material Upload (iPhone)
```
┌─────────────────────┐
│ ← Submit Materials  │
├─────────────────────┤
│                     │
│ Step 2 of 5         │
│ ●●○○○               │
│                     │
│ ABSTRACT            │
│                     │
│ ┌─────────────────┐ │
│ │ Your abstract   │ │
│ │ here...         │ │
│ │                 │ │
│ │                 │ │
│ │                 │ │
│ └─────────────────┘ │
│ 450/1000 chars      │
│                     │
│ ✓ Length OK         │
│ ⚠️ Add lessons      │
│                     │
│ [← Back] [Next →]   │
│                     │
│ 💡 Tip: Include     │
│ real-world lessons  │
│                     │
└─────────────────────┘
```

---

## Interactive Elements & Behaviors

### Speaker-Specific Interactions
- **Drag & Drop**: Presentation files, photos, resource documents
- **Auto-save**: Every 30 seconds for material submissions
- **Progress Tracking**: Visual indicators for submission completeness
- **Smart Validation**: Real-time feedback on abstract length, required fields
- **Calendar Integration**: Export deadlines to Google/Outlook/iCal

### Notification System
- **Push Notifications**: Deadline reminders, organizer messages
- **Email Digests**: Weekly summary of upcoming tasks
- **In-app Alerts**: Real-time updates on schedule changes
- **Customizable**: Set quiet hours, notification preferences

### Communication Features
- **Real-time Chat**: WebSocket-based messaging with organizers
- **Thread Support**: Organized conversations by topic
- **File Sharing**: Direct attachment of presentations and materials
- **Read Receipts**: Know when organizers have seen your messages

### Mobile Optimizations
- **Touch Targets**: Minimum 44px for all interactive elements
- **Swipe Gestures**: Navigate between submission steps
- **Offline Support**: Queue submissions when disconnected
- **Camera Integration**: Direct photo upload from device
- **Voice Notes**: Record speaker bio or notes

---

## Technical Implementation Notes

### Component Architecture
```
SpeakerPortal/
├── Dashboard/
│   ├── SpeakerDashboard.tsx
│   ├── EventCards.tsx
│   └── QuickActions.tsx
├── Invitation/
│   ├── ResponseForm.tsx
│   ├── PreferencesSelector.tsx
│   └── ConfirmationFlow.tsx
├── Submission/
│   ├── MaterialWizard.tsx
│   ├── StepComponents/
│   │   ├── BasicInfo.tsx
│   │   ├── Abstract.tsx
│   │   ├── Biography.tsx
│   │   ├── PhotoUpload.tsx
│   │   └── Presentation.tsx
│   └── ValidationEngine.tsx
├── Communication/
│   ├── MessageCenter.tsx
│   ├── ChatInterface.tsx
│   └── Announcements.tsx
└── Community/
    ├── SpeakerNetwork.tsx
    ├── Discussions.tsx
    └── Resources.tsx
```

### State Management
- **Form State**: React Hook Form with validation
- **File Uploads**: Resumable uploads with progress tracking
- **Real-time Updates**: WebSocket for chat and notifications
- **Offline Queue**: IndexedDB for offline submissions

### Accessibility
- **Screen Readers**: ARIA labels and live regions
- **Keyboard Navigation**: Full keyboard support
- **Focus Management**: Logical focus flow through forms
- **Error Announcements**: Clear error messaging
- **High Contrast**: Support for high contrast mode

---

## Next Steps

These comprehensive speaker wireframes provide:

1. **Complete speaker journey** from invitation to post-event
2. **Self-service capabilities** reducing organizer workload
3. **Mobile-first design** for on-the-go speakers
4. **Community features** fostering speaker engagement
5. **Clear submission workflows** ensuring quality content

The wireframes can be directly translated into React components following the epic timeline, with special focus on Epic 3 (Core Speaker Management) implementation.