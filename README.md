# GodPlugin

GodPlugin is planned as the player-facing wiki and guide layer for supported Minecraft plugins on the server.

Rather than owning another plugin's gameplay state, GodPlugin should explain that plugin's systems, progression, items, mechanics, and current player options in a consistent in-game interface.

## Role

GodPlugin should answer questions such as:

```text
How do Sanctuary anchors work?
What does an Attunement Relic do?
How do I obtain a companion?
Why is this sentry attacking me?
What can I craft at the Divine Altar?
How do another supported plugin's mechanics work?
```

The goal is to make supported plugins discoverable without requiring players to leave the game and search external documentation.

GodPlugin should behave like a contextual wiki, not as the authority for the systems it documents.

## Integration model

Each supported plugin remains authoritative for its own:

- gameplay state
- persistence
- permissions
- progression
- items
- commands
- rules and mechanics

GodPlugin consumes documented information through a supported integration boundary and presents it to players.

A supported plugin should be able to expose guide information without giving GodPlugin direct access to its private database or internal repositories.

Conceptually:

```text
Supported plugin
    owns gameplay and state
        |
        v
public guide/wiki integration
        |
        v
GodPlugin
    organizes and presents documentation
        |
        v
player
```

## Supported-plugin guide content

A plugin integration may provide content such as:

- plugin overview
- getting-started guide
- progression paths
- item descriptions
- recipes and acquisition methods
- mechanics and rules
- UI/menu explanations
- security or permission concepts
- troubleshooting/help topics
- links between related topics
- current version or compatibility information

The exact API and content model still need to be designed.

## Contextual help

A later goal is for GodPlugin to use available player context to make the wiki easier to navigate.

Examples:

- suggest Sanctuary documentation while the player is inside a Sanctuary
- explain an item the player is holding
- explain why an action was blocked by a supported plugin
- point to the next documented progression step

Contextual help should still rely on the supported plugin's public integration surface. GodPlugin should not infer or mutate private plugin state directly.

## Language-model use

The original GodPlugin concept used a language model to respond dynamically to server events and player requests. Language-model assistance can still be useful for natural-language questions, but it should sit on top of authoritative guide content supplied by supported plugins.

The model should help players find and understand documented information. It should not invent undocumented recipes, progression requirements, permissions, or gameplay rules.

A useful target architecture is:

```text
player question
      |
      v
GodPlugin
      |
      +--> supported-plugin guide data
      |
      +--> optional language-model interpretation
      |
      v
answer grounded in supported documentation
```

## Sanctuary

Sanctuary is expected to be one of the first supported plugins.

Sanctuary owns its anchors, territory, security, sentries, companions, altar progression, effects, persistence, and player state. GodPlugin should expose those systems as navigable player documentation without becoming a Sanctuary runtime dependency.

The Sanctuary repository remains the source of truth for Sanctuary behavior.

## Project status

GodPlugin currently contains only the initial project documentation. The wiki/guide architecture described here is the target for the next development phase.

Before implementing it, define:

1. the supported-plugin registration/API contract
2. the guide topic/content model
3. how plugins publish recipes, items, progression, and help topics
4. the in-game navigation/UI
5. how optional natural-language questions are grounded against registered guide content
6. versioning and compatibility behavior when supported plugins change
