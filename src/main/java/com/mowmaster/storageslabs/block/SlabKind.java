package com.mowmaster.storageslabs.block;

import com.mowmaster.mowlib.paging.PageArrows;
import com.mowmaster.mowlib.util.CBPixelBox;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// The three slabs. Each has a layout of cells on its top (in pixels, as modelled facing UP: north = the far side for
// someone standing south of it) and works out its slots and capacity from the tier.
// - SINGLE (slab_1):      1 slot, 3456 per slot; STAR holds Integer.MAX_VALUE
// - COMPACTING (slab_compact): one compacting chain; 1728 of its TOP tier; STAR holds Integer.MAX_VALUE of it.
//                          3 cells: the wide south cell shows the highest tier, then north-west, then north-east.
//                          A chain longer than 3 tiers is shown 3 at a time (page keys turn it).
// - GRID (slab_3):        54 slots (108 at STAR), 9 shown at a time; 64 per slot, times the tier
// Fluids, energy and experience fit in any SINGLE or GRID slot too: see storage/SlabUnits.
public enum SlabKind
{
    SINGLE("slab_single", List.of(cell(2, 2, 14, 14)), null, new float[]{10f}),

    COMPACTING("slab_compacting",
            List.of(cell(2, 8.75, 14, 14), cell(2, 2, 7.5, 7.25), cell(9, 2, 14, 7.25)),
            null, new float[]{4.5f, 4f, 4f}),

    GRID("slab_grid", List.of(
            cell(2, 2, 5, 5), cell(6.5, 2, 9.5, 5), cell(11, 2, 14, 5),
            cell(2, 6.5, 5, 9.5), cell(6.5, 6.5, 9.5, 9.5), cell(11, 6.5, 14, 9.5),
            cell(2, 11, 5, 14), cell(6.5, 11, 9.5, 14), cell(11, 11, 14, 14)),
            // The model's arrows (x 0.25-1.75 and 14.25-15.75, z 7.25-8.75, on the rails' top at y 9), a little bigger
            // so they're easy to hit. Back = west (left for someone looking at the top from the south).
            new PageArrows(new CBPixelBox(-0.25, 8.5, 6.75, 2.25, 10, 9.25), new CBPixelBox(13.75, 8.5, 6.75, 16.25, 10, 9.25), true),
            new float[]{3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f, 3f});

    // Base numbers (tier BASE)
    public static final int SINGLE_CAPACITY = 3456;
    public static final int COMPACTING_CAPACITY = 1728;
    public static final int GRID_SLOT_CAPACITY = 64;
    public static final int GRID_SLOTS = 54;
    public static final int GRID_SLOTS_STAR = 108;
    public static final int CELLS_PER_PAGE_GRID = 9;
    public static final int CELLS_PER_PAGE_COMPACTING = 3;

    private final String id;
    private final List<CBPixelBox> cells;
    private final @Nullable PageArrows arrows;
    private final float[] itemSizes;

    SlabKind(String id, List<CBPixelBox> cells, @Nullable PageArrows arrows, float[] itemSizes)
    {
        this.id = id;
        this.cells = cells;
        this.arrows = arrows;
        this.itemSizes = itemSizes;
    }

    // A cell on the top face (y 8, the slab's top surface inside its rails), as a box that also takes in a click
    // landing on the face. The renderer uses the same numbers.
    private static CBPixelBox cell(double minX, double minZ, double maxX, double maxZ)
    {
        return new CBPixelBox(minX, 7.5, minZ, maxX, 9.5, maxZ);
    }

    // The registry name of the block and item
    public String id()
    {
        return id;
    }

    public List<CBPixelBox> cells()
    {
        return cells;
    }

    public @Nullable PageArrows arrows()
    {
        return arrows;
    }

    // How big (in pixels) an item is drawn in each cell
    public float itemSize(int cell)
    {
        return itemSizes[cell];
    }

    public int cellsPerPage()
    {
        return cells.size();
    }

    // Number of slots (SINGLE, GRID); COMPACTING counts as 1 (its chain is one pool)
    public int slots(SlabTier tier)
    {
        return switch (this)
        {
            case SINGLE, COMPACTING -> 1;
            case GRID -> tier.isMax() ? GRID_SLOTS_STAR : GRID_SLOTS;
        };
    }

    // Items per slot (COMPACTING: items of the chain's top tier)
    public int capacity(SlabTier tier)
    {
        return switch (this)
        {
            case SINGLE -> tier.isMax() ? Integer.MAX_VALUE : multiply(SINGLE_CAPACITY, tier);
            case COMPACTING -> tier.isMax() ? Integer.MAX_VALUE : multiply(COMPACTING_CAPACITY, tier);
            case GRID -> multiply(GRID_SLOT_CAPACITY, tier);
        };
    }

    // long maths so a big multiplier can't wrap round; capped at Integer.MAX_VALUE
    private static int multiply(int base, SlabTier tier)
    {
        return (int) Math.min(Integer.MAX_VALUE, (long) base * tier.multiplier());
    }
}
