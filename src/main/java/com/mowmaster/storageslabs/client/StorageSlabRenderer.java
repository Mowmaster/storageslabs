package com.mowmaster.storageslabs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mowmaster.mowlib.client.CBItemRenderer;
import com.mowmaster.mowlib.client.MowLibClientParticles;
import com.mowmaster.mowlib.transport.TransportType;
import com.mowmaster.mowlib.transport.TransportTypes;
import com.mowmaster.mowlib.util.CBPixelBox;
import com.mowmaster.storageslabs.block.SlabKind;
import com.mowmaster.storageslabs.blockentity.BaseSlabBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// Draws what each visible cell holds on a slab's top: items lying flat; fluids as a surface with the fluid's FLOWING
// texture and colour; experience (glowing green), energy (shimmering red) and other mods' types (their colour) as a
// surface too.
// Everything is drawn in the model's own space (facing UP, pixels as in the model file), after turning the pose the
// same way the blockstate turns the model: x quarter turns, then y (MowLib CBPixelBox x/yQuarterTurnsFromUp).
// 26.x renderers work in two steps: extractRenderState copies what's needed from the block entity into a State, then
// submit draws from the State only.
public class StorageSlabRenderer<T extends BaseSlabBlockEntity> extends CBItemRenderer<T, StorageSlabRenderer.State>
{
    private static final float PX = 1f / 16f;
    private static final float TOP = 8f;            // the slab's top surface (pixels)
    private static final float ITEM_LIFT = 0.15f;   // items sit this far above it (pixels)
    private static final float SURFACE_LIFT = 0.05f;
    private static final float DEPTH = 0.05f;       // how thick a flattened item is drawn
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int MAX_CELLS = 9;

    public StorageSlabRenderer(BlockEntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    // ---- Extract ----

    @Override
    public void extractRenderState(T slab, State state, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay)
    {
        super.extractRenderState(slab, state, partialTick, cameraPos, crumblingOverlay);
        Level level = slab.getLevel();
        state.facing = slab.facing();
        state.kind = slab.kind();
        float time = level == null ? 0 : (level.getGameTime() % 24000) + partialTick;
        List<CBPixelBox> cells = state.kind.cells();
        state.cells = Math.min(cells.size(), MAX_CELLS);
        for (int cell = 0; cell < state.cells; cell++)
        {
            BaseSlabBlockEntity.CellDisplay display = slab.display(cell);
            state.sprites[cell] = null;
            if (!display.item().isEmpty())
            {
                resolve(state.items[cell], display.item(), ItemDisplayContext.FIXED, level, cell);
            }
            else
            {
                state.items[cell].clear();
                if (!display.isEmpty()) surface(display.type(), display.resource(), time, state, cell);
            }
        }
    }

    // The texture, colour and light for a non-item cell
    private static void surface(@Nullable TransportType<?> type, @Nullable Resource resource, float time, State state, int cell)
    {
        state.lights[cell] = state.lightCoords;
        if (resource instanceof FluidResource fluid && !fluid.isEmpty())
        {
            state.sprites[cell] = fluidModel(fluid.getFluid().defaultFluidState()).flowingMaterial().sprite();
            // MowLib knows NeoForge's fluid tints (-1: no tint, e.g. lava has its colour in the texture)
            int tint = MowLibClientParticles.fluidColor(fluid);
            state.colors[cell] = 0xFF000000 | (tint == -1 ? 0xFFFFFF : tint);
            if (fluid.getFluidType().getLightLevel() > 0) state.lights[cell] = FULL_BRIGHT;
            return;
        }
        // Not a fluid: still water's texture, coloured
        state.sprites[cell] = fluidModel(Fluids.WATER.defaultFluidState()).stillMaterial().sprite();
        if (type == TransportTypes.EXPERIENCE.get())
        {
            float pulse = 0.5f + 0.5f * (float) Math.sin(time * 0.15f);
            state.colors[cell] = 0xFF000000 | lerpColor(0x80FF20, 0xD4FF4A, pulse);
            state.lights[cell] = FULL_BRIGHT;
        }
        else if (type == TransportTypes.ENERGY.get())
        {
            float shimmer = 0.75f + 0.25f * (float) Math.sin(time * 0.6f) * (float) Math.sin(time * 0.23f);
            state.colors[cell] = 0xFF000000 | lerpColor(0x600000, 0xFF3030, shimmer);
            state.lights[cell] = FULL_BRIGHT;
        }
        else
        {
            state.colors[cell] = 0xFF000000 | (type == null ? 0xFFFFFF : type.getRgb());
        }
    }

    // A fluid's model (26.1+: fluid textures are FluidModels in the FluidStateModelSet)
    private static net.minecraft.client.renderer.block.FluidModel fluidModel(FluidState fluidState)
    {
        return Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluidState);
    }

