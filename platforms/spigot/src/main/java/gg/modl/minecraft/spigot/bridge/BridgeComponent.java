package gg.modl.minecraft.spigot.bridge;

import gg.modl.minecraft.bridge.AbstractBridgeComponent;
import gg.modl.minecraft.bridge.BridgeReloadPresenter;
import gg.modl.minecraft.bridge.BridgePluginContext;
import gg.modl.minecraft.bridge.config.BridgeConfig;
import gg.modl.minecraft.bridge.config.StaffModeConfig;
import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.bridge.query.BridgeMessageHandler;
import gg.modl.minecraft.bridge.query.BridgeQueryClient;
import gg.modl.minecraft.bridge.reporter.AutoReporter;
import gg.modl.minecraft.bridge.reporter.detection.ViolationTracker;
import gg.modl.minecraft.bridge.reporter.hook.AntiCheatHook;
import gg.modl.minecraft.core.util.PluginLogger;
import gg.modl.minecraft.spigot.bridge.command.ModlBridgeCommand;
import gg.modl.minecraft.spigot.bridge.command.ProxyCmdCommand;
import gg.modl.minecraft.spigot.bridge.handler.BridgeOnlyFreezeHandler;
import gg.modl.minecraft.spigot.bridge.handler.FreezeHandler;
import gg.modl.minecraft.spigot.bridge.handler.StaffModeHandler;
import gg.modl.minecraft.spigot.bridge.reporter.hook.GrimHook;
import gg.modl.minecraft.spigot.bridge.reporter.hook.PolarHook;
import gg.modl.minecraft.spigot.bridge.reporter.hook.VulcanHook;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

public class BridgeComponent extends AbstractBridgeComponent implements Listener {
    private final JavaPlugin plugin;
    private final boolean polarLoaderAvailable;
    @Getter private FreezeHandler freezeHandler;
    private BridgeOnlyFreezeHandler bridgeOnlyFreezeHandler;
    @Getter private StaffModeHandler staffModeHandler;
    @Getter private SpigotBridgeActions bridgeActions;

    public BridgeComponent(JavaPlugin plugin, String apiKey, String backendUrl, String panelUrl,
                           PluginLogger logger, boolean polarLoaderAvailable) {
        super(new SpigotBridgePluginContext(plugin), apiKey, backendUrl, panelUrl, logger);
        this.plugin = plugin;
        this.polarLoaderAvailable = polarLoaderAvailable;
    }

    @Override
    protected void initFreezeHandler(BridgeLocaleManager localeManager) {
        freezeHandler = new FreezeHandler(plugin, localeManager, pluginLogger, context.getScheduler());
        freezeHandler.register();

        if (!bridgeOnly) return;
        bridgeOnlyFreezeHandler = new BridgeOnlyFreezeHandler(plugin, localeManager,
                context.getScheduler(), freezeHandler.getFrozenPlayerStore());
        bridgeOnlyFreezeHandler.register();
    }

    @Override
    protected void initStaffModeHandler(BridgeConfig bridgeConfig,
                                         BridgeLocaleManager localeManager,
                                         StaffModeConfig staffModeConfig) {
        staffModeHandler = new StaffModeHandler(plugin, bridgeConfig, freezeHandler, localeManager, pluginLogger,
                staffModeConfig, context.getScheduler());
        staffModeHandler.register();
        if (bridgeOnlyFreezeHandler != null) bridgeOnlyFreezeHandler.setStaffModeHandler(staffModeHandler);
        bridgeActions = new SpigotBridgeActions(staffModeHandler, freezeHandler);
    }

    @Override
    protected BridgeMessageHandler createMessageHandler() {
        return new SpigotBridgeMessageHandler(plugin, bridgeActions,
                statWipeHandler, this, context.getScheduler());
    }

    @Override
    protected void onBridgeClientCreated(BridgeQueryClient client) {
        staffModeHandler.setBridgeClient(client);
        freezeHandler.setBridgeClient(client);
    }

    @Override
    protected void registerAntiCheatHooks(List<AntiCheatHook> hooks) {
        if (Bukkit.getPluginManager().getPlugin("GrimAC") != null) {
            GrimHook grimHook = new GrimHook(plugin, bridgeConfig, violationTracker, autoReporter);
            grimHook.register();
            hooks.add(grimHook);
        }

        if (Bukkit.getPluginManager().getPlugin("Vulcan") != null) {
            VulcanHook vulcanHook = new VulcanHook(plugin, bridgeConfig, violationTracker, autoReporter);
            vulcanHook.register();
            hooks.add(vulcanHook);
        }

        attachPolarIfLoaded(hooks);

        if (hooks.isEmpty() && !polarLoaderAvailable) {
            pluginLogger.warning("[bridge] No anticheat plugins detected. Install GrimAC, Vulcan, or Polar for anticheat reporting.");
        }
    }

    public void hookPolar() {
        context.getScheduler().runOnMainThread(this::hookPolarOnMainThread);
    }

    private void hookPolarOnMainThread() {
        if (!isAnticheatHookEnabled()) return;
        if (violationTracker == null || autoReporter == null) {
            pluginLogger.warning("Polar enable callback fired but bridge is not fully initialized");
            return;
        }
        attachPolarIfLoaded(hooks);
    }

    private void attachPolarIfLoaded(List<AntiCheatHook> hooks) {
        for (AntiCheatHook hook : hooks) {
            if (hook instanceof PolarHook) return;
        }
        PolarHook polarHook = new PolarHook(plugin, bridgeConfig, violationTracker, autoReporter);
        if (!polarHook.isAvailable()) return;
        polarHook.register();
        if (polarHook.isRegistered()) {
            hooks.add(polarHook);
        }
    }

    @Override
    protected void registerPlatformEvents() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    protected void registerBridgeCommand() {
        PluginCommand command = plugin.getCommand("modlbridge");
        if (command == null) {
            pluginLogger.warning("[bridge] /modlbridge is missing from plugin.yml, reload command unavailable");
            return;
        }
        ModlBridgeCommand executor = new ModlBridgeCommand(
                new BridgeReloadPresenter(localeManager, this::reload));
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    @Override
    protected void registerProxyCommand(BridgeQueryClient client) {
        plugin.getCommand("proxycmd").setExecutor(new ProxyCmdCommand(plugin, localeManager, client));
    }

    @Override
    protected void onDisable() {
        if (staffModeHandler != null) staffModeHandler.shutdown();
        if (context.getScheduler() instanceof SpigotBridgeScheduler) {
            ((SpigotBridgeScheduler) context.getScheduler()).shutdown();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (violationTracker != null) violationTracker.resetPlayer(playerId);
        if (autoReporter != null) autoReporter.clearCooldown(playerId);
    }
}
