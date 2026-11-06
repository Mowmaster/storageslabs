package com.mowmaster.storageslabs.item;

import com.mowmaster.mowlib.compacting.CompactingContents;
import com.mowmaster.mowlib.filter.HeldFilters;
import com.mowmaster.mowlib.filter.ITransportFilter;
import com.mowmaster.mowlib.registry.MowLibDataComponents;
import com.mowmaster.mowlib.transport.TransportContents;
import com.mowmaster.mowlib.transport.TransportType;
import com.mowmaster.storageslabs.block.SlabKind;
import com.mowmaster.storageslabs.block.SlabTier;
import com.mowmaster.storageslabs.block.StorageSlabBlock;
import com.mowmaster.storageslabs.registry.SlabDataComponents;
import com.mowmaster.storageslabs.storage.SlabContents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;

import java.util.function.Consumer;

// A slab as an item. It carries everything the placed slab had (tier, colour, filter, contents) as data components,
// so placing it again gives back the same slab. The tooltip shows the tier, the capacity and what it holds.
public class StorageSlabItem extends BlockItem
{
    // How many filled slots the tooltip lists before "...and N more"
    private static final int TOOLTIP_SLOTS = 6;

    public StorageSlabItem(StorageSlabBlock block, Properties properties)
    {
        super(block, properties);
    }

    public SlabKind kind()
    {
        return ((StorageSlabBlock) getBlock()).kind();
    }

    public static SlabTier tierOf(ItemStack stack)
    {
        Integer tier = stack.get(SlabDataComponents.TIER.get());
        return SlabTier.of(tier == null ? 0 : tier);
    }

    // A new slab item of this tier (the creative tab, the upgrade recipe)
    public static ItemStack withTier(Item item, SlabTier tier)
    {
        ItemStack stack = new ItemStack(item);
        if (tier != SlabTier.BASE) stack.set(SlabDataComponents.TIER.get(), tier.ordinal());
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        super.appendHoverText(stack, context, display, tooltip, flag);
        SlabTier tier = tierOf(stack);
        SlabKind kind = kind();
        tooltip.accept(Component.translatable("tooltip.storageslabs.tier", tier.displayName()).withStyle(ChatFormatting.GRAY));
        String capacityKey = kind == SlabKind.COMPACTING ? "tooltip.storageslabs.capacity_compacting" : "tooltip.storageslabs.capacity";
        tooltip.accept(Component.translatable(capacityKey, kind.slots(tier), kind.capacity(tier)).withStyle(ChatFormatting.GRAY));

        ItemStack filter = HeldFilters.get(stack);
        if (filter.getItem() instanceof ITransportFilter transportFilter)
        {
            String key = transportFilter.isBlacklist(filter) ? "tooltip.storageslabs.filter_blacklist" : "tooltip.storageslabs.filter_whitelist";
            tooltip.accept(Component.translatable(key, filter.getHoverName()).withStyle(ChatFormatting.DARK_GRAY));
        }

        if (kind == SlabKind.COMPACTING)
        {
            CompactingContents contents = stack.get(MowLibDataComponents.COMPACTING_CONTENTS.get());
            if (contents == null || !contents.isSet()) return;
            // The top tier and how many of it the pool makes
            int top = contents.size() - 1;
            tooltip.accept(Component.translatable("tooltip.storageslabs.slot", contents.amount(top), contents.stack(top, 1).getHoverName())
                    .withStyle(ChatFormatting.BLUE));
            return;
        }

        SlabContents contents = stack.get(SlabDataComponents.SLAB_CONTENTS.get());
        if (contents == null || contents.isEmpty()) return;
        int shown = 0;
        for (SlabContents.Entry entry : contents.slots())
        {
            if (shown == TOOLTIP_SLOTS) break;
            for (ResourceStack<?> slot : entry.contents().contents())
            {
                tooltip.accept(describe(entry.contents(), slot));
                shown++;
            }
        }
        if (contents.slots().size() > shown)
        {
            tooltip.accept(Component.translatable("tooltip.storageslabs.more", contents.slots().size() - shown).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    // "Lava: 4000 mB" in the type's colour (MowLib's wording)
    @SuppressWarnings("unchecked")
    private static <R extends Resource> Component describe(TransportContents<R> contents, ResourceStack<?> stack)
    {
        TransportType<R> type = contents.type();
        return Component.empty().append(type.describeAmount((R) stack.resource(), stack.amount())).withStyle(type.getColor());
    }
}
