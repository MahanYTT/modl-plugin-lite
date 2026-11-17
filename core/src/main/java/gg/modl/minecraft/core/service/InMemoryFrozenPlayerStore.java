package gg.modl.minecraft.core.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryFrozenPlayerStore implements FrozenPlayerStore {
    private final Map<UUID, UUID> frozenPlayerToStaff = new ConcurrentHashMap<>();
    private volatile boolean hasFrozen = false;

    @Override
    public void freeze(UUID target, UUID staff) {
        frozenPlayerToStaff.put(target, staff);
        hasFrozen = true;
    }

    @Override
    public void unfreeze(UUID target) {
        if (frozenPlayerToStaff.remove(target) != null) {
            hasFrozen = !frozenPlayerToStaff.isEmpty();
        }
    }

    @Override
    public boolean isFrozen(UUID target) {
        return hasFrozen && frozenPlayerToStaff.containsKey(target);
    }

    @Override
    public boolean hasFrozenPlayers() {
        return hasFrozen;
    }

    @Override
    public boolean releaseOnDisconnect(UUID target) {
        boolean removed = frozenPlayerToStaff.remove(target) != null;
        if (removed) {
            hasFrozen = !frozenPlayerToStaff.isEmpty();
        }
        return removed;
    }
}
