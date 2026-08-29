package dev.liamtolkkinen.god;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Material;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

final class EconomyManager {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private final Path godDirectory;
    private final boolean validateBukkitMaterials;

    EconomyManager(Path godDirectory) throws IOException {
        this(godDirectory, true);
    }

    EconomyManager(Path godDirectory, boolean validateBukkitMaterials) throws IOException {
        this.godDirectory = godDirectory;
        this.validateBukkitMaterials = validateBukkitMaterials;
        validate(readConfig());
    }

    synchronized JsonObject readConfig() throws IOException {
        return JsonParser.parseString(Files.readString(godDirectory.resolve("economy.json"))).getAsJsonObject();
    }

    synchronized JsonObject publicCatalog(String menu) throws IOException {
        JsonObject config = readConfig();
        JsonObject result = new JsonObject();
        result.addProperty("menu", menu);
        result.addProperty("version", config.get("version").getAsInt());
        result.add("items", config.getAsJsonObject(menu).deepCopy());
        return result;
    }

    synchronized JsonObject publicServices() throws IOException {
        JsonObject config = readConfig();
        JsonObject result = new JsonObject();
        result.addProperty("version", config.get("version").getAsInt());
        result.add("services", config.getAsJsonObject("services").deepCopy());
        return result;
    }

    synchronized int servicePrice(String service) throws IOException {
        JsonObject services = readConfig().getAsJsonObject("services");
        if (!services.has(service)) throw new IllegalArgumentException("Unknown priced service: " + service);
        return services.get(service).getAsInt();
    }

    synchronized int earningReward(String eventType) throws IOException {
        JsonObject earnings = readConfig().getAsJsonObject("earnings");
        return earnings.has(eventType) ? earnings.get(eventType).getAsInt() : 0;
    }

    synchronized int calculatePurchaseCost(String material, int count) throws IOException {
        material = canonicalMaterial(material);
        JsonObject buy = readConfig().getAsJsonObject("buy");
        if (!buy.has(material)) throw new IllegalArgumentException("Item is not available from the buy menu: " + material);
        JsonObject price = buy.getAsJsonObject(material);
        int itemQuantity = price.get("itemQuantity").getAsInt();
        int favorQuantity = price.get("favorQuantity").getAsInt();
        if (count < 1 || count % itemQuantity != 0) {
            throw new IllegalArgumentException("Item must be purchased in multiples of " + itemQuantity + ".");
        }
        int maximumItems = readConfig().getAsJsonObject("limits").get("maximumItemsPerTransaction").getAsInt();
        if (count > maximumItems) throw new IllegalArgumentException("Combined reward count exceeds the configured limit.");
        return Math.multiplyExact(count / itemQuantity, favorQuantity);
    }

    synchronized int calculateSaleReturn(String material, int count) throws IOException {
        material = canonicalMaterial(material);
        JsonObject sell = readConfig().getAsJsonObject("sell");
        if (!sell.has(material)) throw new IllegalArgumentException("Item is not accepted by the sell menu: " + material);
        JsonObject price = sell.getAsJsonObject(material);
        int itemQuantity = price.get("itemQuantity").getAsInt();
        int favorQuantity = price.get("favorQuantity").getAsInt();
        if (count < 1 || count % itemQuantity != 0) {
            throw new IllegalArgumentException("Item must be offered in multiples of " + itemQuantity + ".");
        }
        return Math.multiplyExact(count / itemQuantity, favorQuantity);
    }

