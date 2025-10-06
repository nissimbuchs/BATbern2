#!/bin/bash

# Script to append API Requirements, Action APIs, and Navigation Map sections to wireframe files

# Array of files to process (excluding already processed ones)
files=(
    "story-2.3-basic-publishing-engine.md"
    "story-2.4-current-event-landing.md"
    "story-2.4-event-registration.md"
    "story-3.1-speaker-matching-interface.md"
    "story-3.2-invitation-response.md"
    "story-3.3-material-submission-wizard.md"
    "story-3.3-presentation-upload.md"
    "story-3.3-speaker-dashboard.md"
    "story-3.5-event-timeline.md"
    "story-4.3-progressive-publishing.md"
    "story-4.4-logistics-coordination.md"
    "story-5.1-content-discovery.md"
    "story-5.2-personal-dashboard.md"
    "story-5.3-mobile-pwa.md"
    "story-5.3-offline-content.md"
    "story-6.1-employee-analytics.md"
    "story-6.1-partner-analytics-dashboard.md"
    "story-6.2-brand-exposure.md"
    "story-6.3-budget-management.md"
    "story-6.3-custom-report-builder.md"
    "story-6.4-strategic-planning.md"
    "story-6.4-topic-voting.md"
    "story-6.5-partner-meetings.md"
    "story-7.1-speaker-community.md"
    "story-7.1-speaker-profile-management.md"
    "story-7.3-communication-hub.md"
    "story-7.4-community-features.md"
)

cd /Users/nissim/dev/bat/BATbern/docs/wireframes

for file in "${files[@]}"; do
    if [ -f "$file" ]; then
        # Check if file already has the sections
        if grep -q "## API Requirements" "$file"; then
            echo "SKIP: $file already has API sections"
        else
            echo "PROCESSING: $file"
            # Append placeholder sections to file
            cat >> "$file" << 'EOF'

---

## API Requirements

### Data Loading APIs

| API Endpoint | Purpose | Called When | Status in API Design |
|--------------|---------|-------------|---------------------|
| [To be analyzed] | [To be completed] | [To be completed] | ❌ MISSING |

### Real-time Update APIs (if applicable)

| API Endpoint | Purpose | Status |
|--------------|---------|--------|
| [To be analyzed] | [To be completed] | ❌ MISSING |

---

## Action APIs

| Action | API Endpoint | Purpose | Status |
|--------|--------------|---------|--------|
| [To be analyzed] | [To be completed] | [To be completed] | ❌ MISSING |

---

## Navigation Map

| Trigger | Target Screen | Wireframe File | Status |
|---------|---------------|----------------|--------|
| [To be analyzed] | [To be completed] | [To be completed] | ❌ MISSING |
EOF
            echo "DONE: $file"
        fi
    else
        echo "ERROR: File not found: $file"
    fi
done

echo ""
echo "Processing complete!"
