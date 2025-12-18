# Event Analytics `[PLANNED]`

## Overview

The Event Analytics dashboard provides organizers with actionable insights into event performance, attendee engagement, and content effectiveness. Analytics help you:

- **Measure Success** - Track attendance, satisfaction, and engagement metrics
- **Identify Trends** - Discover patterns across events to inform future planning
- **Optimize Content** - Understand which topics and speakers resonate most
- **Demonstrate ROI** - Show partners and stakeholders quantifiable event value
- **Data-Driven Decisions** - Replace gut feelings with evidence-based planning

> **Note**: This feature is currently in planning stages. The documentation below describes the intended functionality. Check [Feature Status](../appendix/feature-status.md) for implementation timeline.

## When to Use This Feature

### Primary Use Cases

1. **Post-Event Review** (2-4 weeks after event)
   - Evaluate event success against KPIs
   - Identify what worked well and what didn't
   - Generate reports for stakeholders

2. **Strategic Planning** (Quarterly)
   - Analyze trends across multiple events
   - Identify successful patterns to replicate
   - Spot declining metrics early

3. **Content Optimization** (Before each event planning cycle)
   - Review which topics had highest engagement
   - Identify speaker effectiveness patterns
   - Cross-reference with [Topic Heat Maps](heat-maps.md)

4. **Partner Reporting** (As needed)
   - Generate customized analytics for sponsors
   - Show partner booth traffic and engagement
   - Demonstrate sponsorship ROI

## How It Works

### Analytics Dashboard

**Navigation**: Dashboard → Analytics → Select Event(s)

#### Overview Metrics (At-a-Glance)

```
┌─────────────────────────────────────────────────────────┐
│  Event #45 - Sustainable Architecture Innovations       │
│  Date: 2024-12-15 | Status: Completed                   │
└─────────────────────────────────────────────────────────┘

Key Metrics:
┌──────────────┬──────────────┬──────────────┬──────────────┐
│ Registrations│   Attendance │   Satisfaction│   Engagement │
│     185/200  │   92.5% (185)│   4.5/5.0    │   High (82%) │
│   ┌─ Goal    │   Target: 85%│   +0.3 vs    │   +12% vs    │
│   │          │              │   Event #44  │   Event #44  │
└───┴──────────┴──────────────┴──────────────┴──────────────┘

Trends (vs Previous Event):
📈 +8% Attendance  |  📈 +6% Satisfaction  |  📉 -2% Cost/Attendee
```

#### Detailed Metrics Categories

### 1. Attendance Analytics

**What It Tracks**:
- **Registration Funnel**: Invited → Registered → Confirmed → Attended
- **Dropout Analysis**: Where and when attendees drop off
- **Demographics**: Company size, role, industry sector
- **Geographic Distribution**: Attendee locations (for venue planning)

**Key Metrics**:
- **Registration Rate**: Registered / Invited (target: 60-70%)
- **Confirmation Rate**: Confirmed / Registered (target: 80-90%)
- **Attendance Rate**: Attended / Confirmed (target: 85-95%)
- **No-Show Rate**: Did Not Attend / Confirmed (target: <10%)

**Visualizations**:
- Funnel chart showing dropoff at each stage
- Time-series: registrations per day leading up to event
- Heatmap: registration times (identify optimal reminder timing)
- Comparative bar chart: this event vs historical average

**Example Insight**:
> "Registration rate dropped 15% at the confirmation stage. Consider simplifying the confirmation process or sending earlier reminders."

### 2. Content Performance

**What It Tracks**:
- **Session Attendance**: Individual session participation rates
- **Topic Engagement**: Attendee interest by topic category
- **Speaker Ratings**: Per-speaker satisfaction scores
- **Content Relevance**: Survey responses on content applicability

**Key Metrics**:
- **Session Fill Rate**: Attendees / Capacity per session
- **Topic Interest Score**: Combination of attendance + ratings + Q&A activity
- **Speaker Effectiveness**: Rating + engagement + content quality scores
- **Content Diversity Index**: Spread across topic categories

**Visualizations**:
- Session attendance bar chart (sorted high to low)
- Topic interest radar chart (multi-dimensional view)
- Speaker comparison scatter plot (rating vs engagement)
- Trend line: topic interest over multiple events

**Example Insight**:
> "Sustainable Building topic scored 4.8/5 with 95% attendance. Consider expanding to 2 sessions next event."

### 3. Engagement Metrics

**What It Tracks**:
- **Q&A Activity**: Questions asked per session
- **Networking Interactions**: Connections made, business cards exchanged `[PLANNED]`
- **Partner Booth Traffic**: Visits and dwell time per sponsor
- **Material Downloads**: Speaker slide downloads, handouts

**Key Metrics**:
- **Engagement Score**: Composite of Q&A, networking, downloads (0-100)
- **Interaction Density**: Interactions per attendee
- **Partner ROI**: Cost per interaction for sponsors
- **Content Value**: Downloads + shares + feedback score

