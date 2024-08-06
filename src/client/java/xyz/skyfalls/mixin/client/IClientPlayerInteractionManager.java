package xyz.skyfalls.mixin.client;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientPlayerInteractionManager.class)
public interface IClientPlayerInteractionManager {
    @Invoker("sendSequencedPacket")
    public void callSendSequencedPacket(ClientWorld world, SequencedPacketCreator packetCreator);
}
