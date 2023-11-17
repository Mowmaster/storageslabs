package com.mowmaster.storageslabs.Registry;

import com.mowmaster.storageslabs.Blocks.BaseStorageSlabBlock;
import com.mowmaster.storageslabs.Blocks.BaseStorageSlabBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class DeferredBlockEntityTypes_StorageSlabs {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister
            .create(ForgeRegistries.BLOCK_ENTITY_TYPES, StorageSlabsReferences.MODID);

    public static final RegistryObject<BlockEntityType<BaseStorageSlabBlockEntity>> BASESTORAGESLAB = BLOCK_ENTITIES.register(
            "block_entity_storageslab",
            () -> BlockEntityType.Builder.of(BaseStorageSlabBlockEntity::new, DeferredRegisterTileBlocks_StorageSlabs.TILE_BASESLAB.get()).build(null));

    private DeferredBlockEntityTypes_StorageSlabs() {
    }
}
