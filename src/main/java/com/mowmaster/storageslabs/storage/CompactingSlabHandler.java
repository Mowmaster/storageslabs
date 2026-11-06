package com.mowmaster.storageslabs.storage;

import com.mowmaster.mowlib.compacting.Compacting;
import com.mowmaster.mowlib.compacting.CompactingChains;
import com.mowmaster.mowlib.compacting.CompactingContents;
import com.mowmaster.mowlib.filter.FilterDirection;
import com.mowmaster.mowlib.filter.ITransportFilter;
import com.mowmaster.mowlib.transport.TransportTypes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.List;

// The compacting slab's storage: MowLib's CompactingContents (one chain, one pool), changed through MowLib's
// Compacting helpers, as a NeoForge item ResourceHandler with transactions. The same object is the block's item
// capability (hoppers, pipes) and what player clicks use.
// - one index per tier (nuggets, ingots, blocks), each showing how many of it the pool makes
// - empty: one index that takes the first item with a chain the filter allows (CompactingChains.filterAllows)
// - an EXTRACT filter is asked about each tier taken out
// - the contents carry their capacity (top-tier items); the slab sets it from its tier with setCapacity
// Transactions: the contents are an immutable record, so the journal's snapshot is just the old record.
//
// TODO MowLib (note only): MowLib's CompactingItemHandler works on an item (ItemAccess); a block-entity version like
// this one would let other mods' compacting blocks share it.
public class CompactingSlabHandler implements ResourceHandler<ItemResource>
{
    // What the handler asks of its block entity
    public interface Host
    {
        // How many of the TOP tier fit
        int compactingCapacity();

        // The slab's filter (EMPTY for none)
        ItemStack compactingFilter();

        // After a committed change (save, sync, comparators)
        void onCompactingChanged();
    }

    private final Host host;
    private CompactingContents contents = CompactingContents.EMPTY;

    private final SnapshotJournal<CompactingContents> journal = new SnapshotJournal<>()
    {
        @Override
        protected CompactingContents createSnapshot()
        {
            return contents;
        }

        @Override
        protected void revertToSnapshot(CompactingContents snapshot)
        {
            contents = snapshot;
        }

        @Override
        protected void onRootCommit(CompactingContents originalState)
        {
            if (!contents.equals(originalState)) host.onCompactingChanged();
        }
    };

    public CompactingSlabHandler(Host host)
    {
        this.host = host;
    }

    public CompactingContents contents()
    {
        return contents;
    }

    // Replaces the contents without telling the host (loading, placing from the item), at the host's capacity
    public void load(CompactingContents loaded)
    {
        contents = loaded.withCapacity(capacity());
    }

    // The tier changed: the contents hold the new capacity from now on
    public void updateCapacity()
    {
        contents = contents.withCapacity(capacity());
    }

    private int capacity()
    {
        return Math.max(0, host.compactingCapacity());
    }

    private boolean allows(List<CompactingContents.Tier> chain)
    {
        return CompactingChains.filterAllows(host.compactingFilter(), chain);
    }

    // ---- ResourceHandler ----

    @Override
    public int size()
    {
        return Math.max(1, contents.size());
    }

    @Override
    public ItemResource getResource(int index)
    {
        return contents.amount(index) > 0 ? contents.resource(index) : ItemResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index)
    {
        return contents.amount(index);
    }

    // What the index can hold: what it has plus the room left (empty: a full slab of that item)
    @Override
    public long getCapacityAsLong(int index, ItemResource resource)
    {
        if (contents.isSet()) return (long) contents.amount(index) + contents.room(index);
        if (resource.isEmpty()) return 0;
        List<CompactingContents.Tier> chain = CompactingChains.find(resource.getItem());
        if (chain == null) return 0;
        CompactingContents fresh = CompactingContents.of(chain, 0, capacity());
        return fresh.room(fresh.tierOf(resource));
    }

    // Each index only takes its own tier; empty takes anything with a chain the filter allows
    @Override
    public boolean isValid(int index, ItemResource resource)
    {
        if (resource.isEmpty()) return false;
        if (contents.isSet()) return index >= 0 && index < contents.size() && resource.equals(contents.resource(index));
        if (index != 0 || !resource.getComponentsPatch().isEmpty()) return false;
        List<CompactingContents.Tier> chain = CompactingChains.find(resource.getItem());
        return chain != null && allows(chain);
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction)
    {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (!isValid(index, resource)) return 0;
        // Compacting.insert: the changed contents and how many went in (it starts the chain when empty).
        // this::allows is a method reference: the same as chain -> allows(chain).
        Compacting.Inserted result = Compacting.insert(contents, resource, amount, capacity(), this::allows);
        if (result.count() <= 0) return 0;
        journal.updateSnapshots(transaction);
        contents = result.contents();
        return result.count();
    }

    // Index-free insert (most pipes): straight to the item's tier
    @Override
    public int insert(ItemResource resource, int amount, TransactionContext transaction)
    {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        int tier = contents.isSet() ? contents.tierOf(resource) : 0;
        return tier < 0 ? 0 : insert(tier, resource, amount, transaction);
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction)
    {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (!contents.isSet() || !resource.equals(contents.resource(index))) return 0;
        if (!ITransportFilter.allowsMoving(host.compactingFilter(), TransportTypes.ITEMS.get(), resource, FilterDirection.EXTRACT)) return 0;
        int extracted = Math.min(amount, contents.amount(index));
        if (extracted <= 0) return 0;
        journal.updateSnapshots(transaction);
        contents = contents.plus(index, -extracted);
        return extracted;
    }

    // ---- For player clicks (each its own committed transaction) ----

    // As much of the stack as fits (the stack isn't changed). Returns how many went in.
    public int insertStack(ItemStack stack)
    {
        if (stack.isEmpty()) return 0;
        try (Transaction transaction = Transaction.openRoot())
        {
            int inserted = insert(ItemResource.of(stack), stack.getCount(), transaction);
            transaction.commit();
            return inserted;
        }
    }

    // Up to `max` of a tier (never more than one normal stack)
    public ItemStack extractTier(int tier, int max)
    {
        ItemResource resource = contents.resource(tier);
        if (resource.isEmpty()) return ItemStack.EMPTY;
        int wanted = Math.min(max, resource.getMaxStackSize());
        if (wanted <= 0) return ItemStack.EMPTY;
        try (Transaction transaction = Transaction.openRoot())
        {
            int extracted = extract(tier, resource, wanted, transaction);
            transaction.commit();
            return extracted > 0 ? resource.toStack(extracted) : ItemStack.EMPTY;
        }
    }
}
