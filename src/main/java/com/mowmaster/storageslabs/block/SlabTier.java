package com.mowmaster.storageslabs.block;

import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

// The seven tiers of a slab. Tier 0 is what the recipes make; each higher tier is crafted from the one below it with
// 8 of its material around the slab (recipe/SlabUpgradeRecipe), and multiplies what each slot holds.
// `color` is the slab's tint until a player dyes it (approximately the average colour of the block named in the
// comment). An enum is a fixed set of named instances; each one here is built with the constructor below.
public enum SlabTier
{
    //    material (null: no upgrade into it)   multiplier  color
    BASE(null,                        1,   0x7D7D7D), // stone
    COPPER(Tags.Items.INGOTS_COPPER,  2,   0xC06B4F), // copper_block
    IRON(Tags.Items.INGOTS_IRON,      4,   0xDCDCDC), // iron_block
    GOLD(Tags.Items.INGOTS_GOLD,      8,   0xF6D03D), // gold_block
    DIAMOND(Tags.Items.GEMS_DIAMOND,  16,  0x62DBD6), // diamond_block
    NETHERITE(Tags.Items.INGOTS_NETHERITE, 64, 0x443F40), // netherite_block
    STAR(Tags.Items.NETHER_STARS,     128, 0xCFD5D6); // white_concrete

    public static final int MAX = STAR.ordinal();

    private final @Nullable TagKey<Item> material;
    private final int multiplier;
    private final int color;

    SlabTier(@Nullable TagKey<Item> material, int multiplier, int color)
    {
        this.material = material;
        this.multiplier = multiplier;
        this.color = color;
    }

    // The item that upgrades a slab of the tier below INTO this tier (null for BASE)
    public @Nullable TagKey<Item> material()
    {
        return material;
    }

    // How many times the base capacity each slot holds (the 1x1 and compacting slabs ignore it at STAR: see SlabKind)
    public int multiplier()
    {
        return multiplier;
    }

    // 0xRRGGBB
    public int color()
    {
        return color;
    }

    public boolean isMax()
    {
        return this == STAR;
    }

    public SlabTier next()
    {
        return isMax() ? this : values()[ordinal() + 1];
    }

    // Any number, clamped into 0..6 (saved data from a newer version, a bad command...)
    public static SlabTier of(int tier)
    {
        return values()[Math.clamp(tier, 0, MAX)];
    }

    // "Copper", "Diamond"... (tier.storageslabs.copper)
    public Component displayName()
    {
        return Component.translatable("tier.storageslabs." + name().toLowerCase());
    }
}