    synchronized OfferingTerms quoteMaterialOffering(UUID playerUuid, String materialName, int requestedCount,
        boolean offerAll, int inventoryCount) throws IOException {
        String material = canonicalMaterial(materialName);
        JsonObject config = readConfig();
        JsonObject sell = config.getAsJsonObject("sell");
        if (!sell.has(material)) throw new IllegalArgumentException("Item is not accepted by the sell menu: " + material);
        JsonObject price = sell.getAsJsonObject(material);
        int itemQuantity = price.get("itemQuantity").getAsInt();
        int favorQuantity = price.get("favorQuantity").getAsInt();
        int maximumItems = config.getAsJsonObject("limits").get("maximumItemsPerTransaction").getAsInt();
        JsonObject ledger = readLedger(playerUuid);
        int availableFavor = availableOfferingFavor(config, ledger);

        int acceptedCount;
        if (offerAll) {
            int bundles = Math.min(inventoryCount / itemQuantity, maximumItems / itemQuantity);
            bundles = Math.min(bundles, availableFavor / favorQuantity);
            if (bundles < 1) throw new IllegalArgumentException(offeringCapacityFailure(config, ledger, inventoryCount, itemQuantity));
            acceptedCount = Math.multiplyExact(bundles, itemQuantity);
        } else {
            if (requestedCount < 1 || requestedCount % itemQuantity != 0) {
                throw new IllegalArgumentException("Item must be offered in multiples of " + itemQuantity + ".");
            }
            if (requestedCount > maximumItems) throw new IllegalArgumentException("Offering count exceeds the configured transaction limit of " + maximumItems + ".");
            if (inventoryCount < requestedCount) throw new IllegalArgumentException("The player does not possess the requested offering quantity.");
            int favorReturn = Math.multiplyExact(requestedCount / itemQuantity, favorQuantity);
            if (favorReturn > availableFavor) {
                if (availableFavor > 0) throw new IllegalArgumentException("Only " + availableFavor + " favor remains available from material offerings.");
                throw new IllegalArgumentException(offeringCapacityFailure(config, ledger, inventoryCount, itemQuantity));
            }
            acceptedCount = requestedCount;
        }
        int favorReturn = Math.multiplyExact(acceptedCount / itemQuantity, favorQuantity);
        return new OfferingTerms(material, acceptedCount, favorReturn, config.get("version").getAsInt());
    }

    synchronized int commitMaterialOffering(UUID playerUuid, OfferingTerms quoted, Runnable removeItems, Runnable restoreItems)
        throws IOException {
        JsonObject config = readConfig();
        int currentVersion = config.get("version").getAsInt();
        if (currentVersion != quoted.configVersion()) {
            throw new IllegalArgumentException("The economy changed after this offering was quoted; request a new quote.");
        }
        JsonObject ledger = readLedger(playerUuid);
        int availableFavor = availableOfferingFavor(config, ledger);
        if (quoted.favorReturn() > availableFavor) {
            throw new IllegalArgumentException(offeringCapacityFailure(config, ledger, quoted.itemCount(),
                config.getAsJsonObject("sell").getAsJsonObject(quoted.material()).get("itemQuantity").getAsInt()));
        }
        int recalculated = calculateSaleReturn(quoted.material(), quoted.itemCount());
        if (recalculated != quoted.favorReturn()) throw new IllegalArgumentException("The offering quote no longer matches the configured price.");

        boolean removed = false;
        try {
            removeItems.run();
            removed = true;
            int before = ledger.get("balance").getAsInt();
            appendTransaction(ledger, quoted.favorReturn(), "material_offering",
                "Offered " + quoted.itemCount() + "x " + quoted.material(), before);
            writeLedger(playerUuid, ledger);
            return quoted.favorReturn();
        } catch (IOException | RuntimeException failure) {
            if (removed) {
                try {
                    restoreItems.run();
                } catch (RuntimeException restoreFailure) {
                    failure.addSuppressed(restoreFailure);
                }
            }
            throw failure;
        }
    }

    String canonicalMaterial(String value) {
        return normalizeMaterial(value);
    }

    synchronized int balance(UUID playerUuid) throws IOException {
        JsonObject ledger = readLedger(playerUuid);
        return ledger.get("balance").getAsInt();
    }

    synchronized void debit(UUID playerUuid, int amount, String category, String description) throws IOException {
        if (amount <= 0) return;
        JsonObject ledger = readLedger(playerUuid);
        int before = ledger.get("balance").getAsInt();
        if (before < amount) throw new IllegalArgumentException("This costs " + amount + " favor, but only " + before + " is available.");
        appendTransaction(ledger, -amount, category, description, before);
        writeLedger(playerUuid, ledger);
    }

