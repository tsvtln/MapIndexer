package xyz.skyfalls.shared.abstraction;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.minecraft.item.Items;
import net.minecraft.village.TradeOffer;
import org.jetbrains.annotations.Nullable;
import net.minecraft.item.ItemStack;

import java.util.List;

@Getter
@EqualsAndHashCode
public abstract class Offer {
    // Fields for Gson serialization/deserialization
    private Type type;
    private boolean isInStock;
    private Item buy1;
    @Nullable
    private Item buy2;
    private List<Item> sellList;

    // Constructor to initialize fields from TradeOffer
    Offer(Type type, TradeOffer offer) {
        this.type = type;
        this.isInStock = !offer.isDisabled();
        this.buy1 = new Item(offer.getOriginalFirstBuyItem());
        
        // Handle the second buy item if present and not AIR
        if (offer.getSecondBuyItem().isPresent() && offer.getSecondBuyItem().get().getItem() != Items.AIR) {
            this.buy2 = new Item(offer.getSecondBuyItem().get().getItem());
        }
        
        // Initialize sellList with items from TradeOffer
        this.sellList = offer.getSellItemList().stream()
                .map(ItemStack::new)
                .map(Item::new)
                .toList();
    }

    enum Type {
        ITEM, BOX
    }
}
