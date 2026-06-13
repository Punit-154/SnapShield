# phish_domains.db

## Source
This is a placeholder for the offline phishing domain reputation database.

## Schema
```sql
CREATE TABLE phish_domains (
    domain TEXT PRIMARY KEY,
    type TEXT
);
```

## Update Plan
A future version should support downloading an updated `phish_domains.db` via the privacy proxy,
with a version/ETag check to avoid unnecessary downloads.

## Data Source
Use a public phishing domain feed that permits redistribution. Examples:
-phishtank.com (check license)
- OpenPhish feeds
- PhishStats

Snapshot date: N/A (placeholder)
