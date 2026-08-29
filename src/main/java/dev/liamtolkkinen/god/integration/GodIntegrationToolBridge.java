package dev.liamtolkkinen.god.integration;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import dev.liamtolkkinen.godapi.GodTool;
import dev.liamtolkkinen.godapi.GodToolContext;
import dev.liamtolkkinen.godapi.GodToolResult;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;

/**
 * Adapts serializer-neutral GodApi tools to GodPlugin's current Gson/OpenAI
 * implementation.
 */
public final class GodIntegrationToolBridge {
    private static final Type ARGUMENT_MAP_TYPE = new TypeToken<Map<String, Object>>() { }.getType();

    private final Gson gson;

    public GodIntegrationToolBridge(Gson gson) {
        this.gson = Objects.requireNonNull(gson, "gson");
    }

    public JsonObject definition(GodTool tool) {
        Objects.requireNonNull(tool, "tool");

        JsonObject definition = new JsonObject();
        definition.addProperty("type", "function");
        definition.addProperty("name", tool.name());
        definition.addProperty("description", tool.description());
        definition.addProperty("strict", true);
        definition.add("parameters", gson.toJsonTree(tool.parameterSchema()));
        return definition;
    }

    public JsonObject execute(GodTool tool, GodToolContext context, JsonObject arguments) {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(arguments, "arguments");

        Map<String, Object> nativeArguments = gson.fromJson(arguments, ARGUMENT_MAP_TYPE);
        GodToolResult result = Objects.requireNonNull(
            tool.execute(context, nativeArguments),
            "God tool " + tool.name() + " returned null"
        );

        JsonObject output = new JsonObject();
        output.addProperty("success", result.success());
        output.addProperty("message", result.message());
        output.add("data", gson.toJsonTree(result.data()));
        return output;
    }
}
