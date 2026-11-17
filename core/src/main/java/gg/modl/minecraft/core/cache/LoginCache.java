package gg.modl.minecraft.core.cache;

import gg.modl.minecraft.api.http.response.PlayerLoginResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LoginCache {
    private static final long LOGIN_RESULT_EXPIRY_MS = TimeUnit.SECONDS.toMillis(30);
    private static final long PRE_LOGIN_RESULT_EXPIRY_MS = TimeUnit.SECONDS.toMillis(60);

    private final ConcurrentHashMap<UUID, CachedLoginResult> loginResultCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PreLoginResult> preLoginResults = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor;

    public LoginCache() {
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "modl-login-cache-cleanup");
            t.setDaemon(true);
            return t;
        });
        cleanupExecutor.scheduleAtFixedRate(this::cleanup, 30, 30, TimeUnit.SECONDS);
    }

    public CachedLoginResult getCachedLoginResult(UUID playerUuid) {
        CachedLoginResult cached = loginResultCache.get(playerUuid);
        if (cached != null && !cached.isExpired(System.currentTimeMillis())) return cached;
        return null;
    }

    public void cacheLoginResult(UUID playerUuid, PlayerLoginResponse response, Map<String, Object> ipInfo, String skinHash) {
        loginResultCache.put(playerUuid, new CachedLoginResult(response, ipInfo, skinHash, System.currentTimeMillis()));
    }

    public void invalidateLoginResult(UUID playerUuid) {
        loginResultCache.remove(playerUuid);
    }

    public void storePreLoginResult(UUID playerUuid, PreLoginResult result) {
        preLoginResults.put(playerUuid, result);
    }

    public PreLoginResult getAndRemovePreLoginResult(UUID playerUuid) {
        return preLoginResults.remove(playerUuid);
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        loginResultCache.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
        long preLoginCutoff = now - PRE_LOGIN_RESULT_EXPIRY_MS;
        preLoginResults.entrySet().removeIf(entry -> entry.getValue().getTimestampMs() < preLoginCutoff);
    }

    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) cleanupExecutor.shutdownNow();
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        loginResultCache.clear();
        preLoginResults.clear();
    }

    @Getter @RequiredArgsConstructor
    public static class CachedLoginResult {
        private final PlayerLoginResponse response;
        private final Map<String, Object> ipInfo;
        private final String skinHash;
        private final long timestampMs;

        public boolean isExpired() {
            return isExpired(System.currentTimeMillis());
        }

        boolean isExpired(long nowMs) {
            return nowMs > timestampMs + LOGIN_RESULT_EXPIRY_MS;
        }
    }

    @Getter
    public static class PreLoginResult {
        private final PlayerLoginResponse response;
        private final Map<String, Object> ipInfo;
        private final String skinHash;
        private final long timestampMs;
        private final Exception error;

        public PreLoginResult(PlayerLoginResponse response, Map<String, Object> ipInfo, String skinHash) {
            this.response = response;
            this.ipInfo = ipInfo;
            this.skinHash = skinHash;
            this.timestampMs = System.currentTimeMillis();
            this.error = null;
        }

        public PreLoginResult(Exception error) {
            this.response = null;
            this.ipInfo = null;
            this.skinHash = null;
            this.timestampMs = System.currentTimeMillis();
            this.error = error;
        }

        public boolean hasError() {
            return error != null;
        }

        public boolean isSuccess() {
            return error == null && response != null;
        }
    }
}
