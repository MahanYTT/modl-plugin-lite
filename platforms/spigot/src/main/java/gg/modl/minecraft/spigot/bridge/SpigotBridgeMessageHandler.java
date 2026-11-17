package gg.modl.minecraft.spigot.bridge;

import gg.modl.minecraft.bridge.BridgeScheduler;
import gg.modl.minecraft.bridge.query.BridgeMessageHandler;
import gg.modl.minecraft.bridge.statwipe.StatWipeHandler;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

@RequiredArgsConstructor
public class SpigotBridgeMessageHandler implements BridgeMessageHandler {
    private final JavaPlugin plugin;
    private final SpigotBridgeActions actions;
    private final StatWipeHandler statWipeHandler;
    private final BridgeComponent bridgeComponent;
    private final BridgeScheduler scheduler;

    @Override
    public void onFreeze(String targetUuid, String staffUuid) {
        actions.freeze(targetUuid, staffUuid);
    }

    @Override
    public void onUnfreeze(String targetUuid) {
        actions.unfreeze(targetUuid);
    }

    @Override
    public void onStaffModeEnter(String staffUuid, String staffName) {
        actions.enterStaffMode(staffUuid);
    }

    @Override
    public void onStaffModeExit(String staffUuid, String staffName) {
        actions.exitStaffMode(staffUuid);
    }

    @Override
    public void onVanishEnter(String staffUuid, String staffName) {
        actions.enterVanish(staffUuid);
    }

    @Override
    public void onVanishExit(String staffUuid, String staffName) {
        actions.exitVanish(staffUuid);
    }

    @Override
    public void onTargetRequest(String staffUuid, String targetUuid) {
        UUID targetUuidParsed = UUID.fromString(targetUuid);
        scheduler.runForPlayer(targetUuidParsed, () -> {
            Player target = Bukkit.getPlayer(targetUuidParsed);
            if (target == null || !target.isOnline()) return;

            bridgeComponent.getBridgeClient().sendMessage("TARGET_RESPONSE", staffUuid, targetUuid,
                    bridgeComponent.getBridgeConfig().getServerName());
            actions.setTarget(staffUuid, targetUuid);
        });
    }

    @Override
    public void onStatWipe(String username, String uuid, String punishmentId) {
        plugin.getLogger().info("[bridge] Processing stat-wipe for " + username + " (punishment: " + punishmentId + ")");
        scheduler.runOnMainThread(() -> {
            boolean success = statWipeHandler.execute(username, uuid, punishmentId);
            plugin.getLogger().info("[bridge] Stat-wipe for " + username + " " +
                    (success ? "succeeded" : "failed") + " (punishment: " + punishmentId + ")");
        });
    }

    @Override
    public void onPanelUrl(String panelUrl) {
    }
}
