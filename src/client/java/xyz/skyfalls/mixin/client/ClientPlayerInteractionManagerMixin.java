package xyz.skyfalls.mixin.client;

import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.text.*;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.skyfalls.MapIndexerClient;
import xyz.skyfalls.TrackerOverlay;
import xyz.skyfalls.shared.InteractionManager;
import xyz.skyfalls.shared.exceptions.InvalidInteractionStateException;

@Mixin(ClientPlayerInteractionManager.class)
abstract class ClientPlayerInteractionManagerMixin implements IClientPlayerInteractionManager {

    private final static Text SHOP = new LiteralText("[SHOP]")
            .setStyle(Style.EMPTY.withColor(TextColor.parse("dark_green")));

    private final static Text MSG_FAILED = new LiteralText("Cant open new shop because the last interaction hasn't finished nor failed.")
            .setStyle(Style.EMPTY.withColor(TextColor.parse("red")));

    @Inject(at = @At("HEAD"), method = "interactBlock", cancellable = true)
    public void interactBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        if (!MapIndexerClient.isOnS26()) {
            return;
        }
        var pos = hitResult.getBlockPos();
        var block = MinecraftClient.getInstance().world.getBlockEntity(pos);
        TrackerOverlay.clear(pos,MinecraftClient.getInstance().world);
        if (block instanceof SignBlockEntity sign) {
            var front = sign.getFrontText().getMessages(false);
            var back = sign.getBackText().getMessages(false);
            if (front.length < 4 || back.length < 4) {
                return;
            }
            if (!front[0].equals(SHOP)) {
                return;
            }
            var shopName = front[1].getString();
            var ownerName = front[2].getString();
            var dimension = MinecraftClient.getInstance().world.getDimensionEntry()
                    .getKey().get().getValue().getPath();
            try {
                InteractionManager.getInstance().startInteraction(hitResult, block,dimension, ownerName, shopName);
            } catch (InvalidInteractionStateException e) {
                MapIndexerClient.sendChatMessage(MSG_FAILED);
                cir.setReturnValue(ActionResult.CONSUME);
                cir.cancel();
            }
        }
    }
}