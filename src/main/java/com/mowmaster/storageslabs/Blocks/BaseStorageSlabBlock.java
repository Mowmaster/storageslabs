package com.mowmaster.storageslabs.Blocks;

import com.google.common.collect.ImmutableMap;
import com.mowmaster.mowlib.BlockEntities.MowLibBaseFilterableBlock;
import com.mowmaster.mowlib.MowLibUtils.MowLibColorReference;
import com.mowmaster.mowlib.api.TransportAndStorage.IFilterItem;
import com.mowmaster.storageslabs.Registry.DeferredBlockEntityTypes_StorageSlabs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class BaseStorageSlabBlock extends MowLibBaseFilterableBlock implements SimpleWaterloggedBlock, EntityBlock {

    //TF
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    //NESW TB
    public static final DirectionProperty PLACEMENTFACING = BlockStateProperties.FACING;
    //NESW
    public static final DirectionProperty GUIROTATION = DirectionProperty.create("horfacing", Direction.Plane.HORIZONTAL);

    //From Base Class
    //public static final IntegerProperty FILTER_STATUS = IntegerProperty.create("filter_status", 0, 2);
    //public static final BooleanProperty WORKCARD_STATUS = BooleanProperty.create("workcard_status");
    //public static final BooleanProperty LIT;
    //public static final IntegerProperty REDSTONE_STATUS;

    //On Ground Facing UP
    protected final VoxelShape PLACEMENT_UP;
    //On Ceiling Facing DOWN
    protected final VoxelShape PLACEMENT_DOWN;
    //On Wall facing [opposite direction], placed facing [direction]
    protected final VoxelShape PLACEMENT_NORTH;
    protected final VoxelShape PLACEMENT_EAST;
    protected final VoxelShape PLACEMENT_SOUTH;
    protected final VoxelShape PLACEMENT_WEST;

    public BaseStorageSlabBlock(Properties p_49795_) {
        super(p_49795_);
        this.registerDefaultState(MowLibColorReference.addColorToBlockState(this.defaultBlockState(),MowLibColorReference.DEFAULTCOLOR).setValue(WATERLOGGED, Boolean.FALSE).setValue(PLACEMENTFACING, Direction.UP).setValue(GUIROTATION, Direction.NORTH).setValue(LIT, Boolean.FALSE).setValue(FILTER_STATUS, 0));
        this.PLACEMENT_UP = Shapes.or(
                Block.box(1.0D, 0.0D, 1.0D, 15.0D, 1.0D, 15.0D),
                Block.box(1.5D, 1.0D, 1.5D, 14.5D, 3.0D, 14.5D),
                Block.box(1.0D, 3.0D, 1.0D, 15.0D, 4.0D, 15.0D)
        );
        this.PLACEMENT_DOWN = Shapes.or(
                Block.box(1.0D, 12.0D, 1.0D, 15.0D, 13.0D, 15.0D),
                Block.box(1.5D, 13.0D, 1.5D, 14.5D, 15.0D, 14.5D),
                Block.box(1.0D, 15.0D, 1.0D, 15.0D, 16.0D, 15.0D)
        );
        this.PLACEMENT_NORTH = Shapes.or(
                Block.box(0.0D, 1.0D, 1.0D, 1.0D, 15.0D, 15.0D),
                Block.box(1.0D, 1.5D, 1.5D, 3.0D, 14.5D, 14.5D),
                Block.box(3.0D, 1.0D, 1.0D, 4.0D, 15.0D, 15.0D)
        );
        this.PLACEMENT_EAST = Shapes.or(
                Block.box(1.0D, 1.0D, 0.0D, 15.0D, 15.0D, 1.0D),
                Block.box(1.5D, 1.5D, 1.0D, 14.5D, 14.5D, 3.0D),
                Block.box(1.0D, 1.0D, 3.0D, 15.0D, 15.0D, 4.0D)
        );
        this.PLACEMENT_SOUTH = Shapes.or(
                Block.box(0.0D, 1.0D, 1.0D, 1.0D, 15.0D, 15.0D),
                Block.box(1.0D, 1.5D, 1.5D, 3.0D, 14.5D, 14.5D),
                Block.box(3.0D, 1.0D, 1.0D, 4.0D, 15.0D, 15.0D)
        );
        this.PLACEMENT_WEST = Shapes.or(
                Block.box(1.0D, 1.0D, 0.0D, 15.0D, 15.0D, 1.0D),
                Block.box(1.5D, 1.5D, 1.0D, 14.5D, 14.5D, 3.0D),
                Block.box(1.0D, 1.0D, 3.0D, 15.0D, 15.0D, 4.0D)
        );

    }

    @Override
    public VoxelShape getShape(BlockState p_60555_, BlockGetter p_60556_, BlockPos p_60557_, CollisionContext p_60558_) {
        Direction direction = p_60555_.getValue(PLACEMENTFACING);
        return switch (direction) {
            case NORTH -> this.PLACEMENT_NORTH;
            case SOUTH -> this.PLACEMENT_SOUTH;
            case EAST -> this.PLACEMENT_EAST;
            case WEST -> this.PLACEMENT_WEST;
            case DOWN -> this.PLACEMENT_DOWN;
            default -> this.PLACEMENT_UP;
        };
    }

    @Override
    public BlockState updateShape(BlockState p_60541_, Direction p_60542_, BlockState p_60543_, LevelAccessor p_60544_, BlockPos p_60545_, BlockPos p_60546_) {
        return super.updateShape(p_60541_, p_60542_, p_60543_, p_60544_, p_60545_, p_60546_);
    }

    @Override
    public BlockState rotate(BlockState state, LevelAccessor level, BlockPos pos, Rotation direction) {
        return super.rotate(state, level, pos, direction);
    }

    @Override
    public BlockState mirror(BlockState p_60528_, Mirror p_60529_) {
        return super.mirror(p_60528_, p_60529_);
    }

    @Override
    public FluidState getFluidState(BlockState p_60577_) {
        return p_60577_.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(p_60577_);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_56120_) {
        p_56120_.add(WATERLOGGED, PLACEMENTFACING, GUIROTATION);
    }

    @Override
    public @Nullable PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.IGNORE;
    }

    @Override
    public void attack(BlockState p_60499_, Level p_60500_, BlockPos p_60501_, Player p_60502_) {
        /*if(!p_60500_.isClientSide()) {
            if (!(p_60502_ instanceof FakePlayer)) {
                BlockEntity blockEntity = p_60500_.getBlockEntity(p_60501_);
                if (blockEntity instanceof BaseBookBlockEntity blockBookEntity) {
                    ItemStack itemInHand = p_60502_.getMainHandItem();
                    ItemStack itemInOffHand = p_60502_.getOffhandItem();

                    if(blockBookEntity.hasFilter() && itemInOffHand.is(com.mowmaster.mowlib.Registry.DeferredRegisterItems.TOOL_FILTERTOOL.get()))
                    {
                        ItemHandlerHelper.giveItemToPlayer(p_60502_,blockBookEntity.removeFilter(null));
                        blockBookEntity.actionOnFilterRemovedFromBlockEntity(1);
                    }
                    else if(blockBookEntity.hasItemFirst())
                    {
                        if(p_60502_.isShiftKeyDown())
                        {
                            ItemHandlerHelper.giveItemToPlayer(p_60502_,blockBookEntity.removeItemFromSlot(blockBookEntity.getCurrentSlot(),false));
                        }
                        else
                        {
                            ItemHandlerHelper.giveItemToPlayer(p_60502_,blockBookEntity.removeItemFromSlotCount(blockBookEntity.getCurrentSlot(),1,false));
                        }
                    }
                }
            }
        }*/

        super.attack(p_60499_, p_60500_, p_60501_, p_60502_);
    }

    @Override
    public InteractionResult use(BlockState p_60503_, Level p_60504_, BlockPos p_60505_, Player p_60506_, InteractionHand p_60507_, BlockHitResult p_60508_) {
        super.use(p_60503_, p_60504_, p_60505_, p_60506_, p_60507_, p_60508_);

        /*ItemStack itemInHand = p_60506_.getMainHandItem();
        if(!p_60504_.isClientSide())
        {
            BlockEntity blockEntity = p_60504_.getBlockEntity(p_60505_);
            if(blockEntity instanceof BaseBookBlockEntity blockBookEntity)
            {
                ItemStack itemInOffHand = p_60506_.getOffhandItem();
                Direction blockDirection = p_60503_.getValue(HorizontalDirectionalBlock.FACING);
                Direction direction = p_60508_.getDirection();
                BlockPos blockpos = p_60508_.getBlockPos().relative(direction);
                Vec3 vec3 = p_60508_.getLocation().subtract((double) blockpos.getX(), (double) blockpos.getY(), (double) blockpos.getZ());
                int slotSwap = getHitValue(vec3,blockDirection);
                if(slotSwap != 0)
                {
                    blockBookEntity.iterateCurrentSlot(slotSwap);
                }
                else if(itemInOffHand.getItem() instanceof IFilterItem)
                {
                    if(blockBookEntity.attemptAddFilter(itemInOffHand,null)) {
                        return InteractionResult.SUCCESS;
                    }
                }
                else if(!itemInHand.isEmpty())
                {
                    ItemStack stackNotInsert = blockBookEntity.addItemStackToSlot(blockBookEntity.getCurrentSlot(),itemInHand,true);
                    if(itemInHand.getCount() > stackNotInsert.getCount())
                    {
                        int shrinkAmount = itemInHand.getCount() - blockBookEntity.addItemStackToSlot(blockBookEntity.getCurrentSlot(),itemInHand,false).getCount();
                        itemInHand.shrink(shrinkAmount);
                        return InteractionResult.SUCCESS;
                    }
                    else return InteractionResult.SUCCESS;
                }
                else if(itemInHand.isEmpty() && p_60506_.isShiftKeyDown())
                {
                    putInPlayersHandAndRemove(p_60503_,p_60504_,p_60505_,p_60506_,p_60507_);
                }
                else return InteractionResult.SUCCESS;
            }
        }*/

        return InteractionResult.SUCCESS;
    }

    private static int getHitValue(Vec3 vec3, Direction blockPlacementDirection) {
        /*System.out.println("BlockDirection: " + blockPlacementDirection.getName());
        System.out.println("HitXCord: " + vec3.x);
        System.out.println("HitYCord: " + vec3.y);
        System.out.println("HitZCord: " + vec3.z);*/
        int returner = 0;
        switch (blockPlacementDirection) {
            case NORTH:
                if(vec3.z>= 0.75D && vec3.z < 0.90D)
                {
                    if(vec3.x>= 0.00D && vec3.x < 0.125D)
                    {
                        //System.out.println("NORTH Direction - Back Arrow");System.out.println("Trigger N1");
                        returner = -1;
                    }
                    else if(vec3.x>= 0.85D && vec3.x < 1.00D)
                    {

                        //System.out.println("Trigger N2");System.out.println("NORTH Direction - Forward Arrow");
                        returner = 1;

                    }
                }
                break;
            case SOUTH:
                if(vec3.z>= 0.125D && vec3.z < 0.25D)
                {
                    if(vec3.x>= 0.85D && vec3.x < 1.00D)
                    {
                        //System.out.println("Trigger S1");System.out.println("SOUTH Direction - Back Arrow");
                        returner = -1;
                    }
                    else if(vec3.x>= 0.00D && vec3.x < 0.125D)
                    {
                        //System.out.println("Trigger S2");System.out.println("SOUTH Direction - Forward Arrow");
                        returner = 1;
                    }
                }
                break;
            case WEST:
                if(vec3.x>= 0.75D && vec3.x < 0.90D)
                {
                    if(vec3.z>= 0.85D && vec3.z < 1.00D)
                    {
                        //System.out.println("Trigger W1");System.out.println("West Direction - Back Arrow");
                        returner = -1;
                    }
                    else if(vec3.z>= 0.00D && vec3.z < 0.13D)
                    {
                        //System.out.println("Trigger W2");System.out.println("West Direction - Forward Arrow");
                        returner = 1;
                    }
                }
                break;
            case EAST:
                if(vec3.x>= 0.125D && vec3.x < 0.25D)
                {
                    if(vec3.z>= 0.00D && vec3.z < 0.125D)
                    {
                        //System.out.println("Trigger E1");System.out.println("EAST Direction - Back Arrow");
                        returner = -1;
                    }
                    else if(vec3.z>= 0.85D && vec3.z < 1.0D)
                    {
                        //System.out.println("Trigger E2");System.out.println("EAST Direction - Forward Arrow");
                        returner = 1;
                    }
                }
                break;
            default:
                returner = 0;
                break;
        }

        return returner;
    }

    @Override
    public void playerDestroy(Level p_49827_, Player p_49828_, BlockPos p_49829_, BlockState p_49830_, @javax.annotation.Nullable BlockEntity p_49831_, ItemStack p_49832_) {
        /*if(!p_49827_.isClientSide())
        {
            if (p_49830_.getBlock() instanceof BasePedestalBlock) {
                if (!p_49827_.isClientSide && !p_49828_.isCreative()) {
                    ItemStack itemstack = new ItemStack(this);
                    int getColor = MowLibColorReference.getColorFromStateInt(p_49830_);
                    ItemStack newStack = MowLibColorReference.addColorToItemStack(itemstack,getColor);
                    newStack.setCount(1);
                    ItemEntity itementity = new ItemEntity(p_49827_, (double)p_49829_.getX() + 0.5D, (double)p_49829_.getY() + 0.5D, (double)p_49829_.getZ() + 0.5D, newStack);
                    itementity.setDefaultPickUpDelay();
                    p_49827_.addFreshEntity(itementity);
                }
            }
        }*/
        super.playerDestroy(p_49827_, p_49828_, p_49829_, p_49830_, p_49831_, p_49832_);
        p_49827_.removeBlock(p_49829_,false);
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level world, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {

        if(player instanceof FakePlayer) {
            return false;
        }

        if (player.isCreative()) {
            if (player.getOffhandItem().getItem().equals(com.mowmaster.mowlib.Registry.DeferredRegisterItems.TOOL_DEVTOOL.get()))
                return willHarvest || super.onDestroyedByPlayer(state, world, pos, player, willHarvest, fluid);
            else
                attack(state, world, pos, player);

            return false;
        }

        return willHarvest || super.onDestroyedByPlayer(state, world, pos, player, willHarvest, fluid);
    }

    @Override
    public void onRemove(BlockState p_60515_, Level p_60516_, BlockPos p_60517_, BlockState p_60518_, boolean p_60519_) {
        if(p_60515_.getBlock() != p_60518_.getBlock())
        {
            /*BlockEntity blockEntity = p_60516_.getBlockEntity(p_60517_);
            if(blockEntity instanceof BasePedestalBlockEntity pedestal) {
                pedestal.dropInventoryItems(p_60516_,p_60517_);
                //Method for upgrades to do things before removal
                pedestal.actionOnRemovedFromPedestal(0);
                pedestal.dropInventoryItemsPrivate(p_60516_,p_60517_);

                //Fixed to drop an item and not spill out
                pedestal.dropLiquidsInWorld(p_60516_,p_60517_);
                pedestal.removeEnergyFromBrokenPedestal(p_60516_,p_60517_);
                pedestal.dropXPInWorld(p_60516_,p_60517_);
                pedestal.dropDustInWorld(p_60516_,p_60517_);
                p_60516_.updateNeighbourForOutputSignal(p_60517_,p_60518_.getBlock());
            }*/
            p_60516_.removeBlock(p_60517_,false);
            super.onRemove(p_60515_, p_60516_, p_60517_, p_60518_, p_60519_);
        }
    }

    public RenderShape getRenderShape(BlockState p_50950_) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return DeferredBlockEntityTypes_StorageSlabs.BASESTORAGESLAB.get().create(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : (level0, pos, state0, blockEntity) -> ((BaseStorageSlabBlockEntity) blockEntity).tick();
    }
}
