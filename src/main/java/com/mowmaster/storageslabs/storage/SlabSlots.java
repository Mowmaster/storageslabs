package com.mowmaster.storageslabs.storage;

import com.mowmaster.mowlib.filter.FilterDirection;
import com.mowmaster.mowlib.filter.ITransportFilter;
import com.mowmaster.mowlib.transport.TransportContents;
import com.mowmaster.mowlib.transport.TransportType;
import com.mowmaster.mowlib.transport.TransportTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// The slots of a 1x1 or 3x3 slab. Unlike MowLib's storages (one transport type each), every slot here can hold ANY
// transport type: slot 0 iron ingots, slot 1 lava, slot 2 energy... A slot takes whatever goes in first and is free
// again once emptied.
// - Capacity: the slab's "items per slot", turned into the type's units by SlabUnits (1000 mB, 20000 FE, 1 level
//   per item), then limited by the slab's filter.
// - Filters: MowLib's rules (ITransportFilter.allowsMoving / limitStored), the same as every MowLib storage.
// - Pipes, hoppers, cables: view(type) is a normal NeoForge ResourceHandler of that type over all the slots (slots
//   holding another type show as empty and refuse it). The block entity offers one per type as capabilities.
// - Transactions: the first change inside a transaction copies the three arrays (at most 108 slots, so cheap);
//   a rollback puts the copy back, and a committed change tells the host once.
public class SlabSlots
{
    // What the slots ask of their block entity
    public interface Host
    {
        // Items per slot (read every time: an upgrade applies straight away)
        int itemsPerSlot();

        // The slab's filter item (EMPTY for none)
        ItemStack filter();

        // After a committed change (save, sync, comparators)
        void onSlotsChanged();
    }

    private final Host host;
    // Per slot: its type (null = empty), resource, amount. Kept together: index i of each array is slot i.
    private TransportType<?>[] types;
    private Resource[] resources;
    private int[] amounts;
    private final Map<TransportType<?>, View<?>> views = new ConcurrentHashMap<>();

    // A copy of the three arrays (a record: a small immutable holder)
    private record State(TransportType<?>[] types, Resource[] resources, int[] amounts) {}

    private final SnapshotJournal<State> journal = new SnapshotJournal<>()
    {
        @Override
        protected State createSnapshot()
        {
            return new State(types.clone(), resources.clone(), amounts.clone());
        }

        @Override
        protected void revertToSnapshot(State snapshot)
        {
            types = snapshot.types();
            resources = snapshot.resources();
            amounts = snapshot.amounts();
        }

        @Override
        protected void onRootCommit(State original)
        {
            if (!Arrays.equals(original.amounts(), amounts) || !Arrays.equals(original.resources(), resources)) host.onSlotsChanged();
        }
    };

    public SlabSlots(Host host, int size)
    {
        this.host = host;
        this.types = new TransportType<?>[size];
        this.resources = new Resource[size];
        this.amounts = new int[size];
    }

    // ---- Reading ----

    public int size()
    {
        return amounts.length;
    }

    public @Nullable TransportType<?> type(int slot)
    {
        return valid(slot) ? types[slot] : null;
    }

    public @Nullable Resource resource(int slot)
    {
        return valid(slot) ? resources[slot] : null;
    }

    public int amount(int slot)
    {
        return valid(slot) ? amounts[slot] : 0;
    }

    public boolean isEmpty(int slot)
    {
        return amount(slot) <= 0;
    }

    public boolean isEmpty()
    {
        for (int amount : amounts) if (amount > 0) return false;
        return true;
    }

    private boolean valid(int slot)
    {
        return slot >= 0 && slot < amounts.length;
    }

    // How much of this resource the slot can hold in all (its own units), after the filter's limit
    public <R extends Resource> int capacity(TransportType<R> type, R resource)
    {
        int capacity = SlabUnits.capacity(type, Math.max(0, host.itemsPerSlot()));
        return ITransportFilter.limitStored(host.filter(), type, resource, capacity);
    }

    // The slot's capacity for what it holds now (0 for an empty slot)
    public int capacity(int slot)
    {
        if (isEmpty(slot)) return 0;
        return capacityOf(types[slot], resources[slot]);
    }

