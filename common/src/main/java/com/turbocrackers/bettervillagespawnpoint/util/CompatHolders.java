package com.turbocrackers.bettervillagespawnpoint.util;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Backport of HolderSet.direct(...) for MC 1.19.2 and below.
 */
public final class CompatHolders {
    private CompatHolders() {} // prevent instantiation

    public static <T> HolderSet<T> direct(List<Holder<T>> holders) {
        // remove nulls just in case
        final List<Holder<T>> list = holders.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableList());

        return new HolderSet.ListBacked<T>() {
            @Override
            protected List<Holder<T>> contents() {
                return list;
            }

            @Override
            public Either<TagKey<T>, List<Holder<T>>> unwrap() {
                return Either.right(list);
            }

            @Override
            public boolean contains(@NotNull Holder<T> holder) {
                return list.contains(holder);
            }
        };
    }
}