# GodPlugin Development Plan

## Architecture rule

GodPlugin owns conversation with the language model. Other plugins own gameplay behavior, state, documentation, validation, persistence, and actions.

GodPlugin must not read another plugin's private database or depend on another plugin's internal implementation classes.

## Repository roles

- GodApi: shared integration contracts
- GodPlugin: player conversation, model requests, integration discovery, and tool routing
- Sanctuary: Sanctuary gameplay and progression
- DivineFavor: favor balances, offerings, shop behavior, and transactions
- Waypoints: saved locations, teleportation, and death return

Deferred systems such as reputation, duels, and generic divine intervention APIs are not part of the current architecture.

## Completed

### GodApi 0.1.0

GodApi is released as a plain Java library with:

- `GodIntegration`
- `GodTool`
- `GodToolContext`
- `GodToolResult`

The API has no Paper, Bukkit, Gson, or OpenAI dependency.

### GodPlugin build normalization

GodPlugin now uses a root Gradle project targeting:

- Java 25
- Paper 26.1.2
- Gradle 9.7.1

The build pins GodApi 0.1.0 and produces the deployable shaded GodPlugin JAR.

### Integration discovery

GodPlugin discovers `GodIntegration` services through Bukkit `ServicesManager`.

Each interaction receives a fresh validated snapshot. The registry:

- works with zero integrations
- rejects invalid integration IDs
- rejects invalid tool names
- rejects duplicate integration IDs
- rejects duplicate tool names
- ignores disabled providers
- removes disabled or unregistered providers from later request snapshots

### Model integration path

GodPlugin now:

1. receives the player message
2. captures bounded server and conversation context
3. discovers active integrations
4. appends integration instructions
5. advertises integration tools to the OpenAI Responses API
6. routes requested tool calls to the owning `GodTool`
7. returns `GodToolResult` through `function_call_output`
8. continues the model response
9. validates the final reply or silence decision
10. audits the interaction

GodApi remains serializer-neutral. Gson conversion stays inside GodPlugin.

### Legacy gameplay cleanup

The following systems have been removed from active GodPlugin source and configuration:

- duels
- PvP offence tracking
- relationship and moral scoring
- relationship decay and ledgers
- relationship-driven favor earnings
- automatic administrator arrival/departure commentary
- automatic PvP commentary
- automatic Totem commentary
- automatic advancement commentary
- automatic mob-kill commentary
- automatic death-return offers
- generic model-controlled gameplay actions
- item rewards and material offerings
- direct favor management
- saved locations and death return
- aliases
- player-specific doctrine
- standalone PowerShell model runner

Historical implementations remain available through Git history for extraction into their proper owning plugins.

## Current GodPlugin responsibility

GodPlugin should stay limited to:

- trigger-word chat
- optional God administration commands
- OpenAI Responses API client behavior
- bounded queueing and timeouts
- cooldowns
- recent public chat context
- recent player interaction context
- base doctrine
- integration discovery and validation
- integration instruction assembly
- integration tool advertisement
- integration tool execution routing
- tool-result continuation
- audit logging
- diagnostics

## Next issue: tool execution policy

GodPlugin supports `on`, `off`, and `listen` modes.

Before real gameplay integrations are registered, GodApi needs a reliable way to distinguish tools that only read state from tools that may change state. GodApi 0.1.0 does not currently express that distinction.

This matters because `listen` historically allowed information lookup while suppressing gameplay actions.

Do not rely on tool naming conventions to determine whether a tool is safe to execute in `listen` mode.

Recommended next step:

1. extend GodApi with explicit tool execution metadata
2. release the next GodApi version
3. update GodPlugin to advertise only read-only tools while in `listen`
4. test that mutating tools cannot execute in `listen`

Keep the addition narrow. Do not introduce a generic Minecraft action abstraction.

## DivineFavor

After tool execution policy is settled, implement DivineFavor as a separate Paper plugin.

DivineFavor owns:

- player favor balances
- transaction history
- credit and debit operations
- administrative adjustments
- configurable material purchase prices
- configurable material offering prices
- service prices
- transaction limits
- balance limits
- quote validation
- atomic offerings
- inventory rollback on failed transactions

Use the previous GodPlugin economy implementation from Git history as source material where it remains useful.

DivineFavor should expose its own GodIntegration rather than giving GodPlugin direct access to favor state.

## Waypoints

Implement Waypoints after DivineFavor.

Waypoints owns:

- named personal waypoints
- world and position persistence
- yaw and pitch
- waypoint-count limits
- safe teleport destination checks
- teleport execution
- last-death location
- death-return expiration
- safe return near death

Use the previous GodPlugin location implementation from Git history as source material where useful.

If Waypoints charges Favor, Waypoints should integrate directly with DivineFavor. GodPlugin should only route the model request to the Waypoints integration.

## Sanctuary integration

After the shared integration path is proven with real standalone plugins, add a Sanctuary GodIntegration.

Sanctuary remains authoritative for:

- anchors
- territory
- security
- sentries
- companions
- beacon progression
- altar progression
- persistence
- permissions
- Sanctuary-specific actions and state

GodPlugin should receive only the information and actions Sanctuary intentionally exposes through GodApi.

## Deferred

Do not implement these unless explicitly brought back into scope:

- DivineInterventions
- GodEvents
- DivineReputation
- duels
- generic model-controlled Bukkit action APIs
- proactive model-triggered server event systems

## Release discipline

For each phase:

1. modify the owning repository
2. add or update automated tests
3. run the full Gradle build
4. update repository documentation
5. verify CI before claiming success
6. release shared API changes before consumers depend on them

Changes currently go directly to `main`.
