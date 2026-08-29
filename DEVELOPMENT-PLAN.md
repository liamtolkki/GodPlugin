# GodPlugin Development Plan

This document describes the planned redesign of GodPlugin based on the currently imported implementation.

GodPlugin is changing from an AI-controlled gameplay system into a general conversation and integration layer for the Minecraft server.

The core rule is:

> GodPlugin owns conversation with the language model. Other plugins own gameplay behavior, state, documentation, and actions.

## Current imported implementation

The imported plugin already contains substantial working infrastructure:

- OpenAI Responses API integration
- asynchronous request processing
- bounded request queue
- request timeouts
- structured JSON-schema model responses
- function/tool calling
- trusted Java validation before actions execute
- interaction IDs
- JSONL audit logging
- recent chat and interaction context
- trigger-word chat handling
- configuration reload and runtime modes

The current implementation also owns gameplay systems that no longer fit the intended architecture:

- Favor economy
- material buying and selling
- reward handling
- player relationship scoring
- player-specific doctrine
- duels
- saved locations
- last-death return teleportation
- server-event commentary
- direct world manipulation
- direct player effects and damage
- mob spawning
- gamerule changes
- advancement changes
- item granting
- weather and time changes
- other generic model-controlled server actions

These systems should not remain responsibilities of GodPlugin.

Some may be removed entirely. Others may later become separate plugins that integrate with GodPlugin through the same public integration API used by Sanctuary and other supported plugins.

## Target responsibility

GodPlugin should become a lightweight language-model interface for the server.

Its responsibilities should be limited to:

1. Listening for player questions intended for God.
2. Discovering supported installed plugin integrations.
3. Collecting custom instructions supplied by those plugins.
4. Advertising tools supplied by those plugins to the model.
5. Routing model tool calls to the plugin that owns the tool.
6. Returning tool results to the model.
7. Delivering the final textual response to the player.
8. Auditing interactions and tool usage.
9. Enforcing general limits such as rate limiting, timeouts, tool-call depth, and output size.

GodPlugin should not know how another plugin stores its data or implements its gameplay systems.

## Integration model

Supported plugins should provide GodPlugin with an integration object through a public API.

The initial contract should expose at least:

- stable integration ID
- plugin display name
- plugin version
- short description
- custom instructions for discussing the plugin with players
- zero or more plugin-owned tools

Conceptually:

```java
public interface GodIntegration {
    String id();

    String pluginName();

    String pluginVersion();

    String description();

    String instructions();

    Collection<GodTool> tools();
}
```

The exact Java API may change during implementation, but the ownership boundary should remain the same.

### Stable integration ID

Each integration should have a stable machine-readable ID, for example:

```text
sanctuary
locations
economy
```

The ID must not depend on the player-facing plugin display name.

## Plugin-provided instructions

Each supported plugin should provide authoritative instructions that GodPlugin adds to the language model's current instructions.

These instructions are intended for the model, not directly for players.

They may include:

- what the plugin does
- player-facing terminology
- progression rules
- important restrictions
- how to explain mechanics
- concepts the model must not invent
- which internal implementation details should not be exposed
- when a tool should be called instead of answering from static documentation

Example Sanctuary instructions could include rules such as:

```text
Use "Anchor" when referring generically to Beacon and Conduit anchors.

A Sanctuary's territory is the union of its active Anchor boundaries.

Temporary aggression lasts 10 minutes or until the aggressive player dies.

Do not tell players that effect levels can be manually lowered.

Use Sanctuary names instead of internal IDs when possible.
```

The supported plugin remains the authoritative source for these instructions.

GodPlugin must not maintain a duplicate hardcoded copy of Sanctuary mechanics or another plugin's documentation.

## Instruction assembly

At runtime, GodPlugin should discover all registered integrations and build the model instructions from them.

Conceptually:

```text
God base instructions

## Installed supported plugins

### Sanctuary
Version: <version>
Description: <description>
<Sanctuary-provided instructions>

### Another Plugin
Version: <version>
Description: <description>
<plugin-provided instructions>
```

