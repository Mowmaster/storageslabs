package com.mowmaster.storageslabs.blockentity;

import com.mowmaster.mowlib.paging.IPageHolder;
import com.mowmaster.mowlib.paging.PageText;
import com.mowmaster.mowlib.storage.StorageInteractions;
import com.mowmaster.mowlib.transport.TransportType;
import com.mowmaster.mowlib.transport.TransportTypes;
import com.mowmaster.mowlib.util.MowLibXpUtils;
import com.mowmaster.mowlib.transport.ExperienceTransportType;
import com.mowmaster.storageslabs.block.SlabKind;
import com.mowmaster.storageslabs.registry.SlabBlockEntities;
import com.mowmaster.storageslabs.registry.SlabDataComponents;
import com.mowmaster.storageslabs.storage.SlabContents;
import com.mowmaster.storageslabs.storage.SlabSlots;
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
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

// The 1x1 and 3x3 slabs: SlabSlots (any transport type in any slot) and, for the 3x3, which page of 9 is shown.
// Pages: MowLib's IPageHolder, so the page keys / scroll turn it while looking at it, and the arrows on the model
// turn it too (StorageSlabBlock).
// By hand (cells found by StorageSlabBlock):
//   right-click with an item       - into the clicked slot (then any other slot already holding it)
//   right-click with a bucket, tank, battery... (anything holding a fluid, energy...) - fills the slot from it, or if
//                                    nothing moves, fills it from the slot. Sneak to store the container as an item.
//   right-click with an empty hand - every matching item from the inventory into that slot
//   left-click                      - 1 item (sneak: a stack); a fluid/energy slot fills the held container; an
//                                    experience slot gives you a level (sneak: everything)
public class StorageSlabBlockEntity extends BaseSlabBlockEntity implements IPageHolder, SlabSlots.Host
{
    private static final String TAG_CONTENTS = "contents";

    private final SlabSlots slots;
    private int page;

    public StorageSlabBlockEntity(BlockPos pos, BlockState state)
    {
        super(SlabBlockEntities.STORAGE_SLAB.get(), pos, state);
        this.slots = new SlabSlots(this, kind().slots(tier()));
    }

    public SlabSlots slots()
    {
        return slots;
    }

    // For capabilities: every slot as a handler of the type
    public <R extends Resource> ResourceHandler<R> handler(TransportType<R> type)
    {
        return slots.view(type);
    }

    @Override
    protected void onTierChanged()
    {
        // A STAR 3x3 has 108 slots; the page stays inside the new page count
        slots.resize(kind().slots(tier()));
        page = Math.floorMod(page, getPageCount());
    }

    // The slot a cell shows on the open page
    public int slotOf(int cell)
    {
        return page * kind().cellsPerPage() + cell;
    }

    // ---- SlabSlots.Host ----

    @Override
    public int itemsPerSlot()
    {
        return kind().capacity(tier());
    }

    @Override
    public ItemStack filter()
    {
        return getFilter();
    }

    @Override
    public void onSlotsChanged()
    {
        contentsChanged();
    }

    // ---- Contents ----

    @Override
    public boolean isContentsEmpty()
    {
        return slots.isEmpty();
    }

    @Override
    public float fullness()
    {
        return slots.fullness();
    }

    @Override
    public CellDisplay display(int cell)
    {
        int slot = slotOf(cell);
        if (slots.isEmpty(slot)) return CellDisplay.EMPTY;
        Resource resource = slots.resource(slot);
        if (resource instanceof ItemResource item) return new CellDisplay(item.toStack(1), slots.type(slot), resource);
        return new CellDisplay(ItemStack.EMPTY, slots.type(slot), resource);
    }

    // ---- Clicks (server) ----

    @Override
    public boolean clickInsert(Player player, InteractionHand hand, int cell)
    {
        int slot = slotOf(cell);
        ItemStack held = player.getItemInHand(hand);
        boolean moved;
        if (held.isEmpty()) moved = insertAllFromInventory(player, slot);
        else moved = (!player.isShiftKeyDown() && useContainer(player, hand, slot)) || insertHeld(player, hand, slot);

        if (moved) level.playSound(null, worldPosition, SoundEvents.BUNDLE_INSERT, SoundSource.BLOCKS, 0.8f, 0.8f + 0.4f * fullness());
        else level.playSound(null, worldPosition, SoundEvents.BUNDLE_INSERT_FAIL, SoundSource.BLOCKS, 0.8f, 1f);
        report(player, slot);
        syncNow();
        return moved;
    }

    @Override
    public boolean clickExtract(Player player, int cell)
    {
        int slot = slotOf(cell);
        if (slots.isEmpty(slot)) return false;
        boolean moved;
        if (slots.holdsItems(slot))
        {
            ItemResource resource = (ItemResource) slots.resource(slot);
            int wanted = player.isShiftKeyDown() ? resource.getMaxStackSize() : 1;
            int taken;
            try (Transaction transaction = Transaction.openRoot())
            {
                taken = slots.extract(slot, TransportTypes.ITEMS.get(), resource, wanted, transaction);
                transaction.commit();
            }
            StorageInteractions.giveToPlayer(player, resource.toStack(taken));
            moved = taken > 0;
        }
        else
        {
            moved = emptyIntoHeld(player, slot) || giveExperience(player, slot);
        }
        if (moved) level.playSound(null, worldPosition, SoundEvents.BUNDLE_REMOVE_ONE, SoundSource.BLOCKS, 0.8f, 0.8f + 0.4f * fullness());
        report(player, slot);
        syncNow();
        return moved;
    }

