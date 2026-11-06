package com.mowmaster.storageslabs.registry;

import com.mowmaster.storageslabs.StorageSlabs;
import com.mowmaster.storageslabs.blockentity.CompactingSlabBlockEntity;
import com.mowmaster.storageslabs.blockentity.StorageSlabBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;
import java.util.function.Supplier;

public class SlabBlockEntities
{
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, StorageSlabs.MODID);

    // The 1x1 and 3x3 slabs (the block says how many slots)
    public static final Supplier<BlockEntityType<StorageSlabBlockEntity>> STORAGE_SLAB = BLOCK_ENTITIES.register("storage_slab",
            () -> new BlockEntityType<>(StorageSlabBlockEntity::new, Set.of(SlabBlocks.SINGLE.get(), SlabBlocks.GRID.get())));

    public static final Supplier<BlockEntityType<CompactingSlabBlockEntity>> COMPACTING_SLAB = BLOCK_ENTITIES.register("compacting_slab",
            () -> new BlockEntityType<>(CompactingSlabBlockEntity::new, Set.of(SlabBlocks.COMPACTING.get())));
}
