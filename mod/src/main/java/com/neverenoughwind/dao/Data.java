package com.neverenoughwind.dao;

// everything the mod ships, loaded once at startup
public record Data(EssenceDao essences, PriceDao prices, AuctionItemDao auctionItems, ItemRuleDao itemRules, WorldDao worlds, ChatPatternDao chat) {
    public static Data load() {
        return new Data(EssenceDao.load(), PriceDao.load(), AuctionItemDao.load(), ItemRuleDao.load(), WorldDao.load(), ChatPatternDao.load());
    }
}
