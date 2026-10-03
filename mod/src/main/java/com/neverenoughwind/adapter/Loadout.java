package com.neverenoughwind.adapter;

import com.neverenoughwind.parse.Damage;
import com.neverenoughwind.parse.DamageCalc;
import com.neverenoughwind.parse.ItemInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.ArrayList;
import java.util.List;

// puts the hovered weapon together with what the player is wearing
public final class Loadout {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public record Result(Damage damage, String note) {
    }

    private Loadout() {}

    // null when the item isnt a weapon or tool
    public static Result damage(ItemStack hovered) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || hovered == null || hovered.isEmpty()) return null;

        // hovering the off hand item: show the main hand weapon with it
        ItemStack weapon = hovered, offHand = player.getOffHandStack();
        String note = null;
        if (hovered == offHand) {
            weapon = player.getMainHandStack();
            if (weapon.isEmpty()) return null;
            note = "As off hand, with " + weapon.getName().getString();
        }
        double bonus = attackBonus(weapon);
        if (bonus <= 0) return null;

        ItemInfo info = Items.info(weapon);
        List<DamageCalc.Source> sources = new ArrayList<>();
        sources.add(new DamageCalc.Source(weapon == hovered ? "this item" : "main hand", info));
        if (offHand != weapon && !offHand.isEmpty()) {
            sources.add(new DamageCalc.Source(offHand == hovered ? "this item" : "off hand", Items.info(offHand)));
        }
        for (EquipmentSlot slot : ARMOR) {
            ItemStack worn = player.getEquippedStack(slot);
            if (!worn.isEmpty() && worn != weapon) sources.add(new DamageCalc.Source("armor", Items.info(worn)));
        }
        int sharpness = info.sharpness() == null ? sharpnessOf(weapon) : info.sharpness();
        // the player hits for 1 with an empty hand
        Damage damage = DamageCalc.compute(1 + bonus, sharpness, level(player, StatusEffects.STRENGTH),
                level(player, StatusEffects.WEAKNESS), sources);
        return new Result(damage, note);
    }

    private static double attackBonus(ItemStack stack) {
        AttributeModifiersComponent mods = stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
        double[] sum = {0};
        mods.applyModifiers(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(EntityAttributes.ATTACK_DAMAGE) && modifier.operation() == EntityAttributeModifier.Operation.ADD_VALUE) {
                sum[0] += modifier.value();
            }
        });
        return sum[0];
    }

    private static int sharpnessOf(ItemStack stack) {
        Integer level = Items.snapshot(stack).enchants().get("minecraft:sharpness");
        return level == null ? 0 : level;
    }

    private static int level(ClientPlayerEntity player, RegistryEntry<StatusEffect> effect) {
        StatusEffectInstance active = player.getStatusEffect(effect);
        return active == null ? 0 : active.getAmplifier() + 1;
    }
}
