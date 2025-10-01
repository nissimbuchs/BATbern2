# Story: Budget & Sponsorship Management - Wireframe

**Story**: Epic 6, Story 3
**Screen**: Budget & Sponsorship Management
**User Role**: Partner
**Related FR**: FR4, FR9 (Budget)

---

## 8. Budget & Sponsorship Management

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back                    Sponsorship & Budget Management                            │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌──── SPONSORSHIP OVERVIEW 2025 ──────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Current Package: PLATINUM                    Annual Investment: CHF 25,000     │ │
│  │  Contract Period: Jan 2025 - Dec 2025        Status: Active ✓                  │ │
│  │                                                                                  │ │
│  │  Package Benefits                            Usage This Year                    │ │
│  │  ─────────────────────────────────────────────────────────────────             │ │
│  │  • Unlimited employee attendance             247/∞ employees attended           │ │
│  │  • 4 speaking slots per year                 3/4 slots used                    │ │
│  │  • Premium logo placement                    Active on all materials           │ │
│  │  • Quarterly partner meetings                2/4 attended                      │ │
│  │  • Custom workshop requests                  1/2 requested                     │ │
│  │  • Priority topic influence                  100% voting power used            │ │
│  │  • Recruitment booth access                  0/2 events utilized               │ │
│  │  • Executive networking events               2/2 attended                      │ │
│  │                                                                                  │ │
│  │  [View Contract] [Upgrade Package] [Renewal Options] [Download Invoice]         │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── BUDGET ALLOCATION & ROI ─────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  2025 Budget Breakdown                      ROI Analysis                        │ │
│  │                                                                                  │ │
│  │  Sponsorship Fee    CHF 25,000              Direct Benefits:                   │ │
│  │  ├─ Base Package    CHF 20,000              Training Value      CHF 45,000     │ │
│  │  └─ Add-ons         CHF 5,000               Recruitment Saved   CHF 32,000     │ │
│  │                                              Brand Exposure      CHF 28,000     │ │
│  │  Additional Costs:                          Network Value       CHF 15,000     │ │
│  │  Travel & Events    CHF 3,000               Knowledge Access    CHF 12,000     │ │
│  │  Team Time (est.)   CHF 8,000               ─────────────────────────────      │ │
│  │  ─────────────────────────                  Total Value        CHF 132,000     │ │
│  │  Total Investment  CHF 36,000                                                   │ │
│  │                                              ROI Ratio: 3.67x                   │ │
│  │                                              Payback Period: 3.3 months         │ │
│  │                                                                                  │ │
│  │  [Export Financial Report] [Compare with Previous Year] [Budget Forecast]       │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── COMPARATIVE ANALYSIS ─────────────────────────────────────────────────────────┐│
│  │                                                                                  ││
│  │  Your Performance vs Other Partners                                             ││
│  │                                                                                  ││
│  │  Metric                  You      Average    Best      Your Rank               ││
│  │  ───────────────────────────────────────────────────────────                   ││
│  │  Employee Attendance     247      156        312       2/8                     ││
│  │  Engagement Rate        89%       72%        91%       2/8                     ││
│  │  Content Downloads      3,421     1,876      4,102     2/8                     ││
│  │  Speaking Contributions  3        2          5         3/8                     ││
│  │  Topic Influence Score   85       65         95        3/8                     ││
│  │  ROI Ratio              3.67x     2.8x       4.2x      2/8                     ││
│  │                                                                                  ││
│  │  Overall Partnership Score: A- (Top 25%)                                        ││
│  │                                                                                  ││
│  │  [View Detailed Comparison] [Benchmark Report] [Best Practices Guide]           ││
│  └──────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                       │
│  ┌──── 2026 PLANNING ─────────────────────────────────────────────────────────────────┐│
│  │                                                                                  ││
│  │  Renewal Options                            Recommended Package                 ││
│  │                                                                                  ││
│  │  ○ PLATINUM (Current)    CHF 25,000        Based on your usage:                ││
│  │  ● PLATINUM PLUS        CHF 35,000        PLATINUM PLUS would provide:         ││
│  │  ○ DIAMOND             CHF 50,000         • 6 speaking slots (+2)              ││
│  │                                            • 4 custom workshops (+2)            ││
│  │  Additional Services:                      • Dedicated account manager          ││
│  │  ☐ Custom Workshop Series  +CHF 10,000    • Priority event access               ││
│  │  ☑ Innovation Lab Access   +CHF 5,000                                          ││
│  │  ☐ Exclusive Events        +CHF 8,000     Projected ROI: 4.2x                  ││
│  │                                                                                  ││
│  │  [Request Proposal] [Schedule Discussion] [Calculate ROI]                       ││
│  └──────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## API Requirements

