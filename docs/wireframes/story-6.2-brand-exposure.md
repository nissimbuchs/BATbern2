# Story: Brand Exposure & Marketing Value - Wireframe

**Story**: Epic 6, Story 2
**Screen**: Brand Exposure & Marketing Value
**User Role**: Partner
**Related FR**: FR4 (Brand Analytics)

---

## 3. Brand Exposure & Marketing Value

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back                      Brand Exposure Analytics                     [Export]    │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌──── BRAND VALUE CALCULATION ────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Total Marketing Value Generated: CHF 105,000                  ROI: 4.2x        │ │
│  │                                                                                  │ │
│  │  ┌─────────────────────────────────────────────────────────────────────────┐   │ │
│  │  │ Exposure Type              Instances    Reach      Value (CHF)          │   │ │
│  │  │ ───────────────────────────────────────────────────────────────────────  │   │ │
│  │  │ Logo on Event Materials    8 events     2,000+     12,000               │   │ │
│  │  │ Website Placement          12 months     15,000+    18,000               │   │ │
│  │  │ Newsletter Mentions        24 issues     5,000+     8,000                │   │ │
│  │  │ Social Media Tags          45 posts      12,000+    15,000               │   │ │
│  │  │ Speaker Presentations      3 talks       450+       9,000                │   │ │
│  │  │ Booth/Banner Display       8 events     1,600+     20,000               │   │ │
│  │  │ Video Sponsorship          5 videos     3,200+     15,000               │   │ │
│  │  │ LinkedIn Amplification     Generated     8,000+     8,000                │   │ │
│  │  │                                                                          │   │ │
│  │  │ Total                                    51,250     105,000              │   │ │
│  │  └─────────────────────────────────────────────────────────────────────────┘   │ │
│  │                                                                                  │ │
│  │  Calculation based on: Industry standard CPM rates for B2B tech marketing       │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── DIGITAL FOOTPRINT ───────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Website Analytics                          Social Media Impact                 │ │
│  │                                                                                  │ │
│  │  Logo Impressions: 45,234                   LinkedIn:                           │ │
│  │  Click-through Rate: 2.3%                   • Mentions: 67                      │ │
│  │  Avg. Time on Partner Page: 1:23            • Engagement: 1,234                 │ │
│  │  Profile Downloads: 234                     • Reach: 12,456                     │ │
│  │                                                                                  │ │
│  │  Traffic Sources:                           Twitter/X:                          │ │
│  │  Direct: 45%  ████████                     • Mentions: 45                       │ │
│  │  Event Pages: 30%  ██████                  • Retweets: 123                      │ │
│  │  Search: 25%  █████                        • Impressions: 8,234                 │ │
│  │                                                                                  │ │
│  │  [View Website Analytics] [Social Media Report] [Competitor Comparison]         │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── CONTENT PERFORMANCE ─────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Sponsored Content Impact                                                       │ │
│  │                                                                                  │ │
│  │  ┌────────────────────────────────────────────────────────────────────────────┐ │ │
│  │  │ Content                     Speaker       Views   Downloads  Engagement    │ │ │
│  │  │ ──────────────────────────────────────────────────────────────────────────  │ │ │
│  │  │ "UBS Cloud Journey"         T. Mueller    1,234   456        92%           │ │ │
│  │  │ "Financial Security"        S. Weber      987     234        88%           │ │ │
│  │  │ "Digital Banking Future"    A. Schmidt    756     189        85%           │ │ │
│  │  └────────────────────────────────────────────────────────────────────────────┘ │ │
│  │                                                                                  │ │
│  │  Your speakers' content ranks in top 15% for engagement                         │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## API Requirements

### Initial Page Load APIs

When the Brand Exposure Analytics screen loads, the following APIs are called to provide the necessary data:

1. **GET /api/v1/partners/{partnerId}/brand/value-calculation**
   - Query params: year (2025), includeBreakdown (true)
   - Returns: Total marketing value, ROI ratio, breakdown by exposure type (instances, reach, calculated value), calculation methodology
   - Used for: Populate brand value calculation panel with all exposure types and values

2. **GET /api/v1/partners/{partnerId}/brand/digital-footprint**
   - Query params: period (12months)
   - Returns: Website analytics (logo impressions, CTR, time on page, profile downloads), traffic sources breakdown
   - Used for: Display website analytics section in digital footprint panel

3. **GET /api/v1/partners/{partnerId}/brand/social-media-impact**
   - Query params: platforms (linkedin, twitter), period (12months)
   - Returns: Platform-specific metrics (mentions, engagement, reach, impressions, retweets)
   - Used for: Display social media impact section in digital footprint panel

