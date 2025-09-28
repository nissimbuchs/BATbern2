# BATbern Attendee Experience - Complete Wireframes

## Overview
This document contains detailed wireframes for all attendee interfaces, covering the complete attendee journey from discovering events to accessing historical content, with emphasis on the prominent current event display (FR6) and intelligent content discovery (FR13).

---

## 1. Current Event Landing Page (Homepage - FR6 Compliant)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ BATbern                                    Upcoming Events  Archive  [EN|DE] [Login] │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │            🚀 SPRING CONFERENCE 2025: CLOUD NATIVE ARCHITECTURE                  │ │
│  │                                                                                   │ │
│  │                          MAY 15, 2025 • KURSAAL BERN                             │ │
│  │                                                                                   │ │
│  │                    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━                          │ │
│  │                                                                                   │ │
│  │                              🎟️ FREE ADMISSION                                   │ │
│  │                         Limited Seats - Register Now!                            │ │
│  │                                                                                   │ │
│  │                         [ REGISTER FOR FREE → ]                                  │ │
│  │                                                                                   │ │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── EVENT HIGHLIGHTS ───────────────────────────────────────────────────────────┐  │
│  │                                                                                  │  │
│  │  📍 Location           ⏰ Schedule              🎯 Topics                        │  │
│  │  Kursaal Bern         08:30 Registration      • Kubernetes at Scale             │  │
│  │  Kornhausstrasse 3    09:00 Keynote           • Service Mesh                    │  │
│  │  3013 Bern           17:30 Networking         • Cloud Security                  │  │
│  │  [Get Directions]     [Full Schedule]         • GitOps & CI/CD                  │  │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── FEATURED SPEAKERS ──────────────────────────────────────────────────────────┐  │
│  │                                                                                  │  │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │  │
│  │  │              │  │              │  │              │  │              │      │  │
│  │  │   [Photo]    │  │   [Photo]    │  │   [Photo]    │  │   [Photo]    │      │  │
│  │  │              │  │              │  │              │  │              │      │  │
│  │  │  Sara Kim    │  │ Peter Muller │  │ Thomas Weber │  │  Anna Lopez  │      │  │
│  │  │              │  │              │  │              │  │              │      │  │
│  │  │ Docker Inc.  │  │  TechCorp    │  │  Swiss Re    │  │   Google     │      │  │
│  │  │              │  │              │  │              │  │              │      │  │
│  │  │ "Container   │  │ "K8s Best    │  │ "Zero Trust  │  │ "AI/ML on    │      │  │
│  │  │  Security"   │  │  Practices"  │  │  Security"   │  │  Kubernetes" │      │  │
│  │  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘      │  │
│  │                                                                                  │  │
│  │                            [View All 8 Speakers →]                              │  │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── AGENDA AT A GLANCE ─────────────────────────────────────────────────────────┐  │
│  │                                                                                  │  │
│  │  Morning Sessions (Technical Deep Dives)     Afternoon (Practical Applications) │  │
│  │                                                                                  │  │
│  │  09:00  Keynote: Future of Cloud Native      13:30  Workshop: Hands-on K8s     │  │
│  │  09:45  Kubernetes Best Practices            14:15  Panel: Security Challenges  │  │
│  │  10:30  Coffee Break & Networking            15:00  Container Orchestration     │  │
│  │  11:00  Service Mesh Architecture            15:45  Coffee & Demos              │  │
│  │  11:45  GitOps Implementation                16:15  Closing Keynote             │  │
│  │  12:30  Lunch Break                          17:00  Networking Apéro            │  │
│  │                                                                                  │  │
│  │                         [Download Full Agenda (PDF)]                            │  │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── WHY ATTEND ─────────────┬──── QUICK LINKS ────────────────────────────────┐   │
│  │                              │                                                  │   │
│  │  ✓ Learn from industry       │  📚 Browse Past Events                          │   │
│  │    experts                    │  📊 Download Presentations                      │   │
│  │  ✓ Network with 200+ IT      │  🔍 Search Archive (20+ years)                  │   │
│  │    professionals              │  📧 Subscribe to Newsletter                     │   │
│  │  ✓ Free admission & lunch    │  👥 Join Community                              │   │
│  │  ✓ Practical takeaways       │  🏢 Become a Partner                            │   │
│  │  ✓ CPE credits available     │                                                  │   │
│  └──────────────────────────────┴──────────────────────────────────────────────────┘  │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### Key Features:
- **Prominent current event** display above the fold
- **Free admission** clearly highlighted
- **Complete logistics** (date, location, time) immediately visible
- **One-click registration** with clear CTA
- **Secondary navigation** to archives below primary content

---

