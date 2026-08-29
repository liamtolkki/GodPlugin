package dev.liamtolkkinen.god;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class EconomyManagerTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("god-economy-test-");
        try {
            writeEconomy(directory);
            EconomyManager economy = new EconomyManager(directory, false);

            writeLedger(directory, 100, 0);
            expectRejected(() -> economy.quoteMaterialOffering(PLAYER, "iron_ingot", 64, false, 64),
                "maximum favor balance");

            writeLedger(directory, 98, 0);
            EconomyManager.OfferingTerms balanceLimited = economy.quoteMaterialOffering(
                PLAYER, "iron_ingot", 0, true, 200);
            require(balanceLimited.itemCount() == 64, "all offering must stop at remaining balance capacity");
            require(balanceLimited.favorReturn() == 2, "balance-limited offering must return two favor");

            writeLedger(directory, 0, 4);
            EconomyManager.OfferingTerms dailyLimited = economy.quoteMaterialOffering(
                PLAYER, "iron_ingot", 0, true, 200);
            require(dailyLimited.itemCount() == 32, "all offering must stop at remaining daily allowance");
            require(dailyLimited.favorReturn() == 1, "daily-limited offering must return one favor");
            expectRejected(() -> economy.quoteMaterialOffering(PLAYER, "iron_ingot", 64, false, 64),
                "Only 1 favor remains");

            writeLedger(directory, 0, 0);
            EconomyManager.OfferingTerms successful = economy.quoteMaterialOffering(
                PLAYER, "iron_ingot", 64, false, 64);
            AtomicInteger successfulInventory = new AtomicInteger(64);
            economy.commitMaterialOffering(PLAYER, successful,
                () -> successfulInventory.addAndGet(-64),
                () -> successfulInventory.addAndGet(64));
            require(successfulInventory.get() == 0, "successful offering must remove inventory");
            require(economy.balance(PLAYER) == 2, "successful offering must credit favor");

            writeLedger(directory, 0, 0);
            EconomyManager.OfferingTerms rollback = economy.quoteMaterialOffering(
                PLAYER, "iron_ingot", 64, false, 64);
            Path ledgerPath = ledgerPath(directory);
            Files.delete(ledgerPath);
            Files.createDirectory(ledgerPath);
            AtomicInteger rollbackInventory = new AtomicInteger(64);
            try {
                economy.commitMaterialOffering(PLAYER, rollback,
                    () -> rollbackInventory.addAndGet(-64),
                    () -> rollbackInventory.addAndGet(64));
                throw new AssertionError("ledger write failure should reject the offering");
            } catch (IOException expected) {
                require(rollbackInventory.get() == 64, "failed ledger write must restore inventory");
            }

            System.out.println("GOD economy transaction tests passed.");
        } finally {
            if (Files.exists(directory)) {
                try (var paths = Files.walk(directory)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void writeEconomy(Path directory) throws IOException {
        Files.writeString(directory.resolve("economy.json"), """
            {
              "version": 1,
              "buy": {},
              "sell": {"minecraft:iron_ingot":{"itemQuantity":32,"favorQuantity":1}},
              "services": {},
              "earnings": {},
              "limits": {
                "maximumBalance":100,
                "dailyEarnedFavor":10,
                "weeklyEarnedFavor":40,
                "dailyOfferingFavor":5,
                "maximumItemsPerTransaction":64
              }
            }
            """);
    }

    private static void writeLedger(Path directory, int balance, int offeringEarnedToday) throws IOException {
        Path path = ledgerPath(directory);
        if (Files.isDirectory(path)) Files.delete(path);
        Files.createDirectories(path.getParent());
        String transaction = offeringEarnedToday == 0 ? "" : """
            {"timestamp":"%s","category":"material_offering","amount":%d,"balanceBefore":0,"balanceAfter":%d,"description":"test"}
            """.formatted(Instant.now(), offeringEarnedToday, offeringEarnedToday);
        Files.writeString(path, """
            {"balance":%d,"transactions":[%s]}
            """.formatted(balance, transaction));
    }

    private static Path ledgerPath(Path directory) {
        return directory.resolve("players").resolve(PLAYER.toString()).resolve("favor.json");
    }

    private static void expectRejected(ThrowingRunnable operation, String expectedMessage) throws Exception {
        try {
            operation.run();
            throw new AssertionError("expected offering rejection containing: " + expectedMessage);
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains(expectedMessage), "unexpected rejection: " + expected.getMessage());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