Installing or removing a supported plugin should therefore change God's available knowledge without requiring a GodPlugin source-code change.

GodPlugin should remain functional when zero integrations are installed.

## Tools

Plugins may expose tools for information or actions that cannot be represented accurately through static instructions alone.

A tool is owned and implemented by the plugin providing it.

Conceptually:

```java
public interface GodTool {
    String name();

    String description();

    JsonObject parameterSchema();

    JsonElement execute(Player player, JsonObject arguments) throws Exception;
}
```

The exact data types may change, but GodPlugin's role should remain routing rather than implementation.

### Tool naming

Tool names should be globally unique after registration.

One possible convention is namespacing them by integration:

```text
sanctuary_get_player_sanctuaries
sanctuary_get_details
locations_get_saved_locations
```

Alternatively, the API may expose integration ID separately from tool name and perform namespacing internally.

The implementation should reject duplicate tool names instead of silently replacing one provider with another.

## Read-only and action tools

The first integration work should prioritize read-only tools.

Examples:

```text
get_player_sanctuaries
get_sanctuary_details
get_sanctuary_progression
get_supported_plugins
```

A plugin may eventually expose actions, for example:

```text
rename_sanctuary
teleport_to_saved_location
purchase_item
```

However, the action must execute in the plugin that owns the behavior.

GodPlugin must not implement generic gameplay actions on another plugin's behalf.

For example, GodPlugin should not call Bukkit commands to simulate a Sanctuary rename. It should call a Sanctuary-owned tool that validates and performs the rename using Sanctuary's own rules.

## Tool routing

The expected call path is:

```text
Player question
    |
    v
GodPlugin
    |
    v
Language model
    |
    | tool call
    v
GodPlugin tool router
    |
    v
Owning plugin integration
    |
    | trusted result
    v
GodPlugin
    |
    v
Language model
    |
    v
Final textual answer
```

GodPlugin should record which integration and tool were invoked in the interaction audit.

## Static knowledge versus live state

Plugin instructions should contain stable documentation and rules.

Tools should be used for information that depends on current server or player state.

For example, Sanctuary instructions can explain what Anchor tiers are and how attunement works.

A Sanctuary tool can answer questions such as:

```text
Which Sanctuaries does this player own?
What tier is this specific Anchor?
Which effect level is currently unlocked?
How many sentries are registered?
What upgrade is currently available?
```

This prevents GodPlugin from reading Sanctuary's database or depending on Sanctuary internals.

## First supported integration: Sanctuary

Sanctuary should be the first real integration used to validate the API.

The first Sanctuary integration should provide authoritative instructions covering at least:

- Sanctuary overview
- Beacon and Conduit Anchors
- Anchor graph behavior
- territory and boundary behavior
- security relationships
- temporary aggression
- Beacon and Conduit effects
- attunement
- sentries
- Watcher's Eye
- companions
- Divine Altar and progression
- trust and optional hard protections

Initial read-only tools should likely include some subset of:

```text
get_player_sanctuaries
get_sanctuary_details
get_sanctuary_anchors
get_sanctuary_effects
get_sanctuary_sentries
get_sanctuary_progression
```

The exact tool list should be driven by useful player questions rather than exposing every internal object.

## Behaviors to remove from GodPlugin

The following current responsibilities should be removed from GodPlugin as part of the redesign unless explicitly retained for another reason:

### Economy and rewards

Remove God-owned:

- Favor balances
- `EconomyManager`
- `economy.json`
- material buy/sell tools
- item reward execution
- service pricing

If an economy is desired later, it should be implemented as its own plugin and expose its own God integration.

### Player relationship scoring

Remove God-owned:

- relationship scores
- relationship decay
- relationship event judgment
- player relationship files
- relationship-based material rewards

God should answer player questions based on authoritative plugin state, not maintain a global moral score for every player.

### Duels

Remove `DuelManager` and `/duel` from GodPlugin.

