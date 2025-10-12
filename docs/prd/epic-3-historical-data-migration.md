# Epic 3: Historical Data Migration

## Epic Overview

**Epic Goal**: Migrate 20+ years of historical BATbern event data from the existing Angular website to the new platform with 100% data integrity, preserving continuity and making the valuable content archive accessible.

**Deliverable**: Complete migration of 54+ historical events, speaker profiles, presentations, and company data from JSON files to PostgreSQL microservices with S3 storage for files.

**Architecture Context**:
- **Migration Tool**: Dedicated Spring Boot batch application
- **Source**: Existing Angular website data (JSON files, presentations, images)
- **Targets**: Event Management, Speaker Coordination, Company Management, Attendee Experience Services
- **Storage**: AWS S3 for presentations, photos, and media with CDN
- **Validation**: Comprehensive data integrity checking and reporting

**Duration**: 3 weeks (Weeks 19-21)

**Prerequisites**: Epic 2 complete (all entity CRUD APIs operational and ready to receive data)

---

## Story 3.1: Historical Data Migration Service
**(Formerly Story 1.15)**

**User Story:**
As a **platform stakeholder**, I want all 20+ years of historical BATbern event data migrated accurately, so that the new platform maintains continuity and preserves our valuable content archive.

**Architecture Integration:**
- **Migration Tool**: Dedicated Spring Boot application with Spring Batch
- **Source Data**: Existing Angular application data (JSON, files, images)
- **Target Services**:
  - Event Management Service (events, sessions, timelines)
  - Speaker Coordination Service (speaker profiles, bios, photos)
  - Company Management Service (company affiliations, partner status)
  - Attendee Experience Service (content metadata for search)
- **File Storage**: AWS S3 for all presentations, photos, and documents
- **Validation**: PostgreSQL-based validation reports and integrity checks

**Migration Scope:**
- **54+ Historical Events** from ~2000-2024
- **500+ Speaker Profiles** with photos and CVs
- **1000+ Presentations** (PDF, PPTX files)
- **Company Relationships** and partner status history
- **Photo Galleries** for events
- **Event Metadata** (topics, locations, dates, attendance)

---

### Phase 1: Data Analysis & Mapping (Week 18, Days 1-2)

**Acceptance Criteria:**

**Data Inventory:**
1. **Complete Audit**: Catalog all existing data sources (JSON structure, file locations, image paths)
2. **Data Quality Assessment**: Identify missing data, inconsistencies, duplicates, format issues
3. **Volume Metrics**: Document exact counts (events, speakers, presentations, files, images)
4. **Sample Validation**: Manually verify sample data for correctness

**Domain Mapping:**
5. **Event Mapping**: Map legacy event JSON to Event Management Service schema
6. **Speaker Mapping**: Map speaker profiles to Speaker Coordination Service schema
7. **Company Mapping**: Map company data to Company Management Service schema
8. **File Mapping**: Map file paths to S3 bucket structure and CDN URLs
9. **Relationship Mapping**: Define foreign key relationships across services

**Migration Strategy:**
10. **Incremental Approach**: Define batch processing strategy (events by year, parallel processing)
11. **Rollback Plan**: Document rollback procedures for failed migrations
12. **Validation Strategy**: Define validation rules and acceptance thresholds
13. **Error Handling**: Plan for partial failures, retries, and manual intervention

**Deliverables:**
- [ ] Data inventory spreadsheet with counts and quality assessment
- [ ] Mapping documentation (legacy → new schema for each entity)
- [ ] Migration strategy document with timelines and risk mitigation
- [ ] Sample data validation report (10 events manually verified)

**Estimated Duration:** 2 days

---

### Phase 2: Migration Implementation (Week 18-19, Days 3-10)

**Acceptance Criteria:**

**Batch Processing Infrastructure:**
1. **Spring Batch Setup**: Configure Spring Batch with job repository and task execution
2. **Batch Jobs**: Create jobs for each entity type (EventMigrationJob, SpeakerMigrationJob, etc.)
3. **Chunk Processing**: Implement chunk-oriented processing (100 records per chunk)
4. **Parallel Execution**: Configure parallel step execution for independent entities

