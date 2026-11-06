package com.mowmaster.storageslabs.block;

import com.mowmaster.mowlib.filter.ITransportFilter;
import com.mowmaster.mowlib.items.CBColorPickerItem;
import com.mowmaster.mowlib.paging.PageArrows;
import com.mowmaster.mowlib.tint.IColorableBlock;
import com.mowmaster.mowlib.util.CBPixelBox;
import com.mowmaster.storageslabs.blockentity.BaseSlabBlockEntity;
import com.mowmaster.storageslabs.blockentity.CompactingSlabBlockEntity;
import com.mowmaster.storageslabs.blockentity.StorageSlabBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// A storage slab: half a block that faces any of the six ways (its storage side points away from the face it was
// placed against, like a pedestal). The models are built facing UP; the blockstate turns them (down = x 180,
// sideways = x 90 then y), and MowLib's CBPixelBox#rotateFromUp turns shapes, cells and arrows the same way.
// - The top (the face it faces): cells you put things in and take things out of; the 3x3's arrows turn the page
// - Any other face: dyes colour it (MowLib IColorableBlock); everything else places blocks against it as normal
// - Offhand filter + right-click: puts the filter in; offhand Filter Tool + left-click takes it out (event/SlabEvents)
// - Left-clicks on the top are handled in event/SlabEvents, so holding left-click never mines it there
// - Breaking it keeps everything in the item (loot table copies the block entity's components)
// FILTER_STATUS (0 none, 1 whitelist, 2 blacklist) only changes the band's tint, so it shares one model.
public class StorageSlabBlock extends Block implements IColorableBlock, SimpleWaterloggedBlock
{
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final IntegerProperty FILTER_STATUS = IntegerProperty.create("filter_status", 0, 2);

    // The model's shape, facing up: the slab (0-8) and the rails around its top (8-9)
    private static final List<CBPixelBox> BOXES = List.of(
            new CBPixelBox(0, 0, 0, 16, 8, 16),
            new CBPixelBox(0, 8, 0, 2, 9, 16),
            new CBPixelBox(14, 8, 0, 16, 9, 16),
            new CBPixelBox(2, 8, 0, 14, 9, 2),
            new CBPixelBox(2, 8, 14, 14, 9, 16));

