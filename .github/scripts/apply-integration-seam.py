from pathlib import Path

# One-time source migration used to wire the released GodApi contract into the legacy class.
path = Path("src/main/java/dev/liamtolkkinen/god/GodPlugin.java")
text = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Expected exactly one match, found {count}: {old[:120]!r}")
    text = text.replace(old, new, 1)


replace_once(
    "import com.google.gson.JsonParser;\nimport io.papermc.paper.event.player.AsyncChatEvent;",
    "import com.google.gson.JsonParser;\n"
    "import dev.liamtolkkinen.god.integration.GodIntegrationRegistry;\n"
    "import dev.liamtolkkinen.god.integration.GodIntegrationToolBridge;\n"
    "import dev.liamtolkkinen.godapi.GodTool;\n"
    "import dev.liamtolkkinen.godapi.GodToolContext;\n"
    "import io.papermc.paper.event.player.AsyncChatEvent;",
)

replace_once(
    "    private ExecutorService executor;\n    private HttpClient httpClient;\n    private Path godDirectory;",
    "    private ExecutorService executor;\n"
    "    private HttpClient httpClient;\n"
    "    private GodIntegrationRegistry integrations;\n"
    "    private GodIntegrationToolBridge integrationToolBridge;\n"
    "    private Path godDirectory;",
)

replace_once(
    "        httpClient = HttpClient.newBuilder()\n"
    "            .connectTimeout(Duration.ofSeconds(timeoutSeconds))\n"
    "            .build();\n\n"
    "        getServer().getPluginManager().registerEvents(this, this);",
    "        httpClient = HttpClient.newBuilder()\n"
    "            .connectTimeout(Duration.ofSeconds(timeoutSeconds))\n"
    "            .build();\n\n"
    "        integrations = new GodIntegrationRegistry(getServer());\n"
    "        integrationToolBridge = new GodIntegrationToolBridge(GSON);\n"
    "        GodIntegrationRegistry.Snapshot startupIntegrations = integrations.snapshot();\n"
    "        getLogger().info(\"Discovered \" + startupIntegrations.integrations().size() +\n"
    "            \" God integration(s) exposing \" + startupIntegrations.tools().size() + \" tool(s).\");\n\n"
    "        getServer().getPluginManager().registerEvents(this, this);",
)

replace_once(
    "        JsonObject config = JsonParser.parseString(Files.readString(godDirectory.resolve(\"config.json\"))).getAsJsonObject();\n"
    "        JsonObject player = new JsonObject();",
    "        JsonObject config = JsonParser.parseString(Files.readString(godDirectory.resolve(\"config.json\"))).getAsJsonObject();\n"
    "        GodIntegrationRegistry.Snapshot integrationSnapshot = integrations.snapshot();\n"
    "        JsonObject player = new JsonObject();",
)

replace_once(
    "        instructions += \" store_location saves the initiating player's current location under resource; delete_saved_location deletes an exact stored name; teleport_saved_location selects one exact stored name after consulting get_saved_locations; return_to_last_death is allowed only after consulting get_last_death and when available. Trusted code charges configured service prices.\";\n\n"
    "        JsonObject schema = JsonParser.parseString(\"\"\"",
    "        instructions += \" store_location saves the initiating player's current location under resource; delete_saved_location deletes an exact stored name; teleport_saved_location selects one exact stored name after consulting get_saved_locations; return_to_last_death is allowed only after consulting get_last_death and when available. Trusted code charges configured service prices.\";\n"
    "        String integrationInstructions = integrationSnapshot.combinedInstructions();\n"
    "        if (!integrationInstructions.isBlank()) {\n"
    "            instructions += \"\\n\\n## Plugin integrations\\n\\n\" + integrationInstructions;\n"
    "        }\n\n"
    "        JsonObject schema = JsonParser.parseString(\"\"\"",
)

text = text.replace("body.add(\"tools\", buildReadOnlyTools());", "body.add(\"tools\", buildReadOnlyTools(integrationSnapshot));")
text = text.replace("continuation.add(\"tools\", buildReadOnlyTools());", "continuation.add(\"tools\", buildReadOnlyTools(integrationSnapshot));")

replace_once(
    "            JsonArray toolOutputs = executeRequestedTools(responseJson, playerUuid, policy, snapshot, approvedQuotes);",
    "            JsonArray toolOutputs = executeRequestedTools(responseJson, interactionId, playerUuid, playerName, policy, snapshot, approvedQuotes, integrationSnapshot);",
)
replace_once(
    "            if (toolRound == 2 && !executeRequestedTools(responseJson, playerUuid, policy, snapshot, new java.util.HashMap<>()).isEmpty()) {",
    "            if (toolRound == 2 && !executeRequestedTools(responseJson, interactionId, playerUuid, playerName, policy, snapshot, new java.util.HashMap<>(), integrationSnapshot).isEmpty()) {",
)

replace_once(
    "    private JsonArray buildReadOnlyTools() {\n        return JsonParser.parseString(\"\"\"",
    "    private JsonArray buildReadOnlyTools(GodIntegrationRegistry.Snapshot integrationSnapshot) {\n        JsonArray tools = JsonParser.parseString(\"\"\"",
)

replace_once(
    "            ]\n            \"\"\").getAsJsonArray();\n    }\n\n    private JsonArray executeRequestedTools(JsonObject response, UUID playerUuid, ResolvedPolicy policy,\n        ServerSnapshot snapshot, Map<String, MaterialQuote> approvedQuotes) throws IOException {",
    "            ]\n            \"\"\").getAsJsonArray();\n"
    "        for (GodTool tool : integrationSnapshot.toolList()) {\n"
    "            tools.add(integrationToolBridge.definition(tool));\n"
    "        }\n"
    "        return tools;\n"
    "    }\n\n"
    "    private JsonArray executeRequestedTools(JsonObject response, String interactionId, UUID playerUuid, String playerName,\n"
    "        ResolvedPolicy policy, ServerSnapshot snapshot, Map<String, MaterialQuote> approvedQuotes,\n"
    "        GodIntegrationRegistry.Snapshot integrationSnapshot) throws IOException {",
)

replace_once(
    "            } else {\n                throw new IllegalArgumentException(\"Unknown model information tool: \" + name);\n            }\n            JsonObject output = new JsonObject();",
    "            } else {\n"
    "                GodTool integrationTool = integrationSnapshot.tools().get(name);\n"
    "                if (integrationTool == null) {\n"
    "                    throw new IllegalArgumentException(\"Unknown model information tool: \" + name);\n"
    "                }\n"
    "                result = integrationToolBridge.execute(\n"
    "                    integrationTool,\n"
    "                    new GodToolContext(playerUuid, playerName, interactionId),\n"
    "                    arguments\n"
    "                );\n"
    "            }\n"
    "            JsonObject output = new JsonObject();",
)

path.write_text(text, encoding="utf-8")
