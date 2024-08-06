package xyz.skyfalls.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.network.NetworkThreadUtils;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.SetTradeOffersS2CPacket;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.text.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.skyfalls.MapIndexerClient;
import xyz.skyfalls.shared.InteractionManager;
import xyz.skyfalls.shared.abstraction.OffersList;
import xyz.skyfalls.shared.exceptions.InvalidInteractionStateException;
import xyz.skyfalls.shared.exceptions.OffersChangedException;
import net.minecraft.network.chat.Text;
import net.minecraft.network.chat.literal.LiteralText;

import java.util.LinkedHashMap;

@Mixin(net.minecraft.client.network.ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    final Logger logger = LogManager.getLogger(MapIndexerClient.MODID + "/PlayNetwork");

    private final static Text MSG_FAILED_STATE = MutableText.of(
                    new LiteralTextContent("Wrong interaction state."))
            .setStyle(Style.EMPTY.withColor(TextColor.parse("red")));

    private final static Text MSG_FAILED_CHANGED = MutableText.of(
                    new LiteralTextContent("Shop verification failed: Offers changed. Please try again."))
            .setStyle(Style.EMPTY.withColor(TextColor.parse("red")));

    LinkedHashMap<Integer, Text> windowNameCache = new LinkedHashMap<>();

    @Inject(at = @At("HEAD"), method = "onOpenScreen", cancellable = true)
    public void onOpenScreen(OpenScreenS2CPacket packet, CallbackInfo ci) {
        if (!MapIndexerClient.isOnS26()) {
            return;
        }
        if (packet.getScreenHandlerType().equals(ScreenHandlerType.MERCHANT)) {
            windowNameCache.put(packet.getSyncId(), packet.getName());
            if (windowNameCache.size() > 10) {
                windowNameCache.remove(windowNameCache.entrySet().iterator().next().getKey());
            }
            if (InteractionManager.getInstance().getState() == InteractionManager.State.INTERACTED_AGAIN) {
                ci.cancel();
            }
        }
    }

    @Inject(at = @At("HEAD"), method = "onSetTradeOffers", cancellable = true)
    public void onSetTradeOffers(SetTradeOffersS2CPacket packet, CallbackInfo ci) {
        if (!MapIndexerClient.isOnS26()) {
            return;
        }
        var client = MinecraftClient.getInstance();
        NetworkThreadUtils.forceMainThread((Packet) packet, (PacketListener) this, client);
        try {
            if (InteractionManager.getInstance().setOffers(packet.getSyncId(), OffersList.from(packet.getOffers()))) {
                HandledScreens.open(ScreenHandlerType.MERCHANT, client, packet.getSyncId(),
                        windowNameCache.getOrDefault(packet.getSyncId(), Text.of("ERROR: UNCACHED")));
            }
        } catch (OffersChangedException e) {
            MapIndexerClient.sendChatMessage(MSG_FAILED_CHANGED);
        } catch (InvalidInteractionStateException e) {
            if (e.getState() != InteractionManager.State.IDLE) {
                MapIndexerClient.sendChatMessage(MSG_FAILED_STATE);
            }
        }
    }
}