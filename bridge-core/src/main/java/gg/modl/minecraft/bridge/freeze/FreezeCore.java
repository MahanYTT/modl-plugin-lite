package gg.modl.minecraft.bridge.freeze;

import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.bridge.query.BridgeQueryClient;
import gg.modl.minecraft.core.bridge.protocol.BridgeAction;
import gg.modl.minecraft.core.service.FrozenPlayerStore;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class FreezeCore implements FrozenPlayerStore {
    private static final String UNKNOWN_NAME = "Unknown";

    private final BridgeLocaleManager localeManager;
    private final FreezeOps ops;
    private final Map<UUID, UUID> frozenPlayerToStaff = new ConcurrentHashMap<>();
    private volatile boolean hasFrozen = false;

    @Setter private BridgeQueryClient bridgeClient;

    public void freeze(String targetUuid, String staffUuid) {
        freeze(UUID.fromString(targetUuid), UUID.fromString(staffUuid));
    }

    public void unfreeze(String targetUuid) {
        unfreeze(UUID.fromString(targetUuid));
    }

    @Override
    public void freeze(UUID target, UUID staff) {
        if (frozenPlayerToStaff.putIfAbsent(target, staff) != null) return;
        hasFrozen = true;
        ops.onFrozen(target);
        ops.sendMessage(target, localeManager.getMessage("freeze.frozen"));
    }

    @Override
    public void unfreeze(UUID target) {
        if (frozenPlayerToStaff.remove(target) == null) return;
        hasFrozen = !frozenPlayerToStaff.isEmpty();
        ops.onUnfrozen(target);
        ops.sendMessage(target, localeManager.getMessage("freeze.unfrozen"));
    }

    @Override
    public boolean isFrozen(UUID uuid) {
        return hasFrozen && frozenPlayerToStaff.containsKey(uuid);
    }

    @Override
    public boolean hasFrozenPlayers() {
        return hasFrozen;
    }

    @Override
    public boolean releaseOnDisconnect(UUID target) {
        if (frozenPlayerToStaff.remove(target) == null) return false;
        hasFrozen = !frozenPlayerToStaff.isEmpty();
        String name = ops.playerName(target);
        if (name == null) name = UNKNOWN_NAME;
        if (bridgeClient != null) {
            bridgeClient.sendMessage(BridgeAction.FREEZE_LOGOUT.wire(), target.toString(), name);
        }
        ops.onUnfrozen(target);
        return true;
    }
}
