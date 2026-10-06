package com.neverenoughwind.feature;

import com.neverenoughwind.adapter.Worlds;
import com.neverenoughwind.config.Config;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoubleBlockProperties;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;

// marks the labyrinth chests the player opened themselves. nothing about chests they didnt touch
public final class Labyrinth {
    private static final String DIMENSION = "minecraft:lab";
    // ticks a click has to turn into an open chest screen
    private static final int WAIT = 20;
    private static final int RANGE = 48;
    // keeps our line off the vanilla block outline
    private static final double GROW = 0.005;

    private static final Set<BlockPos> opened = new HashSet<>();
    private static BlockPos clicked;
    private static int waiting;

    private Labyrinth() {}

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient() && Worlds.in(DIMENSION) && world.getBlockState(hit.getBlockPos()).getBlock() instanceof ChestBlock) {
                clicked = hit.getBlockPos().toImmutable();
                waiting = WAIT;
            }
            return ActionResult.PASS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(Labyrinth::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(Labyrinth::render);
    }

    private static void tick(MinecraftClient mc) {
        if (!Worlds.in(DIMENSION)) {
            opened.clear();
            clicked = null;
            return;
        }
        if (clicked == null) return;
        // only a chest that really opened counts
        if (mc.currentScreen instanceof GenericContainerScreen) {
            mark(mc.world, clicked);
            clicked = null;
        } else if (--waiting <= 0) {
            clicked = null;
        }
    }

    private static void mark(ClientWorld world, BlockPos pos) {
        opened.add(pos);
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && ChestBlock.getDoubleBlockType(state) != DoubleBlockProperties.Type.SINGLE) {
            opened.add(pos.offset(ChestBlock.getFacing(state)));
        }
    }

    private static void render(WorldRenderContext ctx) {
        Config config = Config.get();
        if (opened.isEmpty() || !config.labyrinthMarks || ctx.matrixStack() == null || ctx.consumers() == null) return;
        ClientWorld world = ctx.world();
        Vec3d cam = ctx.camera().getPos();
        VertexConsumer lines = ctx.consumers().getBuffer(RenderLayer.getLines());
        float r = (config.labyrinthColor >> 16 & 0xFF) / 255f, g = (config.labyrinthColor >> 8 & 0xFF) / 255f, b = (config.labyrinthColor & 0xFF) / 255f;
        for (BlockPos pos : opened) {
            if (!pos.isWithinDistance(cam, RANGE)) continue;
            BlockState state = world.getBlockState(pos);
            if (!(state.getBlock() instanceof ChestBlock)) continue;
            DoubleBlockProperties.Type type = ChestBlock.getDoubleBlockType(state);
            // a double chest gets one box, drawn from its first half
            if (type == DoubleBlockProperties.Type.SECOND) continue;
            Box box = shape(world, pos, state);
            if (type == DoubleBlockProperties.Type.FIRST) {
                BlockPos other = pos.offset(ChestBlock.getFacing(state));
                BlockState otherState = world.getBlockState(other);
                if (otherState.getBlock() instanceof ChestBlock) box = box.union(shape(world, other, otherState));
            }
            VertexRendering.drawBox(ctx.matrixStack(), lines, box.expand(GROW).offset(-cam.x, -cam.y, -cam.z), r, g, b, 1f);
        }
    }

    private static Box shape(ClientWorld world, BlockPos pos, BlockState state) {
        return state.getOutlineShape(world, pos).getBoundingBox().offset(pos);
    }
}
