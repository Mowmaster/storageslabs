package com.mowmaster.storageslabs.blockentity;

import com.mowmaster.mowlib.compacting.CompactingContents;
import com.mowmaster.mowlib.paging.IPageHolder;
import com.mowmaster.mowlib.paging.PageText;
import com.mowmaster.mowlib.registry.MowLibDataComponents;
import com.mowmaster.mowlib.storage.StorageInteractions;
import com.mowmaster.mowlib.transport.TransportTypes;
import com.mowmaster.storageslabs.block.SlabKind;
import com.mowmaster.storageslabs.registry.SlabBlockEntities;
import com.mowmaster.storageslabs.storage.CompactingSlabHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;

// The compacting slab: MowLib's CompactingContents (one chain, one pool) in a CompactingSlabHandler. It holds
// kind().capacity(tier) of the chain's TOP tier (1728 iron blocks at tier 0, Integer.MAX_VALUE at STAR); the contents
// carry that capacity, and an upgrade raises it.
// The 3 cells show the chain highest tier first: the wide cell the top tier, then the next two. A chain of more than
// 3 tiers is shown 3 at a time; the page is kept here (MowLib's contents page one tier at a time, like a book) and
// turned through MowLib's IPageHolder (page keys / scroll while looking at it).
// By hand: right-click puts the held item in (any tier of the chain, or starts a chain if empty; an empty hand puts in
// every item of the chain from the inventory); left-click takes 1 of the clicked tier (sneak: a stack).
public class CompactingSlabBlockEntity extends BaseSlabBlockEntity implements IPageHolder, CompactingSlabHandler.Host
{
    private static final String TAG_CONTENTS = "compacting";
    private static final String TAG_PAGE = "page";

    private final CompactingSlabHandler storage = new CompactingSlabHandler(this);
    private int page;

    public CompactingSlabBlockEntity(BlockPos pos, BlockState state)
    {
        super(SlabBlockEntities.COMPACTING_SLAB.get(), pos, state);
    }

    // For the item capability
    public CompactingSlabHandler storage()
    {
        return storage;
    }

    public CompactingContents contents()
    {
        return storage.contents();
    }

    @Override
    protected void onTierChanged()
    {
        storage.updateCapacity();
    }

    // The chain tier a cell shows on the open page (-1: none)
    public int tierOf(int cell)
    {
        CompactingContents contents = storage.contents();
        int tier = contents.size() - 1 - (getPage() * SlabKind.CELLS_PER_PAGE_COMPACTING + cell);
        return tier >= 0 && tier < contents.size() ? tier : -1;
    }

    // ---- CompactingSlabHandler.Host ----

    @Override
    public int compactingCapacity()
    {
        return kind().capacity(tier());
    }

    @Override
    public ItemStack compactingFilter()
    {
        return getFilter();
    }

    @Override
    public void onCompactingChanged()
    {
        contentsChanged();
    }

    // ---- Contents ----

    @Override
    public boolean isContentsEmpty()
    {
        return !storage.contents().isSet();
    }

    @Override
    public float fullness()
    {
        return storage.contents().fullness();
    }

    @Override
    public CellDisplay display(int cell)
    {
        int tier = tierOf(cell);
        if (tier < 0) return CellDisplay.EMPTY;
        return new CellDisplay(storage.contents().stack(tier, 1), TransportTypes.ITEMS.get(), storage.contents().resource(tier));
    }

    // ---- Clicks (server) ----