### Initial Page Load APIs

When the Budget & Sponsorship Management screen loads, the following APIs are called to provide the necessary data:

1. **GET /api/v1/partners/{partnerId}/sponsorship/current**
   - Returns: Current package details (tier, investment, contract period, status), package benefits list, renewal date
   - Used for: Populate sponsorship overview panel with current package information

2. **GET /api/v1/partners/{partnerId}/sponsorship/usage**
   - Query params: year (2025)
   - Returns: Usage metrics for all package benefits (employee attendance, speaking slots used, meetings attended, workshops requested, voting power used, booth utilization)
   - Used for: Display usage statistics in package benefits section

3. **GET /api/v1/partners/{partnerId}/budget/allocation**
   - Query params: year (2025)
   - Returns: Budget breakdown (sponsorship fee components, additional costs), total investment calculation
   - Used for: Populate budget allocation section

4. **GET /api/v1/partners/{partnerId}/budget/roi-analysis**
   - Query params: year (2025)
   - Returns: ROI calculation with benefit values (training, recruitment, brand exposure, network, knowledge), total value, ROI ratio, payback period
   - Used for: Display ROI analysis section

5. **GET /api/v1/partners/{partnerId}/sponsorship/comparative-analysis**
   - Query params: year (2025), anonymize (true)
   - Returns: Anonymized comparison metrics (attendance, engagement, downloads, speaking, influence, ROI) with averages, best scores, partner rank, overall score
   - Used for: Populate comparative analysis table

6. **GET /api/v1/partners/{partnerId}/sponsorship/renewal-options**
   - Query params: year (2026)
   - Returns: Available packages with pricing, add-on services, recommended package based on usage patterns, projected ROI
   - Used for: Display 2026 planning section with renewal options

7. **GET /api/v1/partners/{partnerId}/contracts/current**
   - Returns: Current contract document ID, signing date, terms, renewal terms
   - Used for: Enable view contract functionality

---

## Action APIs

### Contract & Document Management

1. **GET /api/v1/partners/{partnerId}/contracts/{contractId}/download**
   - Returns: Contract PDF download URL, expiration timestamp
   - Used for: Download current contract document

2. **GET /api/v1/partners/{partnerId}/invoices/latest**
   - Returns: Latest invoice details, download URL
   - Used for: Download current invoice

3. **GET /api/v1/partners/{partnerId}/invoices/list**
   - Query params: year, limit, offset
   - Returns: List of all invoices with dates, amounts, payment status
   - Used for: View invoice history

### Package Management

4. **POST /api/v1/partners/{partnerId}/sponsorship/upgrade-request**
   - Payload: `{ targetPackage: "platinum-plus|diamond", effectiveDate, reason }`
   - Response: Upgrade request ID, review timeline, next steps
   - Used for: Request package upgrade

5. **GET /api/v1/partners/{partnerId}/sponsorship/packages/details**
   - Query params: packageTier
   - Returns: Detailed package information, benefits breakdown, pricing structure
   - Used for: View detailed information about specific package tier

6. **GET /api/v1/partners/{partnerId}/sponsorship/renewal-options/detailed**
   - Query params: year (2026)
   - Returns: Comprehensive renewal options, early bird discounts, multi-year options, customization possibilities
   - Used for: Navigate to detailed renewal options screen

### Budget & Financial Analysis

7. **POST /api/v1/partners/{partnerId}/budget/export**
   - Payload: `{ year, format: "excel|pdf", includeForecast: boolean }`
   - Response: Export task ID, estimated completion time
   - Used for: Generate comprehensive budget and financial report

