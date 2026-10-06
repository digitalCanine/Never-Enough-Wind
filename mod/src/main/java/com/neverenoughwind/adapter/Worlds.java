package com.neverenoughwind.adapter;

import com.neverenoughwind.NeverEnoughWind;
import com.neverenoughwind.dao.WorldDao;
import com.neverenoughwind.mixin.BiomeAccessAccessor;
import com.neverenoughwind.model.World;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Locale;
import java.util.Optional;

// reads which world the client is in
public final class Worlds {
    // the subserver we know we're on. portals keep the connection, switching subservers makes a new one, so once its known it holds until the next join
    private static String server;

    private Worlds() {}

    public static boolean onMinewind() {
        ServerInfo info = MinecraftClient.getInstance().getCurrentServerEntry();
        return info != null && info.address != null && info.address.toLowerCase(Locale.ROOT).contains("minewind");
    }

    // null when not on minewind or the world isnt in the data
    public static World current() {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null || NeverEnoughWind.data() == null || !onMinewind()) return null;
        WorldDao dao = NeverEnoughWind.data().worlds();
        long seed = seed(world);
        String dim = dimension(world);
        if (!dao.shared(seed, dim)) {
            World only = dao.find(seed, dim).orElse(null);
            // a world only one subserver has tells us where we are
            if (only != null) server = only.server();
            return only;
        }
        Optional<World> known = dao.onServer(seed, dim, server);
        return known.isPresent() ? known.get() : dao.find(seed, dim).orElse(null);
    }

    // true when the name is the fallback for a shared seed and nothing has settled it
    public static boolean guessing() {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null || NeverEnoughWind.data() == null) return false;
        WorldDao dao = NeverEnoughWind.data().worlds();
        return dao.shared(seed(world), dimension(world)) && dao.onServer(seed(world), dimension(world), server).isEmpty();
    }

    // true in that dimension on minewind, whatever the seed
    public static boolean in(String dimension) {
        ClientWorld world = MinecraftClient.getInstance().world;
        return world != null && onMinewind() && dimension(world).equals(dimension);
    }

    // "seed dimension" of the current world, for the debug line
    public static String raw() {
        ClientWorld world = MinecraftClient.getInstance().world;
        return world == null ? "" : seed(world) + " " + dimension(world);
    }

    // a new connection to a subserver, forget what we knew
    public static void onJoin() {
        server = null;
    }

    // every spawn point packet, not just the last one: a stale one from the previous world often arrives after the real one
    public static void onSpawnPoint(BlockPos pos) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null || pos == null || NeverEnoughWind.data() == null) return;
        NeverEnoughWind.data().worlds().bySpawn(seed(world), new int[]{pos.getX(), pos.getY(), pos.getZ()})
                .ifPresent(match -> server = match.server());
    }

    private static long seed(ClientWorld world) {
        return ((BiomeAccessAccessor) world.getBiomeAccess()).new$seed();
    }

    private static String dimension(ClientWorld world) {
        return world.getRegistryKey().getValue().toString();
    }
}
