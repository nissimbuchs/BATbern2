# Story: Custom Report Builder - Wireframe

**Story**: Epic 6, Story 3
**Screen**: Custom Report Builder
**User Role**: Partner
**Related FR**: FR9 (Custom Reports)

---

## 5. Custom Report Builder (FR9)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back                         Custom Report Builder                      [Templates]│
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  Report Title: [Q1 2025 Sponsorship ROI Report                    ]                  │
│  Report Period: [Q1 2025 ▼]     Format: [● PDF] [○ Excel] [○ PowerPoint]           │
│                                                                                       │
│  ┌──── REPORT SECTIONS ─────────────────────────────────────────────────────────────┐│
│  │                                                                                  ││
│  │  Drag sections to include in report:           Included Sections:              ││
│  │                                                                                  ││
│  │  Available:                                    Report Structure:               ││
│  │  ┌─────────────────────────┐                  ┌──────────────────────────────┐││
│  │  │ □ Competitor Analysis   │                  │ 1. ☑ Executive Summary       │││
│  │  │ □ Content Performance   │                  │ 2. ☑ Employee Attendance     │││
│  │  │ □ Future Projections    │     ────►        │ 3. ☑ ROI Analysis            │││
│  │  │ □ Topic Recommendations │                  │ 4. ☑ Brand Exposure          │││
│  │  │ □ Detailed Financials   │     ◄────        │ 5. ☑ Department Breakdown    │││
│  │  │ □ Benchmark Comparison  │                  │ 6. ☑ Event Performance       │││
│  │  │ □ Individual Tracking   │                  │ 7. ☑ Key Recommendations     │││
│  │  └─────────────────────────┘                  └──────────────────────────────┘││
│  │                                                                                  ││
│  │  [Add Custom Section] [Import from Previous] [Reset]                            ││
│  └──────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                       │
│  ┌──── CUSTOMIZE SECTIONS ──────────────────────────────────────────────────────────┐│
│  │                                                                                  ││
│  │  Selected: Employee Attendance                                                  ││
│  │                                                                                  ││
│  │  Data to Include:                          Visualization:                       ││
│  │  ☑ Total attendance numbers               ● Bar chart                          ││
│  │  ☑ Department breakdown                   ○ Line graph                         ││
│  │  ☑ YoY comparison                        ○ Pie chart                          ││
│  │  ☑ Engagement metrics                     ○ Table only                         ││
│  │  ☐ Individual names (privacy)                                                   ││
│  │                                                                                  ││
│  │  Time Range:                              Comparison:                           ││
│  │  ● Current period only                    ☑ Previous period                     ││
│  │  ○ Last 12 months                        ☑ Industry average                    ││
│  │  ○ Custom: [___] to [___]                ☐ Competitor data                     ││
│  │                                                                                  ││
│  │  Additional Options:                                                            ││
│  │  ☑ Include insights and recommendations                                         ││
│  │  ☑ Add executive talking points                                                 ││
│  │  ☐ Include raw data tables                                                      ││
│  └──────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                       │
│  ┌──── BRANDING & DISTRIBUTION ─────────────────────────────────────────────────────┐│
│  │                                                                                  ││
│  │  Report Branding:                          Distribution:                        ││
│  │  ● UBS + BATbern co-branded               Recipients:                          ││
│  │  ○ UBS only                               ☑ Myself (thomas.mueller@ubs.ch)     ││
│  │  ○ BATbern only                           ☑ Leadership team (5 recipients)     ││
│  │  ○ Minimal branding                       ☐ Extended team                      ││
│  │                                                                                  ││
│  │  Confidentiality:                         Schedule:                            ││
│  │  ● Internal use only                      ● Generate now                        ││
│  │  ○ Shareable externally                   ○ Schedule monthly                   ││
│  │  ○ Public                                 ○ Schedule quarterly                 ││
│  │                                                                                  ││
│  │  [Preview Report] [Generate & Download] [Save Template] [Schedule]              ││
│  └──────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## API Requirements

### Initial Page Load APIs

When the Custom Report Builder screen loads, the following APIs are called to provide the necessary data:

1. **GET /api/v1/partners/{partnerId}/reports/templates**
   - Returns: List of available report templates (standard, saved custom), template metadata, preview thumbnails
   - Used for: Populate templates dropdown and quick-start options

2. **GET /api/v1/partners/{partnerId}/reports/sections/available**
   - Returns: List of all available report sections with descriptions, data requirements, visualization options
   - Used for: Populate available sections list for drag-and-drop

3. **GET /api/v1/partners/{partnerId}/reports/last-used**
   - Returns: Last used report configuration if exists, including sections, settings, branding choices
   - Used for: Pre-fill builder with previous settings for convenience

4. **GET /api/v1/partners/{partnerId}/settings/branding**
   - Returns: Partner branding options, logos, color schemes, approved branding combinations
   - Used for: Populate branding options in distribution section