    @Override
    public boolean clickInsert(Player player, InteractionHand hand, int cell)
    {
        ItemStack held = player.getItemInHand(hand);
        int inserted = 0;
        if (!held.isEmpty())
        {
            inserted = storage.insertStack(held);
            if (inserted > 0) held.consume(inserted, player);
        }
        else if (storage.contents().isSet())
        {
            // Empty hand: every item of the chain from the inventory
            Inventory inventory = player.getInventory();
            for (int index = 0; index < Inventory.INVENTORY_SIZE; index++)
            {
                ItemStack stack = inventory.getItem(index);
                if (stack.isEmpty() || storage.contents().tierOf(ItemResource.of(stack)) < 0) continue;
                int moved = storage.insertStack(stack);
                stack.shrink(moved);
                inserted += moved;
            }
            if (inserted > 0) inventory.setChanged();
        }
        page = Math.floorMod(page, getPageCount()); // a new chain may have fewer pages
        if (inserted > 0) level.playSound(null, worldPosition, SoundEvents.BUNDLE_INSERT, SoundSource.BLOCKS, 0.8f, 0.8f + 0.4f * fullness());
        else level.playSound(null, worldPosition, SoundEvents.BUNDLE_INSERT_FAIL, SoundSource.BLOCKS, 0.8f, 1f);
        report(player, Math.max(0, tierOf(cell)));
        syncNow();
        return inserted > 0;
    }

    @Override
    public boolean clickExtract(Player player, int cell)
    {
        int tier = tierOf(cell);
        if (tier < 0) return false;
        ItemStack taken = storage.extractTier(tier, player.isShiftKeyDown() ? Integer.MAX_VALUE : 1);
        StorageInteractions.giveToPlayer(player, taken);
        if (!taken.isEmpty()) level.playSound(null, worldPosition, SoundEvents.BUNDLE_REMOVE_ONE, SoundSource.BLOCKS, 0.8f, 0.8f + 0.4f * fullness());
        report(player, tier);
        page = Math.floorMod(page, getPageCount()); // emptied: back to one page
        syncNow();
        return !taken.isEmpty();
    }

    // "Iron Block: 12 / 1728" or "Iron Ingot: 108 / 15552" over the hotbar
    private void report(Player player, int tier)
    {
        CompactingContents contents = storage.contents();
        Component what;
        if (!contents.isSet() || tier >= contents.size()) what = Component.translatable("message.storageslabs.empty_slot");
        else
        {
            long capacity = contents.maxTotal() / contents.tiers().get(tier).value();
            what = TransportTypes.ITEMS.get().describeStored(contents.resource(tier), contents.amount(tier), Math.min(Integer.MAX_VALUE, capacity));
        }
        tell(player, getPageCount() > 1 ? PageText.line(getPage(), getPageCount(), what) : what);
    }

    // ---- Pages (IPageHolder): groups of 3 tiers ----

    @Override
    public int getPageCount()
    {
        int tiers = storage.contents().size();
        return Math.max(1, (tiers + SlabKind.CELLS_PER_PAGE_COMPACTING - 1) / SlabKind.CELLS_PER_PAGE_COMPACTING);
    }

    @Override
    public int getPage()
    {
        return Math.floorMod(page, getPageCount());
    }

    @Override
    public void setPage(int newPage)
    {
        page = Math.floorMod(newPage, getPageCount());
        syncNow();
    }

    // ---- Block <-> item: mowlib:compacting_contents ----

    @Override
    protected void applyImplicitComponents(DataComponentGetter components)
    {
        super.applyImplicitComponents(components); // sets the tier first, so the capacity is right
        CompactingContents contents = components.get(MowLibDataComponents.COMPACTING_CONTENTS.get());
        storage.load(contents == null ? CompactingContents.EMPTY : contents);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components)
    {
        super.collectImplicitComponents(components);
        if (storage.contents().isSet()) components.set(MowLibDataComponents.COMPACTING_CONTENTS.get(), storage.contents());
    }

    // ---- Saving ----

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        super.saveAdditional(output);
        if (storage.contents().isSet()) output.store(TAG_CONTENTS, CompactingContents.CODEC, storage.contents());
        output.putInt(TAG_PAGE, page);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        storage.load(input.read(TAG_CONTENTS, CompactingContents.CODEC).orElse(CompactingContents.EMPTY));
        page = input.getIntOr(TAG_PAGE, 0);
    }
}
