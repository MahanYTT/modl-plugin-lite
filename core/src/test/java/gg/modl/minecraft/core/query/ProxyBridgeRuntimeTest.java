package gg.modl.minecraft.core.query;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyBridgeRuntimeTest {

    @Test
    void shutdownStopsBridgeServer() {
        AtomicBoolean bridgeShutdown = new AtomicBoolean(false);
        ProxyBridgeRuntime runtime = new ProxyBridgeRuntime(() -> bridgeShutdown.set(true));

        runtime.shutdown();

        assertTrue(bridgeShutdown.get());
    }
}
