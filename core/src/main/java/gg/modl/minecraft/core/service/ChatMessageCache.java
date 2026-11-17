package gg.modl.minecraft.core.service;

import gg.modl.minecraft.core.util.StringUtil;
import lombok.Value;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

public class ChatMessageCache {
    private static final DateTimeFormatter REPORT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC);
    private static final int DEFAULT_MAX_MESSAGES = 100, REPORT_LOOKBACK_MESSAGES = 10, REPORT_FALLBACK_SECONDS = 120;
    private static final int REPORT_WINDOW_SECONDS = 120;
    private static final long DEFAULT_MAX_AGE_MS = 600_000, CLEANUP_INTERVAL_MS = 30_000;

    private final Map<String, LinkedBlockingDeque<ChatMessage>> serverMessages = new ConcurrentHashMap<>();
    private final Map<String, String> playerToServer = new ConcurrentHashMap<>();
    private final int maxMessagesPerServer;
    private final long maxMessageAge;
    private volatile long lastCleanupTime = 0;

    public ChatMessageCache() {
        this(DEFAULT_MAX_MESSAGES, DEFAULT_MAX_AGE_MS);
    }

    public ChatMessageCache(int maxMessagesPerServer, long maxMessageAge) {
        if (maxMessagesPerServer < 1) {
            throw new IllegalArgumentException(
                    "maxMessagesPerServer must be at least 1 but was " + maxMessagesPerServer);
        }
        this.maxMessagesPerServer = maxMessagesPerServer;
        this.maxMessageAge = maxMessageAge;
    }

    public void addMessage(String serverName, String playerUuid, String playerName, String message) {
        playerToServer.put(playerUuid, serverName);
        if (StringUtil.isBlank(message)) return;
        LinkedBlockingDeque<ChatMessage> queue =
                serverMessages.computeIfAbsent(serverName, k -> new LinkedBlockingDeque<>(maxMessagesPerServer));
        ChatMessage entry = new ChatMessage(playerUuid, playerName, message, serverName, Instant.now());
        while (!queue.offerLast(entry)) queue.pollFirst();

        long now = System.currentTimeMillis();
        if (now - lastCleanupTime > CLEANUP_INTERVAL_MS) {
            lastCleanupTime = now;
            Instant cutoff = Instant.ofEpochMilli(now - maxMessageAge);
            for (LinkedBlockingDeque<ChatMessage> serverQueue : serverMessages.values()) {
                cleanupOldMessages(serverQueue, cutoff);
            }
        }
    }

    public String getChatLogForReport(String reportedPlayerUuid) {
        List<ChatMessage> allMessages = collectMessagesForReportedPlayer(reportedPlayerUuid);
        if (allMessages.isEmpty()) return "";

        Instant startTimestamp = determineReportStartTimestamp(allMessages, reportedPlayerUuid);

        List<ChatMessage> relevantMessages = new ArrayList<>(allMessages.size());
        for (ChatMessage msg : allMessages) {
            if (!msg.getTimestamp().isBefore(startTimestamp)) {
                relevantMessages.add(msg);
            }
        }
        if (relevantMessages.isEmpty()) return "";
        relevantMessages.sort(Comparator.comparing(ChatMessage::getTimestamp));

        StringBuilder chatLog = new StringBuilder();
        for (ChatMessage msg : relevantMessages) {
            chatLog.append(REPORT_TIME_FORMAT.format(msg.getTimestamp()))
                   .append(" ").append(msg.getPlayerName())
                   .append(": ").append(msg.getMessage())
                   .append("\n");
        }
        return chatLog.toString().trim();
    }

    public void updatePlayerServer(String serverName, String playerUuid) {
        playerToServer.put(playerUuid, serverName);
    }

    public void removePlayer(String playerUuid) {
        playerToServer.remove(playerUuid);
    }

    private List<ChatMessage> collectMessagesForReportedPlayer(String reportedPlayerUuid) {
        Instant cutoff = Instant.now().minusMillis(maxMessageAge);
        String currentServer = playerToServer.get(reportedPlayerUuid);
        if (currentServer != null) {
            LinkedBlockingDeque<ChatMessage> serverQueue = serverMessages.get(currentServer);
            if (serverQueue != null && containsPlayerMessage(serverQueue, reportedPlayerUuid)) {
                cleanupOldMessages(serverQueue, cutoff);
                return new ArrayList<>(serverQueue);
            }
        }

        List<ChatMessage> allMessages = new ArrayList<>();
        for (LinkedBlockingDeque<ChatMessage> queue : serverMessages.values()) {
            if (!containsPlayerMessage(queue, reportedPlayerUuid)) continue;
            cleanupOldMessages(queue, cutoff);
            allMessages.addAll(queue);
        }
        return allMessages;
    }

    private static boolean containsPlayerMessage(LinkedBlockingDeque<ChatMessage> queue, String playerUuid) {
        for (ChatMessage msg : queue) {
            if (msg.getPlayerUuid().equals(playerUuid)) return true;
        }
        return false;
    }

    private Instant determineReportStartTimestamp(List<ChatMessage> allMessages, String reportedPlayerUuid) {
        List<ChatMessage> reportedMessages = new ArrayList<>();
        for (ChatMessage msg : allMessages) {
            if (msg.getPlayerUuid().equals(reportedPlayerUuid)) {
                reportedMessages.add(msg);
            }
        }

        if (reportedMessages.isEmpty()) {
            return Instant.now().minusSeconds(REPORT_FALLBACK_SECONDS);
        }
        reportedMessages.sort(Comparator.comparing(ChatMessage::getTimestamp));

        Instant lastReported = reportedMessages.get(reportedMessages.size() - 1).getTimestamp();
        Instant timeFloor = lastReported.minusSeconds(REPORT_WINDOW_SECONDS);

        int startIndex = Math.max(0, reportedMessages.size() - REPORT_LOOKBACK_MESSAGES);
        Instant countFloor = reportedMessages.get(startIndex).getTimestamp();

        return laterOf(timeFloor, countFloor);
    }

    private static Instant laterOf(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static void cleanupOldMessages(LinkedBlockingDeque<ChatMessage> queue, Instant cutoff) {
        queue.removeIf(message -> message.getTimestamp().isBefore(cutoff));
    }

    @Value
    public static class ChatMessage {
        String playerUuid, playerName, message, serverName;
        Instant timestamp;
    }
}
