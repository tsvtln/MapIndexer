package xyz.skyfalls.mixin.client;

import net.minecraft.client.network.ClientLoginNetworkHandler;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.network.packet.s2c.login.LoginSuccessS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.skyfalls.MapIndexerClient;
import xyz.skyfalls.RpcServer;
import xyz.skyfalls.shared.api.ApiService;

@Mixin(ClientLoginNetworkHandler.class)
public class ClientLoginNetworkHandlerMixin {
    @Shadow
    private ServerInfo serverInfo;

    @Inject(at = @At("HEAD"), method = "onSuccess")
    public void onSuccess(LoginSuccessS2CPacket packet, CallbackInfo ci) {
        MapIndexerClient.setIsOnS26(serverInfo);
        ApiService.getInstance().authenticateIfNeeded();
        if(MapIndexerClient.isOnS26()) {
        	RpcServer.warnIfNotRunning();
        }
    }
}