A duel system may become a separate plugin if it is still desired.

### Locations and death return

Remove God-owned:

- saved locations
- `/godlocation`
- last-death storage
- death-return teleport services

These may become a dedicated locations or teleport plugin if desired.

### Server-event commentary

Remove automatic model calls for generic events such as:

- administrator arrival/departure
- PvP kills
- assaults
- Totem saves
- major vanilla advancements
- significant mob kills
- moral mob kills

The redesigned God should primarily respond when a player asks it something.

A supported plugin may later provide a deliberate event or notification integration if proactive guidance is useful.

### Generic world actions

Remove the generic action system that lets the model directly request actions such as:

- `set_time`
- `set_weather`
- `drop_anvil`
- `temporary_gamemode`
- `damage`
- `teleport_to_player`
- `temporary_gamerule`
- `temporary_setblock`
- `advancement`
- `spawn_mob`
- `smite`
- direct item grants
- direct entity kills

Any future non-textual behavior must be implemented by another plugin and exposed through that plugin's integration tools.

## Behaviors to preserve and refactor

The following current implementation concepts are useful and should be retained where practical:

- OpenAI HTTP client
- API key from environment rather than repository configuration
- asynchronous request worker
- bounded queue
- request timeout
- cooldown/rate limiting
- structured response validation
- function-call continuation loop
- strict tool schemas
- interaction IDs
- audit records
- recent conversation context
- trigger-word detection
- clean main-thread handoff when Bukkit API access is required
- failure handling that does not expose internal exceptions directly to players

These should be split out of the current large `GodPlugin.java` instead of being discarded with the old gameplay systems.

## Proposed code structure

The current imported implementation is concentrated heavily in `GodPlugin.java` and uses a nested `plugin/` project directory.

The long-term project should be reorganized toward the same general layout as the other Minecraft plugins:

```text
GodPlugin/
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
├── gradle/
├── README.md
├── DEVELOPMENT-PLAN.md
└── src/
    ├── main/java/dev/liamtolkkinen/god/
    │   ├── GodPlugin.java
    │   ├── ai/
    │   ├── audit/
    │   ├── chat/
    │   └── integration/
    ├── main/resources/
    └── test/java/
```

Possible class boundaries:

```text
GodPlugin
    bootstrap and dependency wiring only

ai/OpenAiClient
    HTTP and Responses API communication

ai/ConversationService
    assembles instructions, context, tools, and model continuations

chat/GodChatListener
    detects and submits player questions

integration/GodIntegration
    public integration contract

integration/GodTool
    public tool contract

integration/GodIntegrationRegistry
    provider discovery, validation, and tool routing

audit/InteractionAuditService
    interaction and tool-call audit records
```

## Paper integration discovery

The preferred initial discovery mechanism is Paper/Bukkit `ServicesManager`.

A supported plugin would register its `GodIntegration` service during startup.

GodPlugin would discover registered providers without directly depending on their implementation classes.

The implementation must handle plugin load order safely. Options include:

- soft dependencies where appropriate
- plugin enable/disable events
- periodic or explicit registry refresh
- provider registration callbacks

The final design should not require GodPlugin to list every supported plugin as a hard dependency.

## API ownership

The integration API may initially live inside GodPlugin if that is simplest, but supported plugins need access to the interfaces at compile time without bundling a second runtime copy.

Before the first external integration is finalized, decide whether to:

1. publish a small `GodApi` artifact, or
2. expose API classes from the GodPlugin JAR and depend on it as `compileOnly`.

A separate small API artifact is likely cleaner if multiple plugins are expected to integrate.

The API should remain narrow and stable.

## Base model instructions

GodPlugin should keep its own base instructions small.

Conceptually:

```text
You are God, an in-game assistant for this Minecraft server.

Answer player questions about Minecraft and supported installed plugins.

For supported plugins, their provided instructions and tool results are authoritative.
Do not invent plugin mechanics that are not present in those instructions or results.
Use available tools when current player or server state is required.
```

