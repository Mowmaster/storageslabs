package com.mowmaster.storageslabs.recipe;

import com.mowmaster.storageslabs.block.SlabTier;
import com.mowmaster.storageslabs.item.StorageSlabItem;
import com.mowmaster.storageslabs.registry.SlabDataComponents;
import com.mowmaster.storageslabs.registry.SlabRecipes;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

// Upgrading a slab one tier: the slab in the middle of a 3x3 grid, 8 of the NEXT tier's material around it
// (block/SlabTier: copper ingots -> tier 1, iron -> 2, gold -> 3, diamonds -> 4, netherite -> 5, nether stars -> 6).
// The result is the same slab with everything it carried (contents, colour, filter) and the next tier. A slab that was
// never dyed shows the new tier's colour; a dyed one keeps its dye.
// A CustomRecipe: the game asks matches/assemble for every grid instead of reading a pattern from JSON (vanilla's
// shaped recipes can't copy the middle item's data onto the result). Data file: {"type": "storageslabs:slab_upgrade"}.
public class SlabUpgradeRecipe extends CustomRecipe
{
    public static final SlabUpgradeRecipe INSTANCE = new SlabUpgradeRecipe();

    private static final int CENTER = 4;

    @Override
    public boolean matches(CraftingInput input, Level level)
    {
        return !assemble(input).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input)
    {
        // CraftingInput is trimmed to what's in the grid: a full 3x3 ring is exactly 3 by 3
        if (input.width() != 3 || input.height() != 3) return ItemStack.EMPTY;
        ItemStack slab = input.getItem(CENTER);
        if (!(slab.getItem() instanceof StorageSlabItem)) return ItemStack.EMPTY;
        SlabTier tier = StorageSlabItem.tierOf(slab);
        if (tier.isMax()) return ItemStack.EMPTY;
        TagKey<Item> material = tier.next().material();
        if (material == null) return ItemStack.EMPTY;
        for (int slot = 0; slot < input.size(); slot++)
        {
            if (slot != CENTER && !input.getItem(slot).is(material)) return ItemStack.EMPTY;
        }
        ItemStack result = slab.copyWithCount(1);
        result.set(SlabDataComponents.TIER.get(), tier.next().ordinal());
        return result;
    }

    @Override
    public RecipeSerializer<SlabUpgradeRecipe> getSerializer()
    {
        return SlabRecipes.SLAB_UPGRADE.get();
    }
}