8. **GET /api/v1/partners/{partnerId}/budget/export/{taskId}/download**
   - Returns: Download URL, expiration timestamp
   - Used for: Download generated financial report

9. **GET /api/v1/partners/{partnerId}/budget/comparison/year-over-year**
   - Query params: currentYear (2025), comparisonYear (2024)
   - Returns: YoY comparison of investment, benefits, ROI, usage metrics, trends
   - Used for: Navigate to year-over-year comparison view

10. **POST /api/v1/partners/{partnerId}/budget/forecast**
    - Payload: `{ targetYear: 2026, assumedPackage, additionalServices: [], growthRate }`
    - Response: Forecasted budget, ROI projection, benefit estimation
    - Used for: Generate budget forecast for planning

### ROI & Value Analysis

11. **GET /api/v1/partners/{partnerId}/budget/roi/detailed**
    - Query params: year, includeMethodology (true)
    - Returns: Detailed ROI breakdown, calculation methodology, benefit-by-benefit value analysis
    - Used for: View detailed ROI analysis with full methodology

12. **POST /api/v1/partners/{partnerId}/budget/roi/calculate-custom**
    - Payload: `{ package, additionalServices: [], customValues: {} }`
    - Response: Custom ROI calculation based on proposed configuration
    - Used for: Calculate ROI for different package scenarios

### Comparative Analysis

13. **GET /api/v1/partners/{partnerId}/sponsorship/benchmarks/detailed**
    - Query params: metrics, includeHistorical (true)
    - Returns: Detailed benchmark data, historical trends, percentile rankings, improvement suggestions
    - Used for: Navigate to detailed benchmark comparison view

14. **GET /api/v1/partners/{partnerId}/sponsorship/best-practices**
    - Returns: Curated best practices based on top performers, actionable recommendations, case studies
    - Used for: View best practices guide

15. **POST /api/v1/partners/{partnerId}/sponsorship/benchmark-report/generate**
    - Payload: `{ includeMetrics: [], format: "pdf|pptx" }`
    - Response: Report generation task ID
    - Used for: Generate comprehensive benchmark report

### Planning & Proposals

16. **POST /api/v1/partners/{partnerId}/sponsorship/proposal-request**
    - Payload: `{ targetYear: 2026, packageTier, additionalServices: [], customRequirements, meetingPreference }`
    - Response: Proposal request ID, expected delivery date, next steps
    - Used for: Request custom proposal for renewal

17. **POST /api/v1/partners/{partnerId}/meetings/renewal-discussion/schedule**
    - Payload: `{ proposedDates: [], attendees: [], topics: [], format: "in-person|virtual" }`
    - Response: Meeting request ID, scheduling status
    - Used for: Schedule renewal discussion meeting

### Add-on Services

18. **PUT /api/v1/partners/{partnerId}/sponsorship/add-ons**
    - Payload: `{ addOns: [{ serviceId, quantity, startDate }] }`
    - Response: Updated package configuration, revised pricing
    - Used for: Add or modify add-on services

19. **GET /api/v1/partners/{partnerId}/sponsorship/add-ons/available**
    - Returns: List of available add-on services with descriptions, pricing, availability
    - Used for: Browse available add-on services

---

## Navigation Map

### Primary Navigation Actions

1. **← Back button** → Navigate back to `Partner Analytics Dashboard`
   - Returns to main partner dashboard

2. **[View Contract] button** → Triggers contract download
   - Downloads current contract PDF
   - Opens in browser or downloads to device
   - No screen navigation

3. **[Upgrade Package] button** → Navigate to `Package Upgrade Request Screen`
   - Package tier selection
   - Effective date picker
   - Justification form
   - Submit upgrade request

4. **[Renewal Options] button** → Navigate to `Renewal Options Detail Screen`
   - Detailed package comparisons
   - Multi-year discount options
   - Customization builder
   - Proposal generator

5. **[Download Invoice] button** → Triggers invoice download
   - Downloads latest invoice PDF
   - Option to view invoice history
   - No screen navigation

6. **[Export Financial Report] button** → Triggers financial report export
   - Select format and options
   - Generates comprehensive report
   - Downloads when ready
   - No screen navigation

