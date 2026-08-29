# GodPlugin Integrations

GodPlugin discovers supported gameplay plugins through `GodApi` and Bukkit's `ServicesManager`.

GodPlugin owns the language-model conversation. Integrating plugins own their gameplay state, instructions, validation, and tool execution.

## Dependency model

GodPlugin bundles the released GodApi contract without relocation.

An integrating Paper plugin should:

1. Compile against the same compatible GodApi release.
2. Avoid shading a private copy of GodApi into its plugin JAR.
3. Declare GodPlugin as a soft dependency when the integration is optional.
4. Register its `GodIntegration` only when GodPlugin is available.
5. Unregister its services when the plugin disables.

This keeps the `GodIntegration` interface class shared through the plugin dependency classloader instead of creating separate copies in different plugin classloaders.

## Registration

A plugin can register one integration through Bukkit's `ServicesManager`:

```java
GodIntegration integration = new MyGodIntegration();
getServer().getServicesManager().register(
    GodIntegration.class,
    integration,
    this,
    ServicePriority.Normal
);
```

On disable:

```java
getServer().getServicesManager().unregisterAll(this);
```

GodPlugin builds a new integration snapshot for each model interaction. Disabled or unregistered plugins therefore disappear from later requests automatically.

## Integration requirements

An integration supplies:

- a stable lowercase integration ID
- plugin name and version
- a short description
- authoritative model instructions
- zero or more tools

Integration IDs may contain lowercase letters, digits, underscores, and hyphens, and must begin with a letter.

Tool names may contain letters, digits, underscores, and hyphens. Tool names must be globally unique across every active integration because the model sees one combined tool namespace.

Duplicate integration IDs or duplicate tool names are rejected rather than silently choosing one provider.

## Tool values

GodApi remains independent of Gson and OpenAI. Tool parameter schemas, arguments, and result data use Java-native JSON-compatible values:

- strings
- numbers
- booleans
- null
- maps with string keys
- collections containing supported values

GodPlugin converts those values to and from its current model-provider representation internally.

## Example read-only tool

```java
public final class StatusTool implements GodTool {
    @Override
    public String name() {
        return "example_get_status";
    }

    @Override
    public String description() {
        return "Returns the current status owned by ExamplePlugin.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(),
            "required", List.of(),
            "additionalProperties", false
        );
    }

    @Override
    public GodToolResult execute(
        GodToolContext context,
        Map<String, Object> arguments
    ) {
        return GodToolResult.success(
            "ExamplePlugin is healthy.",
            Map.of("status", "healthy")
        );
    }
}
```

The integration is responsible for resolving Bukkit or Paper objects from `GodToolContext.playerId()` when gameplay state is needed. GodApi intentionally does not expose Bukkit types.
