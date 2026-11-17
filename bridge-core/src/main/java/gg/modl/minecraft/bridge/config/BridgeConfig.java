package gg.modl.minecraft.bridge.config;

import gg.modl.minecraft.bridge.resource.BridgeYamlResource;
import gg.modl.minecraft.core.util.YamlValues;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import static gg.modl.minecraft.core.util.Java8Collections.listOf;
import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BridgeConfig {
    private static final String FILE_NAME = "bridge-config.yml";
    private static final String DEFAULT_THRESHOLD_KEY = "default";
    private static final String DEFAULT_STAT_WIPE_COMMAND = "clearstats {player}";
    private static final int DEFAULT_REPORT_COOLDOWN = 60;
    private static final int DEFAULT_VIOLATION_THRESHOLD = 10;
    private static final int DEFAULT_QUERY_PORT = 25590;

    @Setter @Builder.Default private volatile String apiKey = "";
    @Setter @Builder.Default private volatile boolean debug = false;
    @Builder.Default private volatile String proxyHost = "";
    @Builder.Default private volatile int proxyPort = DEFAULT_QUERY_PORT;
    @Builder.Default private volatile List<String> statWipeCommands = defaultStatWipeCommands();
    @Builder.Default private volatile String anticheatName = "Anti-cheat";
    @Builder.Default private volatile boolean anticheatHookEnabled = true;
    @Builder.Default private volatile String serverName = "Server 1";
    @Builder.Default private volatile int reportCooldown = DEFAULT_REPORT_COOLDOWN;
    @Builder.Default private volatile Map<String, Integer> reportViolationThresholds = defaultViolationThresholds();

    private static List<String> defaultStatWipeCommands() {
        return new ArrayList<>(listOf(DEFAULT_STAT_WIPE_COMMAND));
    }

    private static Map<String, Integer> defaultViolationThresholds() {
        return new LinkedHashMap<>(mapOf(DEFAULT_THRESHOLD_KEY, DEFAULT_VIOLATION_THRESHOLD));
    }

    public int getReportViolationThreshold(String checkName) {
        Integer checkSpecific = reportViolationThresholds.get(checkName.toLowerCase(Locale.ROOT));
        if (checkSpecific != null) return checkSpecific;
        Integer defaultVal = reportViolationThresholds.get(DEFAULT_THRESHOLD_KEY);
        return defaultVal != null ? defaultVal : DEFAULT_VIOLATION_THRESHOLD;
    }

    public void updateProxyConnection(String host, int port) {
        this.proxyHost = host;
        this.proxyPort = port;
    }

    public static boolean exists(Path dataDir) {
        return Files.exists(dataDir.resolve(FILE_NAME));
    }

    public static BridgeConfig load(Path dataDir) throws IOException {
        Path file = dataDir.resolve(FILE_NAME);
        if (!Files.exists(file)) return new BridgeConfig();

        Map<String, Object> data = BridgeYamlResource.loadMap(file);
        if (data.isEmpty()) return new BridgeConfig();
        return fromMap(data);
    }

    public void save(Path dataDir) throws IOException {
        Path file = dataDir.resolve(FILE_NAME);
        if (!Files.exists(dataDir)) Files.createDirectories(dataDir);

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);

        Yaml yaml = new Yaml(options);
        try (Writer writer = Files.newBufferedWriter(file)) {
            writer.write("# Bridge configuration for modl.gg plugin.\n");
            yaml.dump(toMap(), writer);
        }
    }

    public List<String> settingsNeedingRestart(BridgeConfig other) {
        List<String> changed = new ArrayList<>();
        if (!proxyHost.equals(other.proxyHost)) changed.add("proxy-host");
        if (proxyPort != other.proxyPort) changed.add("proxy-port");
        if (!serverName.equals(other.serverName)) changed.add("server-name");
        return changed;
    }

    public void applyReloadableSettings(BridgeConfig other) {
        statWipeCommands = other.statWipeCommands;
        anticheatName = other.anticheatName;
        anticheatHookEnabled = other.anticheatHookEnabled;
        debug = other.debug;
        reportCooldown = other.reportCooldown;
        reportViolationThresholds = other.reportViolationThresholds;
    }

    private static BridgeConfig fromMap(Map<String, Object> data) {
        return BridgeConfig.builder()
                .proxyHost(getStr(data, "proxy-host", getStr(data, "host", getStr(data, "query-host", ""))))
                .proxyPort(getInt(data, "proxy-port", getInt(data, "port", getInt(data, "query-port", DEFAULT_QUERY_PORT))))
                .anticheatName(getStr(data, "anticheat-name", "Anti-cheat"))
                .anticheatHookEnabled(getBool(data, "anticheat-hook-enabled", true))
                .serverName(getStr(data, "server-name", "Server 1"))
                .reportCooldown(getInt(data, "report-cooldown", DEFAULT_REPORT_COOLDOWN))
                .debug(getBool(data, "debug", false))
                .statWipeCommands(parseStatWipeCommands(data.get("stat-wipe-commands")))
                .reportViolationThresholds(parseViolationThresholds(data.get("report-violation-threshold")))
                .build();
    }

    private static List<String> parseStatWipeCommands(Object raw) {
        if (!(raw instanceof List<?>)) return defaultStatWipeCommands();
        List<String> commands = new ArrayList<>();
        for (Object command : (List<?>) raw) {
            commands.add(String.valueOf(command));
        }
        return commands;
    }

    private static Map<String, Integer> parseViolationThresholds(Object raw) {
        if (!(raw instanceof Map<?, ?>)) return defaultViolationThresholds();
        Map<String, Integer> thresholds = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
            if (entry.getValue() instanceof Number) {
                thresholds.put(String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT),
                        ((Number) entry.getValue()).intValue());
            }
        }
        return thresholds;
    }

    private Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("proxy-host", proxyHost);
        map.put("proxy-port", proxyPort);
        map.put("stat-wipe-commands", statWipeCommands);
        map.put("anticheat-name", anticheatName);
        map.put("anticheat-hook-enabled", anticheatHookEnabled);
        map.put("server-name", serverName);
        map.put("debug", debug);
        map.put("report-cooldown", reportCooldown);
        map.put("report-violation-threshold", reportViolationThresholds);
        return map;
    }

    private static String getStr(Map<String, Object> map, String key, String def) {
        return YamlValues.asString(map.get(key), def);
    }

    private static int getInt(Map<String, Object> map, String key, int def) {
        return YamlValues.asInt(map.get(key), def);
    }

    private static boolean getBool(Map<String, Object> map, String key, boolean def) {
        return YamlValues.asBoolean(map.get(key), def);
    }
}