4. **GET /api/v1/partners/{partnerId}/brand/content-performance**
   - Query params: contentType (sponsored), limit (10), sortBy (views)
   - Returns: Sponsored content items with speaker names, views, downloads, engagement rates, rankings
   - Used for: Populate sponsored content impact table

5. **GET /api/v1/partners/{partnerId}/brand/benchmarks**
   - Returns: Industry benchmarks for engagement, content performance percentiles
   - Used for: Display comparative performance insights

---

## Action APIs

### Export & Reporting

1. **POST /api/v1/partners/{partnerId}/brand/export**
   - Payload: `{ format: "pdf|excel|pptx", sections: ["value", "digital", "social", "content"], includeBenchmarks: boolean }`
   - Response: Export task ID, estimated completion time
   - Used for: Generate comprehensive brand analytics export

2. **GET /api/v1/partners/{partnerId}/brand/export/{taskId}/download**
   - Returns: Download URL, expiration timestamp
   - Used for: Download generated brand report

### Detailed Analytics Navigation

3. **GET /api/v1/partners/{partnerId}/brand/website-analytics/detailed**
   - Query params: dateRange, metrics (impressions, ctr, time, downloads)
   - Returns: Time-series data for website metrics, page-by-page breakdown, user journey analytics
   - Used for: Navigate to detailed website analytics view

4. **GET /api/v1/partners/{partnerId}/brand/social-media/report**
   - Query params: platform (linkedin, twitter, all), dateRange
   - Returns: Comprehensive social media report with post details, engagement timelines, influencer reach
   - Used for: Navigate to social media detailed report

5. **GET /api/v1/partners/{partnerId}/brand/competitors/comparison**
   - Query params: competitors (anonymized), metrics (reach, engagement, value)
   - Returns: Anonymized competitor benchmarking data, industry position, best practices
   - Used for: Navigate to competitor comparison view

### Content & Exposure Deep Dive

6. **GET /api/v1/partners/{partnerId}/brand/exposure/{exposureType}/details**
   - Query params: exposureType (logo, website, newsletter, social, speakers, booth, video, linkedin)
   - Returns: Detailed breakdown for specific exposure type, event-by-event or instance-by-instance data
   - Used for: Drill down into specific exposure type

7. **GET /api/v1/partners/{partnerId}/brand/content/{contentId}/analytics**
   - Returns: Detailed content performance metrics, viewer demographics, engagement timeline, feedback
   - Used for: Navigate to individual content performance view

8. **GET /api/v1/partners/{partnerId}/brand/speakers/impact**
   - Returns: Speaker-by-speaker brand impact, presentation reach, content performance, social amplification
   - Used for: Navigate to speaker brand impact analysis

### Social Media Management

9. **GET /api/v1/partners/{partnerId}/brand/social-media/mentions/list**
   - Query params: platform, dateRange, sentiment (positive, neutral, negative), limit, offset
   - Returns: List of social media mentions with post content, engagement, reach, sentiment analysis
   - Used for: View detailed list of social media mentions

10. **POST /api/v1/partners/{partnerId}/brand/social-media/amplification/request**
    - Payload: `{ contentId, platforms: ["linkedin", "twitter"], targetAudience, scheduledDate }`
    - Response: Amplification campaign ID, estimated reach
    - Used for: Request social media amplification for specific content

### Traffic & Engagement Analysis

11. **GET /api/v1/partners/{partnerId}/brand/website/traffic-sources/detailed**
    - Query params: dateRange, groupBy (source, campaign, referrer)
    - Returns: Detailed traffic source analysis, conversion paths, attribution data
    - Used for: Navigate to detailed traffic source analysis

12. **GET /api/v1/partners/{partnerId}/brand/website/partner-page/analytics**
    - Query params: dateRange
    - Returns: Partner page specific metrics, heatmaps, user behavior, download tracking
    - Used for: View partner page detailed analytics

### Value Calculation & ROI

13. **GET /api/v1/partners/{partnerId}/brand/value-calculation/methodology**
    - Returns: Detailed explanation of value calculation, CPM rates used, industry standards, calculation formulas
    - Used for: Display methodology details modal

14. **POST /api/v1/partners/{partnerId}/brand/value-calculation/custom**
    - Payload: `{ customRates: {}, exposureWeights: {}, dateRange }`
    - Response: Recalculated marketing value with custom parameters
    - Used for: Calculate brand value with custom CPM rates or weights

15. **GET /api/v1/partners/{partnerId}/brand/roi/trend**
    - Query params: period (24months), groupBy (quarter)
    - Returns: Historical ROI trend data, milestone events, correlation with investment
    - Used for: View ROI trend analysis over time

