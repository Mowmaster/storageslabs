package com.mowmaster.storageslabs.Blocks;

import com.mowmaster.mowlib.BlockEntities.MowLibBaseFilterableBlock;
import com.mowmaster.mowlib.MowLibUtils.MowLibColorReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class BaseStorageSlabBlock extends MowLibBaseFilterableBlock implements SimpleWaterloggedBlock, EntityBlock {

    //TF
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    //NESW TB
    public static final DirectionProperty PLACEMENTFACING = BlockStateProperties.FACING;
    //NESW
    public static final DirectionProperty GUIROTATION = BlockStateProperties.HORIZONTAL_FACING;

    //From Base Class
    //public static final IntegerProperty FILTER_STATUS = IntegerProperty.create("filter_status", 0, 2);
    //public static final BooleanProperty WORKCARD_STATUS = BooleanProperty.create("workcard_status");
    //public static final BooleanProperty LIT;
    //public static final IntegerProperty REDSTONE_STATUS;

    protected final VoxelShape PLACEMENT_UP;
    protected final VoxelShape PLACEMENT_DOWN;
    protected final VoxelShape PLACEMENT_NORTH;
    protected final VoxelShape PLACEMENT_EAST;
    protected final VoxelShape PLACEMENT_SOUTH;
    protected final VoxelShape PLACEMENT_WEST;

    public BaseStorageSlabBlock(Properties p_49795_) {
        super(p_49795_);
        this.registerDefaultState(MowLibColorReference.addColorToBlockState(this.defaultBlockState(),MowLibColorReference.DEFAULTCOLOR).setValue(WATERLOGGED, Boolean.FALSE).setValue(PLACEMENTFACING, Direction.UP).setValue(GUIROTATION, Direction.NORTH).setValue(LIT, Boolean.FALSE).setValue(FILTER_STATUS, 0));
        this.PLACEMENT_UP = Shapes.or(
                Block.box(3.0D, 0.0D, 3.0D, 13.0D, 2.0D, 13.0D),
                Block.box(5.0D, 2.0D, 5.0D, 11.0D, 10.0D, 11.0D),
                Block.box(4.0D, 10.0D, 4.0D, 12.0D, 12.0D, 12.0D)
        );
        this.PLACEMENT_DOWN = Shapes.or(
                Block.box(3.0D, 0.0D, 3.0D, 13.0D, 2.0D, 13.0D),
                Block.box(5.0D, 2.0D, 5.0D, 11.0D, 10.0D, 11.0D),
                Block.box(4.0D, 10.0D, 4.0D, 12.0D, 12.0D, 12.0D)
        );
        this.PLACEMENT_NORTH = Shapes.or(
                Block.box(3.0D, 0.0D, 3.0D, 13.0D, 2.0D, 13.0D),
                Block.box(5.0D, 2.0D, 5.0D, 11.0D, 10.0D, 11.0D),
                Block.box(4.0D, 10.0D, 4.0D, 12.0D, 12.0D, 12.0D)
        );
        this.PLACEMENT_EAST = Shapes.or(
                Block.box(3.0D, 0.0D, 3.0D, 13.0D, 2.0D, 13.0D),
                Block.box(5.0D, 2.0D, 5.0D, 11.0D, 10.0D, 11.0D),
                Block.box(4.0D, 10.0D, 4.0D, 12.0D, 12.0D, 12.0D)
        );
        this.PLACEMENT_SOUTH = Shapes.or(
                Block.box(3.0D, 0.0D, 3.0D, 13.0D, 2.0D, 13.0D),
                Block.box(5.0D, 2.0D, 5.0D, 11.0D, 10.0D, 11.0D),
                Block.box(4.0D, 10.0D, 4.0D, 12.0D, 12.0D, 12.0D)
        );
        this.PLACEMENT_WEST = Shapes.or(
                Block.box(3.0D, 0.0D, 3.0D, 13.0D, 2.0D, 13.0D),
                Block.box(5.0D, 2.0D, 5.0D, 11.0D, 10.0D, 11.0D),
                Block.box(4.0D, 10.0D, 4.0D, 12.0D, 12.0D, 12.0D)
        );

    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p_153215_, BlockState p_153216_) {
        return null;
    }
}
