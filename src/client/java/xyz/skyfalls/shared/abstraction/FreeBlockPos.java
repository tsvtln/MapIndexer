package xyz.skyfalls.shared.abstraction;

import net.minecraft.util.math.BlockPos;

public record FreeBlockPos(double x, double y, double z) {
    public static FreeBlockPos of(BlockPos pos) {
        return new FreeBlockPos(pos.getX(), pos.getY(), pos.getZ());
    }
}
