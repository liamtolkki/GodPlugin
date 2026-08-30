# GodPlugin

GodPlugin is the player-facing language-model conversation bridge for the Minecraft server.

GodPlugin owns conversation with the model. Supported plugins own their own gameplay behavior, state, persistence, documentation, validation, and actions.

## Architecture

GodPlugin uses [GodApi](https://github.com/liamtolkki/GodApi) as the shared integration contract.

A supported plugin may register a `GodIntegration` through Bukkit's `ServicesManager`. The integration provides:

- a stable integration ID
- plugin metadata
- model-facing instructions
- zero or more model-callable tools

GodPlugin discovers registered integrations for each interaction, appends their instructions to the model request, advertises their tools, routes requested tool calls back to the owning plugin, and returns tool results to the model through the OpenAI Responses API continuation path.

GodPlugin does not read another plugin's private database or depend on another plugin's internal implementation classes.

Conceptually:

```text
player
  |
  v
GodPlugin
  |
  +--> OpenAI Responses API
  |
  +--> GodIntegration registry
          |
          +--> Sanctuary
          +--> DivineFavor
          +--> Waypoints
          +--> other supported plugins
```

## Current responsibilities

GodPlugin currently owns:

- trigger-word chat handling
- OpenAI Responses API requests
- bounded asynchronous request processing
- cooldowns and queue limits
- recent public-chat context
- recent per-player interaction context
- integration discovery and validation
- integration instruction assembly
- tool advertisement and routing
- tool-result continuation
- interaction auditing
- integration diagnostics

GodPlugin intentionally does not own generic Minecraft gameplay actions, favor balances, saved locations, death return, duels, relationship scoring, or automatic server-event commentary.

## Requirements

- Java 25
- Paper 26.1.2
- Gradle 9.7.1
- `OPENAI_API_KEY` available to the Minecraft server process
- GodApi 0.1.0, bundled by the GodPlugin build

Never commit an API key.

## Build

Use the Gradle wrapper from the repository root:

```text
./gradlew clean build --no-daemon
```

The deployable shaded plugin JAR is produced under `build/libs/`.

The build downloads the pinned GodApi 0.1.0 release JAR and includes the shared API classes in GodPlugin. Integrating plugins should compile against the same GodApi version but should not bundle a separate GodApi copy when registering through GodPlugin's service boundary.

## Configuration

Paper configuration is in `src/main/resources/config.yml`.

Important settings:

- `god-directory`: directory containing `config.json`, `doctrine.md`, and runtime logs
- `trigger-word`: standalone chat word that invokes God
- `request-timeout-seconds`: API request timeout
- `cooldown-seconds`: per-player trigger cooldown
- `maximum-queued-requests`: bounded request queue size
- `public-replies`: broadcast replies when true, otherwise reply only to the initiating player

`config.json` contains model-provider settings and the persisted God mode.

`doctrine.md` contains the base model instructions. Integration-specific instructions belong to the plugin that owns that integration.

## Commands

Administrators with `god.admin` may use:

```text
/god on
/god off
/god listen
/god status
/god reload
/god integrations
```

`/god integrations` shows the currently discovered integrations and their exposed tool counts.

## Integration discovery

GodPlugin builds a fresh integration snapshot for each interaction. This means disabled or unregistered plugins disappear from later requests without GodPlugin retaining stale integration objects.

The registry rejects:

- invalid integration IDs
- invalid tool names
- duplicate integration IDs
- duplicate tool names across integrations
- null or malformed integration metadata

GodPlugin must continue to operate with zero registered integrations.

See [`docs/INTEGRATIONS.md`](docs/INTEGRATIONS.md) for provider-side integration details.

## Tool execution

GodApi uses Java-native JSON-compatible values and contains no Gson or OpenAI types.

GodPlugin converts the integration's parameter schema into the OpenAI function-tool definition. When the model requests a tool call, GodPlugin converts the arguments into the GodApi representation and calls the owning `GodTool` with a `GodToolContext` containing:

- player UUID
- player-name snapshot
- interaction ID

The owning plugin validates and executes its own operation and returns a `GodToolResult`.

GodPlugin then submits that result as a `function_call_output` continuation to the model.

## Auditing

Interactions are appended to:

```text
logs/interactions.jsonl
```

Each record contains the interaction ID, player identity, input, outcome, model response ID, error information when applicable, usage information, tool-call count, plugin version, and God mode.

Configuration changes made through `/god` are written to:

```text
logs/configuration.jsonl
```

## Planned plugin ownership

The intended plugin boundaries are:

- GodPlugin: model conversation and integration routing
- GodApi: shared integration contracts
- Sanctuary: Sanctuary gameplay and progression
- DivineFavor: favor balances, offerings, shop behavior, and transactions
- Waypoints: saved locations, teleportation, and death return

GodPlugin should remain usable without any of those gameplay plugins installed.
