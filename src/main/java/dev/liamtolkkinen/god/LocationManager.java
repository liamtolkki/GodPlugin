package dev.liamtolkkinen.god;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

final class LocationManager {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private final Path godDirectory;

    LocationManager(Path godDirectory) {
        this.godDirectory = godDirectory;
    }

    synchronized void save(Player player, String rawName) throws IOException {
        String name = normalizeName(rawName);
        JsonObject document = readLocations(player.getUniqueId());
        JsonObject locations = document.getAsJsonObject("locations");
        if (!locations.has(name) && locations.size() >= 5) {
            throw new IllegalArgumentException("Five locations are already stored: " + String.join(", ", locations.keySet()));
        }
        locations.add(name, serializeLocation(player.getLocation()));
        write(player.getUniqueId(), "locations.json", document);
    }

    synchronized void delete(UUID playerUuid, String rawName) throws IOException {
        String name = normalizeName(rawName);
        JsonObject document = readLocations(playerUuid);
        if (document.getAsJsonObject("locations").remove(name) == null) throw new IllegalArgumentException("Unknown saved location: " + name);
        write(playerUuid, "locations.json", document);
    }

    synchronized JsonObject list(UUID playerUuid) throws IOException {
        return readLocations(playerUuid).getAsJsonObject("locations").deepCopy();
    }

    synchronized Location saved(UUID playerUuid, String rawName) throws IOException {
        String name = normalizeName(rawName);
        JsonObject locations = readLocations(playerUuid).getAsJsonObject("locations");
        if (!locations.has(name)) throw new IllegalArgumentException("Unknown saved location: " + name);
        return safeLocation(deserializeLocation(locations.getAsJsonObject(name)));
    }

    synchronized String savedWorld(UUID playerUuid, String rawName) throws IOException {
        String name = normalizeName(rawName);
        JsonObject locations = readLocations(playerUuid).getAsJsonObject("locations");
        if (!locations.has(name)) throw new IllegalArgumentException("Unknown saved location: " + name);
        return locations.getAsJsonObject(name).get("world").getAsString();
    }

    synchronized void recordDeath(Player player) throws IOException {
        JsonObject document = serializeLocation(player.getLocation());
        document.addProperty("timestamp", Instant.now().toString());
        write(player.getUniqueId(), "last-death.json", document);
    }

    synchronized JsonObject lastDeathSummary(UUID playerUuid, Duration maximumAge) throws IOException {
        Path path = playerPath(playerUuid, "last-death.json");
        JsonObject result = new JsonObject();
        if (!Files.isRegularFile(path)) {
            result.addProperty("available", false);
            return result;
        }
        JsonObject document = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        Instant timestamp = Instant.parse(document.get("timestamp").getAsString());
        boolean available = !timestamp.isBefore(Instant.now().minus(maximumAge));
        result.addProperty("available", available);
        if (available) {
            result.addProperty("world", document.get("world").getAsString());
            result.addProperty("x", document.get("x").getAsDouble());
            result.addProperty("y", document.get("y").getAsDouble());
            result.addProperty("z", document.get("z").getAsDouble());
            result.addProperty("timestamp", timestamp.toString());
        }
        return result;
    }

    synchronized Location lastDeath(UUID playerUuid, Duration maximumAge) throws IOException {
        JsonObject summary = lastDeathSummary(playerUuid, maximumAge);
        if (!summary.get("available").getAsBoolean()) throw new IllegalArgumentException("No unexpired death location is available.");
        JsonObject document = JsonParser.parseString(Files.readString(playerPath(playerUuid, "last-death.json"))).getAsJsonObject();
        return safeLocation(deserializeLocation(document));
    }

    private JsonObject readLocations(UUID playerUuid) throws IOException {
        Path path = playerPath(playerUuid, "locations.json");
        if (!Files.isRegularFile(path)) {
            JsonObject document = new JsonObject();
            document.add("locations", new JsonObject());
            return document;
        }
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private JsonObject serializeLocation(Location location) {
        JsonObject result = new JsonObject();
        result.addProperty("world", location.getWorld().getKey().asString());
        result.addProperty("x", location.getX());
        result.addProperty("y", location.getY());
        result.addProperty("z", location.getZ());
        result.addProperty("yaw", location.getYaw());
        result.addProperty("pitch", location.getPitch());
        return result;
    }

    private Location deserializeLocation(JsonObject document) {
        World world = Bukkit.getWorld(org.bukkit.NamespacedKey.fromString(document.get("world").getAsString()));
        if (world == null) throw new IllegalArgumentException("Saved world is not currently available.");
        return new Location(world, document.get("x").getAsDouble(), document.get("y").getAsDouble(), document.get("z").getAsDouble(),
            document.get("yaw").getAsFloat(), document.get("pitch").getAsFloat());
    }

    private Location safeLocation(Location requested) {
        int originX = requested.getBlockX();
        int originY = requested.getBlockY();
        int originZ = requested.getBlockZ();
        for (int radius = 0; radius <= 3; radius++) {
            for (int yOffset = -2; yOffset <= 3; yOffset++) {
                for (int xOffset = -radius; xOffset <= radius; xOffset++) {
                    for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                        Location candidate = new Location(requested.getWorld(), originX + xOffset + 0.5, originY + yOffset, originZ + zOffset + 0.5,
                            requested.getYaw(), requested.getPitch());
                        if (candidate.getBlock().isPassable()
                            && candidate.clone().add(0, 1, 0).getBlock().isPassable()
                            && candidate.clone().add(0, -1, 0).getBlock().getType().isSolid()) return candidate;
                    }
                }
            }
        }
        throw new IllegalArgumentException("No safe destination exists near the stored location.");
    }

    private String normalizeName(String rawName) {
        String name = rawName.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (!name.matches("[a-z0-9 _-]{1,24}")) throw new IllegalArgumentException("Location name must be 1-24 letters, digits, spaces, underscores, or hyphens.");
        return name;
    }

    private Path playerPath(UUID playerUuid, String file) {
        return godDirectory.resolve("players").resolve(playerUuid.toString()).resolve(file);
    }

    private void write(UUID playerUuid, String file, JsonObject document) throws IOException {
        Path destination = playerPath(playerUuid, file);
        Files.createDirectories(destination.getParent());
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(document) + System.lineSeparator(), StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
