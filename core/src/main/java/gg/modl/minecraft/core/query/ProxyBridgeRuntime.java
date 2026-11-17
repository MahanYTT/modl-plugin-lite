package gg.modl.minecraft.core.query;

import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.PluginLoader;
import gg.modl.minecraft.core.boot.BootConfig;
import gg.modl.minecraft.core.util.PluginLogger;

public final class ProxyBridgeRuntime {
    private final Runnable bridgeShutdown;

    ProxyBridgeRuntime(Runnable bridgeShutdown) {
        this.bridgeShutdown = bridgeShutdown;
    }

    public static ProxyBridgeRuntime startIfProxy(
            Platform platform,
            PluginLoader pluginLoader,
            BootConfig bootConfig,
            PluginLogger pluginLogger,
            String panelUrl) {
        if (bootConfig.getMode() != BootConfig.Mode.PROXY) return null;

        int bridgePort = bootConfig.getBridgePort();
        String apiKey = bootConfig.getApiKey();

        BridgeMessageDispatcher dispatcher = new BridgeMessageDispatcher(
                platform, pluginLoader.getLocaleManager(), pluginLoader.getFreezeService(),
                pluginLoader.getStaffModeService(), pluginLoader.getVanishService(),
                pluginLoader.getHttpClient(), pluginLogger);

        BridgeServer bridgeServer = new BridgeServer(bridgePort, apiKey, dispatcher, pluginLogger, panelUrl);
        bridgeServer.start();

        pluginLoader.getSyncService().setStatWipeExecutor(bridgeServer);
        pluginLoader.getBridgeService().setExecutor(bridgeServer);

        return new ProxyBridgeRuntime(bridgeServer::shutdown);
    }

    public void shutdown() {
        bridgeShutdown.run();
    }
}
