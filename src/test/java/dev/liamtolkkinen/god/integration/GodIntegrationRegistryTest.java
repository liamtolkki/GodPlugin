package dev.liamtolkkinen.god.integration;

import dev.liamtolkkinen.godapi.GodIntegration;
import dev.liamtolkkinen.godapi.GodTool;
import dev.liamtolkkinen.godapi.GodToolContext;
import dev.liamtolkkinen.godapi.GodToolResult;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class GodIntegrationRegistryTest {
    @Test
    void worksWithNoIntegrations() {
        GodIntegrationRegistry.Snapshot snapshot = registryWith(List.of()).snapshot();

        assertTrue(snapshot.integrations().isEmpty());
        assertTrue(snapshot.tools().isEmpty());
        assertEquals("", snapshot.combinedInstructions());
    }

    @Test
    void discoversEnabledIntegrationAndTool() {
        GodTool tool = new TestTool("test_get_status");
        GodIntegration integration = new TestIntegration(
            "test",
            "TestPlugin",
            "1.0.0",
            "Test integration",
            "Use this integration to inspect test status.",
            List.of(tool)
        );

        GodIntegrationRegistry.Snapshot snapshot = registryWith(
            List.of(registration(integration, true))
        ).snapshot();

        assertEquals(integration, snapshot.integrations().get("test"));
        assertEquals(tool, snapshot.tools().get("test_get_status"));
        assertTrue(snapshot.combinedInstructions().contains("[TestPlugin]"));
        assertTrue(snapshot.combinedInstructions().contains("inspect test status"));
    }

    @Test
    void ignoresDisabledProviderPlugin() {
        GodIntegration integration = new TestIntegration(
            "test",
            "TestPlugin",
            "1.0.0",
            "Test integration",
            "Test instructions",
            List.of(new TestTool("test_get_status"))
        );

        GodIntegrationRegistry.Snapshot snapshot = registryWith(
            List.of(registration(integration, false))
        ).snapshot();

        assertTrue(snapshot.integrations().isEmpty());
        assertTrue(snapshot.tools().isEmpty());
    }

    @Test
    void rejectsDuplicateIntegrationIds() {
        GodIntegration first = new TestIntegration(
            "test",
            "FirstPlugin",
            "1.0.0",
            "First",
            "First instructions",
            List.of()
        );
        GodIntegration second = new TestIntegration(
            "test",
            "SecondPlugin",
            "1.0.0",
            "Second",
            "Second instructions",
            List.of()
        );

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> registryWith(List.of(
                registration(first, true),
                registration(second, true)
            )).snapshot()
        );

        assertTrue(exception.getMessage().contains("Duplicate God integration id 'test'"));
    }

    @Test
    void rejectsDuplicateToolNamesAcrossIntegrations() {
        GodIntegration first = new TestIntegration(
            "first",
            "FirstPlugin",
            "1.0.0",
            "First",
            "First instructions",
            List.of(new TestTool("shared_tool"))
        );
        GodIntegration second = new TestIntegration(
            "second",
            "SecondPlugin",
            "1.0.0",
            "Second",
            "Second instructions",
            List.of(new TestTool("shared_tool"))
        );

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> registryWith(List.of(
                registration(first, true),
                registration(second, true)
            )).snapshot()
        );

        assertTrue(exception.getMessage().contains("Duplicate God tool name 'shared_tool'"));
    }

    private static GodIntegrationRegistry registryWith(
        Collection<RegisteredServiceProvider<GodIntegration>> registrations
    ) {
        Server server = mock(Server.class);
        ServicesManager servicesManager = mock(ServicesManager.class);
        when(server.getServicesManager()).thenReturn(servicesManager);
        when(servicesManager.getRegistrations(GodIntegration.class)).thenReturn(registrations);
        return new GodIntegrationRegistry(server);
    }

    private static RegisteredServiceProvider<GodIntegration> registration(
        GodIntegration integration,
        boolean enabled
    ) {
        @SuppressWarnings("unchecked")
        RegisteredServiceProvider<GodIntegration> registration = mock(RegisteredServiceProvider.class);
        Plugin plugin = mock(Plugin.class);
        when(plugin.isEnabled()).thenReturn(enabled);
        when(registration.getPlugin()).thenReturn(plugin);
        when(registration.getProvider()).thenReturn(integration);
        return registration;
    }

    private record TestIntegration(
        String id,
        String pluginName,
        String pluginVersion,
        String description,
        String instructions,
        Collection<GodTool> tools
    ) implements GodIntegration {
    }

    private record TestTool(String name) implements GodTool {
        @Override
        public String description() {
            return "Returns deterministic test status.";
        }

        @Override
        public Map<String, Object> parameterSchema() {
            return Map.of(
                "type", "object",
                "properties", Map.of(),
                "additionalProperties", false
            );
        }

        @Override
        public GodToolResult execute(GodToolContext context, Map<String, Object> arguments) {
            return GodToolResult.success("Test status is healthy.", Map.of("status", "healthy"));
        }
    }
}
