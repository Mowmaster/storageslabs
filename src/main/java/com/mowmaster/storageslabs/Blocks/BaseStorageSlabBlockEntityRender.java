package com.mowmaster.storageslabs.Blocks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

import static com.mowmaster.storageslabs.Blocks.BaseStorageSlabBlock.PLACEMENTFACING;

public class BaseStorageSlabBlockEntityRender implements BlockEntityRenderer<BaseStorageSlabBlockEntity> {
    @Override
    public void render(BaseStorageSlabBlockEntity p_112307_, float p_112308_, PoseStack p_112309_, MultiBufferSource p_112310_, int p_112311_, int p_112312_) {
        if (!p_112307_.isRemoved())
        {
            List<ItemStack> listed = p_112307_.getCurrentItemStacks();
            BlockPos pos = p_112307_.getPos();
            Level level = p_112307_.getLevel();
            Direction facing = p_112307_.getBlockState().getValue(PLACEMENTFACING);

            if(facing== Direction.UP)//when placed on ground
            {
                //renderTile(world,p_112309_,p_112310_,coin,stack,p_112311_,p_112312_,renderAugmentType);
                //renderTile(world,p_112309_,p_112310_,stack,coin,p_112311_,p_112312_,renderAugmentType);
                renderItemFixed(level,p_112309_,p_112310_,listed.get(0),0.3125D,0.3D,0.3125D,p_112311_,p_112312_);
                renderItemFixed(level,p_112309_,p_112310_,listed.get(1),0.5D,0.3D,0.3125D,p_112311_,p_112312_);
                renderItemFixed(level,p_112309_,p_112310_,listed.get(2),0.6875D,0.3D,0.3125D,p_112311_,p_112312_);

                renderItemFixed(level,p_112309_,p_112310_,listed.get(3),0.3125D,0.3D,0.5D,p_112311_,p_112312_);
                renderItemFixed(level,p_112309_,p_112310_,listed.get(4),0.5D,0.3D,0.5D,p_112311_,p_112312_);
                renderItemFixed(level,p_112309_,p_112310_,listed.get(5),0.6875D,0.3D,0.5D,p_112311_,p_112312_);

                renderItemFixed(level,p_112309_,p_112310_,listed.get(6),0.3125D,0.3D,0.6875D,p_112311_,p_112312_);
                renderItemFixed(level,p_112309_,p_112310_,listed.get(7),0.5D,0.3D,0.6875D,p_112311_,p_112312_);
                renderItemFixed(level,p_112309_,p_112310_,listed.get(8),0.6875D,0.3D,0.6875D,p_112311_,p_112312_);
            }

        }
    }

    public static void renderItemFixed(Level worldIn, PoseStack p_112309_, MultiBufferSource p_112310_, ItemStack itemStack, double transX, double transY, double transZ, int p_112311_, int p_112312_) {
        if (!itemStack.isEmpty()) {
            p_112309_.pushPose();
            p_112309_.translate(transX, transY, transZ);
            p_112309_.scale(0.5F, 0.5F, 0.5F);
            ItemRenderer renderer = Minecraft.getInstance().getItemRenderer();
            BakedModel baked = renderer.getModel(itemStack, worldIn, (LivingEntity)null, 0);
            renderer.render(itemStack, ItemDisplayContext.FIXED, true, p_112309_, p_112310_, p_112311_, p_112312_, baked);
            p_112309_.popPose();
        }
    }
}