**Event Data Migration:**
5. **Event Migration**: Migrate 54+ historical events to Event Management Service
6. **Session Migration**: Migrate event sessions with speaker assignments
7. **Timeline Migration**: Migrate event timeline data (planning history)
8. **Metadata Migration**: Migrate event metadata (topics, descriptions, logistics)

**Speaker Data Migration:**
9. **Speaker Profile Migration**: Migrate speaker profiles with validation
10. **Photo Migration**: Upload speaker photos to S3 with CDN URL generation
11. **CV Migration**: Upload speaker CVs to S3 with access controls
12. **Speaking History**: Establish speaker-event relationships

**Content & Media Migration:**
13. **Presentation Files**: Migrate presentations to S3 (PDF, PPTX)
14. **Event Photos**: Migrate event photo galleries to S3
15. **Thumbnail Generation**: Generate thumbnails for presentations and photos
16. **CDN Configuration**: Update all file references to CDN URLs

**Company Data Migration:**
17. **Company Profiles**: Create company records in Company Management Service
18. **Partner Status**: Migrate historical partner status
19. **Employee Relationships**: Establish speaker-company affiliations
20. **Logo Migration**: Migrate company logos to S3

**Deliverables:**
- [ ] Spring Batch application with all migration jobs implemented
- [ ] All entity data migrated to appropriate microservices
- [ ] All files migrated to S3 with proper organization
- [ ] CDN URLs generated and database references updated

**Estimated Duration:** 8 days (1.5 weeks)

---

### Phase 3: Data Integrity & Validation (Week 19-20, Days 11-15)

**Acceptance Criteria:**

**Referential Integrity:**
1. **Foreign Key Validation**: Verify all event-speaker, speaker-company relationships
2. **Orphan Detection**: Identify orphaned records (speakers without events, etc.)
3. **Duplicate Detection**: Find and resolve duplicate entities
4. **Missing Data Reports**: Generate reports of missing optional data

**File Migration Validation:**
5. **File Count Verification**: Confirm all files migrated (source count = target count)
6. **File Integrity Check**: SHA-256 checksum validation for all files
7. **CDN Access Validation**: Verify all CDN URLs are accessible
8. **S3 Bucket Organization**: Confirm proper S3 key structure and lifecycle policies

**Search Index Building:**
9. **PostgreSQL Full-Text Indexes**: Build search indexes for presentations and events
10. **Speaker Search Index**: Build speaker search indexes
11. **Company Search Index**: Build company autocomplete indexes
12. **Search Validation**: Verify search returns accurate results for sample queries

**Data Validation Reports:**
13. **Migration Success Rate**: Generate report showing success/failure counts per entity type
14. **Data Quality Report**: Document data quality issues found and resolved
15. **Referential Integrity Report**: Confirm 100% referential integrity
16. **Performance Benchmarks**: Confirm migration met <4 hour requirement

**Deliverables:**
- [ ] Data integrity validation report (100% pass rate required)
- [ ] File migration report (all files accounted for)
- [ ] Search index validation report
- [ ] Performance report (migration timing and optimization)

**Estimated Duration:** 5 days (1 week)

---

### Migration Monitoring & Error Handling

**Real-Time Monitoring:**
1. **Progress Dashboard**: Spring Batch Admin dashboard showing job execution status
2. **ETA Calculations**: Real-time estimates for migration completion
3. **Error Tracking**: Real-time error log with failure reasons
4. **Resource Monitoring**: CPU, memory, network usage during migration

**Error Handling:**
5. **Retry Mechanisms**: Automatic retry for transient failures (3 attempts)
6. **Skip Policy**: Skip records with validation errors, log for manual review
7. **Manual Intervention**: Process for manually correcting and re-running failed records
8. **Rollback Procedures**: Scripts to rollback partially migrated data

**Performance Optimization:**
9. **Batch Size Tuning**: Optimize chunk sizes for performance (100-500 records)
10. **Parallel Processing**: Run independent jobs in parallel (events, speakers, companies)
11. **Database Connection Pooling**: Configure adequate connection pools
12. **S3 Multipart Upload**: Use multipart upload for files >10MB

**Deliverables:**
- [ ] Migration dashboard accessible via web interface
- [ ] Error log with detailed failure reasons
- [ ] Retry and rollback scripts tested and documented
- [ ] Performance optimization report

