package com.mowmaster.storageslabs.Blocks;

import com.mowmaster.mowlib.BlockEntities.MowLibBaseFilterableBlockEntity;
import com.mowmaster.mowlib.Capabilities.Dust.CapabilityDust;
import com.mowmaster.mowlib.Capabilities.Experience.CapabilityExperience;
import com.mowmaster.mowlib.MowLibUtils.MowLibItemUtils;
import com.mowmaster.mowlib.api.TransportAndStorage.IFilterItem;
import com.mowmaster.storageslabs.Registry.DeferredBlockEntityTypes_StorageSlabs;
import com.mowmaster.storageslabs.Registry.StorageSlabsReferences;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BaseStorageSlabBlockEntity extends MowLibBaseFilterableBlockEntity {
    private ItemStackHandler itemHandler = createItemHandlerStorageSlab();
    private LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> this.itemHandler);
    private List<ItemStack> stacksList = new ArrayList<>();
    private int currentPage = 0;
    private int pagesCount = 0;
    private BaseStorageSlabBlockEntity getStorageSlabEntity() { return this; }

    public BaseStorageSlabBlockEntity(BlockPos p_155229_, BlockState p_155230_) {
        super(DeferredBlockEntityTypes_StorageSlabs.BASESTORAGESLAB.get(), p_155229_, p_155230_);
    }

    @Override
    public void update() {
        BlockState state = level.getBlockState(getPos());
        this.level.sendBlockUpdated(getPos(), state, state, 3);
        this.setChanged();
    }

    public int getAdditionalSlots()
    {
        return 0;
    }

    public ItemStackHandler createItemHandlerStorageSlab() {
        //9 slots per page, up to 16 pages 0-15
        return new ItemStackHandler(144) {
            @Override
            public void onLoad() {
                super.onLoad();
            }

            @Override
            public void onContentsChanged(int slot) {
                update();
            }

            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                //Run filter checks here(slot==0)?(true):(false)
                IFilterItem filter = getIFilterItem();
                if(filter == null || !filter.getFilterDirection().insert())return true;
                return filter.canAcceptItems(getFilterInBlockEntity(),stack);
            }

            @Override
            public int getSlots() {
                //maybe return less if there is no tank upgrade???
                int baseSlots = 9;
                int additionalSlots = getAdditionalSlots();
                return baseSlots + additionalSlots;
            }

            @Override
            public int getStackLimit(int slot, @Nonnull ItemStack stack) {
                //Run filter checks here
                IFilterItem filter = getIFilterItem();
                if(filter == null || !filter.getFilterDirection().insert())return super.getStackLimit(slot, stack);
                return filter.canAcceptCountItems(getStorageSlabEntity(), getFilterInBlockEntity(),  stack.getMaxStackSize(), getSlotSizeLimit(),stack);
                //return super.getStackLimit(slot, stack);
            }

            @Override
            public int getSlotLimit(int slot) {

                //Hopefully never mess with this again
                //Amount of items allowed in the slot --- may use for bibliomania???
                return super.getSlotLimit(slot);
            }

            @Nonnull
            @Override
            public ItemStack getStackInSlot(int slot) {

                return super.getStackInSlot((slot>getSlots())?(0):(slot));
            }

            /*
                Inserts an ItemStack into the given slot and return the remainder. The ItemStack should not be modified in this function!
                Note: This behaviour is subtly different from IFluidHandler.fill(FluidStack, IFluidHandler.FluidAction)
                Params:
                    slot – Slot to insert into.
                    stack – ItemStack to insert. This must not be modified by the item handler.
                    simulate – If true, the insertion is only simulated
                Returns:
                    The remaining ItemStack that was not inserted (if the entire stack is accepted, then return an empty ItemStack).
                    May be the same as the input ItemStack if unchanged, otherwise a new ItemStack.
                    The returned ItemStack can be safely modified after.
            */
            @Nonnull
            @Override
            public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
                /*IPedestalFilter filter = getIPedestalFilter();
                if(filter != null)
                {
                    if(filter.getFilterDirection().insert())
                    {
                        int countAllowed = filter.canAcceptCountItems(getPedestal(),stack);
                        ItemStack modifiedStack = stack.copy();
                        super.insertItem((slot>getSlots())?(0):(slot), modifiedStack, simulate);
                        ItemStack returnedStack = modifiedStack.copy();
                        returnedStack.setCount(stack.getCount() - countAllowed);
                        return returnedStack;
                    }
                }*/

                return super.insertItem((slot>getSlots())?(0):(slot), stack, simulate);
            }

            /*
                Extracts an ItemStack from the given slot.
                The returned value must be empty if nothing is extracted,
                otherwise its stack size must be less than or equal to amount and ItemStack.getMaxStackSize().
                Params:
                    slot – Slot to extract from.
                    amount – Amount to extract (may be greater than the current stack's max limit)
                    simulate – If true, the extraction is only simulated
                Returns:
                    ItemStack extracted from the slot, must be empty if nothing can be extracted.
                    The returned ItemStack can be safely modified after, so item handlers should return a new or copied stack.
             */
            @Nonnull
            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                IFilterItem filter = getIFilterItem();
                int actualSlot = (slot > getSlots()) ? 0 : slot;
                if (filter == null || !filter.getFilterDirection().extract()) {
                    return super.extractItem(actualSlot, amount, simulate);
                } else {
                    ItemStack stackInSlot = getStackInSlot(actualSlot);
                    return super.extractItem(actualSlot, Math.min(amount, filter.canAcceptCountItems(getStorageSlabEntity(), getFilterInBlockEntity(), stackInSlot.getMaxStackSize(), getSlotSizeLimit(), stackInSlot)), simulate);
                }
            }
        };
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemCapability.cast();
        }
        return super.getCapability(cap, side);
    }

    public void dropInventoryItems(Level worldIn, BlockPos pos) {
        MowLibItemUtils.dropInventoryItems(worldIn, pos, itemHandler);
    }

    /*============================================================================
    ==============================================================================
    ===========================     ITEM START       =============================
    ==============================================================================
    ============================================================================*/

    public boolean hasItem() {
        int firstPartialOrNonEmptySlot = 0;
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            ItemStack stackInSlot = itemHandler.getStackInSlot(i);
            if(stackInSlot.getCount() < stackInSlot.getMaxStackSize() || stackInSlot.isEmpty()) {
                firstPartialOrNonEmptySlot = i;
                break;
            }
        }

        return !itemHandler.getStackInSlot(firstPartialOrNonEmptySlot).isEmpty();
    }

    public Optional<Integer> maybeFirstNonEmptySlot() {
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            if(!itemHandler.getStackInSlot(i).isEmpty()) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    public boolean hasItemFirst() {
        return maybeFirstNonEmptySlot().isPresent();
    }

    public Optional<Integer> maybeLastNonEmptySlot() {
        for (int i = itemHandler.getSlots() - 1; i >= 0; i--) {
            if(!itemHandler.getStackInSlot(i).isEmpty()) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    public Optional<Integer> maybeFirstSlotWithSpaceForMatchingItem(ItemStack stackToMatch) {
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            ItemStack stackInSlot = itemHandler.getStackInSlot(i);
            if (stackInSlot.isEmpty() || (stackInSlot.getCount() < stackInSlot.getMaxStackSize() && ItemHandlerHelper.canItemStacksStack(stackInSlot, stackToMatch))) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    public boolean hasSpaceForItem(ItemStack stackToMatch) {
        return maybeFirstSlotWithSpaceForMatchingItem(stackToMatch).isPresent();
    }

    public ItemStack getItemInPedestal() {
        return maybeFirstNonEmptySlot().map(itemHandler::getStackInSlot).orElse(ItemStack.EMPTY);
    }

    public ItemStack getMatchingItemInPedestalOrEmptySlot(ItemStack stackToMatch) {
        return maybeFirstSlotWithSpaceForMatchingItem(stackToMatch).map(itemHandler::getStackInSlot).orElse(ItemStack.EMPTY);
    }

    public ItemStack getItemInPedestalFirst() {
        return maybeFirstNonEmptySlot().map(itemHandler::getStackInSlot).orElse(ItemStack.EMPTY);
    }

    public int getPedestalSlots() { return itemHandler.getSlots(); }

    public List<ItemStack> getItemStacks() {
        List<ItemStack> listed = new ArrayList<>();
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            if (!itemHandler.getStackInSlot(i).isEmpty()) {
                listed.add(itemHandler.getStackInSlot(i));
            }
        }
        return listed;
    }

    public List<ItemStack> getCurrentItemStacks() {
        List<ItemStack> listed = new ArrayList<>();
        int currentpage = getCurrentPage()+1;
        int maxPage = currentpage*9;
        int minPage = maxPage-9;
        for (int i = minPage; i < maxPage; i++) {
            listed.add(itemHandler.getStackInSlot(i));
        }
        return listed;
    }

    public ItemStack getItemInPedestal(int slot) {
        if (itemHandler.getSlots() > slot) {
            return itemHandler.getStackInSlot(slot);
        } else {
            return ItemStack.EMPTY;
        }
    }

    public ItemStack removeItem(int numToRemove, boolean simulate) {
        return maybeLastNonEmptySlot().map(slot -> itemHandler.extractItem(slot, numToRemove, simulate)).orElse(ItemStack.EMPTY);
    }

    public ItemStack removeItemStack(ItemStack stackToRemove, boolean simulate) {
        int matchingSlotNumber = 0;
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            if(ItemHandlerHelper.canItemStacksStack(itemHandler.getStackInSlot(i), stackToRemove)) {
                matchingSlotNumber = i;
                break;
            }
        }
        return itemHandler.extractItem(matchingSlotNumber, stackToRemove.getCount(), simulate);
    }

    public ItemStack removeItem(boolean simulate) {
        return maybeLastNonEmptySlot().map(slot -> itemHandler.extractItem(slot, itemHandler.getStackInSlot(slot).getCount(), simulate))
                .orElse(ItemStack.EMPTY);
    }

    //If resulting insert stack is empty it means the full stack was inserted
    public boolean addItem(ItemStack itemFromBlock, boolean simulate) {
        return addItemStack(itemFromBlock, simulate).isEmpty();
    }

    //Return result not inserted, if all inserted return empty stack
    public ItemStack addItemStack(ItemStack itemFromBlock, boolean simulate) {
        return maybeFirstSlotWithSpaceForMatchingItem(itemFromBlock).map(slot -> {
            if (itemHandler.isItemValid(slot, itemFromBlock)) {
                ItemStack returner = itemHandler.insertItem(slot, itemFromBlock.copy(), simulate);
                if (!simulate) update();
                return returner;
            }
            return itemFromBlock;
        }).orElse(itemFromBlock);
    }

    public int getSlotSizeLimit() {
        return maybeFirstNonEmptySlot().map(itemHandler::getSlotLimit).orElse(itemHandler.getSlotLimit(0));
    }

    public int getPagesCount()
    {
        return itemHandler.getSlots()/9;
    }

    public int getCurrentPage()
    {
        return (this.currentPage<=0)?(0):(this.currentPage);
    }

    public void iterateCurrentPage(int value)
    {
        if(getPagesCount()>1)
        {
            if((getCurrentPage()+value < getPagesCount()) && (getCurrentPage()+value >= 0))
            {
                this.currentPage +=value;
            }
            else if(getCurrentPage()+value >= getPagesCount())
            {
                this.currentPage = 0;
            }
            else
            {
                this.currentPage = getPagesCount()-1;
            }
            update();
        }
    }

    public void setCurrentPage(int value)
    {
        if(value<=getPagesCount())
        {
            if((value < getPagesCount()) && (value >= 0))
            {
                this.currentPage = value;
            }
            else if(value >= getPagesCount())
            {
                this.currentPage = 0;
            }
            else
            {
                this.currentPage = getPagesCount()-1;
            }
            update();
        }
    }


    /*============================================================================
    ==============================================================================
    ===========================      ITEM END        =============================
    ==============================================================================
    ============================================================================*/

    public void triggerNeighborChange()
    {
        setCurrentPage(getStorageSlabEntity().getRedstonePower());
    }

    @Override
    public void load(CompoundTag p_155245_) {
        super.load(p_155245_);
        CompoundTag invTag = p_155245_.getCompound(StorageSlabsReferences.MODID + "_slabstorage");
        itemHandler.deserializeNBT(invTag);
        this.currentPage = p_155245_.getInt(StorageSlabsReferences.MODID + "_currentpage");
        this.pagesCount = p_155245_.getInt(StorageSlabsReferences.MODID + "_bookpagescount");
    }

    @Override
    public CompoundTag save(CompoundTag p_58888_) {
        super.save(p_58888_);
        p_58888_.put(StorageSlabsReferences.MODID + "_slabstorage", itemHandler.serializeNBT());
        p_58888_.putInt(StorageSlabsReferences.MODID + "_currentpage", this.currentPage);
        p_58888_.putInt(StorageSlabsReferences.MODID + "_bookpagescount", this.pagesCount);
        return p_58888_;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        itemCapability.invalidate();
    }
}
