package com.mowmaster.storageslabs.registry;

import com.mowmaster.storageslabs.StorageSlabs;
import com.mowmaster.storageslabs.block.SlabKind;
import com.mowmaster.storageslabs.block.StorageSlabBlock;
import com.mowmaster.storageslabs.item.StorageSlabItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

// The three slabs and their items. DeferredRegister.Blocks#registerBlock hands the lambda a Properties that already
// has the block's id set (26.x blocks and items must know their id when they're made).
public class SlabBlocks
{
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StorageSlabs.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StorageSlabs.MODID);

    public static final DeferredBlock<StorageSlabBlock> SINGLE = slab(SlabKind.SINGLE);
    public static final DeferredBlock<StorageSlabBlock> COMPACTING = slab(SlabKind.COMPACTING);
    public static final DeferredBlock<StorageSlabBlock> GRID = slab(SlabKind.GRID);

    public static final DeferredItem<StorageSlabItem> SINGLE_ITEM = item(SINGLE);
    public static final DeferredItem<StorageSlabItem> COMPACTING_ITEM = item(COMPACTING);
    public static final DeferredItem<StorageSlabItem> GRID_ITEM = item(GRID);

    public static final List<DeferredBlock<StorageSlabBlock>> ALL = List.of(SINGLE, COMPACTING, GRID);

    // Stone-like, not mined faster by hand than a chest; pistons can't push it (it has a block entity);
    // noOcclusion: it's half a block, so the faces of blocks next to it must still be drawn
    private static DeferredBlock<StorageSlabBlock> slab(SlabKind kind)
    {
        return BLOCKS.registerBlock(kind.id(), properties -> new StorageSlabBlock(properties
                .mapColor(MapColor.STONE).strength(2.5f, 6f).sound(SoundType.STONE)
                .noOcclusion().pushReaction(PushReaction.IMMOVEABLE), kind));
    }

    // useBlockDescriptionPrefix: the item's name is the block's (block.storageslabs.<id>)
    private static DeferredItem<StorageSlabItem> item(DeferredBlock<StorageSlabBlock> block)
    {
        return ITEMS.registerItem(block.getId().getPath(), properties -> new StorageSlabItem(block.get(), properties.useBlockDescriptionPrefix()));
    }
}
