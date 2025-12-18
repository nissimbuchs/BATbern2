# Topic Heat Maps `[IMPLEMENTED]`

## Overview

Topic Heat Maps provide visual insight into the frequency and recency of topics presented at past BATbern events. By visualizing 20+ years of event history, heat maps help organizers:

- **Identify evergreen topics** that consistently attract interest
- **Spot emerging trends** in architectural practice
- **Avoid over-repetition** of recently covered topics
- **Discover gaps** in coverage for future planning
- **Make data-driven decisions** during topic selection (Workflow Step 2)

The heat map uses a color-coded matrix where warmer colors (red, orange) indicate frequent or recent topics, while cooler colors (blue, green) show less frequently covered areas.

## When to Use This Feature

### Primary Use Cases

1. **During Event Planning (Workflow Step 2 - Topic Selection)**
   - Review historical coverage before finalizing event topics
   - Ensure topic diversity across multiple events
   - Balance popular topics with underrepresented areas

2. **Strategic Planning**
   - Identify content gaps in multi-year roadmap
   - Analyze trends in architectural practice evolution
   - Plan topic rotation schedules

3. **Speaker Brainstorming (Workflow Step 3)**
   - Match speaker expertise to underrepresented topics
   - Prioritize speakers for emerging or evergreen topics

### Recommended Frequency

- **Before each event planning cycle**: Review heat map during Step 2
- **Quarterly**: Strategic review of content gaps and trends
- **Annually**: Multi-year pattern analysis for long-term planning

## How It Works

### Accessing the Heat Map

1. Navigate to **Dashboard → Topic Selection** (or Workflow Step 2)
2. Click **"View Topic Heat Map"** button in the Topic Backlog section
3. The heat map overlay will display over your current view

### Understanding the Visualization

#### Color Scale

| Color | Meaning | Interpretation |
|-------|---------|----------------|
| 🔴 **Dark Red** | Very Recent + Frequent | Covered in last 1-2 events, 5+ times historically |
| 🟠 **Orange** | Recent OR Frequent | Covered in last 3-5 events, or 3-4 times historically |
| 🟡 **Yellow** | Moderate Coverage | Covered 6-10 events ago, or 2 times historically |
| 🟢 **Green** | Infrequent | Covered 1 time, 10+ events ago |
| 🔵 **Blue** | Never Covered | No historical record of this topic |

#### Matrix Layout

- **Rows**: Topic categories (e.g., "Sustainable Architecture", "Urban Planning", "Building Technology")
- **Columns**: Time periods (e.g., "Last Year", "2-3 Years Ago", "4-5 Years Ago", "5+ Years Ago")
- **Cells**: Individual topics with color-coded frequency indicators
- **Tooltips**: Hover over any cell to see exact event count and most recent date

### Interpreting the Data

#### Decision Guidelines

```
🔴 Dark Red Topics
→ Consider AVOIDING unless:
  • Significant new development in the field
  • High attendee demand (survey data)
  • Expert speaker available with unique angle

🟠 Orange Topics
→ USE CAUTIOUSLY
  • Ensure fresh perspective if selecting
  • Look for subtopics not previously covered

🟡 Yellow Topics
→ GOOD CANDIDATES
  • Enough time has passed for new developments
  • Attendees may have forgotten earlier coverage

🟢 Green Topics
→ PRIORITY CANDIDATES
  • Underrepresented areas
  • Potential for fresh content
  • Balance your event portfolio

🔵 Blue Topics (Never Covered)
→ EXPLORE CAREFULLY
  • May indicate niche interest
  • Test with speaker brainstorming first
  • Consider attendee demand before committing
```

### Example Workflow

**Scenario**: Planning BATbern Event #45 - Topic Selection

