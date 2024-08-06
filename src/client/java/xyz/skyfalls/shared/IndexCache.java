package xyz.skyfalls.shared;

import com.google.common.util.concurrent.ListenableFuture;
import net.minecraft.util.NetworkUtils;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.*;
import net.minecraft.util.math.BlockPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import xyz.skyfalls.MapIndexerClient;
import xyz.skyfalls.shared.api.ApiService;
import xyz.skyfalls.shared.exceptions.ApiException;
import xyz.skyfalls.shared.utils.RegistryUtils;


import net.minecraft.network.chat.Text;
import net.minecraft.network.chat.literal.LiteralText;
import net.minecraft.network.chat.style.Style;
import net.minecraft.network.chat.TextColor;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class IndexCache {
    private final static Logger logger = LogManager.getLogger(MapIndexerClient.MODID + "/IdxCache");
    //final static Text MSG_INDEXES_FAIL = new LiteralText(
                           // "Failed to download existing indexes. To try again, please leave and join the server again. Check log for details.")
            //.setStyle(Style.EMPTY.withColor(TextColor.parse("red")));
	public class IndexCache {
    final static Text MSG_INDEXES_FAIL;

    static {
        MSG_INDEXES_FAIL = new LiteralText("Failed to index some items.")
            .setStyle(Style.EMPTY.withColor(TextColor.parse("red").result().orElse(TextColor.fromRgb(0xFF0000))));
    }}
    /**
     * The index contains (pos->ageSeconds) of the known shop location
     **/
    private static final Map<String, Map<BlockPos, Long>> indexes = new HashMap<>();

    private static final Map<String, Instant> requestTime = new HashMap<>();
    private static final Map<String, ListenableFuture<?>> futures = Collections.synchronizedMap(new HashMap<>());

    public static @NotNull Map<BlockPos, Long> getIndex(ClientWorld world) {
        return getIndex(RegistryUtils.toString(world.getDimensionEntry()));
    }

    public static @NotNull Map<BlockPos, Long> getIndex(String dimension) {
        synchronized (indexes) {
            return indexes.computeIfAbsent(dimension, e -> new HashMap<>());
        }
    }

    public static boolean hasDownloadedIndex(ClientWorld world) {
        return requestTime.containsKey(RegistryUtils.toString(world.getDimensionEntry()));
    }

    public static ListenableFuture<?> getIndexOrDownload(ClientWorld world) {
        return getIndexOrDownload(RegistryUtils.toString(world.getDimensionEntry()));
    }

    /**
     * @return a {@link ListenableFuture} that can be already completed
     */
    public static synchronized ListenableFuture<?> getIndexOrDownload(String dimension) {
        // if already downloaded or downloading
        var future = futures.get(dimension);
        if (future != null) {
            if (requestTime.containsKey(dimension)
                    && requestTime.get(dimension).isAfter(Instant.now().minus(Duration.ofHours(1)))) {
                return future;
            }
            // if expired or failed
            logger.info("Returned stale index for {}, updating...", dimension);
        }
        future = NetworkUtils.EXECUTOR.submit(() -> {
            updateIndexBlocking(dimension);
        });
        requestTime.put(dimension, Instant.now());
        futures.put(dimension, future);
        return future;
    }

    public static void expireAll() {
        requestTime.replaceAll((k, v) -> Instant.EPOCH);
    }

    private static void updateIndexBlocking(String dimension) {
        try {
            var index = ApiService.getInstance().downloadIndex(dimension);
            mergeIndex(dimension, index);
            logger.info("Updated indexes for " + dimension);
        } catch (IOException | InterruptedException | ApiException e) {
            logger.error("Failed to download indexes", e);
            MapIndexerClient.sendChatMessage(MSG_INDEXES_FAIL);
            throw new RuntimeException(e); // so that it fails
        }
    }

    private static void mergeIndex(String dimension, Map<BlockPos, Long> imported) {
        var existing = getIndex(dimension);
        imported.forEach((k, v) -> existing.merge(k, v, Math::min));
    }

    public static String getDebugInformation() {
        var sb = new StringBuilder();
        sb.append("§dIndexCache:§r\n");
        Stream.concat(requestTime.keySet().stream(), indexes.keySet().stream())
                .distinct()
                .forEach(dim -> {
                    sb.append("    ");
                    sb.append(dim);
                    sb.append(": ");
                    if (indexes.containsKey(dim)) {
                        sb.append("size=").append(indexes.get(dim).size()).append(", ");
                    }
                    if (requestTime.containsKey(dim)) {
                        sb.append("§b(%s)§r".formatted(LocalDateTime.ofInstant(requestTime.get(dim), ZoneOffset.UTC)
                                .atOffset(ZoneOffset.UTC) // https://stackoverflow.com/questions/64988997/java-localdatetime-why-is-unsupported-field-offsetseconds-produced
                                .format(DateTimeFormatter.RFC_1123_DATE_TIME)));
                    } else {
                        sb.append("§e(Offline)§r");
                    }
                    sb.append("\n");
                });
        return sb.toString().trim();
    }

    public static void clearDownloadStatus() {
        futures.clear();
    }
}
