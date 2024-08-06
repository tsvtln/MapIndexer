package xyz.skyfalls.shared;

import com.google.common.util.concurrent.Futures;
import com.google.gson.Gson;
import lombok.Getter;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.NetworkUtils;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.skyfalls.MapIndexerClient;
import xyz.skyfalls.mixin.client.IClientPlayerInteractionManager;
import xyz.skyfalls.shared.abstraction.FreeBlockPos;
import xyz.skyfalls.shared.abstraction.OffersList;
import xyz.skyfalls.shared.api.ApiService;
import xyz.skyfalls.shared.api.BlockStateReport;
import xyz.skyfalls.shared.api.Submission;
import xyz.skyfalls.shared.exceptions.ApiException;
import xyz.skyfalls.shared.exceptions.InvalidInteractionStateException;
import xyz.skyfalls.shared.exceptions.OffersChangedException;
import xyz.skyfalls.shared.utils.RegistryUtils;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class InteractionManager {
    private static final Logger logger = LogManager.getLogger(MapIndexerClient.MODID + "/Interact");

    private static final Text MSG_FAILED_CODE = new LiteralText("Submission failed due to API error: ")
            .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF0000)));

    private static final Text MSG_FAILED_UNKNOWN = new LiteralText("Submission failed due to network error.")
            .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF0000)));

    private static final Text MSG_SUBMITTED = new LiteralText("Shop information submitted.")
            .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x00FF00)));

    private static InteractionManager instance;

    private final Gson gson = new Gson();
    @Getter
    private State state = State.IDLE;
    private BlockHitResult hitResult;
    private BlockPos position;
    private BlockEntity block;
    private String dimension;
    private String owner;
    private String shop;
    private OffersList offers;
    private Instant timeout;

    public void startInteraction(BlockHitResult hitResult, BlockEntity block, String dimension, String ownerName, String shopName) throws InvalidInteractionStateException {
        if (state != State.IDLE) {
            if (!Instant.now().isAfter(timeout)) {
                throw new InvalidInteractionStateException(state);
            }
            if (state != State.VERIFIED) {
                var report = new BlockStateReport(
                        FreeBlockPos.of(position),
                        dimension,
                        BlockStateReport.Reason.INTERACTION_FAILED,
                        RegistryUtils.toString(block.getWorld().getBlockState(position).getBlock().asItem().getRegistryEntry()),
                        true
                );
                Futures.transform(ApiService.getInstance().authenticateIfNeeded(), ignored -> {
                    try {
                        ApiService.getInstance().makeReport(List.of(report));
                    } catch (ApiException | IOException | InterruptedException e) {
                        logger.info("Failed to report", e);
                    }
                    return ignored;
                }, NetworkUtils.EXECUTOR);
                logger.info("Last interaction timed out");
            }
        }
        state = State.INTERACTED_FIRST;
        this.hitResult = hitResult;
        this.block = block;
        this.position = hitResult.getBlockPos();
        this.dimension = dimension;
        this.owner = ownerName;
        this.shop = shopName;
        logger.info("Interacted with shop: \"{}\" owned by {} at {}", shopName, ownerName, position);
        this.timeout = Instant.now().plus(Duration.ofSeconds(1));
    }

    /**
     * @param syncId
     * @param offers New trade offers from the server
     * @return isSecondScreen
     */
    public boolean setOffers(int syncId, OffersList offers) throws OffersChangedException, InvalidInteractionStateException {
        switch (state) {
            case INTERACTED_FIRST -> {
                state = State.OPENED;
                this.offers = offers;
                logger.info("Got offers first time: {}", gson.toJson(offers));
                startVerification(syncId);
                return false;
            }
            case INTERACTED_AGAIN -> {
                if (!this.offers.equals(offers)) {
                    logger.error("Offers changed, new offer: {}", gson.toJson(offers));
                    reset();
                    throw new OffersChangedException();
                }
                state = State.VERIFIED;
                logger.info("Verified offers, submitting...");
                submit();
                return true;
            }
            default -> throw new InvalidInteractionStateException(state);
        }
    }

    private void startVerification(int syncId) {
        var client = MinecraftClient.getInstance();
        if (client.interactionManager instanceof IClientPlayerInteractionManager network) {
            network.callSendSequencedPacket(client.world, i -> new CloseHandledScreenC2SPacket(syncId));
            network.callSendSequencedPacket(client.world, i ->
                    new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hitResult, i));
            state = State.INTERACTED_AGAIN;
        }
    }

    private void submit() {
        Submission submission = new Submission(FreeBlockPos.of(position), dimension, owner, offers);
        NetworkUtils.EXECUTOR.execute(() -> {
            try {
                ApiService.getInstance().submit(submission);
                IndexCache.getIndex(submission.dimension()).put(position, 0L);
                MapIndexerClient.sendChatMessage(MSG_SUBMITTED);
            } catch (InterruptedException | IOException e) {
                logger.error("Network error during submit", e);
                MapIndexerClient.sendChatMessage(MSG_FAILED_UNKNOWN);
            } catch (ApiException e) {
                logger.error("Api error during submit", e);
                MapIndexerClient.sendChatMessage(MSG_FAILED_CODE.copy()
                        .append(Text.literal(Integer.toString(e.getCode()))
                                .append(" ")
                                .append(Text.literal(e.getBody()))));
            }
        });
    }

    public void reset() {
        state = State.IDLE;
    }

    public static synchronized InteractionManager getInstance() {
        if (instance == null) {
            instance = new InteractionManager();
        }
        return instance;
    }

    public enum State {
        IDLE, INTERACTED_FIRST, OPENED, INTERACTED_AGAIN, VERIFIED
    }
}