5. **GET /api/v1/partners/{partnerId}/team/recipients**
   - Returns: List of team members with roles for distribution list (leadership, extended team)
   - Used for: Populate recipient selection options

6. **GET /api/v1/partners/{partnerId}/reports/scheduled**
   - Returns: List of scheduled reports with schedules, recipients, next run times
   - Used for: Display existing scheduled reports (if accessed from schedule view)

---

## Action APIs

### Report Configuration

1. **POST /api/v1/partners/{partnerId}/reports/preview**
   - Payload: `{ title, period, format, sections: [], sectionConfigs: {}, branding, confidentiality }`
   - Response: Preview URL, preview generation task ID, estimated completion time
   - Used for: Generate report preview before final generation

2. **GET /api/v1/partners/{partnerId}/reports/preview/{previewId}**
   - Returns: Preview PDF/HTML URL, page count, section breakdown
   - Used for: Display generated preview in modal or new tab

3. **POST /api/v1/partners/{partnerId}/reports/generate**
   - Payload: `{ title, period, format, sections: [], sectionConfigs: {}, branding, confidentiality, distribution }`
   - Response: Report generation task ID, estimated completion time, queue position
   - Used for: Generate final report for download

4. **GET /api/v1/partners/{partnerId}/reports/generate/{taskId}/status**
   - Returns: Generation status, progress percentage, current section being processed
   - Used for: Poll generation progress for progress bar

5. **GET /api/v1/partners/{partnerId}/reports/generate/{taskId}/download**
   - Returns: Download URL, file size, expiration timestamp
   - Used for: Download completed report

### Template Management

6. **POST /api/v1/partners/{partnerId}/reports/templates/save**
   - Payload: `{ templateName, description, sections: [], sectionConfigs: {}, isDefault: boolean }`
   - Response: Template ID, confirmation
   - Used for: Save current configuration as reusable template

7. **GET /api/v1/partners/{partnerId}/reports/templates/{templateId}**
   - Returns: Complete template configuration, metadata
   - Used for: Load template into builder

8. **PUT /api/v1/partners/{partnerId}/reports/templates/{templateId}**
   - Payload: Updated template configuration
   - Response: Update confirmation
   - Used for: Update existing template

9. **DELETE /api/v1/partners/{partnerId}/reports/templates/{templateId}**
   - Response: Deletion confirmation
   - Used for: Delete saved template

10. **GET /api/v1/partners/{partnerId}/reports/previous**
    - Query params: limit (10)
    - Returns: List of previously generated reports with configurations
    - Used for: Import configuration from previous report

### Section Configuration

11. **GET /api/v1/partners/{partnerId}/reports/sections/{sectionId}/options**
    - Returns: Available data fields, visualization types, comparison options for specific section
    - Used for: Populate section customization options when section is selected

12. **POST /api/v1/partners/{partnerId}/reports/sections/{sectionId}/validate**
    - Payload: Section configuration
    - Response: Validation result, warnings, data availability confirmation
    - Used for: Validate section configuration before adding to report

13. **POST /api/v1/partners/{partnerId}/reports/sections/custom**
    - Payload: `{ title, description, dataSource, fields: [], visualizationType }`
    - Response: Custom section ID, configuration
    - Used for: Create custom report section

### Scheduling & Distribution

14. **POST /api/v1/partners/{partnerId}/reports/schedule**
    - Payload: `{ reportConfig, schedule: "monthly|quarterly|custom", recipients: [], nextRun: timestamp }`
    - Response: Scheduled report ID, confirmation, next execution time
    - Used for: Schedule automatic report generation and distribution

15. **GET /api/v1/partners/{partnerId}/reports/scheduled/{scheduleId}**
    - Returns: Scheduled report details, execution history, recipient list
    - Used for: View scheduled report details

16. **PUT /api/v1/partners/{partnerId}/reports/scheduled/{scheduleId}**
    - Payload: Updated schedule configuration
    - Response: Update confirmation
    - Used for: Modify scheduled report

17. **DELETE /api/v1/partners/{partnerId}/reports/scheduled/{scheduleId}**
    - Response: Deletion confirmation
    - Used for: Cancel scheduled report

18. **POST /api/v1/partners/{partnerId}/reports/{reportId}/distribute**
    - Payload: `{ recipients: [], message, attachmentUrl, deliveryMethod: "email|portal" }`
    - Response: Distribution task ID, delivery confirmation
    - Used for: Manually distribute generated report

### Data & Insights

19. **GET /api/v1/partners/{partnerId}/reports/sections/{sectionId}/data**
    - Query params: period, filters, comparisonPeriod
    - Returns: Raw data for section preview, available data points
    - Used for: Preview data availability and quality for section configuration

20. **GET /api/v1/partners/{partnerId}/reports/insights/generate**
    - Query params: sections, period
    - Returns: AI-generated insights and recommendations for selected sections and period
    - Used for: Generate insights to include in report sections

---

## Navigation Map