## 2. AI-Powered Content Discovery Engine (FR13)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back to Home              BATbern Knowledge Hub                    [Login/Register]│
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐ │
│  │  🔍 What are you looking for?                                                    │ │
│  │  ┌──────────────────────────────────────────────────────────────────────┐       │ │
│  │  │ Search presentations, speakers, topics...                             │ 🎤    │ │
│  │  └──────────────────────────────────────────────────────────────────────┘       │ │
│  │                                                                                   │ │
│  │  Suggestions: "Kubernetes security" "Thomas Weber" "DevOps 2023" "AI/ML"         │ │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── SMART FILTERS ──────────────┬─── YOUR INTERESTS ─────────────────────────────┐ │
│  │                                 │                                                │ │
│  │  Topic Categories               │  Based on your activity:                      │ │
│  │  ☑ Cloud Native (142)          │  • Kubernetes & Containers                     │ │
│  │  ☐ Security (89)               │  • DevOps Practices                            │ │
│  │  ☐ AI/ML (67)                  │  • Cloud Architecture                          │ │
│  │  ☐ DevOps (134)                │                                                │ │
│  │  ☐ Data Engineering (45)       │  [Customize Interests]                         │ │
│  │                                 │                                                │ │
│  │  Time Period                    │  🤖 AI Recommendations                         │ │
│  │  ○ Last Month                  │  "Since you enjoyed 'K8s Security', you       │ │
│  │  ○ Last Year                   │   might like these presentations..."          │ │
│  │  ● Last 5 Years                │                                                │ │
│  │  ○ All Time (20+ years)        │  [View Recommendations →]                      │ │
│  │                                 │                                                │ │
│  │  Content Type                   │                                                │ │
│  │  ☑ Presentations               │                                                │ │
│  │  ☑ Videos                      │                                                │ │
│  │  ☐ Code Examples               │                                                │ │
│  │  ☐ Workshop Materials          │                                                │ │
│  │                                 │                                                │ │
│  │  [Apply Filters] [Clear All]    │                                                │ │
│  └─────────────────────────────────┴────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── SEARCH RESULTS (247 items) ──────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Sort by: [Relevance ▼] [Date ▼] [Rating ▼] [Downloads ▼]     View: [▦▦] [☰]  │ │
│  │                                                                                  │ │
│  │  ┌────────────────────────────────────────────────────────────────────────────┐ │ │
│  │  │ 95% Match                                                                   │ │ │
│  │  │                                                                              │ │ │
│  │  │  🎯 Kubernetes Security Best Practices                                      │ │ │
│  │  │  Thomas Weber • Spring 2024 • ⭐ 4.8/5 • 👁️ 1,247 views • ⬇ 523            │ │ │
│  │  │                                                                              │ │ │
│  │  │  "...implementing zero-trust security in Kubernetes environments with       │ │ │
│  │  │  practical examples from Swiss financial sector compliance..."              │ │ │
│  │  │                                                                              │ │ │
│  │  │  Tags: #kubernetes #security #compliance #fintech                           │ │ │
│  │  │                                                                              │ │ │
│  │  │  [View] [Download PDF] [Watch Video] [💾 Save] [Share]                     │ │ │
│  │  └────────────────────────────────────────────────────────────────────────────┘ │ │
│  │                                                                                  │ │
│  │  ┌────────────────────────────────────────────────────────────────────────────┐ │ │
│  │  │ 87% Match                                                                   │ │ │
│  │  │                                                                              │ │ │
│  │  │  🔒 Container Security Deep Dive                                            │ │ │
│  │  │  Sara Kim • Autumn 2023 • ⭐ 4.9/5 • 👁️ 2,103 views • ⬇ 891               │ │ │
│  │  │                                                                              │ │ │
│  │  │  "...comprehensive security scanning in CI/CD pipelines with                │ │ │
│  │  │  lessons learned from production incidents..."                              │ │ │
│  │  │                                                                              │ │ │
│  │  │  Tags: #docker #security #cicd #devops                                      │ │ │
│  │  │                                                                              │ │ │
│  │  │  [View] [Download PDF] [Watch Video] [💾 Save] [Share]                     │ │ │
│  │  └────────────────────────────────────────────────────────────────────────────┘ │ │
│  │                                                                                  │ │
│  │  [Load More Results...]                                                         │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── LEARNING PATHS ────────────────────────────────────────────────────────────┐   │
│  │                                                                                │   │
│  │  📚 Curated Learning Paths Based on Your Search                               │   │
│  │                                                                                │   │
│  │  Kubernetes Journey: Beginner → Expert                                        │   │
│  │  ━━━━━━━━━━━━━━━━●━━━━━━━━━━  65% Complete                                   │   │
│  │  Next: "Advanced Networking in K8s" by Marc Baum                             │   │
│  │  [Continue Learning →]                                                        │   │
│  └────────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Event Registration Flow

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back                  Register for Spring Conference 2025                          │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐ │
│  │  Registration Progress:  ●━━━━━━━━━○━━━━━━━━━○━━━━━━━━━○                        │ │
│  │                         Details    Sessions   Confirm                            │ │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── STEP 1: YOUR DETAILS ──────────────────────────────────────────────────────┐  │
│  │                                                                                 │  │
│  │  Personal Information                                                          │  │
│  │                                                                                 │  │
│  │  First Name *                    Last Name *                                   │  │
│  │  ┌─────────────────────┐        ┌─────────────────────┐                      │  │
│  │  │ John                │        │ Smith               │                      │  │
│  │  └─────────────────────┘        └─────────────────────┘                      │  │
│  │                                                                                 │  │
│  │  Email Address *                                                               │  │
│  │  ┌──────────────────────────────────────────────────────┐                     │  │
│  │  │ john.smith@company.ch                                │                     │  │
│  │  └──────────────────────────────────────────────────────┘                     │  │
│  │  ✓ We'll send your ticket and event updates here                             │  │
│  │                                                                                 │  │
│  │  Company/Organization *          Job Title                                     │  │
│  │  ┌─────────────────────┐        ┌─────────────────────┐                      │  │
│  │  │ TechCorp AG         │        │ Senior Developer    │                      │  │
│  │  └─────────────────────┘        └─────────────────────┘                      │  │
│  │                                                                                 │  │
│  │  Industry Sector                                                               │  │
│  │  ┌──────────────────────────────────────────────────────┐                     │  │
│  │  │ Financial Services ▼                                  │                     │  │
│  │  └──────────────────────────────────────────────────────┘                     │  │
│  │                                                                                 │  │
│  │  Years of Experience                                                           │  │
│  │  ○ 0-2 years  ○ 3-5 years  ● 6-10 years  ○ 10+ years                        │  │
│  │                                                                                 │  │
│  │  Dietary Requirements (optional)                                               │  │
│  │  ☐ Vegetarian  ☐ Vegan  ☐ Gluten-free  ☐ Other: _________                   │  │
│  │                                                                                 │  │
│  │  How did you hear about us?                                                    │  │
│  │  ☐ Previous attendee  ☑ Colleague  ☐ LinkedIn  ☐ Company                     │  │
│  │                                                                                 │  │
│  │                                            [Next: Choose Sessions →]           │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘

