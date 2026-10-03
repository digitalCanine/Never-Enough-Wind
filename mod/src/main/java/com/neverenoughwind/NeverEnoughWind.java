package com.neverenoughwind;

import com.neverenoughwind.dao.Data;
import com.neverenoughwind.adapter.Chat;
import com.neverenoughwind.feature.NameColor;
import com.neverenoughwind.feature.hud.PartyWidget;
import com.neverenoughwind.feature.hud.SubserverIndicator;
import com.neverenoughwind.parse.ItemParser;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NeverEnoughWind implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("neverenoughwind");

    private static Data data;
    private static ItemParser parser;

    public static Data data() {
        return data;
    }

    // null when the data didnt load, features check for that
    public static ItemParser parser() {
        return parser;
    }

    @Override
    public void onInitializeClient() {
        try {
            data = Data.load();
            parser = new ItemParser(data);
            NameColor.register();
            SubserverIndicator.register();
            Chat.register();
            PartyWidget.register();
            LOG.info("data loaded: {} essences, {} auction items, {} item types",
                    data.essences().all().size(), data.auctionItems().all().size(), data.itemRules().types().size());
        } catch (RuntimeException e) {
            // never take the game down over a data file
            LOG.error("could not load the data files, the mod stays off", e);
        }
    }
}
