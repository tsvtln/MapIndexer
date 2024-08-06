package xyz.skyfalls;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import xyz.skyfalls.shared.utils.ChunkUtils;
import xyz.skyfalls.shared.IndexCache;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ShopSignOverlay{
    private static boolean enabled=false;
    private static long lastUpdate;
    private final static Map<BlockPos, BoxOverlayRenderer.Style> overlays = new HashMap<>();

    public static boolean toggle() {
        if(enabled){
        	overlays.clear();
        }
        enabled = !enabled;
        return enabled;
    }

    private static boolean shouldEnable() {
        return enabled || DeletionManager.isEnabled();
    }

    public static void onEndTick(ClientWorld world) {
        if (!MapIndexerClient.isOnS26() || !shouldEnable()) {
            return;
        }
        // TODO: rate-limit because getLoadedBlockEntities is probably inefficient to run every frame
        if (System.currentTimeMillis() - lastUpdate < Duration.ofSeconds(1).toMillis()) {
            return;
        }
        if (!IndexCache.hasDownloadedIndex(world)) {
            return;
        }

        overlays.clear();
        // not really nullable but easier map makes it easier to chain
        var indexOptional = Optional.of(IndexCache.getIndex(world));
        ChunkUtils.getLoadedBlockEntities().filter(MapIndexerClient::isShopSign)
                .forEach(e -> {
                    // colors: red: doesn't exist purple: should remove yellow: outdated green: less than 1d old
                    var style = indexOptional
                            .map(indexes -> indexes.get(e.getPos()))
                            .map(lastSeen -> {
                                if (lastSeen > Duration.ofDays(1).toSeconds()) {
                                    return BoxOverlayRenderer.Style.YELLOW;
                                }
                                return BoxOverlayRenderer.Style.GREEN;
                            }).orElse(BoxOverlayRenderer.Style.RED);
                    overlays.put(e.getPos(), style);
                });
        indexOptional.ifPresent(indexes -> {
            indexes.keySet().stream()
                    .filter(ChunkUtils::isPosLoaded)
                    .forEach(pos -> {
                        overlays.computeIfAbsent(pos, anyKey -> BoxOverlayRenderer.Style.PURPLE);
                    });
        });
        lastUpdate = System.currentTimeMillis();
    }

	public static Map<BlockPos, BoxOverlayRenderer.Style> getOverlays() {
        if(!shouldEnable()){
            return Map.of();
        }
        return overlays;
	}
}
