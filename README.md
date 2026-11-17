# modl-plugin-lite — Ultra-Low Overhead Minecraft Moderation Plugin

A high-efficiency, minimal-footprint fork of the official **[modl.gg Minecraft Plugin](github.com/modl-gg/minecraft)**, engineered to pair with **[modl-go](https://github.com/MahanYTT/modl-go)** (the single-binary Go + SQLite self-hosted backend & panel).

Supports **Spigot / Paper / Folia**, **Velocity**, **BungeeCord**, and **Fabric** (standalone or proxy+bridge network topologies) with all packet-level replay recording overhead stripped out and hot-path tick/network code optimized for minimal RAM and CPU usage.

---

## Key Optimizations & Changes vs. Upstream

### 1. Complete Replay Removal (Zero Per-Player Packet Buffering)
- **Removed `replay-format` & `replay-recording` dependencies** and stripped `RecordingManager`, `PacketRecorder`, `ModlBackendReplayUploader`, `/replay`, report GUI replay toggles, and `CAPTURE_REPLAY` bridge protocol frames.
- **Why this matters for RAM & CPU**: Upstream continuously records a rolling 60-second packet/entity/movement/chunk ring buffer for every online player, tracks nearby players within a 64-block radius on join/world-change, runs reflection (`Block#getBlockData` + `SpigotConversionUtil`) on every `BlockPlaceEvent` and `BlockBreakEvent`, and spawns background multipart upload threads. Stripping replay eliminates the #1 source of heap allocations, GC pressure, and Netty/tick-thread overhead on busy servers.

### 2. Tick-Thread Fast-Path Guards (`O(1)` Volatile Reads)
- **`FreezeCore` & `FrozenPlayerStore` (`hasFrozenPlayers()`)**: Added a `volatile boolean hasFrozen` fast-path flag updated only when the frozen player map transitions between empty and non-empty. `FreezeHandler#onMove` (`PlayerMoveEvent`, which fires thousands of times per second across online players) and `BridgeOnlyFreezeHandler` now return in a **single volatile boolean read** without calling `Player#getUniqueId()` or hashing a `UUID` whenever no players are frozen.
- **`StaffModeCore` & `StaffModeHandler` (`hasActiveStaffMode()` / `hasVanishedStaff()`)**: Added empty-set guards at the entry of `PlayerInteractEvent`, `PlayerInteractEntityEvent`, `BlockBreakEvent`, `BlockPlaceEvent`, `EntityDamageEvent`, `EntityDamageByEntityEvent`, `PlayerPickupItemEvent`, `PlayerDropItemEvent`, `FoodLevelChangeEvent`, `InventoryClickEvent`, and `EntityTargetLivingEntityEvent`, skipping all event processing in 1 check during normal gameplay.

### 3. Persistent HTTP Keep-Alive & Gzip (`AbstractModlHttpTransport`)
- **HTTP Keep-Alive socket reuse**: Removed unconditional `HttpURLConnection#disconnect()` from the normal `finally` block (disconnecting only on `IOException`). Previously, calling `disconnect()` tore down the underlying TCP/TLS socket after every single HTTP request, forcing a fresh TCP handshake on every 5-second `/v2/minecraft/sync` poll and ticket/punishment call.
- **Gzip compression & pre-sized buffers**: Added `Accept-Encoding: gzip` with transparent `GZIPInputStream` decoding, pre-sized `ByteArrayOutputStream` from `Content-Length`, and replaced `Instant.now()` + `Duration.between()` allocations with primitive `System.nanoTime()`.

### 4. Lock-Free & Allocation-Free Hot Paths
- **`ViolationTracker`**: Replaced unconditional `ConcurrentHashMap#computeIfAbsent` (which acquires a bucket monitor lock on every anticheat violation flag from GrimAC/Vulcan/Polar) with a lock-free `.get(uuid)` fast path.
- **`CommandInterceptService`**: Pre-normalized `muted_commands` into a lowercase `HashSet<String>` at startup for `O(1)` lookup instead of linear `equalsIgnoreCase` list iteration on every player command.
- **`LoginCache`**: Replaced `Instant.now()` + `ChronoUnit` object allocations on every player login check with primitive `long` millisecond timestamps (`System.currentTimeMillis()`).
- **`ChatMessageCache`**: Direct `playerToServer.get(reportedPlayerUuid)` lookup before scanning server deques, and replaced Java Stream pipelines with direct iteration.
- **`ChatCommandLogService` & `BufferedLogChannel`**: Bounded in-memory chat/command queues at 10,000 entries (`ArrayDeque`) to prevent unbounded heap growth if the backend is temporarily offline, and merged `filterByUsername` + `sanitize` into a single pass.

---

## Self-Hosting with `modl-go`

`modl-plugin-lite` adds direct `api-url` support in `boot.yml` (in addition to `-Dmodl.api.url` and `MODL_API_URL` environment variables):

```yaml
# plugins/modl/boot.yml
mode: "standalone" # or "proxy" / "bridge"
api-key: "modl_server_..."
api-url: "http://127.0.0.1:8080"
```

---

## Building from Source

Requires Java 21+ (compiled targeting Java 8 bytecode for universal server compatibility):

```bash
./gradlew test verifyLocaleParity :distribution:shadowJar
```

The unified multi-platform plugin JAR (Spigot / Paper / Folia / Velocity / BungeeCord) is output to:

```text
distribution/build/libs/modl-2.3.2.jar
```

---

## Credits & License

- **Original Author & Upstream Project**: **[modl.gg](https://github.com/modl-gg)** — [`modl-gg/plugin`](https://github.com/modl-gg/plugin)
- **Companion Lightweight Backend**: **[modl-go](https://github.com/MahanYTT/modl-go)**
- **License**: Licensed under the **GNU Affero General Public License v3.0 ([AGPL-3.0](./LICENSE))**, preserving the upstream license and freedom requirements.