---

## Navigation Map

### Primary Navigation Actions

1. **← Back button** → Navigate back to `Partner Analytics Dashboard`
   - Returns to main partner dashboard
   - Preserves session context

2. **[Export] button** → Triggers export flow
   - Opens export format selection modal
   - Choose sections to include
   - Generates branded report
   - No screen navigation

3. **Exposure type row click (in value calculation table)** → Navigate to `Exposure Type Details Screen`
   - Event-by-event or instance-by-instance breakdown
   - Historical data for that exposure type
   - Reach and value trends
   - Related content and materials

4. **[View Website Analytics] button** → Navigate to `Detailed Website Analytics Screen`
   - Time-series charts for all website metrics
   - Page-by-page performance
   - User journey visualization
   - Conversion funnel for partner page

5. **[Social Media Report] button** → Navigate to `Social Media Detailed Report Screen`
   - Platform-by-platform breakdown
   - Post-by-post engagement details
   - Sentiment analysis over time
   - Influencer and advocate identification

6. **[Competitor Comparison] button** → Navigate to `Competitor Benchmarking Screen`
   - Anonymized competitor data
   - Market position analysis
   - Share of voice metrics
   - Best practice recommendations

7. **Content row click (in sponsored content table)** → Navigate to `Content Performance Details Screen`
   - Full content analytics
   - Viewer demographics and behavior
   - Engagement timeline
   - Feedback and ratings
   - Social amplification metrics

8. **Speaker name click** → Navigate to `Speaker Brand Impact Screen`
   - Speaker-specific brand contribution
   - All presentations by that speaker
   - Reach and engagement metrics
   - Social media influence

9. **Website traffic source bar click** → Navigate to `Traffic Source Analysis Screen`
   - Source-specific metrics
   - Conversion paths from that source
   - Campaign attribution
   - Optimization recommendations

### Secondary Navigation (Metrics & Data Interactions)

10. **Total Marketing Value click** → Opens calculation methodology modal
    - Explains value calculation
    - Shows CPM rates used
    - Industry standards reference
    - Option to customize calculation

11. **ROI metric click** → Navigate to `ROI Trend Analysis Screen`
    - Historical ROI over time
    - Correlation with investment levels
    - Milestone events impact
    - Forecasting models

12. **Logo Impressions metric click** → Navigate to `Impression Details Screen`
    - Impression sources breakdown
    - Geographic distribution
    - Time-based patterns
    - Quality score

13. **Click-through Rate metric click** → Opens CTR analysis modal
    - CTR trends over time
    - Comparison with industry benchmarks
    - Landing page performance
    - A/B test opportunities

14. **Social media mention count click** → Navigate to `Mentions List Screen`
    - Chronological list of mentions
    - Sentiment analysis
    - Engagement per mention
    - Response opportunities

15. **Engagement rate click (content table)** → Opens engagement details modal
    - Engagement breakdown by type (views, comments, shares, questions)
    - Engagement timeline
    - Most engaging sections
    - Audience segments

### Event-Driven Navigation

16. **New social media mention** → Shows notification badge
    - Links to mention details
    - Sentiment indicator
    - Optional response action
    - No automatic navigation

17. **Content reaches milestone** → Shows celebration notification
    - Highlights achievement (e.g., 1000 views)
    - Suggests amplification
    - Links to content analytics
    - No automatic navigation

18. **Competitive position change** → Shows info notification
    - Position change indicator
    - Links to competitor comparison
    - Suggests strategy adjustments
    - No automatic navigation

19. **Export generation complete** → Shows notification with download link
    - Download available
    - Preview option
    - Share with team option
    - No automatic navigation

20. **High-value exposure opportunity** → Shows alert notification
    - Describes opportunity (e.g., popular event, high traffic period)
    - Suggests action
    - Links to opportunity details
    - No automatic navigation

21. **Negative sentiment detected** → Shows warning notification
    - Links to mention/content
    - Suggests response
    - Crisis management workflow trigger
    - No automatic navigation

22. **Website traffic spike** → Shows info notification
    - Traffic source identified
    - Real-time metrics
    - Links to analytics
    - No automatic navigation

23. **Amplification campaign complete** → Shows summary notification
    - Campaign results
    - Reach achieved
    - Engagement generated
    - Links to detailed report

24. **Quarterly brand value report ready** → Shows notification
    - Automated report generation
    - Links to report viewer
    - Download and share options
    - No automatic navigation

25. **Industry benchmark update** → Shows info notification
    - New benchmarks available
    - Position change highlighted
    - Updated comparisons displayed
    - No automatic navigation

---
