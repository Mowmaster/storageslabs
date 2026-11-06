package com.mowmaster.storageslabs.client;

import com.mojang.serialization.MapCodec;
import com.mowmaster.mowlib.filter.HeldFilters;
import com.mowmaster.mowlib.filter.ITransportFilter;
import com.mowmaster.storageslabs.blockentity.BaseSlabBlockEntity;
import com.mowmaster.storageslabs.item.StorageSlabItem;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import org.jetbrains.annotations.Nullable;

// Item model tints for the slab items, matching the placed block (used in assets/storageslabs/items/*.json):
//   {"type": "storageslabs:slab_body"} - tintindex 0: the dye, or the tier's colour
//   {"type": "storageslabs:slab_band"} - tintindex 1: white/black for a whitelist/blacklist filter, else the body
// They have no settings, so each codec makes the single instance (MapCodec.unit). Colours are 0xAARRGGBB.
// (MowLib's tint SOURCES for blocks are MowLib's alone; item tints are registered per mod.)
public final class SlabItemTints
{
    private SlabItemTints() {}

    public static int body(ItemStack stack)
    {
        DyedItemColor dyed = stack.get(DataComponents.DYED_COLOR);
        return 0xFF000000 | (dyed != null ? dyed.rgb() : StorageSlabItem.tierOf(stack).color());
    }

    public enum Body implements ItemTintSource
    {
        INSTANCE;

        public static final MapCodec<Body> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override
        public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity)
        {
            return body(stack);
        }

        @Override
        public MapCodec<Body> type()
        {
            return MAP_CODEC;
        }
    }

    public enum Band implements ItemTintSource
    {
        INSTANCE;

        public static final MapCodec<Band> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override
        public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity)
        {
            ItemStack filter = HeldFilters.get(stack);
            if (filter.getItem() instanceof ITransportFilter transportFilter)
            {
                return 0xFF000000 | (transportFilter.isBlacklist(filter) ? BaseSlabBlockEntity.BLACKLIST_BAND : BaseSlabBlockEntity.WHITELIST_BAND);
            }
            return body(stack);
        }

        @Override
        public MapCodec<Band> type()
        {
            return MAP_CODEC;
        }
    }
}