**Visualizations**:
- Heatmap: engagement by time of day (identify fatigue patterns)
- Network graph: attendee connections and clusters `[PLANNED]`
- Partner traffic funnel: booth visits → deep engagements → leads
- Content performance: download counts + time-to-download

**Example Insight**:
> "Engagement drops 30% after 4 PM. Consider shorter afternoon sessions or interactive formats."

### 4. Satisfaction Surveys

**What It Tracks**:
- **Overall Satisfaction**: Post-event survey (5-point scale)
- **Net Promoter Score (NPS)**: Likelihood to recommend (0-10 scale)
- **Category Ratings**: Venue, content, organization, networking
- **Open Feedback**: Text responses with sentiment analysis

**Key Metrics**:
- **CSAT (Customer Satisfaction)**: % rating 4-5 stars (target: 85%+)
- **NPS**: Promoters (9-10) minus Detractors (0-6) (target: +40)
- **Response Rate**: Surveys completed / Attendees (target: 40%+)
- **Sentiment Score**: Positive - Negative mentions in feedback

**Visualizations**:
- NPS gauge chart with segmentation (Promoters, Passives, Detractors)
- Category satisfaction spider chart (compare across dimensions)
- Word cloud: most common terms in open feedback
- Trend line: satisfaction over time (multi-event view)

**Example Insight**:
> "NPS dropped from +45 to +32. Detractors cite 'poor venue WiFi' and 'late start times'. Focus on operational improvements."

### 5. Financial Analytics `[BASIC]`

**What It Tracks**:
- **Cost per Attendee**: Total event cost / Attendees
- **Revenue per Partner**: Sponsorship value / Partner
- **Budget Variance**: Actual vs Planned spending
- **ROI Estimation**: Value generated vs cost (partner-specific)

**Key Metrics**:
- **CPA (Cost per Attendee)**: Target: CHF 150-250 per person
- **Sponsorship Fill Rate**: Revenue / Target (target: 100%+)
- **Budget Adherence**: % over/under budget (target: ±5%)

**Visualizations**:
- Cost breakdown pie chart (venue, catering, speakers, materials)
- Budget vs actual bar chart by category
- Trend line: cost per attendee over multiple events
- Partner ROI comparison (cost vs perceived value)

**Example Insight**:
> "Catering costs exceeded budget by 18%. Review vendor contract or reduce menu options."

### Comparative Analytics

**Multi-Event Analysis**:
- Compare current event against historical average
- Benchmark against top-performing events
- Identify seasonal patterns (quarterly trends)
- Cohort analysis: attendee retention across events

**Filters & Segmentation**:
- **Time Period**: Last 6 months, last year, all time
- **Event Type**: Full-day, afternoon, evening events
- **Topic Category**: Filter by topic (e.g., only Technology events)
- **Attendee Segment**: By role, company size, industry

**Example View**:
```
Compare: Event #45 vs Average of Last 5 Events

Attendance:        92.5% vs 87.2% (↑ 5.3%)   📈 Above Average
Satisfaction:      4.5/5 vs 4.3/5 (↑ 0.2)     📈 Above Average
Engagement:        82%   vs 74%   (↑ 8%)      📈 Significantly Above
Cost/Attendee:     CHF 185 vs CHF 198 (↓ 6.5%) 📈 Below Target
```

### Exporting Reports

**Report Formats**:
- **PDF Executive Summary**: 2-page overview with key metrics and visualizations
- **Detailed Excel Report**: Raw data + pivot tables for custom analysis
- **PowerPoint Deck**: Ready-to-present slides for stakeholders `[PLANNED]`
- **CSV Data Export**: Full dataset for external analysis tools

**Report Templates**:
- **Post-Event Report**: Standard format for internal review
- **Partner Report**: Customized for sponsor ROI demonstration
- **Quarterly Summary**: Aggregate view across 3-4 events
- **Annual Review**: Year-over-year trends and insights

**Scheduling**:
- Auto-generate reports 1 week after event closure
- Schedule quarterly summary reports (first week of each quarter)
- Email distribution lists for automatic sharing

## Tips & Best Practices

### Maximizing Insight Value

1. **Set Baseline Metrics Early**
   - Define target KPIs during event planning (Step 1)
   - Review analytics from similar past events as benchmarks
   - Adjust targets based on event type and context

2. **Capture Data Throughout Event Lifecycle**
   - Pre-event: Track registration funnel in real-time
   - During event: Monitor session attendance and engagement
   - Post-event: Deploy surveys within 48 hours (higher response rate)

3. **Combine Quantitative + Qualitative**
   - Numbers show "what" happened
   - Open feedback explains "why"
   - Use sentiment analysis to scale qualitative insights

4. **Act on Insights**
   - Create action items from each post-event review
   - Track improvement metrics in subsequent events
   - Share learnings with organizer team

### Advanced Techniques

**Cohort Analysis**:
- Track attendee retention: % attending multiple events
- Identify "super attendees" (attend 80%+ of events)
- Analyze dropout patterns: why do attendees stop coming?

