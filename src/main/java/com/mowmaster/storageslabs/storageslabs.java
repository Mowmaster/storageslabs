package com.mowmaster.storageslabs;

import com.mojang.logging.LogUtils;
import com.mowmaster.storageslabs.registry.SlabBlockEntities;
import com.mowmaster.storageslabs.registry.SlabBlocks;
import com.mowmaster.storageslabs.registry.SlabCreativeTab;
import com.mowmaster.storageslabs.registry.SlabDataComponents;
import com.mowmaster.storageslabs.registry.SlabRecipes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(StorageSlabs.MODID)
public class StorageSlabs
{
    public static final String MODID = "storageslabs";
    public static final Logger LOGGER = LogUtils.getLogger();

    // FML hands the constructor the mod event bus; every DeferredRegister is put on it here
    public StorageSlabs(IEventBus modEventBus)
    {
        SlabBlocks.BLOCKS.register(modEventBus);
        SlabBlocks.ITEMS.register(modEventBus);
        SlabBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        SlabDataComponents.COMPONENTS.register(modEventBus);
        SlabRecipes.SERIALIZERS.register(modEventBus);
        SlabCreativeTab.TABS.register(modEventBus);
    }
}