[STEP 2: SESSION PREFERENCES]
┌─────────────────────────────────────────────────────────────────────────────────────┐
│  ┌──── STEP 2: SESSION PREFERENCES ───────────────────────────────────────────────┐  │
│  │                                                                                 │  │
│  │  Select sessions you're most interested in (helps with capacity planning)      │  │
│  │                                                                                 │  │
│  │  Morning Sessions (09:00 - 12:30)                                             │  │
│  │  ┌─────────────────────────────────────────────────────────────────────────┐  │  │
│  │  │ ☑ Keynote: Future of Cloud Native (Required)                            │  │  │
│  │  │ ☑ Kubernetes Best Practices - Peter Muller                              │  │  │
│  │  │ ☐ Service Mesh Architecture - Anna Lopez                                │  │  │
│  │  │ ☐ GitOps Implementation - Marc Baum                                     │  │  │
│  │  └─────────────────────────────────────────────────────────────────────────┘  │  │
│  │                                                                                 │  │
│  │  Afternoon Sessions (13:30 - 17:00)                                           │  │
│  │  ┌─────────────────────────────────────────────────────────────────────────┐  │  │
│  │  │ ☑ Workshop: Hands-on Kubernetes (Limited: 27/30 seats)                  │  │  │
│  │  │ ☐ Panel: Security Challenges in Cloud Native                            │  │  │
│  │  │ ☑ Container Orchestration at Scale                                      │  │  │
│  │  │ ☐ Closing Keynote: AI/ML on Kubernetes                                  │  │  │
│  │  └─────────────────────────────────────────────────────────────────────────┘  │  │
│  │                                                                                 │  │
│  │  Topics of Interest (for future events)                                       │  │
│  │  ☑ Kubernetes  ☑ Security  ☐ AI/ML  ☑ DevOps  ☐ Data Engineering            │  │
│  │                                                                                 │  │
│  │  [← Back]                                          [Next: Confirm →]          │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────┘

