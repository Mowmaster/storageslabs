package com.mowmaster.storageslabs.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mowmaster.mowlib.transport.TransportContents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

// What a 1x1 or 3x3 slab holds, saved in its block entity and carried by its item (storageslabs:slab_contents) so
// nothing is lost when it's broken. Only filled slots are listed, each with its slot number and MowLib's
// TransportContents (the transport type, then that type's resource and amount):
//   {"page": 2, "slots": [{"slot": 0, "contents": {"type": "mowlib:fluids", "contents": [{"resource": {...}, "amount": 4000}]}}]}
// Records are immutable, as data components must be.
public record SlabContents(int page, List<Entry> slots)
{
    public static final SlabContents EMPTY = new SlabContents(0, List.of());

    // One filled slot. TransportContents<?> = contents of "some" type (a wildcard: the type decides which).
    public record Entry(int slot, TransportContents<?> contents)
    {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("slot").forGetter(Entry::slot),
                TransportContents.CODEC.fieldOf("contents").forGetter(Entry::contents)
        ).apply(instance, Entry::new));
    }

    public static final Codec<SlabContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("page", 0).forGetter(SlabContents::page),
            Entry.CODEC.listOf().optionalFieldOf("slots", List.of()).forGetter(SlabContents::slots)
    ).apply(instance, SlabContents::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SlabContents> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public SlabContents
    {
        slots = List.copyOf(slots);
    }

    public boolean isEmpty()
    {
        return slots.isEmpty();
    }
}
