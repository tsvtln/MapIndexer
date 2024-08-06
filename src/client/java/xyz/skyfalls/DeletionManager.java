package xyz.skyfalls;

import lombok.Getter;
import lombok.Setter;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.NetworkUtils;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import xyz.skyfalls.shared.IndexCache;
import xyz.skyfalls.shared.abstraction.FreeBlockPos;
import xyz.skyfalls.shared.api.ApiService;
import xyz.skyfalls.shared.api.Deletion;
import xyz.skyfalls.shared.exceptions.ApiException;
import xyz.skyfalls.shared.utils.RegistryUtils;

import java.awt.Color;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class DeletionManager {
    private final static Logger logger = LogManager.getLogger(MapIndexerClient.MODID + "/Deletion");

    @Setter
    @Getter
    private static boolean enabled = false;

    @Nullable
    private static BlockPos selection;
    @Nullable
    private static ClientWorld selectionInWorld;
    private static volatile State state = State.INIT;

    private final static String MSG_SELECTED = "§aSelected shop at §d[%d, %d, %d]§r. §aLeft click again to delete";
    private final static String MSG_DELETING = "§6Deleting shop at §d[%d, %d, %d]";
    private final static String MSG_SUCCESS = "§2Shop Deleted";
    private final static String MSG_FAILED = "§cFailed to delete shop.§r §eReason: %s";

    private final static ExecutorService executor = Executors.newCachedThreadPool();

    public static boolean toggle() {
        reset();
        enabled = !enabled;
        return enabled;
    }

    public static void set(BlockPos pos, ClientWorld world) {
        selection = pos;
        selectionInWorld = world;
        state = State.SELECTED;
        MapIndexerClient.sendChatMessage(Text.literal(MSG_SELECTED.formatted(pos.getX(), pos.getY(), pos.getZ())));
    }

    public static void reset() {
        selection = null;
        selectionInWorld = null;
        state = State.INIT;
    }

    public static boolean handleInteract(MinecraftClient client, ClientPlayerEntity player, int clickCount) {
        if (!enabled || clickCount != 1) {
            return false;
        }
        var lookingAt = getShopInSight(player.getCameraPosVec(client.getTickDelta()), player.getRotationVecClient());
        if (lookingAt != null) {
            handleInteract(lookingAt, client.world);
        } else {
            reset();
            return false;
        }
        return true; // stop propagation
    }

    public static void handleInteract(BlockPos pos, ClientWorld world) {
        if (!enabled) {
            return;
        }
        if (!world.equals(selectionInWorld)) {
            set(pos, world);
            return;
        }
        if (!pos.equals(selection)) {
            set(pos, world);
        } else {
            if (state != State.SELECTED) {
                return;
            }
            MapIndexerClient.sendChatMessage(
                Text.literal(MSG_DELETING.formatted(selection.getX(), selection.getY(), selection.getZ())));
            
            executor.execute(() -> {
                try {
                    ApiService.getInstance().delete(new Deletion(FreeBlockPos.of(selection),
                            RegistryUtils.toString(selectionInWorld.getDimensionEntry())));
                    MapIndexerClient.sendChatMessage(Text.literal(MSG_SUCCESS));
                    IndexCache.getIndex(selectionInWorld).remove(selection);
                } catch (ApiException | IOException | InterruptedException e) {
                    MapIndexerClient.sendChatMessage(Text.literal(MSG_FAILED.formatted(e)));
                    logger.error("Failed to delete shop:", e);
                }
                reset();
            });
        }
    }

    private static BlockPos getShopInSight(Vec3d at, Vec3d rot) {
        var mc = MinecraftClient.getInstance();
        var end = at.add(rot.multiply(8)); // distance
        return BlockView.raycast(at, end, new AtomicInteger(), (context2, pos) -> {
            if (IndexCache.getIndex(mc.world).get(pos) != null) {
                return pos.toImmutable();
            }
            return null; // continue
        }, context2 -> null);
    }

    public static void render(WorldRenderContext context) {
        if (!enabled) {
            return;
        }
        var mc = MinecraftClient.getInstance();
        var player = mc.player;
        var delta = context.tickDelta();
        var start = player.getClientCameraPosVec(delta);
        var buffer = context.consumers().getBuffer(RenderLayer.getLines());
        var cam = context.camera().getPos();
        if (state != State.INIT && context.world().equals(selectionInWorld)) {
            var color = switch (state) {
                case SELECTED -> new Color(1f, 0f, 0f);
                case DELETING -> new Color(1f, 1f, 0f);
                default -> throw new IllegalArgumentException("Unexpected value: " + state);
            };
            WorldRenderer.drawShapeOutline(context.matrixStack(), buffer, VoxelShapes.fullCube(),
                    selection.getX() - cam.getX(), selection.getY() - cam.getY(), selection.getZ() - cam.getZ(),
                    1f, 0.25f, 0.25f, 0.5f, true);
        }
        var hitPos = getShopInSight(start, player.getRotationVecClient());
        if (hitPos != null && !hitPos.equals(selection)) {
            WorldRenderer.drawShapeOutline(context.matrixStack(), buffer, VoxelShapes.fullCube(),
                    hitPos.getX() - cam.getX(), hitPos.getY() - cam.getY(), hitPos.getZ() - cam.getZ(),
                    0f, 0f, 0f, 0.5f, true);
        }
    }

    enum State {
        INIT, SELECTED, DELETING;
    }
}
