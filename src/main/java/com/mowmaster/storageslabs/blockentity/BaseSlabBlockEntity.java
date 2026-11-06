package com.mowmaster.storageslabs.blockentity;

import com.mowmaster.mowlib.blockentity.CBSyncedBlockEntity;
import com.mowmaster.mowlib.filter.IFilterHolder;
import com.mowmaster.mowlib.filter.HeldFilters;
import com.mowmaster.mowlib.registry.MowLibDataComponents;
import com.mowmaster.mowlib.tint.CBTintBlockEntity;
import com.mowmaster.mowlib.tint.CBTintData;
import com.mowmaster.mowlib.tint.ITintHolder;
import com.mowmaster.storageslabs.block.SlabKind;
import com.mowmaster.storageslabs.block.SlabTier;
import com.mowmaster.storageslabs.block.StorageSlabBlock;
import com.mowmaster.storageslabs.registry.SlabDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

// What every slab's block entity has: its tier, its colour (MowLib CBTintData, region 0), its filter (MowLib
// IFilterHolder), and the client copy (MowLib CBSyncedBlockEntity: everything saved is also sent to clients, which
// the renderer reads).
// Pipes can change the contents every tick, so content changes only mark the slab as needing a sync, and the server
// tick sends at most one update every 4 ticks. Player clicks call sync() straight away.
// Colours (MowLib's tint source asks getTint for each model tintindex):
//   tintindex 0 - the body: the player's dye, or the tier's colour while undyed
//   tintindex 1 - the band in the middle: white with a whitelist filter, black with a blacklist, else the body colour
public abstract class BaseSlabBlockEntity extends CBSyncedBlockEntity implements ITintHolder, IFilterHolder
{
    public static final int WHITELIST_BAND = 0xF2F2F2;
    public static final int BLACKLIST_BAND = 0x1E1E22;
    private static final String TAG_TIER = "tier";
    private static final String TAG_FILTER = "filter";
    private static final String TAG_COLOR = "color"; // CBTintData's key for region 0
    private static final int SYNC_INTERVAL = 4;

    protected final CBTintData tints;
    private SlabTier tier = SlabTier.BASE;
    private ItemStack filter = ItemStack.EMPTY;
    private boolean needsSync;

