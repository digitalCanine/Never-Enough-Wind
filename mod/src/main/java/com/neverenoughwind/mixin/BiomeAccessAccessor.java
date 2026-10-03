package com.neverenoughwind.mixin;

import net.minecraft.world.biome.source.BiomeAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// the hashed seed the server sent for this world
@Mixin(BiomeAccess.class)
public interface BiomeAccessAccessor {
    @Accessor("seed")
    long new$seed();
}
