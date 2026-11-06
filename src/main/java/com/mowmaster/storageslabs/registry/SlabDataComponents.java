package com.mowmaster.storageslabs.registry;

import com.mojang.serialization.Codec;
import com.mowmaster.storageslabs.StorageSlabs;
import com.mowmaster.storageslabs.storage.SlabContents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// The mod's item data components (1.20's stack NBT is gone: an item's extra data is typed components).
// persistent = saved with the item; networkSynchronized = sent to clients (tooltips, item tints).
// The compacting slab's contents use MowLib's mowlib:compacting_contents; the filter uses mowlib:held_filter; the
// colour uses vanilla's minecraft:dyed_color.
public class SlabDataComponents
{
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, StorageSlabs.MODID);

    // The slab's tier, 0-6 (block/SlabTier). Missing = 0.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TIER =
            COMPONENTS.registerComponentType("tier", builder -> builder.persistent(Codec.intRange(0, 6)).networkSynchronized(ByteBufCodecs.VAR_INT));

    // What a 1x1 or 3x3 slab holds (storage/SlabContents)
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SlabContents>> SLAB_CONTENTS =
            COMPONENTS.registerComponentType("slab_contents", builder -> builder.persistent(SlabContents.CODEC).networkSynchronized(SlabContents.STREAM_CODEC));
}
