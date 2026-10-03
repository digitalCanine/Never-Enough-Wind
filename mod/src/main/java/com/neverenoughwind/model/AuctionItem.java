package com.neverenoughwind.model;

import java.util.List;

// style is null for wiki entries, item is null when only the type is known
public record AuctionItem(
        String name,
        String item,
        String type,
        List<String> flavor,
        NameStyle style,
        Integer sharpness,
        boolean needsItemId,
        String status) {

    // axe, hoe, sword... the part after the material
    public String toolType() {
        return type != null ? type : toolTypeOf(item);
    }

    public static String toolTypeOf(String itemId) {
        if (itemId == null) return null;
        String path = itemId.substring(itemId.indexOf(':') + 1);
        return path.substring(path.lastIndexOf('_') + 1);
    }
}
