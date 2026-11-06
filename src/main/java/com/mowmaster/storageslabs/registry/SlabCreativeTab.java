package com.mowmaster.storageslabs.registry;

import com.mowmaster.storageslabs.StorageSlabs;
import com.mowmaster.storageslabs.block.SlabTier;
import com.mowmaster.storageslabs.item.StorageSlabItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class SlabCreativeTab
{
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, StorageSlabs.MODID);

    // Every slab at every tier
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("storageslabs", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.storageslabs"))
            .withTabsBefore(CreativeModeTabs.FUNCTIONAL_BLOCKS)
            .icon(() -> SlabBlocks.GRID_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                for (DeferredItem<StorageSlabItem> item : java.util.List.of(SlabBlocks.SINGLE_ITEM, SlabBlocks.COMPACTING_ITEM, SlabBlocks.GRID_ITEM))
                {
                    for (SlabTier tier : SlabTier.values()) output.accept(StorageSlabItem.withTier(item.get(), tier));
                }
            }).build());
}