    private static int lerpColor(int from, int to, float t)
    {
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }

    // ---- Submit ----

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (state.kind == null) return;
        poseStack.pushPose();
        // Turn the model space like the blockstate does, around the block's centre. The pose applies the LAST rotation
        // first, so y (applied second by the blockstate) is rotated here first. -90 degrees per quarter turn matches the
        // blockstate's direction (checked: "x": 90 turns the top to face north; "y": 90 turns north to east).
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.rotate(Axis.YP.rotationDegrees(-90f * CBPixelBox.yQuarterTurnsFromUp(state.facing)));
        poseStack.rotate(Axis.XP.rotationDegrees(-90f * CBPixelBox.xQuarterTurnsFromUp(state.facing)));
        poseStack.translate(-0.5, -0.5, -0.5);

        // Items are drawn with their top towards the model's north on a slab facing up or down (readable from the side
        // you'd stand on), and towards the model's south on a wall (that's "up" once turned)
        boolean topNorth = state.facing.getAxis().isVertical();
        List<CBPixelBox> cells = state.kind.cells();
        for (int cell = 0; cell < state.cells; cell++)
        {
            CBPixelBox box = cells.get(cell);
            if (!state.items[cell].isEmpty())
            {
                item(poseStack, collector, state.items[cell], box, state.kind.itemSize(cell), topNorth, state.lightCoords);
            }
            else if (state.sprites[cell] != null)
            {
                TextureAtlasSprite sprite = state.sprites[cell];
                int color = state.colors[cell];
                int light = state.lights[cell];
                collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(sprite.atlasLocation()),
                        (pose, buffer) -> surfaceQuad(buffer, pose, sprite, box, color, light));
            }
        }
        poseStack.popPose();
    }

    // An item lying flat in the middle of a cell
    private static void item(PoseStack poseStack, SubmitNodeCollector collector, ItemStackRenderState item, CBPixelBox box,
                             float size, boolean topNorth, int light)
    {
        poseStack.pushPose();
        poseStack.translate((box.minX() + box.maxX()) / 2 * PX, (TOP + ITEM_LIFT) * PX, (box.minZ() + box.maxZ()) / 2 * PX);
        // A FIXED item faces north as drawn: tip it to face the sky (its top then points south)...
        poseStack.rotate(Axis.XP.rotationDegrees(90));
        // ...and half a turn in its own plane so its top points north
        if (topNorth) poseStack.rotate(Axis.ZP.rotationDegrees(180));
        poseStack.scale(size * PX, size * PX, DEPTH);
        item.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    // A flat square over the cell, facing up, textured with the part of the sprite under it (so neighbouring cells
    // line up). Flowing fluid textures are twice the size of a block: half of the sprite covers the whole block.
    private static void surfaceQuad(VertexConsumer buffer, PoseStack.Pose pose, TextureAtlasSprite sprite, CBPixelBox box,
                                    int color, int light)
    {
        float inset = 0.25f;
        float x0 = (float) (box.minX() + inset), x1 = (float) (box.maxX() - inset);
        float z0 = (float) (box.minZ() + inset), z1 = (float) (box.maxZ() - inset);
        float y = (TOP + SURFACE_LIFT) * PX;
        float u0 = sprite.getU(x0 / 32f), u1 = sprite.getU(x1 / 32f);
        float v0 = sprite.getV(z0 / 32f), v1 = sprite.getV(z1 / 32f);
        vertex(buffer, pose, x0 * PX, y, z0 * PX, u0, v0, color, light);
        vertex(buffer, pose, x0 * PX, y, z1 * PX, u0, v1, color, light);
        vertex(buffer, pose, x1 * PX, y, z1 * PX, u1, v1, color, light);
        vertex(buffer, pose, x1 * PX, y, z0 * PX, u1, v0, color, light);
    }

    // One corner: position, colour (0xAARRGGBB), texture position, no damage overlay, light, facing up
    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int color, int light)
    {
        buffer.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, 0, 1, 0);
    }

    public static class State extends BlockEntityRenderState
    {
        Direction facing = Direction.UP;
        @Nullable SlabKind kind;
        int cells;
        // Reused every frame rather than made new each time
        final ItemStackRenderState[] items = newStates(MAX_CELLS);
        final @Nullable TextureAtlasSprite[] sprites = new TextureAtlasSprite[MAX_CELLS];
        final int[] colors = new int[MAX_CELLS];
        final int[] lights = new int[MAX_CELLS];
    }
}
