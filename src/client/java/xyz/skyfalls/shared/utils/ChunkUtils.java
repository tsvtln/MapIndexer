package xyz.skyfalls.shared.utils;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Objects;
import java.util.stream.Stream;

public class ChunkUtils {
    public static boolean isPosLoaded(BlockPos pos) {
        if (MinecraftClient.getInstance().world == null) {
            return false;
        }
        return !MinecraftClient.getInstance().world.getWorldChunk(pos).isEmpty();
    }

    public static Stream<BlockEntity> getLoadedBlockEntities() {
        return getLoadedChunks()
                .flatMap(chunk -> chunk.getBlockEntities().values().stream());
    }

    public static Stream<WorldChunk> getLoadedChunks() {
        MinecraftClient client = MinecraftClient.getInstance();
        int radius = Math.max(2, client.options.getClampedViewDistance()) + 3;
        int diameter = radius * 2 + 1;

        ChunkPos center = client.player.getChunkPos();
        ChunkPos min = new ChunkPos(center.x - radius, center.z - radius);
        ChunkPos max = new ChunkPos(center.x + radius, center.z + radius);


        // TODO: investigate ChunkSectionPos
        Stream<WorldChunk> stream = Stream.<ChunkPos>iterate(min, pos -> {

                    int x = pos.x;
                    int z = pos.z;

                    x++;

                    if (x > max.x) {
                        x = min.x;
                        z++;
                    }

                    if (z > max.z)
                        throw new IllegalStateException("Stream limit didn't work.");

                    return new ChunkPos(x, z);

                }).limit((long) diameter * diameter)
                .filter(c -> client.world.isChunkLoaded(c.x, c.z))
                .map(c -> client.world.getChunk(c.x, c.z)).filter(Objects::nonNull);

        return stream;
    }

}