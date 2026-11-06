package com.mowmaster.storageslabs.storage;

import com.mowmaster.mowlib.transport.TransportStorageHost;
import com.mowmaster.mowlib.transport.TransportType;
import com.mowmaster.mowlib.transport.TransportTypes;
import com.mowmaster.mowlib.util.MowLibXpUtils;
import net.neoforged.neoforge.transfer.resource.Resource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// How much of each transport type one "item" of slot capacity stands for. A slot that holds 3456 items holds
// 3456 x 1000 mB of a fluid, 3456 x 20000 FE, or 3456 levels of experience.
//
// TODO MowLib: TransportType has no "base unit" (how much of the type matches one item) and no icon, so the numbers
// for MowLib's four types are here, and any type another mod adds counts 1 per item and is drawn in its colour
// (client/StorageSlabRenderer). See NOTES.md in mowlib-from-storageslabs.zip.
public final class SlabUnits
{
    private SlabUnits() {}

    public static final int FLUID_PER_ITEM = 1000;   // mB: a bucket
    public static final int ENERGY_PER_ITEM = 20000; // FE
    public static final int LEVELS_PER_ITEM = 1;     // experience levels (stored as points)

    // A slot of `items` capacity, in the type's own units (items, mB, FE, experience points). At most Integer.MAX_VALUE.
    public static int capacity(TransportType<?> type, int items)
    {
        if (type == TransportTypes.EXPERIENCE.get())
        {
            // Levels to points: the points needed to go from level 0 to that level (MowLib clamps it to an int)
            return MowLibXpUtils.pointsForLevel((int) Math.min(Integer.MAX_VALUE, (long) items * LEVELS_PER_ITEM));
        }
        return (int) Math.min(Integer.MAX_VALUE, (long) items * perItem(type));
    }

    public static long perItem(TransportType<?> type)
    {
        if (type == TransportTypes.FLUIDS.get()) return FLUID_PER_ITEM;
        if (type == TransportTypes.ENERGY.get()) return ENERGY_PER_ITEM;
        return 1; // items, and types MowLib has no unit for yet
    }

    // ---- The empty resource of a type ----
    // TransportType doesn't say what its empty resource is (ItemResource.EMPTY, FluidResource.EMPTY,
    // AmountResource.EMPTY...), but every storage it makes reports it for an empty index. So: make one throwaway empty
    // storage per type, once, and ask it. (Also in the MowLib notes: TransportType#emptyResource would be simpler.)

    private static final Map<TransportType<?>, Resource> EMPTY = new ConcurrentHashMap<>();

    // A storage host that holds nothing and is never told anything
    private static final TransportStorageHost NOWHERE = new TransportStorageHost()
    {
        @Override
        public void onStorageChanged(TransportType<?> type) {}

        @Override
        public int getCapacity(TransportType<?> type)
        {
            return 1; // item storages are sized in stacks: one slot to ask
        }
    };

    // The unchecked cast is safe: a type's storage only ever holds that type's resource class
    @SuppressWarnings("unchecked")
    public static <R extends Resource> R empty(TransportType<R> type)
    {
        return (R) EMPTY.computeIfAbsent(type, each -> each.createStorage(NOWHERE).getResource(0));
    }
}