Plugin-specific mechanics should not be copied into this base prompt.

## Chat interaction

The current natural chat trigger is worth preserving.

For example:

```text
God, how do I upgrade my Sanctuary?
```

A command fallback may also be useful:

```text
/god <question>
```

Administrative commands should be reduced to configuration and diagnostics relevant to the conversation/integration system.

Possible future administration commands:

```text
/god status
/god reload
/god integrations
/god tools
```

`/god status` should be able to report discovered supported integrations and their versions.

## Audit expectations

Audit logs should remain part of GodPlugin.

An interaction record should include enough information to diagnose model behavior without requiring another plugin's private state.

Useful fields include:

- interaction ID
- timestamp
- player UUID/name
- original player question
- installed integrations and versions used for the request
- tool calls requested
- integration that handled each tool call
- success/failure of each call
- final response
- model response ID
- token usage when available
- plugin version

Sensitive API keys must never be written to logs.

## Failure behavior

If an integration fails while handling a tool call:

- GodPlugin should not invent a replacement result.
- The failure should be audited.
- The model should receive a bounded error result or the interaction should fail safely.
- Other unrelated integrations should remain usable.

If an integration disappears while the server is running, its tools and instructions should be removed from subsequent requests.

## Versioning

GodPlugin should include the version of every active integration in the model context and audit data.

This makes responses traceable to the exact plugin implementation that supplied the documentation or tool result.

## Planned implementation phases

### Phase 1: project cleanup

- Preserve the imported source as the implementation reference.
- Move the Paper project out of the nested `plugin/` directory.
- Adopt Java 25, Paper 26.1.2, Gradle 9.7.1, and the same general build conventions used by Sanctuary.
- Add normal build/test CI.
- Split the large `GodPlugin.java` into focused services without intentionally changing conversation behavior yet.

### Phase 2: integration API

- Add `GodIntegration`.
- Add `GodTool`.
- Add integration registry/discovery.
- Add duplicate-ID and duplicate-tool validation.
- Add `/god integrations` diagnostics.
- Add tests for registration and tool routing.

### Phase 3: read-only conversation model

- Remove the generic world-action output schema.
- Remove God-owned gameplay tool execution.
- Keep only textual replies plus plugin-provided tool calls.
- Build model instructions from active integrations.
- Preserve audit, timeout, rate-limit, and tool-continuation behavior.

### Phase 4: remove old God gameplay systems

- Remove Favor/economy.
- Remove relationship scoring.
- Remove duels.
- Remove saved locations and death return.
- Remove generic event commentary.
- Remove old policy files that are no longer used.
- Reduce configuration to God conversation/integration settings.

### Phase 5: Sanctuary integration

- Add the first real `GodIntegration` implementation to Sanctuary.
- Provide Sanctuary's authoritative player-facing instructions.
- Add a small set of useful read-only Sanctuary tools.
- Verify common player questions are answered correctly from Sanctuary data.

### Phase 6: optional action integrations

Only after the read-only system is stable, allow external plugins to expose narrowly scoped action tools when useful.

GodPlugin itself should remain free of generic gameplay action implementations.

## Non-goals

The redesigned GodPlugin is not intended to:

- become a replacement for plugin APIs
- read other plugins' databases directly
- duplicate other plugins' documentation manually
- implement a global economy
- implement a global permissions system
- own teleportation systems
- own combat systems
- own progression systems
- act as an unrestricted server administrator
- let the model execute arbitrary Bukkit or console commands

## Architectural summary

The intended boundary is:

```text
Supported plugin
    owns gameplay
    owns state
    owns documentation
    owns actions
        |
        | GodIntegration
        v
GodPlugin
    gathers instructions
    presents available tools
    routes tool calls
    talks to the language model
    audits interactions
        |
        v
Player
```

GodPlugin should know that Sanctuary is installed and know what Sanctuary chooses to tell it.

It should not know how Sanctuary works internally.

That separation is the central design goal for the redesign.
