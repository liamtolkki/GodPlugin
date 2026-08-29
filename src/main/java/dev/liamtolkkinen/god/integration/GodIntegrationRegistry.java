package dev.liamtolkkinen.god.integration;

import dev.liamtolkkinen.godapi.GodIntegration;
import dev.liamtolkkinen.godapi.GodTool;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Discovers and validates active God integrations registered through Bukkit's
 * ServicesManager.
 */
public final class GodIntegrationRegistry {
    private static final Pattern INTEGRATION_ID_PATTERN = Pattern.compile("[a-z][a-z0-9_-]{0,63}");
    private static final Pattern TOOL_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final Server server;

    public GodIntegrationRegistry(Server server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    /**
     * Returns a validated snapshot of integrations that are currently registered
     * by enabled plugins.
     *
     * <p>A fresh snapshot is built on each call so disabled or unregistered
     * plugins disappear from subsequent requests automatically.</p>
     */
    public Snapshot snapshot() {
        Collection<RegisteredServiceProvider<GodIntegration>> registrations =
            server.getServicesManager().getRegistrations(GodIntegration.class);

        LinkedHashMap<String, GodIntegration> integrations = new LinkedHashMap<>();
        LinkedHashMap<String, GodTool> tools = new LinkedHashMap<>();

        for (RegisteredServiceProvider<GodIntegration> registration : registrations) {
            Plugin providerPlugin = registration.getPlugin();
            if (providerPlugin == null || !providerPlugin.isEnabled()) {
                continue;
            }

            GodIntegration integration = Objects.requireNonNull(
                registration.getProvider(),
                "GodIntegration service provider is null"
            );
            validateIntegration(integration);

            GodIntegration duplicateIntegration = integrations.putIfAbsent(integration.id(), integration);
            if (duplicateIntegration != null) {
                throw new IllegalStateException(
                    "Duplicate God integration id '" + integration.id() + "' from " +
                        duplicateIntegration.pluginName() + " and " + integration.pluginName() + "."
                );
            }

            for (GodTool tool : integration.tools()) {
                validateTool(integration, tool);
                GodTool duplicateTool = tools.putIfAbsent(tool.name(), tool);
                if (duplicateTool != null) {
                    throw new IllegalStateException(
                        "Duplicate God tool name '" + tool.name() + "'. Tool names must be unique across integrations."
                    );
                }
            }
        }

        return new Snapshot(integrations, tools);
    }

    private static void validateIntegration(GodIntegration integration) {
        requireText(integration.id(), "integration id");
        if (!INTEGRATION_ID_PATTERN.matcher(integration.id()).matches()) {
            throw new IllegalStateException(
                "Invalid God integration id '" + integration.id() +
                    "'. Expected lowercase letters, digits, underscores, or hyphens, beginning with a letter."
            );
        }

        requireText(integration.pluginName(), "plugin name for integration " + integration.id());
        requireText(integration.pluginVersion(), "plugin version for integration " + integration.id());
        requireText(integration.description(), "description for integration " + integration.id());
        Objects.requireNonNull(integration.instructions(), "instructions for integration " + integration.id());
        Objects.requireNonNull(integration.tools(), "tools for integration " + integration.id());
    }

    private static void validateTool(GodIntegration integration, GodTool tool) {
        Objects.requireNonNull(tool, "integration " + integration.id() + " contains a null tool");
        requireText(tool.name(), "tool name for integration " + integration.id());
        if (!TOOL_NAME_PATTERN.matcher(tool.name()).matches()) {
            throw new IllegalStateException(
                "Invalid God tool name '" + tool.name() + "' from integration '" + integration.id() + "'."
            );
        }
        requireText(tool.description(), "description for tool " + tool.name());
        Objects.requireNonNull(tool.parameterSchema(), "parameter schema for tool " + tool.name());
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("God integration " + field + " must not be blank.");
        }
    }

    public record Snapshot(
        Map<String, GodIntegration> integrations,
        Map<String, GodTool> tools
    ) {
        public Snapshot {
            integrations = Collections.unmodifiableMap(new LinkedHashMap<>(integrations));
            tools = Collections.unmodifiableMap(new LinkedHashMap<>(tools));
        }

        public List<GodIntegration> integrationList() {
            return List.copyOf(integrations.values());
        }

        public List<GodTool> toolList() {
            return List.copyOf(tools.values());
        }

        public String combinedInstructions() {
            List<String> sections = new ArrayList<>();
            for (GodIntegration integration : integrations.values()) {
                String instructions = integration.instructions().trim();
                if (!instructions.isEmpty()) {
                    sections.add("[" + integration.pluginName() + "]\n" + instructions);
                }
            }
            return String.join("\n\n", sections);
        }
    }
}
