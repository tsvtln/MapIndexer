package xyz.skyfalls.shared.abstraction;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;

@Getter
@EqualsAndHashCode(callSuper = true)
public class OfferNormal extends Offer {
        public OfferNormal(net.minecraft.village.TradeOffer e) {
        super(Type.ITEM,e);
        this.sellList = List.of(new Item(e.getSellItem()));
    }
}
