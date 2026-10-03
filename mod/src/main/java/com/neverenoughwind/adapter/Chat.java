package com.neverenoughwind.adapter;

import com.neverenoughwind.NeverEnoughWind;
import com.neverenoughwind.parse.ChatMatch;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// one place where chat comes in. every line is matched once, then handed to whoever listens
public final class Chat {
    private static final List<Consumer<ChatMatch>> listeners = new ArrayList<>();

    private Chat() {}

    public static void listen(Consumer<ChatMatch> listener) {
        listeners.add(listener);
    }

    public static void register() {
        // minewind sends everything as game messages. overlay = action bar, not chat
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay || NeverEnoughWind.data() == null || !Worlds.onMinewind()) return;
            NeverEnoughWind.data().chat().match(message.getString()).ifPresent(match -> {
                for (Consumer<ChatMatch> l : listeners) {
                    try {
                        l.accept(match);
                    } catch (RuntimeException e) {
                        NeverEnoughWind.LOG.error("chat listener failed on {}", match.id(), e);
                    }
                }
            });
        });
    }
}
