package xyz.skyfalls.shared.abstraction;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.village.TradeOffer;

@Getter
@EqualsAndHashCode(callSuper = true)
public class OfferBox extends Offer {
    private final static String KEY = "BlockEntityTag";

    OfferBox(TradeOffer offer) {
        super(Type.BOX, offer);
        var inventory = DefaultedList.ofSize(27, ItemStack.EMPTY);
        var sellNbt = offer.getSellItem().getOrCreateNbt();
        if (sellNbt != null && sellNbt.contains(KEY)) {
            Inventories.readNbt(sellNbt.getCompound("BlockEntityTag"), inventory);
        }
        sellList = inventory.stream().map(Item::new).toList();
    }
}
