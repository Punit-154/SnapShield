# phish_domains.db

## Source
Generated phishing domain database with 521 domains covering common scam patterns.

## Schema
```sql
CREATE TABLE phish_domains (
    domain TEXT PRIMARY KEY,
    type TEXT
);
```

## Contents
- **521 total domains**
- Bank impersonation (HSBC, SBI, ICICI, HDFC, Axis, etc.)
- Government service impersonation (Income Tax, Aadhaar, UIDAI, EPFO, etc.)
- Tech company impersonation (Apple, Google, Microsoft, Amazon, etc.)
- Generic phishing patterns (secure-login, verify-account, etc.)
- Mix of suspicious TLDs (.tk, .ml, .ga, .cf, .xyz, .top, .club) and .com/.net

## Update Plan
A future version should support downloading an updated `phish_domains.db` via the privacy proxy,
with a version/ETag check to avoid unnecessary downloads.

## Regeneration
To regenerate the database:
```bash
python scripts/generate_phish_db.py
```

## Snapshot Date
2026-06-13
