package gg.modl.minecraft.bridge.reporter;

import gg.modl.minecraft.bridge.config.BridgeConfig;
import gg.modl.minecraft.bridge.reporter.detection.DetectionSource;
import gg.modl.minecraft.bridge.reporter.detection.ViolationTracker;
import gg.modl.minecraft.core.util.PluginLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoReporterTest {

    private static final DetectionSource SOURCE = DetectionSource.GRIM;
    private static final String CHECK_NAME = "speed";
    private static final String PLAYER_NAME = "byteful";

    @TempDir
    Path tempDir;

    @Test
    void reportAboveThresholdCreatesTicket() throws IOException {
        TestFixture fixture = createFixture();
        fixture.addViolation();

        fixture.autoReporter.checkAndReport(fixture.playerUuid, PLAYER_NAME, SOURCE, CHECK_NAME);

        assertEquals(1, fixture.ticketCreator.tickets.size());
    }

    @Test
    void cooldownAppliesAfterIntendedReportSubmission() throws IOException {
        TestFixture fixture = createFixture();
        fixture.addViolation();

        fixture.autoReporter.checkAndReport(fixture.playerUuid, PLAYER_NAME, SOURCE, CHECK_NAME);
        fixture.addViolation();
        fixture.autoReporter.checkAndReport(fixture.playerUuid, PLAYER_NAME, SOURCE, CHECK_NAME);

        assertEquals(1, fixture.ticketCreator.tickets.size());
    }

    @Test
    void ticketCreationFailureIsLoggedWithoutSettingCooldown() throws IOException {
        BridgeConfig config = loadConfig();
        ThrowingTicketCreator ticketCreator = new ThrowingTicketCreator();
        ViolationTracker violationTracker = new ViolationTracker();
        Logger logger = isolatedLogger("ticket-failure-no-replay");
        CapturingHandler handler = attachWarningHandler(logger);
        try {
            AutoReporter autoReporter = new AutoReporter(PluginLogger.fromJul(logger), config, ticketCreator, violationTracker);
            UUID playerUuid = UUID.randomUUID();
            violationTracker.addViolation(playerUuid, SOURCE, CHECK_NAME, "verbose");

            autoReporter.checkAndReport(playerUuid, PLAYER_NAME, SOURCE, CHECK_NAME);
            assertEquals(1, ticketCreator.attempts);
            assertEquals(1, handler.warnings.size());
            assertTrue(handler.warnings.get(0).getMessage().contains("Ticket creation failed"));

            autoReporter.checkAndReport(playerUuid, PLAYER_NAME, SOURCE, CHECK_NAME);
            assertEquals(2, ticketCreator.attempts);
            assertEquals(2, handler.warnings.size());
        } finally {
            logger.removeHandler(handler);
        }
    }

    private static Logger isolatedLogger(String name) {
        Logger logger = Logger.getLogger("AutoReporterTest." + name + "." + UUID.randomUUID());
        logger.setUseParentHandlers(false);
        return logger;
    }

    private static CapturingHandler attachWarningHandler(Logger logger) {
        CapturingHandler handler = new CapturingHandler();
        logger.addHandler(handler);
        return handler;
    }

    private static final class CapturingHandler extends Handler {
        private final List<LogRecord> warnings = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                warnings.add(record);
            }
        }

        @Override public void flush() {}
        @Override public void close() throws SecurityException {}
    }

    private TestFixture createFixture() throws IOException {
        BridgeConfig config = loadConfig();
        RecordingTicketCreator ticketCreator = new RecordingTicketCreator();
        ViolationTracker violationTracker = new ViolationTracker();
        AutoReporter autoReporter = new AutoReporter(
                PluginLogger.fromJul(Logger.getLogger("test")),
                config,
                ticketCreator,
                violationTracker
        );
        return new TestFixture(autoReporter, ticketCreator, violationTracker);
    }

    private BridgeConfig loadConfig() throws IOException {
        Files.write(tempDir.resolve("bridge-config.yml"), String.join(System.lineSeparator(),
                "anticheat-name: \"TestAC\"",
                "server-name: \"TestServer\"",
                "report-cooldown: 60",
                "report-violation-threshold:",
                "  default: 1"
        ).getBytes(StandardCharsets.UTF_8));
        return BridgeConfig.load(tempDir);
    }

    private static final class TestFixture {
        private final UUID playerUuid = UUID.randomUUID();
        private final AutoReporter autoReporter;
        private final RecordingTicketCreator ticketCreator;
        private final ViolationTracker violationTracker;

        private TestFixture(AutoReporter autoReporter, RecordingTicketCreator ticketCreator,
                            ViolationTracker violationTracker) {
            this.autoReporter = autoReporter;
            this.ticketCreator = ticketCreator;
            this.violationTracker = violationTracker;
        }

        private void addViolation() {
            violationTracker.addViolation(playerUuid, SOURCE, CHECK_NAME, "verbose");
        }
    }

    private static final class ThrowingTicketCreator implements TicketCreator {
        private int attempts;

        @Override
        public void createTicket(TicketRequest request) {
            attempts++;
            throw new RuntimeException("ticket creation failed");
        }
    }

    private static final class RecordingTicketCreator implements TicketCreator {
        private final List<TicketRequest> tickets = new ArrayList<>();

        @Override
        public void createTicket(TicketRequest request) {
            tickets.add(request);
        }
    }
}
