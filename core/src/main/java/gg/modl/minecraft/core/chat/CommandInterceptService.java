package gg.modl.minecraft.core.chat;

import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.CachedProfile;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.punishment.PunishmentMessageService;
import gg.modl.minecraft.core.service.ChatCommandLogService;
import gg.modl.minecraft.core.service.FreezeService;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class CommandInterceptService {
    public enum CommandResult { ALLOWED, BLOCKED_FROZEN, BLOCKED_MUTED }

    private final Cache cache;
    private final FreezeService freezeService;
    private final ChatCommandLogService chatCommandLogService;
    private final LocaleManager localeManager;
    private final PunishmentMessageService punishmentMessageService;
    private final Set<String> mutedCommands;

    public CommandInterceptService(Cache cache, FreezeService freezeService, ChatCommandLogService chatCommandLogService,
                                   LocaleManager localeManager, PunishmentMessageService punishmentMessageService,
                                   List<String> mutedCommands) {
        this.cache = cache;
        this.freezeService = freezeService;
        this.chatCommandLogService = chatCommandLogService;
        this.localeManager = localeManager;
        this.punishmentMessageService = punishmentMessageService;
        this.mutedCommands = normalizeMutedCommands(mutedCommands);
    }

    private static Set<String> normalizeMutedCommands(List<String> mutedCommands) {
        if (mutedCommands == null || mutedCommands.isEmpty()) return Collections.emptySet();
        Set<String> normalized = new HashSet<>(mutedCommands.size());
        for (String cmd : mutedCommands) {
            if (cmd == null) continue;
            String stripped = cmd.startsWith("/") ? cmd.substring(1) : cmd;
            String lower = stripped.trim().toLowerCase(Locale.ROOT);
            if (!lower.isEmpty()) normalized.add(lower);
        }
        return normalized.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(normalized);
    }

    public CommandResult handleCommand(UUID uuid, String username, String command, String serverName) {
        if (freezeService.isFrozen(uuid)) {
            return CommandResult.BLOCKED_FROZEN;
        }

        chatCommandLogService.addCommand(uuid.toString(), username, command, serverName);

        CachedProfile profile = cache.getPlayerProfile(uuid);
        if (profile != null && profile.isMuted() && isBlockedCommand(command)) {
            return CommandResult.BLOCKED_MUTED;
        }

        return CommandResult.ALLOWED;
    }

    public String getBlockMessage(CommandResult result, UUID uuid) {
        if (result == CommandResult.BLOCKED_FROZEN) {
            return localeManager.getMessage("freeze.command_blocked");
        } else if (result == CommandResult.BLOCKED_MUTED) {
            CachedProfile profile = cache.getPlayerProfile(uuid);
            return punishmentMessageService.getMuteMessage(profile != null ? profile.getActiveMute() : null);
        } else {
            return null;
        }
    }

    private boolean isBlockedCommand(String commandLine) {
        if (commandLine == null || mutedCommands.isEmpty()) return false;

        String line = commandLine.startsWith("/") ? commandLine.substring(1) : commandLine;
        int spaceIndex = line.indexOf(' ');
        String baseCommand = (spaceIndex >= 0 ? line.substring(0, spaceIndex) : line).toLowerCase(Locale.ROOT);

        return mutedCommands.contains(baseCommand);
    }
}