**Predictive Analytics** `[PLANNED]`:
- Forecast attendance based on invitation timing and response rates
- Predict session popularity based on historical topic performance
- Estimate no-show rates to optimize catering and capacity

**A/B Testing**:
- Test two session formats and compare engagement
- Compare different reminder email strategies (timing, content)
- Experiment with venue layouts and measure networking impact

**Segmentation Strategies**:
- Compare satisfaction by attendee role (architects vs engineers)
- Analyze engagement by company size (startups vs enterprises)
- Track topic preferences by industry sector

## Troubleshooting

### Common Issues

#### "No data showing for recent event"

**Cause**: Event not marked as completed, or data sync delay

**Solutions**:
1. Verify event status: Dashboard → Events → Check status is "COMPLETED"
2. Wait 24 hours after event for full data aggregation
3. Check Settings → Analytics → Data Sync Status
4. Contact support if still missing after 48 hours

#### "Survey response rate is very low (<20%)"

**Causes**:
- Survey sent too late (>1 week after event)
- Survey too long (>10 questions)
- No incentive or follow-up reminders

**Solutions**:
1. Send survey within 48 hours of event (while experience is fresh)
2. Keep surveys under 5 minutes (8-10 questions max)
3. Send reminder email after 3 days to non-responders
4. Consider incentive: early access to speaker slides, prize draw

#### "Analytics show conflicting data (e.g., 110% attendance)"

**Cause**: Data quality issues (duplicate registrations, check-in errors)

**Solutions**:
1. Review raw data: Analytics → Data Quality → Check Duplicates
2. Verify check-in process was followed correctly
3. Run data cleanup: Analytics → Tools → Deduplicate Records
4. Adjust manual count if necessary (note in event log)

#### "Can't compare across events (missing data)"

**Cause**: Historical events missing required fields or not digitized

**Solutions**:
1. Backfill historical data: Dashboard → Events → Import Historical Data
2. Focus on recent events (last 12 months) for reliable trends
3. Document data gaps for transparency in reports
4. Set data standards for future events to ensure consistency

## Related Features

- **[Topic Heat Maps](heat-maps.md)** - Complement analytics with historical topic patterns
- **[Event Management](../entity-management/events.md)** - Event data source for analytics
- **[Post-Event Review](../workflow/phase-f-communication.md#post-event-review)** - Workflow integration

## Future Enhancements `[ROADMAP]`

### Planned Features

- **Real-Time Dashboard** - Live metrics during event (current attendance, engagement)
- **Predictive Analytics** - Forecast attendance, identify at-risk registrations
- **Machine Learning Insights** - Auto-detect anomalies and surface recommendations
- **Custom Report Builder** - Drag-and-drop interface for personalized reports
- **API Access** - Export data to BI tools (Tableau, Power BI, Looker)
- **Attendee Journey Maps** - Visualize individual paths through event
- **Social Media Analytics** - Track event hashtag reach and sentiment
- **Comparative Benchmarking** - Compare against industry standards (IACC, MPI)

### Example Dashboard Mockup

```
┌────────────────────────────────────────────────────────────────┐
│  Event Analytics Dashboard - Event #45                          │
│  📊 Overview  📈 Trends  👥 Attendees  📝 Content  💰 Financial │
└────────────────────────────────────────────────────────────────┘

Overview (Last 30 Days):
┌─────────────┬─────────────┬─────────────┬─────────────┐
│ 📊 Events   │ 👥 Attendees│ ⭐ Avg CSAT │ 💰 Total    │
│      3      │     521     │    4.4/5.0  │  CHF 96.5K  │
│  ±0 MoM     │  +12% MoM   │  +0.2 MoM   │  +8% MoM    │
└─────────────┴─────────────┴─────────────┴─────────────┘

📈 Attendance Trend (Last 6 Events):
    95% ┤                             ╭─●
    90% ┤                      ╭─●───╯
    85% ┤               ╭─●───╯
    80% ┤        ╭─●───╯
    75% ┤  ●───╯
    70% ┴──────────────────────────────────────
        #40  #41  #42  #43  #44  #45

🎯 Top Topics (Engagement Score):
  1. Sustainable Building (4.8, 95%)  ████████████████  98
  2. Smart Cities (4.6, 88%)          ███████████████   92
  3. Timber Construction (4.5, 82%)   ██████████████    87
  4. BIM Integration (4.3, 78%)       ████████████      81
  5. Urban Planning (4.1, 72%)        ███████████       76

💡 Insights:
  • Attendance trending up 15% over last 3 events
  • Technology topics consistently score 10% higher engagement
  • Wednesday afternoon events have 8% better attendance than Friday
  • Catering costs decreased 12% with optimized vendor
  • 92% of attendees requested more technical deep-dives

[Export PDF] [Schedule Report] [View Details →]
```

---

**Back to Features**: Return to [Features Overview](README.md) →
