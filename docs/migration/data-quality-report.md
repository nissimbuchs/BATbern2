# Data Quality Report

Generated: 2025-11-17T18:54:13.391Z

## Summary

- **Total Issues Found**: 9
- **Critical**: 0
- **Warning**: 4
- **Info**: 5

## Warnings

These issues should be addressed to ensure data quality.

### Duplicate session titles within same event

- **Type**: duplicate
- **Field**: title
- **Affected Records**: 6
- **Examples**:
  - 10-interaktive komplexe gui's bei dhl
  - 25-outsourcing - wie wir unsere eigene lieferfähigkeit steigern
  - 26-reiseplanung tür-zu-tür und intermodal bei der postauto app
- **Remediation**: Review and deduplicate or differentiate session titles.

### PDF files referenced but not found on disk

- **Type**: referential_integrity
- **Field**: pdf
- **Affected Records**: 96
- **Examples**:
  - n/a
  - n/a
  - n/a
  - n/a
  - n/a
- **Remediation**: Locate missing PDF files or update references.

### Portrait images referenced but not found on disk

- **Type**: referential_integrity
- **Field**: portrait
- **Affected Records**: 17
- **Examples**:
  - Matilda Anello, Credit Suisse: matilda.anello.jpg
  - Walter Grolimund, Swisscom Fixnet: walter.grolimund.jpg
  - Dr. Thomas Wettstein, BKW FMB Energie AG: thomas.wettstein.jpg
  - Richard Schären (Informatikstrategieorgan Bund ISB): richard.schaeren.jpg
  - Guido Steiner, RTC AG: guido.steiner.jpg
- **Remediation**: Locate missing portrait images or update references.

### Company name variations detected

- **Type**: inconsistency
- **Field**: company
- **Affected Records**: 5
- **Examples**:
  - mobiliar / Mobiliar
  - postfinance / PostFinance
  - sbb / SBB
- **Remediation**: Normalize company names during migration.

## Information

These are observations that may be useful during migration.

### Speaker entries with missing or empty biography

- **Type**: missing_field
- **Field**: bio
- **Affected Records**: 3
- **Examples**:
  - Jérôme Koller, Die Mobiliar
  - Christof Leuenberger, die Mobiliar
  - Peter Kummer, Stephan Sigrist, Marco Brenner, Oscar Nierstrasz - Moderation: Nissim Buchs, Andreas Grütter
- **Remediation**: Add biographies for speakers where available.

### Speaker entries with missing portrait filename

- **Type**: missing_field
- **Field**: portrait
- **Affected Records**: 54
- **Examples**:
  - Dierk Matthäus, Universität Bern
  - Dr. Stephan Aier, Universität St. Gallen
  - Dr. Christian Wilhelmi, Alpiq AG
- **Remediation**: Add portrait images for speakers or use placeholder.

### Same PDF referenced by multiple sessions (may be intentional)

- **Type**: duplicate
- **Field**: pdf
- **Affected Records**: 20
- **Examples**:
  - n/a
  - https://www.slideshare.net/batbern/
  - 
- **Remediation**: Verify if PDF sharing is intentional or needs correction.

### Events with no sessions/presentations

- **Type**: referential_integrity
- **Field**: bat
- **Affected Records**: 3
- **Examples**:
  - BAT 58:  AI in der Software Entwicklung
  - BAT 59:  Thema noch offen
  - BAT 60:  Thema noch offen
- **Remediation**: Add sessions for these events or confirm they are future events.

### Multiple date formats detected in topics

- **Type**: inconsistency
- **Field**: datum
- **Affected Records**: 60
- **Examples**:
  - DD. Month YY, HH:MMh
  - other
  - DD. Month YYYY
- **Remediation**: Standardize date format during migration.