    synchronized void credit(UUID playerUuid, int amount, String category, String description) throws IOException {
        if (amount <= 0) return;
        JsonObject config = readConfig();
        int maximum = config.getAsJsonObject("limits").get("maximumBalance").getAsInt();
        JsonObject ledger = readLedger(playerUuid);
        int before = ledger.get("balance").getAsInt();
        JsonObject limits = config.getAsJsonObject("limits");
        if (category.equals("material_offering")) {
            int used = positiveCreditsSince(ledger, "material_offering", Instant.now().minusSeconds(86400));
            int limit = limits.get("dailyOfferingFavor").getAsInt();
            if (used + amount > limit) throw new IllegalArgumentException("The daily material-offering limit is " + limit + " favor.");
        } else if (category.startsWith("earned_")) {
            int daily = positiveCreditsSince(ledger, "earned_", Instant.now().minusSeconds(86400));
            int weekly = positiveCreditsSince(ledger, "earned_", Instant.now().minusSeconds(7 * 86400L));
            if (daily + amount > limits.get("dailyEarnedFavor").getAsInt()) throw new IllegalArgumentException("Daily earned-favor limit reached.");
            if (weekly + amount > limits.get("weeklyEarnedFavor").getAsInt()) throw new IllegalArgumentException("Weekly earned-favor limit reached.");
        }
        if (before + amount > maximum) throw new IllegalArgumentException("This would exceed the maximum favor balance of " + maximum + ".");
        appendTransaction(ledger, amount, category, description, before);
        writeLedger(playerUuid, ledger);
    }

    private int positiveCreditsSince(JsonObject ledger, String categoryPrefix, Instant cutoff) {
        int total = 0;
        for (JsonElement element : ledger.getAsJsonArray("transactions")) {
            JsonObject transaction = element.getAsJsonObject();
            if (!transaction.get("category").getAsString().startsWith(categoryPrefix)) continue;
            if (Instant.parse(transaction.get("timestamp").getAsString()).isBefore(cutoff)) continue;
            int amount = transaction.get("amount").getAsInt();
            if (amount > 0) total += amount;
        }
        return total;
    }

    private int availableOfferingFavor(JsonObject config, JsonObject ledger) {
        JsonObject limits = config.getAsJsonObject("limits");
        int balanceRoom = Math.max(0, limits.get("maximumBalance").getAsInt() - ledger.get("balance").getAsInt());
        int usedToday = positiveCreditsSince(ledger, "material_offering", Instant.now().minusSeconds(86400));
        int dailyRoom = Math.max(0, limits.get("dailyOfferingFavor").getAsInt() - usedToday);
        return Math.min(balanceRoom, dailyRoom);
    }

    private String offeringCapacityFailure(JsonObject config, JsonObject ledger, int inventoryCount, int itemQuantity) {
        JsonObject limits = config.getAsJsonObject("limits");
        int balance = ledger.get("balance").getAsInt();
        if (balance >= limits.get("maximumBalance").getAsInt()) {
            return "The player is already at the maximum favor balance of " + limits.get("maximumBalance").getAsInt() + ".";
        }
        int usedToday = positiveCreditsSince(ledger, "material_offering", Instant.now().minusSeconds(86400));
        if (usedToday >= limits.get("dailyOfferingFavor").getAsInt()) {
            return "The daily material-offering limit of " + limits.get("dailyOfferingFavor").getAsInt() + " favor has been reached.";
        }
        if (inventoryCount < itemQuantity) return "The player does not possess one complete offering bundle.";
        return "No complete offering bundle fits within the remaining favor limits.";
    }

    synchronized String setPrice(String menu, String materialName, int itemQuantity, int favorQuantity) throws IOException {
        if (!menu.equals("buy") && !menu.equals("sell")) throw new IllegalArgumentException("Menu must be buy or sell.");
        String material = normalizeMaterial(materialName);
        if (itemQuantity < 1 || favorQuantity < 1) throw new IllegalArgumentException("Quantities must be positive integers.");
        JsonObject config = readConfig();
        JsonObject price = new JsonObject();
        price.addProperty("itemQuantity", itemQuantity);
        price.addProperty("favorQuantity", favorQuantity);
        config.getAsJsonObject(menu).add(material, price);
        bumpVersion(config);
        validate(config);
        writeConfig(config);
        return material + ": " + itemQuantity + " item(s) for " + favorQuantity + " favor";
    }

