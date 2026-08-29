# GOD

GOD is a stateless, atomic OpenAI Responses API processor and Paper integration
for the Minecraft server. It accepts player messages and curated server events,
makes one API request, validates one structured decision, logs the interaction,
and can execute a constrained set of temporary in-game actions.

## Requirements

- PowerShell 7+
- `OPENAI_API_KEY` in the environment of the process invoking GOD
- An OpenAI API project with access to the configured model

Never put an API key in this directory or commit one to Git.

## Usage

```powershell
.\Invoke-God.ps1 `
    -Player "LiamTolkkinen" `
    -PlayerUuid "95a9b364-216c-4504-9cc5-ecdb76f9d65b" `
    -Message "God, are you listening?"
```

Optional context must be a JSON object:

```powershell
.\Invoke-God.ps1 `
    -Player "LiamTolkkinen" `
    -PlayerUuid "95a9b364-216c-4504-9cc5-ecdb76f9d65b" `
    -Message "God, are you listening?" `
    -ContextJson '{"dimension":"minecraft:overworld","onlinePlayers":1}'
```

The script writes the validated decision as JSON to standard output. Every
invocation appends a timestamped audit record to `logs\interactions.jsonl`,
including silent and failed interactions. Logs are excluded from Git.

Administrator status is resolved from the server's live `ops.json` by UUID.
Optional player-specific doctrines and relationship ledgers live beneath
`players\<uuid>\`.

## Atomicity

A named mutex permits only one invocation to call the API at a time. The
entire model response is parsed and validated before it is returned. Every
interaction has a UUID suitable for later deduplication when Minecraft action
execution is added.

## Configuration

Committed defaults live in `config.json`. To override them locally, copy it to
`config.local.json`; the local file is excluded from Git. The API key is read
only from `OPENAI_API_KEY`.

`doctrine.md` is deliberately incomplete until the GOD roleplay behavior is
defined.

## Paper plugin

The conversation bridge lives in `plugin\`. It detects `God` as a standalone
word in ordinary chat, resolves trusted player policy, queues one atomic API
request at a time, and returns a public reply, silence, and up to five validated
temporary actions.

Each request includes up to fifteen recent public messages and the same
player's ten most recent interactions, all limited to the preceding thirty
minutes, plus up to 25 nearby living entities within 32 blocks. This provides
bounded continuity without retaining an API-side conversation.

The mutable material shop, service prices, and earning limits live in
`economy.json`. Regular players begin with zero favor and spend it separately
from relationship; administrators bypass economic cost. GOD loads shop details
through read-only tools only when relevant. Trusted code calculates and
validates every transaction.

Material offerings use expiring server-owned quotes. When a player offers all
of a material, the plugin calculates the largest complete offering permitted by
live inventory, configured bundle size, transaction size, remaining daily
offering allowance, and remaining favor balance capacity. Inventory removal
and favor credit execute through one synchronized economy operation, with item
restoration on failure.

Curated server events also enter the same atomic queue. The initial set covers
PvP kills, Totem saves, major advancements, significant hostile-mob kills, and
morally significant kills such as villagers or iron golems. Each category can
be disabled in the plugin configuration. GOD may remain silent on any passive
event to avoid narrating routine gameplay.

Build it against the Paper libraries already installed on this server:

```powershell
.\plugin\build.ps1
```

The resulting `plugin\build\God.jar` is a generated artifact and is excluded
from Git. Deploy it to `C:\MinecraftServer\plugins\God.jar` and fully restart
the server. The Minecraft process must have `OPENAI_API_KEY` in its environment.

## In-game administration

Operators may use `/god on`, `/god listen`, `/god off`, `/god status`, and
`/god reload`. Economy configuration is mutable both in `economy.json` and
through `/god economy` commands:

```text
/god economy buy set <item> <item-quantity> <favor-quantity>
/god economy buy remove <item>
/god economy buy list
/god economy sell set <item> <item-quantity> <favor-quantity>
/god economy sell remove <item>
/god economy sell list
/god economy service set <service> <favor-price>
/god economy earning set <event> <favor-reward>
/god economy limit set <limit> <value>
/god economy reload
```

Aliases are managed with `/god alias set <online-player> <alias>`, `/god alias
remove <alias>`, and `/god alias list`. Favor can be inspected or adjusted with
`/god favor get|add|take`.

Players manage up to five personal locations with `/godlocation save`,
`/godlocation list`, `/godlocation delete`, and `/godlocation go`. Teleports
use the configured service prices. `/duel <player>` creates a 60-second
challenge; a reciprocal command or the invited player's first attack accepts
it. Attacking before acceptance remains a recorded offence.
