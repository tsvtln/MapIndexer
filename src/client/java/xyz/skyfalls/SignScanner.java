package xyz.skyfalls;

import com.google.common.util.concurrent.Futures;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.NetworkUtils;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.skyfalls.shared.IndexCache;
import xyz.skyfalls.shared.abstraction.FreeBlockPos;
import xyz.skyfalls.shared.api.ApiService;
import xyz.skyfalls.shared.api.BlockStateReport;
import xyz.skyfalls.shared.utils.RegistryUtils;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SignScanner {
    private final static Logger logger = LogManager.getLogger(MapIndexerClient.MODID + "/Scanner");
    private static Set<BlockPos> reported = new HashSet<>();

    private static ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    //TODO: static Set<BlockPos> noticedSigns = new HashSet<>();
    public static void scanSignsInChunk(ClientWorld world, WorldChunk chunk) {
        if (!MapIndexerClient.isOnS26()) {
            return;
        }
        var blockEntities = chunk.getBlockEntities().values();
        executor.schedule(() -> {
            Futures.transform(IndexCache.getIndexOrDownload(world), (ignored) -> {
                if (!IndexCache.hasDownloadedIndex(world)) {
                    throw new RuntimeException("Sanity check failed. Listener called when index hasn't been downloaded.");
                }
                var chunkPos = chunk.getPos();
                // skip if not loaded
                if (!world.getChunkManager().isChunkLoaded(chunkPos.x, chunkPos.z)) {
                    return null;
                }
                var index = Objects.requireNonNull(IndexCache.getIndex(world));
                var signsInChunk = blockEntities.stream()
                        .filter(MapIndexerClient::isShopSign)
                        .map(BlockEntity::getPos).toList();

                var shouldRemove = index.keySet().stream()
                        .filter(e -> chunk.equals(world.getWorldChunk(e)))
                        .filter(e -> !signsInChunk.contains(e))
                        //.filter(e -> !(chunk.getBlockState(e).getBlock() instanceof AbstractSignBlock))
                        .filter(Predicate.not(reported::contains))
                        .collect(Collectors.toSet());

                if (!shouldRemove.isEmpty()) {
                    //logger.info("remove is not empty, blockEntities={}, signs={}", blockEntities.size(), signsInChunk.size());
                    String dimension = RegistryUtils.toString(world.getDimensionEntry());
                    ApiService.getInstance().authenticateIfNeeded().addListener(() -> {
                        var reports = shouldRemove.stream().map(e -> new BlockStateReport(FreeBlockPos.of(e), dimension,
                                BlockStateReport.Reason.BLOCK_CHANGED,
                                RegistryUtils.toString(chunk.getBlockState(e).getBlock().asItem().getRegistryEntry()),
                                false)).toList();
                        logger.info("Reporting {} removals in chunk {} dim {}", shouldRemove.size(), chunk.getPos(), dimension);
                        try {
                            ApiService.getInstance().makeReport(reports);
                            reported.addAll(shouldRemove);
                        } catch (Exception e) {
                            logger.info("Failed to report", e);
                        }
                    }, NetworkUtils.EXECUTOR);
                }
                return ignored;
            }, MinecraftClient.getInstance());
            // sends chunk data
            // sends block entity data whenever it wants
            // doesn't provide synchronization markers
            // refuses to elaborate further
            // get fucked
        }, 30, TimeUnit.SECONDS);
    }
}
