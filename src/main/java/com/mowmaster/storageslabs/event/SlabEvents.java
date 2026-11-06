package com.mowmaster.storageslabs.event;

import com.mowmaster.mowlib.items.tools.FilterToolItem;
import com.mowmaster.mowlib.storage.StorageInteractions;
import com.mowmaster.mowlib.transport.TransportType;
import com.mowmaster.mowlib.transport.TransportTypes;
import com.mowmaster.mowlib.util.LeftClickCooldown;
import com.mowmaster.storageslabs.StorageSlabs;
import com.mowmaster.storageslabs.block.StorageSlabBlock;
import com.mowmaster.storageslabs.blockentity.BaseSlabBlockEntity;
import com.mowmaster.storageslabs.registry.SlabBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.transfer.resource.Resource;

// @EventBusSubscriber puts each @SubscribeEvent method on the right bus by its event type: RegisterCapabilitiesEvent
// is a mod bus event, PlayerInteractEvent a game bus event.
@EventBusSubscriber(modid = StorageSlabs.MODID)
public final class SlabEvents
{
    private SlabEvents() {}

    // Pipes, hoppers, cables from any mod reach every slot: for each transport type (items, fluids, energy,
    // mowlib:experience_handler, and any type another mod registers), its capability on the 1x1/3x3 slab is that
    // type's view of the slots. The compacting slab only offers items.
    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event)
    {
        for (TransportType<?> type : TransportTypes.REGISTRY) registerSlots(event, type);
        event.registerBlockEntity(Capabilities.Item.BLOCK, SlabBlockEntities.COMPACTING_SLAB.get(), (slab, side) -> slab.storage());
    }

    // Generic so the type's <R> matches the handler's
    private static <R extends Resource> void registerSlots(RegisterCapabilitiesEvent event, TransportType<R> type)
    {
        type.registerBlockCapability(event, SlabBlockEntities.STORAGE_SLAB.get(), (slab, side) -> slab.handler(type));
    }

    // Left-click. An event rather than Block#attack so that:
    // - clicking a cell takes things out instead of mining (cancelled on both sides), also for creative players
    //   (who otherwise break blocks instantly)
    // - the Filter Tool in the offhand takes the filter out from any face
    // The click position isn't in the event, so it's worked out from where the player looks (the same on both sides).
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event)
    {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof StorageSlabBlock block)) return;
        if (!(level.getBlockEntity(pos) instanceof BaseSlabBlockEntity slab)) return;
        Player player = event.getEntity();

        if (player.getOffhandItem().getItem() instanceof FilterToolItem)
        {
            event.setCanceled(true);
            if (LeftClickCooldown.ready(player, level) && !level.isClientSide()) StorageInteractions.removeFilter(player, slab);
            return;
        }

        HitResult hit = player.pick(player.blockInteractionRange(), 1f, false);
        if (!(hit instanceof BlockHitResult blockHit) || !blockHit.getBlockPos().equals(pos)) return;
        int cell = block.cellAt(state, blockHit);
        if (cell < 0) return; // not on a cell: mine it as normal

        event.setCanceled(true);
        if (!LeftClickCooldown.ready(player, level)) return; // held down: only at right-click's pace
        if (!level.isClientSide()) slab.clickExtract(player, cell);
    }
}
