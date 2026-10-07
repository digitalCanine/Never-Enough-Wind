package com.neverenoughwind.parse;

import com.neverenoughwind.model.AuctionItem;
import com.neverenoughwind.model.NameStyle;

import java.util.List;

// what the parser worked out for one item. category is null for plain items.
// restoreStyle is only set when the name should be repainted. date = the "Summer 2017" line of its lore, null without one
public record ItemInfo(
        String category,
        String detail,
        List<EssenceEntry> essences,
        List<Soul> souls,
        List<String> flavor,
        AuctionItem auction,
        boolean renamed,
        NameStyle restoreStyle,
        Integer sharpness,
        String date) {

    public static final ItemInfo NONE = new ItemInfo(null, null, List.of(), List.of(), List.of(), null, false, null, null, null);

    public boolean isEmpty() {
        return category == null;
    }
}
