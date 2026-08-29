package dev.liamtolkkinen.god.integration;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.liamtolkkinen.godapi.GodTool;
import dev.liamtolkkinen.godapi.GodToolContext;
import dev.liamtolkkinen.godapi.GodToolResult;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GodIntegrationToolBridgeTest {
    @Test
    void advertisesSerializerNeutralToolAsOpenAiFunction() {
        GodIntegrationToolBridge bridge = new GodIntegrationToolBridge(new Gson());
        GodTool tool = new StatusTool();

        JsonObject definition = bridge.definition(tool);

        assertEquals("function", definition.get("type").getAsString());
        assertEquals("test_get_status", definition.get("name").getAsString());
        assertTrue(definition.get("strict").getAsBoolean());
        assertEquals("object", definition.getAsJsonObject("parameters").get("type").getAsString());
        assertFalse(definition.getAsJsonObject("parameters").get("additionalProperties").getAsBoolean());
    }

    @Test
    void routesArgumentsAndReturnsStructuredToolResult() {
        GodIntegrationToolBridge bridge = new GodIntegrationToolBridge(new Gson());
        GodTool tool = new StatusTool();
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        GodToolContext context = new GodToolContext(playerId, "Tester", "interaction-1");
        JsonObject arguments = new JsonObject();
        arguments.addProperty("detail", "short");

        JsonObject output = bridge.execute(tool, context, arguments);

        assertTrue(output.get("success").getAsBoolean());
        assertEquals("Test status is healthy.", output.get("message").getAsString());
        assertEquals("healthy", output.getAsJsonObject("data").get("status").getAsString());
        assertEquals("short", output.getAsJsonObject("data").get("detail").getAsString());
        assertEquals(playerId.toString(), output.getAsJsonObject("data").get("player_id").getAsString());
        assertEquals("interaction-1", output.getAsJsonObject("data").get("interaction_id").getAsString());
    }

    private static final class StatusTool implements GodTool {
        @Override
        public String name() {
            return "test_get_status";
        }

        @Override
        public String description() {
            return "Returns deterministic test status.";
        }

        @Override
        public Map<String, Object> parameterSchema() {
            return Map.of(
                "type", "object",
                "properties", Map.of(
                    "detail", Map.of("type", "string")
                ),
                "required", java.util.List.of("detail"),
                "additionalProperties", false
            );
        }

        @Override
        public GodToolResult execute(GodToolContext context, Map<String, Object> arguments) {
            return GodToolResult.success(
                "Test status is healthy.",
                Map.of(
                    "status", "healthy",
                    "detail", arguments.get("detail"),
                    "player_id", context.playerId().toString(),
                    "interaction_id", context.interactionId()
                )
            );
        }
    }
}