    @SuppressWarnings("unchecked")
    private static <R extends Resource> int capacityOf(SlabSlots slots, TransportType<R> type, Resource resource)
    {
        return slots.capacity(type, (R) resource);
    }

    private int capacityOf(TransportType<?> type, Resource resource)
    {
        return capacityOf(this, type, resource);
    }

    // How full all slots are together, 0 to 1 (each slot counts the same, whatever it holds)
    public float fullness()
    {
        if (size() == 0) return 0;
        double sum = 0;
        for (int slot = 0; slot < size(); slot++)
        {
            if (isEmpty(slot)) continue;
            int capacity = capacity(slot);
            sum += capacity > 0 ? Math.min(1.0, (double) amounts[slot] / capacity) : 1.0;
        }
        return (float) (sum / size());
    }

    // "Iron Ingot: 120 / 3456", "Lava: 4000 mB / 3456000 mB" (MowLib's wording, in the type's colour), or null if empty
    public @Nullable Component describe(int slot)
    {
        if (isEmpty(slot)) return null;
        return describeOf(types[slot], resources[slot], amounts[slot], capacity(slot));
    }

    @SuppressWarnings("unchecked")
    private static <R extends Resource> Component describeOf(TransportType<R> type, Resource resource, int amount, int capacity)
    {
        return type.describeStored((R) resource, amount, capacity);
    }

    // ---- Changing (inside a transaction) ----

    // Whether the slot could take this resource at all (empty, or already holding exactly it) and the filter allows it
    public <R extends Resource> boolean accepts(int slot, TransportType<R> type, R resource)
    {
        if (!valid(slot) || resource.isEmpty()) return false;
        if (!ITransportFilter.allowsMoving(host.filter(), type, resource, FilterDirection.INSERT)) return false;
        return isEmpty(slot) || (types[slot] == type && resources[slot].equals(resource));
    }

    public <R extends Resource> int insert(int slot, TransportType<R> type, R resource, int amount, TransactionContext transaction)
    {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (!accepts(slot, type, resource)) return 0;
        int current = amount(slot);
        int inserted = (int) Math.min(amount, Math.max(0L, (long) capacity(type, resource) - current));
        if (inserted <= 0) return 0;
        journal.updateSnapshots(transaction);
        types[slot] = type;
        resources[slot] = resource;
        amounts[slot] = current + inserted;
        return inserted;
    }

    public <R extends Resource> int extract(int slot, TransportType<R> type, R resource, int amount, TransactionContext transaction)
    {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (!valid(slot) || isEmpty(slot) || types[slot] != type || !resources[slot].equals(resource)) return 0;
        if (!ITransportFilter.allowsMoving(host.filter(), type, resource, FilterDirection.EXTRACT)) return 0;
        int extracted = Math.min(amount, amounts[slot]);
        if (extracted <= 0) return 0;
        journal.updateSnapshots(transaction);
        amounts[slot] -= extracted;
        if (amounts[slot] <= 0) clearSlot(slot);
        return extracted;
    }

    private void clearSlot(int slot)
    {
        types[slot] = null;
        resources[slot] = null;
        amounts[slot] = 0;
    }

    // ---- Views for one type ----

    // All slots seen as a ResourceHandler of `type` (for capabilities and moving with MowLib's helpers)
    @SuppressWarnings("unchecked")
    public <R extends Resource> View<R> view(TransportType<R> type)
    {
        return (View<R>) views.computeIfAbsent(type, each -> new View<>(this, type));
    }

    // One slot seen as a one-index ResourceHandler of `type` (a bucket filling from the clicked slot...)
    public <R extends Resource> ResourceHandler<R> single(int slot, TransportType<R> type)
    {
        return new Single<>(view(type), slot);
    }

    // A record implementing an interface: a small class whose fields are its header
    public record View<R extends Resource>(SlabSlots slots, TransportType<R> type) implements ResourceHandler<R>
    {
        @Override
        public int size()
        {
            return slots.size();
        }

        @Override
        @SuppressWarnings("unchecked")
        public R getResource(int index)
        {
            return slots.type(index) == type && !slots.isEmpty(index) ? (R) slots.resource(index) : SlabUnits.empty(type);
        }

