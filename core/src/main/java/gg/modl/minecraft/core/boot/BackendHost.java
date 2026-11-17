package gg.modl.minecraft.core.boot;

import java.util.logging.Logger;

public final class BackendHost {
    public static final String PRODUCTION_API_URL = "https://api.modl.gg";
    public static final String TESTING_API_URL = "https://api.modl.top";

    private static final String OVERRIDE_PROPERTY = "modl.api.url";
    private static final String OVERRIDE_ENV = "MODL_API_URL";

    private static final Logger logger = Logger.getLogger(BackendHost.class.getName());
    private static volatile String configuredApiUrl = null;
    private static volatile boolean overrideLogged = false;

    private BackendHost() {
    }

    public static void setConfiguredApiUrl(String apiUrl) {
        String normalized = normalizeUrl(apiUrl);
        configuredApiUrl = normalized;
        overrideLogged = false;
    }

    public static String resolve(boolean useTestingApi) {
        String override = configuredOverride();
        if (override != null) {
            logOverrideOnce(override);
            return override;
        }
        return useTestingApi ? TESTING_API_URL : PRODUCTION_API_URL;
    }

    private static String configuredOverride() {
        String value = System.getProperty(OVERRIDE_PROPERTY);
        if (value == null || value.trim().isEmpty()) {
            value = System.getenv(OVERRIDE_ENV);
        }
        String normalized = normalizeUrl(value);
        if (normalized != null) {
            return normalized;
        }
        return configuredApiUrl;
    }

    private static String normalizeUrl(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        int end = trimmed.length();
        while (end > 0 && trimmed.charAt(end - 1) == '/') {
            end--;
        }
        return end == trimmed.length() ? trimmed : trimmed.substring(0, end);
    }

    private static void logOverrideOnce(String override) {
        if (overrideLogged) {
            return;
        }
        overrideLogged = true;
        logger.info("Backend API host overridden to " + override
                + " (via boot.yml api-url, -D" + OVERRIDE_PROPERTY + ", or " + OVERRIDE_ENV + ")");
        if (!override.regionMatches(true, 0, "https://", 0, 8)) {
            logger.warning("Backend API override is not HTTPS; the API key will be transmitted without TLS: " + override);
        }
    }
}