    private final SlabKind kind;
    // EnumMap: a map keyed by an enum, stored as a plain array (fast). One shape per facing, worked out once.
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    public StorageSlabBlock(Properties properties, SlabKind kind)
    {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.UP)
                .setValue(WATERLOGGED, false)
                .setValue(FILTER_STATUS, 0));
        for (Direction facing : Direction.values())
        {
            shapes.put(facing, CBPixelBox.union(BOXES.stream().map(box -> box.rotateFromUp(facing)).toList()));
        }
    }

    public SlabKind kind()
    {
        return kind;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING, WATERLOGGED, FILTER_STATUS);
    }

    @Override
    protected @NotNull VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return shapes.get(state.getValue(FACING));
    }

    // ---- Placing: the top faces away from the clicked face; two slabs placed onto each other's tops face apart ----

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        Direction direction = context.getClickedFace();
        BlockState clicked = context.getLevel().getBlockState(context.getClickedPos().relative(direction.getOpposite()));
        Direction facing = clicked.is(this) && clicked.getValue(FACING) == direction ? direction.getOpposite() : direction;
        boolean inWater = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
        return defaultBlockState().setValue(FACING, facing).setValue(WATERLOGGED, inWater);
    }

    // Placed from an item that carries a filter: the band shows it straight away (the block entity has just read the
    // item's components, the block state doesn't know yet)
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BaseSlabBlockEntity slab && state.getValue(FILTER_STATUS) != slab.filterStatus())
        {
            level.setBlock(pos, state.setValue(FILTER_STATUS, slab.filterStatus()), Block.UPDATE_ALL);
        }
    }

    // ---- Block entity ----

    @Override
    public boolean usesTintBlockEntity()
    {
        return false;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return kind == SlabKind.COMPACTING ? new CompactingSlabBlockEntity(pos, state) : new StorageSlabBlockEntity(pos, state);
    }

    // Server only: sends content changes from pipes to clients, at most every 4 ticks
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        if (level.isClientSide()) return null;
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof BaseSlabBlockEntity slab && tickLevel instanceof ServerLevel serverLevel) slab.serverTick(serverLevel);
        };
    }

    // ---- Colours: region 0 is the body (dyeable), region 1 the band (the filter's colour; never dyed) ----

    @Override
    public int getTintRegionCount()
    {
        return 2;
    }

    @Override
    public int getTintRegion(BlockState state, BlockHitResult hit)
    {
        return 0;
    }

    // ---- Where a click landed ----

    // The cell clicked on the top, or -1 (another face, the rails, the arrows)
    public int cellAt(BlockState state, BlockHitResult hit)
    {
        Direction facing = state.getValue(FACING);
        if (hit.getDirection() != facing) return -1;
        Vec3 pixel = CBPixelBox.pixelInside(hit);
        List<CBPixelBox> cells = kind.cells();
        for (int cell = 0; cell < cells.size(); cell++)
        {
            if (cells.get(cell).rotateFromUp(facing).contains(pixel)) return cell;
        }
        return -1;
    }

    // -1 / +1 for the 3x3's arrows, 0 for anything else
    public int arrowAt(BlockState state, BlockHitResult hit)
    {
        PageArrows arrows = kind.arrows();
        return arrows == null ? 0 : arrows.arrowAtFromUp(state.getValue(FACING), hit);
    }

    // ---- Right-click ----

    @Override
    protected @NotNull InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                   Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (!(level.getBlockEntity(pos) instanceof BaseSlabBlockEntity slab)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (hand == InteractionHand.MAIN_HAND && tryInsertFilter(level, player, slab)) return InteractionResult.SUCCESS;

        int turn = arrowAt(state, hit);
        if (turn != 0)
        {
            if (!level.isClientSide() && slab instanceof com.mowmaster.mowlib.paging.IPageHolder pages)
            {
                pages.turnPage(turn);
                PageArrows.playTurnSound(level, pos);
            }
            return InteractionResult.SUCCESS;
        }

        int cell = cellAt(state, hit);
        if (cell < 0)
        {
            // Not the top: dyes and the colour picker colour it; anything else acts normally (placing blocks...)
            if (stack.getItem() instanceof CBColorPickerItem || IColorableBlock.dyeColorOf(stack) != null)
            {
                return tryDyeBlock(stack, state, level, pos, player, hit);
            }
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        // An empty hand is handled by useWithoutItem (vanilla calls it after TRY_WITH_EMPTY_HAND)
        if (stack.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide()) slab.clickInsert(player, hand, cell);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (!(level.getBlockEntity(pos) instanceof BaseSlabBlockEntity slab)) return InteractionResult.PASS;
        if (tryInsertFilter(level, player, slab)) return InteractionResult.SUCCESS;
        int turn = arrowAt(state, hit);
        if (turn != 0)
        {
            if (!level.isClientSide() && slab instanceof com.mowmaster.mowlib.paging.IPageHolder pages)
            {
                pages.turnPage(turn);
                PageArrows.playTurnSound(level, pos);
            }
            return InteractionResult.SUCCESS;
        }
        int cell = cellAt(state, hit);
        if (cell < 0) return InteractionResult.PASS;
        if (!level.isClientSide()) slab.clickInsert(player, InteractionHand.MAIN_HAND, cell);
        return InteractionResult.SUCCESS;
    }

    // A filter in the offhand goes in (main hand empty, or holding anything: the offhand filter wins)
    private static boolean tryInsertFilter(Level level, Player player, BaseSlabBlockEntity slab)
    {
        if (!(player.getOffhandItem().getItem() instanceof ITransportFilter)) return false;
        if (!level.isClientSide())
        {
            if (!slab.insertFilter(player.getOffhandItem()))
            {
                player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.storageslabs.filter_present"));
            }
        }
        return true;
    }

    // ---- Breaking: the contents stay in the item ----

    // Creative players get no drops from the loot table: like a shulker box, a creative player breaking a slab that
    // holds something (or is dyed, upgraded, filtered) still gets the item, so nothing is lost by accident
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player)
    {
        if (!level.isClientSide() && player.isCreative() && level.getBlockEntity(pos) instanceof BaseSlabBlockEntity slab
                && !slab.collectComponents().isEmpty())
        {
            ItemStack drop = new ItemStack(this);
            drop.applyComponents(slab.collectComponents());
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // ---- Comparators: 0 when empty, 1-15 by how full ----

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state)
    {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction)
    {
        if (!(level.getBlockEntity(pos) instanceof BaseSlabBlockEntity slab) || slab.isContentsEmpty()) return 0;
        return 1 + Math.round(Math.min(1f, slab.fullness()) * 14);
    }

    // ---- Waterlogging ----

    @Override
    protected @NotNull FluidState getFluidState(BlockState state)
    {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected @NotNull BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                              Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random)
    {
        if (state.getValue(WATERLOGGED)) ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
    }

    // ---- Rotation (structures, wrenches) ----

    @Override
    protected @NotNull BlockState rotate(BlockState state, Rotation rotation)
    {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected @NotNull BlockState mirror(BlockState state, Mirror mirror)
    {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
