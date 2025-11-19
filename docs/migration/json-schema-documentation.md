# JSON Schema Documentation

Generated: 2025-11-17T18:50:23.283Z

## Overview

| File | Total Records | Fields | Optional Fields |
|------|---------------|--------|------------------|
| sessions.json | 302 | 6 | 3 |
| topics.json | 60 | 6 | 2 |
| pictures.json | 163 | 2 | 0 |

---

## sessions.json

Total Records: 302

### Fields

| Field | Type | Occurrence | Sample Value |
|-------|------|------------|---------------|
| abstract | string | 77% (232/302) | `Die Mobiliar verfügt über eine Mainframe-basierte ...` |
| authoren | string | 23% (70/302) | `Thomas Goetz` |
| bat | number | 100% (302/302) | `1` |
| pdf | string | 100% (302/302) | `BAT_Nr.01.pdf` |
| referenten | array | 77% (232/302) | (object) |
| title | string | 100% (302/302) | `Programmheft` |

### Nested Schema: referenten (Speaker)

| Field | Type | Sample Value |
|-------|------|---------------|
| bio | string | `Thomas Goetz studierte an der Universität Bern Che...` |
| company | string | `mobiliar` |
| name | string | `Thomas Goetz, Die Mobiliar` |
| portrait | string | `thomas.goetz.jpg` |

### Optional Fields

- **authoren**: 23% of records
- **abstract**: 77% of records
- **referenten**: 77% of records

---

## topics.json

Total Records: 60

### Fields

| Field | Type | Occurrence | Sample Value |
|-------|------|------------|---------------|
| bat | number | 100% (60/60) | `1` |
| datum | string | 100% (60/60) | `24. Juni 05, 16:00h - 18:30h` |
| eventType | string | 100% (60/60) | `Abend-BAT` |
| next | number | 3% (2/60) | `1` |
| planned | number | 5% (3/60) | `1` |
| topic | string | 100% (60/60) | ` GUI Frameworks ` |

---

## pictures.json

Total Records: 163

### Fields

| Field | Type | Occurrence | Sample Value |
|-------|------|------------|---------------|
| bat | number | 100% (163/163) | `1` |
| image | string | 100% (163/163) | `01.jpg` |