    protected BaseSlabBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state)
    {
        super(type, pos, state);
        this.tints = new CBTintData(this, 1);
    }

    public SlabKind kind()
    {
        return getBlockState().getBlock() instanceof StorageSlabBlock slab ? slab.kind() : SlabKind.SINGLE;
    }

    public Direction facing()
    {
        BlockState state = getBlockState();
        return state.hasProperty(StorageSlabBlock.FACING) ? state.getValue(StorageSlabBlock.FACING) : Direction.UP;
    }

    // ---- Tier ----

    public SlabTier tier()
    {
        return tier;
    }

    protected void setTierQuietly(SlabTier newTier)
    {
        tier = newTier;
        onTierChanged();
    }

    // The 3x3 grows to 108 slots at STAR
    protected void onTierChanged() {}

    // ---- Contents (the subclasses) ----

    public abstract boolean isContentsEmpty();

    // 0 to 1, for comparators
    public abstract float fullness();

    // A player clicked a cell (the block found which); return true if something happened. Server only.
    public abstract boolean clickInsert(Player player, net.minecraft.world.InteractionHand hand, int cell);

    public abstract boolean clickExtract(Player player, int cell);

    // What a cell shows (client renderer)
    public abstract CellDisplay display(int cell);

    // The content changed through a transaction (pipes or clicks): save, tell comparators, sync soon
    protected void contentsChanged()
    {
        setChanged();
        needsSync = true;
        if (level != null) level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
    }

    // Called every tick on the server (StorageSlabBlock#getTicker)
    public void serverTick(ServerLevel serverLevel)
    {
        if (needsSync && serverLevel.getGameTime() % SYNC_INTERVAL == 0)
        {
            needsSync = false;
            sync();
        }
    }

    // After a player click: now, not in up to 4 ticks
    protected void syncNow()
    {
        needsSync = false;
        sync();
    }

    // A message over the hotbar
    protected static void tell(Player player, Component message)
    {
        player.sendOverlayMessage(message);
    }

    // ---- Colours (ITintHolder) ----

    @Override
    public int getColor(int region)
    {
        return tints.getColor(region);
    }

    @Override
    public void setColor(int region, int color)
    {
        tints.setColor(region, color);
    }

    public int bodyColor()
    {
        int dyed = tints.getColor(0);
        return dyed != CBTintBlockEntity.UNDYED ? dyed : tier.color();
    }

    @Override
    public int getTint(int tintIndex)
    {
        if (tintIndex != 1) return bodyColor();
        return switch (filterStatus())
        {
            case 1 -> WHITELIST_BAND;
            case 2 -> BLACKLIST_BAND;
            default -> bodyColor();
        };
    }

    // ---- Filter (IFilterHolder): the band's colour is a blockstate, so changing it re-draws the block ----

    @Override
    public ItemStack getFilter()
    {
        return filter;
    }

    @Override
    public boolean insertFilter(ItemStack stack)
    {
        if (!filter.isEmpty() || !HeldFilters.isFilter(stack)) return false;
        filter = stack.split(1);
        filterChanged();
        return true;
    }

    @Override
    public ItemStack removeFilter()
    {
        ItemStack removed = filter;
        filter = ItemStack.EMPTY;
        if (!removed.isEmpty()) filterChanged();
        return removed;
    }

    private void filterChanged()
    {
        if (level != null && !level.isClientSide() && getBlockState().hasProperty(StorageSlabBlock.FILTER_STATUS))
        {
            level.setBlock(worldPosition, getBlockState().setValue(StorageSlabBlock.FILTER_STATUS, filterStatus()), Block.UPDATE_ALL);
        }
        syncNow();
    }

    // ---- Block <-> item: the item keeps tier, colour, filter (and the subclass's contents) ----

    @Override
    protected void applyImplicitComponents(DataComponentGetter components)
    {
        super.applyImplicitComponents(components);
        Integer itemTier = components.get(SlabDataComponents.TIER.get());
        setTierQuietly(SlabTier.of(itemTier == null ? 0 : itemTier));
        tints.applyItemColor(components, 0);
        ItemStackTemplate itemFilter = components.get(MowLibDataComponents.HELD_FILTER.get());
        filter = itemFilter == null ? ItemStack.EMPTY : itemFilter.create();
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components)
    {
        super.collectImplicitComponents(components);
        if (tier != SlabTier.BASE) components.set(SlabDataComponents.TIER.get(), tier.ordinal());
        tints.collectItemColor(components);
        if (!filter.isEmpty()) components.set(MowLibDataComponents.HELD_FILTER.get(), ItemStackTemplate.fromNonEmptyStack(filter));
    }

    // ---- Saving (also the client copy) ----

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        super.saveAdditional(output);
        output.putInt(TAG_TIER, tier.ordinal());
        tints.save(output);
        if (!filter.isEmpty()) output.store(TAG_FILTER, ItemStack.OPTIONAL_CODEC, filter);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        // The tier first: it sets the number of slots the contents load into
        setTierQuietly(SlabTier.of(input.getIntOr(TAG_TIER, 0)));
        // CBTintData#load re-draws the chunk on the client every time; content syncs come often, so only load the
        // colour when it really changed
        if (level == null || !level.isClientSide() || input.getIntOr(TAG_COLOR, CBTintBlockEntity.UNDYED) != tints.getColor(0))
        {
            tints.load(input);
        }
        filter = input.read(TAG_FILTER, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
    }

    // What one cell shows. item: an item to draw (EMPTY for none); otherwise a non-item resource to draw as a flat
    // surface (fluids with their flowing texture, other types in their colour). A record: an immutable data holder.
    public record CellDisplay(ItemStack item, @org.jetbrains.annotations.Nullable com.mowmaster.mowlib.transport.TransportType<?> type,
                              @org.jetbrains.annotations.Nullable net.neoforged.neoforge.transfer.resource.Resource resource)
    {
        public static final CellDisplay EMPTY = new CellDisplay(ItemStack.EMPTY, null, null);

        public boolean isEmpty()
        {
            return item.isEmpty() && (resource == null || resource.isEmpty());
        }
    }
}
