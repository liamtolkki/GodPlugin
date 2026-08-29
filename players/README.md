# Player policy and relationship data

Players are keyed by authenticated Minecraft UUID, not display name:

```text
players/<uuid>/doctrine.md
players/<uuid>/relationship.json
```

`doctrine.md` is optional, owner-authored, and authoritative. GOD must never
create or derive one from behavior.

`relationship.json` is an evidence ledger. If absent, the effective value
starts at 50. Events use this form:

```json
{
  "events": [
    {
      "timestamp": "2026-08-20T02:00:00-04:00",
      "category": "protected_player",
      "impact": 8,
      "description": "Defended another player without asking for reward."
    }
  ]
}
```

Impact decays with a 30-day half-life. Records are appended, not silently
rewritten or removed.