    // Held stack into the slot; what's left into other slots already holding the same item
    private boolean insertHeld(Player player, InteractionHand hand, int slot)
    {
        ItemStack held = player.getItemInHand(hand);
        ItemResource resource = ItemResource.of(held);
        int inserted = 0;
        try (Transaction transaction = Transaction.openRoot())
        {
            inserted += slots.insert(slot, TransportTypes.ITEMS.get(), resource, held.getCount(), transaction);
            for (int other = 0; other < slots.size() && inserted < held.getCount(); other++)
            {
                if (other != slot && !slots.isEmpty(other)) inserted += slots.insert(other, TransportTypes.ITEMS.get(), resource, held.getCount() - inserted, transaction);
            }
            transaction.commit();
        }
        if (inserted > 0) held.consume(inserted, player); // creative players keep their stack
        return inserted > 0;
    }

    // Empty hand: every stack in the inventory matching the slot's item goes into it
    private boolean insertAllFromInventory(Player player, int slot)
    {
        if (!slots.holdsItems(slot)) return false;
        ItemResource resource = (ItemResource) slots.resource(slot);
        Inventory inventory = player.getInventory();
        int total = 0;
        for (int index = 0; index < Inventory.INVENTORY_SIZE; index++)
        {
            ItemStack stack = inventory.getItem(index);
            if (stack.isEmpty() || !resource.matches(stack)) continue;
            int inserted;
            try (Transaction transaction = Transaction.openRoot())
            {
                inserted = slots.insert(slot, TransportTypes.ITEMS.get(), resource, stack.getCount(), transaction);
                transaction.commit();
            }
            if (inserted <= 0) break; // full
            stack.shrink(inserted);
            total += inserted;
        }
        if (total > 0) inventory.setChanged();
        return total > 0;
    }

    // A held container of some non-item type (bucket, tank, battery...): into the slot, or else out of it
    private boolean useContainer(Player player, InteractionHand hand, int slot)
    {
        for (TransportType<?> type : TransportTypes.REGISTRY)
        {
            if (type == TransportTypes.ITEMS.get()) continue;
            if (!slots.isEmpty(slot) && slots.type(slot) != type) continue;
            ResourceHandler<?> held = type.getItemHandler(StorageInteractions.heldAccess(player, hand));
            if (held == null) continue;
            ResourceHandler<?> into = slots.single(slot, type);
            if (StorageInteractions.move(type, held, into, Integer.MAX_VALUE) > 0) return true;
            if (StorageInteractions.move(type, into, held, Integer.MAX_VALUE) > 0) return true;
        }
        return false;
    }

    // Left-click on a non-item slot: fill the held container from it
    private boolean emptyIntoHeld(Player player, int slot)
    {
        TransportType<?> type = slots.type(slot);
        if (type == null) return false;
        ResourceHandler<?> held = type.getItemHandler(StorageInteractions.heldAccess(player, InteractionHand.MAIN_HAND));
        return held != null && StorageInteractions.move(type, slots.single(slot, type), held, Integer.MAX_VALUE) > 0;
    }

    // Left-click on an experience slot with nothing that holds experience: a level's worth to the player (sneak: all)
    private boolean giveExperience(Player player, int slot)
    {
        if (slots.type(slot) != TransportTypes.EXPERIENCE.get()) return false;
        int wanted = player.isShiftKeyDown() ? Integer.MAX_VALUE
                : MowLibXpUtils.pointsForLevel(player.experienceLevel + 1) - MowLibXpUtils.pointsForLevel(player.experienceLevel);
        int taken;
        try (Transaction transaction = Transaction.openRoot())
        {
            taken = slots.extract(slot, TransportTypes.EXPERIENCE.get(), ExperienceTransportType.EXPERIENCE, Math.max(1, wanted), transaction);
            transaction.commit();
        }
        if (taken > 0) player.giveExperiencePoints(taken);
        return taken > 0;
    }

    // "Page 2/6: Iron Ingot: 120 / 3456" over the hotbar
    private void report(Player player, int slot)
    {
        Component what = slots.describe(slot);
        if (what == null) what = Component.translatable("message.storageslabs.empty_slot");
        tell(player, getPageCount() > 1 ? PageText.line(page, getPageCount(), what) : what);
    }

    // ---- Pages (IPageHolder) ----

    @Override
    public int getPageCount()
    {
        return kind() == SlabKind.GRID ? Math.max(1, slots.size() / kind().cellsPerPage()) : 1;
    }

    @Override
    public int getPage()
    {
        return page;
    }

    @Override
    public void setPage(int newPage)
    {
        page = Math.floorMod(newPage, getPageCount());
        syncNow();
    }

    // ---- Block <-> item ----

    @Override
    protected void applyImplicitComponents(DataComponentGetter components)
    {
        super.applyImplicitComponents(components); // sets the tier, so the slots have the right size
        SlabContents contents = components.get(SlabDataComponents.SLAB_CONTENTS.get());
        slots.load(contents == null ? SlabContents.EMPTY : contents);
        page = contents == null ? 0 : Math.floorMod(contents.page(), getPageCount());
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components)
    {
        super.collectImplicitComponents(components);
        if (!slots.isEmpty()) components.set(SlabDataComponents.SLAB_CONTENTS.get(), slots.toContents(page));
    }

    // ---- Saving ----

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        super.saveAdditional(output);
        output.store(TAG_CONTENTS, SlabContents.CODEC, slots.toContents(page));
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        SlabContents contents = input.read(TAG_CONTENTS, SlabContents.CODEC).orElse(SlabContents.EMPTY);
        slots.load(contents);
        page = Math.floorMod(contents.page(), getPageCount());
    }
}