7. **[Compare with Previous Year] button** → Navigate to `Year-over-Year Comparison Screen`
   - Side-by-side metrics comparison
   - Trend visualization
   - Investment vs value analysis
   - Improvement highlights

8. **[Budget Forecast] button** → Navigate to `Budget Forecasting Tool Screen`
   - Scenario planning
   - Package selection
   - Growth rate assumptions
   - ROI projections

9. **[View Detailed Comparison] button** → Navigate to `Detailed Benchmark Comparison Screen`
   - Full benchmark metrics
   - Historical performance trends
   - Peer analysis
   - Percentile rankings

10. **[Benchmark Report] button** → Triggers benchmark report generation
    - Select metrics to include
    - Choose format
    - Generates branded report
    - No screen navigation

11. **[Best Practices Guide] button** → Navigate to `Best Practices Guide Screen`
    - Curated recommendations
    - Case studies from top performers
    - Action plan builder
    - Implementation tracking

12. **[Request Proposal] button** → Navigate to `Custom Proposal Request Screen`
    - Package customization
    - Special requirements form
    - Budget constraints input
    - Meeting scheduling

13. **[Schedule Discussion] button** → Navigate to `Meeting Scheduler Screen`
    - Calendar view
    - Topic selection (renewal discussion)
    - Attendee list
    - Format preference (in-person/virtual)

14. **[Calculate ROI] button** → Opens ROI calculator modal
    - Select prospective package
    - Add services and assumptions
    - Instant ROI calculation
    - Comparison with current ROI
    - No full screen navigation

15. **Package tier radio button (2026 planning)** → Updates package preview
    - Shows package benefits
    - Updates pricing
    - Recalculates projected ROI
    - No screen navigation

16. **Add-on service checkbox** → Updates total pricing
    - Adds/removes service from calculation
    - Updates projected investment
    - Recalculates ROI
    - No screen navigation

### Secondary Navigation (Data Interactions)

17. **Package benefit item click** → Opens benefit details modal
    - Explains benefit
    - Shows usage history
    - Provides utilization tips
    - No screen navigation

18. **Budget line item click** → Opens cost breakdown modal
    - Detailed cost explanation
    - Historical comparison
    - Optimization suggestions
    - No screen navigation

19. **ROI benefit item click** → Opens value calculation modal
    - Explains how value is calculated
    - Shows data sources
    - Industry benchmarks used
    - No screen navigation

20. **Comparative metric row click** → Navigate to `Metric Detail Screen`
    - Historical trends for that metric
    - Improvement strategies
    - Top performer insights
    - Related metrics

21. **Overall Partnership Score click** → Opens scoring methodology modal
    - Explains scoring algorithm
    - Shows component scores
    - Improvement recommendations
    - No screen navigation

22. **Recommended Package section** → Expands recommendation details
    - Shows usage-based analysis
    - Explains why recommended
    - Comparison with alternatives
    - No screen navigation

### Event-Driven Navigation

23. **Contract renewal approaching (90 days)** → Shows renewal reminder banner
    - Links to renewal options
    - Highlights early bird discounts
    - Suggests scheduling discussion
    - No automatic navigation

24. **Package upgrade request submitted** → Shows confirmation notification
    - Request ID provided
    - Expected response timeline
    - Next steps outlined
    - No automatic navigation

25. **Proposal generated** → Shows notification with access link
    - Download proposal PDF
    - View online
    - Schedule discussion option
    - No automatic navigation

26. **Meeting scheduled** → Shows confirmation notification
    - Calendar invite sent
    - Meeting details
    - Add to calendar option
    - No automatic navigation

27. **Financial report ready** → Shows notification with download link
    - Download available
    - Preview option
    - Share with team
    - No automatic navigation

28. **ROI calculation complete** → Updates ROI display
    - Animated counter update
    - Highlights changes
    - No screen navigation

29. **Benchmark data updated** → Shows info notification
    - New data available
    - Position changes highlighted
    - Updated rankings
    - No automatic navigation

30. **Invoice payment due** → Shows reminder notification
    - Payment deadline
    - Amount due
    - Payment options
    - Links to invoice

31. **New package benefits available** → Shows info notification
    - Describes new benefits
    - Links to upgrade options
    - Shows value proposition
    - No automatic navigation

---