    synchronized String removePrice(String menu, String materialName) throws IOException {
        if (!menu.equals("buy") && !menu.equals("sell")) throw new IllegalArgumentException("Menu must be buy or sell.");
        String material = normalizeMaterial(materialName);
        JsonObject config = readConfig();
        if (config.getAsJsonObject(menu).remove(material) == null) throw new IllegalArgumentException("Item is not on the " + menu + " menu.");
        bumpVersion(config);
        writeConfig(config);
        return material + " removed from the " + menu + " menu";
    }

    synchronized String setNamedValue(String section, String name, int value) throws IOException {
        if (!section.equals("services") && !section.equals("limits") && !section.equals("earnings")) throw new IllegalArgumentException("Unknown economy section.");
        if (value < 0) throw new IllegalArgumentException("Value cannot be negative.");
        JsonObject config = readConfig();
        JsonObject values = config.getAsJsonObject(section);
        if (!values.has(name)) throw new IllegalArgumentException("Unknown " + section + " setting: " + name);
        values.addProperty(name, value);
        bumpVersion(config);
        validate(config);
        writeConfig(config);
        return name + " = " + value;
    }

    private JsonObject readLedger(UUID playerUuid) throws IOException {
        Path path = godDirectory.resolve("players").resolve(playerUuid.toString()).resolve("favor.json");
        if (!Files.isRegularFile(path)) {
            JsonObject ledger = new JsonObject();
            ledger.addProperty("balance", 0);
            ledger.add("transactions", new JsonArray());
            return ledger;
        }
        JsonObject ledger = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        if (!ledger.has("balance")) ledger.addProperty("balance", 0);
        if (!ledger.has("transactions")) ledger.add("transactions", new JsonArray());
        return ledger;
    }

    private void appendTransaction(JsonObject ledger, int amount, String category, String description, int before) {
        JsonObject transaction = new JsonObject();
        transaction.addProperty("timestamp", Instant.now().toString());
        transaction.addProperty("category", category);
        transaction.addProperty("amount", amount);
        transaction.addProperty("balanceBefore", before);
        transaction.addProperty("balanceAfter", before + amount);
        transaction.addProperty("description", description);
        ledger.getAsJsonArray("transactions").add(transaction);
        ledger.addProperty("balance", before + amount);
    }

    private void writeLedger(UUID playerUuid, JsonObject ledger) throws IOException {
        Path directory = godDirectory.resolve("players").resolve(playerUuid.toString());
        Files.createDirectories(directory);
        atomicWrite(directory.resolve("favor.json"), ledger);
    }

    private void writeConfig(JsonObject config) throws IOException {
        atomicWrite(godDirectory.resolve("economy.json"), config);
    }

    private void atomicWrite(Path destination, JsonObject document) throws IOException {
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(document) + System.lineSeparator(), StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void bumpVersion(JsonObject config) {
        config.addProperty("version", config.get("version").getAsInt() + 1);
    }

    private String normalizeMaterial(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!normalized.contains(":")) normalized = "minecraft:" + normalized;
        if (!normalized.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Unknown item material: " + value);
        }
        if (!validateBukkitMaterials) return normalized;
        Material material = Material.matchMaterial(normalized);
        if (material == null || !material.isItem()) throw new IllegalArgumentException("Unknown item material: " + value);
        return material.getKey().asString();
    }

    private void validate(JsonObject config) {
        for (String required : new String[]{"version", "buy", "sell", "services", "earnings", "limits"}) {
            if (!config.has(required)) throw new IllegalArgumentException("Economy configuration is missing " + required + ".");
        }
        for (String menuName : new String[]{"buy", "sell"}) {
            for (var entry : config.getAsJsonObject(menuName).entrySet()) {
                normalizeMaterial(entry.getKey());
                JsonObject price = entry.getValue().getAsJsonObject();
                if (price.get("itemQuantity").getAsInt() < 1 || price.get("favorQuantity").getAsInt() < 1) {
                    throw new IllegalArgumentException("Invalid price for " + entry.getKey());
                }
            }
        }
    }

    record OfferingTerms(String material, int itemCount, int favorReturn, int configVersion) {}
}