        @Override
        public long getAmountAsLong(int index)
        {
            return slots.type(index) == type ? slots.amount(index) : 0;
        }

        @Override
        public long getCapacityAsLong(int index, R resource)
        {
            if (resource.isEmpty()) return slots.type(index) == type ? slots.capacity(index) : 0;
            return slots.capacity(type, resource);
        }

        @Override
        public boolean isValid(int index, R resource)
        {
            return slots.accepts(index, type, resource);
        }

        @Override
        public int insert(int index, R resource, int amount, TransactionContext transaction)
        {
            return slots.insert(index, type, resource, amount, transaction);
        }

        // Index-free insert (most pipes): slots already holding it first, then empty ones
        @Override
        public int insert(R resource, int amount, TransactionContext transaction)
        {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            int inserted = 0;
            for (int index = 0; index < size() && inserted < amount; index++)
            {
                if (!slots.isEmpty(index)) inserted += insert(index, resource, amount - inserted, transaction);
            }
            for (int index = 0; index < size() && inserted < amount; index++)
            {
                if (slots.isEmpty(index)) inserted += insert(index, resource, amount - inserted, transaction);
            }
            return inserted;
        }

        @Override
        public int extract(int index, R resource, int amount, TransactionContext transaction)
        {
            return slots.extract(index, type, resource, amount, transaction);
        }
    }

    private record Single<R extends Resource>(View<R> view, int slot) implements ResourceHandler<R>
    {
        @Override public int size() { return 1; }
        @Override public R getResource(int index) { return view.getResource(slot); }
        @Override public long getAmountAsLong(int index) { return view.getAmountAsLong(slot); }
        @Override public long getCapacityAsLong(int index, R resource) { return view.getCapacityAsLong(slot, resource); }
        @Override public boolean isValid(int index, R resource) { return view.isValid(slot, resource); }
        @Override public int insert(int index, R resource, int amount, TransactionContext transaction) { return view.insert(slot, resource, amount, transaction); }
        @Override public int extract(int index, R resource, int amount, TransactionContext transaction) { return view.extract(slot, resource, amount, transaction); }
    }

    // ---- Size (a tier upgrade can add slots) ----

    // Grows (or shrinks) to `size` slots, keeping what's in the slots that remain. Only call outside transactions.
    public void resize(int size)
    {
        if (size == size()) return;
        types = Arrays.copyOf(types, size);
        resources = Arrays.copyOf(resources, size);
        amounts = Arrays.copyOf(amounts, size);
    }

    // ---- Saving: SlabContents ----

    public SlabContents toContents(int page)
    {
        List<SlabContents.Entry> entries = new ArrayList<>();
        for (int slot = 0; slot < size(); slot++)
        {
            if (isEmpty(slot) || types[slot].getResourceCodec() == null) continue; // a type that can't be saved
            entries.add(new SlabContents.Entry(slot, single(types[slot], resources[slot], amounts[slot])));
        }
        return new SlabContents(page, entries);
    }

    @SuppressWarnings("unchecked")
    private static <R extends Resource> TransportContents<R> single(TransportType<R> type, Resource resource, int amount)
    {
        return new TransportContents<>(type, List.of(new ResourceStack<>((R) resource, amount)));
    }

    // Replaces everything (loading, placing a slab from its item). Slots past the end are kept out of range and dropped.
    public void load(SlabContents contents)
    {
        Arrays.fill(types, null);
        Arrays.fill(resources, null);
        Arrays.fill(amounts, 0);
        for (SlabContents.Entry entry : contents.slots())
        {
            int slot = entry.slot();
            if (!valid(slot)) continue;
            for (ResourceStack<?> stack : entry.contents().contents())
            {
                if (stack.isEmpty()) continue;
                types[slot] = entry.contents().type();
                resources[slot] = stack.resource();
                amounts[slot] = stack.amount();
                break; // one resource per slot
            }
        }
    }

    // Whether this type is MowLib's items (the type players click in and out by hand)
    public boolean holdsItems(int slot)
    {
        return type(slot) == TransportTypes.ITEMS.get();
    }
}