---

## Epic 3 Success Metrics

**Data Migration Success (End of Week 20):**
- ✅ 100% of 54+ historical events migrated successfully
- ✅ 100% of speaker profiles migrated with photos/CVs
- ✅ 100% of presentations migrated to S3 with CDN URLs
- ✅ 100% referential integrity validation pass rate
- ✅ All company relationships established correctly
- ✅ Search indexes built and verified functional

**Technical Performance:**
- **Migration Time**: Complete migration in <4 hours total runtime
- **Data Integrity**: 100% validation pass rate (zero data loss)
- **File Upload Speed**: >10MB/s average for S3 uploads
- **Error Rate**: <1% requiring manual intervention
- **Search Performance**: Migrated content searchable in <500ms

**Business Value:**
- **Content Continuity**: 20+ years of BATbern history preserved
- **Public Website Ready**: Historical content available for browsing (Epic 4)
- **Search Functional**: Attendees can discover historical presentations
- **Speaker Profiles**: Complete speaker history accessible
- **Archive Complete**: Platform ready for production launch

**Quality Gates:**
- [ ] Zero data loss (all source data accounted for)
- [ ] 100% file integrity (checksums match)
- [ ] All CDN URLs accessible and functional
- [ ] Search returns accurate results for all content types
- [ ] No orphaned records or broken relationships
- [ ] Performance benchmarks met (<4 hour migration)

---

## Dependencies & Prerequisites

**Required Before Starting Epic 3:**
- ✅ Epic 2 complete (all entity CRUD APIs operational)
- ✅ Company Management Service API ready
- ✅ Event Management Service API ready
- ✅ Speaker Coordination Service API ready
- ✅ S3 buckets configured with CDN integration
- ✅ PostgreSQL databases ready for data import
- ✅ Redis caches configured for search

**Enables Following Epics:**
- **Epic 4**: Public Website (requires migrated events and content for display)
- **Epic 5**: Enhanced Organizer Workflows (historical data provides context)
- **Epic 6**: Speaker Portal (speaker history shows past participation)

---

## Risk Management

**Technical Risks:**
- **Risk**: Large file uploads may timeout or fail
  - **Mitigation**: Use multipart upload, implement retry logic, monitor progress
- **Risk**: Data quality issues may require extensive cleanup
  - **Mitigation**: Phase 1 analysis identifies issues early, manual cleanup process
- **Risk**: Referential integrity violations due to inconsistent legacy data
  - **Mitigation**: Fuzzy matching for speakers/companies, manual review queue

**Schedule Risks:**
- **Risk**: Migration taking longer than 3 weeks
  - **Mitigation**: Prioritize event and speaker data first, defer non-critical media
  - **Mitigation**: Parallel processing and performance optimization
- **Risk**: Data quality issues requiring extensive manual intervention
  - **Mitigation**: Accept some data gaps, document for post-launch cleanup

---

## Transition to Epic 4

**Epic 3 Exit Criteria:**
- [ ] All migration batch jobs completed successfully
- [ ] Data integrity validation 100% pass rate
- [ ] All files accessible via CDN URLs
- [ ] Search indexes operational and tested
- [ ] Migration documentation complete
- [ ] Rollback procedures tested

**Handoff to Epic 4 (Public Website):**
- Historical events available via Event Management API
- Event archive searchable via full-text search
- Presentations downloadable via S3/CDN
- Speaker profiles complete with photos
- Platform ready for public website launch

---

## Notes & References

**Original Story Location:**
- Full story details: `docs/prd/epic-1-foundation-stories.md` Story 1.15 (archived)
- Individual story file: `docs/stories/1.15-*.md`

**Data Sources:**
- Existing Angular website data location: [TBD - specify actual path]
- JSON schema documentation: [TBD]
- Sample data for testing: [TBD]

**Epic Reorganization Context:**
- Date: 2025-10-12
- Reason: Isolate data migration as dedicated epic after CRUD foundation (Epic 2)
- Dependencies: Requires Epic 2 complete before migration can begin
- Enables: Public website (Epic 4) requires migrated historical data

---

*This epic was created as part of the epic reorganization on 2025-10-12 to prioritize data migration immediately after entity CRUD foundation.*