[STEP 3: CONFIRMATION]
┌─────────────────────────────────────────────────────────────────────────────────────┐
│  ┌──── STEP 3: CONFIRM REGISTRATION ──────────────────────────────────────────────┐  │
│  │                                                                                 │  │
│  │  Review Your Registration                                                      │  │
│  │  ┌─────────────────────────────────────────────────────────────────────────┐  │  │
│  │  │ Event:     Spring Conference 2025: Cloud Native Architecture             │  │  │
│  │  │ Date:      May 15, 2025                                                  │  │  │
│  │  │ Location:  Kursaal Bern                                                  │  │  │
│  │  │ Time:      08:30 - 18:00                                                 │  │  │
│  │  │ Price:     FREE (including lunch & coffee)                               │  │  │
│  │  │                                                                           │  │  │
│  │  │ Attendee:  John Smith                                                    │  │  │
│  │  │ Email:     john.smith@company.ch                                         │  │  │
│  │  │ Company:   TechCorp AG                                                   │  │  │
│  │  │                                                                           │  │  │
│  │  │ Selected Sessions:                                                       │  │  │
│  │  │ • Morning: Keynote, Kubernetes Best Practices                            │  │  │
│  │  │ • Afternoon: Workshop, Container Orchestration                           │  │  │
│  │  └─────────────────────────────────────────────────────────────────────────┘  │  │
│  │                                                                                 │  │
│  │  Communication Preferences                                                     │  │
│  │  ☑ Send me event reminders (1 week and 1 day before)                         │  │
│  │  ☑ Subscribe to BATbern newsletter (monthly)                                  │  │
│  │  ☐ Notify me about similar future events                                      │  │
│  │                                                                                 │  │
│  │  Terms & Conditions                                                            │  │
│  │  ☑ I agree to the event terms and photo/video policy                         │  │
│  │  ☑ I understand this is a free event with limited capacity                   │  │
│  │                                                                                 │  │
│  │  [← Back]                                   [Complete Registration]           │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Personal Attendee Dashboard (Logged In)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ BATbern                                                    John Smith ▼  [Logout]    │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  Welcome back, John! Here's your personalized BATbern experience.                    │
│                                                                                       │
│  ┌──── YOUR UPCOMING EVENTS ──────────────────────────────────────────────────────┐  │
│  │                                                                                 │  │
│  │  ┌──────────────────────────────────────────────────────────────────────────┐  │  │
│  │  │ 📅 Spring Conference 2025                           In 45 days            │  │  │
│  │  │ May 15, 2025 • Kursaal Bern                                              │  │  │
│  │  │                                                                           │  │  │
│  │  │ Your Schedule:                              Actions:                     │  │  │
│  │  │ • 09:45 Kubernetes Best Practices            [📅 Add to Calendar]        │  │  │
│  │  │ • 13:30 Hands-on Workshop                    [📧 Email Ticket]           │  │  │
│  │  │ • 15:00 Container Orchestration               [📍 Get Directions]        │  │  │
│  │  │                                                [👥 Who's Attending]       │  │  │
│  │  └──────────────────────────────────────────────────────────────────────────┘  │  │
│  │                                                                                 │  │
│  │  No other events registered. [Browse Upcoming Events →]                        │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── RECOMMENDED FOR YOU ────────┬──── YOUR CONTENT LIBRARY ──────────────────┐   │
│  │                                  │                                             │   │
│  │  Based on your interests:        │  Saved Presentations (12)                  │   │
│  │                                  │                                             │   │
│  │  🎯 "Zero Trust in K8s"          │  📄 K8s Security Best Practices            │   │
│  │     Thomas Weber • 2024          │     Thomas Weber • 2024 • Downloaded       │   │
│  │     95% match to your interests  │                                             │   │
│  │     [View] [Save]                │  📄 Container Orchestration Guide          │   │
│  │                                  │     Sara Kim • 2023 • Downloaded           │   │
│  │  🔥 "GitOps Workflows"           │                                             │   │
│  │     Marc Baum • 2024             │  📄 DevOps Transformation                   │   │
│  │     Trending in your company     │     Peter Muller • 2023 • Bookmarked       │   │
│  │     [View] [Save]                │                                             │   │
│  │                                  │  [View All] [Manage Downloads]             │   │
│  │  [More Recommendations →]         │                                             │   │
│  └──────────────────────────────────┴─────────────────────────────────────────────┘  │
│                                                                                       │
│  ┌──── YOUR LEARNING PATH ─────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Kubernetes Mastery Track                                    Level: Intermediate │ │
│  │  ━━━━━━━━━━━━━━━━━━━━●━━━━━━━━━━━━━━  60% Complete                             │ │
│  │                                                                                  │ │
│  │  ✓ Introduction to Containers        ✓ K8s Fundamentals      ● K8s Security    │ │
│  │  ✓ Docker Deep Dive                  ✓ Advanced Deployments  ○ Service Mesh    │ │
│  │                                                                                  │ │
│  │  Next: "Kubernetes Security" - 45 min estimated                                 │ │
│  │  [Continue Learning] [Change Path] [View Achievements]                          │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── COMMUNITY ACTIVITY ──────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  🏆 Your Stats                        📊 Community Pulse                       │ │
│  │  Events Attended: 8                   Hot Topics This Month:                   │ │
│  │  Presentations Viewed: 47             • AI/ML on Kubernetes (⬆ 45%)            │ │
│  │  Community Points: 230                • FinOps & Cost Control (⬆ 32%)          │ │
│  │  Rank: Active Member                  • Platform Engineering (New!)            │ │
│  │                                                                                  │ │
│  │  [View Profile] [Leaderboard]         [Join Discussion →]                       │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Historical Archive Browser

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back                    BATbern Archive: 20+ Years of Knowledge                    │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─── BROWSE BY YEAR ──────────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  2025  2024  2023  2022  2021  2020  2019  2018  2017  2016  [Show All Years] │ │
│  │   ●                                                                              │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── 2025 EVENTS ─────────────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  ┌────────────────────────────────────────────────────────────────────────────┐ │ │
│  │  │ 🗓️ Spring Conference 2025                          May 15, 2025           │ │ │
│  │  │ Cloud Native Architecture                          8 Speakers • 247 Attendees│ │ │
│  │  │                                                                             │ │ │
│  │  │ Featured Presentations:                                                     │ │ │
│  │  │ • Kubernetes Best Practices - P. Muller            ⭐ 4.8 • ⬇ 523          │ │ │
│  │  │ • Container Security - S. Kim                      ⭐ 4.9 • ⬇ 891          │ │ │
│  │  │ • Zero Trust Architecture - T. Weber               ⭐ 4.7 • ⬇ 445          │ │ │
│  │  │                                                                             │ │ │
│  │  │ [View Event] [Browse Presentations] [Photo Gallery] [Attendee List]        │ │ │
│  │  └────────────────────────────────────────────────────────────────────────────┘ │ │
│  │                                                                                  │ │
│  │  ┌────────────────────────────────────────────────────────────────────────────┐ │ │
│  │  │ 🗓️ Summer Workshop 2025                           July 20, 2025 (Upcoming)│ │ │
│  │  │ AI/ML in Production                                Registration Open       │ │ │
│  │  │                                                                             │ │ │
│  │  │ Confirmed Speakers:                                                        │ │ │
│  │  │ • Deep Learning at Scale - Dr. A. Mueller                                  │ │ │
│  │  │ • MLOps Best Practices - L. Chen                                           │ │ │
│  │  │ • More speakers being confirmed...                                         │ │ │
│  │  │                                                                             │ │ │
│  │  │ [Register Now] [View Details] [Set Reminder]                               │ │ │
│  │  └────────────────────────────────────────────────────────────────────────────┘ │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── EXPLORE BY TOPIC ────────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  📊 Topic Evolution Over Time                                                   │ │
│  │                                                                                  │ │
│  │  Kubernetes     ████████████████████ 142 presentations                         │ │
│  │  DevOps         ███████████████████  134 presentations                         │ │
│  │  Security       ████████████         89 presentations                          │ │
│  │  Cloud Native   ████████████         78 presentations                          │ │
│  │  AI/ML          █████████            67 presentations                          │ │
│  │  Microservices  ████████             56 presentations                          │ │
│  │  Data Eng.      ██████               45 presentations                          │ │
│  │                                                                                  │ │
│  │  [View Topic Timeline] [Compare Topics] [Download Stats]                        │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── SPEAKERS HALL OF FAME ───────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Most Presentations          Highest Rated           Most Downloaded            │ │
│  │  1. Thomas Weber (15)        1. Sara Kim (4.9)      1. Peter Muller (3,421)    │ │
│  │  2. Sara Kim (12)            2. Anna Lopez (4.8)    2. Sara Kim (2,987)        │ │
│  │  3. Peter Muller (12)        3. Marc Baum (4.8)     3. Thomas Weber (2,654)    │ │
│  │                                                                                  │ │
│  │  [View All Speakers] [Speaker Directory] [Become a Speaker]                     │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Community Features & Engagement

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back                         BATbern Community Hub                                 │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─── CONTENT RATINGS & REVIEWS ──────────────────────────────────────────────────┐  │
│  │                                                                                 │  │
│  │  Recent Community Activity                                                     │  │
│  │                                                                                 │  │
│  │  ┌─────────────────────────────────────────────────────────────────────────┐  │  │
│  │  │ ⭐⭐⭐⭐⭐ "Excellent deep dive into K8s security"                         │  │  │
│  │  │ John S. reviewed "Kubernetes Security Best Practices"                   │  │  │
│  │  │ "The real-world examples and failure stories made this incredibly       │  │  │
│  │  │  valuable. Implementing these saved us from a major incident."          │  │  │
│  │  │ 👍 Helpful (42)  💬 Comments (8)                         2 hours ago    │  │  │
│  │  └─────────────────────────────────────────────────────────────────────────┘  │  │
│  │                                                                                 │  │
│  │  ┌─────────────────────────────────────────────────────────────────────────┐  │  │
│  │  │ ⭐⭐⭐⭐ "Great intro but wanted more advanced content"                     │  │  │
│  │  │ Maria T. reviewed "Docker Fundamentals"                                  │  │  │
│  │  │ "Perfect for beginners. Would love a follow-up on multi-stage builds"   │  │  │
│  │  │ 👍 Helpful (18)  💬 Comments (3)                         Yesterday       │  │  │
│  │  └─────────────────────────────────────────────────────────────────────────┘  │  │
│  │                                                                                 │  │
│  │  [Write Review] [View All Reviews] [Top Rated Content]                        │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── DISCUSSION FORUMS ────────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Active Discussions                                     [Start New Discussion]  │ │
│  │                                                                                  │ │
│  │  🔥 "Who's implementing Platform Engineering?"          42 replies • 2h ago     │ │
│  │     Latest: "We're building an IDP with Backstage..."                          │ │
│  │     [Join Discussion]                                                          │ │
│  │                                                                                  │ │
│  │  💬 "Best practices for FinOps in Switzerland?"        28 replies • 5h ago     │ │
│  │     Latest: "Swiss data residency requirements..."                             │ │
│  │     [Join Discussion]                                                          │ │
│  │                                                                                  │ │
│  │  📚 "Study group for CKA certification"                15 members • Active      │ │
│  │     Next meetup: May 1, online                                                 │ │
│  │     [Join Group]                                                               │ │
│  │                                                                                  │ │
│  │  [Browse All Topics] [My Discussions] [Trending]                               │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── SOCIAL SHARING ──────────────┬─── LEARNING PATHS ──────────────────────────┐  │
│  │                                  │                                             │  │
│  │  Share Your Learning             │  Community-Curated Paths                   │  │
│  │                                  │                                             │  │
│  │  "Just completed the Kubernetes  │  🎯 Zero to K8s Hero (6 months)            │  │
│  │   security track! 🎉"            │     Created by: Thomas W.                  │  │
│  │   - Posted by John S.            │     Followers: 234                         │  │
│  │                                  │     [Start Path]                            │  │
│  │  [🔗 LinkedIn] [🐦 Twitter]      │                                             │  │
│  │                                  │  🔒 Security First Developer                │  │
│  │  Your Share Stats:               │     Created by: Sara K.                    │  │
│  │  • Content shared: 12 times      │     Followers: 189                         │  │
│  │  • Reached: ~1,200 people        │     [Start Path]                            │  │
│  │  • Engagement rate: 8.5%         │                                             │  │
│  └──────────────────────────────────┴─────────────────────────────────────────────┘  │
│                                                                                       │
│  ┌─── ACHIEVEMENTS & GAMIFICATION ─────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Your Achievements                           Community Leaderboard              │ │
│  │                                                                                  │ │
│  │  🏆 Early Bird (Registered 1 month early)    1. Thomas W. - 2,450 pts         │ │
│  │  📚 Knowledge Seeker (10+ presentations)     2. Sara K. - 2,230 pts           │ │
│  │  🤝 Community Helper (5+ helpful reviews)    3. Anna L. - 1,890 pts           │ │
│  │  🎯 Learning Path Complete (1 path)          ...                               │ │
│  │  🔓 Next: Super Learner (25+ presentations) 47. You - 230 pts                  │ │
│  │                                                                                  │ │
│  │  [View All Badges] [Share Achievements]      [View Full Leaderboard]           │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 7. Mobile PWA Experience (iPhone/Android)

### Mobile: Current Event Landing
```
┌──────────────────────┐
│ ☰ BATbern    🔍  EN  │
├──────────────────────┤
│                      │
│  SPRING CONF 2025    │
│  ═══════════════     │
│                      │
│  CLOUD NATIVE        │
│  ARCHITECTURE        │
│                      │
│  📅 May 15, 2025     │
│  📍 Kursaal Bern     │
│                      │
│  ┌────────────────┐  │
│  │                │  │
│  │  REGISTER FREE │  │
│  │                │  │
│  └────────────────┘  │
│                      │
│  🎟️ FREE ADMISSION   │
│  Limited Seats!      │
│                      │
├──────────────────────┤
│  FEATURED SPEAKERS   │
│                      │
│  ┌─────┐ ┌─────┐    │
│  │Sara │ │Peter│ ►  │
│  │ Kim │ │Müller│    │
│  └─────┘ └─────┘    │
│                      │
│  [View All Speakers] │
│                      │
├──────────────────────┤
│  QUICK INFO          │
│                      │
│  ⏰ 08:30 - 18:00    │
│  🍽️ Lunch included   │
│  👥 200+ attendees   │
│  📚 8 sessions       │
│                      │
│  [Full Schedule ↓]   │
│                      │
├──────────────────────┤
│ [📚 Past Events]     │
│ [🔍 Search Archive]  │
│ [👤 My Account]      │
└──────────────────────┘
```

### Mobile: Content Discovery
```
┌──────────────────────┐
│ ← Search    Filter ⚙ │
├──────────────────────┤
│                      │
│ ┌──────────────────┐ │
│ │ 🔍 Search...     │ │
│ └──────────────────┘ │
│                      │
│ Recent: "kubernetes" │
│                      │
├──────────────────────┤
│ RESULTS (247)        │
│                      │
│ ┌──────────────────┐ │
│ │ K8s Security     │ │
│ │ Best Practices   │ │
│ │                  │ │
│ │ T. Weber • 2024  │ │
│ │ ⭐ 4.8 • 👁️ 1.2K  │ │
│ │                  │ │
│ │ [View] [Save]    │ │
│ └──────────────────┘ │
│                      │
│ ┌──────────────────┐ │
│ │ Container        │ │
│ │ Security         │ │
│ │                  │ │
│ │ S. Kim • 2023    │ │
│ │ ⭐ 4.9 • 👁️ 2.1K  │ │
│ │                  │ │
│ │ [View] [Save]    │ │
│ └──────────────────┘ │
│                      │
│ [Load More...]       │
│                      │
├──────────────────────┤
│ 🏠 📚 🔍 💾 👤        │
└──────────────────────┘
```

### Mobile: Registration Flow
```
┌──────────────────────┐
│ ← Register   Step 1/3 │
├──────────────────────┤
│                      │
│ YOUR DETAILS         │
│                      │
│ First Name *         │
│ ┌──────────────────┐ │
│ │ John             │ │
│ └──────────────────┘ │
│                      │
│ Last Name *          │
│ ┌──────────────────┐ │
│ │ Smith            │ │
│ └──────────────────┘ │
│                      │
│ Email *              │
│ ┌──────────────────┐ │
│ │ john@company.ch  │ │
│ └──────────────────┘ │
│                      │
│ Company *            │
│ ┌──────────────────┐ │
│ │ TechCorp AG      │ │
│ └──────────────────┘ │
│                      │
│ Experience           │
│ ○ 0-2  ○ 3-5        │
│ ● 6-10 ○ 10+        │
│                      │
│ ┌────────────────┐   │
│ │     NEXT →     │   │
│ └────────────────┘   │
│                      │
└──────────────────────┘
```

---

## 8. Offline Content & Download Manager

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back                          Offline Content Manager                    [Settings]│
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─── DOWNLOAD STATUS ─────────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Available Storage: 2.4 GB          Used: 847 MB (35%)                         │ │
│  │  ████████████░░░░░░░░░░░░░░░░░░░░░░░                                          │ │
│  │                                                                                  │ │
│  │  Auto-download: ● On WiFi only  ○ Always  ○ Never                             │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── OFFLINE CONTENT ─────────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Downloaded for Offline (12 items)                          Sort: [Recent ▼]   │ │
│  │                                                                                  │ │
│  │  ┌────────────────────────────────────────────────────────────────────────────┐ │ │
│  │  │ 📄 Kubernetes Security Best Practices                                      │ │ │
│  │  │    PDF • 12.3 MB • Downloaded 2 days ago                                  │ │ │
│  │  │    ✓ Slides  ✓ Video  ✓ Code samples                                      │ │ │
│  │  │    [Open] [Update] [Delete]                                                │ │ │
│  │  ├────────────────────────────────────────────────────────────────────────────┤ │ │
│  │  │ 📄 Container Orchestration at Scale                                        │ │ │
│  │  │    PDF • 8.7 MB • Downloaded 1 week ago                                    │ │ │
│  │  │    ✓ Slides  ✗ Video (156 MB)  ✓ Resources                                │ │ │
│  │  │    [Open] [Download Video] [Delete]                                        │ │ │
│  │  ├────────────────────────────────────────────────────────────────────────────┤ │ │
│  │  │ 📁 Spring Conference 2025 Bundle                                           │ │ │
│  │  │    8 presentations • 89 MB • Synced today                                  │ │ │
│  │  │    Event in 45 days                                                        │ │ │
│  │  │    [Browse] [Update All] [Delete]                                          │ │ │
│  │  └────────────────────────────────────────────────────────────────────────────┘ │ │
│  │                                                                                  │ │
│  │  [Download More] [Delete All] [Export to Device]                               │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── SMART SYNC ───────────────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Automatic Downloads                                                            │ │
│  │  ☑ Upcoming event materials (2 weeks before)                                   │ │
│  │  ☑ Bookmarked presentations                                                     │ │
│  │  ☑ In-progress learning paths                                                  │ │
│  │  ☐ Trending in my company                                                      │ │
│  │  ☐ New content matching interests                                              │ │
│  │                                                                                  │ │
│  │  Sync Schedule: ● Daily at 02:00  ○ Weekly  ○ Manual only                     │ │
│  │  Last sync: Today, 02:00 (Success)                                             │ │
│  │  Next sync: Tomorrow, 02:00                                                    │ │
│  │                                                                                  │ │
│  │  [Sync Now] [Configure Rules] [View Sync History]                              │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── OFFLINE VIEWER ──────────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Continue Reading (Offline Mode)                                                │ │
│  │                                                                                  │ │
│  │  "Kubernetes Security Best Practices"                                          │ │
│  │  Page 12 of 45 • Last viewed: Yesterday                                        │ │
│  │  [Continue Reading →]                                                           │ │
│  │                                                                                  │ │
│  │  Reading Queue (3 items)                                                       │ │
│  │  • GitOps Workflows - 20 min read                                              │ │
│  │  • Service Mesh Patterns - 15 min read                                         │ │
│  │  • Cloud Cost Optimization - 25 min read                                       │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Interactive Elements & Behaviors

### Attendee-Specific Interactions
- **Infinite Scroll**: Content discovery with virtual scrolling
- **Smart Search**: Auto-complete with AI suggestions
- **One-click Registration**: Streamlined event signup
- **Bookmark/Save**: Quick content saving for offline
- **Social Sharing**: Direct share to LinkedIn/Twitter
- **Rating System**: 5-star ratings with written reviews

### Progressive Web App Features
- **Install Prompt**: Add to home screen capability
- **Push Notifications**: Event reminders, new content alerts
- **Background Sync**: Auto-download bookmarked content
- **Offline Mode**: Full functionality without connection
- **Camera Access**: QR code scanning for check-in

### Personalization Engine
- **Interest Tracking**: Learn from viewing patterns
- **Smart Recommendations**: AI-powered content suggestions
- **Company Matching**: See what colleagues are viewing
- **Learning Paths**: Personalized curriculum generation
- **Notification Preferences**: Granular control per topic

### Content Discovery Features
- **Semantic Search**: Understanding intent, not just keywords
- **Visual Timeline**: Browse content by time period
- **Topic Evolution**: See how topics trend over years
- **Related Content**: Automatic linking of similar presentations
- **Preview Mode**: Quick preview without full download

---

## Responsive Breakpoints

### Mobile (320-767px)
- Single column layout
- Bottom navigation bar
- Swipe gestures enabled
- Touch-optimized controls
- Compressed headers

### Tablet (768-1023px)
- Two-column layouts where appropriate
- Side navigation drawer
- Mixed touch/mouse interactions
- Larger touch targets

### Desktop (1024px+)
- Full multi-column layouts
- Persistent navigation
- Hover states enabled
- Keyboard shortcuts active
- Advanced filtering options

---

## Accessibility Features

### Visual
- **High Contrast Mode**: Toggle for better visibility
- **Text Scaling**: 50-200% without breaking layout
- **Focus Indicators**: Clear keyboard navigation
- **Color Blind Safe**: Patterns not just colors
- **Dark Mode**: Full dark theme support

### Navigation
- **Keyboard Only**: Complete keyboard navigation
- **Screen Reader**: ARIA labels and landmarks
- **Skip Links**: Jump to main content
- **Breadcrumbs**: Clear location indication
- **Search First**: Search prominently accessible

### Content
- **Alt Text**: All images have descriptions
- **Transcripts**: Video content transcribed
- **Captions**: Live captions for videos
- **Language Toggle**: DE/EN/FR support
- **Simple Language**: Option for simplified content

---

## Technical Implementation Notes

### Component Architecture
```
AttendeePortal/
├── Landing/
│   ├── CurrentEventHero.tsx
│   ├── SpeakerCarousel.tsx
│   ├── AgendaOverview.tsx
│   └── QuickRegistration.tsx
├── Discovery/
│   ├── SearchEngine.tsx
│   ├── AIRecommendations.tsx
│   ├── FilterPanel.tsx
│   ├── ResultsGrid.tsx
│   └── LearningPaths.tsx
├── Registration/
│   ├── RegistrationWizard.tsx
│   ├── SessionSelector.tsx
│   └── Confirmation.tsx
├── Dashboard/
│   ├── PersonalDashboard.tsx
│   ├── EventCards.tsx
│   ├── ContentLibrary.tsx
│   └── CommunityFeed.tsx
├── Archive/
│   ├── YearBrowser.tsx
│   ├── TopicExplorer.tsx
│   └── SpeakerDirectory.tsx
├── Community/
│   ├── Reviews.tsx
│   ├── Forums.tsx
│   ├── Achievements.tsx
│   └── SocialShare.tsx
└── PWA/
    ├── ServiceWorker.ts
    ├── OfflineManager.tsx
    ├── InstallPrompt.tsx
    └── BackgroundSync.ts
```

### State Management
- **Global State**: Zustand for user preferences, auth
- **Server State**: React Query with aggressive caching
- **Local State**: Component state for UI interactions
- **Offline State**: IndexedDB for offline content
- **Search State**: Separate context for search/filters

### Performance Optimizations
- **Code Splitting**: Route-based lazy loading
- **Image Optimization**: WebP with fallbacks, lazy loading
- **Virtual Scrolling**: For large content lists
- **Service Worker**: Aggressive caching strategy
- **CDN**: Static assets on CloudFront
- **Search Index**: Client-side search with Fuse.js

### PWA Implementation
```typescript
// Service Worker Registration
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('/sw.js');
}

// Offline Detection
window.addEventListener('online', updateOnlineStatus);
window.addEventListener('offline', updateOnlineStatus);

// Install Prompt
let deferredPrompt;
window.addEventListener('beforeinstallprompt', (e) => {
  deferredPrompt = e;
  showInstallButton();
});
```

---

## Success Metrics

### User Engagement
- Time to first registration: <30 seconds
- Content discovery time: <10 seconds
- Mobile usage: >60% of traffic
- PWA installation rate: >30%
- Return visitor rate: >70%

### Performance
- Lighthouse Score: >95
- First Contentful Paint: <1.5s
- Time to Interactive: <3s
- Offline functionality: 100%
- Search response: <200ms

### Accessibility
- WCAG 2.1 AA compliance
- Screen reader tested
- Keyboard navigation complete
- Mobile touch targets: 44px min
- Color contrast: 4.5:1 minimum

---

## Next Steps

These comprehensive attendee wireframes provide:

1. **Prominent event display** meeting FR6 requirements
2. **AI-powered discovery** for 20+ years of content
3. **Frictionless registration** with smart defaults
4. **Personalized experience** based on interests
5. **Full PWA capabilities** for mobile users
6. **Community engagement** features
7. **Offline-first** architecture

The wireframes can be implemented following Epic 5 (Attendee Experience) timeline, with special focus on mobile PWA capabilities and content discovery features that differentiate BATbern from static conference sites.