### Primary Navigation Actions

1. **← Back button** → Navigate back to `Partner Analytics Dashboard`
   - Prompts to save current configuration if changes made
   - Returns to main dashboard

2. **[Templates] button** → Opens templates panel (overlay or side panel)
   - Browse saved templates
   - Preview template structure
   - Load template into builder
   - No full screen navigation

3. **Report Period dropdown** → Updates available data
   - Refreshes section data availability
   - Updates date range for all sections
   - No screen navigation

4. **Format radio buttons (PDF/Excel/PowerPoint)** → Updates format selection
   - Changes available customization options
   - Updates preview rendering
   - No screen navigation

5. **Available section item (drag)** → Adds to included sections
   - Drag-and-drop to report structure
   - Auto-assigns section number
   - Triggers section configuration panel
   - No screen navigation

6. **Included section item (drag)** → Reorders sections
   - Drag to reorder in report structure
   - Updates section numbering
   - No screen navigation

7. **Included section checkbox (uncheck)** → Removes from report
   - Removes section from structure
   - Returns to available sections
   - No screen navigation

8. **Included section item click** → Loads section configuration
   - Updates "Customize Sections" panel
   - Shows section-specific options
   - No screen navigation

9. **[Add Custom Section] button** → Opens custom section builder modal
   - Define section title and description
   - Select data source
   - Configure fields and visualization
   - Adds to included sections when saved

10. **[Import from Previous] button** → Opens previous reports list modal
    - Shows recently generated reports
    - Preview report structure
    - Load configuration into builder
    - No screen navigation after import

11. **[Reset] button** → Clears report configuration
    - Confirmation prompt
    - Clears all sections and settings
    - Resets to default state
    - No screen navigation

12. **Visualization radio buttons** → Updates section visualization
    - Changes chart type for selected section
    - Updates preview if available
    - No screen navigation

13. **Data checkbox toggles** → Updates section data inclusion
    - Adds/removes data points from section
    - May affect visualization options
    - No screen navigation

14. **Comparison checkboxes** → Adds comparison data
    - Includes comparative metrics in section
    - May affect report length
    - No screen navigation

15. **Branding radio buttons** → Updates report branding
    - Changes logos and styling
    - Updates preview branding
    - No screen navigation

16. **Recipients checkboxes** → Updates distribution list
    - Adds/removes recipients
    - Updates email count
    - No screen navigation

17. **[Preview Report] button** → Navigate to `Report Preview Screen`
    - Generates report preview
    - Shows loading modal during generation
    - Opens preview in new tab or modal
    - Can return to builder to make changes

18. **[Generate & Download] button** → Triggers report generation
    - Validates configuration
    - Shows generation progress modal
    - Downloads report when complete
    - No screen navigation

19. **[Save Template] button** → Opens save template modal
    - Enter template name and description
    - Set as default option
    - Saves configuration
    - Shows success notification

20. **[Schedule] button** → Navigate to `Report Scheduling Screen`
    - Configure schedule (monthly, quarterly, custom)
    - Set distribution preferences
    - Preview schedule
    - Save scheduled report

### Secondary Navigation (Data & Configuration)

21. **Template selection from Templates panel** → Loads template configuration
    - Populates all builder fields
    - Loads included sections
    - Applies section configurations
    - No screen navigation

22. **Previous report selection from Import modal** → Loads previous configuration
    - Imports report structure
    - Loads section configurations
    - Pre-fills all settings
    - No screen navigation

23. **Time range selection change** → Updates data availability
    - Validates data exists for range
    - Updates section previews
    - May show warnings for limited data
    - No screen navigation

24. **Section configuration change** → Updates section preview
    - Recalculates data requirements
    - Updates estimated report size
    - May trigger validation warnings
    - No screen navigation

### Event-Driven Navigation

25. **Report generation complete** → Shows notification with download link
    - Download available
    - View online option
    - Share with team option
    - Can navigate to report viewer

26. **Preview generation complete** → Opens preview
    - Displays in modal or new tab
    - Navigation to full preview screen
    - Edit/regenerate options

27. **Report generation failure** → Shows error notification
    - Explains failure reason
    - Suggests corrections
    - Offers retry option
    - No automatic navigation

28. **Template saved successfully** → Shows success notification
    - Template available in Templates panel
    - Option to use immediately
    - No automatic navigation

29. **Schedule created successfully** → Shows confirmation notification
    - Next execution time displayed
    - Links to scheduled reports list
    - Option to navigate to schedule manager

30. **Data validation warning** → Shows warning banner
    - Indicates sections with limited data
    - Suggests alternative configurations
    - No automatic navigation

31. **Distribution complete** → Shows success notification
    - Delivery confirmation
    - Recipient list shown
    - Delivery report available
    - No automatic navigation

32. **Scheduled report generated** → Shows notification (if user is online)
    - Report available in reports library
    - Links to view/download
    - Distribution status
    - No automatic navigation

---
