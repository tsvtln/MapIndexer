package xyz.skyfalls.command;

import com.google.gson.Gson;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.skyfalls.*;
import xyz.skyfalls.shared.IndexCache;
import xyz.skyfalls.shared.abstraction.Item;
import xyz.skyfalls.shared.api.ApiService;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class Command {
    private static final Logger logger = LogManager.getLogger(MapIndexerClient.MODID + "/CMD");

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher,
                                CommandRegistryAccess registry) {
        dispatcher.register(literal("mapindexer")
                .executes(context -> {
                    context.getSource().sendFeedback(Text.literal("§bMapApi by IsSkyfalls_§r, version %s"
                            .formatted(xyz.skyfalls.Version.VERSION)));
                    context.getSource().sendFeedback(Text.literal(ApiService.getInstance().getDebugInformation()));
                    context.getSource().sendFeedback(Text.literal(IndexCache.getDebugInformation()));
                    return 0;
                })
                .then(literal("overlay").executes(context -> {
                    boolean enabled = ShopSignOverlay.toggle();
                    if (enabled) {
                        context.getSource().sendFeedback(Text.literal("Enabled overlay"));
                    } else {
                        context.getSource().sendFeedback(Text.literal("Disabled overlay"));
                    }
                    return 0;
                }))
                .then(literal("delete").executes(context -> {
                    boolean enabled = DeletionManager.toggle();
                    if (enabled) {
                        context.getSource().sendFeedback(Text.literal("§aEntered deletion mode. You can delete your own shop from the index by left clicking it twice."));
                        context.getSource().sendFeedback(Text.literal("§6If your shop isn't highlighted, please relog to update your local database."));
                    } else {
                        context.getSource().sendFeedback(Text.literal("§bDisabled deletion mode"));
                    }
                    return 0;
                }))
                .then(literal("link").executes(context -> {
                    context.getSource()
                            .sendFeedback(Text.literal("§e§o§nClick here to activate the browser integration.")
                                    .setStyle(Style.EMPTY.withClickEvent(
                                            new ClickEvent(ClickEvent.Action.OPEN_URL, RpcServer.getLinkUrl()))));
                    context.getSource().sendFeedback(Text.literal("§dIt's not recommended to share this link with anyone else."));
                    return 0;
                }))
                .then(literal("dumpitem").executes(context -> {
                    var item = new Item(MinecraftClient.getInstance().player.getInventory().getMainHandStack());
                    logger.info(new Gson().toJson(item));
                    return 0;
                })));
        dispatcher.register(literal("track")
                .then(argument("pos", ClientBlockPosArgument.blockPos())
                        .executes(context -> {
                            var pos = ClientBlockPosArgument.getBlockPos(context, "pos");
                            var dim = "overworld";
                            TrackerOverlay.track(dim, pos);
                            return 0;
                        })
                        .then(argument("dim", StringArgumentType.string())
                                .executes(context -> {
                                    var pos = ClientBlockPosArgument.getBlockPos(context, "pos");
                                    var dim = StringArgumentType.getString(context, "dim");
                                    TrackerOverlay.track(dim, pos);
                                    return 0;
                                })))
                .then(literal("clear").executes(context -> {
                    TrackerOverlay.clear();
                    return 0;
                })));
    }
}
