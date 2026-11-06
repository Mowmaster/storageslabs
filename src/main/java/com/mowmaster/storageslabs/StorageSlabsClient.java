package com.mowmaster.storageslabs;

import com.mowmaster.storageslabs.client.SlabItemTints;
import com.mowmaster.storageslabs.client.StorageSlabRenderer;
import com.mowmaster.storageslabs.registry.SlabBlockEntities;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

// Client-only setup; this class never loads on a dedicated server.
// The block tints need nothing here: MowLib registers a tint source for every IColorableBlock (tintindex = region).
@Mod(value = StorageSlabs.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = StorageSlabs.MODID, value = Dist.CLIENT)
public class StorageSlabsClient
{
    // What the slabs hold, drawn on their tops
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerBlockEntityRenderer(SlabBlockEntities.STORAGE_SLAB.get(), StorageSlabRenderer::new);
        event.registerBlockEntityRenderer(SlabBlockEntities.COMPACTING_SLAB.get(), StorageSlabRenderer::new);
    }

    // The item models' tints (assets/storageslabs/items/*.json)
    @SubscribeEvent
    public static void registerItemTintSources(RegisterColorHandlersEvent.ItemTintSources event)
    {
        event.register(Identifier.fromNamespaceAndPath(StorageSlabs.MODID, "slab_body"), SlabItemTints.Body.MAP_CODEC);
        event.register(Identifier.fromNamespaceAndPath(StorageSlabs.MODID, "slab_band"), SlabItemTints.Band.MAP_CODEC);
    }
}
