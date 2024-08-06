package xyz.skyfalls.shared.abstraction;

import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.village.TradeOfferList;

import java.util.ArrayList;

public class OffersList extends ArrayList<Offer> {
    OffersList(int size) {
        super(size);
    }

    public static OffersList from(TradeOfferList offers) {
        OffersList list = new OffersList(offers.size());
        offers.forEach(e -> {
            if (e.getSellItem().getItem() instanceof BlockItem bi) {
                if (bi.getBlock() instanceof ShulkerBoxBlock) {
                    list.add(new OfferBox(e));
                    return;
                }
            }
            list.add(new OfferNormal(e));
        });
        return list;
    }
}
