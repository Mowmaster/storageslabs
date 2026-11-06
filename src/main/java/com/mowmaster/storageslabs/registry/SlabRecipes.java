package com.mowmaster.storageslabs.registry;

import com.mojang.serialization.MapCodec;
import com.mowmaster.storageslabs.StorageSlabs;
import com.mowmaster.storageslabs.recipe.SlabUpgradeRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class SlabRecipes
{
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, StorageSlabs.MODID);

    // The tier upgrade (slab in the middle, 8 of the next tier's material around it). It has no settings, so the
    // JSON is just {"type": "storageslabs:slab_upgrade"} and both codecs produce the single instance.
    public static final Supplier<RecipeSerializer<SlabUpgradeRecipe>> SLAB_UPGRADE = SERIALIZERS.register("slab_upgrade",
            () -> new RecipeSerializer<>(MapCodec.unit(SlabUpgradeRecipe.INSTANCE), StreamCodec.unit(SlabUpgradeRecipe.INSTANCE)));
}