1. **Open Heat Map** during Workflow Step 2
2. **Review Red/Orange Topics**:
   - "Passive House Standards" = 🔴 (covered at Event #43, #41, #38)
   - **Decision**: Skip unless major code updates
3. **Identify Green Topics**:
   - "Timber Construction Innovation" = 🟢 (covered once at Event #32)
   - **Decision**: Good candidate if speaker available
4. **Explore Blue Topics**:
   - "AI in Architectural Design" = 🔵 (never covered)
   - **Decision**: Emerging trend, validate demand with advisory board
5. **Select Balanced Mix**:
   - 2 green topics (underrepresented)
   - 1 yellow topic (moderate recency)
   - 1 blue topic (emerging, validated demand)

### Filtering & Refinement

The heat map supports interactive filtering:

- **Time Range**: Focus on last 5/10/15/20+ years
- **Topic Categories**: Show only specific categories (e.g., "Technology", "Sustainability")
- **Event Types**: Filter by event format (full-day, afternoon, evening)
- **Minimum Threshold**: Hide topics with less than N occurrences

**Example Filter**: "Show all Technology topics from last 10 years with at least 2 occurrences"

## Tips & Best Practices

### Strategic Insights

1. **Balance is Key**
   - Don't over-rotate on blue topics (risk of low demand)
   - Don't avoid all red topics (some have evergreen appeal)
   - Aim for 60% green/yellow, 30% orange, 10% red/blue

2. **Context Matters**
   - Red topics may be justified if industry regulations changed
   - Blue topics may indicate emerging fields worth exploring
   - Green topics aren't automatically "better" - consider demand

3. **Combine with Other Data**
   - Cross-reference with attendee surveys
   - Review speaker availability for underrepresented topics
   - Consider industry news and trends

4. **Document Decisions**
   - Add notes in Topic Backlog explaining why you selected red topics
   - Track which blue topics you tested and their reception
   - Share insights with future organizers

### Advanced Techniques

**Pattern Recognition**:
- Look for "cooling" topics (red → orange → yellow over time)
- Identify "rising" topics (blue → green → yellow, increasing frequency)
- Spot cyclical topics (appear every 4-5 events consistently)

**Gap Analysis**:
- Export heat map data to identify systematic coverage gaps
- Plan multi-year topic rotation schedules
- Align with Swiss architectural practice evolution

**Speaker Matching**:
- Use heat map to prioritize speaker outreach for underrepresented topics
- Identify experts in blue/green areas for targeted recruitment

## Troubleshooting

### Common Issues

#### "Heat map shows no data"

**Cause**: Historical data not loaded or filtered too aggressively

**Solution**:
1. Clear all filters (click "Reset Filters" button)
2. Expand time range to "All Time"
3. Verify you have historical events in the system (Admin → Event Archive)

#### "Colors don't match my expectations"

**Cause**: Misunderstanding the color scale or data calculation

**Solution**:
1. Hover over specific cells to see exact counts and dates
2. Review the color legend (top-right corner of heat map)
3. Remember: Color considers BOTH frequency AND recency

#### "Topic I know we covered shows as blue (never covered)"

**Cause**: Topic name variation or historical data entry inconsistency

**Solution**:
1. Search for similar topic names (e.g., "BIM" vs "Building Information Modeling")
2. Report to admin to consolidate duplicate topics
3. Use "Topic Aliases" feature to link related names `[PLANNED]`

#### "Heat map is slow to load"

**Cause**: Large dataset (20+ years of events) loading client-side

**Solution**:
1. Use time range filters to reduce data volume
2. Pre-load heat map at start of planning session (cache lasts 24 hours)
3. Contact support if performance doesn't improve

### Data Quality Issues

**Missing Historical Data**:
- Heat map only shows digitized events (typically last 20 years)
- Earlier events may need manual entry by admin
- Report gaps to admin team for backfill

**Inconsistent Topic Names**:
- "Sustainable Building" vs "Green Architecture" vs "Eco-Design"
- Admin can create topic aliases to merge related names `[PLANNED]`
- Use standardized topic list when entering new events

## Related Features

- **[Topic Backlog Management](../workflow/phase-a-setup.md#step-2-topic-selection)** - Where heat map integrates into workflow
- **[Event Management](../entity-management/events.md)** - Historical event data source
- **[Analytics Dashboard](analytics.md)** - Additional event performance metrics `[PLANNED]`

## Technical Details

### Data Sources

- **Events Table**: Event metadata (date, number, type)
- **Sessions Table**: Individual presentations within events
- **Topics Table**: Standardized topic taxonomy
- **Session_Topics Join Table**: Many-to-many relationship

### Calculation Logic

```
Heat Score = (Frequency Weight × 0.6) + (Recency Weight × 0.4)

Frequency Weight:
- 0 occurrences: 0
- 1 occurrence: 0.2
- 2-3 occurrences: 0.4
- 4-5 occurrences: 0.6
- 6-10 occurrences: 0.8
- 11+ occurrences: 1.0

Recency Weight (events ago):
- 1-2 events ago: 1.0
- 3-5 events ago: 0.8
- 6-10 events ago: 0.6
- 11-15 events ago: 0.4
- 16-20 events ago: 0.2
- 21+ events ago: 0.0
```

### Performance

- Heat map data cached for 24 hours client-side
- ~50KB data transfer for 20 years of events
- Renders in <500ms on modern browsers
- Supports 1000+ unique topics efficiently

### Accessibility

- Color-blind friendly palette (uses patterns + color)
- Keyboard navigation supported (arrow keys to move between cells)
- Screen reader compatible (table structure with descriptive labels)
- High contrast mode available (Settings → Accessibility)

---

**Next**: Learn about [File Uploads](file-uploads.md) for managing speaker materials →
