package xyz.skyfalls.shared.api;

import lombok.AllArgsConstructor;
import xyz.skyfalls.shared.abstraction.FreeBlockPos;

@AllArgsConstructor
public class BlockStateReport {
    FreeBlockPos position;
    String dimension;
    BlockStateReport.Reason reason;
    String blockType;
    boolean isShopSign;

    public enum Reason {
        INTERACTION_FAILED, BLOCK_CHANGED
    }
}
