package xyz.skyfalls;

import lombok.Getter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import net.minecraft.text.literal.LiteralText;
import net.minecraft.text.style.Style;
import net.minecraft.text.TextColor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.skyfalls.command.Command;
import xyz.skyfalls.shared.IndexCache;
import xyz.skyfalls.shared.api.ApiService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MapIndexerClient implements ClientModInitializer {
    public static final String MODID = "MapIndexer";
    private static final Logger logger = LogManager.getLogger(MODID + "/Main");

    private static final List<String> S26_DOMAIN_LIST = new ArrayList<>(List.of(
        "play.server26.net",
        "ipv4.server26.net",
        "ipv6.server26.net",
        "play.server26.eu",
        "ipv4.server26.eu",
        "ipv6.server26.eu"));

    private static final Text SHOP = new LiteralText("[SHOP]")
        .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x00FF00)));

    @Getter
    private static String apiBase;

    @Getter
    private static boolean isDevMode;

    @Getter
    private static boolean isOnS26;

    @Override
    public void onInitializeClient() {
        apiBase = System.getProperty("mapindexer.apibase", "https://api.map26.skyfalls.xyz/v1");
        isDevMode = Boolean.getBoolean("mapindexer.dev");
        logger.info("""
            =================
            MapIndexer:
                ApiBase: {}
                DevMode: {}
            =================
            """, apiBase, isDevMode);

        if (isDevMode) {
            S26_DOMAIN_LIST.add("dev.server26.net");
        }

        try {
            Config.load();
        } catch (IOException e) {
            logger.error("Failed to load configuration file", e);
        }

        WorldRenderEvents.LAST.register(BoxOverlayRenderer::onLast);
        ClientTickEvents.END_WORLD_TICK.register(ShopSignOverlay::onEndTick);
        ClientChunkEvents.CHUNK_LOAD.register(SignScanner::scanSignsInChunk);
        ClientPreAttackCallback.EVENT.register(DeletionManager::handleInteract);
        WorldRenderEvents.LAST.register(DeletionManager::render);
        ClientCommandRegistrationCallback.EVENT.register(Command::register);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ApiService.getInstance().resetFailedAuthentication();
            DeletionManager.reset();
            DeletionManager.setEnabled(false);
            IndexCache.expireAll();
        });

        RpcServer.serve();
    }

    public static void sendChatMessage(Text msg) {
        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(msg);
    }

    public static void setIsOnS26(ServerInfo serverInfo) {
        if (serverInfo == null && isDevMode) {
            // serverInfo will be null on single player
            isOnS26 = true;
            return;
        }
        isOnS26 = serverInfo != null && !serverInfo.isLocal()
                && S26_DOMAIN_LIST.contains(serverInfo.address.toLowerCase());
    }

    public static boolean isShopSign(BlockEntity block) {
        if (block instanceof SignBlockEntity sign) {
            var front = sign.getFrontText().getMessages(false);
            var back = sign.getBackText().getMessages(false);
            return front.length == 4 && back.length == 4 && front[0].equals(SHOP);
        }
        return false;
    }
}
