package com.mowmaster.storageslabs.Registry;

import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class DeferredRegistryItems_StorageSlabs {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, StorageSlabsReferences.MODID);

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
